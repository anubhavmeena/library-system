use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AdminUser,
    models::admin::GalleryPhoto,
    response::ApiResponse,
    services::user::{remove_uploaded_file, save_file, validate_upload, IMAGE_CONTENT_TYPES},
};
use axum::extract::{Multipart, Path, State};
use std::sync::Arc;
use uuid::Uuid;

pub async fn list_gallery(
    State(state): State<Arc<AppState>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let photos = sqlx::query_as::<_, GalleryPhoto>(
        "SELECT * FROM gallery_photos ORDER BY uploaded_at DESC",
    )
    .fetch_all(&state.db)
    .await?;

    Ok(ApiResponse::success("Gallery retrieved", photos))
}

pub async fn upload_gallery_photo(
    State(state): State<Arc<AppState>>,
    admin: AdminUser,
    mut multipart: Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let mut file_url: Option<String> = None;
    let mut caption: Option<String> = None;

    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        let field_name = field.name().unwrap_or("").to_string();
        if field_name == "caption" {
            caption = Some(field.text().await.map_err(|e| AppError::BadRequest(e.to_string()))?);
        } else {
            let filename = field.file_name().unwrap_or("gallery.jpg").to_string();
            let content_type = field.content_type().map(|s| s.to_string());
            let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;

            validate_upload(
                content_type.as_deref(),
                &data,
                IMAGE_CONTENT_TYPES,
                "Invalid file type. Only JPEG, PNG, WebP allowed.",
            )?;

            let url = save_file(&state.config.upload_dir, admin.0.user_id, "gallery", &filename, &data).await?;
            file_url = Some(url);
        }
    }

    let url = file_url.ok_or_else(|| AppError::BadRequest("No file provided".into()))?;

    let photo = sqlx::query_as::<_, GalleryPhoto>(
        "INSERT INTO gallery_photos (id, url, caption, uploaded_by, uploaded_at) VALUES (gen_random_uuid(), $1, $2, $3, NOW()) RETURNING *",
    )
    .bind(&url)
    .bind(&caption)
    .bind(admin.0.user_id.to_string())
    .fetch_one(&state.db)
    .await?;

    Ok(ApiResponse::success("Photo uploaded", photo))
}

pub async fn delete_gallery_photo(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let url: String = sqlx::query_scalar("SELECT url FROM gallery_photos WHERE id = $1")
        .bind(id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Photo not found".into()))?;

    sqlx::query("DELETE FROM gallery_photos WHERE id = $1")
        .bind(id)
        .execute(&state.db)
        .await?;

    remove_uploaded_file(&state.config.upload_dir, &url).await;

    Ok(ApiResponse::ok("Photo deleted"))
}

#[cfg(test)]
mod integration_tests {
    use crate::test_support::*;
    use tower::ServiceExt;

    #[tokio::test]
    #[ignore]
    async fn gallery_is_public_to_list_but_admin_only_to_mutate() {
        let state = test_state().await;
        let router = test_router(state.clone());

        let resp = router.clone().oneshot(get_request("/api/gallery", None)).await.unwrap();
        assert_eq!(resp.status(), 200);

        let admin = admin_token(&state).await;
        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/gallery", Some(&admin),
            vec![
                text_field("caption", "Reading room"),
                file_field("photo", "room.png", "image/png", tiny_png_bytes()),
            ],
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["caption"], "Reading room");
        let photo_id = body["data"]["id"].as_str().unwrap().to_string();

        let resp = router.clone().oneshot(get_request("/api/gallery", None)).await.unwrap();
        let body = body_json(resp).await;
        assert!(body["data"].as_array().unwrap().iter().any(|p| p["id"] == photo_id));

        let (_id, token) = create_test_user(&state, "STUDENT", "Not Admin Gallery").await;
        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/gallery", Some(&token),
            vec![file_field("photo", "room2.png", "image/png", tiny_png_bytes())],
        )).await.unwrap();
        assert_eq!(resp.status(), 403);

        let resp = router.clone().oneshot(delete_request(&format!("/api/gallery/{photo_id}"), Some(&token))).await.unwrap();
        assert_eq!(resp.status(), 403);

        let resp = router.clone().oneshot(delete_request(&format!("/api/gallery/{photo_id}"), Some(&admin))).await.unwrap();
        assert_eq!(resp.status(), 200);

        let resp = router.clone().oneshot(delete_request(&format!("/api/gallery/{photo_id}"), Some(&admin))).await.unwrap();
        assert_eq!(resp.status(), 404, "already-deleted photo");
    }
}
