pub mod cashfree;
pub mod razorpay;

use crate::{app_state::AppState, models::membership::CreateOrderResponse};
use rust_decimal::Decimal;
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
    match state.config.payment_gateway.as_str() {
        "RAZORPAY" => razorpay::create_order(state, membership_id, amount).await,
        _ => {
            cashfree::create_order(
                state,
                membership_id,
                user_id,
                user_mobile,
                user_email,
                user_name,
                amount,
            )
            .await
        }
    }
}

pub async fn verify_payment(
    state: &Arc<AppState>,
    order_id: &str,
    payment_id: Option<&str>,
    signature: Option<&str>,
) -> crate::error::Result<bool> {
    match state.config.payment_gateway.as_str() {
        "RAZORPAY" => {
            razorpay::verify_payment(state, order_id, payment_id, signature)
        }
        _ => cashfree::verify_payment(state, order_id).await,
    }
}
