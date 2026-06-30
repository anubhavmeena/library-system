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
