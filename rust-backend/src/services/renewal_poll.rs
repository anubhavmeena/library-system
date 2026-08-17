use crate::{
    app_state::AppState, error::AppError, models::renewal_poll::RenewalPollEntry, models::user::User,
    services::notification,
};
use chrono::NaiveDate;
use std::sync::Arc;
use uuid::Uuid;

/// Entry point for the cron job (see main.rs::start_scheduler).
pub async fn run_renewal_poll_job(state: Arc<AppState>) {
    tracing::info!("Running renewal-poll scheduler job");
    match send_scheduled_renewal_polls(&state).await {
        Ok(n) => tracing::info!("Sent {n} renewal poll(s)"),
        Err(e) => tracing::error!("Renewal poll job error: {e}"),
    }
}

/// Sends the seat_renewal_confirmation poll to every ACTIVE membership whose
/// end_date falls within the next 3 days, exactly once per (membership_id,
/// end_date) pair (see the migration comment for why membership_id alone
/// isn't enough). A windowed BETWEEN rather than an exact `= today + 3`
/// match is deliberate: it mirrors why the 7-day reminder job uses a window
/// rather than exact-day matching, so a missed/delayed cron tick (e.g. a
/// service restart) doesn't silently skip a student's poll. Only one
/// checkpoint exists here ("3 days before"), so no days_left branching is
/// needed like the 7-and-3-day text reminder has — the NOT EXISTS gate alone
/// prevents duplicate sends on subsequent days within the window.
async fn send_scheduled_renewal_polls(state: &Arc<AppState>) -> crate::error::Result<i64> {
    let rows: Vec<(Uuid, Uuid, String, Option<String>, NaiveDate)> = sqlx::query_as(
        "SELECT m.id, m.user_id, u.name, u.mobile, m.end_date
         FROM memberships m JOIN users u ON u.id = m.user_id
         WHERE m.status = 'ACTIVE'
           AND m.end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '3 days'
           AND NOT EXISTS (
               SELECT 1 FROM renewal_polls rp
               WHERE rp.membership_id = m.id AND rp.end_date = m.end_date
           )",
    )
    .fetch_all(&state.db)
    .await?;

    let mut count = 0i64;
    for (membership_id, user_id, name, mobile, end_date) in rows {
        // No mobile on file: skip both the send AND the insert, so this
        // membership is retried on every subsequent day within the 3-day
        // window (rather than getting permanently gated out) until a
        // mobile number is added or the window closes.
        let Some(mobile) = mobile else {
            tracing::warn!("Skipping renewal poll for membership {membership_id}: no mobile on file");
            continue;
        };

        // Insert the "sent" marker BEFORE the async network call completes
        // (mirrors send_scheduled_renewal_reminders' ordering in
        // services/admin.rs) so a second cron tick that starts before this
        // send finishes can't double-send.
        let poll_id: Uuid = sqlx::query_scalar(
            "INSERT INTO renewal_polls (membership_id, user_id, end_date) VALUES ($1, $2, $3) RETURNING id",
        )
        .bind(membership_id)
        .bind(user_id)
        .bind(end_date)
        .fetch_one(&state.db)
        .await?;

        let s = state.clone();
        let n = name.clone();
        let m = mobile.clone();
        tokio::spawn(async move {
            if let Some(wamid) = notification::send_renewal_poll(&s, &m, &n).await {
                let _ = sqlx::query("UPDATE renewal_polls SET wa_message_id = $1 WHERE id = $2")
                    .bind(&wamid)
                    .bind(poll_id)
                    .execute(&s.db)
                    .await;
            }
        });
        count += 1;
    }

    Ok(count)
}

/// Called from the webhook handler when an inbound quick-reply button tap
/// arrives. Best-effort: logs and swallows errors rather than propagating,
/// same rationale as activity_log::log_activity — a DB hiccup here must
/// never cause the webhook handler to fail/retry against Meta. Naturally
/// idempotent against Meta's at-least-once webhook delivery: re-running the
/// same update with the same values is a no-op.
pub async fn record_poll_response(state: &Arc<AppState>, wa_message_id: &str, response: &str) {
    let result = sqlx::query(
        "UPDATE renewal_polls SET response = $1, responded_at = NOW() WHERE wa_message_id = $2",
    )
    .bind(response)
    .bind(wa_message_id)
    .execute(&state.db)
    .await;

    match result {
        Ok(r) if r.rows_affected() == 0 => {
            tracing::warn!("Renewal poll webhook: no row found for wa_message_id={wa_message_id}");
        }
        Err(e) => tracing::error!("Failed to record renewal poll response: {e}"),
        _ => {}
    }
}

