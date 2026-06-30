use axum::{http::StatusCode, Json};
use chrono::Utc;
use serde::Serialize;

#[derive(Serialize)]
pub struct ApiResponse<T: Serialize> {
    pub success: bool,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    pub timestamp: String,
}

impl<T: Serialize> ApiResponse<T> {
    pub fn success(message: impl Into<String>, data: T) -> (StatusCode, Json<Self>) {
        (
            StatusCode::OK,
            Json(Self {
                success: true,
                message: message.into(),
                data: Some(data),
                timestamp: Utc::now().to_rfc3339(),
            }),
        )
    }
}

impl ApiResponse<()> {
    pub fn ok(message: impl Into<String>) -> (StatusCode, Json<Self>) {
        (
            StatusCode::OK,
            Json(Self {
                success: true,
                message: message.into(),
                data: None,
                timestamp: Utc::now().to_rfc3339(),
            }),
        )
    }

    pub fn created(message: impl Into<String>) -> (StatusCode, Json<Self>) {
        (
            StatusCode::CREATED,
            Json(Self {
                success: true,
                message: message.into(),
                data: None,
                timestamp: Utc::now().to_rfc3339(),
            }),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{http::StatusCode, Json};

    #[test]
    fn ok_returns_200_with_success_true() {
        let (status, Json(body)) = ApiResponse::ok("deleted");
        assert_eq!(status, StatusCode::OK);
        assert!(body.success);
    }

    #[test]
    fn ok_message_is_preserved() {
        let (_, Json(body)) = ApiResponse::ok("User deleted successfully");
        assert_eq!(body.message, "User deleted successfully");
    }

    #[test]
    fn ok_data_is_absent() {
        let (_, Json(body)) = ApiResponse::ok("done");
        assert!(body.data.is_none());
    }

    #[test]
    fn created_returns_201_with_success_true() {
        let (status, Json(body)) = ApiResponse::created("registered");
        assert_eq!(status, StatusCode::CREATED);
        assert!(body.success);
    }

    #[test]
    fn created_message_is_preserved() {
        let (_, Json(body)) = ApiResponse::created("User registered");
        assert_eq!(body.message, "User registered");
    }

    #[test]
    fn success_returns_200_with_data() {
        let (status, Json(body)) = ApiResponse::success("OTP sent", 42u32);
        assert_eq!(status, StatusCode::OK);
        assert!(body.success);
        assert_eq!(body.data, Some(42u32));
    }

    #[test]
    fn success_message_is_preserved() {
        let (_, Json(body)) = ApiResponse::success("OTP sent", "token_value");
        assert_eq!(body.message, "OTP sent");
    }

    #[test]
    fn success_with_string_data() {
        let (_, Json(body)) = ApiResponse::success("ok", "session_token_123");
        assert_eq!(body.data, Some("session_token_123"));
    }

    #[test]
    fn timestamp_is_non_empty() {
        let (_, Json(body)) = ApiResponse::ok("test");
        assert!(!body.timestamp.is_empty());
    }

    #[test]
    fn timestamp_is_valid_rfc3339() {
        let (_, Json(body)) = ApiResponse::ok("test");
        chrono::DateTime::parse_from_rfc3339(&body.timestamp)
            .expect("timestamp must be valid RFC3339");
    }
}
