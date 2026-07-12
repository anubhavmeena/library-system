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
    pub channel: Option<String>,
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
    auth::send_otp(&state, &req.contact, req.channel.as_deref()).await?;
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

#[cfg(test)]
mod integration_tests {
    use crate::test_support::*;
    use tower::ServiceExt;

    // Dev mode (guaranteed by test_support's Config, never `.env`) makes the
    // OTP deterministic: always "123456", regardless of contact.
    const DEV_OTP: &str = "123456";

    #[tokio::test]
    #[ignore]
    async fn full_registration_flow_send_otp_verify_register() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let mobile = unique_mobile();

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/verify-otp", None,
            serde_json::json!({ "contact": mobile, "otp": DEV_OTP }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["verified"], true);
        assert_eq!(body["data"]["isNewUser"], true);
        let session_token = body["data"]["sessionToken"].as_str().unwrap().to_string();

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": session_token, "name": "Integration Test Student", "gender": "Female" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert!(body["data"]["accessToken"].as_str().unwrap().len() > 10);
        assert_eq!(body["data"]["user"]["name"], "Integration Test Student");
        assert_eq!(body["data"]["user"]["role"], "STUDENT");

        // registering the same (now-consumed) session token again must fail
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": session_token, "name": "Duplicate Attempt" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    #[tokio::test]
    #[ignore]
    async fn returning_user_logs_in_via_otp() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let mobile = unique_mobile();

        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        let resp = router.clone().oneshot(json_request("POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": DEV_OTP }))).await.unwrap();
        let body = body_json(resp).await;
        let session_token = body["data"]["sessionToken"].as_str().unwrap().to_string();
        router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": session_token, "name": "Returning User" }),
        )).await.unwrap();

        // second visit: send-otp -> verify-otp (isNewUser=false) -> login
        clear_otp_cooldown(&state, &mobile).await;
        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        let resp = router.clone().oneshot(json_request("POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": DEV_OTP }))).await.unwrap();
        let body = body_json(resp).await;
        assert_eq!(body["data"]["isNewUser"], false, "second verify-otp for a registered mobile must report isNewUser=false");
        let session_token2 = body["data"]["sessionToken"].as_str().unwrap().to_string();

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/login", None, serde_json::json!({ "sessionToken": session_token2 }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["user"]["name"], "Returning User");
    }

    #[tokio::test]
    #[ignore]
    async fn wrong_otp_is_rejected_and_does_not_consume_the_real_one() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let mobile = unique_mobile();

        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": "000000" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);

        // the real OTP must still work afterward
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": DEV_OTP }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
    }

    #[tokio::test]
    #[ignore]
    async fn otp_resend_cooldown_is_enforced() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let mobile = unique_mobile();

        let resp = router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let resp = router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        assert_eq!(resp.status(), 400, "an immediate second send-otp within the cooldown window must be rejected");
    }

    #[tokio::test]
    #[ignore]
    async fn invalid_or_expired_session_token_rejected_on_login_and_register() {
        let state = test_state().await;
        let router = test_router(state.clone());

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/login", None, serde_json::json!({ "sessionToken": "not-a-real-session-token" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": "not-a-real-session-token", "name": "Nobody" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    #[tokio::test]
    #[ignore]
    async fn duplicate_mobile_registration_is_rejected() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let mobile = unique_mobile();

        let register_once = |router: axum::Router, mobile: String| async move {
            router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
            let resp = router.clone().oneshot(json_request("POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": DEV_OTP }))).await.unwrap();
            let body = body_json(resp).await;
            body["data"]["sessionToken"].as_str().unwrap().to_string()
        };

        let token1 = register_once(router.clone(), mobile.clone()).await;
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": token1, "name": "First Registration" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);

        // A second OTP session for the SAME mobile, then trying to register
        // again (rather than login) must fail since the user already exists.
        clear_otp_cooldown(&state, &mobile).await;
        let token2 = register_once(router.clone(), mobile.clone()).await;
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": token2, "name": "Second Registration Attempt" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 409, "unique mobile constraint violation maps to 409 Conflict");
    }

    #[tokio::test]
    #[ignore]
    async fn admin_login_succeeds_for_seeded_admin_and_rejects_students() {
        let state = test_state().await;
        let router = test_router(state.clone());

        // This test always targets the one real, fixed admin contact, so
        // back-to-back suite runs within the 10s cooldown window (e.g.
        // quickly re-running after a failure) would otherwise collide with
        // themselves here.
        clear_otp_cooldown(&state, "9071356842").await;
        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": "9071356842" }))).await.unwrap();
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/admin/login", None,
            serde_json::json!({ "contact": "9071356842", "otp": DEV_OTP }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["user"]["role"], "ADMIN");

        // an ordinary student mobile (not in ADMIN_PHONES, not role=ADMIN) must be rejected
        let mobile = unique_mobile();
        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": DEV_OTP }))).await.unwrap();
        let body = body_json(resp).await;
        let session_token = body["data"]["sessionToken"].as_str().unwrap().to_string();
        router.clone().oneshot(json_request(
            "POST", "/api/auth/register", None,
            serde_json::json!({ "sessionToken": session_token, "name": "Not An Admin" }),
        )).await.unwrap();

        clear_otp_cooldown(&state, &mobile).await;
        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/admin/login", None,
            serde_json::json!({ "contact": mobile, "otp": DEV_OTP }),
        )).await.unwrap();
        assert_eq!(resp.status(), 403);
    }

    #[tokio::test]
    #[ignore]
    async fn refresh_token_issues_a_new_valid_token() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Refresh Target").await;

        let resp = router.clone().oneshot(json_request("POST", "/api/auth/refresh", Some(&token), serde_json::json!({}))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        let new_token = body["data"]["accessToken"].as_str().unwrap().to_string();
        // JWT claims (incl. `iat`/`exp`) are second-granularity, so a refresh
        // issued within the same wall-clock second as the original token can
        // legitimately be byte-identical -- only assert it decodes correctly,
        // not that it necessarily differs.
        assert!(!new_token.is_empty());

        // the new token must itself be usable
        let resp = router.clone().oneshot(get_request("/api/users/me", Some(&new_token))).await.unwrap();
        assert_eq!(resp.status(), 200);
    }

    #[tokio::test]
    #[ignore]
    async fn refresh_rejects_missing_or_garbage_bearer() {
        let state = test_state().await;
        let router = test_router(state.clone());

        let resp = router.clone().oneshot(json_request("POST", "/api/auth/refresh", None, serde_json::json!({}))).await.unwrap();
        assert_eq!(resp.status(), 401);

        let resp = router.clone().oneshot(json_request("POST", "/api/auth/refresh", Some("garbage-token"), serde_json::json!({}))).await.unwrap();
        assert_eq!(resp.status(), 401);
    }

    #[tokio::test]
    #[ignore]
    async fn inactive_user_cannot_login() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (id, _token) = create_test_user(&state, "STUDENT", "Soon Inactive").await;
        sqlx::query("UPDATE users SET is_active = false WHERE id = $1").bind(id).execute(&state.db).await.unwrap();
        let mobile: String = sqlx::query_scalar("SELECT mobile FROM users WHERE id = $1").bind(id).fetch_one(&state.db).await.unwrap();

        router.clone().oneshot(json_request("POST", "/api/auth/send-otp", None, serde_json::json!({ "contact": mobile }))).await.unwrap();
        let resp = router.clone().oneshot(json_request("POST", "/api/auth/verify-otp", None, serde_json::json!({ "contact": mobile, "otp": DEV_OTP }))).await.unwrap();
        let body = body_json(resp).await;
        let session_token = body["data"]["sessionToken"].as_str().unwrap().to_string();

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/auth/login", None, serde_json::json!({ "sessionToken": session_token }),
        )).await.unwrap();
        assert_eq!(resp.status(), 403, "login() explicitly rejects an inactive user with Forbidden");
    }
}

