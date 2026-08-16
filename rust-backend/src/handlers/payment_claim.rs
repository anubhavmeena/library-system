use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AdminUser,
    models::payment_claim::{CreatePayLinkRequest, ReviewPaymentClaimRequest},
    response::ApiResponse,
    services::{activity_log as alog, payment_claim as svc},
};
use axum::{
    extract::{Multipart, Path, Query, State},
    Json,
};
use std::sync::Arc;
use uuid::Uuid;

/// Public — no AuthUser/AdminUser. Identity comes entirely from the short
/// linkId minted into the reminder's /pay link (see upi_pay::create_pay_link).
pub async fn submit_claim(
    State(state): State<Arc<AppState>>,
    mut multipart: Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let mut link_id: Option<String> = None;
    let mut file: Option<(String, Option<String>, Vec<u8>)> = None;

    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        match field.name().unwrap_or("") {
            "linkId" => link_id = Some(field.text().await.map_err(|e| AppError::BadRequest(e.to_string()))?),
            "file" => {
                let filename = field.file_name().unwrap_or("screenshot.jpg").to_string();
                let content_type = field.content_type().map(|s| s.to_string());
                let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;
                file = Some((filename, content_type, data.to_vec()));
            }
            _ => {}
        }
    }

    let link_id = link_id.ok_or_else(|| AppError::BadRequest("linkId is required".into()))?;
    let (filename, content_type, data) = file.ok_or_else(|| AppError::BadRequest("Screenshot file is required".into()))?;

    let claim = svc::submit_claim(&state, &link_id, &filename, content_type.as_deref(), &data).await?;

    Ok(ApiResponse::success("Payment verification submitted", claim))
}

/// Public — backs PayRedirectPage.jsx, which resolves the short id into the
/// UPI params it needs to build the upi://pay deep link client-side.
pub async fn get_pay_link(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let info = svc::get_pay_link_info(&state, &id).await?;
    Ok(ApiResponse::success("Payment link resolved", info))
}

/// Admin-only — backs the ad-hoc "Send Payment Request" button on Create
/// Membership. Returns a short link only; the caller builds/sends the
/// WhatsApp message itself via the existing generic message endpoint.
pub async fn create_pay_link(
    State(state): State<Arc<AppState>>,
    admin: AdminUser,
    Json(req): Json<CreatePayLinkRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let link = svc::create_ad_hoc_pay_link(&state, req.student_id, req.amount).await?;
    let label = alog::user_label(&state, req.student_id).await;
    alog::log_activity(&state, &admin.0, "CREATE_PAY_LINK", "student", Some(req.student_id.to_string()),
        format!("Created a ₹{} payment link for {label}", req.amount)).await;
    Ok(ApiResponse::success("Payment link created", serde_json::json!({ "link": link })))
}

pub async fn list_claims(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<std::collections::HashMap<String, String>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let status = q.get("status").map(|s| s.as_str());
    let claims = svc::list_claims(&state, status).await?;
    Ok(ApiResponse::success("Payment claims retrieved", claims))
}

pub async fn review_claim(
    State(state): State<Arc<AppState>>,
    admin: AdminUser,
    Path(id): Path<Uuid>,
    Json(req): Json<ReviewPaymentClaimRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let claim = svc::review_claim(&state, id, admin.0.user_id, &req.status).await?;
    let label = alog::user_label(&state, claim.user_id).await;
    alog::log_activity(&state, &admin.0, "REVIEW_PAYMENT_CLAIM", "payment_claim", Some(id.to_string()),
        format!("Reviewed payment claim from {label}: {}", claim.status)).await;
    Ok(ApiResponse::success("Payment claim reviewed", claim))
}

#[cfg(test)]
mod integration_tests {
    use crate::{services::upi_pay, test_support::*};
    use serde_json::json;
    use tower::ServiceExt;

    fn extract_link_id(link: &str) -> String {
        // link looks like "<frontend_url>/pay?id=<id>"
        link.split("id=").nth(1).unwrap().to_string()
    }

    #[tokio::test]
    #[ignore]
    async fn ad_hoc_pay_link_create_and_resolve() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let admin = admin_token(&state).await;
        let (user_id, _t) = create_test_user(&state, "STUDENT", "Pay Link Target").await;

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/admin/pay-links", Some(&admin), json!({ "studentId": user_id, "amount": "500" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        let link = body["data"]["link"].as_str().unwrap().to_string();
        let link_id = extract_link_id(&link);

