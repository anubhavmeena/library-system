use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AdminUser,
    models::{admin::*, settings::{SaveAppSettingsRequest, UpdateNotificationSettingRequest}},
    response::ApiResponse,
    services::{admin as svc, settings as settings_svc},
};
use axum::{
    extract::{Path, Query, State},
    Json,
};
use std::sync::Arc;
use uuid::Uuid;

// ── Dashboard ─────────────────────────────────────────────────────────────────

pub async fn dashboard(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let stats = svc::get_dashboard(&state).await?;
    Ok(ApiResponse::success("Dashboard stats", stats))
}

// ── Students ──────────────────────────────────────────────────────────────────

pub async fn list_students(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<AdminStudentsQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let page = q.page.unwrap_or(0).max(0);
    let size = q.size.unwrap_or(20);
    let (students, total) = svc::list_students(
        &state, page, size,
        q.search.as_deref(),
        q.status.as_deref(),
        q.membership_status.as_deref(),
        q.sort_by.as_deref(),
        q.sort_dir.as_deref(),
    ).await?;
    Ok(ApiResponse::success(
        "Students retrieved",
        serde_json::json!({ "students": students, "total": total, "page": page, "size": size }),
    ))
}

pub async fn get_student(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let user = svc::get_student(&state, user_id).await?;
    Ok(ApiResponse::success("Student retrieved", user))
}

pub async fn update_student_status(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
    Json(req): Json<UpdateStudentStatusRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::update_student_status(&state, user_id, req.is_active).await?;
    Ok(ApiResponse::ok("Status updated"))
}

pub async fn update_student(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
    Json(req): Json<AdminUpdateStudentRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let user = svc::update_student(&state, user_id, &req).await?;
    Ok(ApiResponse::success("Student updated", user))
}

pub async fn delete_student(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::delete_student(&state, user_id).await?;
    Ok(ApiResponse::ok("Student deleted"))
}

pub async fn get_student_payments(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let payments = svc::get_student_payments(&state, user_id).await?;
    Ok(ApiResponse::success("Payments retrieved", payments))
}

pub async fn get_pending_fees(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let data = svc::get_pending_fees(&state).await?;
    Ok(ApiResponse::success("Pending fees retrieved", data))
}

pub async fn clear_pending_fees(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
    Json(req): Json<ClearPendingFeesRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::clear_pending_fees(&state, user_id, req.amount_cleared, req.payment_mode.as_deref()).await?;
    Ok(ApiResponse::ok("Pending fees cleared"))
}

pub async fn import_student(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<ImportStudentRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let user = svc::import_student(&state, &req).await?;
    Ok(ApiResponse::success("Student imported", user))
}

pub async fn import_student_with_photo(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    mut multipart: axum::extract::Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let mut name: Option<String> = None;
    let mut phone: Option<String> = None;
    let mut photo: Option<(Option<String>, Vec<u8>)> = None;

    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        match field.name().unwrap_or("") {
            "name" => name = Some(field.text().await.map_err(|e| AppError::BadRequest(e.to_string()))?),
            "phone" => phone = Some(field.text().await.map_err(|e| AppError::BadRequest(e.to_string()))?),
            "photo" => {
                let content_type = field.content_type().map(|s| s.to_string());
                let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;
                photo = Some((content_type, data.to_vec()));
            }
            _ => {}
        }
    }

    let name = name.ok_or_else(|| AppError::BadRequest("Name is required".into()))?;
    let phone = phone.ok_or_else(|| AppError::BadRequest("Phone is required".into()))?;

    let result = svc::import_student_with_photo(&state, &name, &phone, photo).await?;
    Ok(ApiResponse::success("Student added successfully", result))
}

// ── Seat map ──────────────────────────────────────────────────────────────────

pub async fn seat_map(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<std::collections::HashMap<String, String>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let shift = q.get("shift").map(|s| s.as_str()).unwrap_or("MORNING");
    let date = q
        .get("date")
        .and_then(|d| chrono::NaiveDate::parse_from_str(d, "%Y-%m-%d").ok())
        .unwrap_or_else(|| chrono::Local::now().date_naive());
    let map = svc::get_seat_map(&state, shift, date).await?;
    Ok(ApiResponse::success("Seat map retrieved", map))
}

// ── Memberships ───────────────────────────────────────────────────────────────

pub async fn expiring_memberships(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<std::collections::HashMap<String, String>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let days: i32 = q.get("withinDays").and_then(|d| d.parse().ok()).unwrap_or(7);
    let data = svc::get_expiring_memberships(&state, days).await?;
    Ok(ApiResponse::success("Expiring memberships", data))
}

