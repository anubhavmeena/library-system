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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generate_id_has_expected_length_and_charset() {
        let id = generate_id();
        assert_eq!(id.len(), ID_LEN);
        assert!(id.chars().all(|c| c.is_ascii_alphanumeric()));
    }

    #[test]
    fn generate_id_is_not_constant() {
        // Astronomically unlikely to collide twice in a row if truly random.
        let a = generate_id();
        let b = generate_id();
        assert_ne!(a, b);
    }

    #[test]
    fn pay_link_payload_serializes_camel_case() {
        let payload = PayLinkPayload {
            user_id: Uuid::new_v4(),
            claim_type: Some("DUES".to_string()),
            membership_id: Some(Uuid::new_v4()),
            amount: Decimal::from(500),
            vpa: "test@ybl".to_string(),
            payee_name: "Target Zone".to_string(),
            note: "note".to_string(),
        };
        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("claimType"));
        assert!(json.contains("membershipId"));
        assert!(json.contains("payeeName"));
        assert!(!json.contains("claim_type"));
    }

    #[test]
    fn pay_link_payload_roundtrips_with_none_fields() {
        let payload = PayLinkPayload {
            user_id: Uuid::new_v4(),
            claim_type: None,
            membership_id: None,
            amount: Decimal::from(100),
            vpa: "a@b".to_string(),
            payee_name: "P".to_string(),
            note: "n".to_string(),
        };
        let json = serde_json::to_string(&payload).unwrap();
        let back: PayLinkPayload = serde_json::from_str(&json).unwrap();
        assert_eq!(back.claim_type, None);
        assert_eq!(back.membership_id, None);
        assert_eq!(back.user_id, payload.user_id);
    }
}