/// `size = None` returns every row (the "All" page-size option), same
/// convention as activity_log::list_activity_logs.
pub async fn list_renewal_polls(
    state: &Arc<AppState>,
    page: i64,
    size: Option<i64>,
) -> crate::error::Result<(Vec<RenewalPollEntry>, i64)> {
    let total: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM renewal_polls")
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)?;

    let base = r#"SELECT rp.id, rp.membership_id, rp.user_id, u.name, u.mobile, u.email,
                          rp.end_date, rp.sent_at, rp.response, rp.responded_at
                   FROM renewal_polls rp JOIN users u ON u.id = rp.user_id
                   ORDER BY rp.sent_at DESC"#;

    let logs = match size {
        Some(size) => sqlx::query_as::<_, RenewalPollEntry>(&format!("{base} LIMIT $1 OFFSET $2"))
            .bind(size)
            .bind(page * size)
            .fetch_all(&state.db)
            .await
            .map_err(AppError::Database)?,
        None => sqlx::query_as::<_, RenewalPollEntry>(base)
            .fetch_all(&state.db)
            .await
            .map_err(AppError::Database)?,
    };

    Ok((logs, total))
}

/// Manual admin resend for a specific poll row. Reuses the SAME
/// `renewal_polls` row (resetting sent_at/response/responded_at to "pending"
/// again) rather than inserting a new one — a fresh INSERT would violate the
/// (membership_id, end_date) semantics the scheduler's NOT EXISTS gate
/// relies on, since neither value changes on a resend.
pub async fn resend_poll(state: &Arc<AppState>, poll_id: Uuid) -> crate::error::Result<()> {
    let row: Option<(Uuid, String, Option<String>)> = sqlx::query_as(
        "SELECT rp.user_id, u.name, u.mobile
         FROM renewal_polls rp JOIN users u ON u.id = rp.user_id
         WHERE rp.id = $1",
    )
    .bind(poll_id)
    .fetch_optional(&state.db)
    .await
    .map_err(AppError::Database)?;

    let Some((_user_id, name, mobile)) = row else {
        return Err(AppError::NotFound(format!("Renewal poll {poll_id} not found")));
    };
    let mobile = mobile.ok_or_else(|| AppError::BadRequest("Student has no mobile on file".into()))?;

    sqlx::query(
        "UPDATE renewal_polls SET sent_at = NOW(), wa_message_id = NULL, response = NULL, responded_at = NULL WHERE id = $1",
    )
    .bind(poll_id)
    .execute(&state.db)
    .await
    .map_err(AppError::Database)?;

    if let Some(wamid) = notification::send_renewal_poll(state, &mobile, &name).await {
        sqlx::query("UPDATE renewal_polls SET wa_message_id = $1 WHERE id = $2")
            .bind(&wamid)
            .bind(poll_id)
            .execute(&state.db)
            .await
            .map_err(AppError::Database)?;
    }

    Ok(())
}

/// On-demand admin send for one specific student — the "Send Renewal Poll"
/// action on the admin Students page. Unlike the scheduler, this always
/// inserts a fresh `renewal_polls` row regardless of days-left or whether
/// one was already sent for this cycle (same "explicit admin action
/// bypasses gating" convention as send_renewal_reminders bypassing
/// reminder_sent) — an admin deliberately triggering a send wants a record
/// of that send, not a silent no-op because the scheduler already fired.
pub async fn send_individual_poll(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<()> {
    let user = sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await
        .map_err(AppError::Database)?
        .ok_or_else(|| AppError::NotFound("User not found".into()))?;
    let mobile = user
        .mobile
        .clone()
        .ok_or_else(|| AppError::BadRequest("Student has no mobile number on file".into()))?;

    // Only ACTIVE — a GRACE membership is already overdue and has its own
    // separate dues-clearing flow (grace-dues reminders), not "continue for
    // next month" renewal semantics.
    let membership: Option<(Uuid, NaiveDate)> = sqlx::query_as(
        "SELECT id, end_date FROM memberships WHERE user_id = $1 AND status = 'ACTIVE' ORDER BY end_date DESC LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await
    .map_err(AppError::Database)?;
    let (membership_id, end_date) = membership
        .ok_or_else(|| AppError::BadRequest("Student has no active membership to send a renewal poll for".into()))?;

    let poll_id: Uuid = sqlx::query_scalar(
        "INSERT INTO renewal_polls (membership_id, user_id, end_date) VALUES ($1, $2, $3) RETURNING id",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(end_date)
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)?;

    if let Some(wamid) = notification::send_renewal_poll(state, &mobile, &user.name).await {
        sqlx::query("UPDATE renewal_polls SET wa_message_id = $1 WHERE id = $2")
            .bind(&wamid)
            .bind(poll_id)
            .execute(&state.db)
            .await
            .map_err(AppError::Database)?;
    }

    Ok(())
}
