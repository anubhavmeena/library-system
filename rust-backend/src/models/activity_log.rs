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
    pub admin_mobile: Option<String>,
    pub action: String,
    pub entity_type: Option<String>,
    pub entity_id: Option<String>,
    pub description: String,
    pub created_at: NaiveDateTime,
    /// Students mentioned by this entry, resolved at read time for linking.
    #[sqlx(skip)]
    pub students: Vec<LinkedStudent>,
}

#[derive(Debug, Clone, Default, Serialize)]
pub struct LinkedStudent {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
}

#[derive(Debug, Serialize, FromRow)]
pub struct ActivityLogAdmin {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
}
