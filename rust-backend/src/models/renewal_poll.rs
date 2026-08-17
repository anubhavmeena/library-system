use chrono::{NaiveDate, NaiveDateTime};
use serde::Serialize;
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct RenewalPollEntry {
    pub id: Uuid,
    pub membership_id: Uuid,
    pub user_id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub end_date: NaiveDate,
    pub sent_at: NaiveDateTime,
    pub response: Option<String>,
    pub responded_at: Option<NaiveDateTime>,
    pub membership_status: String,
    pub seat_number: Option<String>,
}
