use chrono::{NaiveDate, NaiveDateTime};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct User {
    pub id: Uuid,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub name: String,
    pub address: Option<String>,
    pub father_name: Option<String>,
    pub photo_url: Option<String>,
    pub aadhaar_url: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub gender: Option<String>,
    #[serde(skip_serializing)]
    pub password_hash: Option<String>,
    pub is_active: bool,
    pub role: String,
    pub created_at: NaiveDateTime,
    pub updated_at: Option<NaiveDateTime>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UserProfile {
    pub id: Uuid,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub name: String,
    pub address: Option<String>,
    pub father_name: Option<String>,
    pub photo_url: Option<String>,
    pub aadhaar_url: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub gender: Option<String>,
    pub is_active: bool,
    pub role: String,
    pub created_at: NaiveDateTime,
}

impl From<User> for UserProfile {
    fn from(u: User) -> Self {
        Self {
            id: u.id,
            mobile: u.mobile,
            email: u.email,
            name: u.name,
            address: u.address,
            father_name: u.father_name,
            photo_url: u.photo_url,
            aadhaar_url: u.aadhaar_url,
            date_of_birth: u.date_of_birth,
            gender: u.gender,
            is_active: u.is_active,
            role: u.role,
            created_at: u.created_at,
        }
    }
}

#[derive(Debug, Serialize, Deserialize, sqlx::FromRow)]
pub struct Feedback {
    pub id: Uuid,
    pub user_id: Uuid,
    #[sqlx(rename = "type")]
    pub feedback_type: String,
    pub subject: String,
    pub description: String,
    pub status: String,
    pub admin_notes: Option<String>,
    pub created_at: Option<NaiveDateTime>,
    pub updated_at: Option<NaiveDateTime>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateProfileRequest {
    pub name: Option<String>,
    pub address: Option<String>,
    pub father_name: Option<String>,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub email: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct SubmitFeedbackRequest {
    #[serde(alias = "type")]
    pub feedback_type: String,
    pub subject: String,
    pub description: String,
}