pub async fn send_reminders(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<SendRemindersRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let count = svc::send_renewal_reminders(&state, req.user_ids).await?;
    Ok(ApiResponse::success(
        "Reminders sent",
        format!("Sent renewal reminders to {} student(s)", count),
    ))
}

pub async fn send_pending_fee_reminders(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    body: Option<Json<SendRemindersRequest>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let user_ids = body.and_then(|Json(b)| b.user_ids);
    let count = svc::send_pending_fee_reminders(&state, user_ids).await?;
    Ok(ApiResponse::success(
        "Pending fee reminders sent",
        format!("Sent pending fee reminders to {} student(s)", count),
    ))
}

pub async fn send_direct_message(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
    Json(req): Json<DirectMessageRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::send_direct_message(&state, user_id, &req.message).await?;
    Ok(ApiResponse::ok("Message sent"))
}

pub async fn broadcast(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<BroadcastRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let bcast = svc::broadcast(&state, &req.message).await?;
    Ok(ApiResponse::success(
        "Broadcast sent",
        format!("Broadcast sent to {} member(s)", bcast.recipient_count),
    ))
}

pub async fn broadcast_history(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let history = svc::get_broadcast_history(&state).await?;
    Ok(ApiResponse::success("Broadcast history", history))
}

pub async fn create_cash_membership(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<CashMembershipRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let result = svc::create_cash_membership(&state, &req).await?;
    Ok(ApiResponse::success("Cash membership created", result))
}

pub async fn change_membership_seat(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
    Json(req): Json<ChangeSeatRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::change_membership_seat(&state, membership_id, &req.seat_number).await?;
    Ok(ApiResponse::ok("Seat changed"))
}

pub async fn swap_membership_seat(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
    Json(req): Json<SwapSeatRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::swap_membership_seats(&state, membership_id, req.other_user_id).await?;
    Ok(ApiResponse::ok("Seats exchanged"))
}

pub async fn update_membership_plan(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
    Json(req): Json<UpdatePlanRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::update_membership_plan(&state, membership_id, &req).await?;
    Ok(ApiResponse::success("Membership updated", membership))
}

// ── Feedback ──────────────────────────────────────────────────────────────────

pub async fn list_feedback(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<FeedbackQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let feedbacks = svc::get_all_feedback(&state, q.feedback_type.as_deref(), q.status.as_deref()).await?;
    Ok(ApiResponse::success("Feedback retrieved", feedbacks))
}

pub async fn update_feedback(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(feedback_id): Path<Uuid>,
    Json(req): Json<UpdateFeedbackRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let feedback = svc::update_feedback(&state, feedback_id, &req).await?;
    Ok(ApiResponse::success("Feedback updated", feedback))
}

// ── Revenue ───────────────────────────────────────────────────────────────────

/// Java's revenue/breakdown/daily-payments endpoints declare `from`/`to`/`date`
/// as required `@RequestParam String` — Spring 400s automatically if they're
/// absent. Rust's `Query` extractor treats them as optional, so this restores
/// the same required-param strictness instead of silently defaulting to a
/// trailing-30-day (or "today") window the frontend never actually relies on.
fn require_date(value: Option<chrono::NaiveDate>, field: &str) -> crate::error::Result<chrono::NaiveDate> {
    value.ok_or_else(|| AppError::BadRequest(format!("Missing required parameter: {field}")))
}

pub async fn revenue_report(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<RevenueQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let from = require_date(q.from, "from")?;
    let to = require_date(q.to, "to")?;
    let report = svc::get_revenue(&state, from, to).await?;
    Ok(ApiResponse::success("Revenue report", report))
}

pub async fn payment_breakdown(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<RevenueQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let from = require_date(q.from, "from")?;
    let to = require_date(q.to, "to")?;
    let breakdown = svc::get_payment_breakdown(&state, from, to).await?;
    Ok(ApiResponse::success("Payment breakdown", breakdown))
}

pub async fn daily_payments(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<std::collections::HashMap<String, String>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let date_str = q.get("date").map(|s| s.as_str()).unwrap_or("");
    let date: chrono::NaiveDate = date_str
        .parse()
        .map_err(|_| AppError::BadRequest("Missing required parameter: date".into()))?;

    let payments = sqlx::query_as::<_, DailyPaymentItem>(
        r#"SELECT u.name AS student_name,
                  u.mobile AS student_mobile,
                  p.amount,
                  p.payment_gateway,
                  COALESCE(p.gateway_payment_id, p.gateway_order_id) AS reference_id,
                  p.created_at AS paid_at
           FROM payments p
           JOIN users u ON u.id = p.user_id
           WHERE p.status = 'SUCCESS' AND DATE(p.created_at) = $1
           ORDER BY p.created_at"#,
    )
    .bind(date)
    .fetch_all(&state.db)
    .await?;

    Ok(ApiResponse::success("Daily payments", payments))
}

