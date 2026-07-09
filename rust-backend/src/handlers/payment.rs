use crate::{
    app_state::AppState,
    middleware::AuthUser,
    models::membership::{
        CreateOrderRequest, VerifyDuesPaymentRequest, VerifyPaymentRequest, VerifyPendingPaymentRequest,
    },
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
        req.shift.as_deref(),
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

pub async fn create_dues_order(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let order = svc::create_dues_order(&state, user.user_id).await?;
    Ok(ApiResponse::success("Dues order created", order))
}

pub async fn verify_dues_payment(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<VerifyDuesPaymentRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::verify_and_pay_dues(
        &state,
        user.user_id,
        req.membership_id,
        &req.order_id,
        req.payment_id.as_deref(),
        req.signature.as_deref(),
    )
    .await?;
    Ok(ApiResponse::success("Dues cleared, membership active", membership))
}

pub async fn create_pending_order(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let order = svc::create_pending_order(&state, user.user_id).await?;
    Ok(ApiResponse::success("Pending amount order created", order))
}

pub async fn verify_pending_payment(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<VerifyPendingPaymentRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::verify_and_pay_pending(
        &state,
        user.user_id,
        req.membership_id,
        &req.order_id,
        req.payment_id.as_deref(),
        req.signature.as_deref(),
    )
    .await?;
    Ok(ApiResponse::success("Pending amount cleared", membership))
}
