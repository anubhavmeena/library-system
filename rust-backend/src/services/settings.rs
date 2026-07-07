use crate::{
    app_state::AppState,
    error::AppError,
    models::settings::{
        catalog_entry, AppSettings, NotificationCatalogEntry, NotificationSettingDto,
        NotificationSettingRow, SaveAppSettingsRequest, UpdateNotificationSettingRequest,
        NOTIFICATION_CATALOG,
    },
};
use std::sync::Arc;

pub const DEFAULT_GRACE_DAYS: i32 = 10;

// ── App settings (singleton row) ─────────────────────────────────────────────

pub async fn get_app_settings(state: &Arc<AppState>) -> crate::error::Result<AppSettings> {
    if let Some(row) = sqlx::query_as::<_, AppSettings>("SELECT * FROM app_settings WHERE id = 1")
        .fetch_optional(&state.db)
        .await?
    {
        return Ok(row);
    }

    let inserted = sqlx::query_as::<_, AppSettings>(
        "INSERT INTO app_settings (id, grace_days, convenience_fee, water_tanker_rate)
         VALUES (1, $1, 0, 0)
         ON CONFLICT (id) DO NOTHING
         RETURNING *",
    )
    .bind(DEFAULT_GRACE_DAYS)
    .fetch_optional(&state.db)
    .await?;

    if let Some(row) = inserted {
        return Ok(row);
    }

    // Lost a race with a concurrent first-read that inserted the row first.
    sqlx::query_as::<_, AppSettings>("SELECT * FROM app_settings WHERE id = 1")
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)
}

pub async fn save_app_settings(
    state: &Arc<AppState>,
    req: &SaveAppSettingsRequest,
) -> crate::error::Result<AppSettings> {
    sqlx::query_as::<_, AppSettings>(
        "INSERT INTO app_settings (id, wifi_name, wifi_password, grace_days, convenience_fee, water_tanker_rate, updated_at)
         VALUES (1, $1, $2, $3, $4, $5, NOW())
         ON CONFLICT (id) DO UPDATE SET
             wifi_name = $1, wifi_password = $2, grace_days = $3,
             convenience_fee = $4, water_tanker_rate = $5, updated_at = NOW()
         RETURNING *",
    )
    .bind(&req.wifi_name)
    .bind(&req.wifi_password)
    .bind(req.grace_days)
    .bind(req.convenience_fee)
    .bind(req.water_tanker_rate)
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)
}

/// Current grace-period length in days, falling back to the default if the
/// settings row doesn't exist yet — used by status resolution, which must
/// never fail just because nobody has opened the admin Settings page yet.
pub async fn grace_days(state: &Arc<AppState>) -> i32 {
    sqlx::query_scalar::<_, i32>("SELECT grace_days FROM app_settings WHERE id = 1")
        .fetch_optional(&state.db)
        .await
        .ok()
        .flatten()
        .unwrap_or(DEFAULT_GRACE_DAYS)
}

// ── Notification settings (one row per key) ──────────────────────────────────

fn to_dto(entry: &NotificationCatalogEntry, row: Option<&NotificationSettingRow>) -> NotificationSettingDto {
    NotificationSettingDto {
        notification_key: entry.key.to_string(),
        send_to_student: row.map(|r| r.send_to_student).unwrap_or(entry.default_send_to_student),
        send_to_admin: row.map(|r| r.send_to_admin).unwrap_or(entry.default_send_to_admin),
        hindi_enabled: row.map(|r| r.hindi_enabled).unwrap_or(false),
        hindi_text_student: row
            .and_then(|r| r.hindi_text_student.clone())
            .or_else(|| entry.default_hindi_text_student.map(str::to_string)),
        hindi_text_admin: row
            .and_then(|r| r.hindi_text_admin.clone())
            .or_else(|| entry.default_hindi_text_admin.map(str::to_string)),
        student_editable: entry.student_editable,
        admin_editable: entry.admin_editable,
        hindi_editable: entry.hindi_editable,
    }
}

