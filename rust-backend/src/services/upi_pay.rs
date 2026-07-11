use crate::{app_state::AppState, error::AppError};
use rand::Rng;
use redis::AsyncCommands;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

const PAY_LINK_TTL_SECS: u64 = 7 * 24 * 60 * 60;
const ID_CHARS: &[u8] = b"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
const ID_LEN: usize = 10;

// Everything needed to build a upi://pay deep link, plus optional claim
// context. Stored in Redis behind a short opaque id rather than embedded
// directly in the URL — a signed JWT + 5 UPI query params made for a
// 300+ character link, unwieldy in a WhatsApp message. Mirrors the existing
// OTP session-token pattern (services/otp.rs's `session:<uuid>` keys) rather
// than a self-contained token.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PayLinkPayload {
    pub user_id: Uuid,
    // None for the admin's ad-hoc "Send Payment Request" (Create Membership) —
    // that flow only needs the UPI redirect, not the claim-upload workflow,
    // since the admin themselves is present to confirm the payment there.
    pub claim_type: Option<String>,
    pub membership_id: Option<Uuid>,
    pub amount: Decimal,
    pub vpa: String,
    pub payee_name: String,
    pub note: String,
}

async fn conn(state: &Arc<AppState>) -> crate::error::Result<impl AsyncCommands> {
    state.redis.get_multiplexed_async_connection().await.map_err(AppError::Redis)
}

fn generate_id() -> String {
    let mut rng = rand::thread_rng();
    (0..ID_LEN).map(|_| ID_CHARS[rng.gen_range(0..ID_CHARS.len())] as char).collect()
}

/// Returns the short `{frontend_url}/pay?id=<id>` link.
pub async fn create_pay_link(state: &Arc<AppState>, payload: &PayLinkPayload) -> crate::error::Result<String> {
    let id = generate_id();
    let json = serde_json::to_string(payload).map_err(|e| AppError::Internal(e.to_string()))?;
    let mut c = conn(state).await?;
    c.set_ex::<_, _, ()>(format!("pay_link:{id}"), json, PAY_LINK_TTL_SECS).await?;
    Ok(format!("{}/pay?id={id}", state.config.frontend_url))
}

pub async fn resolve_pay_link(state: &Arc<AppState>, id: &str) -> crate::error::Result<PayLinkPayload> {
    let mut c = conn(state).await?;
    let json: Option<String> = c.get(format!("pay_link:{id}")).await?;
    let json = json.ok_or_else(|| AppError::NotFound("Payment link not found or expired".into()))?;
    serde_json::from_str(&json).map_err(|e| AppError::Internal(e.to_string()))
}
