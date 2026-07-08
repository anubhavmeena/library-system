use crate::{
    app_state::AppState, middleware::AdminUser, models::admin::ReplyRequest, response::ApiResponse,
    services::mailbox as svc,
};
use axum::extract::{Path, State};
use std::sync::Arc;

pub async fn list_messages(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let messages = svc::list_messages(state.config.clone()).await?;
    Ok(ApiResponse::success("Inbox retrieved", messages))
}

pub async fn get_message(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(message_number): Path<u32>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let message = svc::get_message(state.config.clone(), message_number).await?;
    Ok(ApiResponse::success("Message retrieved", message))
}

pub async fn delete_message(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(message_number): Path<u32>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::delete_message(state.config.clone(), message_number).await?;
    Ok(ApiResponse::ok("Message deleted"))
}

pub async fn reply(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(message_number): Path<u32>,
    axum::Json(req): axum::Json<ReplyRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::reply_to_message(state.config.clone(), message_number, req.body).await?;
    Ok(ApiResponse::ok("Reply sent"))
}
