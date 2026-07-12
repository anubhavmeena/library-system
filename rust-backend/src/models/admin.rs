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
    pub joined_at: Option<NaiveDateTime>,
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
    pub dues_amount: Option<Decimal>,
    #[serde(skip)]
    pub current_status: Option<String>,
    #[serde(skip)]
    pub current_end_date: Option<NaiveDate>,
    #[serde(skip)]
    pub latest_ever_status: Option<String>,
    #[sqlx(default)]
    pub display_status: String,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct GalleryPhoto {
    pub id: Uuid,
    pub url: String,
    pub caption: Option<String>,
    pub uploaded_by: Option<String>,
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
    pub errors: Vec<ImportRowError>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportRowError {
    pub row: i32,
    pub name: String,
    pub phone: String,
    pub reason: String,
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
    pub orphaned_seat_memberships: i64,
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
    pub student_id: Option<Uuid>,
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
    pub payment_mode: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeSeatRequest {
    pub seat_number: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SwapSeatRequest {
    pub other_user_id: Uuid,
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
#[serde(rename_all = "camelCase")]
pub struct ClearPendingFeesRequest {
    pub amount_cleared: Decimal,
    pub note: Option<String>,
    pub payment_mode: Option<String>,
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

/// Response for `POST /api/admin/students/import/single-with-photo` — mirrors
/// Java's `ManualImportWithPhotoResponse`.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportWithPhotoResponse {
    pub message: String,
    pub photo_url: Option<String>,
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
    /// Date-only — the frontend's `<input type="date">` sends "YYYY-MM-DD" with
    /// no time component, which doesn't deserialize as NaiveDateTime.
    pub joined_at: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReleaseSeatRequest {
    pub notify_student: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MarkPendingRequest {
    pub pending_amount: Decimal,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClearAmountRequest {
    pub amount_cleared: Decimal,
    pub payment_mode: Option<String>,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct SeatHistoryEntryDto {
    pub membership_id: Uuid,
    pub student_name: String,
    pub student_mobile: Option<String>,
    pub shift: Option<String>,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub status: String,
    pub seat_number: Option<String>,
    pub plan_name: Option<String>,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct GraceDuesStudentItem {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub email: Option<String>,
    pub seat_number: Option<String>,
    pub membership_end: NaiveDate,
    pub dues_amount: Decimal,
}

#[derive(Debug, Serialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct OrphanedSeatItem {
    pub id: Uuid,
    pub name: String,
    pub mobile: Option<String>,
    pub membership_id: Uuid,
    pub membership_end: NaiveDate,
}

// ── Admin Mailbox (IMAP) ──────────────────────────────────────────────────────

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InboxSummary {
    pub message_number: u32,
    pub from: String,
    pub subject: String,
    pub date: String,
    pub is_read: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InboxMessage {
    pub message_number: u32,
    pub from: String,
    pub subject: String,
    pub date: String,
    pub is_read: bool,
    pub body: String,
    pub attachments: Vec<AttachmentInfo>,
}

/// `index` is the attachment's position within its message, re-derived the
/// same deterministic way on both list and download — there's no persisted
/// attachment store, so `GET .../attachments/:index` just re-walks the MIME
/// tree and picks the Nth one again.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AttachmentInfo {
    pub index: usize,
    pub filename: String,
    pub content_type: String,
    pub size: usize,
}

#[derive(Debug, Deserialize)]
pub struct ReplyRequest {
    pub body: String,
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn cash_membership_request_deserializes_camel_case_and_studentid_alias() {
        let user_id = Uuid::new_v4();
        let plan_id = Uuid::new_v4();
        let json = format!(
            r#"{{"studentId":"{user_id}","planId":"{plan_id}","shift":"MORNING","seatNumber":"A1","startDate":"2026-01-01","paidAmount":"400","pendingAmount":"0","paymentMode":"CASH"}}"#
        );
        let req: CashMembershipRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.user_id, user_id);
        assert_eq!(req.plan_id, plan_id);
        assert_eq!(req.shift, "MORNING");
        assert_eq!(req.seat_number, Some("A1".to_string()));
        assert_eq!(req.amount, rust_decimal::Decimal::from(400));
        assert_eq!(req.pending_amount, Some(rust_decimal::Decimal::ZERO));
    }

    #[test]
    fn cash_membership_request_seat_and_pending_are_optional() {
        let json = format!(
            r#"{{"studentId":"{}","planId":"{}","shift":"FULL_DAY","startDate":"2026-01-01","paidAmount":"600"}}"#,
            Uuid::new_v4(), Uuid::new_v4()
        );
        let req: CashMembershipRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.seat_number, None);
        assert_eq!(req.pending_amount, None);
        assert_eq!(req.payment_mode, None);
    }

    #[test]
    fn update_student_status_request_uses_active_field_name() {
        let req: UpdateStudentStatusRequest = serde_json::from_str(r#"{"active":false}"#).unwrap();
        assert!(!req.is_active);
    }

    #[test]
    fn import_student_request_accepts_phone_alias_for_mobile() {
        let req: ImportStudentRequest = serde_json::from_str(r#"{"name":"A","phone":"9876543210"}"#).unwrap();
        assert_eq!(req.mobile, Some("9876543210".to_string()));
    }

    #[test]
    fn student_list_item_display_status_is_not_serialized_from_internal_fields() {
        // current_status/current_end_date/latest_ever_status are #[serde(skip)]
        // (computed server-side into display_status before the row is ever
        // serialized) -- confirm they don't leak into the JSON.
        let item = StudentListItem {
            id: Uuid::new_v4(), name: "A".into(), mobile: None, email: None, photo_url: None,
            aadhaar_url: None, is_active: true, gender: None, address: None, date_of_birth: None,
            joined_at: None, membership_id: None, membership_plan_id: None, plan_name: None,
            seat_number: None, shift: None, membership_start: None, membership_end: None,
            membership_status: None, days_remaining: None, payment_mode: None, pending_amount: None,
            dues_amount: None, current_status: Some("ACTIVE".into()), current_end_date: None,
            latest_ever_status: None, display_status: "PAID".to_string(),
        };
        let json = serde_json::to_string(&item).unwrap();
        assert!(json.contains("displayStatus"));
        assert!(!json.contains("currentStatus"));
        assert!(!json.contains("current_status"));
    }

    #[test]
    fn seat_map_seat_serializes_camel_case() {
        let seat = SeatMapSeat {
            seat_number: "A1".into(), is_occupied: true, student_id: Some(Uuid::new_v4()),
            student_name: Some("Alice".into()), student_mobile: None, student_gender: None,
            shift: Some("MORNING".into()), membership_end: None,
        };
        let json = serde_json::to_string(&seat).unwrap();
        assert!(json.contains("seatNumber"));
        assert!(json.contains("isOccupied"));
        assert!(json.contains("studentId"));
    }
}
