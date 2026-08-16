use chrono::NaiveDateTime;
use serde::Serialize;
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct ActivityLogEntry {
    pub id: Uuid,
    pub admin_id: Uuid,
    pub admin_name: String,
    pub action: String,
    pub entity_type: Option<String>,
    pub entity_id: Option<String>,
    pub description: String,
    pub created_at: NaiveDateTime,
}
