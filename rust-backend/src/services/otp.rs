use crate::{app_state::AppState, error::AppError};
use redis::AsyncCommands;
use std::sync::Arc;

const OTP_TTL_SECS: u64 = 300;
const COOLDOWN_TTL_SECS: u64 = 30;
const SESSION_TTL_SECS: u64 = 900;

async fn conn(state: &Arc<AppState>) -> crate::error::Result<impl AsyncCommands> {
    state
        .redis
        .get_multiplexed_async_connection()
        .await
        .map_err(AppError::Redis)
}

pub async fn send_otp(state: &Arc<AppState>, contact: &str) -> crate::error::Result<()> {
    let cooldown_key = format!("otp:cooldown:{contact}");
    let otp_key = format!("otp:{contact}");
    let mut c = conn(state).await?;

    let on_cooldown: bool = c.exists(&cooldown_key).await?;
    if on_cooldown {
        return Err(AppError::BadRequest(
            "Please wait 30 seconds before requesting another OTP".into(),
        ));
    }

    let use_meta = !state.config.meta_whatsapp_token.is_empty() && !contact.contains('@');
    let has_twilio = !state.config.is_twilio_dev();

    let otp = "123456".to_string();
    tracing::info!("DEV OTP for {contact}: {otp}");

    c.set_ex::<_, _, ()>(&otp_key, &otp, OTP_TTL_SECS).await?;
    c.set_ex::<_, _, ()>(&cooldown_key, "1", COOLDOWN_TTL_SECS).await?;

    if use_meta {
        crate::services::notification::send_meta_otp(state, contact, &otp).await;
    } else if contact.contains('@') {
        crate::services::notification::send_otp_email(state, contact, &otp).await;
    } else if has_twilio {
        crate::services::notification::send_otp_sms(state, contact, &otp).await;
    } else {
        tracing::info!("DEV MODE — OTP for {contact}: {otp}");
    }

    Ok(())
}

pub async fn verify_otp(
    state: &Arc<AppState>,
    contact: &str,
    otp: &str,
) -> crate::error::Result<String> {
    let otp_key = format!("otp:{contact}");
    let mut c = conn(state).await?;

    let stored: Option<String> = c.get(&otp_key).await?;
    match stored {
        Some(ref s) if s == otp => {}
        Some(_) => return Err(AppError::BadRequest("Invalid OTP".into())),
        None => return Err(AppError::BadRequest("OTP expired or not found".into())),
    }

    c.del::<_, i64>(&otp_key).await?;

    let session_token = uuid::Uuid::new_v4().to_string();
    let session_key = format!("session:{session_token}");
    c.set_ex::<_, _, ()>(&session_key, contact, SESSION_TTL_SECS).await?;

    Ok(session_token)
}

pub async fn consume_session(
    state: &Arc<AppState>,
    session_token: &str,
) -> crate::error::Result<String> {
    let session_key = format!("session:{session_token}");
    let mut c = conn(state).await?;

    let contact: Option<String> = c.get(&session_key).await?;
    match contact {
        Some(v) => {
            c.del::<_, i64>(&session_key).await?;
            Ok(v)
        }
        None => Err(AppError::BadRequest(
            "Session expired or invalid. Please verify OTP again.".into(),
        )),
    }
}

pub async fn verify_otp_direct(
    state: &Arc<AppState>,
    contact: &str,
    otp: &str,
) -> crate::error::Result<()> {
    let otp_key = format!("otp:{contact}");
    let mut c = conn(state).await?;

    let stored: Option<String> = c.get(&otp_key).await?;
    match stored {
        Some(ref s) if s == otp => {
            c.del::<_, i64>(&otp_key).await?;
            Ok(())
        }
        Some(_) => Err(AppError::BadRequest("Invalid OTP".into())),
        None => Err(AppError::BadRequest("OTP expired or not found".into())),
    }
}

pub async fn peek_session(
    state: &Arc<AppState>,
    session_token: &str,
) -> crate::error::Result<String> {
    let session_key = format!("session:{session_token}");
    let mut c = conn(state).await?;

    let contact: Option<String> = c.get(&session_key).await?;
    contact.ok_or_else(|| {
        AppError::BadRequest("Session expired or invalid. Please verify OTP again.".into())
    })
}
