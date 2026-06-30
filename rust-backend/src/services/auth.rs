use crate::{
    app_state::AppState,
    error::AppError,
    models::user::User,
    services::{jwt, notification, otp},
};
use chrono::NaiveDate;
use sqlx::PgPool;
use std::sync::Arc;

pub async fn send_otp(state: &Arc<AppState>, contact: &str) -> crate::error::Result<()> {
    otp::send_otp(state, contact).await
}

pub async fn verify_otp(
    state: &Arc<AppState>,
    contact: &str,
    code: &str,
) -> crate::error::Result<(String, bool)> {
    let session_token = otp::verify_otp(state, contact, code).await?;

    let is_new_user = if contact.contains('@') {
        user_exists_by_email(&state.db, contact).await?
    } else {
        user_exists_by_mobile(&state.db, contact).await?
    };

    Ok((session_token, !is_new_user))
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
        "INSERT INTO users (mobile, email, name, address, gender, date_of_birth, role)
         VALUES ($1, $2, $3, $4, $5, $6, 'STUDENT')
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

    let token = jwt::create_token(
        user.id,
        &user.role,
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

    Ok((token, user))
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

    let token = jwt::create_token(
        user.id,
        "STUDENT",
        &user.name,
        user.email.as_deref(),
        user.mobile.as_deref(),
        &state.config.jwt_secret,
        state.config.jwt_expiry_ms,
    )?;

    Ok((token, User { role: "STUDENT".to_string(), ..user }))
}

pub async fn admin_login(
    state: &Arc<AppState>,
    contact: &str,
    otp_code: &str,
) -> crate::error::Result<(String, User)> {
    otp::verify_otp_direct(state, contact, otp_code).await?;

    let user = if contact.contains('@') {
        sqlx::query_as::<_, User>("SELECT * FROM users WHERE email = $1")
            .bind(contact)
            .fetch_optional(&state.db)
            .await?
    } else {
        sqlx::query_as::<_, User>("SELECT * FROM users WHERE mobile = $1")
            .bind(contact)
            .fetch_optional(&state.db)
            .await?
    }
    .ok_or_else(|| AppError::NotFound("User not found".into()))?;

    if user.role != "ADMIN" {
        return Err(AppError::Forbidden);
    }

    if !user.is_active {
        return Err(AppError::Forbidden);
    }

    let token = jwt::create_token(
        user.id,
        &user.role,
        &user.name,
        user.email.as_deref(),
        user.mobile.as_deref(),
        &state.config.jwt_secret,
        state.config.jwt_expiry_ms,
    )?;

    Ok((token, user))
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
