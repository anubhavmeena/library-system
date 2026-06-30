use crate::{
    app_state::AppState,
    error::AppError,
    middleware::AdminUser,
    models::admin::*,
    response::ApiResponse,
    services::admin as svc,
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
    Json(_req): Json<ClearPendingFeesRequest>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    svc::clear_pending_fees(&state, user_id).await?;
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

pub async fn revenue_report(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<RevenueQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let report = svc::get_revenue(&state, q.from, q.to).await?;
    Ok(ApiResponse::success("Revenue report", report))
}

pub async fn payment_breakdown(
    State(state): State<Arc<AppState>>,
    _admin: AdminUser,
    Query(q): Query<RevenueQuery>,
) -> crate::error::Result<impl axum::response::IntoResponse> {
    let breakdown = svc::get_payment_breakdown(&state, q.from, q.to).await?;
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
        .unwrap_or_else(|_| chrono::Local::now().date_naive());

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
        let data = field.bytes().await.map_err(|e| AppError::BadRequest(e.to_string()))?;
        let result = svc::bulk_import_students(&state, &data).await?;
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
