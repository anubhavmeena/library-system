use crate::{
    app_state::AppState,
    error::AppError,
    models::membership::{
        CreateOrderResponse, Membership, MembershipPlan, MembershipWithPlan, Payment, PlanWithFee,
    },
    services::{ids, notification, payment, settings},
};
use chrono::NaiveDate;
use redis::AsyncCommands;
use rust_decimal::Decimal;
use std::sync::Arc;
use uuid::Uuid;

/// `/api/plans` barely changes (admin-edited convenience fee aside) and is
/// hit on every page load — cached whole-response like `seat::get_availability`.
const PLANS_CACHE_KEY: &str = "plans:active";
const PLANS_CACHE_TTL_SECS: u64 = 300;

/// Per-user, short-lived — this reflects live membership/payment state, so
/// unlike the plans cache the TTL is the primary safety net (kept short
/// deliberately) rather than something invalidation is expected to fully
/// cover; see `invalidate_status_cache` for the one high-value spot
/// (the student's own payment verification) it's paired with.
const STATUS_CACHE_TTL_SECS: u64 = 15;

fn status_cache_key(user_id: Uuid) -> String {
    format!("membership:status:{user_id}")
}

/// Clears a user's cached display status. Call after any action that changes
/// what `get_my_display_status` would return for them. Currently wired into
/// the student's own payment-verification paths (verify_payment,
/// verify_and_pay_dues, verify_and_pay_pending) — the highest-traffic moment
/// someone immediately re-checks their status. Admin-driven mutations
/// (clear dues, change status, release seat, etc.) rely on the short TTL
/// above instead, rather than being wired individually.
pub async fn invalidate_status_cache(state: &Arc<AppState>, user_id: Uuid) {
    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        let _: Result<(), _> = conn.del(&status_cache_key(user_id)).await;
    }
}

pub async fn list_active_plans(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<PlanWithFee>> {
    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        if let Ok(cached) = conn.get::<_, String>(PLANS_CACHE_KEY).await {
            if let Ok(plans) = serde_json::from_str::<Vec<PlanWithFee>>(&cached) {
                return Ok(plans);
            }
        }
    }

    let plans = sqlx::query_as::<_, MembershipPlan>(
        "SELECT * FROM membership_plans WHERE is_active = true ORDER BY price",
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)?;

    let convenience_fee = settings::get_app_settings(state).await?.convenience_fee;
    let result: Vec<PlanWithFee> = plans
        .into_iter()
        .map(|plan| PlanWithFee { plan, convenience_fee })
        .collect();

    if let Ok(json) = serde_json::to_string(&result) {
        if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
            let _ = conn.set_ex::<_, _, ()>(PLANS_CACHE_KEY, &json, PLANS_CACHE_TTL_SECS).await;
        }
    }

    Ok(result)
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
           m.dues_amount, pf.pending_amount
    FROM memberships m
    JOIN membership_plans p ON p.id = m.plan_id
    LEFT JOIN payments pay ON pay.membership_id = m.id AND pay.status = 'SUCCESS'
    LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(pending_amount), 0)::numeric AS pending_amount
        FROM payments WHERE user_id = m.user_id AND status = 'SUCCESS'
    ) pf ON true";

type MembershipRow = (
    Uuid, Uuid, Uuid, String, String, Option<Uuid>, Option<String>,
    Option<String>, NaiveDate, NaiveDate, String, Option<Decimal>, Option<chrono::NaiveDateTime>,
    Option<Decimal>, Option<Decimal>, Option<Decimal>,
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
        pending_amount: r.15,
    }
}

