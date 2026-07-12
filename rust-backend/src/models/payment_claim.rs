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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_pay_link_request_deserializes_camel_case() {
        let id = Uuid::new_v4();
        let json = format!(r#"{{"studentId":"{id}","amount":"500"}}"#);
        let req: CreatePayLinkRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.student_id, id);
        assert_eq!(req.amount, Decimal::from(500));
    }

    #[test]
    fn review_payment_claim_request_deserializes_camel_case() {
        let req: ReviewPaymentClaimRequest = serde_json::from_str(r#"{"status":"VERIFIED"}"#).unwrap();
        assert_eq!(req.status, "VERIFIED");
    }

    #[test]
    fn pay_link_info_serializes_camel_case_and_omits_user_membership_ids() {
        let info = PayLinkInfo {
            vpa: "a@b".into(),
            payee_name: "Target Zone".into(),
            amount: Decimal::from(300),
            note: "n".into(),
            claim_type: Some("DUES".into()),
        };
        let json = serde_json::to_string(&info).unwrap();
        assert!(json.contains("payeeName"));
        assert!(json.contains("claimType"));
        assert!(!json.contains("userId"));
        assert!(!json.contains("membershipId"));
    }

    #[test]
    fn payment_claim_serializes_camel_case() {
        let claim = PaymentClaim {
            id: Uuid::new_v4(),
            user_id: Uuid::new_v4(),
            claim_type: "PENDING_FEE".into(),
            membership_id: None,
            amount_claimed: Decimal::from(150),
            screenshot_url: "/uploads/x.png".into(),
            status: "PENDING".into(),
            created_at: chrono::NaiveDateTime::parse_from_str("2026-01-01 10:00:00", "%Y-%m-%d %H:%M:%S").unwrap(),
            reviewed_at: None,
            reviewed_by: None,
        };
        let json = serde_json::to_string(&claim).unwrap();
        assert!(json.contains("claimType"));
        assert!(json.contains("amountClaimed"));
        assert!(json.contains("screenshotUrl"));
        assert!(json.contains("reviewedAt"));
        assert!(json.contains("reviewedBy"));
    }
}