pub async fn get_notification_settings(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<NotificationSettingDto>> {
    let rows = sqlx::query_as::<_, NotificationSettingRow>("SELECT * FROM notification_settings")
        .fetch_all(&state.db)
        .await;

    // Fail-open: if the table read itself fails, still return sensible
    // catalog defaults rather than 500ing the Settings page.
    let rows = match rows {
        Ok(r) => r,
        Err(e) => {
            tracing::error!("notification_settings read failed, using catalog defaults: {e}");
            return Ok(NOTIFICATION_CATALOG.iter().map(|e| to_dto(e, None)).collect());
        }
    };

    let mut out = Vec::with_capacity(NOTIFICATION_CATALOG.len());
    for entry in NOTIFICATION_CATALOG {
        let existing = rows.iter().find(|r| r.notification_key == entry.key);
        if existing.is_none() {
            // Lazily create the row on first read, seeding any bilingual default.
            let _ = sqlx::query(
                "INSERT INTO notification_settings
                    (notification_key, send_to_student, send_to_admin, hindi_enabled, hindi_text_student, hindi_text_admin)
                 VALUES ($1, $2, $3, false, $4, $5)
                 ON CONFLICT (notification_key) DO NOTHING",
            )
            .bind(entry.key)
            .bind(entry.default_send_to_student)
            .bind(entry.default_send_to_admin)
            .bind(entry.default_hindi_text_student)
            .bind(entry.default_hindi_text_admin)
            .execute(&state.db)
            .await;
        }
        out.push(to_dto(entry, existing));
    }
    Ok(out)
}

pub async fn update_notification_setting(
    state: &Arc<AppState>,
    key: &str,
    req: &UpdateNotificationSettingRequest,
) -> crate::error::Result<NotificationSettingDto> {
    let entry = catalog_entry(key)
        .ok_or_else(|| AppError::NotFound(format!("Unknown notification key: {key}")))?;

    let existing = sqlx::query_as::<_, NotificationSettingRow>(
        "SELECT * FROM notification_settings WHERE notification_key = $1",
    )
    .bind(key)
    .fetch_optional(&state.db)
    .await?;

    let current_student = existing.as_ref().map(|r| r.send_to_student).unwrap_or(entry.default_send_to_student);
    let current_admin = existing.as_ref().map(|r| r.send_to_admin).unwrap_or(entry.default_send_to_admin);

    if !entry.student_editable && req.send_to_student != current_student {
        return Err(AppError::BadRequest(format!("{key}: sendToStudent is not editable")));
    }
    if !entry.admin_editable && req.send_to_admin != current_admin {
        return Err(AppError::BadRequest(format!("{key}: sendToAdmin is not editable")));
    }
    if !entry.hindi_editable && req.hindi_enabled {
        return Err(AppError::BadRequest(format!("{key}: Hindi is not supported for this notification")));
    }

    let row = sqlx::query_as::<_, NotificationSettingRow>(
        "INSERT INTO notification_settings
            (notification_key, send_to_student, send_to_admin, hindi_enabled, hindi_text_student, hindi_text_admin, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, NOW())
         ON CONFLICT (notification_key) DO UPDATE SET
             send_to_student = $2, send_to_admin = $3, hindi_enabled = $4,
             hindi_text_student = $5, hindi_text_admin = $6, updated_at = NOW()
         RETURNING *",
    )
    .bind(key)
    .bind(req.send_to_student)
    .bind(req.send_to_admin)
    .bind(req.hindi_enabled)
    .bind(&req.hindi_text_student)
    .bind(&req.hindi_text_admin)
    .fetch_one(&state.db)
    .await?;

    Ok(to_dto(entry, Some(&row)))
}

/// `english + hindi` when Hindi is enabled and non-blank for this setting,
/// else just the English text — never replaces the English copy.
pub fn apply_hindi(english: &str, dto: &NotificationSettingDto, for_student: bool) -> String {
    let hindi = if for_student { &dto.hindi_text_student } else { &dto.hindi_text_admin };
    match hindi {
        Some(h) if dto.hindi_enabled && !h.trim().is_empty() => format!("{english}\n\n{h}"),
        _ => english.to_string(),
    }
}

/// Look up one notification setting by key, falling back to catalog defaults
/// (fail-open) if the row can't be read — mirrors Java's FAIL_OPEN_DEFAULTS.
pub async fn setting_for(state: &Arc<AppState>, key: &str) -> NotificationSettingDto {
    let entry = catalog_entry(key).unwrap_or(&NOTIFICATION_CATALOG[0]);
    let row = sqlx::query_as::<_, NotificationSettingRow>(
        "SELECT * FROM notification_settings WHERE notification_key = $1",
    )
    .bind(key)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten();
    to_dto(entry, row.as_ref())
}