/// Same aggregate as `MEMBERSHIP_WITH_PLAN_SELECT`'s `pf` lateral join, for the
/// call sites that build `MembershipWithPlan` by hand instead of via `map_row`.
async fn sum_pending_amount(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<Decimal> {
    let sum: Decimal = sqlx::query_scalar(
        "SELECT COALESCE(SUM(pending_amount), 0) FROM payments WHERE user_id = $1 AND status = 'SUCCESS'",
    )
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;
    Ok(sum)
}

/// 2.5% surcharge for clearing dues/pending amount via the payment gateway,
/// rounded to the nearest whole rupee (matches this app's whole-rupee pricing
/// convention — see migrations/004_plan_price_whole_rupees.sql). Distinct from
/// the flat, admin-configured `app_settings.convenience_fee` added to regular
/// plan purchases — this one is a fixed percentage, specific to the self-serve
/// dues/pending-amount checkouts.
fn with_convenience_fee(amount: Decimal) -> Decimal {
    (amount * Decimal::new(1025, 3)).round()
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
            if days_overdue > grace_days as i64 { "GRACE_OVERDUE" } else { "GRACE" }
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
    let cache_key = status_cache_key(user_id);
    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        if let Ok(cached) = conn.get::<_, String>(&cache_key).await {
            return Ok(cached);
        }
    }

    let current = find_current_membership(state, user_id).await?;

    // pending_amount, latest_ever status, and grace_days are three independent
    // lookups with no data dependency on each other, previously issued as three
    // separate sequential pool checkouts — under connection-pool contention this
    // handler had to win the pool queue 3x (4x counting find_current_membership
    // above) instead of once, making it disproportionately likely to stall under
    // load compared to single-query sibling endpoints. Batched into one round trip
    // via independent scalar subqueries (each still NULL-safe on no match, exactly
    // as the original three queries were).
    let (pending_amount, latest_ever, grace_days): (Option<Decimal>, Option<String>, i32) =
        sqlx::query_as(
            "SELECT
                (SELECT pending_amount FROM payments
                 WHERE membership_id = $1 AND status = 'SUCCESS'
                 ORDER BY created_at DESC LIMIT 1) AS pending_amount,
                (SELECT status FROM memberships
                 WHERE user_id = $2 AND status != 'PENDING'
                 ORDER BY created_at DESC LIMIT 1) AS latest_ever_status,
                COALESCE(
                    (SELECT grace_days FROM app_settings WHERE id = 1),
                    $3
                ) AS grace_days",
        )
        .bind(current.as_ref().map(|m| m.id))
        .bind(user_id)
        .bind(settings::DEFAULT_GRACE_DAYS)
        .fetch_one(&state.db)
        .await?;

    let status = resolve_display_status(
        current.as_ref().map(|m| m.status.as_str()),
        current.as_ref().map(|m| m.end_date),
        pending_amount,
        latest_ever.as_deref(),
        grace_days,
    ).to_string();

    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        let _ = conn.set_ex::<_, _, ()>(&cache_key, &status, STATUS_CACHE_TTL_SECS).await;
    }

    Ok(status)
}

