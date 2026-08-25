use crate::{
    app_state::AppState,
    error::AppError,
    models::{
        membership::Membership,
        payment_claim::{AdminPaymentClaimItem, PaymentClaim},
    },
    services::{admin, notification, upi_pay, user as user_svc},
};
use rust_decimal::Decimal;
use std::sync::Arc;
use uuid::Uuid;

/// Resolves the short pay-link id (see upi_pay::resolve_pay_link) to find
/// out who's submitting and what for, checks for an existing PENDING claim
/// of the same type (blocks accidental duplicate submission), saves the
/// screenshot via the same upload machinery as profile photos, and notifies
/// the admin. Doesn't touch dues/pending state itself — that only happens
/// once an admin reviews and verifies the claim.
pub async fn submit_claim(
    state: &Arc<AppState>,
    link_id: &str,
    filename: &str,
    content_type: Option<&str>,
    file_data: &[u8],
) -> crate::error::Result<PaymentClaim> {
    let link = upi_pay::resolve_pay_link(state, link_id).await?;
    let user_id = link.user_id;
    let membership_id = link.membership_id;
    let amount = link.amount;
    let claim_type = link.claim_type.as_deref().ok_or_else(|| {
        AppError::BadRequest("This payment link doesn't support claim submission".into())
    })?;

    let existing: Option<Uuid> = sqlx::query_scalar(
        "SELECT id FROM payment_verification_claims
         WHERE user_id = $1 AND claim_type = $2 AND status = 'PENDING'",
    )
    .bind(user_id)
    .bind(claim_type)
    .fetch_optional(&state.db)
    .await?;
    if existing.is_some() {
        return Err(AppError::Conflict(
            "You already have a pending payment verification for this — please wait for the admin to review it.".into(),
        ));
    }

    user_svc::validate_upload(
        content_type,
        file_data,
        user_svc::IMAGE_CONTENT_TYPES,
        "Invalid file type. Only JPEG, PNG, WebP allowed.",
    )?;
    let screenshot_url = user_svc::save_file(
        &state.config.upload_dir,
        user_id,
        "payment_claim",
        filename,
        file_data,
    )
    .await?;

    let claim = sqlx::query_as::<_, PaymentClaim>(
        "INSERT INTO payment_verification_claims (user_id, claim_type, membership_id, amount_claimed, screenshot_url)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING *",
    )
    .bind(user_id)
    .bind(claim_type)
    .bind(membership_id)
    .bind(amount)
    .bind(&screenshot_url)
    .fetch_one(&state.db)
    .await?;

    let student_name: String = sqlx::query_scalar("SELECT name FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .unwrap_or_else(|| "A student".to_string());

    let admin_msg = format!(
        "💰 Payment verification needed — {student_name} claims ₹{amount:.2} ({claim_type}) paid via UPI. \
Review at {}/admin/payment-verifications",
        state.config.frontend_url
    );
    if !state.config.admin_whatsapp.is_empty() {
        notification::send_whatsapp_to(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
    }

    Ok(claim)
}

pub async fn get_pay_link_info(state: &Arc<AppState>, link_id: &str) -> crate::error::Result<crate::models::payment_claim::PayLinkInfo> {
    let link = upi_pay::resolve_pay_link(state, link_id).await?;
    Ok(crate::models::payment_claim::PayLinkInfo {
        vpa: link.vpa,
        payee_name: link.payee_name,
        amount: link.amount,
        note: link.note,
        claim_type: link.claim_type,
    })
}

/// Backs the admin's ad-hoc "Send Payment Request" on Create Membership —
/// no claim_type/membership_id, since that flow doesn't use the
/// upload-a-screenshot verification workflow (the admin is present to
/// confirm the payment themselves via the existing checkbox+submit).
pub async fn create_ad_hoc_pay_link(
    state: &Arc<AppState>,
    student_id: Uuid,
    amount: rust_decimal::Decimal,
) -> crate::error::Result<String> {
    let app_settings = crate::services::settings::get_app_settings(state).await?;
    let payee_name = crate::services::settings::upi_payee_name(Some(&app_settings));
    let vpa = app_settings.upi_id.filter(|v| !v.is_empty()).ok_or_else(|| {
        AppError::BadRequest("Set a UPI ID in Settings first".into())
    })?;
    let student_name: String = sqlx::query_scalar("SELECT name FROM users WHERE id = $1")
        .bind(student_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Student not found".into()))?;

    upi_pay::create_pay_link(state, &upi_pay::PayLinkPayload {
        user_id: student_id,
        claim_type: None,
        membership_id: None,
        amount,
        vpa,
        payee_name,
        note: format!("Library fee - {student_name}"),
    }).await
}

pub async fn list_claims(
    state: &Arc<AppState>,
    status_filter: Option<&str>,
) -> crate::error::Result<Vec<AdminPaymentClaimItem>> {
    sqlx::query_as::<_, AdminPaymentClaimItem>(
        r#"SELECT c.id, c.user_id,
                  u.name  AS student_name,
                  u.mobile AS student_mobile,
                  c.claim_type, c.membership_id, c.amount_claimed, c.screenshot_url,
                  c.status, c.created_at, c.reviewed_at, c.reviewed_by
           FROM payment_verification_claims c
           JOIN users u ON u.id = c.user_id
           WHERE ($1::text IS NULL OR c.status = $1)
           ORDER BY c.created_at DESC"#,
    )
    .bind(status_filter)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn review_claim(
    state: &Arc<AppState>,
    claim_id: Uuid,
    admin_user_id: Uuid,
    new_status: &str,
) -> crate::error::Result<PaymentClaim> {
    if !matches!(new_status, "VERIFIED" | "REJECTED") {
        return Err(AppError::BadRequest(format!(
            "Invalid status '{new_status}' — must be VERIFIED or REJECTED"
        )));
    }

    let claim = sqlx::query_as::<_, PaymentClaim>(
        "SELECT * FROM payment_verification_claims WHERE id = $1",
    )
    .bind(claim_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Payment claim not found".into()))?;

    if claim.status != "PENDING" {
        return Err(AppError::BadRequest(format!(
            "This claim has already been {}",
            claim.status.to_lowercase()
        )));
    }

    if new_status == "VERIFIED" {
        match claim.claim_type.as_str() {
            "DUES" => {
                let membership_id = claim.membership_id.ok_or_else(|| {
                    AppError::Internal("DUES claim missing membership_id".into())
                })?;
                let membership = sqlx::query_as::<_, Membership>(
                    "SELECT * FROM memberships WHERE id = $1",
                )
                .bind(membership_id)
                .fetch_optional(&state.db)
                .await?
                .ok_or_else(|| AppError::NotFound("Linked membership not found".into()))?;

                if membership.status == "GRACE" {
                    admin::clear_dues(state, membership_id, claim.amount_claimed, Some("UPI-QR")).await?;
                } else if membership.dues_amount.unwrap_or(Decimal::ZERO) <= Decimal::ZERO {
                    // Dues were already cleared through another channel (e.g. a
                    // cash payment taken at the desk) before this UPI proof got
                    // reviewed. Don't re-run clear_dues — it would double-apply
                    // the +1 month extension — just close this claim out as
                    // verified since the underlying debt is already gone.
                } else {
                    return Err(AppError::Conflict(format!(
                        "Linked membership is no longer in GRACE (now {}) and still has dues outstanding — resolve manually before verifying this claim",
                        membership.status
                    )));
                }
            }
            "PENDING_FEE" => {
                let total_pending: Option<Decimal> = sqlx::query_scalar(
                    "SELECT SUM(pending_amount) FROM payments
                     WHERE user_id = $1 AND status = 'SUCCESS' AND pending_amount > 0",
                )
                .bind(claim.user_id)
                .fetch_one(&state.db)
                .await?;

                if total_pending.unwrap_or(Decimal::ZERO) > Decimal::ZERO {
                    admin::clear_pending_fees(state, claim.user_id, claim.amount_claimed, Some("UPI-QR")).await?;
                }
                // else: pending fees already cleared through another channel —
                // close the claim out as verified without re-applying anything.
            }
            other => return Err(AppError::Internal(format!("Unknown claim_type '{other}'"))),
        }
    }

    let updated = sqlx::query_as::<_, PaymentClaim>(
        "UPDATE payment_verification_claims
         SET status = $1, reviewed_at = NOW(), reviewed_by = $2
         WHERE id = $3
         RETURNING *",
    )
    .bind(new_status)
    .bind(admin_user_id)
    .bind(claim_id)
    .fetch_one(&state.db)
    .await?;

    let user = sqlx::query_as::<_, crate::models::user::User>("SELECT * FROM users WHERE id = $1")
        .bind(claim.user_id)
        .fetch_optional(&state.db)
        .await?;
    if let Some(user) = user {
        let msg = if new_status == "VERIFIED" {
            format!(
                "✅ Your payment of ₹{:.2} has been verified and applied. Thank you! - Target Zone Library Team",
                claim.amount_claimed
            )
        } else {
            format!(
                "❌ We couldn't verify your recent UPI payment of ₹{:.2}. Please contact the library. - Target Zone Library Team",
                claim.amount_claimed
            )
        };
        notification::send_direct_message(state, user.mobile.as_deref(), user.email.as_deref(), &msg).await;
    }

    Ok(updated)
}
