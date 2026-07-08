use crate::{
    app_state::AppState,
    error::AppError,
    models::user::{Feedback, UpdateProfileRequest, User},
};
use std::sync::Arc;
use uuid::Uuid;

pub async fn get_profile(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<User> {
    sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".into()))
}

pub async fn update_profile(
    state: &Arc<AppState>,
    user_id: Uuid,
    req: &UpdateProfileRequest,
) -> crate::error::Result<User> {
    // Normalize + pre-check email uniqueness ourselves so a clash surfaces as
    // a clean 400 instead of the unique-constraint violation bubbling up as a
    // generic 500 — mirrors Java's UserService.updateProfile.
    let normalized_email = req
        .email
        .as_deref()
        .map(str::trim)
        .filter(|e| !e.is_empty())
        .map(|e| e.to_lowercase());

    if let Some(ref email) = normalized_email {
        let existing: Option<Uuid> = sqlx::query_scalar("SELECT id FROM users WHERE email = $1")
            .bind(email)
            .fetch_optional(&state.db)
            .await?;
        if let Some(existing_id) = existing {
            if existing_id != user_id {
                return Err(AppError::BadRequest(
                    "Email already in use by another account".into(),
                ));
            }
        }
    }

    sqlx::query_as::<_, User>(
        "UPDATE users SET
            name          = COALESCE($2, name),
            address       = COALESCE($3, address),
            father_name   = COALESCE($4, father_name),
            gender        = COALESCE($5, gender),
            date_of_birth = COALESCE($6, date_of_birth),
            email         = COALESCE($7, email),
            updated_at    = NOW()
         WHERE id = $1 RETURNING *",
    )
    .bind(user_id)
    .bind(&req.name)
    .bind(&req.address)
    .bind(&req.father_name)
    .bind(&req.gender)
    .bind(req.date_of_birth)
    .bind(normalized_email)
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn update_photo_url(
    state: &Arc<AppState>,
    user_id: Uuid,
    url: &str,
) -> crate::error::Result<()> {
    sqlx::query("UPDATE users SET photo_url = $2, updated_at = NOW() WHERE id = $1")
        .bind(user_id)
        .bind(url)
        .execute(&state.db)
        .await?;
    Ok(())
}

pub async fn delete_photo(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<()> {
    let current: Option<String> = sqlx::query_scalar("SELECT photo_url FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .flatten();
    let url = current.ok_or_else(|| AppError::BadRequest("No photo to delete.".into()))?;

    remove_uploaded_file(&state.config.upload_dir, &url).await;

    sqlx::query("UPDATE users SET photo_url = NULL, updated_at = NOW() WHERE id = $1")
        .bind(user_id)
        .execute(&state.db)
        .await?;
    Ok(())
}

pub(crate) fn uploaded_file_path(upload_dir: &str, url: &str) -> Option<String> {
    let relative = url.strip_prefix("/uploads/")?;
    Some(format!("{}/{relative}", upload_dir.trim_end_matches('/')))
}

/// Best-effort disk cleanup for a stored `/uploads/...` URL — mirrors Java's
/// `Files.deleteIfExists`, which never fails the request over a missing file.
pub async fn remove_uploaded_file(upload_dir: &str, url: &str) {
    let Some(path) = uploaded_file_path(upload_dir, url) else { return };
    if let Err(e) = tokio::fs::remove_file(&path).await {
        if e.kind() != std::io::ErrorKind::NotFound {
            tracing::warn!("Could not delete uploaded file {path}: {e}");
        }
    }
}

pub async fn update_aadhaar_url(
    state: &Arc<AppState>,
    user_id: Uuid,
    url: &str,
) -> crate::error::Result<()> {
    sqlx::query("UPDATE users SET aadhaar_url = $2, updated_at = NOW() WHERE id = $1")
        .bind(user_id)
        .bind(url)
        .execute(&state.db)
        .await?;
    Ok(())
}

pub async fn delete_aadhaar(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<()> {
    let current: Option<String> = sqlx::query_scalar("SELECT aadhaar_url FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .flatten();
    let url = current.ok_or_else(|| AppError::BadRequest("No Aadhaar card on file.".into()))?;

    remove_uploaded_file(&state.config.upload_dir, &url).await;

    sqlx::query("UPDATE users SET aadhaar_url = NULL, updated_at = NOW() WHERE id = $1")
        .bind(user_id)
        .execute(&state.db)
        .await?;
    Ok(())
}

pub async fn submit_feedback(
    state: &Arc<AppState>,
    user_id: Uuid,
    feedback_type: &str,
    subject: &str,
    description: &str,
) -> crate::error::Result<Feedback> {
    sqlx::query_as::<_, Feedback>(
        "INSERT INTO feedbacks (id, user_id, \"type\", subject, description, status)
         VALUES (gen_random_uuid(), $1, $2, $3, $4, 'OPEN') RETURNING *",
    )
    .bind(user_id)
    .bind(feedback_type)
    .bind(subject)
    .bind(description)
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn get_my_feedback(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<Feedback>> {
    sqlx::query_as::<_, Feedback>(
        "SELECT * FROM feedbacks WHERE user_id = $1 ORDER BY created_at DESC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

/// Mirrors Java's `UserService.getAdminContact` — reads the first ADMIN-role
/// user from the DB (real contact details an admin actually set up their
/// account with), not the `ADMIN_EMAIL`/`ADMIN_WHATSAPP`/`ADMIN_PHONES` env
/// vars, which are just the OTP-less-login allowlist and notification
/// destinations, not necessarily a student-facing contact.
pub async fn get_admin_contact(state: &Arc<AppState>) -> serde_json::Value {
    let row: Option<(String, Option<String>, Option<String>)> = sqlx::query_as(
        "SELECT name, mobile, email FROM users WHERE role = 'ADMIN' ORDER BY created_at LIMIT 1",
    )
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten();

    match row {
        Some((name, mobile, email)) => serde_json::json!({ "name": name, "mobile": mobile, "email": email }),
        None => serde_json::json!({ "name": "Admin", "mobile": null, "email": null }),
    }
}

pub const MAX_UPLOAD_BYTES: usize = 5_242_880; // 5 MB — matches Java's UserService/GalleryService
pub const IMAGE_CONTENT_TYPES: &[&str] = &["image/jpeg", "image/png", "image/webp"];
pub const AADHAAR_CONTENT_TYPES: &[&str] = &["image/jpeg", "image/png", "image/webp", "application/pdf"];

/// Rejects the upload up front — matches Java's `UserService`/`GalleryService`
/// content-type allowlist + 5MB cap, checked before any file ever touches disk.
pub fn validate_upload(
    content_type: Option<&str>,
    data: &[u8],
    allowed: &[&str],
    type_error: &str,
) -> crate::error::Result<()> {
    match content_type {
        Some(ct) if allowed.contains(&ct) => {}
        _ => return Err(AppError::BadRequest(type_error.to_string())),
    }
    if data.len() > MAX_UPLOAD_BYTES {
        return Err(AppError::BadRequest("File must not exceed 5MB.".into()));
    }
    Ok(())
}

pub async fn save_file(
    upload_dir: &str,
    user_id: Uuid,
    kind: &str,
    filename: &str,
    data: &[u8],
) -> crate::error::Result<String> {
    let dir = format!("{upload_dir}/{user_id}");
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|e| AppError::Internal(format!("Cannot create upload dir: {e}")))?;

    let safe_name = sanitize_filename(filename);
    let path = format!("{dir}/{kind}_{safe_name}");

    tokio::fs::write(&path, data)
        .await
        .map_err(|e| AppError::Internal(format!("Cannot write file: {e}")))?;

    Ok(format!("/uploads/{user_id}/{kind}_{safe_name}"))
}

pub(crate) fn sanitize_filename(name: &str) -> String {
    name.chars()
        .map(|c| if c.is_alphanumeric() || c == '.' || c == '-' { c } else { '_' })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::{
        sanitize_filename, uploaded_file_path, validate_upload, AADHAAR_CONTENT_TYPES,
        IMAGE_CONTENT_TYPES, MAX_UPLOAD_BYTES,
    };

    #[test]
    fn alphanumeric_dot_hyphen_pass_through() {
        assert_eq!(sanitize_filename("photo-123.jpg"), "photo-123.jpg");
    }

    #[test]
    fn spaces_replaced_with_underscore() {
        assert_eq!(sanitize_filename("my photo file.jpg"), "my_photo_file.jpg");
    }

    #[test]
    fn slashes_replaced_with_underscore() {
        assert_eq!(sanitize_filename("path/to/file.jpg"), "path_to_file.jpg");
    }

    #[test]
    fn special_chars_at_hash_dollar_replaced() {
        assert_eq!(sanitize_filename("file@#$.jpg"), "file___.jpg");
    }

    #[test]
    fn empty_string_stays_empty() {
        assert_eq!(sanitize_filename(""), "");
    }

    #[test]
    fn multiple_dots_preserved() {
        assert_eq!(sanitize_filename("my.file.name.jpg"), "my.file.name.jpg");
    }

    #[test]
    fn path_traversal_dots_preserved_slashes_replaced() {
        assert_eq!(sanitize_filename("../../etc/passwd"), ".._.._etc_passwd");
    }

    #[test]
    fn result_contains_no_spaces_or_slashes() {
        let inputs = ["hello world.jpg", "a/b/c.png", "foo bar baz.webp"];
        for input in inputs {
            let result = sanitize_filename(input);
            assert!(!result.contains(' '), "space in result for: {input}");
            assert!(!result.contains('/'), "slash in result for: {input}");
        }
    }

    #[test]
    fn validate_upload_accepts_allowed_image_type_under_size_cap() {
        assert!(validate_upload(Some("image/png"), &[0u8; 100], IMAGE_CONTENT_TYPES, "bad type").is_ok());
    }

    #[test]
    fn validate_upload_rejects_missing_content_type() {
        assert!(validate_upload(None, &[0u8; 100], IMAGE_CONTENT_TYPES, "bad type").is_err());
    }

    #[test]
    fn validate_upload_rejects_disallowed_content_type() {
        assert!(validate_upload(Some("application/pdf"), &[0u8; 100], IMAGE_CONTENT_TYPES, "bad type").is_err());
    }

    #[test]
    fn validate_upload_allows_pdf_for_aadhaar_but_not_image_set() {
        assert!(validate_upload(Some("application/pdf"), &[0u8; 100], AADHAAR_CONTENT_TYPES, "bad type").is_ok());
    }

    #[test]
    fn validate_upload_rejects_oversized_file() {
        let data = vec![0u8; MAX_UPLOAD_BYTES + 1];
        assert!(validate_upload(Some("image/jpeg"), &data, IMAGE_CONTENT_TYPES, "bad type").is_err());
    }

    #[test]
    fn validate_upload_accepts_file_exactly_at_size_cap() {
        let data = vec![0u8; MAX_UPLOAD_BYTES];
        assert!(validate_upload(Some("image/jpeg"), &data, IMAGE_CONTENT_TYPES, "bad type").is_ok());
    }

    #[test]
    fn uploaded_file_path_joins_dir_and_relative_path() {
        assert_eq!(
            uploaded_file_path("/data/uploads", "/uploads/user123/photo_a.jpg"),
            Some("/data/uploads/user123/photo_a.jpg".to_string())
        );
    }

    #[test]
    fn uploaded_file_path_strips_trailing_slash_on_dir() {
        assert_eq!(
            uploaded_file_path("/data/uploads/", "/uploads/gallery/g1.png"),
            Some("/data/uploads/gallery/g1.png".to_string())
        );
    }

    #[test]
    fn uploaded_file_path_rejects_url_without_uploads_prefix() {
        assert_eq!(uploaded_file_path("/data/uploads", "https://evil.com/x"), None);
    }

    #[test]
    fn url_built_from_sanitized_filename_has_no_spaces() {
        let user_id = uuid::Uuid::new_v4();
        let safe = sanitize_filename("my photo.jpg");
        let url = format!("/uploads/{user_id}/photo_{safe}");
        assert!(!url.contains(' '));
        assert!(url.starts_with("/uploads/"));
    }
}
