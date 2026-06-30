use crate::{
    app_state::AppState,
    error::AppError,
    models::seat::{AdminSeatBooking, SeatAvailability, SeatAvailabilityResponse, SeatBooking, StudentSeatBooking},
    services::notification,
};
use chrono::NaiveDate;
use redis::AsyncCommands;
use std::collections::HashMap;
use std::sync::Arc;
use uuid::Uuid;

const CACHE_TTL_SECS: u64 = 300;

fn group_by_row(seats: &[SeatAvailability]) -> HashMap<String, Vec<SeatAvailability>> {
    let mut map: HashMap<String, Vec<SeatAvailability>> = HashMap::new();
    for seat in seats {
        map.entry(seat.row_label.clone()).or_default().push(seat.clone());
    }
    map
}

pub async fn get_availability(
    state: &Arc<AppState>,
    shift: &str,
    date: NaiveDate,
) -> crate::error::Result<SeatAvailabilityResponse> {
    let cache_key = format!("seats:availability:{shift}:{date}");

    // Try cache first
    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        if let Ok(cached) = conn.get::<_, String>(&cache_key).await {
            if let Ok(seats) = serde_json::from_str::<Vec<SeatAvailability>>(&cached) {
                let available = seats.iter().filter(|s| s.is_active && !s.is_booked).count();
                let booked = seats.iter().filter(|s| s.is_booked).count();
                let seats_by_row = group_by_row(&seats);
                return Ok(SeatAvailabilityResponse {
                    shift: shift.to_string(),
                    date,
                    available_count: available,
                    booked_count: booked,
                    seats_by_row,
                    seats,
                });
            }
        }
    }

    let rows = sqlx::query_as::<_, (Uuid, String, String, i32, bool, bool, Option<Uuid>, Option<String>)>(
        r#"SELECT
            s.id,
            s.seat_number,
            s.row_label,
            s.seat_index,
            s.is_active,
            CASE WHEN sb.id IS NOT NULL THEN true ELSE false END,
            sb.user_id,
            u.name
         FROM seats s
         LEFT JOIN seat_bookings sb ON sb.seat_id = s.id
            AND sb.status = 'ACTIVE'
            AND sb.booking_date <= $1
            AND sb.end_date >= $1
            AND (sb.shift = $2 OR sb.shift = 'FULL_DAY' OR $2::text = 'FULL_DAY')
         LEFT JOIN users u ON u.id = sb.user_id
         ORDER BY s.row_label, s.seat_index"#,
    )
    .bind(date)
    .bind(shift)
    .fetch_all(&state.db)
    .await?;

    let seats: Vec<SeatAvailability> = rows
        .into_iter()
        .map(|(id, seat_number, row_label, seat_index, is_active, is_booked, booked_by, booked_by_name)| {
            SeatAvailability {
                seat_id: id,
                seat_number,
                row_label,
                seat_index,
                is_active,
                is_booked,
                booked_by,
                booked_by_name,
            }
        })
        .collect();

    // Cache the result
    if let Ok(json) = serde_json::to_string(&seats) {
        if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
            let _ = conn.set_ex::<_, _, ()>(&cache_key, &json, CACHE_TTL_SECS).await;
        }
    }

    let available = seats.iter().filter(|s| s.is_active && !s.is_booked).count();
    let booked = seats.iter().filter(|s| s.is_booked).count();
    let seats_by_row = group_by_row(&seats);

    Ok(SeatAvailabilityResponse {
        shift: shift.to_string(),
        date,
        available_count: available,
        booked_count: booked,
        seats_by_row,
        seats,
    })
}

pub async fn book_seat(
    state: &Arc<AppState>,
    user_id: Uuid,
    membership_id: Uuid,
    seat_number: &str,
    shift: &str,
    start_date: NaiveDate,
    end_date: NaiveDate,
) -> crate::error::Result<SeatBooking> {
    let seat = sqlx::query_as::<_, crate::models::seat::Seat>(
        "SELECT * FROM seats WHERE seat_number = $1 AND is_active = true",
    )
    .bind(seat_number)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound(format!("Seat {seat_number} not found")))?;

    // Check for conflicting bookings
    let conflict: i64 = sqlx::query_scalar(
        r#"SELECT COUNT(*) FROM seat_bookings
         WHERE seat_id = $1
           AND status = 'ACTIVE'
           AND booking_date <= $3
           AND end_date >= $2
           AND (shift = $4 OR shift = 'FULL_DAY' OR $4::text = 'FULL_DAY')"#,
    )
    .bind(seat.id)
    .bind(start_date)
    .bind(end_date)
    .bind(shift)
    .fetch_one(&state.db)
    .await?;

    if conflict > 0 {
        return Err(AppError::Conflict(format!(
            "Seat {seat_number} is already booked for {shift} during the requested period"
        )));
    }

    let booking = sqlx::query_as::<_, SeatBooking>(
        "INSERT INTO seat_bookings (seat_id, user_id, membership_id, shift, booking_date, end_date)
         VALUES ($1, $2, $3, $4, $5, $6) RETURNING *",
    )
    .bind(seat.id)
    .bind(user_id)
    .bind(membership_id)
    .bind(shift)
    .bind(start_date)
    .bind(end_date)
    .fetch_one(&state.db)
    .await
    .map_err(|e| {
        if e.to_string().contains("unique") {
            AppError::Conflict(format!("Seat {seat_number} is already booked"))
        } else {
            AppError::Database(e)
        }
    })?;

    sqlx::query("UPDATE memberships SET seat_id = $2, seat_number = $3 WHERE id = $1")
        .bind(membership_id)
        .bind(seat.id)
        .bind(seat_number)
        .execute(&state.db)
        .await?;

    invalidate_seat_cache(state, shift, start_date, end_date).await;

    // Send booking notification to student and admin
    let state2 = state.clone();
    let seat_num = seat_number.to_string();
    let shift_str = shift.to_string();
    tokio::spawn(async move {
        let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
            .bind(user_id)
            .fetch_optional(&state2.db)
            .await;

        let membership_plan = sqlx::query_as::<_, (String, String, rust_decimal::Decimal)>(
            "SELECT mp.name, mp.plan_type, mp.price FROM memberships m
             JOIN membership_plans mp ON mp.id = m.plan_id
             WHERE m.id = $1",
        )
        .bind(membership_id)
        .fetch_optional(&state2.db)
        .await;

        let payment_amount = sqlx::query_scalar::<_, Option<rust_decimal::Decimal>>(
            "SELECT amount FROM payments WHERE membership_id = $1 AND status = 'SUCCESS' LIMIT 1",
        )
        .bind(membership_id)
        .fetch_optional(&state2.db)
        .await
        .ok()
        .flatten()
        .flatten();

        if let (Ok(Some(user)), Ok(Some((plan_name, plan_type, plan_price)))) = (user, membership_plan) {
            let info = notification::BookingInfo {
                user_name: user.name,
                user_mobile: user.mobile,
                user_email: user.email,
                plan_name,
                plan_type,
                seat_number: Some(seat_num),
                shift: shift_str,
                start_date,
                end_date,
                amount_paid: payment_amount.unwrap_or(plan_price),
            };
            notification::send_booking_confirmed(&state2, &info).await;
        }
    });

    Ok(booking)
}