pub async fn create_order(
    state: &Arc<AppState>,
    user_id: Uuid,
    plan_id: Uuid,
    shift: Option<&str>,
    seat_number: Option<&str>,
    coupon_code: Option<&str>,
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

    // Coupons only apply to a fresh booking, never a queued renewal — both
    // create_order and create_cash_membership share this same insert path, so
    // this guard is the only thing standing between a crafted API request and
    // a renewal silently getting a discount it was never meant to have.
    if membership_status == "QUEUED" && coupon_code.is_some() {
        return Err(AppError::BadRequest("Coupons only apply to new bookings, not renewals".into()));
    }

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

    // Discount applies to the plan price only — the convenience fee is added
    // after, unaffected, matching the admin-configured surcharge's role as a
    // flat gateway-handling cost rather than part of the "price" being discounted.
    let (applied_coupon_code, discount_amount) = if let Some(code) = coupon_code {
        let coupon = crate::services::coupon::validate_coupon_code(state, code).await?;
        let discount = (plan.price * Decimal::from(coupon.discount_percent) / Decimal::from(100)).round();
        (Some(coupon.code), discount)
    } else {
        (None, Decimal::ZERO)
    };

    let convenience_fee = settings::get_app_settings(state).await?.convenience_fee;
    let charge_amount = (plan.price - discount_amount) + convenience_fee;

    let mut order = payment::create_order(
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
        "UPDATE memberships SET gateway_order_id = $2, checkout_amount = $3, coupon_code = $4, discount_amount = $5 WHERE id = $1",
    )
    .bind(membership.id)
    .bind(&order.order_id)
    .bind(charge_amount)
    .bind(&applied_coupon_code)
    .bind(if applied_coupon_code.is_some() { Some(discount_amount) } else { None })
    .execute(&state.db)
    .await?;

    if applied_coupon_code.is_some() {
        order.discount_amount = Some(discount_amount);
    }

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

    // A QUEUED renewal must stay QUEUED even once paid — it only becomes ACTIVE
    // when mark_expired_and_start_grace promotes it after the *current*
    // membership's end_date actually passes (see that function's QUEUED-sibling
    // lookup). Flipping it to ACTIVE here would leave two ACTIVE rows for the
    // same user at once, and once the original later expires there is no more
    // QUEUED sibling for that job to find — it would wrongly push the
    // already-superseded original into GRACE with a fresh dues charge despite
    // the student having already paid for their renewal. The frontend already
    // expects this: membershipSlice's verifyPayment.fulfilled branches on
    // `status === 'QUEUED'` to file the result under `state.queued` rather
    // than `state.current`.
    let target_status = if membership.status == "QUEUED" { "QUEUED" } else { "ACTIVE" };

    let paid = payment::verify_payment(state, order_id, payment_id, signature).await?;
    if !paid {
        return Err(AppError::BadRequest("Payment verification failed".into()));
    }

    let charge_amount = membership.checkout_amount.unwrap_or_default();

    let payment_rec = sqlx::query_as::<_, Payment>(
        "INSERT INTO payments (membership_id, user_id, amount, payment_gateway, gateway_order_id, gateway_payment_id, invoice_id, status, coupon_code, discount_amount)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'SUCCESS', $8, $9) RETURNING *",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(charge_amount)
    .bind(&state.config.payment_gateway)
    .bind(order_id)
    .bind(payment_id)
    .bind(ids::generate_invoice_id())
    .bind(&membership.coupon_code)
    .bind(membership.discount_amount)
    .fetch_one(&state.db)
    .await?;

    let mut membership = sqlx::query_as::<_, Membership>(
        "UPDATE memberships SET status = $3 WHERE id = $1 AND user_id = $2 RETURNING *",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(target_status)
    .fetch_one(&state.db)
    .await?;
    invalidate_status_cache(state, user_id).await;

    // Auto-assign seat if seat_number is present. The payment has already been charged
    // by this point, so a conflict here can't fail the request outright — instead we
    // decline the phantom claim (clear seat_number back to NULL) rather than silently
    // marking the membership as holding a seat someone else actually has; the admin
    // "Students Needing a Seat" tool picks up memberships left in that state.
    //
    // `membership` is kept in sync with whichever branch below actually runs —
    // it's what the response at the bottom of this function is built from, and
    // previously stayed stale (still showing the originally-requested seat)
    // even when the conflict branch had just nulled it out in the DB.
    if let Some(seat_num) = membership.seat_number.clone() {
        if let Ok(Some(seat)) = sqlx::query_as::<_, crate::models::seat::Seat>(
            "SELECT * FROM seats WHERE seat_number = $1",
        )
        .bind(&seat_num)
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
                membership.seat_number = None;
                membership.seat_id = None;
            } else {
                let _ = sqlx::query(
                    "UPDATE memberships SET seat_id = $2 WHERE id = $1",
                )
                .bind(membership.id)
                .bind(seat.id)
                .execute(&state.db)
                .await;
                membership.seat_id = Some(seat.id);

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

    let pending_amount = sum_pending_amount(state, user_id).await?;

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
        pending_amount: Some(pending_amount),
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
    let charge_amount = with_convenience_fee(dues);

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
        charge_amount,
    )
    .await?;

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
    invalidate_status_cache(state, user_id).await;

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

    let pending_amount = sum_pending_amount(state, user_id).await?;

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
        pending_amount: Some(pending_amount),
    })
}

