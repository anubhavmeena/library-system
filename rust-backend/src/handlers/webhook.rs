use crate::{app_state::AppState, services::renewal_poll};
use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode},
};
use bytes::Bytes;
use hmac::{Hmac, Mac};
use serde::Deserialize;
use sha2::Sha256;
use std::{collections::HashMap, sync::Arc};

/// GET /api/whatsapp/webhook — Meta's one-time subscription verification
/// handshake, done once from Meta Business Manager when pointing the
/// webhook at https://targetzone.co.in/api/whatsapp/webhook. Bypasses the
/// ApiResponse envelope every other handler uses — Meta expects a raw
/// challenge echo, not JSON.
pub async fn verify_webhook(
    State(state): State<Arc<AppState>>,
    Query(q): Query<HashMap<String, String>>,
) -> Result<String, StatusCode> {
    let token_ok = q
        .get("hub.verify_token")
        .map(|t| *t == state.config.meta_webhook_verify_token)
        .unwrap_or(false);
    if token_ok {
        Ok(q.get("hub.challenge").cloned().unwrap_or_default())
    } else {
        Err(StatusCode::FORBIDDEN)
    }
}

#[derive(Deserialize)]
struct WebhookPayload {
    #[serde(default)]
    entry: Vec<Entry>,
}
#[derive(Deserialize)]
struct Entry {
    #[serde(default)]
    changes: Vec<Change>,
}
#[derive(Deserialize)]
struct Change {
    value: ChangeValue,
}
#[derive(Deserialize)]
struct ChangeValue {
    #[serde(default)]
    messages: Vec<InboundMessage>,
}
#[derive(Deserialize)]
struct InboundMessage {
    #[serde(rename = "type")]
    msg_type: String,
    button: Option<ButtonReply>,
    context: Option<MsgContext>,
}
#[derive(Deserialize)]
struct ButtonReply {
    text: Option<String>,
    payload: Option<String>,
}
#[derive(Deserialize)]
struct MsgContext {
    id: String,
}

/// POST /api/whatsapp/webhook — the actual event delivery. Needs the RAW
/// body bytes (not pre-parsed JSON) to verify X-Hub-Signature-256 before
/// trusting the payload.
pub async fn receive_webhook(State(state): State<Arc<AppState>>, headers: HeaderMap, body: Bytes) -> StatusCode {
    if !state.config.is_meta_app_secret_dev() {
        let sig_header = headers.get("X-Hub-Signature-256").and_then(|v| v.to_str().ok());
        if !verify_signature(&state.config.meta_app_secret, &body, sig_header) {
            tracing::warn!("WhatsApp webhook: signature verification failed");
            return StatusCode::FORBIDDEN;
        }
    }

    let Ok(payload) = serde_json::from_slice::<WebhookPayload>(&body) else {
        tracing::warn!("WhatsApp webhook: failed to parse payload");
        return StatusCode::OK; // ack anyway — don't make Meta retry a malformed payload
    };

    for entry in payload.entry {
        for change in entry.changes {
            for msg in change.value.messages {
                if msg.msg_type != "button" {
                    continue;
                }
                let (Some(button), Some(ctx)) = (msg.button, msg.context) else { continue };
                let response = button.text.or(button.payload).unwrap_or_default().to_uppercase();
                if response != "YES" && response != "NO" {
                    continue;
                }
                renewal_poll::record_poll_response(&state, &ctx.id, &response).await;
            }
        }
    }

    StatusCode::OK
}

fn verify_signature(secret: &str, body: &[u8], sig_header: Option<&str>) -> bool {
    let Some(sig) = sig_header.and_then(|h| h.strip_prefix("sha256=")) else { return false };
    type HmacSha256 = Hmac<Sha256>;
    let Ok(mut mac) = HmacSha256::new_from_slice(secret.as_bytes()) else { return false };
    mac.update(body);
    hex::encode(mac.finalize().into_bytes()) == sig
}

#[cfg(test)]
mod tests {
    use super::verify_signature;
    use hmac::{Hmac, Mac};
    use sha2::Sha256;

    const SECRET: &str = "test_meta_app_secret";

    fn compute_sig(secret: &str, body: &[u8]) -> String {
        type HmacSha256 = Hmac<Sha256>;
        let mut mac = HmacSha256::new_from_slice(secret.as_bytes()).unwrap();
        mac.update(body);
        format!("sha256={}", hex::encode(mac.finalize().into_bytes()))
    }

    #[test]
    fn valid_signature_is_accepted() {
        let body = b"{\"entry\":[]}";
        let sig = compute_sig(SECRET, body);
        assert!(verify_signature(SECRET, body, Some(&sig)));
    }

    #[test]
    fn tampered_body_is_rejected() {
        let sig = compute_sig(SECRET, b"{\"entry\":[]}");
        assert!(!verify_signature(SECRET, b"{\"entry\":[1]}", Some(&sig)));
    }

    #[test]
    fn wrong_secret_is_rejected() {
        let body = b"{\"entry\":[]}";
        let sig = compute_sig("a_different_secret", body);
        assert!(!verify_signature(SECRET, body, Some(&sig)));
    }

    #[test]
    fn missing_header_is_rejected() {
        assert!(!verify_signature(SECRET, b"{\"entry\":[]}", None));
    }

    #[test]
    fn malformed_header_without_prefix_is_rejected() {
        assert!(!verify_signature(SECRET, b"{\"entry\":[]}", Some("deadbeef")));
    }
}
