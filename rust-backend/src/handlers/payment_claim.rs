use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AdminUser,
    models::payment_claim::{CreatePayLinkRequest, ReviewPaymentClaimRequest},
    response::ApiResponse,
    services::payment_claim as svc,
};
use axum::{
    extract::{Multipart, Path, Query, State},
    Json,
};
use std::sync::Arc;
use uuid::Uuid;

/// Public — no AuthUser/AdminUser. Identity comes entirely from the short
/// linkId minted into the reminder's /pay link (see upi_pay::create_pay_link).
pub async fn submit_claim(
    State(state): State<Arc<AppState>>,
    mut multipart: Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let mut link_id: Option<String> = None;
    let mut file: Option<(String, Option<String>, Vec<u8>)> = None;

    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        match field.name().unwrap_or("") {
            "linkId" => link_id = Some(field.text().await.map_err(|e| AppError::BadRequest(e.to_string()))?),
            "file" => {
                let filename = field.file_name().unwrap_or("screenshot.jpg").to_string();
                let content_type = field.content_type().map(|s| s.to_string());
                let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;
                file = Some((filename, content_type, data.to_vec()));
            }
            _ => {}
        }
    }

    let link_id = link_id.ok_or_else(|| AppError::BadRequest("linkId is required".into()))?;
    let (filename, content_type, data) = file.ok_or_else(|| AppError::BadRequest("Screenshot file is required".into()))?;

    let claim = svc::submit_claim(&state, &link_id, &filename, content_type.as_deref(), &data).await?;

    Ok(ApiResponse::success("Payment verification submitted", claim))
}

/// Public — backs PayRedirectPage.jsx, which resolves the short id into the
/// UPI params it needs to build the upi://pay deep link client-side.
pub async fn get_pay_link(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let info = svc::get_pay_link_info(&state, &id).await?;
    Ok(ApiResponse::success("Payment link resolved", info))
}

/// Admin-only — backs the ad-hoc "Send Payment Request" button on Create
/// Membership. Returns a short link only; the caller builds/sends the
/// WhatsApp message itself via the existing generic message endpoint.
pub async fn create_pay_link(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<CreatePayLinkRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let link = svc::create_ad_hoc_pay_link(&state, req.student_id, req.amount).await?;
    Ok(ApiResponse::success("Payment link created", serde_json::json!({ "link": link })))
}

pub async fn list_claims(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<std::collections::HashMap<String, String>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let status = q.get("status").map(|s| s.as_str());
    let claims = svc::list_claims(&state, status).await?;
    Ok(ApiResponse::success("Payment claims retrieved", claims))
}

pub async fn review_claim(
    State(state): State<Arc<AppState>>,
    admin: AdminUser,
    Path(id): Path<Uuid>,
    Json(req): Json<ReviewPaymentClaimRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let claim = svc::review_claim(&state, id, admin.0.user_id, &req.status).await?;
    Ok(ApiResponse::success("Payment claim reviewed", claim))
}
