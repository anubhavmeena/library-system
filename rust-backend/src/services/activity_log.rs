use crate::{app_state::AppState, error::AppError, middleware::AuthUser, models::activity_log::ActivityLogEntry};
use std::sync::Arc;
use uuid::Uuid;

/// Records one admin action. Deliberately best-effort: a logging failure
/// (e.g. a transient DB hiccup) must never roll back or fail the admin
/// action it's describing, so errors are logged and swallowed rather than
/// propagated via `?`.
pub async fn log_activity(
    state: &Arc<AppState>,
    admin: &AuthUser,
    action: &str,
    entity_type: &str,
    entity_id: Option<String>,
    description: impl Into<String>,
) {
    let description = description.into();
    let result = sqlx::query(
        r#"INSERT INTO activity_log (admin_id, admin_name, action, entity_type, entity_id, description)
           VALUES ($1, $2, $3, $4, $5, $6)"#,
    )
    .bind(admin.user_id)
    .bind(&admin.name)
    .bind(action)
    .bind(entity_type)
    .bind(&entity_id)
    .bind(&description)
    .execute(&state.db)
    .await;

    if let Err(e) = result {
        tracing::error!("Failed to record activity log for action '{action}': {e}");
    }
}

/// `size = None` returns every row (the "All" page-size option) — the table
/// is admin-panel-only traffic, so an unbounded scan here is fine.
pub async fn list_activity_logs(
    state: &Arc<AppState>,
    page: i64,
    size: Option<i64>,
) -> crate::error::Result<(Vec<ActivityLogEntry>, i64)> {
    let total: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM activity_log")
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)?;

    let logs = match size {
        Some(size) => sqlx::query_as::<_, ActivityLogEntry>(
            r#"SELECT id, admin_id, admin_name, action, entity_type, entity_id, description, created_at
               FROM activity_log ORDER BY created_at DESC LIMIT $1 OFFSET $2"#,
        )
        .bind(size)
        .bind(page * size)
        .fetch_all(&state.db)
        .await
        .map_err(AppError::Database)?,
        None => sqlx::query_as::<_, ActivityLogEntry>(
            r#"SELECT id, admin_id, admin_name, action, entity_type, entity_id, description, created_at
               FROM activity_log ORDER BY created_at DESC"#,
        )
        .fetch_all(&state.db)
        .await
        .map_err(AppError::Database)?,
    };

    Ok((logs, total))
}

/// "Name (mobile)" for a human-readable log description, falling back to the
/// bare id if the user has since been deleted or has no mobile on file.
pub async fn user_label(state: &Arc<AppState>, user_id: Uuid) -> String {
    let row: Option<(String, Option<String>)> =
        sqlx::query_as("SELECT name, mobile FROM users WHERE id = $1")
            .bind(user_id)
            .fetch_optional(&state.db)
            .await
            .ok()
            .flatten();
    match row {
        Some((name, Some(mobile))) => format!("{name} ({mobile})"),
        Some((name, None)) => name,
        None => user_id.to_string(),
    }
}

/// Same idea as `user_label`, resolved through a membership id instead.
pub async fn membership_label(state: &Arc<AppState>, membership_id: Uuid) -> String {
    let row: Option<(String, Option<String>)> = sqlx::query_as(
        "SELECT u.name, u.mobile FROM memberships m JOIN users u ON u.id = m.user_id WHERE m.id = $1",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten();
    match row {
        Some((name, Some(mobile))) => format!("{name} ({mobile})"),
        Some((name, None)) => name,
        None => membership_id.to_string(),
    }
}
