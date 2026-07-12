use crate::{
    app_state::AppState,
    error::AppError,
    models::user::User,
    services::{jwt, notification, otp},
};
use chrono::NaiveDate;
use sqlx::PgPool;
use std::sync::Arc;

/// Mirrors Java's `AuthService.normalizeContact` and `admin::import_student`'s
/// digit-stripping: without this, a self-registering/logging-in student whose
/// typed number carries formatting (e.g. "+91 861-9679323") never matches the
/// bare-digits row an admin import created for the same person, so they land
/// on a second, empty account with no membership/booking history. Emails pass
/// through unchanged.
pub(crate) fn normalize_contact(contact: &str) -> crate::error::Result<String> {
    let contact = contact.trim();
    if contact.contains('@') {
        return Ok(contact.to_string());
    }
    let mut digits: String = contact.chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.len() == 12 && digits.starts_with("91") {
        digits = digits[2..].to_string();
    } else if digits.len() == 11 && digits.starts_with('0') {
        digits = digits[1..].to_string();
    }
    if digits.len() != 10 {
        return Err(AppError::BadRequest("Please enter a valid 10-digit mobile number.".into()));
    }
    Ok(digits)
}

pub async fn send_otp(state: &Arc<AppState>, contact: &str, channel: Option<&str>) -> crate::error::Result<()> {
    let contact = normalize_contact(contact)?;
    otp::send_otp_channel(state, &contact, channel).await
}

pub async fn verify_otp(
    state: &Arc<AppState>,
    contact: &str,
    code: &str,
) -> crate::error::Result<(String, bool)> {
    let contact = normalize_contact(contact)?;
    let session_token = otp::verify_otp(state, &contact, code).await?;

    let is_new_user = if contact.contains('@') {
        user_exists_by_email(&state.db, &contact).await?
    } else {
        user_exists_by_mobile(&state.db, &contact).await?
    };

    Ok((session_token, !is_new_user))
}

/// Mirrors Java's `AuthService.effectiveRole`: a user whose mobile is in the
/// `ADMIN_PHONES` allowlist is treated as ADMIN regardless of their stored
/// `role` column — the allowlist is a superset grant, never a downgrade.
fn effective_role(user: &User, config: &crate::config::Config) -> String {
    if user.role == "ADMIN" {
        return "ADMIN".to_string();
    }
    match &user.mobile {
        Some(mobile) if config.admin_phones.iter().any(|p| p == mobile) => "ADMIN".to_string(),
        _ => user.role.clone(),
    }
}

pub async fn register(
    state: &Arc<AppState>,
    session_token: &str,
    name: &str,
    email: Option<&str>,
    address: Option<&str>,
    gender: Option<&str>,
    date_of_birth: Option<NaiveDate>,
) -> crate::error::Result<(String, User)> {
    let contact = otp::consume_session(state, session_token).await?;

    let (mobile, reg_email) = if contact.contains('@') {
        (None, Some(contact.as_str()))
    } else {
        (Some(contact.as_str()), email)
    };

    let user = sqlx::query_as::<_, User>(
        "INSERT INTO users (mobile, email, name, address, gender, date_of_birth, role, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, 'STUDENT', NOW())
         RETURNING *",
    )
    .bind(mobile)
    .bind(reg_email)
    .bind(name)
    .bind(address)
    .bind(gender)
    .bind(date_of_birth)
    .fetch_one(&state.db)
    .await
    .map_err(|e| {
        if e.to_string().contains("unique") {
            AppError::Conflict("User with this mobile or email already exists".into())
        } else {
            AppError::Database(e)
        }
    })?;

    let role = effective_role(&user, &state.config);
    let token = jwt::create_token(
        user.id,
        &role,
        &user.name,
        user.email.as_deref(),
        user.mobile.as_deref(),
        &state.config.jwt_secret,
        state.config.jwt_expiry_ms,
    )?;

    let u = user.clone();
    let s = state.clone();
    tokio::spawn(async move {
        notification::send_welcome(&s, &u.name, u.mobile.as_deref(), u.email.as_deref()).await;
    });

    Ok((token, User { role, ..user }))
}

pub async fn login(
    state: &Arc<AppState>,
    session_token: &str,
) -> crate::error::Result<(String, User)> {
    let contact = otp::consume_session(state, session_token).await?;

    let user = if contact.contains('@') {
        find_user_by_email(&state.db, &contact).await?
    } else {
        find_user_by_mobile(&state.db, &contact).await?
    };

    if !user.is_active {
        return Err(AppError::Forbidden);
    }

    let role = effective_role(&user, &state.config);
    let token = jwt::create_token(
        user.id,
        &role,
        &user.name,
        user.email.as_deref(),
        user.mobile.as_deref(),
        &state.config.jwt_secret,
        state.config.jwt_expiry_ms,
    )?;

    Ok((token, User { role, ..user }))
}

