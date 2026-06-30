use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AuthUser,
    response::ApiResponse,
    services::{idcard, membership as svc, notification},
};
use axum::{
    body::Body,
    extract::State,
    http::{header, StatusCode},
    response::Response,
};
use bytes::Bytes;
use std::sync::Arc;

pub async fn list_plans(
    State(state): State<Arc<AppState>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let plans = svc::list_active_plans(&state).await?;
    Ok(ApiResponse::success("Plans retrieved", plans))
}

pub async fn get_my_membership(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::get_active_membership(&state, user.user_id).await?;
    Ok(ApiResponse::success("Membership retrieved", membership))
}

pub async fn get_my_all_memberships(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let memberships = svc::get_all_memberships(&state, user.user_id).await?;
    Ok(ApiResponse::success("Memberships retrieved", memberships))
}

pub async fn get_my_queued_membership(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::get_queued_membership(&state, user.user_id).await?;
    Ok(ApiResponse::success("Queued membership retrieved", membership))
}

pub async fn call_admin(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::get_active_membership(&state, user.user_id)
        .await?
        .ok_or_else(|| AppError::BadRequest("No active membership found".into()))?;

    let seat_number = membership
        .seat_number
        .ok_or_else(|| AppError::BadRequest("No seat assigned to your membership".into()))?;

    let state2 = state.clone();
    let name = user.name.clone();
    let seat = seat_number.clone();
    tokio::spawn(async move {
        notification::send_seat_assistance(&state2, &name, &seat).await;
    });

    Ok(ApiResponse::success("Admin has been notified", ()))
}

pub async fn download_id_card(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<Response> {
    let pdf = idcard::generate(&state, user.user_id).await?;
    let response = Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, "application/pdf")
        .header(
            header::CONTENT_DISPOSITION,
            r#"attachment; filename="id-card.pdf""#,
        )
        .body(Body::from(Bytes::from(pdf)))
        .map_err(|e| AppError::Internal(e.to_string()))?;
    Ok(response)
}
