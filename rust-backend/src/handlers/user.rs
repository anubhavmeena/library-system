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
        let content_type = field.content_type().map(|s| s.to_string());
        let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;

        svc::validate_upload(
            content_type.as_deref(),
            &data,
            svc::IMAGE_CONTENT_TYPES,
            "Invalid file type. Only JPEG, PNG, WebP allowed.",
        )?;

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
        let content_type = field.content_type().map(|s| s.to_string());
        let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;

        svc::validate_upload(
            content_type.as_deref(),
            &data,
            svc::AADHAAR_CONTENT_TYPES,
            "Invalid file type. Only JPEG, PNG, WebP, or PDF allowed.",
        )?;

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

#[cfg(test)]
mod integration_tests {
    use crate::test_support::*;
    use tower::ServiceExt;

    #[tokio::test]
    #[ignore]
    async fn get_and_update_own_profile() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Profile Owner").await;

        let resp = router.clone().oneshot(get_request("/api/users/me", Some(&token))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["name"], "Profile Owner");
        assert_eq!(body["data"]["isActive"], true);

        let resp = router.clone().oneshot(json_request(
            "PATCH", "/api/users/me", Some(&token),
            serde_json::json!({ "name": "Updated Name", "address": "42 Test Ave", "gender": "Other", "fatherName": "Test Father" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["name"], "Updated Name");
        assert_eq!(body["data"]["address"], "42 Test Ave");
        assert_eq!(body["data"]["fatherName"], "Test Father");

        // partial update: omitted fields must be preserved, not wiped
        let resp = router.clone().oneshot(json_request(
            "PATCH", "/api/users/me", Some(&token), serde_json::json!({ "gender": "Female" }),
        )).await.unwrap();
        let body = body_json(resp).await;
        assert_eq!(body["data"]["gender"], "Female");
        assert_eq!(body["data"]["name"], "Updated Name", "fields omitted from a PATCH must be left untouched");
        assert_eq!(body["data"]["address"], "42 Test Ave");
    }

    #[tokio::test]
    #[ignore]
    async fn get_me_requires_auth() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let resp = router.clone().oneshot(get_request("/api/users/me", None)).await.unwrap();
        assert_eq!(resp.status(), 401);
    }

    #[tokio::test]
    #[ignore]
    async fn any_authed_user_can_view_another_users_profile_by_id() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id_a, token_a) = create_test_user(&state, "STUDENT", "Viewer").await;
        let (id_b, _token_b) = create_test_user(&state, "STUDENT", "Viewed").await;

        let resp = router.clone().oneshot(get_request(&format!("/api/users/{id_b}"), Some(&token_a))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["name"], "Viewed");
    }

    #[tokio::test]
    #[ignore]
    async fn get_user_unknown_id_is_not_found() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Someone").await;
        let resp = router.clone().oneshot(get_request(&format!("/api/users/{}", uuid::Uuid::new_v4()), Some(&token))).await.unwrap();
        assert_eq!(resp.status(), 404);
    }

    #[tokio::test]
    #[ignore]
    async fn admin_contact_is_public() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let resp = router.clone().oneshot(get_request("/api/users/admin-contact", None)).await.unwrap();
        assert_eq!(resp.status(), 200);
    }

    #[tokio::test]
    #[ignore]
    async fn feedback_submit_and_list_own() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Feedback Author").await;

        // `feedbacks.type` has a DB CHECK constraint allowing only
        // FEEDBACK/COMPLAINT (see migrations) -- any other value 500s rather
        // than 400ing cleanly, since submit_feedback maps every DB error the
        // same way; use a valid value here and probe that rough edge separately.
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/users/feedback", Some(&token),
            serde_json::json!({ "type": "FEEDBACK", "subject": "More outlets", "description": "Row C needs more power outlets." }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["status"], "OPEN");
        assert_eq!(body["data"]["type"], "FEEDBACK");

        let resp = router.clone().oneshot(get_request("/api/users/feedback/my", Some(&token))).await.unwrap();
        let body = body_json(resp).await;
        assert!(body["data"].as_array().unwrap().iter().any(|f| f["subject"] == "More outlets"));
    }

    #[tokio::test]
    #[ignore]
    async fn feedback_with_invalid_type_500s_instead_of_400ing_cleanly() {
        // Documents a known rough edge rather than desired behavior: an
        // out-of-range `type` trips the DB CHECK constraint, and
        // submit_feedback maps every DB error to a generic 500 instead of
        // validating/translating it into a 400 first.
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Bad Feedback Type").await;
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/users/feedback", Some(&token),
            serde_json::json!({ "type": "SUGGESTION", "subject": "x", "description": "y" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 500);
    }

    #[tokio::test]
    #[ignore]
    async fn feedback_submission_requires_auth() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/users/feedback", None,
            serde_json::json!({ "type": "COMPLAINT", "subject": "x", "description": "y" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 401);
    }
}
