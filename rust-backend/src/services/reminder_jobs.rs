//! Tracks bulk-reminder sends (grace-dues, pending-fee) that now run as
//! background tasks instead of blocking the triggering HTTP request — see
//! `services::admin::send_grace_dues_reminders` /
//! `send_pending_fee_reminders`. A row here is the only record of whether
//! those background sends actually succeeded, since the response that
//! kicked them off returned long before they finished.

use crate::app_state::AppState;
use crate::models::admin::ReminderJob;
use std::sync::Arc;
use uuid::Uuid;

pub const GRACE_DUES: &str = "GRACE_DUES";
pub const PENDING_FEE: &str = "PENDING_FEE";

/// Starts a job row. `total_count == 0` is completed immediately — there are
/// no background sends coming to close it out otherwise.
pub async fn create_job(
    state: &Arc<AppState>,
    job_type: &str,
    total_count: i64,
) -> crate::error::Result<Uuid> {
    let status = if total_count == 0 { "COMPLETED" } else { "RUNNING" };
    let row: (Uuid,) = sqlx::query_as(
        "INSERT INTO reminder_jobs (job_type, total_count, status, completed_at)
         VALUES ($1, $2, $3, CASE WHEN $3 = 'COMPLETED' THEN NOW() END)
         RETURNING id",
    )
    .bind(job_type)
    .bind(total_count as i32)
    .bind(status)
    .fetch_one(&state.db)
    .await?;
    Ok(row.0)
}

/// Records one recipient's delivery outcome and, if this was the last one
/// outstanding, closes the job out as COMPLETED. Each call is a single
/// atomic UPDATE, so concurrent background tasks recording results for the
/// same job can't race each other into double-completing it.
pub async fn record_result(state: &Arc<AppState>, job_id: Uuid, success: bool) {
    let result: Result<(i32, i32, i32), sqlx::Error> = sqlx::query_as(
        "UPDATE reminder_jobs
         SET success_count = success_count + CASE WHEN $1 THEN 1 ELSE 0 END,
             failure_count = failure_count + CASE WHEN $1 THEN 0 ELSE 1 END
         WHERE id = $2
         RETURNING success_count, failure_count, total_count",
    )
    .bind(success)
    .bind(job_id)
    .fetch_one(&state.db)
    .await;

    let (success_count, failure_count, total_count) = match result {
        Ok(row) => row,
        Err(e) => {
            tracing::error!("Failed to record reminder job {job_id} result: {e}");
            return;
        }
    };

    if success_count + failure_count >= total_count {
        if let Err(e) = sqlx::query(
            "UPDATE reminder_jobs SET status = 'COMPLETED', completed_at = NOW()
             WHERE id = $1 AND status = 'RUNNING'",
        )
        .bind(job_id)
        .execute(&state.db)
        .await
        {
            tracing::error!("Failed to complete reminder job {job_id}: {e}");
        }
    }
}

/// Most recent job of each type, for the admin panel's "last sent" status —
/// `DISTINCT ON` picks the newest row per `job_type` in one query.
pub async fn get_latest_jobs(state: &Arc<AppState>) -> crate::error::Result<Vec<ReminderJob>> {
    sqlx::query_as::<_, ReminderJob>(
        "SELECT DISTINCT ON (job_type) id, job_type, total_count, success_count,
                failure_count, status, started_at, completed_at
         FROM reminder_jobs
         ORDER BY job_type, started_at DESC",
    )
    .fetch_all(&state.db)
    .await
    .map_err(crate::error::AppError::Database)
}
