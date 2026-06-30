use crate::{
    app_state::AppState,
    middleware::{AdminUser, AuthUser},
    models::seat::{AdminBookingsQuery, BookSeatRequest, SeatAvailabilityQuery},
    response::ApiResponse,
    services::seat as svc,
};
use axum::{
    extract::{Path, Query, State},
    Json,
};
use chrono::Local;
use std::sync::Arc;
use uuid::Uuid;

pub async fn get_availability(
    State(state): State<Arc<AppState>>,
    Query(q): Query<SeatAvailabilityQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let date = q.date.unwrap_or_else(|| Local::now().date_naive());
    let availability = svc::get_availability(&state, &q.shift, date).await?;
    Ok(ApiResponse::success("Seat availability retrieved", availability))
}

pub async fn book_seat(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
    Json(req): Json<BookSeatRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let booking = svc::book_seat(
        &state,
        user.user_id,
        req.membership_id,
        &req.seat_number,
        &req.shift,
        req.start_date,
        req.end_date,
    )
    .await?;
    Ok(ApiResponse::success("Seat booked", booking))
}

pub async fn release_booking(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::release_booking(&state, membership_id).await?;
    Ok(ApiResponse::ok("Seat booking released"))
}

pub async fn get_my_bookings(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let bookings = svc::get_my_bookings(&state, user.user_id).await?;
    Ok(ApiResponse::success("Bookings retrieved", bookings))
}

pub async fn get_admin_bookings(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<AdminBookingsQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let bookings = svc::get_admin_bookings(&state, &q.shift, q.date).await?;
    Ok(ApiResponse::success("Bookings retrieved", bookings))
}
