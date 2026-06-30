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
#[serde(rename_all = "camelCase")]
pub struct Feedback {
    pub id: Uuid,
    pub user_id: Uuid,
    #[sqlx(rename = "type")]
    #[serde(rename = "type")]
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

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDateTime;
    use uuid::Uuid;

    fn sample_user() -> User {
        User {
            id: Uuid::new_v4(),
            mobile: Some("9876543210".to_string()),
            email: Some("alice@example.com".to_string()),
            name: "Alice".to_string(),
            address: Some("123 Main St".to_string()),
            father_name: Some("Bob".to_string()),
            photo_url: Some("/uploads/photo.jpg".to_string()),
            aadhaar_url: Some("/uploads/aadhaar.pdf".to_string()),
            date_of_birth: NaiveDate::from_ymd_opt(1995, 6, 15),
            gender: Some("Female".to_string()),
            password_hash: Some("$2b$hashed_password_value".to_string()),
            is_active: true,
            role: "STUDENT".to_string(),
            created_at: NaiveDateTime::parse_from_str("2025-01-01 10:00:00", "%Y-%m-%d %H:%M:%S").unwrap(),
            updated_at: None,
        }
    }

    #[test]
    fn user_profile_from_user_maps_all_fields() {
        let user = sample_user();
        let id = user.id;
        let profile = UserProfile::from(user);
        assert_eq!(profile.id, id);
        assert_eq!(profile.name, "Alice");
        assert_eq!(profile.mobile, Some("9876543210".to_string()));
        assert_eq!(profile.email, Some("alice@example.com".to_string()));
        assert_eq!(profile.address, Some("123 Main St".to_string()));
        assert_eq!(profile.father_name, Some("Bob".to_string()));
        assert_eq!(profile.photo_url, Some("/uploads/photo.jpg".to_string()));
        assert_eq!(profile.aadhaar_url, Some("/uploads/aadhaar.pdf".to_string()));
        assert_eq!(profile.gender, Some("Female".to_string()));
        assert!(profile.is_active);
        assert_eq!(profile.role, "STUDENT");
    }

    #[test]
    fn user_serialization_excludes_password_hash() {
        let user = sample_user();
        let json = serde_json::to_string(&user).unwrap();
        assert!(!json.contains("password_hash"));
        assert!(!json.contains("hashed_password_value"));
    }

    #[test]
    fn user_profile_does_not_expose_password_hash() {
        let user = sample_user();
        let profile = UserProfile::from(user);
        let json = serde_json::to_string(&profile).unwrap();
        assert!(!json.contains("password_hash"));
        assert!(!json.contains("hashed_password_value"));
    }

    #[test]
    fn user_profile_null_optionals_preserved() {
        let mut user = sample_user();
        user.mobile = None;
        user.email = None;
        user.photo_url = None;
        user.aadhaar_url = None;
        let profile = UserProfile::from(user);
        assert_eq!(profile.mobile, None);
        assert_eq!(profile.email, None);
        assert_eq!(profile.photo_url, None);
        assert_eq!(profile.aadhaar_url, None);
    }

    #[test]
    fn user_profile_serializes_camel_case_keys() {
        let user = sample_user();
        let profile = UserProfile::from(user);
        let json = serde_json::to_string(&profile).unwrap();
        assert!(json.contains("fatherName"), "Expected camelCase fatherName");
        assert!(json.contains("photoUrl"), "Expected camelCase photoUrl");
        assert!(json.contains("isActive"), "Expected camelCase isActive");
        assert!(json.contains("createdAt"), "Expected camelCase createdAt");
        assert!(!json.contains("father_name"), "Should not contain snake_case");
        assert!(!json.contains("photo_url"), "Should not contain snake_case");
    }

    #[test]
    fn update_profile_request_partial_fields_deserialized() {
        let json = r#"{"name": "Bob", "address": "New Address"}"#;
        let req: UpdateProfileRequest = serde_json::from_str(json).unwrap();
        assert_eq!(req.name, Some("Bob".to_string()));
        assert_eq!(req.address, Some("New Address".to_string()));
        assert_eq!(req.gender, None);
        assert_eq!(req.email, None);
        assert_eq!(req.father_name, None);
    }

    #[test]
    fn update_profile_request_empty_object_all_none() {
        let req: UpdateProfileRequest = serde_json::from_str("{}").unwrap();
        assert_eq!(req.name, None);
        assert_eq!(req.address, None);
        assert_eq!(req.gender, None);
        assert_eq!(req.email, None);
        assert_eq!(req.father_name, None);
        assert_eq!(req.date_of_birth, None);
    }

    #[test]
    fn submit_feedback_request_uses_type_alias() {
        let json = r#"{"type": "COMPLAINT", "subject": "Issue", "description": "Service problem"}"#;
        let req: SubmitFeedbackRequest = serde_json::from_str(json).unwrap();
        assert_eq!(req.feedback_type, "COMPLAINT");
        assert_eq!(req.subject, "Issue");
        assert_eq!(req.description, "Service problem");
    }
}