// ── Expenses ──────────────────────────────────────────────────────────────────

pub async fn get_expenses(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<ExpenseQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let expense = svc::get_expenses(&state, q.year, q.month).await?;
    Ok(ApiResponse::success("Expenses retrieved", expense))
}

pub async fn bulk_import(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    mut multipart: axum::extract::Multipart,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        let name = field.name().unwrap_or("").to_string();
        if name != "file" { continue; }
        let filename = field.file_name().unwrap_or("import.csv").to_string();
        let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;
        let result = svc::bulk_import_students(&state, &data, &filename).await?;
        return Ok(ApiResponse::success("Import complete", result));
    }
    Err(AppError::BadRequest("No file provided".into()))
}

pub async fn save_expense(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<SaveExpenseRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let expense = svc::save_expense(&state, &req).await?;
    Ok(ApiResponse::success("Expense saved", expense))
}

// ── App / Notification settings ──────────────────────────────────────────────

pub async fn get_app_settings(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let settings = settings_svc::get_app_settings(&state).await?;
    Ok(ApiResponse::success("Settings retrieved", settings))
}

pub async fn save_app_settings(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Json(req): Json<SaveAppSettingsRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let settings = settings_svc::save_app_settings(&state, &req).await?;
    Ok(ApiResponse::success("Settings saved", settings))
}

pub async fn get_notification_settings(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let settings = settings_svc::get_notification_settings(&state).await?;
    Ok(ApiResponse::success("Notification settings retrieved", settings))
}

pub async fn update_notification_setting(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(key): Path<String>,
    Json(req): Json<UpdateNotificationSettingRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let updated = settings_svc::update_notification_setting(&state, &key, &req).await?;
    Ok(ApiResponse::success("Notification setting updated", updated))
}

// ── Grace / dues admin actions ───────────────────────────────────────────────

pub async fn release_seat(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
    Json(req): Json<ReleaseSeatRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::release_seat(&state, membership_id, req.notify_student).await?;
    Ok(ApiResponse::ok("Seat released"))
}

pub async fn renew_seat(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let membership = svc::renew_seat(&state, membership_id).await?;
    Ok(ApiResponse::success("Membership renewed", membership))
}

pub async fn mark_membership_pending(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
    Json(req): Json<MarkPendingRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::mark_membership_pending(&state, membership_id, req.pending_amount).await?;
    Ok(ApiResponse::ok("Membership marked pending"))
}

pub async fn mark_membership_grace(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::mark_membership_grace(&state, membership_id).await?;
    Ok(ApiResponse::ok("Membership marked grace"))
}

pub async fn clear_dues(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(membership_id): Path<Uuid>,
    Json(req): Json<ClearAmountRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::clear_dues(&state, membership_id, req.amount_cleared, req.payment_mode.as_deref()).await?;
    Ok(ApiResponse::ok("Dues cleared"))
}

pub async fn run_expiry_check(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let count = svc::run_expiry_check(&state).await?;
    Ok(ApiResponse::success(
        "Expiry check complete",
        format!("{count} membership(s) transitioned to grace"),
    ))
}

pub async fn orphaned_seats(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let data = svc::get_orphaned_seats(&state).await?;
    Ok(ApiResponse::success("Orphaned seats retrieved", data))
}

pub async fn grace_dues_students(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let data = svc::get_grace_dues_students(&state).await?;
    Ok(ApiResponse::success("Grace dues students retrieved", data))
}

pub async fn send_grace_dues_reminders(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    body: Option<Json<SendRemindersRequest>>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let user_ids = body.and_then(|Json(b)| b.user_ids);
    let count = svc::send_grace_dues_reminders(&state, user_ids).await?;
    Ok(ApiResponse::success(
        "Grace dues reminders sent",
        format!("Sent grace dues reminders to {count} student(s)"),
    ))
}

// ── Seat / student history ────────────────────────────────────────────────────

pub async fn seat_history(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(seat_number): Path<String>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let data = svc::get_seat_history(&state, &seat_number).await?;
    Ok(ApiResponse::success("Seat history retrieved", data))
}

pub async fn student_seat_history(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Path(user_id): Path<Uuid>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let data = svc::get_student_seat_history(&state, user_id).await?;
    Ok(ApiResponse::success("Student seat history retrieved", data))
}