pub async fn release_booking(
    state: &Arc<AppState>,
    membership_id: Uuid,
) -> crate::error::Result<()> {
    let bookings = sqlx::query_as::<_, SeatBooking>(
        "UPDATE seat_bookings SET status = 'RELEASED'
         WHERE membership_id = $1 AND status = 'ACTIVE' RETURNING *",
    )
    .bind(membership_id)
    .fetch_all(&state.db)
    .await?;

    for b in &bookings {
        invalidate_seat_cache(state, &b.shift, b.booking_date, b.end_date).await;
    }

    sqlx::query("UPDATE memberships SET status = 'CANCELLED' WHERE id = $1")
        .bind(membership_id)
        .execute(&state.db)
        .await?;

    Ok(())
}

pub async fn get_my_bookings(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<StudentSeatBooking>> {
    let rows = sqlx::query_as::<_, (
        uuid::Uuid, uuid::Uuid, String, String, uuid::Uuid, uuid::Uuid,
        String, NaiveDate, NaiveDate, String, Option<chrono::NaiveDateTime>,
    )>(
        r#"SELECT sb.id, sb.seat_id, s.seat_number, s.row_label,
                  sb.user_id, sb.membership_id, sb.shift,
                  sb.booking_date, sb.end_date, sb.status, sb.created_at
           FROM seat_bookings sb
           JOIN seats s ON s.id = sb.seat_id
           WHERE sb.user_id = $1 AND sb.status = 'ACTIVE'
           ORDER BY sb.booking_date"#,
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)?;

    Ok(rows.into_iter().map(|r| StudentSeatBooking {
        id: r.0,
        seat_id: r.1,
        seat_number: r.2,
        row_label: r.3,
        user_id: r.4,
        membership_id: r.5,
        shift: r.6,
        booking_date: r.7,
        end_date: r.8,
        status: r.9,
        created_at: r.10,
    }).collect())
}

pub async fn get_admin_bookings(
    state: &Arc<AppState>,
    shift: &str,
    date: NaiveDate,
) -> crate::error::Result<Vec<AdminSeatBooking>> {
    sqlx::query_as::<_, (
        Uuid, String, String, NaiveDate, NaiveDate,
        Uuid, String, Option<String>, Uuid, String,
    )>(
        r#"SELECT sb.id, s.seat_number, sb.shift, sb.booking_date, sb.end_date,
                  u.id, u.name, u.mobile, sb.membership_id, sb.status
           FROM seat_bookings sb
           JOIN seats s ON s.id = sb.seat_id
           JOIN users u ON u.id = sb.user_id
           WHERE sb.status = 'ACTIVE'
             AND sb.booking_date <= $2
             AND sb.end_date >= $2
             AND (sb.shift = $1 OR sb.shift = 'FULL_DAY' OR $1::text = 'FULL_DAY')
           ORDER BY s.row_label, s.seat_index"#,
    )
    .bind(shift)
    .bind(date)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(|r| AdminSeatBooking {
        booking_id: r.0,
        seat_number: r.1,
        shift: r.2,
        booking_date: r.3,
        end_date: r.4,
        user_id: r.5,
        user_name: r.6,
        user_mobile: r.7,
        membership_id: r.8,
        status: r.9,
    })
    .collect::<Vec<_>>()
    .pipe(Ok)
}

pub async fn invalidate_seat_cache(state: &Arc<AppState>, shift: &str, from: NaiveDate, to: NaiveDate) {
    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        let mut date = from;
        while date <= to {
            let key = format!("seats:availability:{shift}:{date}");
            let _ = conn.del::<_, i64>(&key).await;
            if shift == "FULL_DAY" {
                let _ = conn.del::<_, i64>(format!("seats:availability:MORNING:{date}")).await;
                let _ = conn.del::<_, i64>(format!("seats:availability:EVENING:{date}")).await;
            }
            date += chrono::Duration::days(1);
        }
    }
}

trait Pipe: Sized {
    fn pipe<F, R>(self, f: F) -> R where F: FnOnce(Self) -> R { f(self) }
}
impl<T> Pipe for T {}
