use crate::{app_state::AppState, error::AppError, models::membership::CreateOrderResponse};
use rand::Rng;
use rust_decimal::Decimal;
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

pub async fn create_order(
    state: &Arc<AppState>,
    membership_id: Uuid,
    user_id: Uuid,
    user_mobile: Option<&str>,
    user_email: Option<&str>,
    user_name: &str,
    amount: Decimal,
) -> crate::error::Result<CreateOrderResponse> {
    if state.config.is_cashfree_dev() {
        let mock_id = format!("dev_order_{}", random_suffix());
        return Ok(CreateOrderResponse {
            order_id: mock_id,
            payment_session_id: None,
            membership_id,
            amount,
            gateway: "CASHFREE".into(),
            discount_amount: None,
        });
    }

    let url = format!("{}/pg/orders", state.config.cashfree_base_url);

    // Cashfree requires the caller to supply a unique order_id and rejects a
    // repeat with "order_already_exists" — unlike Razorpay, which mints its own
    // fresh id server-side regardless of what reference we pass. Using the bare
    // membership_id meant any retry for the same membership (an abandoned
    // checkout, a dues/pending-amount payment attempted twice) reused the exact
    // same order_id and got permanently rejected. Appending a fresh random
    // suffix per attempt fixes this for all three flows that share this
    // function (regular booking, dues, pending-amount) — order_id is just an
    // opaque string end-to-end (stored on gateway_order_id, read back at
    // verify time), so the format change needs no other code to change.
    let body = json!({
        "order_id": format!("{membership_id}_{}", random_suffix()),
        "order_amount": amount,
        "order_currency": "INR",
        "customer_details": {
            "customer_id": user_id.to_string(),
            "customer_name": user_name,
            "customer_phone": sanitize_phone(user_mobile),
            "customer_email": user_email.unwrap_or(""),
        }
    });

    let resp = state
        .http
        .post(&url)
        .header("x-api-version", "2025-01-01")
        .header("x-client-id", &state.config.cashfree_app_id)
        .header("x-client-secret", &state.config.cashfree_secret_key)
        .json(&body)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("Cashfree request error: {e}")))?;

    if !resp.status().is_success() {
        let text = resp.text().await.unwrap_or_default();
        return Err(AppError::Internal(format!("Cashfree order creation failed: {text}")));
    }

    let data: serde_json::Value = resp
        .json()
        .await
        .map_err(|e| AppError::Internal(format!("Cashfree response parse error: {e}")))?;

    let order_id = data["order_id"]
        .as_str()
        .ok_or_else(|| AppError::Internal("No order_id in Cashfree response".into()))?
        .to_string();

    let payment_session_id = data["payment_session_id"].as_str().map(|s| s.to_string());

    Ok(CreateOrderResponse {
        order_id,
        payment_session_id,
        membership_id,
        amount,
        gateway: "CASHFREE".into(),
        discount_amount: None,
    })
}

pub async fn verify_payment(
    state: &Arc<AppState>,
    order_id: &str,
) -> crate::error::Result<bool> {
    if order_id.starts_with("dev_") || state.config.is_cashfree_dev() {
        return Ok(true);
    }

    let url = format!("{}/pg/orders/{order_id}", state.config.cashfree_base_url);

    let resp = state
        .http
        .get(&url)
        .header("x-api-version", "2025-01-01")
        .header("x-client-id", &state.config.cashfree_app_id)
        .header("x-client-secret", &state.config.cashfree_secret_key)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("Cashfree verify request error: {e}")))?;

    if !resp.status().is_success() {
        return Ok(false);
    }

    let data: serde_json::Value = resp
        .json()
        .await
        .map_err(|e| AppError::Internal(format!("Cashfree verify parse error: {e}")))?;

    Ok(data["order_status"].as_str() == Some("PAID"))
}

// Defense-in-depth: Cashfree rejects customer_phone values containing
// anything but digits, and legacy/dirty data (e.g. a mobile stored with a
// "+91 xxx-xxxxxxx" format from before auth::normalize_contact normalized on
// write) must not be forwarded as-is, or order creation fails with an opaque
// 500 that masks the real cause.
fn sanitize_phone(mobile: Option<&str>) -> String {
    let Some(mobile) = mobile else { return "0000000000".to_string() };
    let mut digits: String = mobile.chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.len() == 12 && digits.starts_with("91") {
        digits = digits[2..].to_string();
    } else if digits.len() == 11 && digits.starts_with('0') {
        digits = digits[1..].to_string();
    }
    if digits.len() == 10 { digits } else { "0000000000".to_string() }
}

fn random_suffix() -> String {
    let mut rng = rand::thread_rng();
    (0..8)
        .map(|_| rng.sample(rand::distributions::Alphanumeric) as char)
        .collect()
}