        let resp = router.clone().oneshot(get_request(&format!("/api/pay/{link_id}"), None)).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["amount"], "500");
        assert_eq!(body["data"]["claimType"], serde_json::Value::Null, "ad-hoc admin links carry no claim_type");

        // unknown link id
        let resp = router.clone().oneshot(get_request("/api/pay/does-not-exist", None)).await.unwrap();
        assert_eq!(resp.status(), 404);

        // non-admin cannot create pay links
        let (_id2, token2) = create_test_user(&state, "STUDENT", "Not Admin PayLink").await;
        let resp = router.clone().oneshot(json_request(
            "POST", "/api/admin/pay-links", Some(&token2), json!({ "studentId": user_id, "amount": "500" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 403);
    }

    #[tokio::test]
    #[ignore]
    async fn ad_hoc_link_rejects_claim_submission() {
        let state = test_state().await;
        let admin = admin_token(&state).await;
        let router = test_router(state.clone());
        let (user_id, _t) = create_test_user(&state, "STUDENT", "No Claim Allowed").await;

        let resp = router.clone().oneshot(json_request(
            "POST", "/api/admin/pay-links", Some(&admin), json!({ "studentId": user_id, "amount": "300" }),
        )).await.unwrap();
        let link = body_json(resp).await["data"]["link"].as_str().unwrap().to_string();
        let link_id = extract_link_id(&link);

        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/payments/claims", None,
            vec![text_field("linkId", &link_id), file_field("file", "shot.png", "image/png", tiny_png_bytes())],
        )).await.unwrap();
        assert_eq!(resp.status(), 400);
    }

    /// The claim-upload workflow is normally reached via a reminder-generated
    /// link (send_grace_dues_reminders/send_pending_fee_reminders), which
    /// stashes a `claim_type` the ad-hoc admin link deliberately omits. Rather
    /// than reproducing the whole reminder+notification side channel just to
    /// get a link id, mint one directly through the same upi_pay service the
    /// reminders call internally.
    #[tokio::test]
    #[ignore]
    async fn full_claim_submission_and_admin_review_lifecycle() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let admin = admin_token(&state).await;
        let (user_id, _t) = create_test_user(&state, "STUDENT", "Claim Submitter").await;

        // PENDING_FEE only needs a real outstanding pending_amount for the
        // user (unlike DUES, which needs a real GRACE membership_id) --
        // simplest real setup for exercising review_claim's VERIFIED path.
        let (plan_id, price, _days): (uuid::Uuid, rust_decimal::Decimal, i32) =
            sqlx::query_as("SELECT id, price, duration_days FROM membership_plans WHERE name = 'Half Day'")
                .fetch_one(&state.db).await.unwrap();
        let pending = rust_decimal::Decimal::from(150);
        let paid = price - pending;
        let today = chrono::Local::now().date_naive();
        let seat = free_seat_today(&state, "MORNING").await;
        router.clone().oneshot(json_request(
            "POST", "/api/admin/memberships/cash", Some(&admin),
            json!({ "studentId": user_id, "planId": plan_id, "shift": "MORNING", "seatNumber": seat, "startDate": today, "paidAmount": paid.to_string(), "pendingAmount": pending.to_string(), "paymentMode": "CASH" }),
        )).await.unwrap();

        let link = upi_pay::create_pay_link(&state, &upi_pay::PayLinkPayload {
            user_id,
            claim_type: Some("PENDING_FEE".to_string()),
            membership_id: None,
            amount: pending,
            vpa: "testlibrary@ybl".to_string(),
            payee_name: "Target Zone Library".to_string(),
            note: "Pending fee".to_string(),
        }).await.unwrap();
        let link_id = extract_link_id(&link);

        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/payments/claims", None,
            vec![text_field("linkId", &link_id), file_field("file", "shot.png", "image/png", tiny_png_bytes())],
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["status"], "PENDING");
        let claim_id = body["data"]["id"].as_str().unwrap().to_string();

        // a second claim submission for the same still-pending claim type must be rejected
        let link2 = upi_pay::create_pay_link(&state, &upi_pay::PayLinkPayload {
            user_id,
            claim_type: Some("PENDING_FEE".to_string()),
            membership_id: None,
            amount: pending,
            vpa: "testlibrary@ybl".to_string(),
            payee_name: "Target Zone Library".to_string(),
            note: "Pending fee".to_string(),
        }).await.unwrap();
        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/payments/claims", None,
            vec![text_field("linkId", &extract_link_id(&link2)), file_field("file", "shot2.png", "image/png", tiny_png_bytes())],
        )).await.unwrap();
        assert_eq!(resp.status(), 409);

        // admin sees it in the PENDING list
        let resp = router.clone().oneshot(get_request("/api/admin/payment-claims?status=PENDING", Some(&admin))).await.unwrap();
        let body = body_json(resp).await;
        assert!(body["data"].as_array().unwrap().iter().any(|c| c["id"] == claim_id));

        // invalid review status
        let resp = router.clone().oneshot(json_request(
            "PATCH", &format!("/api/admin/payment-claims/{claim_id}"), Some(&admin), json!({ "status": "MAYBE" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 400);

        // approve it
        let resp = router.clone().oneshot(json_request(
            "PATCH", &format!("/api/admin/payment-claims/{claim_id}"), Some(&admin), json!({ "status": "VERIFIED" }),
        )).await.unwrap();
        assert_eq!(resp.status(), 200);
        let body = body_json(resp).await;
        assert_eq!(body["data"]["status"], "VERIFIED");

        // non-admin cannot list or review claims
        let (_id2, token2) = create_test_user(&state, "STUDENT", "Not Admin Claims").await;
        let resp = router.clone().oneshot(get_request("/api/admin/payment-claims", Some(&token2))).await.unwrap();
        assert_eq!(resp.status(), 403);
    }

    #[tokio::test]
    #[ignore]
    async fn submit_claim_requires_link_id_and_file() {
        let state = test_state().await;
        let router = test_router(state.clone());
        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/payments/claims", None, vec![text_field("linkId", "whatever-not-real")],
        )).await.unwrap();
        assert_eq!(resp.status(), 400);

        let resp = router.clone().oneshot(multipart_request(
            "POST", "/api/payments/claims", None, vec![file_field("file", "shot.png", "image/png", tiny_png_bytes())],
        )).await.unwrap();
        assert_eq!(resp.status(), 400);
    }
}
