use chrono::{NaiveDate, NaiveDateTime};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct StudentListItem {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub photo_url: Option<String>,
    pub aadhaar_url: Option<String>,
    pub is_active: bool,
    pub gender: Option<String>,
    pub address: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub joined_at: NaiveDateTime,
    pub membership_id: Option<Uuid>,
    pub membership_plan_id: Option<Uuid>,
    pub plan_name: Option<String>,
    pub seat_number: Option<String>,
    pub shift: Option<String>,
    pub membership_start: Option<NaiveDate>,
    pub membership_end: Option<NaiveDate>,
    pub membership_status: Option<String>,
    pub days_remaining: Option<i32>,
    pub payment_mode: Option<String>,
    pub pending_amount: Option<Decimal>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct GalleryPhoto {
    pub id: Uuid,
    pub url: String,
    pub caption: Option<String>,
    pub uploaded_by: Option<Uuid>,
    pub uploaded_at: Option<NaiveDateTime>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct NotificationLog {
    pub id: Uuid,
    pub user_id: Option<Uuid>,
    pub recipient: Option<String>,
    pub message: Option<String>,
    pub event: Option<String>,
    pub channel: Option<String>,
    pub status: Option<String>,
    pub error_message: Option<String>,
    pub sent_at: Option<NaiveDateTime>,
    pub created_at: Option<NaiveDateTime>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct MonthlyExpense {
    pub id: Uuid,
    pub year: i32,
    pub month: i32,
    pub water_tanker_qty: i32,
    pub water_tanker_price: Decimal,
    pub electricity_bill: Decimal,
    pub internet_bill: Decimal,
    pub miscellaneous: Decimal,
    pub created_at: Option<NaiveDateTime>,
    pub updated_at: Option<NaiveDateTime>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct MiscExpenseItem {
    pub id: Uuid,
    pub monthly_expense_id: Uuid,
    pub description: String,
    pub amount: Decimal,
    pub sort_order: Option<i32>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MonthlyExpenseWithItems {
    #[serde(flatten)]
    pub expense: MonthlyExpense,
    pub misc_items: Vec<MiscExpenseItem>,
    pub total: Decimal,
}

#[derive(Debug, Deserialize)]
pub struct ExpenseQuery {
    pub year: i32,
    pub month: i32,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportResult {
    pub imported: i32,
    pub skipped: i32,
    pub total_rows: i32,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct BroadcastMessage {
    pub id: Uuid,
    pub message: String,
    pub recipient_count: i32,
    pub sent_at: Option<NaiveDateTime>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DashboardStats {
    pub total_students: i64,
    pub active_students: i64,
    pub active_memberships: i64,
    pub expired_memberships: i64,
    pub expiring_this_week: i64,
    pub total_seats: i64,
    pub occupied_seats: i64,
    pub available_seats: i64,
    pub revenue_today: Decimal,
    pub revenue_this_month: Decimal,
    pub payments_this_month: i64,
    pub total_visitors: i64,
    pub visitors_today: i64,
}

#[derive(Debug, Serialize)]
pub struct AdminStudentDetail {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub photo_url: Option<String>,
    pub is_active: bool,
    pub created_at: NaiveDateTime,
    pub active_membership: Option<AdminMembershipSummary>,
    pub pending_amount: Option<Decimal>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct AdminMembershipSummary {
    pub id: Uuid,
    pub plan_name: String,
    pub seat_number: Option<String>,
    pub shift: Option<String>,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub status: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SeatMapSeat {
    pub seat_number: String,
    pub is_occupied: bool,
    pub student_name: Option<String>,
    pub student_mobile: Option<String>,
    pub student_gender: Option<String>,
    pub shift: Option<String>,
    pub membership_end: Option<NaiveDate>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSeatMapResponse {
    pub shift: String,
    pub date: NaiveDate,
    pub seats_by_row: std::collections::HashMap<String, Vec<SeatMapSeat>>,
    pub occupied_seats: i64,
    pub available_seats: i64,
    pub total_seats: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RevenueReport {
    pub from_date: NaiveDate,
    pub to_date: NaiveDate,
    pub total_revenue: Decimal,
    pub total_transactions: i64,
    pub half_day_revenue: Decimal,
    pub full_day_revenue: Decimal,
    pub daily_breakdown: Vec<DailyRevenue>,
}

#[derive(Debug, Serialize, FromRow)]
pub struct DailyRevenue {
    pub date: NaiveDate,
    pub amount: Decimal,
    pub count: i64,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct PaymentBreakdownItem {
    pub gateway: Option<String>,
    pub amount: Decimal,
    pub count: i64,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct DailyPaymentItem {
    pub student_name: String,
    pub student_mobile: Option<String>,
    pub amount: Decimal,
    pub payment_gateway: Option<String>,
    pub reference_id: Option<String>,
    pub paid_at: Option<NaiveDateTime>,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct ExpiringMembershipItem {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub seat_number: Option<String>,
    pub membership_end: NaiveDate,
    pub days_remaining: i32,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct PendingFeeItem {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub seat_number: Option<String>,
    pub membership_end: Option<NaiveDate>,
    pub pending_amount: Decimal,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveExpenseRequest {
    pub year: i32,
    pub month: i32,
    pub water_tanker_qty: Option<i32>,
    pub water_tanker_price: Option<Decimal>,
    pub electricity_bill: Option<Decimal>,
    pub internet_bill: Option<Decimal>,
    pub misc_items: Option<Vec<MiscExpenseItemRequest>>,
}

#[derive(Debug, Deserialize)]
pub struct MiscExpenseItemRequest {
    pub description: String,
    pub amount: Decimal,
    pub sort_order: Option<i32>,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AdminFeedbackItem {
    pub id: Uuid,
    pub user_id: Uuid,
    pub student_name: String,
    pub student_mobile: Option<String>,
    #[serde(rename = "type")]
    pub feedback_type: String,
    pub subject: String,
    pub description: String,
    pub status: String,
    pub admin_notes: Option<String>,
    pub created_at: Option<NaiveDateTime>,
    pub updated_at: Option<NaiveDateTime>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateFeedbackRequest {
    pub status: Option<String>,
    pub admin_notes: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct BroadcastRequest {
    pub message: String,
}

#[derive(Debug, Deserialize)]
pub struct DirectMessageRequest {
    pub message: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SendRemindersRequest {
    pub user_ids: Option<Vec<Uuid>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CashMembershipRequest {
    #[serde(rename = "studentId")]
    pub user_id: Uuid,
    pub plan_id: Uuid,
    pub shift: String,
    pub seat_number: Option<String>,
    pub start_date: NaiveDate,
    #[serde(rename = "paidAmount")]
    pub amount: Decimal,
    pub pending_amount: Option<Decimal>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeSeatRequest {
    pub seat_number: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdatePlanRequest {
    pub plan_id: Option<Uuid>,
    pub additional_days: Option<i32>,
    pub end_date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct AdminStudentsQuery {
    pub page: Option<i64>,
    pub size: Option<i64>,
    pub search: Option<String>,
    pub status: Option<String>,
    #[serde(rename = "sortBy")]
    pub sort_by: Option<String>,
    #[serde(rename = "sortDir")]
    pub sort_dir: Option<String>,
    #[serde(rename = "membershipStatus")]
    pub membership_status: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct FeedbackQuery {
    #[serde(rename = "type")]
    pub feedback_type: Option<String>,
    pub status: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct RevenueQuery {
    pub from: Option<NaiveDate>,
    pub to: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct ClearPendingFeesRequest {
    pub note: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateStudentStatusRequest {
    #[serde(rename = "active")]
    pub is_active: bool,
}

#[derive(Debug, Deserialize)]
pub struct ImportStudentRequest {
    pub name: String,
    #[serde(alias = "phone")]
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub address: Option<String>,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminUpdateStudentRequest {
    pub name: Option<String>,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub address: Option<String>,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub joined_at: Option<NaiveDateTime>,
}
