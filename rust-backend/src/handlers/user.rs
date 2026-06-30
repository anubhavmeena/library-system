use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AuthUser,
    models::user::{SubmitFeedbackRequest, UpdateProfileRequest},
    response::ApiResponse,
    services::user as svc,
};
use axum::{
    extract::{Multipart, Path, State},
    Json,
};
use std::sync::Arc;
use uuid::Uuid;

pub async fn get_admin_contact(
    State(state): State<Arc<AppState>>,
) -> impl axum::response::IntoResponse {
    let contact = svc::get_admin_contact(&state).await;
    ApiResponse::success("Admin contact", contact)
}

pub async fn get_me(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let profile = svc::get_profile(&state, user.user_id).await?;
    Ok(ApiResponse::success("Profile retrieved", crate::models::user::UserProfile::from(profile)))
}

pub async fn get_user(
    State(state): State<Arc<AppState>>,
    _user: AuthUser,
    Path(user_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let profile = svc::get_profile(&state, user_id).await?;
    Ok(ApiResponse::success("User retrieved", crate::models::user::UserProfile::from(profile)))
}

pub async fn update_me(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<UpdateProfileRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let updated = svc::update_profile(&state, user.user_id, &req).await?;
    Ok(ApiResponse::success("Profile updated", crate::models::user::UserProfile::from(updated)))
}

pub async fn upload_photo(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    mut multipart: Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        let filename = field.file_name().unwrap_or("photo.jpg").to_string();
        let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;

        let url = svc::save_file(&state.config.upload_dir, user.user_id, "photo", &filename, &data).await?;
        svc::update_photo_url(&state, user.user_id, &url).await?;
        return Ok(ApiResponse::success("Photo uploaded", serde_json::json!({ "url": url })));
    }
    Err(AppError::BadRequest("No file provided".into()))
}

pub async fn delete_photo(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::delete_photo(&state, user.user_id).await?;
    Ok(ApiResponse::ok("Photo deleted"))
}

pub async fn upload_aadhaar(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    mut multipart: Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        let filename = field.file_name().unwrap_or("aadhaar.pdf").to_string();
        let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;

        let url = svc::save_file(&state.config.upload_dir, user.user_id, "aadhaar", &filename, &data).await?;
        svc::update_aadhaar_url(&state, user.user_id, &url).await?;
        return Ok(ApiResponse::success("Aadhaar uploaded", serde_json::json!({ "url": url })));
    }
    Err(AppError::BadRequest("No file provided".into()))
}

pub async fn delete_aadhaar(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::delete_aadhaar(&state, user.user_id).await?;
    Ok(ApiResponse::ok("Aadhaar deleted"))
}

pub async fn submit_feedback(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<SubmitFeedbackRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let feedback = svc::submit_feedback(
        &state,
        user.user_id,
        &req.feedback_type,
        &req.subject,
        &req.description,
    )
    .await?;
    Ok(ApiResponse::success("Feedback submitted", feedback))
}

pub async fn get_my_feedback(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let feedbacks = svc::get_my_feedback(&state, user.user_id).await?;
    Ok(ApiResponse::success("Feedback retrieved", feedbacks))
}
