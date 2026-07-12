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

#[cfg(test)]
mod integration_tests {
    use crate::test_support::*;
    use tower::ServiceExt;

    #[tokio::test]
    #[ignore]
    async fn track_records_page_and_defaults_when_body_absent() {
        let state = test_state().await;
        let router = test_router(state.clone());

        let resp = router.clone().oneshot(json_request("POST", "/api/visitor/track", None, serde_json::json!({ "page": "/library" }))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM visitor_events WHERE page = '/library'")
            .fetch_one(&state.db).await.unwrap();
        assert!(count >= 1);

        // no body at all (Java's @RequestBody(required=false) equivalent)
        let req = axum::http::Request::builder()
            .method("POST").uri("/api/visitor/track")
            .body(axum::body::Body::empty()).unwrap();
        let resp = router.clone().oneshot(req).await.unwrap();
        assert_eq!(resp.status(), 200);
        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM visitor_events WHERE page = '/'")
            .fetch_one(&state.db).await.unwrap();
        assert!(count >= 1);
    }
}
