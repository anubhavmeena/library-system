use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AuthUser,
    response::ApiResponse,
    services::{idcard, membership as svc, notification},
};
use axum::{
    body::Body,
    extract::State,
    http::{header, StatusCode},
    response::Response,
};
use bytes::Bytes;
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
    // Falls back to a GRACE membership when there's no ACTIVE one — the
    // frontend's "pay your dues" banner depends on this endpoint returning
    // the GRACE row rather than nothing.
    let membership = svc::find_current_membership(&state, user.user_id).await?;
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

pub async fn get_my_status(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let status = svc::get_my_display_status(&state, user.user_id).await?;
    Ok(ApiResponse::success("Status retrieved", status))
}

pub async fn call_admin(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::find_current_membership(&state, user.user_id)
        .await?
        .ok_or_else(|| AppError::BadRequest("No active membership found".into()))?;

    let seat_number = membership
        .seat_number
        .ok_or_else(|| AppError::BadRequest("No seat assigned to your membership".into()))?;

    let state2 = state.clone();
    let name = user.name.clone();
    let seat = seat_number.clone();
    tokio::spawn(async move {
        notification::send_seat_assistance(&state2, &name, &seat).await;
    });

    Ok(ApiResponse::success("Admin has been notified", ()))
}

pub async fn download_id_card(
    State(state): State<Arc<AppState>>,
    user: AuthUser,
) -> crate::error::Result<Response> {
    let pdf = idcard::generate(&state, user.user_id).await?;
    let response = Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, "application/pdf")
        .header(
            header::CONTENT_DISPOSITION,
            r#"attachment; filename="id-card.pdf""#,
        )
        .body(Body::from(Bytes::from(pdf)))
        .map_err(|e| AppError::Internal(e.to_string()))?;
    Ok(response)
}

#[cfg(test)]
mod integration_tests {
    use crate::test_support::*;
    use tower::ServiceExt;
    use serde_json::json;

    async fn get_plan(state: &std::sync::Arc<crate::app_state::AppState>, name: &str) -> serde_json::Value {
        let router = test_router(state.clone());
        let resp = router.oneshot(get_request("/api/plans", None)).await.unwrap();
        let body = body_json(resp).await;
        body["data"].as_array().unwrap().iter().find(|p| p["name"] == name).unwrap().clone()
    }

    async fn book_and_pay(
        router: &axum::Router, token: &str, plan_id: &str, shift: Option<&str>, seat: Option<&str>,
    ) -> serde_json::Value {
        let mut body = json!({ "planId": plan_id });
        if let Some(s) = shift { body["shift"] = json!(s); }
        if let Some(s) = seat { body["seatNumber"] = json!(s); }
        let resp = router.clone().oneshot(json_request("POST", "/api/payments/create-order", Some(token), body)).await.unwrap();
        assert_eq!(resp.status(), 200, "create-order should succeed");
        let order = body_json(resp).await;
        let membership_id = order["data"]["membershipId"].as_str().unwrap().to_string();
        let order_id = order["data"]["orderId"].as_str().unwrap().to_string();
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/verify", Some(token),
            json!({ "gatewayOrderId": order_id, "gatewayPaymentId": "dev_pay_x", "signature": "dev_sig", "membershipId": membership_id }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200, "verify should succeed");
        body_json(resp).await
    }

