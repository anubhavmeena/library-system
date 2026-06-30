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
    pub status: String,
    #[serde(rename = "createdAt")]
    pub created_at: Option<NaiveDateTime>,
    pub updated_at: Option<NaiveDateTime>,
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
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateOrderRequest {
    pub plan_id: Uuid,
    pub shift: String,
    pub seat_number: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateOrderResponse {
    pub order_id: String,
    pub payment_session_id: Option<String>,
    pub membership_id: Uuid,
    pub amount: Decimal,
    pub gateway: String,
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
