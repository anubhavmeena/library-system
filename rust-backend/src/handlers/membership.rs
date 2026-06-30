use crate::{
    app_state::AppState,
    middleware::AuthUser,
    response::ApiResponse,
    services::membership as svc,
};
use axum::extract::State;
use std::sync::Arc;

pub async fn list_plans(
    State(state): State<Arc<AppState>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let plans = svc::list_active_plans(&state).await?;
    Ok(ApiResponse::success("Plans retrieved", plans))
}

pub async fn get_my_membership(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::get_active_membership(&state, user.user_id).await?;
    Ok(ApiResponse::success("Membership retrieved", membership))
}

pub async fn get_my_all_memberships(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let memberships = svc::get_all_memberships(&state, user.user_id).await?;
    Ok(ApiResponse::success("Memberships retrieved", memberships))
}

pub async fn get_my_queued_membership(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::get_queued_membership(&state, user.user_id).await?;
    Ok(ApiResponse::success("Queued membership retrieved", membership))
}

pub async fn call_admin(
    State(state): State<Arc<AppState>>,
    _user: AuthUser,
) -> impl axum::response::IntoResponse {
    let contact = crate::services::user::get_admin_contact(&state).await;
    ApiResponse::success("Admin contact", contact)
}
