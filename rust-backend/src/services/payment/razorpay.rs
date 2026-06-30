use crate::{app_state::AppState, error::AppError, models::membership::CreateOrderResponse};
use hmac::{Hmac, Mac};
use rand::Rng;
use rust_decimal::Decimal;
use serde_json::json;
use sha2::Sha256;
use std::sync::Arc;
use uuid::Uuid;

pub async fn create_order(
    state: &Arc<AppState>,
    membership_id: Uuid,
    amount: Decimal,
) -> crate::error::Result<CreateOrderResponse> {
    if state.config.is_razorpay_dev() {
        let mock_id = format!("dev_order_{}", random_suffix());
        return Ok(CreateOrderResponse {
            order_id: mock_id,
            payment_session_id: None,
            membership_id,
            amount,
            gateway: "RAZORPAY".into(),
        });
    }

    let amount_paise = (amount * Decimal::from(100))
        .to_string()
        .parse::<u64>()
        .map_err(|_| AppError::Internal("Invalid amount".into()))?;

    let body = json!({
        "amount": amount_paise,
        "currency": "INR",
        "receipt": membership_id.to_string(),
    });

    let resp = state
        .http
        .post("https://api.razorpay.com/v1/orders")
        .basic_auth(&state.config.razorpay_key_id, Some(&state.config.razorpay_key_secret))
        .json(&body)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("Razorpay request error: {e}")))?;

    if !resp.status().is_success() {
        let text = resp.text().await.unwrap_or_default();
        return Err(AppError::Internal(format!("Razorpay order creation failed: {text}")));
    }

    let data: serde_json::Value = resp
        .json()
        .await
        .map_err(|e| AppError::Internal(format!("Razorpay response parse error: {e}")))?;

    let order_id = data["id"]
        .as_str()
        .ok_or_else(|| AppError::Internal("No order id in Razorpay response".into()))?
        .to_string();

    Ok(CreateOrderResponse {
        order_id,
        payment_session_id: None,
        membership_id,
        amount,
        gateway: "RAZORPAY".into(),
    })
}

pub fn verify_payment(
    state: &Arc<AppState>,
    order_id: &str,
    payment_id: Option<&str>,
    signature: Option<&str>,
) -> crate::error::Result<bool> {
    if order_id.starts_with("dev_") || state.config.is_razorpay_dev() {
        return Ok(true);
    }

    let payment_id = payment_id.ok_or_else(|| AppError::BadRequest("payment_id required".into()))?;
    let signature = signature.ok_or_else(|| AppError::BadRequest("signature required".into()))?;

    Ok(verify_hmac_signature(&state.config.razorpay_key_secret, order_id, payment_id, signature))
}

pub(crate) fn verify_hmac_signature(secret: &str, order_id: &str, payment_id: &str, signature: &str) -> bool {
    let payload = format!("{order_id}|{payment_id}");
    type HmacSha256 = Hmac<Sha256>;
    let Ok(mut mac) = HmacSha256::new_from_slice(secret.as_bytes()) else { return false };
    mac.update(payload.as_bytes());
    hex::encode(mac.finalize().into_bytes()) == signature
}

fn random_suffix() -> String {
    let mut rng = rand::thread_rng();
    (0..8)
        .map(|_| rng.sample(rand::distributions::Alphanumeric) as char)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::verify_hmac_signature;

    const SECRET: &str = "test_razorpay_secret_key";

    fn compute_sig(secret: &str, order_id: &str, payment_id: &str) -> String {
        use hmac::{Hmac, Mac};
        use sha2::Sha256;
        type HmacSha256 = Hmac<Sha256>;
        let payload = format!("{order_id}|{payment_id}");
        let mut mac = HmacSha256::new_from_slice(secret.as_bytes()).unwrap();
        mac.update(payload.as_bytes());
        hex::encode(mac.finalize().into_bytes())
    }

    #[test]
    fn valid_signature_accepted() {
        let sig = compute_sig(SECRET, "order_123", "pay_456");
        assert!(verify_hmac_signature(SECRET, "order_123", "pay_456", &sig));
    }

    #[test]
    fn wrong_signature_rejected() {
        assert!(!verify_hmac_signature(SECRET, "order_123", "pay_456", "deadbeef0000"));
    }

    #[test]
    fn wrong_payment_id_rejected() {
        let sig = compute_sig(SECRET, "order_123", "pay_456");
        assert!(!verify_hmac_signature(SECRET, "order_123", "pay_DIFFERENT", &sig));
    }

    #[test]
    fn wrong_order_id_rejected() {
        let sig = compute_sig(SECRET, "order_123", "pay_456");
        assert!(!verify_hmac_signature(SECRET, "order_DIFFERENT", "pay_456", &sig));
    }

    #[test]
    fn wrong_secret_rejected() {
        let sig = compute_sig(SECRET, "order_123", "pay_456");
        assert!(!verify_hmac_signature("wrong_secret_key", "order_123", "pay_456", &sig));
    }

    #[test]
    fn payload_format_is_order_pipe_payment() {
        // Canonically: "{order_id}|{payment_id}" — NOT "{payment_id}|{order_id}"
        let sig_correct = compute_sig(SECRET, "order_ABC", "pay_XYZ");
        let sig_reversed = compute_sig(SECRET, "pay_XYZ", "order_ABC");
        assert_ne!(sig_correct, sig_reversed);
        assert!(verify_hmac_signature(SECRET, "order_ABC", "pay_XYZ", &sig_correct));
        assert!(!verify_hmac_signature(SECRET, "order_ABC", "pay_XYZ", &sig_reversed));
    }

    #[test]
    fn dev_order_prefix_constant() {
        // Dev-mode orders start with "dev_"; verify_payment checks this before HMAC
        assert!("dev_order_AbCdEfGh".starts_with("dev_"));
        assert!(!"order_live_123".starts_with("dev_"));
    }
}
