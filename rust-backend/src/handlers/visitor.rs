use crate::{app_state::AppState, response::ApiResponse};
use axum::{body::Bytes, extract::State};
use serde::Deserialize;
use std::sync::Arc;

#[derive(Deserialize)]
pub struct TrackRequest {
    #[serde(default)]
    pub page: Option<String>,
}

/// Matches Java's `@RequestBody(required = false)` — the body, and even the
/// `page` key within it, may be entirely absent; both default to "/".
pub async fn track(State(state): State<Arc<AppState>>, body: Bytes) -> impl axum::response::IntoResponse {
    let page = serde_json::from_slice::<TrackRequest>(&body)
        .ok()
        .and_then(|r| r.page)
        .unwrap_or_else(|| "/".to_string());

    let _ = sqlx::query("INSERT INTO visitor_events (page) VALUES ($1)")
        .bind(&page)
        .execute(&state.db)
        .await;

    ApiResponse::ok("Tracked")
}
