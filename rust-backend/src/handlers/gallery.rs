use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AdminUser,
    models::admin::GalleryPhoto,
    response::ApiResponse,
    services::user::save_file,
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
            let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;
            let url = save_file(&state.config.upload_dir, admin.0.user_id, "gallery", &filename, &data).await?;
            file_url = Some(url);
        }
    }

    let url = file_url.ok_or_else(|| AppError::BadRequest("No file provided".into()))?;

    let photo = sqlx::query_as::<_, GalleryPhoto>(
        "INSERT INTO gallery_photos (url, caption, uploaded_by) VALUES ($1, $2, $3) RETURNING *",
    )
    .bind(&url)
    .bind(&caption)
    .bind(admin.0.user_id)
    .fetch_one(&state.db)
    .await?;

    Ok(ApiResponse::success("Photo uploaded", photo))
}

pub async fn delete_gallery_photo(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let deleted = sqlx::query("DELETE FROM gallery_photos WHERE id = $1")
        .bind(id)
        .execute(&state.db)
        .await?;

    if deleted.rows_affected() == 0 {
        return Err(AppError::NotFound("Photo not found".into()));
    }

    Ok(ApiResponse::ok("Photo deleted"))
}
