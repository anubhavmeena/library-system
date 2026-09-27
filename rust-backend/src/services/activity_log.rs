use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AuthUser,
    models::activity_log::{ActivityLogAdmin, ActivityLogEntry, LinkedStudent},
};
use chrono::NaiveDate;
use regex::Regex;
use sqlx::{Postgres, QueryBuilder};
use std::{
    collections::HashMap,
    sync::{Arc, LazyLock},
};
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
        r#"INSERT INTO activity_log (admin_id, admin_name, admin_mobile, action, entity_type, entity_id, description)
           VALUES ($1, $2, $3, $4, $5, $6, $7)"#,
    )
    .bind(admin.user_id)
    .bind(&admin.name)
    .bind(&admin.mobile)
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

/// Optional filters for `list_activity_logs`. `from`/`to` are inclusive IST
/// calendar dates — `created_at` is stored as naive UTC, so it's shifted by
/// +05:30 before taking the date.
#[derive(Debug, Default)]
pub struct ActivityLogFilters {
    pub search: Option<String>,
    pub admin_id: Option<Uuid>,
    pub action: Option<String>,
    pub from: Option<NaiveDate>,
    pub to: Option<NaiveDate>,
}

fn push_filters<'a>(qb: &mut QueryBuilder<'a, Postgres>, f: &'a ActivityLogFilters) {
    qb.push(" WHERE TRUE");
    if let Some(search) = f.search.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        let pattern = format!("%{}%", search.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_"));
        qb.push(" AND (description ILIKE ").push_bind(pattern.clone())
            .push(" OR admin_name ILIKE ").push_bind(pattern.clone())
            .push(" OR COALESCE(admin_mobile, '') ILIKE ").push_bind(pattern.clone())
            .push(" OR action ILIKE ").push_bind(pattern)
            .push(")");
    }
    if let Some(admin_id) = f.admin_id {
        qb.push(" AND admin_id = ").push_bind(admin_id);
    }
    if let Some(action) = f.action.as_deref() {
        qb.push(" AND action = ").push_bind(action);
    }
    if let Some(from) = f.from {
        qb.push(" AND (created_at + INTERVAL '5 hours 30 minutes')::date >= ").push_bind(from);
    }
    if let Some(to) = f.to {
        qb.push(" AND (created_at + INTERVAL '5 hours 30 minutes')::date <= ").push_bind(to);
    }
}

/// `size = None` returns every row (the "All" page-size option) — the table
/// is admin-panel-only traffic, so an unbounded scan here is fine.
pub async fn list_activity_logs(
    state: &Arc<AppState>,
    filters: &ActivityLogFilters,
    page: i64,
    size: Option<i64>,
) -> crate::error::Result<(Vec<ActivityLogEntry>, i64)> {
    let mut count_qb = QueryBuilder::<Postgres>::new("SELECT COUNT(*) FROM activity_log");
    push_filters(&mut count_qb, filters);
    let total: i64 = count_qb
        .build_query_scalar()
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)?;

    let mut qb = QueryBuilder::<Postgres>::new(
        "SELECT id, admin_id, admin_name, admin_mobile, action, entity_type, entity_id, description, created_at FROM activity_log",
    );
    push_filters(&mut qb, filters);
    qb.push(" ORDER BY created_at DESC");
    if let Some(size) = size {
        qb.push(" LIMIT ").push_bind(size).push(" OFFSET ").push_bind(page * size);
    }
    let mut logs: Vec<ActivityLogEntry> = qb
        .build_query_as()
        .fetch_all(&state.db)
        .await
        .map_err(AppError::Database)?;

    attach_students(state, &mut logs).await;
    Ok((logs, total))
}

/// Distinct admins and actions present in the log, for the table's filter
/// dropdowns.
pub async fn list_filter_options(
    state: &Arc<AppState>,
) -> crate::error::Result<(Vec<ActivityLogAdmin>, Vec<String>)> {
    let admins = sqlx::query_as::<_, ActivityLogAdmin>(
        r#"SELECT DISTINCT ON (admin_id) admin_id AS id, admin_name AS name, admin_mobile AS mobile
           FROM activity_log ORDER BY admin_id, created_at DESC"#,
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)?;
    let actions: Vec<String> = sqlx::query_scalar("SELECT DISTINCT action FROM activity_log ORDER BY action")
        .fetch_all(&state.db)
        .await
        .map_err(AppError::Database)?;
    Ok((admins, actions))
}

static MOBILE_IN_PARENS: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\((\+?\d{10,13})\)").unwrap());

/// Resolves the students each log line mentions so the frontend can link
/// their names to the profile page. Two sources: the log's own entity id
/// (student / membership rows), and any "Name (mobile)" label in the
/// description — which also catches the second student in a SWAP_SEAT line.
/// Best-effort like `log_activity`: a lookup failure just means no links.
async fn attach_students(state: &Arc<AppState>, logs: &mut [ActivityLogEntry]) {
    let mut user_ids: Vec<Uuid> = Vec::new();
    let mut membership_ids: Vec<Uuid> = Vec::new();
    let mut mobiles: Vec<String> = Vec::new();
    for log in logs.iter() {
        if let Some(id) = log.entity_id.as_deref().and_then(|s| Uuid::parse_str(s).ok()) {
            match log.entity_type.as_deref() {
                Some("student") => user_ids.push(id),
                Some("membership") => membership_ids.push(id),
                _ => {}
            }
        }
        mobiles.extend(MOBILE_IN_PARENS.captures_iter(&log.description).map(|c| c[1].to_string()));
    }
    if user_ids.is_empty() && membership_ids.is_empty() && mobiles.is_empty() {
        return;
    }

    let rows: Vec<(Option<Uuid>, Uuid, String, Option<String>)> = match sqlx::query_as(
        r#"SELECT NULL::uuid, u.id, u.name, u.mobile FROM users u
             WHERE u.id = ANY($1) OR u.mobile = ANY($3)
           UNION ALL
           SELECT m.id, u.id, u.name, u.mobile FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.id = ANY($2)"#,
    )
    .bind(&user_ids)
    .bind(&membership_ids)
    .bind(&mobiles)
    .fetch_all(&state.db)
    .await
    {
        Ok(rows) => rows,
        Err(e) => {
            tracing::warn!("Failed to resolve students for activity logs: {e}");
            return;
        }
    };

    let mut by_user: HashMap<Uuid, LinkedStudent> = HashMap::new();
    let mut by_membership: HashMap<Uuid, Uuid> = HashMap::new();
    let mut by_mobile: HashMap<String, Uuid> = HashMap::new();
    for (membership_id, id, name, mobile) in rows {
        if let Some(mid) = membership_id {
            by_membership.insert(mid, id);
        }
        if let Some(m) = &mobile {
            by_mobile.insert(m.clone(), id);
        }
        by_user.entry(id).or_insert(LinkedStudent { id, name, mobile });
    }

    for log in logs.iter_mut() {
        let mut ids: Vec<Uuid> = Vec::new();
        if let Some(id) = log.entity_id.as_deref().and_then(|s| Uuid::parse_str(s).ok()) {
            match log.entity_type.as_deref() {
                Some("student") => ids.push(id),
                Some("membership") => ids.extend(by_membership.get(&id).copied()),
                _ => {}
            }
        }
        for c in MOBILE_IN_PARENS.captures_iter(&log.description) {
            ids.extend(by_mobile.get(&c[1]).copied());
        }
        for id in ids {
            if log.students.iter().any(|s| s.id == id) {
                continue;
            }
            if let Some(s) = by_user.get(&id) {
                log.students.push(s.clone());
            }
        }
    }
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
