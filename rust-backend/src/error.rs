use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("Not found: {0}")]
    NotFound(String),
    #[error("Bad request: {0}")]
    BadRequest(String),
    #[error("Unauthorized")]
    Unauthorized,
    #[error("Forbidden")]
    Forbidden,
    #[error("Conflict: {0}")]
    Conflict(String),
    #[error("Internal error: {0}")]
    Internal(String),
    #[error(transparent)]
    Database(#[from] sqlx::Error),
    #[error(transparent)]
    Redis(#[from] redis::RedisError),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, message) = match &self {
            AppError::NotFound(m) => (StatusCode::NOT_FOUND, m.clone()),
            AppError::BadRequest(m) => (StatusCode::BAD_REQUEST, m.clone()),
            AppError::Unauthorized => (StatusCode::UNAUTHORIZED, "Unauthorized".into()),
            AppError::Forbidden => (StatusCode::FORBIDDEN, "Forbidden".into()),
            AppError::Conflict(m) => (StatusCode::CONFLICT, m.clone()),
            AppError::Internal(m) => (StatusCode::INTERNAL_SERVER_ERROR, m.clone()),
            AppError::Database(e) => {
                tracing::error!("DB error: {e}");
                (StatusCode::INTERNAL_SERVER_ERROR, "Database error".into())
            }
            AppError::Redis(e) => {
                tracing::error!("Redis error: {e}");
                (StatusCode::INTERNAL_SERVER_ERROR, "Cache error".into())
            }
        };

        let body = json!({
            "success": false,
            "message": message,
            "data": null,
            "timestamp": chrono::Utc::now().to_rfc3339()
        });

        (status, Json(body)).into_response()
    }
}

pub type Result<T> = std::result::Result<T, AppError>;

#[cfg(test)]
mod tests {
    use super::*;
    use axum::response::IntoResponse;

    fn status(err: AppError) -> StatusCode {
        err.into_response().status()
    }

    #[test]
    fn not_found_is_404() {
        assert_eq!(status(AppError::NotFound("thing".into())), StatusCode::NOT_FOUND);
    }

    #[test]
    fn bad_request_is_400() {
        assert_eq!(status(AppError::BadRequest("invalid input".into())), StatusCode::BAD_REQUEST);
    }

    #[test]
    fn unauthorized_is_401() {
        assert_eq!(status(AppError::Unauthorized), StatusCode::UNAUTHORIZED);
    }

    #[test]
    fn forbidden_is_403() {
        assert_eq!(status(AppError::Forbidden), StatusCode::FORBIDDEN);
    }

    #[test]
    fn conflict_is_409() {
        assert_eq!(status(AppError::Conflict("duplicate".into())), StatusCode::CONFLICT);
    }

    #[test]
    fn internal_is_500() {
        assert_eq!(status(AppError::Internal("something crashed".into())), StatusCode::INTERNAL_SERVER_ERROR);
    }

    #[test]
    fn not_found_display_includes_message() {
        let e = AppError::NotFound("seat A1".into());
        assert!(e.to_string().contains("seat A1"));
    }

    #[test]
    fn bad_request_display_includes_message() {
        let e = AppError::BadRequest("OTP expired".into());
        assert!(e.to_string().contains("OTP expired"));
    }

    #[test]
    fn conflict_display_includes_message() {
        let e = AppError::Conflict("Seat A1 is already booked".into());
        assert!(e.to_string().contains("Seat A1 is already booked"));
    }

    #[test]
    fn internal_display_includes_message() {
        let e = AppError::Internal("payment gateway timeout".into());
        assert!(e.to_string().contains("payment gateway timeout"));
    }

    #[test]
    fn unauthorized_display_is_unauthorized() {
        assert!(AppError::Unauthorized.to_string().contains("Unauthorized"));
    }

    #[test]
    fn forbidden_display_is_forbidden() {
        assert!(AppError::Forbidden.to_string().contains("Forbidden"));
    }
}
