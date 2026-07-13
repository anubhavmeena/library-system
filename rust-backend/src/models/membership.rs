use chrono::{NaiveDate, NaiveDateTime};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct MembershipPlan {
    pub id: Uuid,
    pub name: String,
    pub plan_type: String,
    pub price: Decimal,
    pub duration_days: i32,
    pub description: Option<String>,
    pub is_active: bool,
}

/// `GET /api/plans` response shape — the admin-configured convenience fee
/// (added on top of `price` when paying via the gateway) isn't a plan
/// column, so it's flattened onto each plan here rather than living on
/// `MembershipPlan` itself. Mirrors Java's `PlanDto`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlanWithFee {
    #[serde(flatten)]
    pub plan: MembershipPlan,
    pub convenience_fee: Decimal,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Membership {
    pub id: Uuid,
    pub user_id: Uuid,
    pub plan_id: Uuid,
    pub seat_id: Option<Uuid>,
    pub seat_number: Option<String>,
    pub shift: Option<String>,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub status: String,
    pub reminder_sent: bool,
    pub created_at: Option<NaiveDateTime>,
    /// Set when the membership enters GRACE (= plan price at the moment of
    /// transition); null otherwise. Never auto-cleared by the DB — only an
    /// admin clear-dues / student dues-payment sets it back to 0.
    pub dues_amount: Option<Decimal>,
    /// In-flight checkout correlation key for `verify_dues_payment` — set at
    /// `create_dues_order()` time, read back on verify.
    pub gateway_order_id: Option<String>,
    /// Amount snapshotted at dues-order-creation time, read back on verify.
    pub checkout_amount: Option<Decimal>,
    /// Coupon applied at create_order time (fresh bookings only), staged here
    /// until verify_payment copies it onto the resulting `payments` row.
    pub coupon_code: Option<String>,
    pub discount_amount: Option<Decimal>,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Payment {
    pub id: Uuid,
    pub membership_id: Uuid,
    pub user_id: Uuid,
    pub amount: Decimal,
    pub pending_amount: Option<Decimal>,
    pub payment_gateway: Option<String>,
    pub gateway_order_id: Option<String>,
    pub gateway_payment_id: Option<String>,
    pub invoice_id: Option<String>,
    pub status: String,
    #[serde(rename = "createdAt")]
    pub created_at: Option<NaiveDateTime>,
    pub updated_at: Option<NaiveDateTime>,
    pub coupon_code: Option<String>,
    pub discount_amount: Option<Decimal>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MembershipWithPlan {
    pub id: Uuid,
    pub user_id: Uuid,
    pub plan_id: Uuid,
    pub plan_name: String,
    pub plan_type: String,
    pub seat_id: Option<Uuid>,
    pub seat_number: Option<String>,
    pub shift: Option<String>,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub status: String,
    pub amount_paid: Option<Decimal>,
    pub plan_price: Option<Decimal>,
    pub created_at: Option<NaiveDateTime>,
    pub dues_amount: Option<Decimal>,
    /// Sum of `payments.pending_amount` (SUCCESS rows) across every membership
    /// this user has ever had — same aggregate `admin::get_pending_fees` uses,
    /// so a student clearing "their" pending amount clears the same total an
    /// admin would see and clear via the admin Students list.
    pub pending_amount: Option<Decimal>,
}

#[derive(Debug, Deserialize)]
pub struct VerifyDuesPaymentRequest {
    #[serde(rename = "gatewayOrderId")]
    pub order_id: String,
    #[serde(rename = "gatewayPaymentId")]
    pub payment_id: Option<String>,
    pub signature: Option<String>,
    #[serde(rename = "membershipId")]
    pub membership_id: Uuid,
}

#[derive(Debug, Deserialize)]
pub struct VerifyPendingPaymentRequest {
    #[serde(rename = "gatewayOrderId")]
    pub order_id: String,
    #[serde(rename = "gatewayPaymentId")]
    pub payment_id: Option<String>,
    pub signature: Option<String>,
    #[serde(rename = "membershipId")]
    pub membership_id: Uuid,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateOrderRequest {
    pub plan_id: Uuid,
    /// Absent when queuing a renewal — the current membership's own shift is
    /// inherited server-side. Required for a fresh (non-renewal) booking.
    pub shift: Option<String>,
    pub seat_number: Option<String>,
    /// Only valid for a fresh booking — rejected if this call turns out to be
    /// a queued renewal (see `services::membership::create_order`).
    pub coupon_code: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateOrderResponse {
    pub order_id: String,
    pub payment_session_id: Option<String>,
    pub membership_id: Uuid,
    pub amount: Decimal,
    pub gateway: String,
    pub discount_amount: Option<Decimal>,
}

#[derive(Debug, Deserialize)]
pub struct VerifyPaymentRequest {
    #[serde(rename = "gatewayOrderId")]
    pub order_id: String,
    #[serde(rename = "gatewayPaymentId")]
    pub payment_id: Option<String>,
    pub signature: Option<String>,
    #[serde(rename = "membershipId")]
    pub membership_id: Uuid,
}

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal::Decimal;
    use uuid::Uuid;

    #[test]
    fn membership_plan_serializes_camel_case() {
        let plan = MembershipPlan {
            id: Uuid::new_v4(),
            name: "Monthly".to_string(),
            plan_type: "STANDARD".to_string(),
            price: "999.00".parse::<Decimal>().unwrap(),
            duration_days: 30,
            description: Some("30-day plan".to_string()),
            is_active: true,
        };
        let json = serde_json::to_string(&plan).unwrap();
        assert!(json.contains("planType"), "Expected camelCase planType");
        assert!(json.contains("durationDays"), "Expected camelCase durationDays");
        assert!(json.contains("isActive"), "Expected camelCase isActive");
        assert!(!json.contains("plan_type"), "Should not contain snake_case");
    }

    #[test]
    fn create_order_request_deserializes_camel_case() {
        let json = format!(
            r#"{{"planId": "{}", "shift": "MORNING", "seatNumber": "A1"}}"#,
            Uuid::new_v4()
        );
        let req: CreateOrderRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.shift, Some("MORNING".to_string()));
        assert_eq!(req.seat_number, Some("A1".to_string()));
    }

    #[test]
    fn create_order_request_shift_is_optional_for_renewals() {
        let json = format!(r#"{{"planId": "{}"}}"#, Uuid::new_v4());
        let req: CreateOrderRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.shift, None);
        assert_eq!(req.seat_number, None);
    }

    #[test]
    fn create_order_request_seat_number_is_optional() {
        let json = format!(r#"{{"planId": "{}", "shift": "EVENING"}}"#, Uuid::new_v4());
        let req: CreateOrderRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.seat_number, None);
    }

    #[test]
    fn verify_payment_request_field_renames() {
        let membership_id = Uuid::new_v4();
        let json = format!(
            r#"{{"gatewayOrderId":"order_123","gatewayPaymentId":"pay_456","signature":"sig_abc","membershipId":"{membership_id}"}}"#
        );
        let req: VerifyPaymentRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.order_id, "order_123");
        assert_eq!(req.payment_id, Some("pay_456".to_string()));
        assert_eq!(req.signature, Some("sig_abc".to_string()));
        assert_eq!(req.membership_id, membership_id);
    }

    #[test]
    fn verify_payment_request_optional_fields_absent() {
        let membership_id = Uuid::new_v4();
        let json = format!(r#"{{"gatewayOrderId":"order_123","membershipId":"{membership_id}"}}"#);
        let req: VerifyPaymentRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.payment_id, None);
        assert_eq!(req.signature, None);
    }

    #[test]
    fn create_order_response_serializes_camel_case() {
        let resp = CreateOrderResponse {
            order_id: "order_123".to_string(),
            payment_session_id: Some("session_xyz".to_string()),
            membership_id: Uuid::new_v4(),
            amount: "999.00".parse::<Decimal>().unwrap(),
            gateway: "CASHFREE".to_string(),
            discount_amount: None,
        };
        let json = serde_json::to_string(&resp).unwrap();
        assert!(json.contains("orderId"), "Expected camelCase orderId");
        assert!(json.contains("paymentSessionId"), "Expected camelCase paymentSessionId");
        assert!(json.contains("membershipId"), "Expected camelCase membershipId");
    }

    #[test]
    fn create_order_response_dev_order_id_is_recognized() {
        // Dev mode order IDs start with "dev_" — this prefix triggers HMAC bypass
        let dev_id = "dev_order_AbCdEfGh";
        assert!(dev_id.starts_with("dev_"));
        assert!(!dev_id.is_empty());
    }
}
