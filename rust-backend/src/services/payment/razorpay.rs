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

    let payload = format!("{order_id}|{payment_id}");

    type HmacSha256 = Hmac<Sha256>;
    let mut mac = HmacSha256::new_from_slice(state.config.razorpay_key_secret.as_bytes())
        .map_err(|e| AppError::Internal(format!("HMAC key error: {e}")))?;
    mac.update(payload.as_bytes());
    let expected = hex::encode(mac.finalize().into_bytes());

    Ok(expected == signature)
}

fn random_suffix() -> String {
    let mut rng = rand::thread_rng();
    (0..8)
        .map(|_| rng.sample(rand::distributions::Alphanumeric) as char)
        .collect()
}