pub async fn admin_login(
    state: &Arc<AppState>,
    contact: &str,
    otp_code: &str,
) -> crate::error::Result<(String, User)> {
    let contact = normalize_contact(contact)?;
    otp::verify_otp_direct(state, &contact, otp_code).await?;

    let user = if contact.contains('@') {
        sqlx::query_as::<_, User>("SELECT * FROM users WHERE email = $1")
            .bind(&contact)
            .fetch_optional(&state.db)
            .await?
    } else {
        sqlx::query_as::<_, User>("SELECT * FROM users WHERE mobile = $1")
            .bind(&contact)
            .fetch_optional(&state.db)
            .await?
    }
    .ok_or_else(|| AppError::NotFound("User not found".into()))?;

    let is_admin_phone = user
        .mobile
        .as_deref()
        .is_some_and(|m| state.config.admin_phones.iter().any(|p| p == m));
    if user.role != "ADMIN" && !is_admin_phone {
        return Err(AppError::Forbidden);
    }

    if !user.is_active {
        return Err(AppError::Forbidden);
    }

    let role = effective_role(&user, &state.config);
    let token = jwt::create_token(
        user.id,
        &role,
        &user.name,
        user.email.as_deref(),
        user.mobile.as_deref(),
        &state.config.jwt_secret,
        state.config.jwt_expiry_ms,
    )?;

    Ok((token, User { role, ..user }))
}

async fn user_exists_by_mobile(db: &PgPool, mobile: &str) -> crate::error::Result<bool> {
    let exists: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM users WHERE mobile = $1)")
        .bind(mobile)
        .fetch_one(db)
        .await?;
    Ok(exists)
}

async fn user_exists_by_email(db: &PgPool, email: &str) -> crate::error::Result<bool> {
    let exists: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM users WHERE email = $1)")
        .bind(email)
        .fetch_one(db)
        .await?;
    Ok(exists)
}

async fn find_user_by_mobile(db: &PgPool, mobile: &str) -> crate::error::Result<User> {
    sqlx::query_as::<_, User>("SELECT * FROM users WHERE mobile = $1")
        .bind(mobile)
        .fetch_optional(db)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".into()))
}

async fn find_user_by_email(db: &PgPool, email: &str) -> crate::error::Result<User> {
    sqlx::query_as::<_, User>("SELECT * FROM users WHERE email = $1")
        .bind(email)
        .fetch_optional(db)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".into()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    fn student(mobile: Option<&str>) -> User {
        User {
            id: Uuid::new_v4(),
            mobile: mobile.map(|m| m.to_string()),
            email: None,
            name: "Test User".to_string(),
            address: None,
            father_name: None,
            photo_url: None,
            aadhaar_url: None,
            date_of_birth: None,
            gender: None,
            is_active: true,
            role: "STUDENT".to_string(),
            created_at: chrono::Local::now().naive_local(),
            updated_at: None,
        }
    }

    #[test]
    fn normalize_contact_strips_dashes_spaces_and_country_code() {
        assert_eq!(normalize_contact("+91 861-9679323").unwrap(), "8619679323");
        assert_eq!(normalize_contact("918619679323").unwrap(), "8619679323");
        assert_eq!(normalize_contact("+918619679323").unwrap(), "8619679323");
        assert_eq!(normalize_contact("8619679323").unwrap(), "8619679323");
    }

    #[test]
    fn normalize_contact_leaves_email_unchanged_but_trims() {
        assert_eq!(normalize_contact("  roshan@example.com  ").unwrap(), "roshan@example.com");
    }

    #[test]
    fn normalize_contact_rejects_wrong_digit_count() {
        assert!(normalize_contact("12345").is_err());
        assert!(normalize_contact("091-8619679323").is_err());
    }

    #[test]
    fn effective_role_keeps_db_admin_role() {
        let mut config = crate::config::test_config();
        config.admin_phones = vec![];
        let mut user = student(Some("9000000000"));
        user.role = "ADMIN".to_string();
        assert_eq!(effective_role(&user, &config), "ADMIN");
    }

    #[test]
    fn effective_role_grants_admin_via_allowlisted_mobile() {
        let mut config = crate::config::test_config();
        config.admin_phones = vec!["9000000000".to_string()];
        let user = student(Some("9000000000"));
        assert_eq!(effective_role(&user, &config), "ADMIN");
    }

    #[test]
    fn effective_role_is_student_when_not_allowlisted() {
        let mut config = crate::config::test_config();
        config.admin_phones = vec!["9111111111".to_string()];
        let user = student(Some("9000000000"));
        assert_eq!(effective_role(&user, &config), "STUDENT");
    }

    #[test]
    fn effective_role_handles_missing_mobile() {
        let mut config = crate::config::test_config();
        config.admin_phones = vec!["9000000000".to_string()];
        let user = student(None);
        assert_eq!(effective_role(&user, &config), "STUDENT");
    }
}
