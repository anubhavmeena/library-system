use crate::{
    app_state::AppState,
    middleware::AuthUser,
    models::membership::{CreateOrderRequest, VerifyPaymentRequest},
    response::ApiResponse,
    services::membership as svc,
};
use axum::{extract::State, Json};
use std::sync::Arc;

pub async fn get_payment_history(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let payments = svc::get_payment_history(&state, user.user_id).await?;
    Ok(ApiResponse::success("Payments retrieved", payments))
}

pub async fn create_order(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<CreateOrderRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let order = svc::create_order(
        &state,
        user.user_id,
        req.plan_id,
        &req.shift,
        req.seat_number.as_deref(),
    )
    .await?;
    Ok(ApiResponse::success("Order created", order))
}

pub async fn verify_payment(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<VerifyPaymentRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::verify_payment(
        &state,
        user.user_id,
        req.membership_id,
        &req.order_id,
        req.payment_id.as_deref(),
        req.signature.as_deref(),
    )
    .await?;
    Ok(ApiResponse::success("Payment verified, membership activated", membership))
}