/// Pending-amount checkout — clears the sum of the user's outstanding
/// payments.pending_amount rows (across every membership they've ever had),
/// the same aggregate `admin::get_pending_fees`/`clear_pending_fees` use, so
/// self-clearing here resolves the same total an admin would see (plus the
/// gateway convenience fee, same as dues).
pub async fn create_pending_order(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<CreateOrderResponse> {
    let rows = sqlx::query_as::<_, (Uuid, Decimal)>(
        "SELECT membership_id, pending_amount FROM payments
         WHERE user_id = $1 AND status = 'SUCCESS' AND pending_amount > 0
         ORDER BY created_at ASC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await?;

    let total_pending: Decimal = rows.iter().map(|(_, p)| *p).sum();
    if total_pending <= Decimal::ZERO {
        return Err(AppError::BadRequest("No outstanding pending amount found".into()));
    }
    // Anchor the order on the oldest pending row's membership — same anchor
    // admin::clear_pending_fees uses (rows.first()) — purely for correlating
    // the in-flight gateway checkout; not necessarily the student's current
    // membership.
    let membership_id = rows[0].0;
    let charge_amount = with_convenience_fee(total_pending);

    let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;

    let order = payment::create_order(
        state,
        membership_id,
        user_id,
        user.mobile.as_deref(),
        user.email.as_deref(),
        &user.name,
        charge_amount,
    )
    .await?;

    sqlx::query("UPDATE memberships SET gateway_order_id = $2, checkout_amount = $3 WHERE id = $1")
        .bind(membership_id)
        .bind(&order.order_id)
        .bind(charge_amount)
        .execute(&state.db)
        .await?;

    Ok(order)
}

pub async fn verify_and_pay_pending(
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

    let rows = sqlx::query_as::<_, (Uuid, Decimal)>(
        "SELECT id, pending_amount FROM payments
         WHERE user_id = $1 AND status = 'SUCCESS' AND pending_amount > 0
         ORDER BY created_at ASC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await?;
    let total_pending: Decimal = rows.iter().map(|(_, p)| *p).sum();
    let charge_amount = membership.checkout_amount.unwrap_or_default();
    if total_pending <= Decimal::ZERO || charge_amount <= Decimal::ZERO {
        return Err(AppError::BadRequest("No outstanding pending amount found".into()));
    }
    // This flow never does partial payments — the order was always created for
    // the full then-current total_pending (plus fee), so a verified payment
    // clears the whole outstanding balance, whatever it is right now.
    let remainder = Decimal::ZERO;

    for (payment_row_id, _) in &rows {
        sqlx::query("UPDATE payments SET pending_amount = 0, updated_at = NOW() WHERE id = $1")
            .bind(payment_row_id)
            .execute(&state.db)
            .await?;
    }
    invalidate_status_cache(state, user_id).await;

    let invoice_id = ids::generate_invoice_id();
    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, gateway_order_id, gateway_payment_id, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'SUCCESS')",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(charge_amount)
    .bind(remainder)
    .bind(&state.config.payment_gateway)
    .bind(order_id)
    .bind(payment_id)
    .bind(&invoice_id)
    .execute(&state.db)
    .await?;

    let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;
    let current = find_current_membership(state, user_id).await?;

    let pf_setting = settings::setting_for(state, "PENDING_FEE_CLEARED").await;
    if pf_setting.send_to_student {
        let msg = settings::apply_hindi(
            &format!("Your pending library fee of Rs.{charge_amount:.0} has been cleared. Thank you! - Target Zone Library Team"),
            &pf_setting, true,
        );
        notification::send_direct_message(state, user.mobile.as_deref(), user.email.as_deref(), &msg).await;
    }
    if pf_setting.send_to_admin {
        let seat = current.as_ref().and_then(|m| m.seat_number.as_deref()).unwrap_or("—");
        let admin_msg = format!(
            "Pending Fee Cleared (self-service)\nStudent: {}\nSeat: {seat}\nAmount: Rs.{charge_amount:.0}",
            user.name
        );
        if !state.config.admin_whatsapp.is_empty() {
            notification::send_direct_message(state, Some(&state.config.admin_whatsapp.clone()), None, &admin_msg).await;
        }
        notification::send_direct_message(state, None, Some(&state.config.admin_email.clone()), &admin_msg).await;
    }

    let s = state.clone();
    let receipt_event = notification::PaymentReceiptInfo {
        user_id,
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        invoice_id,
        amount_paid: charge_amount,
        amount_pending: remainder,
        plan_name: current.as_ref().map(|m| m.plan_name.clone()).unwrap_or_default(),
        seat_number: current.as_ref().and_then(|m| m.seat_number.clone()),
        valid_upto: current.as_ref().map(|m| m.end_date),
        payment_method: state.config.payment_gateway.clone(),
    };
    tokio::spawn(async move {
        notification::send_payment_receipt_typed(&s, &receipt_event, "PENDING_FEE_CLEARED").await
    });

    current.ok_or_else(|| AppError::Internal("Membership not found after clearing pending amount".into()))
}

pub async fn get_payment_history(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<Payment>> {
    // ₹0 rows (e.g. a dues-correction placeholder) aren't real payments —
    // nothing to show the student. Matches Java's getUserPayments filter.
    sqlx::query_as::<_, Payment>(
        "SELECT * FROM payments WHERE user_id = $1 AND amount > 0 ORDER BY created_at DESC",
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
    fn convenience_fee_rounds_down_below_midpoint() {
        // 80 * 1.025 = 82.00 exactly
        assert_eq!(with_convenience_fee(Decimal::from(80)), Decimal::from(82));
        // 50 * 1.025 = 51.25 -> rounds to 51
        assert_eq!(with_convenience_fee(Decimal::from(50)), Decimal::from(51));
    }

    #[test]
    fn convenience_fee_rounds_up_above_midpoint() {
        // 33 * 1.025 = 33.825 -> rounds to 34
        assert_eq!(with_convenience_fee(Decimal::from(33)), Decimal::from(34));
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
    fn grace_past_threshold_becomes_overdue() {
        let end = today() - chrono::Duration::days(11);
        assert_eq!(
            resolve_display_status(Some("GRACE"), Some(end), None, None, 10),
            "GRACE_OVERDUE"
        );
    }

    #[test]
    fn grace_exactly_at_threshold_stays_grace() {
        // days_overdue > grace_days is the boundary — exactly grace_days
        // overdue must still read GRACE, not GRACE_OVERDUE.
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
