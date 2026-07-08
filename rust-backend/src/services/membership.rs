use crate::{
    app_state::AppState,
    error::AppError,
    models::membership::{
        CreateOrderResponse, Membership, MembershipPlan, MembershipWithPlan, Payment,
    },
    services::{ids, notification, payment, settings},
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

const MEMBERSHIP_WITH_PLAN_SELECT: &str = "
    SELECT m.id, m.user_id, m.plan_id, p.name, p.plan_type, m.seat_id,
           COALESCE(m.seat_number, (
               SELECT s.seat_number FROM seat_bookings sb
               JOIN seats s ON s.id = sb.seat_id
               WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
               LIMIT 1
           )) AS seat_number,
           m.shift, m.start_date, m.end_date, m.status, pay.amount, m.created_at, p.price,
           m.dues_amount
    FROM memberships m
    JOIN membership_plans p ON p.id = m.plan_id
    LEFT JOIN payments pay ON pay.membership_id = m.id AND pay.status = 'SUCCESS'";

type MembershipRow = (
    Uuid, Uuid, Uuid, String, String, Option<Uuid>, Option<String>,
    Option<String>, NaiveDate, NaiveDate, String, Option<Decimal>, Option<chrono::NaiveDateTime>,
    Option<Decimal>, Option<Decimal>,
);

fn map_row(r: MembershipRow) -> MembershipWithPlan {
    MembershipWithPlan {
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
        dues_amount: r.14,
    }
}

pub async fn get_active_membership(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Option<MembershipWithPlan>> {
    let sql = format!(
        "{MEMBERSHIP_WITH_PLAN_SELECT}
         WHERE m.user_id = $1 AND m.status = 'ACTIVE'
         ORDER BY m.created_at DESC LIMIT 1"
    );
    Ok(sqlx::query_as::<_, MembershipRow>(&sql)
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .map(map_row))
}

/// The membership that governs the student's current standing — GRACE always
/// outranks ACTIVE regardless of end_date, since an unresolved dues row must
/// never be hidden behind a newer self-booked ACTIVE membership.
pub async fn find_current_membership(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Option<MembershipWithPlan>> {
    let sql = format!(
        "{MEMBERSHIP_WITH_PLAN_SELECT}
         WHERE m.user_id = $1 AND m.status IN ('ACTIVE', 'GRACE')
         ORDER BY CASE WHEN m.status = 'GRACE' THEN 0 ELSE 1 END, m.end_date DESC LIMIT 1"
    );
    Ok(sqlx::query_as::<_, MembershipRow>(&sql)
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .map(map_row))
}

pub async fn get_queued_membership(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Option<MembershipWithPlan>> {
    let sql = format!(
        "{MEMBERSHIP_WITH_PLAN_SELECT}
         WHERE m.user_id = $1 AND m.status = 'QUEUED'
         ORDER BY m.created_at DESC LIMIT 1"
    );
    Ok(sqlx::query_as::<_, MembershipRow>(&sql)
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .map(map_row))
}

pub async fn get_all_memberships(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<MembershipWithPlan>> {
    let sql = format!("{MEMBERSHIP_WITH_PLAN_SELECT} WHERE m.user_id = $1 ORDER BY m.created_at DESC");
    Ok(sqlx::query_as::<_, MembershipRow>(&sql)
        .bind(user_id)
        .fetch_all(&state.db)
        .await?
        .into_iter()
        .map(map_row)
        .collect())
}

/// Resolved status shown to the student/admin instead of the raw DB status —
/// `GRACE` never auto-closes in the DB, this is a pure label threshold based
/// on how many days overdue the grace-status membership is.
pub fn resolve_display_status(
    current_status: Option<&str>,
    current_end_date: Option<NaiveDate>,
    current_pending_amount: Option<Decimal>,
    latest_ever_status: Option<&str>,
    grace_days: i32,
) -> &'static str {
    match current_status {
        Some("GRACE") => {
            let today = chrono::Local::now().date_naive();
            let days_overdue = current_end_date.map(|d| (today - d).num_days()).unwrap_or(0);
            if days_overdue > grace_days as i64 { "EXPIRED" } else { "GRACE" }
        }
        Some(_) => {
            if current_pending_amount.map(|p| p > Decimal::ZERO).unwrap_or(false) { "PENDING" } else { "PAID" }
        }
        None => match latest_ever_status {
            Some("EXPIRED") | Some("CANCELLED") => "RELEASED",
            _ => "NEW",
        },
    }
}

pub async fn get_my_display_status(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<String> {
    let current = find_current_membership(state, user_id).await?;

    let pending_amount: Option<Decimal> = if let Some(ref m) = current {
        sqlx::query_scalar(
            "SELECT pending_amount FROM payments
             WHERE membership_id = $1 AND status = 'SUCCESS'
             ORDER BY created_at DESC LIMIT 1",
        )
        .bind(m.id)
        .fetch_optional(&state.db)
        .await?
        .flatten()
    } else {
        None
    };

    let latest_ever: Option<String> = sqlx::query_scalar(
        "SELECT status FROM memberships WHERE user_id = $1 AND status != 'PENDING'
         ORDER BY created_at DESC LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?;

    let grace_days = settings::grace_days(state).await;

    Ok(resolve_display_status(
        current.as_ref().map(|m| m.status.as_str()),
        current.as_ref().map(|m| m.end_date),
        pending_amount,
        latest_ever.as_deref(),
        grace_days,
    ).to_string())
}

pub async fn create_order(
    state: &Arc<AppState>,
    user_id: Uuid,
    plan_id: Uuid,
    shift: Option<&str>,
    seat_number: Option<&str>,
) -> crate::error::Result<CreateOrderResponse> {
    let has_grace: bool = sqlx::query_scalar(
        "SELECT EXISTS(SELECT 1 FROM memberships WHERE user_id = $1 AND status = 'GRACE')",
    )
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;
    if has_grace {
        return Err(AppError::BadRequest(
            "You have unpaid dues on a previous membership. Please clear your dues before purchasing a new plan.".into(),
        ));
    }

    let has_queued: bool = sqlx::query_scalar(
        "SELECT EXISTS(SELECT 1 FROM memberships WHERE user_id = $1 AND status = 'QUEUED')",
    )
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;
    if has_queued {
        return Err(AppError::BadRequest("You already have a queued renewal.".into()));
    }

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
    let (start_date, status, inherited_shift, inherited_seat) =
        determine_start_date(state, user_id, today).await?;
    let end_date = start_date + chrono::Duration::days(plan.duration_days as i64 - 1);

    let membership_status = if status == "QUEUED" { "QUEUED" } else { "PENDING" };

    // Queued renewal: inherit seat/shift from the current membership regardless
    // of what the request sent. Fresh booking: the request must supply a shift.
    let (resolved_shift, resolved_seat) = if membership_status == "QUEUED" {
        (
            inherited_shift.ok_or_else(|| {
                AppError::Internal("Active membership missing shift while queuing renewal".into())
            })?,
            inherited_seat,
        )
    } else {
        (
            shift.ok_or_else(|| AppError::BadRequest("Shift is required".into()))?.to_string(),
            seat_number.map(|s| s.to_string()),
        )
    };

    let membership = sqlx::query_as::<_, Membership>(
        "INSERT INTO memberships (user_id, plan_id, seat_number, shift, start_date, end_date, status)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *",
    )
    .bind(user_id)
    .bind(plan_id)
    .bind(&resolved_seat)
    .bind(&resolved_shift)
    .bind(start_date)
    .bind(end_date)
    .bind(membership_status)
    .fetch_one(&state.db)
    .await?;

    let convenience_fee = settings::get_app_settings(state).await?.convenience_fee;
    let charge_amount = plan.price + convenience_fee;

    let order = payment::create_order(
        state,
        membership.id,
        user_id,
        user.mobile.as_deref(),
        user.email.as_deref(),
        &user.name,
        charge_amount,
    )
    .await?;

    // Never persist a PENDING Payment row — stash the in-flight checkout on
    // the membership itself and only construct a Payment row once the
    // gateway confirms SUCCESS in verify_payment(). An abandoned/failed
    // checkout this way leaves zero Payment rows.
    sqlx::query(
        "UPDATE memberships SET gateway_order_id = $2, checkout_amount = $3 WHERE id = $1",
    )
    .bind(membership.id)
    .bind(&order.order_id)
    .bind(charge_amount)
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
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE id = $1 AND user_id = $2",
    )
    .bind(membership_id)
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Membership not found".into()))?;

    if membership.gateway_order_id.as_deref() != Some(order_id) {
        return Err(AppError::BadRequest("Order does not match this membership".into()));
    }

    let paid = payment::verify_payment(state, order_id, payment_id, signature).await?;
    if !paid {
        return Err(AppError::BadRequest("Payment verification failed".into()));
    }

    let charge_amount = membership.checkout_amount.unwrap_or_default();

    let payment_rec = sqlx::query_as::<_, Payment>(
        "INSERT INTO payments (membership_id, user_id, amount, payment_gateway, gateway_order_id, gateway_payment_id, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'SUCCESS') RETURNING *",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(charge_amount)
    .bind(&state.config.payment_gateway)
    .bind(order_id)
    .bind(payment_id)
    .bind(ids::generate_invoice_id())
    .fetch_one(&state.db)
    .await?;

    let membership = sqlx::query_as::<_, Membership>(
        "UPDATE memberships SET status = 'ACTIVE' WHERE id = $1 AND user_id = $2 RETURNING *",
    )
    .bind(membership_id)
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;

    // Auto-assign seat if seat_number is present. The payment has already been charged
    // by this point, so a conflict here can't fail the request outright — instead we
    // decline the phantom claim (clear seat_number back to NULL) rather than silently
    // marking the membership as holding a seat someone else actually has; the admin
    // "Students Needing a Seat" tool picks up memberships left in that state.
    if let Some(ref seat_num) = membership.seat_number {
        if let Ok(Some(seat)) = sqlx::query_as::<_, crate::models::seat::Seat>(
            "SELECT * FROM seats WHERE seat_number = $1",
        )
        .bind(seat_num)
        .fetch_optional(&state.db)
        .await
        {
            let conflict: i64 = sqlx::query_scalar(
                r#"SELECT COUNT(*) FROM seat_bookings
                 WHERE seat_id = $1
                   AND status = 'ACTIVE'
                   AND booking_date <= $3
                   AND end_date >= $2
                   AND (shift = $4 OR shift = 'FULL_DAY' OR $4::text = 'FULL_DAY')"#,
            )
            .bind(seat.id)
            .bind(membership.start_date)
            .bind(membership.end_date)
            .bind(membership.shift.as_deref().unwrap_or("FULL_DAY"))
            .fetch_one(&state.db)
            .await
            .unwrap_or(0);

            if conflict > 0 {
                tracing::warn!(
                    "Seat {seat_num} already booked when finalizing membership {} — leaving seat unassigned; admin must reassign", membership.id
                );
                let _ = sqlx::query("UPDATE memberships SET seat_number = NULL, seat_id = NULL WHERE id = $1")
                    .bind(membership.id)
                    .execute(&state.db)
                    .await;
            } else {
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
                     ON CONFLICT (seat_id, shift, booking_date) DO UPDATE SET
                         status = 'ACTIVE', user_id = EXCLUDED.user_id,
                         membership_id = EXCLUDED.membership_id, end_date = EXCLUDED.end_date
                     WHERE seat_bookings.status != 'ACTIVE'",
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

    let state2 = state.clone();
    let info = notification::BookingInfo {
        user_id,
        membership_id: membership.id,
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        plan_name: plan.name.clone(),
        plan_type: plan.plan_type.clone(),
        seat_number: membership.seat_number.clone(),
        shift: membership.shift.clone().unwrap_or_default(),
        start_date: membership.start_date,
        end_date: membership.end_date,
        amount_paid: payment_rec.amount,
    };
    tokio::spawn(async move { notification::send_booking_confirmed(&state2, &info).await });

    let state3 = state.clone();
    let receipt_event = notification::PaymentReceiptInfo {
        user_id,
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        invoice_id: payment_rec.invoice_id.clone().unwrap_or_default(),
        amount_paid: payment_rec.amount,
        amount_pending: payment_rec.pending_amount.unwrap_or_default(),
        plan_name: plan.name.clone(),
        seat_number: membership.seat_number.clone(),
        valid_upto: Some(membership.end_date),
        payment_method: state.config.payment_gateway.clone(),
    };
    tokio::spawn(async move { notification::send_payment_receipt(&state3, &receipt_event).await });

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
        amount_paid: Some(payment_rec.amount),
        plan_price: Some(plan.price),
        created_at: membership.created_at,
        dues_amount: membership.dues_amount,
    })
}

/// Dues-payment checkout — no convenience fee, amount is whatever is owed.
pub async fn create_dues_order(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<CreateOrderResponse> {
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE user_id = $1 AND status = 'GRACE'
         ORDER BY end_date DESC LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::BadRequest("No outstanding dues found".into()))?;

    let dues = membership.dues_amount.unwrap_or_default();
    if dues <= Decimal::ZERO {
        return Err(AppError::BadRequest("No outstanding dues found".into()));
    }

    let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;

    let order = payment::create_order(
        state,
        membership.id,
        user_id,
        user.mobile.as_deref(),
        user.email.as_deref(),
        &user.name,
        dues,
    )
    .await?;

    sqlx::query(
        "UPDATE memberships SET gateway_order_id = $2, checkout_amount = $3 WHERE id = $1",
    )
    .bind(membership.id)
    .bind(&order.order_id)
    .bind(dues)
    .execute(&state.db)
    .await?;

    Ok(order)
}

pub async fn verify_and_pay_dues(
    state: &Arc<AppState>,
    user_id: Uuid,
    membership_id: Uuid,
    order_id: &str,
    payment_id: Option<&str>,
    signature: Option<&str>,
) -> crate::error::Result<MembershipWithPlan> {
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE id = $1 AND user_id = $2",
    )
    .bind(membership_id)
    .bind(user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Membership not found".into()))?;

    // Race guard: an admin may have released the seat while checkout was in flight.
    if membership.status != "GRACE" {
        return Err(AppError::BadRequest("This membership is no longer awaiting dues payment".into()));
    }
    if membership.gateway_order_id.as_deref() != Some(order_id) {
        return Err(AppError::BadRequest("Order does not match this membership".into()));
    }

    let paid = payment::verify_payment(state, order_id, payment_id, signature).await?;
    if !paid {
        return Err(AppError::BadRequest("Payment verification failed".into()));
    }

    let plan = sqlx::query_as::<_, MembershipPlan>("SELECT * FROM membership_plans WHERE id = $1")
        .bind(membership.plan_id)
        .fetch_one(&state.db)
        .await?;

    let dues_paid = membership.checkout_amount.unwrap_or(membership.dues_amount.unwrap_or_default());
    let new_end_date = membership.end_date + chrono::Duration::days(plan.duration_days as i64);

    let payment_rec = sqlx::query_as::<_, Payment>(
        "INSERT INTO payments (membership_id, user_id, amount, payment_gateway, gateway_order_id, gateway_payment_id, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'SUCCESS') RETURNING *",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(dues_paid)
    .bind(&state.config.payment_gateway)
    .bind(order_id)
    .bind(payment_id)
    .bind(ids::generate_invoice_id())
    .fetch_one(&state.db)
    .await?;

    let updated = sqlx::query_as::<_, Membership>(
        "UPDATE memberships SET status = 'ACTIVE', dues_amount = 0, end_date = $2 WHERE id = $1 RETURNING *",
    )
    .bind(membership_id)
    .bind(new_end_date)
    .fetch_one(&state.db)
    .await?;

    // Un-hold the seat from the far-future sentinel back to the real end date.
    sqlx::query(
        "UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'",
    )
    .bind(membership_id)
    .bind(new_end_date)
    .execute(&state.db)
    .await?;
    if let Some(ref shift) = updated.shift {
        crate::services::seat::invalidate_seat_cache(state, shift, membership.end_date, new_end_date).await;
    }

    let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;

    let state2 = state.clone();
    let receipt_event = notification::PaymentReceiptInfo {
        user_id,
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        invoice_id: payment_rec.invoice_id.clone().unwrap_or_default(),
        amount_paid: payment_rec.amount,
        amount_pending: Decimal::ZERO,
        plan_name: plan.name.clone(),
        seat_number: updated.seat_number.clone(),
        valid_upto: Some(new_end_date),
        payment_method: state.config.payment_gateway.clone(),
    };
    tokio::spawn(async move {
        notification::send_payment_receipt_typed(&state2, &receipt_event, "GRACE_DUES_CLEARED").await
    });

    Ok(MembershipWithPlan {
        id: updated.id,
        user_id: updated.user_id,
        plan_id: updated.plan_id,
        plan_name: plan.name,
        plan_type: plan.plan_type.clone(),
        seat_id: updated.seat_id,
        seat_number: updated.seat_number,
        shift: updated.shift,
        start_date: updated.start_date,
        end_date: updated.end_date,
        status: updated.status,
        amount_paid: Some(payment_rec.amount),
        plan_price: Some(plan.price),
        created_at: updated.created_at,
        dues_amount: updated.dues_amount,
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

/// Resolves the start date/status for a new order, and — when this is a queued
/// renewal (an ACTIVE membership not yet past its end date exists) — the
/// seat/shift to inherit from that membership, ignoring whatever the request
/// supplied. Mirrors Java's PaymentService.createOrder, which does the same
/// inheritance since the frontend's "Renew" button only ever sends `planId`.
async fn determine_start_date(
    state: &Arc<AppState>,
    user_id: Uuid,
    today: NaiveDate,
) -> crate::error::Result<(NaiveDate, String, Option<String>, Option<String>)> {
    let active: Option<(NaiveDate, Option<String>, Option<String>)> = sqlx::query_as(
        "SELECT end_date, seat_number, shift FROM memberships
         WHERE user_id = $1 AND status = 'ACTIVE' AND end_date >= $2
         ORDER BY end_date DESC LIMIT 1",
    )
    .bind(user_id)
    .bind(today)
    .fetch_optional(&state.db)
    .await?;

    if let Some((active_end, active_seat, active_shift)) = active {
        let start = active_end + chrono::Duration::days(1);
        Ok((start, "QUEUED".into(), active_shift, active_seat))
    } else {
        Ok((today, "PENDING".into(), None, None))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn today() -> NaiveDate {
        chrono::Local::now().date_naive()
    }

    #[test]
    fn grace_within_threshold_stays_grace() {
        let end = today() - chrono::Duration::days(5);
        assert_eq!(
            resolve_display_status(Some("GRACE"), Some(end), None, None, 10),
            "GRACE"
        );
    }

    #[test]
    fn grace_past_threshold_becomes_expired() {
        let end = today() - chrono::Duration::days(11);
        assert_eq!(
            resolve_display_status(Some("GRACE"), Some(end), None, None, 10),
            "EXPIRED"
        );
    }

    #[test]
    fn grace_exactly_at_threshold_stays_grace() {
        // days_overdue > grace_days is the boundary — exactly grace_days
        // overdue must still read GRACE, not EXPIRED.
        let end = today() - chrono::Duration::days(10);
        assert_eq!(
            resolve_display_status(Some("GRACE"), Some(end), None, None, 10),
            "GRACE"
        );
    }

    #[test]
    fn active_with_pending_amount_is_pending() {
        assert_eq!(
            resolve_display_status(Some("ACTIVE"), Some(today()), Some(Decimal::from(500)), None, 10),
            "PENDING"
        );
    }

    #[test]
    fn active_with_zero_pending_is_paid() {
        assert_eq!(
            resolve_display_status(Some("ACTIVE"), Some(today()), Some(Decimal::ZERO), None, 10),
            "PAID"
        );
    }

    #[test]
    fn active_with_no_pending_row_is_paid() {
        assert_eq!(
            resolve_display_status(Some("ACTIVE"), Some(today()), None, None, 10),
            "PAID"
        );
    }

    #[test]
    fn no_current_membership_and_expired_history_is_released() {
        assert_eq!(resolve_display_status(None, None, None, Some("EXPIRED"), 10), "RELEASED");
        assert_eq!(resolve_display_status(None, None, None, Some("CANCELLED"), 10), "RELEASED");
    }

    #[test]
    fn no_current_membership_and_no_history_is_new() {
        assert_eq!(resolve_display_status(None, None, None, None, 10), "NEW");
    }

    #[test]
    fn no_current_membership_with_non_terminal_history_is_new() {
        // A PENDING-excluded lookup should never surface QUEUED/PENDING here,
        // but any other unexpected value should still fail safe to NEW rather
        // than falsely claiming RELEASED.
        assert_eq!(resolve_display_status(None, None, None, Some("QUEUED"), 10), "NEW");
    }
}
