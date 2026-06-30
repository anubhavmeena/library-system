use crate::{
    app_state::AppState,
    error::AppError,
    models::membership::{
        CreateOrderResponse, Membership, MembershipPlan, MembershipWithPlan, Payment,
    },
    services::{notification, payment},
};
use chrono::NaiveDate;
use rust_decimal::Decimal;
use std::sync::Arc;
use uuid::Uuid;

pub async fn list_active_plans(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<MembershipPlan>> {
    sqlx::query_as::<_, MembershipPlan>(
        "SELECT * FROM membership_plans WHERE is_active = true ORDER BY price",
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn get_active_membership(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Option<MembershipWithPlan>> {
    let row = sqlx::query_as::<_, (
        Uuid, Uuid, Uuid, String, String, Option<Uuid>, Option<String>,
        Option<String>, NaiveDate, NaiveDate, String, Option<Decimal>, Option<chrono::NaiveDateTime>,
        Option<Decimal>,
    )>(
        "SELECT m.id, m.user_id, m.plan_id, p.name, p.plan_type, m.seat_id,
                COALESCE(m.seat_number, (
                    SELECT s.seat_number FROM seat_bookings sb
                    JOIN seats s ON s.id = sb.seat_id
                    WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
                    LIMIT 1
                )) AS seat_number,
                m.shift, m.start_date, m.end_date, m.status, pay.amount, m.created_at, p.price
         FROM memberships m
         JOIN membership_plans p ON p.id = m.plan_id
         LEFT JOIN payments pay ON pay.membership_id = m.id AND pay.status = 'SUCCESS'
         WHERE m.user_id = $1 AND m.status = 'ACTIVE'
         ORDER BY m.created_at DESC LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?
    .map(|r| MembershipWithPlan {
        id: r.0,
        user_id: r.1,
        plan_id: r.2,
        plan_name: r.3,
        plan_type: r.4,
        seat_id: r.5,
        seat_number: r.6,
        shift: r.7,
        start_date: r.8,
        end_date: r.9,
        status: r.10,
        amount_paid: r.11,
        created_at: r.12,
        plan_price: r.13,
    });

    Ok(row)
}

pub async fn get_queued_membership(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Option<MembershipWithPlan>> {
    sqlx::query_as::<_, (
        Uuid, Uuid, Uuid, String, String, Option<Uuid>, Option<String>,
        Option<String>, NaiveDate, NaiveDate, String, Option<Decimal>, Option<chrono::NaiveDateTime>,
        Option<Decimal>,
    )>(
        "SELECT m.id, m.user_id, m.plan_id, p.name, p.plan_type, m.seat_id,
                COALESCE(m.seat_number, (
                    SELECT s.seat_number FROM seat_bookings sb
                    JOIN seats s ON s.id = sb.seat_id
                    WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
                    LIMIT 1
                )) AS seat_number,
                m.shift, m.start_date, m.end_date, m.status, pay.amount, m.created_at, p.price
         FROM memberships m
         JOIN membership_plans p ON p.id = m.plan_id
         LEFT JOIN payments pay ON pay.membership_id = m.id AND pay.status = 'SUCCESS'
         WHERE m.user_id = $1 AND m.status = 'QUEUED'
         ORDER BY m.created_at DESC LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?
    .map(|r| MembershipWithPlan {
        id: r.0,
        user_id: r.1,
        plan_id: r.2,
        plan_name: r.3,
        plan_type: r.4,
        seat_id: r.5,
        seat_number: r.6,
        shift: r.7,
        start_date: r.8,
        end_date: r.9,
        status: r.10,
        amount_paid: r.11,
        created_at: r.12,
        plan_price: r.13,
    })
    .pipe(Ok)
}

pub async fn get_all_memberships(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<MembershipWithPlan>> {
    sqlx::query_as::<_, (
        Uuid, Uuid, Uuid, String, String, Option<Uuid>, Option<String>,
        Option<String>, NaiveDate, NaiveDate, String, Option<Decimal>, Option<chrono::NaiveDateTime>,
        Option<Decimal>,
    )>(
        "SELECT m.id, m.user_id, m.plan_id, p.name, p.plan_type, m.seat_id,
                COALESCE(m.seat_number, (
                    SELECT s.seat_number FROM seat_bookings sb
                    JOIN seats s ON s.id = sb.seat_id
                    WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
                    LIMIT 1
                )) AS seat_number,
                m.shift, m.start_date, m.end_date, m.status, pay.amount, m.created_at, p.price
         FROM memberships m
         JOIN membership_plans p ON p.id = m.plan_id
         LEFT JOIN payments pay ON pay.membership_id = m.id AND pay.status = 'SUCCESS'
         WHERE m.user_id = $1
         ORDER BY m.created_at DESC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(|r| MembershipWithPlan {
        id: r.0,
        user_id: r.1,
        plan_id: r.2,
        plan_name: r.3,
        plan_type: r.4,
        seat_id: r.5,
        seat_number: r.6,
        shift: r.7,
        start_date: r.8,
        end_date: r.9,
        status: r.10,
        amount_paid: r.11,
        created_at: r.12,
        plan_price: r.13,
    })
    .collect::<Vec<_>>()
    .pipe(Ok)
}

pub async fn create_order(
    state: &Arc<AppState>,
    user_id: Uuid,
    plan_id: Uuid,
    shift: &str,
    seat_number: Option<&str>,
) -> crate::error::Result<CreateOrderResponse> {
    let plan = sqlx::query_as::<_, MembershipPlan>(
        "SELECT * FROM membership_plans WHERE id = $1 AND is_active = true",
    )
    .bind(plan_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Plan not found or inactive".into()))?;

    let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".into()))?;

    let today = chrono::Local::now().date_naive();
    let (start_date, status) = determine_start_date(state, user_id, today).await?;
    let end_date = start_date + chrono::Duration::days(plan.duration_days as i64 - 1);

    let membership_status = if status == "QUEUED" { "QUEUED" } else { "PENDING" };

    let membership = sqlx::query_as::<_, Membership>(
        "INSERT INTO memberships (user_id, plan_id, seat_number, shift, start_date, end_date, status)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *",
    )
    .bind(user_id)
    .bind(plan_id)
    .bind(seat_number)
    .bind(shift)
    .bind(start_date)
    .bind(end_date)
    .bind(membership_status)
    .fetch_one(&state.db)
    .await?;

    let order = payment::create_order(
        state,
        membership.id,
        user_id,
        user.mobile.as_deref(),
        user.email.as_deref(),
        &user.name,
        plan.price,
    )
    .await?;

    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, payment_gateway, gateway_order_id, status)
         VALUES ($1, $2, $3, $4, $5, 'PENDING')",
    )
    .bind(membership.id)
    .bind(user_id)
    .bind(plan.price)
    .bind(&order.gateway)
    .bind(&order.order_id)
    .execute(&state.db)
    .await?;

    Ok(order)
}

pub async fn verify_payment(
    state: &Arc<AppState>,
    user_id: Uuid,
    membership_id: Uuid,
    order_id: &str,
    payment_id: Option<&str>,
    signature: Option<&str>,
) -> crate::error::Result<MembershipWithPlan> {
    let paid = payment::verify_payment(state, order_id, payment_id, signature).await?;

    if !paid {
        sqlx::query(
            "UPDATE payments SET status = 'FAILED', updated_at = NOW()
             WHERE membership_id = $1 AND gateway_order_id = $2",
        )
        .bind(membership_id)
        .bind(order_id)
        .execute(&state.db)
        .await?;
        return Err(AppError::BadRequest("Payment verification failed".into()));
    }

    sqlx::query(
        "UPDATE payments SET status = 'SUCCESS', gateway_order_id = $3, gateway_payment_id = $4, updated_at = NOW()
         WHERE membership_id = $1 AND gateway_order_id = $2",
    )
    .bind(membership_id)
    .bind(order_id)
    .bind(order_id)
    .bind(payment_id)
    .execute(&state.db)
    .await?;

    let membership = sqlx::query_as::<_, Membership>(
        "UPDATE memberships SET status = 'ACTIVE' WHERE id = $1 AND user_id = $2 RETURNING *",
    )
    .bind(membership_id)
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;

    // Auto-assign seat if seat_number is present
    if let Some(ref seat_num) = membership.seat_number {
        if let Ok(seat) = sqlx::query_as::<_, crate::models::seat::Seat>(
            "SELECT * FROM seats WHERE seat_number = $1",
        )
        .bind(seat_num)
        .fetch_optional(&state.db)
        .await
        {
            if let Some(seat) = seat {
                let _ = sqlx::query(
                    "UPDATE memberships SET seat_id = $2 WHERE id = $1",
                )
                .bind(membership.id)
                .bind(seat.id)
                .execute(&state.db)
                .await;

                let _ = sqlx::query(
                    "INSERT INTO seat_bookings (seat_id, user_id, membership_id, shift, booking_date, end_date)
                     VALUES ($1, $2, $3, $4, $5, $6)
                     ON CONFLICT (seat_id, shift, booking_date) DO NOTHING",
                )
                .bind(seat.id)
                .bind(user_id)
                .bind(membership.id)
                .bind(&membership.shift)
                .bind(membership.start_date)
                .bind(membership.end_date)
                .execute(&state.db)
                .await;

                if let Some(ref shift) = membership.shift {
                    crate::services::seat::invalidate_seat_cache(
                        state, shift, membership.start_date, membership.end_date,
                    ).await;
                }
            }
        }
    }

    let plan = sqlx::query_as::<_, MembershipPlan>(
        "SELECT * FROM membership_plans WHERE id = $1",
    )
    .bind(membership.plan_id)
    .fetch_one(&state.db)
    .await?;

    let user = sqlx::query_as::<_, crate::models::user::User>(
        "SELECT * FROM users WHERE id = $1",
    )
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;

    let payment_rec = sqlx::query_as::<_, Payment>(
        "SELECT * FROM payments WHERE membership_id = $1 AND status = 'SUCCESS' LIMIT 1",
    )
    .bind(membership.id)
    .fetch_optional(&state.db)
    .await?;

    let state2 = state.clone();
    let info = notification::BookingInfo {
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        plan_name: plan.name.clone(),
        plan_type: plan.plan_type.clone(),
        seat_number: membership.seat_number.clone(),
        shift: membership.shift.clone().unwrap_or_default(),
        start_date: membership.start_date,
        end_date: membership.end_date,
        amount_paid: payment_rec.as_ref().map(|p| p.amount).unwrap_or(plan.price),
    };
    tokio::spawn(async move { notification::send_booking_confirmed(&state2, &info).await });

    Ok(MembershipWithPlan {
        id: membership.id,
        user_id: membership.user_id,
        plan_id: membership.plan_id,
        plan_name: plan.name,
        plan_type: plan.plan_type.clone(),
        seat_id: membership.seat_id,
        seat_number: membership.seat_number,
        shift: membership.shift,
        start_date: membership.start_date,
        end_date: membership.end_date,
        status: membership.status,
        amount_paid: payment_rec.map(|p| p.amount),
        plan_price: Some(plan.price),
        created_at: membership.created_at,
    })
}

pub async fn get_payment_history(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<Payment>> {
    sqlx::query_as::<_, Payment>(
        "SELECT * FROM payments WHERE user_id = $1 ORDER BY created_at DESC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

async fn determine_start_date(
    state: &Arc<AppState>,
    user_id: Uuid,
    today: NaiveDate,
) -> crate::error::Result<(NaiveDate, String)> {
    let active: Option<NaiveDate> = sqlx::query_scalar(
        "SELECT end_date FROM memberships WHERE user_id = $1 AND status = 'ACTIVE'
         ORDER BY end_date DESC LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?;

    if let Some(active_end) = active {
        let start = active_end + chrono::Duration::days(1);
        Ok((start, "QUEUED".into()))
    } else {
        Ok((today, "PENDING".into()))
    }
}

trait Pipe: Sized {
    fn pipe<F, R>(self, f: F) -> R where F: FnOnce(Self) -> R {
        f(self)
    }
}
impl<T> Pipe for T {}
