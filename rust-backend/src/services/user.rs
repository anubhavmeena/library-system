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
    .bind(&req.email)
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
    sqlx::query("UPDATE users SET photo_url = NULL, updated_at = NOW() WHERE id = $1")
        .bind(user_id)
        .execute(&state.db)
        .await?;
    Ok(())
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

pub async fn get_admin_contact(state: &Arc<AppState>) -> serde_json::Value {
    serde_json::json!({
        "email": state.config.admin_email,
        "whatsapp": state.config.admin_whatsapp,
        "phones": state.config.admin_phones,
    })
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

fn sanitize_filename(name: &str) -> String {
    name.chars()
        .map(|c| if c.is_alphanumeric() || c == '.' || c == '-' { c } else { '_' })
        .collect()
}
