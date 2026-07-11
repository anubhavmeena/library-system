use chrono::NaiveDateTime;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct PaymentClaim {
    pub id: Uuid,
    pub user_id: Uuid,
    pub claim_type: String,
    pub membership_id: Option<Uuid>,
    pub amount_claimed: Decimal,
    pub screenshot_url: String,
    pub status: String,
    pub created_at: NaiveDateTime,
    pub reviewed_at: Option<NaiveDateTime>,
    pub reviewed_by: Option<Uuid>,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AdminPaymentClaimItem {
    pub id: Uuid,
    pub user_id: Uuid,
    pub student_name: String,
    pub student_mobile: Option<String>,
    pub claim_type: String,
    pub membership_id: Option<Uuid>,
    pub amount_claimed: Decimal,
    pub screenshot_url: String,
    pub status: String,
    pub created_at: NaiveDateTime,
    pub reviewed_at: Option<NaiveDateTime>,
    pub reviewed_by: Option<Uuid>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReviewPaymentClaimRequest {
    pub status: String,
}

/// Display-safe subset of upi_pay::PayLinkPayload returned by the public
/// GET /api/pay/:id — deliberately omits userId/membershipId, which the
/// frontend never needs (the linkId alone identifies the student server-side
/// when a claim is later submitted).
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PayLinkInfo {
    pub vpa: String,
    pub payee_name: String,
    pub amount: Decimal,
    pub note: String,
    pub claim_type: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreatePayLinkRequest {
    pub student_id: Uuid,
    pub amount: Decimal,
}