    #[tokio::test]
    #[ignore]
    async fn plans_endpoint_is_public_and_camel_case() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let resp = router.clone().oneshot(get_request("/api/plans", None)).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        let plans = body["data"].as_array().unwrap();
        assert!(!plans.is_empty());
        assert!(plans[0].get("durationDays").is_some());
        assert!(plans[0].get("convenienceFee").is_some());
    }

    #[tokio::test]
    #[ignore]
    async fn fresh_booking_activates_and_assigns_the_requested_seat() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Fresh Booker").await;
        let plan = get_plan(&state, "Full Day").await;
        let seat = free_seat_today(&state, "EVENING").await;

        let verified = book_and_pay(&router, &token, plan["id"].as_str().unwrap(), Some("EVENING"), Some(&seat)).await;
        assert_eq!(verified["data"]["status"], "ACTIVE");
        assert_eq!(verified["data"]["seatNumber"], seat, "seat confirmed free moments ago should be assigned outright");

        // no shift on a fresh (non-renewal) booking must be rejected -- a
        // *different* student, since the first one now has an active
        // membership and an omitted shift there would mean "queue a renewal"
        // instead (shift/seat legitimately optional in that case).
        let (_id2, token2) = create_test_user(&state, "STUDENT", "Fresh Booker No Shift").await;
        let plan2 = get_plan(&state, "Full Day").await;
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/create-order", Some(&token2), json!({ "planId": plan2["id"] }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    /// Regression test for the fixed bug: verify_payment used to build its
    /// response from a `membership` value captured *before* the seat-conflict
    /// decline ran, so a client whose seat got silently un-assigned (someone
    /// else already held it) was falsely told they got the seat they asked
    /// for. Now the in-memory value is kept in sync with what was actually
    /// written to the DB either way.
    #[tokio::test]
    #[ignore]
    async fn verify_payment_response_matches_db_truth_on_seat_conflict() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id_a, token_a) = create_test_user(&state, "STUDENT", "Seat Holder").await;
        let (_id_b, token_b) = create_test_user(&state, "STUDENT", "Seat Contender").await;
        let plan = get_plan(&state, "Full Day").await;

        // Rather than betting a randomly-picked seat is free (this shared dev
        // DB already carries a lot of ad-hoc test data), guarantee the
        // conflict deterministically: user A books *some* seat, then read
        // back from the DB whichever seat A actually ended up holding a real
        // ACTIVE booking on for today -- that's the one guaranteed occupied,
        // regardless of whether A's own request happened to collide with
        // still-earlier data.
        let seat = unique_seat_number();
        book_and_pay(&router, &token_a, plan["id"].as_str().unwrap(), Some("MORNING"), Some(&seat)).await;

        let today = chrono::Local::now().date_naive();
        let occupied_seat: String = sqlx::query_scalar(
            "SELECT s.seat_number FROM seat_bookings sb JOIN seats s ON s.id = sb.seat_id
             WHERE sb.status = 'ACTIVE' AND sb.booking_date <= $1 AND sb.end_date >= $1
               AND (sb.shift = 'MORNING' OR sb.shift = 'FULL_DAY')
             ORDER BY (s.seat_number = $2) DESC LIMIT 1",
        )
        .bind(today)
        .bind(&seat)
        .fetch_one(&state.db)
        .await
        .expect("at least one MORNING/FULL_DAY seat should be occupied today after booking one");

        let second = book_and_pay(&router, &token_b, plan["id"].as_str().unwrap(), Some("MORNING"), Some(&occupied_seat)).await;
        let membership_id: uuid::Uuid = second["data"]["id"].as_str().unwrap().parse().unwrap();
        let db_seat_number: Option<String> = sqlx::query_scalar("SELECT seat_number FROM memberships WHERE id = $1")
            .bind(membership_id).fetch_one(&state.db).await.unwrap();

        assert_eq!(second["data"]["status"], "ACTIVE", "payment itself still succeeds despite the seat conflict");
        assert_eq!(db_seat_number, None, "seat conflict must clear seat_number in the DB");
        assert_eq!(second["data"]["seatNumber"], serde_json::Value::Null, "API response must match DB truth, not the originally-requested seat");
    }

    /// Regression test for the fixed bug: a paid renewal used to flip to
    /// ACTIVE immediately, leaving two ACTIVE rows for the same student and
    /// causing the nightly expiry job to later find no QUEUED sibling and
    /// wrongly push the (already-superseded, already-paid) original into
    /// GRACE with a fresh dues charge. Now the renewal stays QUEUED until the
    /// original genuinely expires.
    #[tokio::test]
    #[ignore]
    async fn paid_renewal_stays_queued_until_original_expires_no_bogus_grace() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Renewer").await;
        let admin = admin_token(&state).await;
        let plan = get_plan(&state, "Full Day").await;
        let seat = free_seat_today(&state, "MORNING").await;

        let original = book_and_pay(&router, &token, plan["id"].as_str().unwrap(), Some("MORNING"), Some(&seat)).await;
        let original_id = original["data"]["id"].as_str().unwrap().to_string();

        // queue a renewal while the original is still active (no shift/seat: inherited)
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/create-order", Some(&token), json!({ "planId": plan["id"] }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let order = body_json(resp).await;
        let renewal_id = order["data"]["membershipId"].as_str().unwrap().to_string();

        let renewal_status_before: String = sqlx::query_scalar("SELECT status FROM memberships WHERE id = $1::uuid")
            .bind(&renewal_id).fetch_one(&state.db).await.unwrap();
        assert_eq!(renewal_status_before, "QUEUED");

        // a second concurrent renewal attempt must be rejected
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/create-order", Some(&token), json!({ "planId": plan["id"] }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400, "a student may only have one QUEUED renewal at a time");

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/verify", Some(&token),
            json!({ "gatewayOrderId": order["data"]["orderId"], "gatewayPaymentId": "dev_pay_x", "signature": "dev_sig", "membershipId": renewal_id }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let verified = body_json(resp).await;
        assert_eq!(verified["data"]["status"], "QUEUED", "a paid renewal must stay QUEUED, not jump to ACTIVE");

        let original_status: String = sqlx::query_scalar("SELECT status FROM memberships WHERE id = $1::uuid")
            .bind(&original_id).fetch_one(&state.db).await.unwrap();
        assert_eq!(original_status, "ACTIVE", "exactly one ACTIVE membership should exist for this student at a time");

        // simulate elapsed time via the ordinary admin end-date edit, then run the real cron logic
        let yesterday = (chrono::Local::now().date_naive() - chrono::Duration::days(1)).to_string();
        let resp = router.clone().oneshot(json_request(
            "PATCH", &format!("/api/admin/memberships/{original_id}/plan"), Some(&admin),
            json!({ "endDate": yesterday }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/admin/memberships/run-expiry-check", Some(&admin), json!({}),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);

        let (original_final, original_dues): (String, Option<rust_decimal::Decimal>) =
            sqlx::query_as("SELECT status, dues_amount FROM memberships WHERE id = $1::uuid")
                .bind(&original_id).fetch_one(&state.db).await.unwrap();
        let renewal_final: String = sqlx::query_scalar("SELECT status FROM memberships WHERE id = $1::uuid")
            .bind(&renewal_id).fetch_one(&state.db).await.unwrap();

        assert_eq!(original_final, "EXPIRED", "the original should simply expire, not enter GRACE");
        assert!(original_dues.unwrap_or_default() == rust_decimal::Decimal::ZERO, "no bogus dues charge on the already-paid-for original");
        assert_eq!(renewal_final, "ACTIVE", "expiry-check should cleanly promote the QUEUED renewal");
    }

    #[tokio::test]
    #[ignore]
    async fn dues_and_pending_self_serve_flows() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Dues And Pending").await;
        let admin = admin_token(&state).await;
        let plan = get_plan(&state, "Full Day").await;
        let seat = free_seat_today(&state, "EVENING").await;

        let verified = book_and_pay(&router, &token, plan["id"].as_str().unwrap(), Some("EVENING"), Some(&seat)).await;
        let membership_id = verified["data"]["id"].as_str().unwrap().to_string();

        // no dues yet -> dues-order must be rejected
        let resp = router.clone().oneshot(json_request("POST", "/api/payments/dues/create-order", Some(&token), json!({}))).await.unwrap();
        assert_eq!(resp.status(), 400);

        // admin forces GRACE (mirrors what the nightly job would eventually do)
        let resp = router.clone().oneshot(json_request(
            "PATCH", &format!("/api/admin/memberships/{membership_id}/mark-grace"), Some(&admin), json!({}),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);

        // a fresh purchase attempt must now be blocked by the unresolved GRACE
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/create-order", Some(&token), json!({ "planId": plan["id"], "shift": "EVENING" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);

        let resp = router.clone().oneshot(json_request("POST", "/api/payments/dues/create-order", Some(&token), json!({}))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let order = body_json(resp).await;
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/payments/dues/verify", Some(&token),
            json!({ "gatewayOrderId": order["data"]["orderId"], "gatewayPaymentId": "x", "signature": "y", "membershipId": membership_id }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["status"], "ACTIVE");
        let dues: rust_decimal::Decimal = body["data"]["duesAmount"].as_str().unwrap().parse().unwrap();
        assert_eq!(dues, rust_decimal::Decimal::ZERO);

        // no pending amount yet -> pending-order must be rejected
        let resp = router.clone().oneshot(json_request("POST", "/api/payments/pending/create-order", Some(&token), json!({}))).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    #[tokio::test]
    #[ignore]
    async fn my_status_and_queued_and_all_memberships() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Status Checker").await;

        let resp = router.clone().oneshot(get_request("/api/memberships/my/status", Some(&token))).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"], "NEW", "brand-new student with no membership history reads NEW");

        let resp = router.clone().oneshot(get_request("/api/memberships/my", Some(&token))).await.unwrap();
        let body = body_json(resp).await;
        assert_eq!(body["data"], serde_json::Value::Null);

        let resp = router.clone().oneshot(get_request("/api/memberships/my/queued", Some(&token))).await.unwrap();
        let body = body_json(resp).await;
        assert_eq!(body["data"], serde_json::Value::Null);

        let resp = router.clone().oneshot(get_request("/api/memberships/my/all", Some(&token))).await.unwrap();
        let body = body_json(resp).await;
        assert_eq!(body["data"].as_array().unwrap().len(), 0);
    }

    #[tokio::test]
    #[ignore]
    async fn call_admin_without_a_membership_is_rejected() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "No Membership Caller").await;
        let resp = router.clone().oneshot(json_request("POST", "/api/memberships/my/call-admin", Some(&token), json!({}))).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    #[tokio::test]
    #[ignore]
    async fn id_card_download_requires_an_active_membership() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "No Card Yet").await;
        let resp = router.clone().oneshot(get_request("/api/memberships/my/id-card", Some(&token))).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    #[tokio::test]
    #[ignore]
    async fn id_card_download_succeeds_for_active_membership() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let (_id, token) = create_test_user(&state, "STUDENT", "Has A Card").await;
        let plan = get_plan(&state, "Full Day").await;
        let seat = free_seat_today(&state, "FULL_DAY").await;
        book_and_pay(&router, &token, plan["id"].as_str().unwrap(), Some("FULL_DAY"), Some(&seat)).await;

        let resp = router.clone().oneshot(get_request("/api/memberships/my/id-card", Some(&token))).await.unwrap();
        assert_eq!(resp.status(), 200);
        assert_eq!(resp.headers().get("content-type").unwrap(), "application/pdf");
        let bytes = axum::body::to_bytes(resp.into_body(), usize::MAX).await.unwrap();
        assert!(bytes.starts_with(b"%PDF"), "response body should be a real PDF");
    }

    #[tokio::test]
    #[ignore]
    async fn membership_endpoints_require_auth() {
        let state = test_state().await;
        let router = test_router(state.clone());
        for uri in ["/api/memberships/my", "/api/memberships/my/all", "/api/memberships/my/queued", "/api/memberships/my/status"] {
            let resp = router.clone().oneshot(get_request(uri, None)).await.unwrap();
            assert_eq!(resp.status(), 401, "{uri} must require auth");
        }
    }
}
