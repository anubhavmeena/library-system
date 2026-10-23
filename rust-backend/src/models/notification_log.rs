use chrono::NaiveDateTime;
use serde::Serialize;
use sqlx::FromRow;
use uuid::Uuid;

/// Most text columns are nullable because the table predates this backend
/// (the Java notification-service's schema left them nullable).
#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct NotificationLogEntry {
    pub id: Uuid,
    pub user_id: Option<Uuid>,
    pub student_name: Option<String>,
    pub student_mobile: Option<String>,
    pub recipient: Option<String>,
    pub message: Option<String>,
    pub event: Option<String>,
    pub channel: Option<String>,
    pub status: Option<String>,
    pub error_message: Option<String>,
    /// AUTO | MANUAL
    pub trigger_type: String,
    /// Admin name for MANUAL sends.
    pub sent_by: Option<String>,
    pub sent_at: Option<NaiveDateTime>,
}
