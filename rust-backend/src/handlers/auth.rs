use crate::{app_state::AppState, error::AppError, response::ApiResponse, services::auth};
use axum::{
    extract::State,
    http::HeaderMap,
    Json,
};
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Deserialize)]
pub struct SendOtpRequest {
    pub contact: String,
}

#[derive(Deserialize)]
pub struct VerifyOtpRequest {
    pub contact: String,
    pub otp: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RegisterRequest {
    pub session_token: String,
    pub name: String,
    pub email: Option<String>,
    pub address: Option<String>,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoginRequest {
    pub session_token: String,
}

#[derive(Deserialize)]
pub struct AdminLoginRequest {
    pub contact: String,
    pub otp: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OtpVerifyResponse {
    pub verified: bool,
    pub session_token: String,
    pub is_new_user: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthResponse {
    pub access_token: String,
    pub token_type: String,
    pub expires_in: i64,
    pub user: crate::models::user::UserProfile,
}

fn make_auth_response(token: String, user: crate::models::user::User, expiry_ms: i64) -> AuthResponse {
    AuthResponse {
        access_token: token,
        token_type: "Bearer".into(),
        expires_in: expiry_ms,
        user: user.into(),
    }
}

pub async fn send_otp(
    State(state): State<Arc<AppState>>,
    Json(req): Json<SendOtpRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    auth::send_otp(&state, &req.contact).await?;
    Ok(ApiResponse::ok("OTP sent successfully"))
}

pub async fn verify_otp(
    State(state): State<Arc<AppState>>,
    Json(req): Json<VerifyOtpRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let (session_token, is_new_user) = auth::verify_otp(&state, &req.contact, &req.otp).await?;
    Ok(ApiResponse::success(
        "OTP verified",
        OtpVerifyResponse { verified: true, session_token, is_new_user },
    ))
}

pub async fn register(
    State(state): State<Arc<AppState>>,
    Json(req): Json<RegisterRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let (token, user) = auth::register(
        &state,
        &req.session_token,
        &req.name,
        req.email.as_deref(),
        req.address.as_deref(),
        req.gender.as_deref(),
        req.date_of_birth,
    )
    .await?;

    Ok(ApiResponse::success(
        "Registered successfully",
        make_auth_response(token, user, state.config.jwt_expiry_ms),
    ))
}

pub async fn login(
    State(state): State<Arc<AppState>>,
    Json(req): Json<LoginRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let (token, user) = auth::login(&state, &req.session_token).await?;
    Ok(ApiResponse::success(
        "Logged in successfully",
        make_auth_response(token, user, state.config.jwt_expiry_ms),
    ))
}

pub async fn admin_login(
    State(state): State<Arc<AppState>>,
    Json(req): Json<AdminLoginRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let (token, user) = auth::admin_login(&state, &req.contact, &req.otp).await?;
    Ok(ApiResponse::success(
        "Admin logged in",
        make_auth_response(token, user, state.config.jwt_expiry_ms),
    ))
}

pub async fn refresh_token(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let bearer = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .ok_or(AppError::Unauthorized)?;

    let claims = crate::services::jwt::decode_token(bearer, &state.config.jwt_secret)?;
    let user_id: uuid::Uuid = claims.sub.parse().map_err(|_| AppError::Unauthorized)?;

    let user = sqlx::query_as::<_, crate::models::user::User>(
        "SELECT * FROM users WHERE id = $1 AND is_active = true",
    )
    .bind(user_id)
    .fetch_optional(&state.db)
    .await
    .map_err(AppError::Database)?
    .ok_or(AppError::Unauthorized)?;

    let new_token = crate::services::jwt::create_token(
        user.id,
        &user.role,
        &user.name,
        user.email.as_deref(),
        user.mobile.as_deref(),
        &state.config.jwt_secret,
        state.config.jwt_expiry_ms,
    )?;

    Ok(ApiResponse::success(
        "Token refreshed",
        make_auth_response(new_token, user, state.config.jwt_expiry_ms),
    ))
}
