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
        });
    }

    let url = format!("{}/pg/orders", state.config.cashfree_base_url);

    let body = json!({
        "order_id": membership_id.to_string(),
        "order_amount": amount,
        "order_currency": "INR",
        "customer_details": {
            "customer_id": user_id.to_string(),
            "customer_name": user_name,
            "customer_phone": user_mobile.unwrap_or("0000000000"),
            "customer_email": user_email.unwrap_or(""),
        }
    });

    let resp = state
        .http
        .post(&url)
        .header("x-api-version", "2023-08-01")
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
        .header("x-api-version", "2023-08-01")
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

fn random_suffix() -> String {
    let mut rng = rand::thread_rng();
    (0..8)
        .map(|_| rng.sample(rand::distributions::Alphanumeric) as char)
        .collect()
}
