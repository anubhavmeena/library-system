use crate::{
    app_state::AppState, middleware::AdminUser, models::admin::ReplyRequest, response::ApiResponse,
    services::{activity_log as alog, mailbox as svc},
};
use axum::{
    extract::{Path, State},
    http::header,
};
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
    admin: AdminUser,
    Path(message_number): Path<u32>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::delete_message(state.config.clone(), message_number).await?;
    alog::log_activity(&state, &admin.0, "DELETE_INBOX_MESSAGE", "inbox_message", Some(message_number.to_string()),
        format!("Deleted inbox message #{message_number}")).await;
    Ok(ApiResponse::ok("Message deleted"))
}

pub async fn get_attachment(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path((message_number, index)): Path<(u32, usize)>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let (filename, content_type, bytes) =
        svc::get_attachment(state.config.clone(), message_number, index).await?;
    // Sanitized the same way uploaded filenames are, to keep an
    // attacker-controlled email filename from smuggling anything unexpected
    // into the Content-Disposition header value.
    let safe_name = crate::services::user::sanitize_filename(&filename);
    let disposition = format!("attachment; filename=\"{safe_name}\"");
    Ok((
        [
            (header::CONTENT_TYPE, content_type),
            (header::CONTENT_DISPOSITION, disposition),
        ],
        bytes,
    ))
}

pub async fn reply(
    State(state): State<Arc<AppState>>,
    admin: AdminUser,
    Path(message_number): Path<u32>,
    axum::Json(req): axum::Json<ReplyRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::reply_to_message(state.config.clone(), message_number, req.body).await?;
    alog::log_activity(&state, &admin.0, "REPLY_INBOX_MESSAGE", "inbox_message", Some(message_number.to_string()),
        format!("Replied to inbox message #{message_number}")).await;
    Ok(ApiResponse::ok("Reply sent"))
}
