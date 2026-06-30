use crate::{app_state::AppState, response::ApiResponse};
use axum::{extract::State, Json};
use serde::Deserialize;
use std::sync::Arc;

#[derive(Deserialize)]
pub struct TrackRequest {
    pub page: String,
}

pub async fn track(
    State(state): State<Arc<AppState>>,
    Json(req): Json<TrackRequest>,
) -> impl axum::response::IntoResponse {
    let _ = sqlx::query("INSERT INTO visitor_events (page) VALUES ($1)")
        .bind(&req.page)
        .execute(&state.db)
        .await;

    ApiResponse::ok("Tracked")
}
