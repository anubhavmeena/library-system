use crate::{
    app_state::AppState,
    error::AppError,
    models::{
        admin::*,
        membership::{Membership, MembershipPlan},
        user::User,
    },
    services::{ids, notification, settings, upi_pay},
};
use chrono::NaiveDate;
use rust_decimal::Decimal;
use std::sync::Arc;
use uuid::Uuid;

// ── Dashboard ─────────────────────────────────────────────────────────────────

pub async fn get_dashboard(state: &Arc<AppState>) -> crate::error::Result<DashboardStats> {
    let total_students: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM users WHERE role = 'STUDENT'")
            .fetch_one(&state.db)
            .await?;

    let active_students: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = true")
            .fetch_one(&state.db)
            .await?;

    let active_memberships: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM memberships WHERE status = 'ACTIVE'")
            .fetch_one(&state.db)
            .await?;

    let expiring_this_week: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM memberships WHERE status = 'ACTIVE'
         AND end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'",
    )
    .fetch_one(&state.db)
    .await?;

    let total_seats: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM seats WHERE is_active = true")
            .fetch_one(&state.db)
            .await?;

    // Matches Java's `occupiedSeats = activeMem` exactly — the dashboard counts
    // occupancy by membership, not by actual seat_bookings rows, which is
    // exactly why `orphaned_seat_memberships` (an ACTIVE membership with no
    // matching seat_bookings row — see `get_orphaned_seats`) is tracked as
    // its own separate metric rather than folded into this count.
    let occupied_seats = active_memberships;

    let orphaned_seat_memberships: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM memberships m
         WHERE m.status = 'ACTIVE'
           AND NOT EXISTS (
               SELECT 1 FROM seat_bookings sb
               WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
           )",
    )
    .fetch_one(&state.db)
    .await?;

    let revenue_today: Option<Decimal> = sqlx::query_scalar(
        "SELECT SUM(amount) FROM payments WHERE status = 'SUCCESS' AND DATE(created_at) = CURRENT_DATE",
    )
    .fetch_one(&state.db)
    .await?;

    let revenue_this_month: Option<Decimal> = sqlx::query_scalar(
        "SELECT SUM(amount) FROM payments WHERE status = 'SUCCESS'
         AND DATE_TRUNC('month', created_at) = DATE_TRUNC('month', NOW())",
    )
    .fetch_one(&state.db)
    .await?;

    let payments_this_month: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM payments WHERE status = 'SUCCESS'
         AND DATE_TRUNC('month', created_at) = DATE_TRUNC('month', NOW())",
    )
    .fetch_one(&state.db)
    .await?;

    let total_visitors: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM visitor_events")
            .fetch_one(&state.db)
            .await?;

    let visitors_today: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM visitor_events WHERE DATE(created_at) = CURRENT_DATE",
    )
    .fetch_one(&state.db)
    .await?;

    let expired_memberships: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM memberships WHERE status = 'EXPIRED'")
            .fetch_one(&state.db)
            .await?;

    Ok(DashboardStats {
        total_students,
        active_students,
        active_memberships,
        expired_memberships,
        expiring_this_week,
        orphaned_seat_memberships,
        total_seats,
        occupied_seats,
        available_seats: (total_seats - occupied_seats).max(0),
        revenue_today: revenue_today.unwrap_or_default(),
        revenue_this_month: revenue_this_month.unwrap_or_default(),
        payments_this_month,
        total_visitors,
        visitors_today,
    })
}

// ── Students ──────────────────────────────────────────────────────────────────

const STUDENT_SELECT: &str = "
    SELECT
        u.id, u.name, u.mobile, u.email, u.photo_url, u.aadhaar_url,
        u.is_active, u.created_at AS joined_at, u.gender, u.address, u.date_of_birth,
        m.id AS membership_id, m.plan_id AS membership_plan_id, mp.name AS plan_name,
        CASE WHEN m.status IN ('ACTIVE', 'GRACE') THEN COALESCE(m.seat_number, (
            SELECT s.seat_number FROM seat_bookings sb
            JOIN seats s ON s.id = sb.seat_id
            WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
            LIMIT 1
        )) END AS seat_number, m.shift,
        m.start_date AS membership_start, m.end_date AS membership_end,
        m.status AS membership_status,
        (m.end_date - CURRENT_DATE)::int AS days_remaining,
        CASE WHEN p.payment_gateway = 'CASH' THEN 'CASH'
             WHEN p.payment_gateway = 'UPI-QR' THEN 'UPI-QR'
             WHEN p.payment_gateway IS NOT NULL THEN 'ONLINE-PG'
             ELSE NULL END AS payment_mode,
        ps.pending_amount,
        m.dues_amount,
        cur.status AS current_status, cur.end_date AS current_end_date,
        le.status AS latest_ever_status
    FROM users u
    LEFT JOIN LATERAL (
        -- Never PENDING: an abandoned/never-paid-for checkout must not read as
        -- \"has a membership\" — matches Java's getAllStudents (mem is only
        -- ACTIVE/GRACE, falling back to the latest non-PENDING row otherwise).
        -- GRACE outranks ACTIVE, mirroring the `cur` lateral below.
        SELECT * FROM memberships WHERE user_id = u.id AND status != 'PENDING'
        ORDER BY
            CASE WHEN status = 'GRACE' THEN 0 WHEN status = 'ACTIVE' THEN 1 ELSE 2 END,
            CASE WHEN status IN ('ACTIVE', 'GRACE') THEN end_date END DESC,
            created_at DESC
        LIMIT 1
    ) m ON true
    LEFT JOIN membership_plans mp ON mp.id = m.plan_id
    LEFT JOIN LATERAL (
        SELECT payment_gateway FROM payments
        WHERE membership_id = m.id AND status = 'SUCCESS'
        ORDER BY created_at DESC LIMIT 1
    ) p ON true
    LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(pending_amount), 0)::numeric AS pending_amount
        FROM payments WHERE user_id = u.id AND status = 'SUCCESS'
    ) ps ON true
    LEFT JOIN LATERAL (
        SELECT status, end_date FROM memberships
        WHERE user_id = u.id AND status IN ('ACTIVE', 'GRACE')
        ORDER BY CASE WHEN status = 'GRACE' THEN 0 ELSE 1 END, end_date DESC
        LIMIT 1
    ) cur ON true
    LEFT JOIN LATERAL (
        SELECT status FROM memberships
        WHERE user_id = u.id AND status != 'PENDING'
        ORDER BY created_at DESC LIMIT 1
    ) le ON true";

const STUDENT_COUNT_FROM: &str = "
    SELECT COUNT(*) FROM users u
    LEFT JOIN LATERAL (
        -- SELECT * (not just status) so the seat-number search predicate below
        -- can read m.seat_number/m.id, matching STUDENT_SELECT's `m` lateral.
        SELECT * FROM memberships WHERE user_id = u.id AND status != 'PENDING'
        ORDER BY
            CASE WHEN status = 'GRACE' THEN 0 WHEN status = 'ACTIVE' THEN 1 ELSE 2 END,
            CASE WHEN status IN ('ACTIVE', 'GRACE') THEN end_date END DESC,
            created_at DESC
        LIMIT 1
    ) m ON true
    LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(pending_amount), 0)::numeric AS pending_amount
        FROM payments WHERE user_id = u.id AND status = 'SUCCESS'
    ) ps ON true
    LEFT JOIN LATERAL (
        SELECT status, end_date FROM memberships
        WHERE user_id = u.id AND status IN ('ACTIVE', 'GRACE')
        ORDER BY CASE WHEN status = 'GRACE' THEN 0 ELSE 1 END, end_date DESC
        LIMIT 1
    ) cur ON true
    LEFT JOIN LATERAL (
        SELECT status FROM memberships
        WHERE user_id = u.id AND status != 'PENDING'
        ORDER BY created_at DESC LIMIT 1
    ) le ON true";

// Mirrors the `seat_number` projection in STUDENT_SELECT so seat-based search
// matches exactly what the admin sees in the Seat & Shift column — only a
// currently-held (ACTIVE/GRACE) seat is searchable, not a released one.
const SEAT_NUMBER_EXPR: &str = "CASE WHEN m.status IN ('ACTIVE', 'GRACE') THEN COALESCE(m.seat_number, (
        SELECT s.seat_number FROM seat_bookings sb
        JOIN seats s ON s.id = sb.seat_id
        WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
        LIMIT 1
    )) END";

pub async fn list_students(
    state: &Arc<AppState>,
    page: i64,
    size: i64,
    search: Option<&str>,
    status: Option<&str>,
    membership_status: Option<&str>,
    sort_by: Option<&str>,
    sort_dir: Option<&str>,
) -> crate::error::Result<(Vec<StudentListItem>, i64)> {
    let offset = page * size;
    let pattern = search.map(|s| format!("%{s}%"));

    let order_col = match sort_by.unwrap_or("createdAt") {
        "name" => "u.name",
        "mobile" => "u.mobile",
        "seatNumber" => "COALESCE(m.seat_number, '')",
        "endDate" => "COALESCE(m.end_date, '9999-12-31'::date)",
        "paymentMode" => "COALESCE(p.payment_gateway, '')",
        "pendingAmount" => "COALESCE(ps.pending_amount, 0)",
        "isActive" => "u.is_active",
        _ => "u.created_at",
    };
    let order_dir = if sort_dir == Some("asc") { "ASC" } else { "DESC" };

    let grace_days = settings::grace_days(state).await;

    let mut extra: Vec<String> = vec![];
    if let Some("ACTIVE") = status { extra.push("u.is_active = true".into()); }
    else if let Some("INACTIVE") = status { extra.push("u.is_active = false".into()); }
    // These mirror services::membership::resolve_display_status's buckets — kept in sync manually
    // since the display status itself is computed in Rust after the row comes back, not in SQL.
    match membership_status {
        Some("ACTIVE") => extra.push("m.status = 'ACTIVE'".into()),
        Some("INACTIVE") => extra.push("(m.status IS NULL OR m.status != 'ACTIVE')".into()),
        Some("NEW") => extra.push("cur.status IS NULL AND (le.status IS NULL OR le.status NOT IN ('EXPIRED', 'CANCELLED'))".into()),
        Some("RELEASED") => extra.push("cur.status IS NULL AND le.status IN ('EXPIRED', 'CANCELLED')".into()),
        Some("GRACE") => extra.push(format!("cur.status = 'GRACE' AND (CURRENT_DATE - cur.end_date) <= {grace_days}")),
        Some("GRACE_OVERDUE") => extra.push(format!("cur.status = 'GRACE' AND (CURRENT_DATE - cur.end_date) > {grace_days}")),
        Some("PENDING") => extra.push("cur.status IS NOT NULL AND cur.status != 'GRACE' AND COALESCE(ps.pending_amount, 0) > 0".into()),
        Some("PAID") => extra.push("cur.status IS NOT NULL AND cur.status != 'GRACE' AND COALESCE(ps.pending_amount, 0) <= 0".into()),
        _ => {}
    }

    let filter = if extra.is_empty() { String::new() } else { format!("AND {}", extra.join(" AND ")) };

    if let Some(ref pat) = pattern {
        // Seat search accepts "a1", "B15", "D-10", "d 10" etc — strip
        // everything but letters/digits and uppercase before matching, since
        // stored seat numbers are plain "A1".."D28" with no separators.
        let raw = search.expect("pattern is Some only when search is Some");
        let seat_cleaned: String = raw.chars().filter(|c| c.is_ascii_alphanumeric()).collect();
        let seat_pat = format!("%{}%", seat_cleaned.to_uppercase());

        let sql = format!(
            "{STUDENT_SELECT} WHERE u.role = 'STUDENT' {filter}
             AND (u.name ILIKE $3 OR u.mobile ILIKE $3 OR u.email ILIKE $3
                  OR UPPER(COALESCE({SEAT_NUMBER_EXPR}, '')) LIKE $4)
             ORDER BY {order_col} {order_dir} NULLS LAST LIMIT $1 OFFSET $2"
        );
        let mut users = sqlx::query_as::<_, StudentListItem>(&sql)
            .bind(size).bind(offset).bind(pat).bind(&seat_pat)
            .fetch_all(&state.db).await?;

        let count_sql = format!(
            "{STUDENT_COUNT_FROM} WHERE u.role = 'STUDENT' {filter}
             AND (u.name ILIKE $1 OR u.mobile ILIKE $1 OR u.email ILIKE $1
                  OR UPPER(COALESCE({SEAT_NUMBER_EXPR}, '')) LIKE $2)"
        );
        let total: i64 = sqlx::query_scalar(&count_sql)
            .bind(pat).bind(&seat_pat).fetch_one(&state.db).await?;

        attach_display_status(state, &mut users).await;
        Ok((users, total))
    } else {
        let sql = format!(
            "{STUDENT_SELECT} WHERE u.role = 'STUDENT' {filter}
             ORDER BY {order_col} {order_dir} NULLS LAST LIMIT $1 OFFSET $2"
        );
        let mut users = sqlx::query_as::<_, StudentListItem>(&sql)
            .bind(size).bind(offset)
            .fetch_all(&state.db).await?;

        let count_sql = format!("{STUDENT_COUNT_FROM} WHERE u.role = 'STUDENT' {filter}");
        let total: i64 = sqlx::query_scalar(&count_sql)
            .fetch_one(&state.db).await?;

        attach_display_status(state, &mut users).await;
        Ok((users, total))
    }
}

async fn attach_display_status(state: &Arc<AppState>, students: &mut [StudentListItem]) {
    let grace_days = settings::grace_days(state).await;
    for s in students.iter_mut() {
        s.display_status = crate::services::membership::resolve_display_status(
            s.current_status.as_deref(),
            s.current_end_date,
            s.pending_amount,
            s.latest_ever_status.as_deref(),
            grace_days,
        )
        .to_string();
    }
}

pub async fn get_student(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<StudentListItem> {
    let sql = format!("{STUDENT_SELECT} WHERE u.id = $1");
    let mut student = sqlx::query_as::<_, StudentListItem>(&sql)
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Student not found".into()))?;
    attach_display_status(state, std::slice::from_mut(&mut student)).await;
    Ok(student)
}

pub async fn update_student_status(
    state: &Arc<AppState>,
    user_id: Uuid,
    is_active: bool,
) -> crate::error::Result<()> {
    sqlx::query(
        "UPDATE users SET is_active = $2, updated_at = NOW() WHERE id = $1",
    )
    .bind(user_id)
    .bind(is_active)
    .execute(&state.db)
    .await
    .map(|_| ())
    .map_err(AppError::Database)
}

pub async fn update_student(
    state: &Arc<AppState>,
    user_id: Uuid,
    req: &AdminUpdateStudentRequest,
) -> crate::error::Result<StudentListItem> {
    sqlx::query(
        "UPDATE users SET
            name          = COALESCE($2, name),
            email         = COALESCE($3, email),
            address       = COALESCE($4, address),
            gender        = COALESCE($5, gender),
            date_of_birth = COALESCE($6, date_of_birth),
            mobile        = COALESCE($7, mobile),
            created_at    = COALESCE($8, created_at),
            updated_at    = NOW()
         WHERE id = $1",
    )
    .bind(user_id)
    .bind(&req.name)
    .bind(&req.email)
    .bind(&req.address)
    .bind(&req.gender)
    .bind(req.date_of_birth)
    .bind(&req.mobile)
    .bind(req.joined_at.and_then(|d| d.and_hms_opt(0, 0, 0)))
    .execute(&state.db)
    .await
    .map_err(AppError::Database)?;

    // Shifting the join date should shift an in-progress membership's dates
    // to match, so the student's remaining term stays consistent.
    if let Some(new_start) = req.joined_at {
        if let Some((mem_id, plan_id)) = sqlx::query_as::<_, (Uuid, Uuid)>(
            "SELECT id, plan_id FROM memberships
             WHERE user_id = $1 AND status = 'ACTIVE' AND end_date >= CURRENT_DATE
             ORDER BY created_at DESC LIMIT 1",
        )
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        {
            let duration_days: i32 = sqlx::query_scalar("SELECT duration_days FROM membership_plans WHERE id = $1")
                .bind(plan_id)
                .fetch_one(&state.db)
                .await?;
            let new_end = new_start + chrono::Duration::days(duration_days as i64 - 1);

            sqlx::query("UPDATE memberships SET start_date = $2, end_date = $3 WHERE id = $1")
                .bind(mem_id)
                .bind(new_start)
                .bind(new_end)
                .execute(&state.db)
                .await?;
            sqlx::query("UPDATE seat_bookings SET booking_date = $2, end_date = $3 WHERE membership_id = $1 AND status = 'ACTIVE'")
                .bind(mem_id)
                .bind(new_start)
                .bind(new_end)
                .execute(&state.db)
                .await?;
        }
    }

    get_student(state, user_id).await
}

pub async fn delete_student(state: &Arc<AppState>, user_id: Uuid) -> crate::error::Result<()> {
    let mut tx = state.db.begin().await.map_err(AppError::Database)?;

    sqlx::query("DELETE FROM seat_bookings WHERE user_id = $1")
        .bind(user_id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    sqlx::query("DELETE FROM payments WHERE user_id = $1")
        .bind(user_id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    sqlx::query("DELETE FROM memberships WHERE user_id = $1")
        .bind(user_id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    sqlx::query("DELETE FROM feedbacks WHERE user_id = $1")
        .bind(user_id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    sqlx::query("DELETE FROM users WHERE id = $1 AND role = 'STUDENT'")
        .bind(user_id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    tx.commit().await.map_err(AppError::Database)?;
    Ok(())
}

pub async fn get_student_payments(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<crate::models::membership::Payment>> {
    sqlx::query_as::<_, crate::models::membership::Payment>(
        "SELECT * FROM payments WHERE user_id = $1 ORDER BY created_at DESC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn get_pending_fees(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<PendingFeeItem>> {
    sqlx::query_as::<_, PendingFeeItem>(
        r#"SELECT u.id, u.name, u.mobile, u.email,
                  COALESCE(m.seat_number, (
                      SELECT s.seat_number FROM seat_bookings sb
                      JOIN seats s ON s.id = sb.seat_id
                      WHERE sb.membership_id = m.membership_id AND sb.status = 'ACTIVE'
                      LIMIT 1
                  )) AS seat_number,
                  m.end_date AS membership_end,
                  SUM(p.pending_amount) AS pending_amount
           FROM users u
           JOIN payments p ON p.user_id = u.id
           LEFT JOIN LATERAL (
               SELECT id AS membership_id, seat_number, end_date, status FROM memberships
               WHERE user_id = u.id AND status != 'PENDING'
               ORDER BY
                   CASE WHEN status = 'GRACE' THEN 0 WHEN status = 'ACTIVE' THEN 1 ELSE 2 END,
                   CASE WHEN status IN ('ACTIVE', 'GRACE') THEN end_date END DESC,
                   created_at DESC
               LIMIT 1
           ) m ON true
           WHERE p.pending_amount > 0 AND p.status = 'SUCCESS'
             -- Excludes released students: when a student has no ACTIVE/GRACE
             -- membership, the lateral join above falls back to their most
             -- recent row regardless of status, which is 'EXPIRED' exactly
             -- when resolve_display_status would compute them as RELEASED.
             AND (m.status IS NULL OR m.status != 'EXPIRED')
           GROUP BY u.id, u.name, u.mobile, u.email, m.seat_number, m.membership_id, m.end_date
           ORDER BY SUM(p.pending_amount) DESC"#,
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn clear_pending_fees(
    state: &Arc<AppState>,
    user_id: Uuid,
    amount_cleared: Decimal,
    payment_mode: Option<&str>,
) -> crate::error::Result<()> {
    if amount_cleared <= Decimal::ZERO {
        return Err(AppError::BadRequest("Amount to clear must be positive".into()));
    }

    let (mode_db, mode_label) = resolve_admin_payment_mode(payment_mode)?;

    let rows = sqlx::query_as::<_, (Uuid, Uuid, Decimal)>(
        "SELECT id, membership_id, pending_amount FROM payments
         WHERE user_id = $1 AND status = 'SUCCESS' AND pending_amount > 0
         ORDER BY created_at ASC",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await?;

    let total_pending: Decimal = rows.iter().map(|(_, _, p)| *p).sum();
    if amount_cleared > total_pending {
        return Err(AppError::BadRequest(
            "Amount to clear exceeds the outstanding pending balance".into(),
        ));
    }
    let remainder = total_pending - amount_cleared;

    // Zero out every existing pending row, then record this clearance as its own
    // cash payment (with the leftover as its pending_amount) — same mechanism as
    // clear_dues, giving this transaction a real invoice_id for the receipt below.
    let membership_id = rows.first().map(|(_, m, _)| *m).ok_or_else(|| {
        AppError::BadRequest("No outstanding pending balance for this student".into())
    })?;
    for (payment_id, _, _) in &rows {
        sqlx::query("UPDATE payments SET pending_amount = 0, updated_at = NOW() WHERE id = $1")
            .bind(payment_id)
            .execute(&state.db)
            .await?;
    }

    let invoice_id = ids::generate_invoice_id();
    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'SUCCESS')",
    )
    .bind(membership_id)
    .bind(user_id)
    .bind(amount_cleared)
    .bind(remainder)
    .bind(mode_db)
    .bind(&invoice_id)
    .execute(&state.db)
    .await?;

    let user = sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;
    let membership = crate::services::membership::get_active_membership(state, user_id).await?;

    let pf_setting = settings::setting_for(state, "PENDING_FEE_CLEARED").await;
    if pf_setting.send_to_student {
        let msg = settings::apply_hindi(
            &format!("Your pending library fee of Rs.{amount_cleared:.0} has been cleared. Thank you! - Target Zone Library Team"),
            &pf_setting, true,
        );
        notification::send_direct_message(state, user.mobile.as_deref(), user.email.as_deref(), &msg).await;
    }
    if pf_setting.send_to_admin {
        let seat = membership.as_ref().and_then(|m| m.seat_number.as_deref()).unwrap_or("\u{2014}");
        let admin_msg = format!(
            "Pending Fee Cleared\nStudent: {}\nSeat: {seat}\nAmount: Rs.{amount_cleared:.0}", user.name
        );
        if !state.config.admin_whatsapp.is_empty() {
            notification::send_direct_message(state, Some(&state.config.admin_whatsapp.clone()), None, &admin_msg).await;
        }
        notification::send_direct_message(state, None, Some(&state.config.admin_email.clone()), &admin_msg).await;
    }

    let s = state.clone();
    let receipt_event = notification::PaymentReceiptInfo {
        user_id,
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        invoice_id,
        amount_paid: amount_cleared,
        amount_pending: remainder,
        plan_name: membership.as_ref().map(|m| m.plan_name.clone()).unwrap_or_default(),
        seat_number: membership.as_ref().and_then(|m| m.seat_number.clone()),
        valid_upto: membership.as_ref().map(|m| m.end_date),
        payment_method: mode_label.into(),
    };
    tokio::spawn(async move {
        notification::send_payment_receipt_typed(&s, &receipt_event, "DUES_CLEARED").await
    });

    Ok(())
}

/// Find-or-create by mobile — mirrors Java's `ImportService.findOrCreateUser`.
/// Calling this twice with the same phone must not throw a conflict: the
/// second call is a no-op that just returns the already-imported student.
pub async fn import_student(
    state: &Arc<AppState>,
    req: &ImportStudentRequest,
) -> crate::error::Result<User> {
    let phone: String = req
        .mobile
        .as_deref()
        .unwrap_or("")
        .chars()
        .filter(|c| c.is_ascii_digit())
        .collect();
    if phone.is_empty() {
        return Err(AppError::BadRequest("Phone number is invalid".into()));
    }

    if let Some(existing) = sqlx::query_as::<_, User>("SELECT * FROM users WHERE mobile = $1")
        .bind(&phone)
        .fetch_optional(&state.db)
        .await?
    {
        return Ok(existing);
    }

    sqlx::query_as::<_, User>(
        "INSERT INTO users (name, mobile, email, address, gender, date_of_birth, role, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, 'STUDENT', NOW()) RETURNING *",
    )
    .bind(req.name.trim())
    .bind(&phone)
    .bind(&req.email)
    .bind(&req.address)
    .bind(&req.gender)
    .bind(req.date_of_birth)
    .fetch_one(&state.db)
    .await
    .map_err(|e| {
        if e.to_string().contains("unique") {
            AppError::Conflict("User already exists".into())
        } else {
            AppError::Database(e)
        }
    })
}

/// Separate from `import_student` above (matching Java's separate
/// `importSingleStudentWithPhoto`) so the existing JSON contract used by the
/// web frontend and Android app is untouched — this one is multipart with an
/// optional photo attached to the same find-or-create-by-mobile student.
pub async fn import_student_with_photo(
    state: &Arc<AppState>,
    name: &str,
    phone: &str,
    photo: Option<(Option<String>, Vec<u8>)>,
) -> crate::error::Result<crate::models::admin::ImportWithPhotoResponse> {
    let clean_phone: String = phone.chars().filter(|c| c.is_ascii_digit()).collect();
    if clean_phone.is_empty() {
        return Err(AppError::BadRequest("Phone number is invalid".into()));
    }

    let user = if let Some(existing) = sqlx::query_as::<_, User>("SELECT * FROM users WHERE mobile = $1")
        .bind(&clean_phone)
        .fetch_optional(&state.db)
        .await?
    {
        existing
    } else {
        sqlx::query_as::<_, User>(
            "INSERT INTO users (name, mobile, role, created_at) VALUES ($1, $2, 'STUDENT', NOW()) RETURNING *",
        )
        .bind(name.trim())
        .bind(&clean_phone)
        .fetch_one(&state.db)
        .await?
    };

    let mut photo_url = None;
    if let Some((content_type, data)) = photo {
        if !data.is_empty() {
            crate::services::user::validate_upload(
                content_type.as_deref(),
                &data,
                crate::services::user::IMAGE_CONTENT_TYPES,
                "Invalid file type. Only JPEG, PNG, WebP allowed.",
            )?;
            let url = crate::services::user::save_file(
                &state.config.upload_dir,
                user.id,
                "photo",
                "photo.jpg",
                &data,
            )
            .await?;
            sqlx::query("UPDATE users SET photo_url = $2 WHERE id = $1")
                .bind(user.id)
                .bind(&url)
                .execute(&state.db)
                .await?;
            photo_url = Some(url);
        }
    }

    Ok(crate::models::admin::ImportWithPhotoResponse {
        message: "Student added successfully".to_string(),
        photo_url,
    })
}

// Bulk import is a cash-enrollment pipeline, not a contact-list import: each
// row produces a real User + Membership + Payment + seat booking, matching
// Java's ImportService.processRow. Column layout is fixed-position (matching
// the Java-generated template), not header-name-based:
//   [1]=name [2]=phone [3]=fees [4]=join date [5]=seat number
const IMPORT_DATE_FORMATS: &[&str] = &[
    "%m-%d-%Y",
    "%d-%m-%Y",
    "%d/%m/%Y",
    "%Y-%m-%d",
    "%m/%d/%Y",
    "%d/%m/%y",
    "%m/%d/%y",
];

pub async fn bulk_import_students(
    state: &Arc<AppState>,
    data: &[u8],
    filename: &str,
) -> crate::error::Result<ImportResult> {
    let lower = filename.to_lowercase();
    let rows: Vec<Vec<String>> = if lower.ends_with(".xlsx") {
        parse_excel_rows(data, false)?
    } else if lower.ends_with(".xls") {
        parse_excel_rows(data, true)?
    } else {
        parse_csv_rows(data)?
    };

    let mut total_rows = rows.len().saturating_sub(1) as i32;
    let date_hint = rows
        .first()
        .and_then(|header| header.get(4))
        .and_then(|h| extract_date_format_hint(h));

    let active_plans: Vec<MembershipPlan> = sqlx::query_as::<_, MembershipPlan>(
        "SELECT * FROM membership_plans WHERE is_active = true",
    )
    .fetch_all(&state.db)
    .await?;

    let mut imported = 0i32;
    let mut errors: Vec<ImportRowError> = Vec::new();

    for (i, cols) in rows.iter().enumerate().skip(1) {
        let get = |idx: usize| cols.get(idx).map(|s| s.trim()).unwrap_or("");
        let name = get(1).to_string();
        let phone: String = get(2).chars().filter(|c| c.is_ascii_digit()).collect();

        if name.is_empty() && phone.is_empty() {
            total_rows -= 1;
            continue;
        }

        match process_import_row(state, cols, &active_plans, date_hint.as_deref()).await {
            Ok(()) => imported += 1,
            Err(e) => errors.push(ImportRowError {
                row: (i + 1) as i32,
                name,
                phone,
                reason: strip_error_prefix(&e.to_string()),
            }),
        }
    }

    Ok(ImportResult {
        imported,
        skipped: total_rows - imported,
        total_rows,
        errors,
    })
}

async fn process_import_row(
    state: &Arc<AppState>,
    cols: &[String],
    active_plans: &[MembershipPlan],
    date_hint: Option<&str>,
) -> crate::error::Result<()> {
    let get = |idx: usize| cols.get(idx).map(|s| s.trim()).unwrap_or("");
    let name = get(1).to_string();
    let phone: String = get(2).chars().filter(|c| c.is_ascii_digit()).collect();
    let fees = parse_import_fees(get(3));
    let seat_number = normalize_seat_number(get(5));

    if name.is_empty() {
        return Err(AppError::BadRequest("Name is blank".into()));
    }
    if phone.is_empty() {
        return Err(AppError::BadRequest("Phone is blank".into()));
    }
    if seat_number.is_empty() {
        return Err(AppError::BadRequest("Seat is blank".into()));
    }

    let mut start_date = parse_import_date(get(4), date_hint)?;

    if active_plans.is_empty() {
        return Err(AppError::BadRequest("No active plans configured".into()));
    }
    let plan = active_plans
        .iter()
        .min_by(|a, b| (a.price - fees).abs().cmp(&(b.price - fees).abs()))
        .unwrap();

    let shift = if plan.plan_type == "FULL_DAY" { "FULL_DAY" } else { "MORNING" };

    // If the sheet's date is so far in the past the membership would already
    // be expired, start from today so the student appears active on the seat map.
    let today = chrono::Local::now().date_naive();
    if start_date + chrono::Duration::days(plan.duration_days as i64) < today {
        start_date = today;
    }

    let user_id = find_or_create_user_by_mobile(state, &name, &phone).await?;

    let req = CashMembershipRequest {
        user_id,
        plan_id: plan.id,
        shift: shift.to_string(),
        seat_number: Some(seat_number),
        start_date,
        amount: plan.price,
        pending_amount: Some(Decimal::ZERO),
        payment_mode: None, // bulk sheet imports are always treated as cash
    };
    create_cash_membership(state, &req).await?;
    Ok(())
}

/// Idempotent find-or-create by mobile — re-importing an already-registered
/// phone number is a no-op success, matching Java's findOrCreateUser, rather
/// than the unique-constraint 409 a plain INSERT would raise.
async fn find_or_create_user_by_mobile(
    state: &Arc<AppState>,
    name: &str,
    mobile: &str,
) -> crate::error::Result<Uuid> {
    if let Some(id) = sqlx::query_scalar::<_, Uuid>("SELECT id FROM users WHERE mobile = $1")
        .bind(mobile)
        .fetch_optional(&state.db)
        .await?
    {
        return Ok(id);
    }
    let id: Uuid = sqlx::query_scalar(
        "INSERT INTO users (name, mobile, role, created_at) VALUES ($1, $2, 'STUDENT', NOW()) RETURNING id",
    )
    .bind(name.trim())
    .bind(mobile)
    .fetch_one(&state.db)
    .await?;
    Ok(id)
}

fn strip_error_prefix(msg: &str) -> String {
    for prefix in ["Not found: ", "Bad request: ", "Conflict: ", "Internal error: "] {
        if let Some(rest) = msg.strip_prefix(prefix) {
            return rest.to_string();
        }
    }
    msg.to_string()
}

fn parse_import_fees(raw: &str) -> Decimal {
    let cleaned: String = raw.chars().filter(|c| c.is_ascii_digit() || *c == '.').collect();
    if cleaned.is_empty() {
        Decimal::ZERO
    } else {
        cleaned.parse::<Decimal>().unwrap_or(Decimal::ZERO)
    }
}

/// Uppercase, strip whitespace/hyphens, then collapse a zero-padded numeric
/// suffix ("A007" -> "A7") — matches Java's `[A-Z]+0+(\d)` -> `$1$2` regex.
fn normalize_seat_number(raw: &str) -> String {
    let cleaned: String = raw
        .trim()
        .to_uppercase()
        .chars()
        .filter(|c| !c.is_whitespace() && *c != '-')
        .collect();
    let re = regex::Regex::new(r"([A-Z]+)0+(\d)").unwrap();
    re.replace(&cleaned, "$1$2").to_string()
}

fn parse_import_date(raw: &str, hint: Option<&str>) -> crate::error::Result<NaiveDate> {
    let raw = raw.trim();
    if raw.is_empty() {
        return Ok(chrono::Local::now().date_naive());
    }
    if let Some(fmt) = hint {
        if let Ok(d) = NaiveDate::parse_from_str(raw, fmt) {
            return Ok(d);
        }
    }
    for fmt in IMPORT_DATE_FORMATS {
        if let Ok(d) = NaiveDate::parse_from_str(raw, fmt) {
            return Ok(d);
        }
    }
    Err(AppError::BadRequest(format!("Cannot parse date: {raw}")))
}

/// Extracts a date pattern hinted in a header cell like `"Date (dd/MM/yyyy)"`,
/// translating the Java-style tokens to chrono's strftime specifiers.
/// Best-effort — falls back silently to `IMPORT_DATE_FORMATS` on any failure.
fn extract_date_format_hint(header_cell: &str) -> Option<String> {
    let open = header_cell.find('(')?;
    let close = header_cell.rfind(')')?;
    if close <= open {
        return None;
    }
    let pattern = header_cell[open + 1..close].trim();
    if pattern.is_empty() {
        return None;
    }
    Some(translate_java_date_pattern(pattern))
}

fn translate_java_date_pattern(pattern: &str) -> String {
    let chars: Vec<char> = pattern.chars().collect();
    let mut out = String::new();
    let mut i = 0;
    while i < chars.len() {
        match chars[i] {
            'y' => {
                let start = i;
                while i < chars.len() && chars[i] == 'y' {
                    i += 1;
                }
                out.push_str(if i - start >= 4 { "%Y" } else { "%y" });
            }
            'M' => {
                while i < chars.len() && chars[i] == 'M' {
                    i += 1;
                }
                out.push_str("%m");
            }
            'd' => {
                while i < chars.len() && chars[i] == 'd' {
                    i += 1;
                }
                out.push_str("%d");
            }
            other => {
                out.push(other);
                i += 1;
            }
        }
    }
    out
}

fn parse_csv_rows(data: &[u8]) -> crate::error::Result<Vec<Vec<String>>> {
    let mut reader = csv::ReaderBuilder::new()
        .has_headers(false)
        .flexible(true)
        .trim(csv::Trim::All)
        .from_reader(data);

    reader
        .records()
        .map(|r| {
            r.map(|record| record.iter().map(|s| s.to_string()).collect())
                .map_err(|e| AppError::BadRequest(format!("Invalid CSV row: {e}")))
        })
        .collect()
}

fn parse_excel_rows(data: &[u8], is_xls: bool) -> crate::error::Result<Vec<Vec<String>>> {
    use calamine::Reader;

    let cursor = std::io::Cursor::new(data);
    let range = if is_xls {
        let mut wb = calamine::Xls::new(cursor)
            .map_err(|e| AppError::BadRequest(format!("Cannot read .xls file: {e:?}")))?;
        let sheet = wb
            .sheet_names()
            .into_iter()
            .next()
            .ok_or_else(|| AppError::BadRequest("Excel file has no sheets".into()))?;
        wb.worksheet_range(&sheet)
            .map_err(|e| AppError::BadRequest(format!("Cannot read sheet: {e:?}")))?
    } else {
        let mut wb = calamine::Xlsx::new(cursor)
            .map_err(|e| AppError::BadRequest(format!("Cannot read .xlsx file: {e:?}")))?;
        let sheet = wb
            .sheet_names()
            .into_iter()
            .next()
            .ok_or_else(|| AppError::BadRequest("Excel file has no sheets".into()))?;
        wb.worksheet_range(&sheet)
            .map_err(|e| AppError::BadRequest(format!("Cannot read sheet: {e:?}")))?
    };

    Ok(range
        .rows()
        .map(|row| row.iter().map(|cell| cell.to_string().trim().to_string()).collect())
        .collect())
}

// ── Seat map ──────────────────────────────────────────────────────────────────

pub async fn get_seat_map(
    state: &Arc<AppState>,
    shift: &str,
    date: NaiveDate,
) -> crate::error::Result<AdminSeatMapResponse> {
    use std::collections::HashMap;

    let seats = sqlx::query_as::<_, crate::models::seat::Seat>(
        "SELECT * FROM seats ORDER BY row_label, seat_index",
    )
    .fetch_all(&state.db)
    .await?;

    // FULL_DAY view shows all bookings; MORNING/EVENING views include FULL_DAY bookings too
    let shift_filters: Vec<String> = match shift {
        "MORNING" => vec!["MORNING".into(), "FULL_DAY".into()],
        "EVENING" => vec!["EVENING".into(), "FULL_DAY".into()],
        _ => vec!["MORNING".into(), "EVENING".into(), "FULL_DAY".into()],
    };

    // The second end_date condition closes the daily display gap between
    // midnight (when an ACTIVE membership becomes overdue) and the 5 AM
    // grace-transition cron actually running: an overdue-but-not-yet-graced
    // seat still shows occupied, but only when viewing *today* specifically
    // — this must never leak into legitimately future/past date views.
    // sb.end_date is reported separately from m.end_date because a GRACE membership's
    // seat_bookings.end_date is pushed to the far-future 9999-12-31 sentinel to hold the
    // seat indefinitely — the membership's real end_date is what expiry views must show.
    let occupants = sqlx::query_as::<_, (Uuid, String, Uuid, String, Option<String>, Option<String>, NaiveDate, NaiveDate)>(
        "SELECT sb.seat_id, sb.shift, u.id, u.name, u.mobile, u.gender, sb.end_date, m.end_date
         FROM seat_bookings sb
         JOIN users u ON u.id = sb.user_id
         JOIN memberships m ON m.id = sb.membership_id
         WHERE sb.status = 'ACTIVE'
           AND sb.booking_date <= $1
           AND (
               sb.end_date >= $1
               OR (m.status IN ('ACTIVE', 'GRACE') AND $1 = CURRENT_DATE AND m.end_date < CURRENT_DATE)
           )
           AND sb.shift = ANY($2)",
    )
    .bind(date)
    .bind(&shift_filters)
    .fetch_all(&state.db)
    .await?;

    let occupant_map: HashMap<Uuid, (Uuid, String, Option<String>, Option<String>, String, NaiveDate)> = occupants
        .into_iter()
        .map(|(seat_id, sb_shift, student_id, name, mobile, gender, _sb_end, membership_end)| {
            (seat_id, (student_id, name, mobile, gender, sb_shift, membership_end))
        })
        .collect();

    let mut seats_by_row: HashMap<String, Vec<SeatMapSeat>> = HashMap::new();
    let mut occupied_count = 0i64;
    let total = seats.len() as i64;

    for seat in seats {
        let occ = occupant_map.get(&seat.id);
        let is_occupied = occ.is_some();
        if is_occupied {
            occupied_count += 1;
        }
        let map_seat = SeatMapSeat {
            seat_number: seat.seat_number.clone(),
            is_occupied,
            student_id: occ.map(|(id, _, _, _, _, _)| *id),
            student_name: occ.map(|(_, n, _, _, _, _)| n.clone()),
            student_mobile: occ.and_then(|(_, _, m, _, _, _)| m.clone()),
            student_gender: occ.and_then(|(_, _, _, g, _, _)| g.clone()),
            shift: occ.map(|(_, _, _, _, s, _)| s.clone()),
            membership_end: occ.map(|(_, _, _, _, _, e)| *e),
        };
        seats_by_row
            .entry(seat.row_label.clone())
            .or_default()
            .push(map_seat);
    }

    Ok(AdminSeatMapResponse {
        shift: shift.to_string(),
        date,
        seats_by_row,
        occupied_seats: occupied_count,
        available_seats: total - occupied_count,
        total_seats: total,
    })
}

/// History of every non-abandoned booking a physical seat has ever had.
/// Known accepted gap (matches the Java backend): `change_membership_seat`
/// mutates a membership's seat in place rather than inserting a new row, so
/// a student moved off this seat via admin "Change Seat" won't appear here
/// afterward.
pub async fn get_seat_history(
    state: &Arc<AppState>,
    seat_number: &str,
) -> crate::error::Result<Vec<SeatHistoryEntryDto>> {
    sqlx::query_as::<_, SeatHistoryEntryDto>(
        r#"SELECT m.id AS membership_id, u.name AS student_name, u.mobile AS student_mobile,
                  m.shift, m.start_date, m.end_date, m.status,
                  s.seat_number, mp.name AS plan_name
           FROM memberships m
           JOIN users u ON u.id = m.user_id
           JOIN seats s ON s.seat_number = $1
           LEFT JOIN membership_plans mp ON mp.id = m.plan_id
           WHERE COALESCE(m.seat_number, '') = $1
             AND m.status NOT IN ('PENDING', 'CANCELLED')
           ORDER BY m.start_date DESC"#,
    )
    .bind(seat_number)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

/// Same shape, scoped to one student — backs the Student Detail page's
/// seat-history section.
pub async fn get_student_seat_history(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> crate::error::Result<Vec<SeatHistoryEntryDto>> {
    sqlx::query_as::<_, SeatHistoryEntryDto>(
        r#"SELECT m.id AS membership_id, u.name AS student_name, u.mobile AS student_mobile,
                  m.shift, m.start_date, m.end_date, m.status,
                  m.seat_number, mp.name AS plan_name
           FROM memberships m
           JOIN users u ON u.id = m.user_id
           LEFT JOIN membership_plans mp ON mp.id = m.plan_id
           WHERE m.user_id = $1
             AND m.status NOT IN ('PENDING', 'CANCELLED')
           ORDER BY m.start_date DESC"#,
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

// ── Memberships ───────────────────────────────────────────────────────────────

pub async fn get_expiring_memberships(
    state: &Arc<AppState>,
    days: i32,
) -> crate::error::Result<Vec<ExpiringMembershipItem>> {
    sqlx::query_as::<_, ExpiringMembershipItem>(
        r#"SELECT u.id, u.name, u.mobile, u.email,
                  COALESCE(m.seat_number, (
                      SELECT s.seat_number FROM seat_bookings sb
                      JOIN seats s ON s.id = sb.seat_id
                      WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
                      LIMIT 1
                  )) AS seat_number,
                  m.end_date AS membership_end,
                  (m.end_date - CURRENT_DATE)::int AS days_remaining
           FROM memberships m
           JOIN users u ON u.id = m.user_id
           WHERE m.status = 'ACTIVE'
             AND m.end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + ($1 || ' days')::INTERVAL
           ORDER BY m.end_date"#,
    )
    .bind(days.to_string())
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn send_renewal_reminders(
    state: &Arc<AppState>,
    user_ids: Option<Vec<Uuid>>,
) -> crate::error::Result<i64> {
    // Treat empty vec the same as None (send to all) — matches frontend behaviour
    let user_ids = user_ids.filter(|v| !v.is_empty());

    // This is the manual admin override, not the scheduler — it intentionally
    // ignores `reminder_sent` (empty/no ids sends to everyone expiring within
    // 7 days regardless of the flag, matching Java's `sendBulkReminders`) and
    // never sets `reminder_sent` itself; only the daily scheduler job does.
    let rows: Vec<(Uuid, String, Option<String>, Option<String>, NaiveDate)> = if let Some(ids) = &user_ids {
        sqlx::query_as(
            "SELECT m.id, u.name, u.mobile, u.email, m.end_date
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'ACTIVE' AND m.user_id = ANY($1::uuid[])",
        )
        .bind(ids)
        .fetch_all(&state.db)
        .await?
    } else {
        sqlx::query_as(
            "SELECT m.id, u.name, u.mobile, u.email, m.end_date
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'ACTIVE'
               AND m.end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'",
        )
        .fetch_all(&state.db)
        .await?
    };

    let mut count = 0i64;
    let today = chrono::Local::now().date_naive();

    for (_mid, name, mobile, email, end_date) in &rows {
        let days_left = (*end_date - today).num_days().max(0);
        let s = state.clone();
        let n = name.clone();
        let m = mobile.clone();
        let e = email.clone();
        let ed = *end_date;
        tokio::spawn(async move {
            notification::send_renewal_reminder(&s, &n, m.as_deref(), e.as_deref(), days_left, ed).await;
        });
        count += 1;
    }

    if count > 0 {
        let admin_msg = format!(
            "Renewal Reminders Sent! {count} student(s) notified about upcoming membership expiry."
        );
        if !state.config.admin_whatsapp.is_empty() {
            notification::send_whatsapp_to(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
        }
        notification::send_email_to(
            state,
            &state.config.admin_email.clone(),
            &format!("Renewal Reminders Sent — {count} student(s)"),
            &admin_msg,
        ).await;
    }

    Ok(count)
}

pub async fn get_orphaned_seats(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<OrphanedSeatItem>> {
    sqlx::query_as::<_, OrphanedSeatItem>(
        r#"SELECT u.id, u.name, u.mobile, m.id AS membership_id, m.end_date AS membership_end
           FROM memberships m
           JOIN users u ON u.id = m.user_id
           WHERE m.status IN ('ACTIVE', 'GRACE')
             AND NOT EXISTS (
                 SELECT 1 FROM seat_bookings sb
                 WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
             )
           ORDER BY m.end_date"#,
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn get_grace_dues_students(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<GraceDuesStudentItem>> {
    sqlx::query_as::<_, GraceDuesStudentItem>(
        r#"SELECT u.id, u.name, u.mobile, u.email,
                  COALESCE(m.seat_number, (
                      SELECT s.seat_number FROM seat_bookings sb
                      JOIN seats s ON s.id = sb.seat_id
                      WHERE sb.membership_id = m.id AND sb.status = 'ACTIVE'
                      LIMIT 1
                  )) AS seat_number,
                  m.end_date AS membership_end,
                  COALESCE(m.dues_amount, 0) AS dues_amount
           FROM memberships m
           JOIN users u ON u.id = m.user_id
           WHERE m.status = 'GRACE'
           ORDER BY m.end_date"#,
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn send_grace_dues_reminders(
    state: &Arc<AppState>,
    user_ids: Option<Vec<Uuid>>,
) -> crate::error::Result<i64> {
    let ids_filter = user_ids.filter(|v| !v.is_empty());

    let rows: Vec<(Uuid, Uuid, String, Option<String>, Option<String>, Decimal)> = if let Some(ref ids) = ids_filter {
        sqlx::query_as(
            "SELECT u.id, m.id, u.name, u.mobile, u.email, COALESCE(m.dues_amount, 0)
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'GRACE' AND u.id = ANY($1)",
        )
        .bind(ids)
        .fetch_all(&state.db)
        .await?
    } else {
        sqlx::query_as(
            "SELECT u.id, m.id, u.name, u.mobile, u.email, COALESCE(m.dues_amount, 0)
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'GRACE'",
        )
        .fetch_all(&state.db)
        .await?
    };

    let count = rows.len() as i64;
    let setting = settings::setting_for(state, "GRACE_DUES_REMINDER").await;
    let app_settings = settings::get_app_settings(state).await.ok();
    let upi_id = app_settings.as_ref().and_then(|s| s.upi_id.clone()).filter(|v| !v.is_empty());
    for (user_id, membership_id, name, mobile, email, dues) in &rows {
        let mut text = format!(
            "Grace Period Reminder - Hi {name}, your library membership is in its grace period with Rs.{dues:.0} \
in outstanding dues. Please clear your dues soon to avoid losing your seat. - Target Zone Library Team"
        );
        if let Some(vpa) = &upi_id {
            let payload = upi_pay::PayLinkPayload {
                user_id: *user_id,
                claim_type: Some("DUES".to_string()),
                membership_id: Some(*membership_id),
                amount: *dues,
                vpa: vpa.clone(),
                payee_name: "Target Zone Library".to_string(),
                note: format!("Grace dues - {name}"),
            };
            if let Ok(link) = upi_pay::create_pay_link(state, &payload).await {
                text.push_str(&format!("\n\nPay via UPI: {link}"));
            }
        }
        let msg = settings::apply_hindi(&text, &setting, true);
        if setting.send_to_student {
            notification::send_direct_message(state, mobile.as_deref(), email.as_deref(), &msg).await;
        }
    }

    if count > 0 {
        let admin_msg = format!("Grace Dues Reminders Sent! {count} student(s) notified about grace-period dues.");
        if !state.config.admin_whatsapp.is_empty() {
            notification::send_whatsapp_to(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
        }
        notification::send_email_to(
            state,
            &state.config.admin_email.clone(),
            &format!("Grace Dues Reminders Sent — {count} student(s)"),
            &admin_msg,
        ).await;
    }

    Ok(count)
}

pub async fn send_pending_fee_reminders(
    state: &Arc<AppState>,
    user_ids: Option<Vec<Uuid>>,
) -> crate::error::Result<i64> {
    // Treat empty vec the same as None (send to all) — matches frontend behaviour
    let ids = user_ids.filter(|v| !v.is_empty());

    let rows: Vec<(Uuid, String, Option<String>, Option<String>, Option<Decimal>)> = if let Some(ref ids) = ids {
        sqlx::query_as(
            "SELECT u.id, u.name, u.mobile, u.email, SUM(p.pending_amount)
             FROM users u JOIN payments p ON p.user_id = u.id
             WHERE p.pending_amount > 0 AND p.status = 'SUCCESS'
               AND u.id = ANY($1)
             GROUP BY u.id, u.name, u.mobile, u.email",
        )
        .bind(ids)
        .fetch_all(&state.db)
        .await?
    } else {
        // "Send to All" — must mirror get_pending_fees' released-student
        // exclusion, since this branch re-queries independently rather than
        // trusting an admin-picked id list (see comment there).
        sqlx::query_as(
            "SELECT u.id, u.name, u.mobile, u.email, SUM(p.pending_amount)
             FROM users u
             JOIN payments p ON p.user_id = u.id
             LEFT JOIN LATERAL (
                 SELECT status FROM memberships
                 WHERE user_id = u.id AND status != 'PENDING'
                 ORDER BY
                     CASE WHEN status = 'GRACE' THEN 0 WHEN status = 'ACTIVE' THEN 1 ELSE 2 END,
                     CASE WHEN status IN ('ACTIVE', 'GRACE') THEN end_date END DESC,
                     created_at DESC
                 LIMIT 1
             ) m ON true
             WHERE p.pending_amount > 0 AND p.status = 'SUCCESS'
               AND (m.status IS NULL OR m.status != 'EXPIRED')
             GROUP BY u.id, u.name, u.mobile, u.email",
        )
        .fetch_all(&state.db)
        .await?
    };

    let count = rows.len() as i64;
    let app_settings = settings::get_app_settings(state).await.ok();
    let upi_id = app_settings.as_ref().and_then(|s| s.upi_id.clone()).filter(|v| !v.is_empty());
    for (user_id, name, mobile, email, pending) in &rows {
        let amount = pending.unwrap_or_default();
        let mut msg = format!(
            "Pending Fee Reminder - Hi {name}, you have a pending library fee of Rs.{amount:.0}. \
Please visit the library or contact us to clear your dues. - Target Zone Library Team"
        );
        if let Some(vpa) = &upi_id {
            let payload = upi_pay::PayLinkPayload {
                user_id: *user_id,
                claim_type: Some("PENDING_FEE".to_string()),
                membership_id: None,
                amount,
                vpa: vpa.clone(),
                payee_name: "Target Zone Library".to_string(),
                note: format!("Pending fee - {name}"),
            };
            if let Ok(link) = upi_pay::create_pay_link(state, &payload).await {
                msg.push_str(&format!("\n\nPay via UPI: {link}"));
            }
        }
        notification::send_direct_message(state, mobile.as_deref(), email.as_deref(), &msg).await;
    }

    // Admin summary copy
    if count > 0 {
        let admin_msg = format!(
            "Pending Fee Reminders Sent! {count} student(s) notified about outstanding dues."
        );
        if !state.config.admin_whatsapp.is_empty() {
            notification::send_whatsapp_to(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
        }
        notification::send_email_to(
            state,
            &state.config.admin_email.clone(),
            &format!("Pending Fee Reminders Sent — {count} student(s)"),
            &admin_msg,
        ).await;
    }

    Ok(count)
}

pub async fn send_direct_message(
    state: &Arc<AppState>,
    user_id: Uuid,
    message: &str,
) -> crate::error::Result<()> {
    let user = sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".into()))?;

    notification::send_direct_message(state, user.mobile.as_deref(), user.email.as_deref(), message).await;
    Ok(())
}

pub async fn broadcast(
    state: &Arc<AppState>,
    message: &str,
) -> crate::error::Result<BroadcastMessage> {
    // Matches Java's `findStudentsWithActiveMemberships` exactly: STUDENT role,
    // a mobile on file, and an EXISTS check (not a JOIN) so a student is
    // never targeted twice even if they somehow hold more than one ACTIVE
    // membership row. `end_date >= CURRENT_DATE` additionally excludes a
    // membership that's ACTIVE in name only because today's grace-transition
    // cron hasn't run yet.
    let users: Vec<(Option<String>, Option<String>)> = sqlx::query_as(
        "SELECT u.mobile, u.email FROM users u
         WHERE u.role = 'STUDENT'
           AND u.mobile IS NOT NULL
           AND EXISTS (
               SELECT 1 FROM memberships m
               WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.end_date >= CURRENT_DATE
           )
         ORDER BY u.name",
    )
    .fetch_all(&state.db)
    .await?;

    let recipient_count = users.iter().filter(|(m, _)| m.is_some()).count() as i32;

    let bcast = sqlx::query_as::<_, BroadcastMessage>(
        "INSERT INTO broadcast_messages (id, message, recipient_count, sent_at) VALUES (gen_random_uuid(), $1, $2, NOW()) RETURNING *",
    )
    .bind(message)
    .bind(recipient_count)
    .fetch_one(&state.db)
    .await?;

    let s = state.clone();
    let msg = message.to_string();
    tokio::spawn(async move {
        notification::send_broadcast(&s, &users, &msg).await;
    });

    Ok(bcast)
}

pub async fn get_broadcast_history(
    state: &Arc<AppState>,
) -> crate::error::Result<Vec<BroadcastMessage>> {
    sqlx::query_as::<_, BroadcastMessage>(
        "SELECT * FROM broadcast_messages ORDER BY sent_at DESC LIMIT 5",
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

// ── Cash membership ───────────────────────────────────────────────────────────

/// Admin-selectable payment mode for the three cash-desk write paths
/// (create_cash_membership, clear_dues, clear_pending_fees). Deliberately does
/// NOT accept "ONLINE-PG" — that value is only ever written by the real
/// gateway-verification code in services/membership.rs (verify_payment /
/// verify_and_pay_dues / verify_and_pay_pending), never by an admin action.
/// Missing/None defaults to CASH so older (not-yet-upgraded) clients that
/// don't send this field keep working unchanged. Centralized so the DB value
/// and the receipt label can't drift apart the way the old hardcoded 'CASH'
/// literal + hardcoded "CASH" receipt string did across all three call sites.
fn resolve_admin_payment_mode(raw: Option<&str>) -> crate::error::Result<(&'static str, &'static str)> {
    match raw.unwrap_or("CASH") {
        "CASH" => Ok(("CASH", "Cash")),
        "UPI-QR" => Ok(("UPI-QR", "UPI (QR)")),
        other => Err(AppError::BadRequest(format!(
            "Invalid payment mode '{other}' — must be CASH or UPI-QR"
        ))),
    }
}

pub async fn create_cash_membership(
    state: &Arc<AppState>,
    req: &CashMembershipRequest,
) -> crate::error::Result<serde_json::Value> {
    let plan = sqlx::query_as::<_, crate::models::membership::MembershipPlan>(
        "SELECT * FROM membership_plans WHERE id = $1",
    )
    .bind(req.plan_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Plan not found".into()))?;

    // Guards against a stale client value double-counting an amount as both
    // paid and owed (production incident this exact check was added for).
    let pending = req.pending_amount.unwrap_or_default();
    if req.amount + pending != plan.price {
        return Err(AppError::BadRequest(
            "Paid amount + pending amount must equal the plan price".into(),
        ));
    }

    let (mode_db, mode_label) = resolve_admin_payment_mode(req.payment_mode.as_deref())?;

    let blocked: bool = sqlx::query_scalar(
        "SELECT EXISTS(SELECT 1 FROM memberships
         WHERE user_id = $1 AND (status = 'GRACE' OR (status = 'ACTIVE' AND end_date >= CURRENT_DATE)))",
    )
    .bind(req.user_id)
    .fetch_one(&state.db)
    .await?;
    if blocked {
        return Err(AppError::BadRequest(
            "This student already has an active membership or unresolved dues".into(),
        ));
    }

    let end_date = req.start_date + chrono::Duration::days(plan.duration_days as i64 - 1);

    // Validate the seat is real and actually free *before* creating the membership —
    // otherwise a conflict discovered later leaves a membership claiming a seat_number
    // it never actually reserved (services::seat::book_seat already does this check
    // correctly for the student self-serve flow; this mirrors it).
    let seat = if let Some(ref seat_num) = req.seat_number {
        let seat = sqlx::query_as::<_, crate::models::seat::Seat>(
            "SELECT * FROM seats WHERE seat_number = $1 AND is_active = true",
        )
        .bind(seat_num)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Seat {seat_num} not found")))?;

        let conflict: i64 = sqlx::query_scalar(
            r#"SELECT COUNT(*) FROM seat_bookings
             WHERE seat_id = $1
               AND status = 'ACTIVE'
               AND booking_date <= $3
               AND end_date >= $2
               AND (shift = $4 OR shift = 'FULL_DAY' OR $4::text = 'FULL_DAY')"#,
        )
        .bind(seat.id)
        .bind(req.start_date)
        .bind(end_date)
        .bind(&req.shift)
        .fetch_one(&state.db)
        .await?;

        if conflict > 0 {
            return Err(AppError::Conflict(format!(
                "Seat {seat_num} is already booked for {} during the requested period", req.shift
            )));
        }

        Some(seat)
    } else {
        None
    };

    let membership = sqlx::query_as::<_, Membership>(
        "INSERT INTO memberships (user_id, plan_id, seat_number, shift, start_date, end_date, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'ACTIVE') RETURNING *",
    )
    .bind(req.user_id)
    .bind(req.plan_id)
    .bind(&req.seat_number)
    .bind(&req.shift)
    .bind(req.start_date)
    .bind(end_date)
    .fetch_one(&state.db)
    .await?;

    let invoice_id = crate::services::ids::generate_invoice_id();
    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'SUCCESS')",
    )
    .bind(membership.id)
    .bind(req.user_id)
    .bind(req.amount)
    .bind(req.pending_amount)
    .bind(mode_db)
    .bind(&invoice_id)
    .execute(&state.db)
    .await?;

    // Assign seat — availability was already validated above, so this insert should
    // always succeed; the ON CONFLICT clause remains only as a defensive fallback for
    // reclaiming a slot released between the check above and this insert.
    if let Some(seat) = seat {
        sqlx::query("UPDATE memberships SET seat_id = $2 WHERE id = $1")
            .bind(membership.id)
            .bind(seat.id)
            .execute(&state.db)
            .await?;

        let rows_affected = sqlx::query(
            "INSERT INTO seat_bookings (seat_id, user_id, membership_id, shift, booking_date, end_date)
             VALUES ($1, $2, $3, $4, $5, $6)
             ON CONFLICT (seat_id, shift, booking_date) DO UPDATE SET
                 status = 'ACTIVE', user_id = EXCLUDED.user_id,
                 membership_id = EXCLUDED.membership_id, end_date = EXCLUDED.end_date
             WHERE seat_bookings.status != 'ACTIVE'",
        )
        .bind(seat.id)
        .bind(req.user_id)
        .bind(membership.id)
        .bind(&req.shift)
        .bind(req.start_date)
        .bind(end_date)
        .execute(&state.db)
        .await?
        .rows_affected();

        if rows_affected == 0 {
            return Err(AppError::Conflict(format!(
                "Seat {} was booked by someone else just now — please retry", req.seat_number.as_deref().unwrap_or_default()
            )));
        }
    }

    // Send booking confirmation notification to student and admin
    if let Ok(user) = sqlx::query_as::<_, crate::models::user::User>(
        "SELECT * FROM users WHERE id = $1",
    )
    .bind(req.user_id)
    .fetch_one(&state.db)
    .await
    {
        let info = notification::BookingInfo {
            user_id: req.user_id,
            membership_id: membership.id,
            user_name: user.name.clone(),
            user_mobile: user.mobile.clone(),
            user_email: user.email.clone(),
            plan_name: plan.name.clone(),
            plan_type: plan.plan_type.clone(),
            seat_number: req.seat_number.clone(),
            shift: req.shift.clone(),
            start_date: req.start_date,
            end_date,
            amount_paid: req.amount,
        };
        let s = state.clone();
        tokio::spawn(async move { notification::send_booking_confirmed(&s, &info).await });

        let s2 = state.clone();
        let receipt_event = notification::PaymentReceiptInfo {
            user_id: req.user_id,
            user_name: user.name.clone(),
            user_mobile: user.mobile.clone(),
            user_email: user.email.clone(),
            invoice_id: invoice_id.clone(),
            amount_paid: req.amount,
            amount_pending: pending,
            plan_name: plan.name.clone(),
            seat_number: req.seat_number.clone(),
            valid_upto: Some(end_date),
            payment_method: mode_label.into(),
        };
        tokio::spawn(async move { notification::send_payment_receipt(&s2, &receipt_event).await });
    }

    Ok(serde_json::json!({
        "membership_id": membership.id,
        "start_date": membership.start_date,
        "end_date": end_date,
        "status": "ACTIVE"
    }))
}

// ── Seat change ───────────────────────────────────────────────────────────────

/// Reports whether extending `membership`'s own seat booking's `end_date` out
/// to `new_end` (which may be the far-future GRACE sentinel — a plain date
/// comparison in SQL, not the day-by-day cache-busting loop that sentinel is
/// dangerous with elsewhere) would newly overlap a *different* membership
/// already booked on the same physical seat. `membership_id != $2` excludes
/// the row being extended itself, since its own (pre-extension) range would
/// otherwise trivially "conflict" with the new one. Always `false` when the
/// membership never held a seat.
async fn seat_conflict_on_extension(
    state: &Arc<AppState>,
    membership: &Membership,
    new_end: NaiveDate,
) -> crate::error::Result<bool> {
    let Some(seat_id) = membership.seat_id else { return Ok(false) };
    let shift = membership.shift.as_deref().unwrap_or("FULL_DAY");

    let conflict: i64 = sqlx::query_scalar(
        r#"SELECT COUNT(*) FROM seat_bookings
         WHERE seat_id = $1
           AND status = 'ACTIVE'
           AND membership_id != $2
           AND booking_date <= $4
           AND end_date >= $3
           AND (shift = $5 OR shift = 'FULL_DAY' OR $5::text = 'FULL_DAY')"#,
    )
    .bind(seat_id)
    .bind(membership.id)
    .bind(membership.start_date)
    .bind(new_end)
    .bind(shift)
    .fetch_one(&state.db)
    .await?;

    Ok(conflict > 0)
}

/// `renew_seat`, `clear_dues`, `update_membership_plan`'s additional-days/
/// explicit-end-date branches, and `mark_membership_grace` all push a
/// booking's end_date further out in place (unlike
/// `change_membership_seat`/`book_seat`/`create_cash_membership`, which claim
/// a seat fresh and already conflict-check it) — this rejects the whole
/// action outright when that would double-book a seat a later tenant already
/// holds. Only suitable for a single interactive admin action with a caller
/// to report the error to; the batch job (`mark_expired_and_start_grace`)
/// uses `seat_conflict_on_extension` directly instead, since aborting an
/// entire nightly sweep over one problem membership isn't the right response
/// there (see that function for what it does instead).
async fn check_no_seat_conflict_on_extension(
    state: &Arc<AppState>,
    membership: &Membership,
    new_end: NaiveDate,
) -> crate::error::Result<()> {
    if seat_conflict_on_extension(state, membership, new_end).await? {
        return Err(AppError::Conflict(format!(
            "Seat {} is already booked by another student during the extended period",
            membership.seat_number.as_deref().unwrap_or("?")
        )));
    }
    Ok(())
}

pub async fn change_membership_seat(
    state: &Arc<AppState>,
    membership_id: Uuid,
    new_seat_number: &str,
) -> crate::error::Result<()> {
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE id = $1",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Membership not found".into()))?;

    if membership.status != "ACTIVE" {
        return Err(AppError::BadRequest(
            "Seat can only be changed for an ACTIVE membership".into(),
        ));
    }

    let new_seat = sqlx::query_as::<_, crate::models::seat::Seat>(
        "SELECT * FROM seats WHERE seat_number = $1 AND is_active = true",
    )
    .bind(new_seat_number)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Seat not found".into()))?;

    // Validate the new seat is actually free *before* releasing the old booking —
    // otherwise a conflict discovered later leaves the student with no seat at all
    // (old one released, new one silently not reserved because someone else holds it).
    let conflict: i64 = sqlx::query_scalar(
        r#"SELECT COUNT(*) FROM seat_bookings
         WHERE seat_id = $1
           AND status = 'ACTIVE'
           AND booking_date <= $3
           AND end_date >= $2
           AND (shift = $4 OR shift = 'FULL_DAY' OR $4::text = 'FULL_DAY')"#,
    )
    .bind(new_seat.id)
    .bind(membership.start_date)
    .bind(membership.end_date)
    .bind(membership.shift.as_deref().unwrap_or("FULL_DAY"))
    .fetch_one(&state.db)
    .await?;
    if conflict > 0 {
        return Err(AppError::Conflict(format!(
            "Seat {new_seat_number} is already booked during the requested period"
        )));
    }

    // Release old bookings
    sqlx::query(
        "UPDATE seat_bookings SET status = 'RELEASED'
         WHERE membership_id = $1 AND status = 'ACTIVE'",
    )
    .bind(membership_id)
    .execute(&state.db)
    .await?;

    // Create new booking, reclaiming any released slot for the same date
    let rows_affected = sqlx::query(
        "INSERT INTO seat_bookings (seat_id, user_id, membership_id, shift, booking_date, end_date)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (seat_id, shift, booking_date) DO UPDATE SET
             status = 'ACTIVE', user_id = EXCLUDED.user_id,
             membership_id = EXCLUDED.membership_id, end_date = EXCLUDED.end_date
         WHERE seat_bookings.status != 'ACTIVE'",
    )
    .bind(new_seat.id)
    .bind(membership.user_id)
    .bind(membership_id)
    .bind(&membership.shift)
    .bind(membership.start_date)
    .bind(membership.end_date)
    .execute(&state.db)
    .await?
    .rows_affected();

    if rows_affected == 0 {
        return Err(AppError::Conflict(format!(
            "Seat {new_seat_number} was booked by someone else just now — please retry"
        )));
    }

    sqlx::query(
        "UPDATE memberships SET seat_id = $2, seat_number = $3 WHERE id = $1",
    )
    .bind(membership_id)
    .bind(new_seat.id)
    .bind(new_seat_number)
    .execute(&state.db)
    .await?;

    if let Some(ref shift) = membership.shift {
        crate::services::seat::invalidate_seat_cache(
            state, shift, membership.start_date, membership.end_date,
        ).await;
    }

    Ok(())
}

/// Swaps the physical seat between two ACTIVE memberships — each student keeps
/// their own plan/shift/date range, only the seat_number/seat_id trade places.
/// Unlike `change_membership_seat`, no pre-flight conflict check against a third
/// party is needed: both seats are already legitimately held by exactly these
/// two memberships, so releasing both before reassigning (all in one transaction)
/// can't collide with anyone else's booking.
pub async fn swap_membership_seats(
    state: &Arc<AppState>,
    membership_id: Uuid,
    other_user_id: Uuid,
) -> crate::error::Result<()> {
    let membership_a = sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
        .bind(membership_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Membership not found".into()))?;

    let membership_b = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE user_id = $1 AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1",
    )
    .bind(other_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::BadRequest("The other student doesn't have an active membership".into()))?;

    if membership_a.id == membership_b.id {
        return Err(AppError::BadRequest("Cannot exchange a seat with the same student".into()));
    }
    if membership_a.status != "ACTIVE" {
        return Err(AppError::BadRequest("This student's membership must be ACTIVE to exchange seats".into()));
    }

    let (seat_id_a, seat_number_a) = match (membership_a.seat_id, membership_a.seat_number.clone()) {
        (Some(id), Some(num)) => (id, num),
        _ => return Err(AppError::BadRequest("This student doesn't currently have a seat".into())),
    };
    let (seat_id_b, seat_number_b) = match (membership_b.seat_id, membership_b.seat_number.clone()) {
        (Some(id), Some(num)) => (id, num),
        _ => return Err(AppError::BadRequest("The other student doesn't currently have a seat".into())),
    };

    let mut tx = state.db.begin().await.map_err(AppError::Database)?;

    // Release both current bookings first so the unique (seat_id, shift, booking_date)
    // constraint never has to reconcile two ACTIVE rows for the same seat at once.
    sqlx::query("UPDATE seat_bookings SET status = 'RELEASED' WHERE membership_id = $1 AND status = 'ACTIVE'")
        .bind(membership_a.id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;
    sqlx::query("UPDATE seat_bookings SET status = 'RELEASED' WHERE membership_id = $1 AND status = 'ACTIVE'")
        .bind(membership_b.id)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    // A takes B's old seat, keeping A's own shift/date range; reclaims the slot
    // just released above if the (seat, shift, date) key matches exactly.
    sqlx::query(
        "INSERT INTO seat_bookings (seat_id, user_id, membership_id, shift, booking_date, end_date)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (seat_id, shift, booking_date) DO UPDATE SET
             status = 'ACTIVE', user_id = EXCLUDED.user_id,
             membership_id = EXCLUDED.membership_id, end_date = EXCLUDED.end_date
         WHERE seat_bookings.status != 'ACTIVE'",
    )
    .bind(seat_id_b)
    .bind(membership_a.user_id)
    .bind(membership_a.id)
    .bind(&membership_a.shift)
    .bind(membership_a.start_date)
    .bind(membership_a.end_date)
    .execute(&mut *tx)
    .await
    .map_err(AppError::Database)?;

    // B takes A's old seat.
    sqlx::query(
        "INSERT INTO seat_bookings (seat_id, user_id, membership_id, shift, booking_date, end_date)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (seat_id, shift, booking_date) DO UPDATE SET
             status = 'ACTIVE', user_id = EXCLUDED.user_id,
             membership_id = EXCLUDED.membership_id, end_date = EXCLUDED.end_date
         WHERE seat_bookings.status != 'ACTIVE'",
    )
    .bind(seat_id_a)
    .bind(membership_b.user_id)
    .bind(membership_b.id)
    .bind(&membership_b.shift)
    .bind(membership_b.start_date)
    .bind(membership_b.end_date)
    .execute(&mut *tx)
    .await
    .map_err(AppError::Database)?;

    sqlx::query("UPDATE memberships SET seat_id = $2, seat_number = $3 WHERE id = $1")
        .bind(membership_a.id)
        .bind(seat_id_b)
        .bind(&seat_number_b)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;
    sqlx::query("UPDATE memberships SET seat_id = $2, seat_number = $3 WHERE id = $1")
        .bind(membership_b.id)
        .bind(seat_id_a)
        .bind(&seat_number_a)
        .execute(&mut *tx)
        .await
        .map_err(AppError::Database)?;

    tx.commit().await.map_err(AppError::Database)?;

    if let Some(ref shift) = membership_a.shift {
        crate::services::seat::invalidate_seat_cache(state, shift, membership_a.start_date, membership_a.end_date).await;
    }
    if let Some(ref shift) = membership_b.shift {
        crate::services::seat::invalidate_seat_cache(state, shift, membership_b.start_date, membership_b.end_date).await;
    }

    Ok(())
}

pub async fn update_membership_plan(
    state: &Arc<AppState>,
    membership_id: Uuid,
    req: &UpdatePlanRequest,
) -> crate::error::Result<Membership> {
    let before = sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
        .bind(membership_id)
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)?;

    if let Some(new_plan_id) = req.plan_id {
        sqlx::query("UPDATE memberships SET plan_id = $2 WHERE id = $1")
            .bind(membership_id)
            .bind(new_plan_id)
            .execute(&state.db)
            .await?;
    }
    if let Some(extra_days) = req.additional_days {
        let new_end = before.end_date + chrono::Duration::days(extra_days as i64);
        check_no_seat_conflict_on_extension(state, &before, new_end).await?;

        sqlx::query(
            "UPDATE memberships SET end_date = end_date + ($2 || ' days')::INTERVAL WHERE id = $1",
        )
        .bind(membership_id)
        .bind(extra_days.to_string())
        .execute(&state.db)
        .await?;
        sqlx::query(
            "UPDATE seat_bookings SET end_date = end_date + ($2 || ' days')::INTERVAL
             WHERE membership_id = $1 AND status = 'ACTIVE'",
        )
        .bind(membership_id)
        .bind(extra_days.to_string())
        .execute(&state.db)
        .await?;
    }
    if let Some(end_date) = req.end_date {
        check_no_seat_conflict_on_extension(state, &before, end_date).await?;

        sqlx::query("UPDATE memberships SET end_date = $2 WHERE id = $1")
            .bind(membership_id)
            .bind(end_date)
            .execute(&state.db)
            .await?;
        sqlx::query(
            "UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'",
        )
        .bind(membership_id)
        .bind(end_date)
        .execute(&state.db)
        .await?;
    }

    let after = sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
        .bind(membership_id)
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)?;

    // Busting the cache is what keeps the seat map from showing stale
    // occupancy after an admin extends/shortens a booking here — same pattern
    // as change_membership_seat/clear_dues just above and below.
    // `memberships.end_date` is always a real, finite date — even while
    // GRACE (it's `seat_bookings.end_date` that carries the far-future
    // sentinel) — so no capping is needed here.
    if after.end_date != before.end_date {
        if let Some(ref shift) = after.shift {
            let from = before.end_date.min(after.end_date);
            let to = before.end_date.max(after.end_date);
            crate::services::seat::invalidate_seat_cache(state, shift, from, to).await;
        }
    }

    Ok(after)
}

// ── Grace / dues admin actions ───────────────────────────────────────────────

/// Force-frees a seat from an ACTIVE (currently-paying) or GRACE membership —
/// dues, if any, are NOT waived. `notify_student` is a per-call choice, not a
/// persistent setting.
pub async fn release_seat(
    state: &Arc<AppState>,
    membership_id: Uuid,
    notify_student: bool,
) -> crate::error::Result<()> {
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE id = $1 AND status IN ('ACTIVE', 'GRACE')",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("No active or grace membership found".into()))?;

    sqlx::query("UPDATE memberships SET status = 'EXPIRED' WHERE id = $1")
        .bind(membership_id)
        .execute(&state.db)
        .await?;

    let bookings = sqlx::query_as::<_, crate::models::seat::SeatBooking>(
        "UPDATE seat_bookings SET status = 'RELEASED' WHERE membership_id = $1 AND status = 'ACTIVE' RETURNING *",
    )
    .bind(membership_id)
    .fetch_all(&state.db)
    .await?;

    let user_name = sqlx::query_scalar::<_, String>("SELECT name FROM users WHERE id = $1")
        .bind(membership.user_id)
        .fetch_optional(&state.db)
        .await?
        .unwrap_or_else(|| "Unknown".to_string());

    for b in &bookings {
        // A released seat becomes bookable again for upcoming dates, so the
        // invalidation must look forward, not just at/before today — matches
        // Java's `releaseSeat` (`today` .. `today + 14 days`). A GRACE
        // booking's end_date may be the far-future sentinel, which would hang
        // the day-by-day cache-busting loop if ever used as the upper bound,
        // so the window here is hardcoded rather than derived from it.
        let today = chrono::Local::now().date_naive();
        let from = b.booking_date.min(today);
        let to = today + chrono::Duration::days(14);
        crate::services::seat::invalidate_seat_cache(state, &b.shift, from, to).await;

        let s = state.clone();
        let uname = user_name.clone();
        let seat_num = sqlx::query_scalar::<_, String>("SELECT seat_number FROM seats WHERE id = $1")
            .bind(b.seat_id)
            .fetch_optional(&state.db)
            .await
            .ok()
            .flatten()
            .unwrap_or_else(|| "N/A".to_string());
        tokio::spawn(async move { notification::send_seat_expired(&s, &uname, &seat_num).await });
    }

    if notify_student {
        let user = sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
            .bind(membership.user_id)
            .fetch_optional(&state.db)
            .await?;
        if let Some(user) = user {
            let msg = "Your library seat has been released due to non-payment. Please contact the library or clear your dues to book again. - Target Zone Library Team".to_string();
            notification::send_direct_message(state, user.mobile.as_deref(), user.email.as_deref(), &msg).await;
        }
    }

    Ok(())
}

/// Admin manual +1 month extension for an already-PAID ACTIVE student.
pub async fn renew_seat(state: &Arc<AppState>, membership_id: Uuid) -> crate::error::Result<Membership> {
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE id = $1 AND status = 'ACTIVE'",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("No active membership found".into()))?;

    let new_end = membership.end_date.checked_add_months(chrono::Months::new(1))
        .ok_or_else(|| AppError::Internal("Date overflow computing renewal".into()))?;

    check_no_seat_conflict_on_extension(state, &membership, new_end).await?;

    let updated = sqlx::query_as::<_, Membership>(
        "UPDATE memberships SET end_date = $2 WHERE id = $1 RETURNING *",
    )
    .bind(membership_id)
    .bind(new_end)
    .fetch_one(&state.db)
    .await?;

    sqlx::query("UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'")
        .bind(membership_id)
        .bind(new_end)
        .execute(&state.db)
        .await?;

    if let Some(ref shift) = updated.shift {
        crate::services::seat::invalidate_seat_cache(state, shift, membership.end_date, new_end).await;
    }

    Ok(updated)
}

/// Admin correction: a membership was wrongly marked fully paid — void the
/// erroneous latest Payment row and record the real (partial) amount owed.
pub async fn mark_membership_pending(
    state: &Arc<AppState>,
    membership_id: Uuid,
    pending_amount: Decimal,
) -> crate::error::Result<()> {
    let membership = sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
        .bind(membership_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Membership not found".into()))?;

    if membership.status != "ACTIVE" {
        return Err(AppError::BadRequest(
            "Only an ACTIVE membership can be marked Pending".into(),
        ));
    }

    let plan = sqlx::query_as::<_, MembershipPlan>("SELECT * FROM membership_plans WHERE id = $1")
        .bind(membership.plan_id)
        .fetch_one(&state.db)
        .await?;

    if pending_amount > plan.price {
        return Err(AppError::BadRequest(format!(
            "Pending amount (₹{pending_amount}) cannot exceed the plan price (₹{})",
            plan.price
        )));
    }

    let latest_gateway: Option<String> = sqlx::query_scalar(
        "DELETE FROM payments WHERE id = (
            SELECT id FROM payments WHERE membership_id = $1 ORDER BY created_at DESC LIMIT 1
         ) RETURNING payment_gateway",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await?;

    let paid_amount = (plan.price - pending_amount).max(Decimal::ZERO);

    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'SUCCESS')",
    )
    .bind(membership_id)
    .bind(membership.user_id)
    .bind(paid_amount)
    .bind(pending_amount)
    .bind(latest_gateway.unwrap_or_else(|| "CASH".to_string()))
    .bind(ids::generate_invoice_id())
    .execute(&state.db)
    .await?;

    Ok(())
}

/// Admin correction: a membership was wrongly marked fully paid — it's
/// actually unresolved dues. Resets `end_date` to today (else "days
/// overdue" would go negative), sets `dues_amount = plan.price`, and pushes
/// the linked seat booking to the far-future sentinel like the grace cron does.
pub async fn mark_membership_grace(state: &Arc<AppState>, membership_id: Uuid) -> crate::error::Result<()> {
    let membership = sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
        .bind(membership_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Membership not found".into()))?;

    if membership.status != "ACTIVE" {
        return Err(AppError::BadRequest(
            "Only an ACTIVE membership can be marked Grace".into(),
        ));
    }

    // GRACE holds the seat indefinitely (the far-future sentinel below), so
    // this must reject up front if a different membership already holds a
    // legitimate future booking on the same seat — otherwise this would
    // silently double-book it. A single interactive admin action, so
    // rejecting outright (rather than the batch job's skip-and-continue) is
    // the right response; see `check_no_seat_conflict_on_extension`.
    let far_future = NaiveDate::from_ymd_opt(9999, 12, 31).expect("valid sentinel date");
    check_no_seat_conflict_on_extension(state, &membership, far_future).await?;

    let plan = sqlx::query_as::<_, MembershipPlan>("SELECT * FROM membership_plans WHERE id = $1")
        .bind(membership.plan_id)
        .fetch_one(&state.db)
        .await?;

    let latest_gateway: Option<String> = sqlx::query_scalar(
        "DELETE FROM payments WHERE id = (
            SELECT id FROM payments WHERE membership_id = $1 ORDER BY created_at DESC LIMIT 1
         ) RETURNING payment_gateway",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await?;

    // Void the incorrect payment with a zero-amount corrected row — the real
    // amount owed lives in memberships.dues_amount, not payments.pending_amount.
    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, invoice_id, status)
         VALUES ($1, $2, 0, 0, $3, $4, 'SUCCESS')",
    )
    .bind(membership_id)
    .bind(membership.user_id)
    .bind(latest_gateway.unwrap_or_else(|| "CASH".to_string()))
    .bind(ids::generate_invoice_id())
    .execute(&state.db)
    .await?;

    let today = chrono::Local::now().date_naive();

    sqlx::query(
        "UPDATE memberships SET status = 'GRACE', end_date = $2, dues_amount = $3 WHERE id = $1",
    )
    .bind(membership_id)
    .bind(today)
    .bind(plan.price)
    .execute(&state.db)
    .await?;

    sqlx::query(
        "UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'",
    )
    .bind(membership_id)
    .bind(far_future)
    .execute(&state.db)
    .await?;

    if let Some(ref shift) = membership.shift {
        // Bounded (never the far-future sentinel as an upper bound) but wide
        // enough forward to actually cover the held-indefinitely seat map —
        // matches Java's markMembershipGrace (`today` .. `today + 60 days`).
        crate::services::seat::invalidate_seat_cache(state, shift, today, today + chrono::Duration::days(60)).await;
    }

    Ok(())
}

/// Admin dues clearance — full or partial. Extends `end_date` by +1 month
/// from the membership's existing (stale) end_date, not from today, so an
/// overdue-by-more-than-a-month clearance can land in the past; this is a
/// deliberate product decision carried over from the Java backend.
pub async fn clear_dues(
    state: &Arc<AppState>,
    membership_id: Uuid,
    amount_cleared: Decimal,
    payment_mode: Option<&str>,
) -> crate::error::Result<()> {
    let membership = sqlx::query_as::<_, Membership>(
        "SELECT * FROM memberships WHERE id = $1 AND status = 'GRACE'",
    )
    .bind(membership_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("No grace membership found".into()))?;

    let dues = membership.dues_amount.unwrap_or_default();
    if amount_cleared <= Decimal::ZERO || amount_cleared > dues {
        return Err(AppError::BadRequest("Amount cleared must be between 0 and the outstanding dues".into()));
    }

    let (mode_db, mode_label) = resolve_admin_payment_mode(payment_mode)?;

    let remainder = dues - amount_cleared;
    let new_end = membership.end_date.checked_add_months(chrono::Months::new(1))
        .ok_or_else(|| AppError::Internal("Date overflow computing dues clearance".into()))?;

    check_no_seat_conflict_on_extension(state, &membership, new_end).await?;

    let invoice_id = ids::generate_invoice_id();
    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, invoice_id, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'SUCCESS')",
    )
    .bind(membership_id)
    .bind(membership.user_id)
    .bind(amount_cleared)
    .bind(remainder)
    .bind(mode_db)
    .bind(&invoice_id)
    .execute(&state.db)
    .await?;

    let updated = sqlx::query_as::<_, Membership>(
        "UPDATE memberships SET status = 'ACTIVE', dues_amount = 0, end_date = $2 WHERE id = $1 RETURNING *",
    )
    .bind(membership_id)
    .bind(new_end)
    .fetch_one(&state.db)
    .await?;

    sqlx::query("UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'")
        .bind(membership_id)
        .bind(new_end)
        .execute(&state.db)
        .await?;

    if let Some(ref shift) = updated.shift {
        crate::services::seat::invalidate_seat_cache(state, shift, membership.end_date, new_end).await;
    }

    let plan = sqlx::query_as::<_, MembershipPlan>("SELECT * FROM membership_plans WHERE id = $1")
        .bind(updated.plan_id)
        .fetch_one(&state.db)
        .await?;
    let user = sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
        .bind(membership.user_id)
        .fetch_one(&state.db)
        .await?;

    let gd_setting = settings::setting_for(state, "GRACE_DUES_CLEARED").await;
    if gd_setting.send_to_student {
        let msg = settings::apply_hindi(
            &format!("Your outstanding dues of Rs.{amount_cleared:.0} have been cleared and your membership is active again. - Target Zone Library Team"),
            &gd_setting, true,
        );
        notification::send_direct_message(state, user.mobile.as_deref(), user.email.as_deref(), &msg).await;
    }

    let s = state.clone();
    let receipt_event = notification::PaymentReceiptInfo {
        user_id: membership.user_id,
        user_name: user.name.clone(),
        user_mobile: user.mobile.clone(),
        user_email: user.email.clone(),
        invoice_id,
        amount_paid: amount_cleared,
        amount_pending: remainder,
        plan_name: plan.name,
        seat_number: updated.seat_number.clone(),
        valid_upto: Some(new_end),
        payment_method: mode_label.into(),
    };
    tokio::spawn(async move {
        notification::send_payment_receipt_typed(&s, &receipt_event, "GRACE_DUES_CLEARED").await
    });

    Ok(())
}

// ── Feedback ──────────────────────────────────────────────────────────────────

pub async fn get_all_feedback(
    state: &Arc<AppState>,
    feedback_type: Option<&str>,
    status: Option<&str>,
) -> crate::error::Result<Vec<AdminFeedbackItem>> {
    sqlx::query_as::<_, AdminFeedbackItem>(
        r#"SELECT f.id, f.user_id,
                  u.name  AS student_name,
                  u.mobile AS student_mobile,
                  f."type" AS feedback_type,
                  f.subject, f.description, f.status, f.admin_notes,
                  f.created_at, f.updated_at
           FROM feedbacks f
           JOIN users u ON u.id = f.user_id
           WHERE ($1::text IS NULL OR f."type" = $1)
             AND ($2::text IS NULL OR f.status = $2)
           ORDER BY f.created_at DESC"#,
    )
    .bind(feedback_type)
    .bind(status)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

/// Feedback progresses OPEN → UNDER_REVIEW → RESOLVED and never backward —
/// mirrors Java's `FeedbackAdminService.validateStatusTransition`.
fn validate_feedback_status_transition(current: &str, next: &str) -> crate::error::Result<()> {
    let valid = match current {
        "OPEN" => true,
        "UNDER_REVIEW" => matches!(next, "UNDER_REVIEW" | "RESOLVED"),
        "RESOLVED" => next == "RESOLVED",
        _ => false,
    };
    if valid {
        Ok(())
    } else {
        Err(AppError::BadRequest(format!(
            "Invalid status transition: {current} → {next}"
        )))
    }
}

pub async fn update_feedback(
    state: &Arc<AppState>,
    feedback_id: Uuid,
    req: &UpdateFeedbackRequest,
) -> crate::error::Result<crate::models::user::Feedback> {
    let normalized_status = match req.status.as_deref().map(str::trim) {
        Some(s) if !s.is_empty() => {
            let upper = s.to_uppercase();
            if !matches!(upper.as_str(), "OPEN" | "UNDER_REVIEW" | "RESOLVED") {
                return Err(AppError::BadRequest(
                    "Invalid status. Must be OPEN, UNDER_REVIEW, or RESOLVED".into(),
                ));
            }
            Some(upper)
        }
        _ => None,
    };

    if let Some(ref next) = normalized_status {
        let current: String = sqlx::query_scalar("SELECT status FROM feedbacks WHERE id = $1")
            .bind(feedback_id)
            .fetch_optional(&state.db)
            .await?
            .ok_or_else(|| AppError::NotFound("Feedback not found".into()))?;
        validate_feedback_status_transition(&current, next)?;
    }

    sqlx::query_as::<_, crate::models::user::Feedback>(
        "UPDATE feedbacks SET
            status     = COALESCE($2, status),
            admin_notes = COALESCE($3, admin_notes),
            updated_at = NOW()
         WHERE id = $1 RETURNING *",
    )
    .bind(feedback_id)
    .bind(normalized_status)
    .bind(req.admin_notes.as_deref().map(str::trim))
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)
}

// ── Revenue ───────────────────────────────────────────────────────────────────

pub async fn get_revenue(
    state: &Arc<AppState>,
    from: NaiveDate,
    to: NaiveDate,
) -> crate::error::Result<RevenueReport> {
    let total: Option<Decimal> = sqlx::query_scalar(
        "SELECT SUM(amount) FROM payments WHERE status = 'SUCCESS'
         AND DATE(created_at) BETWEEN $1 AND $2",
    )
    .bind(from)
    .bind(to)
    .fetch_one(&state.db)
    .await?;

    let total_transactions: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM payments WHERE status = 'SUCCESS'
         AND DATE(created_at) BETWEEN $1 AND $2",
    )
    .bind(from)
    .bind(to)
    .fetch_one(&state.db)
    .await?;

    let daily_breakdown = sqlx::query_as::<_, DailyRevenue>(
        "SELECT DATE(created_at) as date, SUM(amount) as amount, COUNT(*) as count
         FROM payments WHERE status = 'SUCCESS'
         AND DATE(created_at) BETWEEN $1 AND $2
         GROUP BY DATE(created_at) ORDER BY date",
    )
    .bind(from)
    .bind(to)
    .fetch_all(&state.db)
    .await?;

    let half_day_revenue: Option<Decimal> = sqlx::query_scalar(
        "SELECT SUM(p.amount) FROM payments p
         JOIN memberships m ON m.id = p.membership_id
         JOIN membership_plans mp ON mp.id = m.plan_id
         WHERE p.status = 'SUCCESS' AND DATE(p.created_at) BETWEEN $1 AND $2
           AND mp.plan_type = 'HALF_DAY'",
    )
    .bind(from)
    .bind(to)
    .fetch_one(&state.db)
    .await?;

    let full_day_revenue: Option<Decimal> = sqlx::query_scalar(
        "SELECT SUM(p.amount) FROM payments p
         JOIN memberships m ON m.id = p.membership_id
         JOIN membership_plans mp ON mp.id = m.plan_id
         WHERE p.status = 'SUCCESS' AND DATE(p.created_at) BETWEEN $1 AND $2
           AND mp.plan_type = 'FULL_DAY'",
    )
    .bind(from)
    .bind(to)
    .fetch_one(&state.db)
    .await?;

    Ok(RevenueReport {
        from_date: from,
        to_date: to,
        total_revenue: total.unwrap_or_default(),
        total_transactions,
        half_day_revenue: half_day_revenue.unwrap_or_default(),
        full_day_revenue: full_day_revenue.unwrap_or_default(),
        daily_breakdown,
    })
}

pub async fn get_payment_breakdown(
    state: &Arc<AppState>,
    from: NaiveDate,
    to: NaiveDate,
) -> crate::error::Result<Vec<PaymentBreakdownItem>> {
    sqlx::query_as::<_, PaymentBreakdownItem>(
        "SELECT payment_gateway AS gateway, SUM(amount) AS amount, COUNT(*)::bigint AS count
         FROM payments WHERE status = 'SUCCESS'
         AND DATE(created_at) BETWEEN $1 AND $2
         GROUP BY payment_gateway
         ORDER BY amount DESC",
    )
    .bind(from)
    .bind(to)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

// ── Expenses ──────────────────────────────────────────────────────────────────

pub async fn get_expenses(
    state: &Arc<AppState>,
    year: i32,
    month: i32,
) -> crate::error::Result<Option<MonthlyExpenseWithItems>> {
    let Some(expense) = sqlx::query_as::<_, MonthlyExpense>(
        "SELECT * FROM monthly_expenses WHERE year = $1 AND month = $2",
    )
    .bind(year)
    .bind(month)
    .fetch_optional(&state.db)
    .await? else {
        return Ok(None);
    };

    let mut misc_items = sqlx::query_as::<_, MiscExpenseItem>(
        "SELECT * FROM misc_expense_items WHERE monthly_expense_id = $1 ORDER BY sort_order",
    )
    .bind(expense.id)
    .fetch_all(&state.db)
    .await?;

    // Migration fallback: a record saved before the itemized-breakdown feature
    // existed has a nonzero `miscellaneous` total but no misc_expense_items
    // rows — synthesize a single "General" line so the breakdown isn't just
    // empty. Matches Java's ExpenseService.toDto.
    if misc_items.is_empty() && expense.miscellaneous > Decimal::ZERO {
        misc_items.push(MiscExpenseItem {
            id: Uuid::nil(),
            monthly_expense_id: expense.id,
            description: "General".to_string(),
            amount: expense.miscellaneous,
            sort_order: None,
        });
    }

    // Matches Java's toDto: water cost is price × qty, and the misc total
    // comes only from `misc_items` (real breakdown or the synthesized legacy
    // fallback above) — `expense.miscellaneous` is a denormalized cache of
    // that same sum, not a separate line, so it must not also be added here.
    let total = expense.electricity_bill
        + expense.internet_bill
        + expense.water_tanker_price * Decimal::from(expense.water_tanker_qty)
        + misc_items.iter().map(|i| i.amount).sum::<Decimal>();

    Ok(Some(MonthlyExpenseWithItems { expense, misc_items, total }))
}

pub async fn save_expense(
    state: &Arc<AppState>,
    req: &SaveExpenseRequest,
) -> crate::error::Result<MonthlyExpenseWithItems> {
    let misc_total: Decimal = req.misc_items.as_ref()
        .map(|v| v.iter().map(|i| i.amount).sum())
        .unwrap_or_default();

    let expense = sqlx::query_as::<_, MonthlyExpense>(
        "INSERT INTO monthly_expenses (year, month, water_tanker_qty, water_tanker_price,
                                        electricity_bill, internet_bill, miscellaneous)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         ON CONFLICT (year, month) DO UPDATE SET
            water_tanker_qty   = EXCLUDED.water_tanker_qty,
            water_tanker_price = EXCLUDED.water_tanker_price,
            electricity_bill   = EXCLUDED.electricity_bill,
            internet_bill      = EXCLUDED.internet_bill,
            miscellaneous      = EXCLUDED.miscellaneous,
            updated_at         = NOW()
         RETURNING *",
    )
    .bind(req.year)
    .bind(req.month)
    .bind(req.water_tanker_qty.unwrap_or(0))
    .bind(req.water_tanker_price.unwrap_or_default())
    .bind(req.electricity_bill.unwrap_or_default())
    .bind(req.internet_bill.unwrap_or_default())
    .bind(misc_total)
    .fetch_one(&state.db)
    .await?;

    sqlx::query("DELETE FROM misc_expense_items WHERE monthly_expense_id = $1")
        .bind(expense.id)
        .execute(&state.db)
        .await?;

    let mut misc_items = Vec::new();
    if let Some(ref req_items) = req.misc_items {
        for (i, item) in req_items.iter().enumerate() {
            let inserted = sqlx::query_as::<_, MiscExpenseItem>(
                "INSERT INTO misc_expense_items (id, monthly_expense_id, description, amount, sort_order)
                 VALUES (gen_random_uuid(), $1, $2, $3, $4) RETURNING *",
            )
            .bind(expense.id)
            .bind(&item.description)
            .bind(item.amount)
            .bind(i as i32)
            .fetch_one(&state.db)
            .await?;
            misc_items.push(inserted);
        }
    }

    let total = expense.electricity_bill
        + expense.internet_bill
        + expense.water_tanker_price * Decimal::from(expense.water_tanker_qty)
        + misc_items.iter().map(|i| i.amount).sum::<Decimal>();

    Ok(MonthlyExpenseWithItems { expense, misc_items, total })
}

// ── Scheduler (called by background tasks) ───────────────────────────────────

pub async fn run_expiry_reminder_job(state: Arc<AppState>) {
    tracing::info!("Running expiry reminder scheduler job");
    match send_renewal_reminders(&state, None).await {
        Ok(n) => tracing::info!("Sent {n} renewal reminders"),
        Err(e) => tracing::error!("Reminder job error: {e}"),
    }
}

pub async fn run_mark_expired_job(state: Arc<AppState>) {
    tracing::info!("Running grace-transition scheduler job");
    match mark_expired_and_start_grace(&state).await {
        Ok(n) => tracing::info!("Grace transition: {n} membership(s) moved to GRACE"),
        Err(e) => tracing::error!("Grace transition job error: {e}"),
    }
}

/// Manual-trigger entry point for the admin "Cron Jobs" page — runs the same
/// logic as the daily scheduler and returns the count graced.
pub async fn run_expiry_check(state: &Arc<AppState>) -> crate::error::Result<i64> {
    mark_expired_and_start_grace(state).await
}

/// For every ACTIVE membership whose end_date has passed: if a QUEUED
/// renewal exists, the old membership simply expires and the queued one
/// activates (happy path, unchanged from before grace existed). Otherwise
/// the membership enters GRACE — dues_amount is set to the plan price and
/// the linked seat booking is held indefinitely (far-future sentinel) until
/// an admin releases the seat or the student/admin clears the dues.
pub async fn mark_expired_and_start_grace(state: &Arc<AppState>) -> crate::error::Result<i64> {
    let today = chrono::Local::now().date_naive();
    let far_future = NaiveDate::from_ymd_opt(9999, 12, 31).expect("valid sentinel date");

    let overdue = sqlx::query_as::<_, (Uuid, Uuid, Uuid, Option<String>, String)>(
        "SELECT m.id, m.user_id, m.plan_id, m.shift, u.name
         FROM memberships m
         JOIN users u ON u.id = m.user_id
         WHERE m.status = 'ACTIVE' AND m.end_date < $1",
    )
    .bind(today)
    .fetch_all(&state.db)
    .await?;

    if overdue.is_empty() {
        tracing::info!("grace_transition: no newly overdue memberships");
        return Ok(0);
    }

    let mut graced = 0i64;

    for (mem_id, user_id, plan_id, shift, name) in &overdue {
        let queued: Option<Uuid> = sqlx::query_scalar(
            "SELECT id FROM memberships WHERE user_id = $1 AND status = 'QUEUED' ORDER BY created_at LIMIT 1",
        )
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?;

        if let Some(queued_id) = queued {
            sqlx::query("UPDATE memberships SET status = 'EXPIRED' WHERE id = $1")
                .bind(mem_id)
                .execute(&state.db)
                .await?;
            sqlx::query("UPDATE memberships SET status = 'ACTIVE' WHERE id = $1")
                .bind(queued_id)
                .execute(&state.db)
                .await?;
            tracing::info!("Activated queued plan {queued_id} for user {user_id}");
            continue;
        }

        let plan = sqlx::query_as::<_, MembershipPlan>("SELECT * FROM membership_plans WHERE id = $1")
            .bind(plan_id)
            .fetch_one(&state.db)
            .await?;

        sqlx::query("UPDATE memberships SET status = 'GRACE', dues_amount = $2 WHERE id = $1")
            .bind(mem_id)
            .bind(plan.price)
            .execute(&state.db)
            .await?;

        // Unlike mark_membership_grace (a single interactive admin action
        // that can reject outright), this is an unattended nightly sweep —
        // aborting the whole run over one problem membership isn't the right
        // response. The membership above still correctly enters GRACE with
        // dues owed either way; only the seat *hold* is skipped when holding
        // it indefinitely would double-book a seat a later tenant already
        // legitimately holds (e.g. an admin pre-booked the next student
        // before this one defaulted). That later tenant's own booking is
        // left completely untouched, and this membership's stale (already-
        // past) seat_booking simply stops mattering to any future-dated
        // availability query without needing further changes here.
        let current = sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
            .bind(mem_id)
            .fetch_one(&state.db)
            .await?;
        if seat_conflict_on_extension(state, &current, far_future).await? {
            tracing::warn!(
                "grace_transition: membership {mem_id} entered GRACE with dues but its seat hold was \
skipped — seat {:?} already has a later legitimate booking",
                current.seat_number
            );
        } else {
            sqlx::query("UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'")
                .bind(mem_id)
                .bind(far_future)
                .execute(&state.db)
                .await?;
        }

        if let Some(shift) = shift {
            // Matches Java's ExpiryReminderScheduler.markExpiredAndStartGrace
            // (`today` .. `today + 14 days`) — the seat is now held
            // indefinitely, so nearby upcoming dates need busting too, not
            // just today.
            crate::services::seat::invalidate_seat_cache(state, shift, today, today + chrono::Duration::days(14)).await;
        }

        graced += 1;
        tracing::info!("Membership {mem_id} for '{name}' entered GRACE (dues Rs.{})", plan.price);

        let user = sqlx::query_as::<_, User>("SELECT * FROM users WHERE id = $1")
            .bind(user_id)
            .fetch_optional(&state.db)
            .await?;
        if let Some(user) = user {
            let s = state.clone();
            let uname = name.clone();
            let dues = plan.price;
            tokio::spawn(async move {
                let setting = settings::setting_for(&s, "MEMBERSHIP_GRACE").await;
                if setting.send_to_student {
                    let grace_days = settings::grace_days(&s).await;
                    let msg = settings::apply_hindi(
                        &format!(
                            "Hi {uname}, your library membership has expired with Rs.{dues:.0} in dues. \
You have {grace_days} day(s) grace period to clear your dues before your seat is released. - Target Zone Library Team"
                        ),
                        &setting, true,
                    );
                    notification::send_direct_message(&s, user.mobile.as_deref(), user.email.as_deref(), &msg).await;
                }
                if setting.send_to_admin && !s.config.admin_whatsapp.is_empty() {
                    let admin_msg = format!("Membership Grace Started: {uname} now owes Rs.{dues:.0} in dues.");
                    notification::send_whatsapp_to(&s, &s.config.admin_whatsapp.clone(), &admin_msg).await;
                }
            });
        }
    }

    tracing::info!("grace_transition: {graced} membership(s) moved to GRACE out of {} overdue", overdue.len());
    Ok(graced)
}

#[cfg(test)]
mod import_tests {
    use super::*;

    #[test]
    fn normalize_seat_number_strips_leading_zeros_after_letters() {
        assert_eq!(normalize_seat_number("A007"), "A7");
        assert_eq!(normalize_seat_number("b-14"), "B14");
        assert_eq!(normalize_seat_number(" c 3 "), "C3");
        assert_eq!(normalize_seat_number("D26"), "D26");
    }

    #[test]
    fn parse_import_fees_strips_non_numeric_characters() {
        assert_eq!(parse_import_fees("Rs 400.00"), Decimal::new(40000, 2));
        assert_eq!(parse_import_fees("400"), Decimal::new(400, 0));
        assert_eq!(parse_import_fees(""), Decimal::ZERO);
        assert_eq!(parse_import_fees("₹1,200"), Decimal::new(1200, 0));
    }

    #[test]
    fn translate_java_date_pattern_maps_common_tokens() {
        assert_eq!(translate_java_date_pattern("dd/MM/yyyy"), "%d/%m/%Y");
        assert_eq!(translate_java_date_pattern("M-d-yyyy"), "%m-%d-%Y");
        assert_eq!(translate_java_date_pattern("yy/MM/dd"), "%y/%m/%d");
    }

    #[test]
    fn extract_date_format_hint_reads_parenthesized_pattern() {
        assert_eq!(
            extract_date_format_hint("Date (dd/MM/yyyy)").as_deref(),
            Some("%d/%m/%Y")
        );
        assert_eq!(extract_date_format_hint("Join Date"), None);
    }

    #[test]
    fn parse_import_date_falls_back_across_formats() {
        assert_eq!(
            parse_import_date("15/06/2026", None).unwrap(),
            NaiveDate::from_ymd_opt(2026, 6, 15).unwrap()
        );
        assert_eq!(
            parse_import_date("2026-06-15", None).unwrap(),
            NaiveDate::from_ymd_opt(2026, 6, 15).unwrap()
        );
        assert_eq!(
            parse_import_date("06-15-2026", None).unwrap(),
            NaiveDate::from_ymd_opt(2026, 6, 15).unwrap()
        );
    }

    #[test]
    fn parse_import_date_blank_defaults_to_today() {
        let today = chrono::Local::now().date_naive();
        assert_eq!(parse_import_date("", None).unwrap(), today);
    }

    #[test]
    fn parse_import_date_uses_header_hint_first() {
        // "05/06/2026" is ambiguous (day/month or month/day); the hint must win.
        let d = parse_import_date("05/06/2026", Some("%d/%m/%Y")).unwrap();
        assert_eq!(d, NaiveDate::from_ymd_opt(2026, 6, 5).unwrap());
    }

    #[test]
    fn parse_import_date_unparseable_is_an_error() {
        assert!(parse_import_date("not-a-date", None).is_err());
    }

    #[test]
    fn parse_csv_rows_splits_fixed_columns() {
        let csv = "S.No,Name,Phone,Fees,Date,Seat\n1,Ravi Kumar,9876543210,400,15/06/2026,A7\n";
        let rows = parse_csv_rows(csv.as_bytes()).unwrap();
        assert_eq!(rows.len(), 2);
        assert_eq!(rows[1][1], "Ravi Kumar");
        assert_eq!(rows[1][2], "9876543210");
        assert_eq!(rows[1][5], "A7");
    }
}

#[cfg(test)]
mod feedback_status_tests {
    use super::validate_feedback_status_transition;

    #[test]
    fn open_can_transition_to_any_status() {
        assert!(validate_feedback_status_transition("OPEN", "OPEN").is_ok());
        assert!(validate_feedback_status_transition("OPEN", "UNDER_REVIEW").is_ok());
        assert!(validate_feedback_status_transition("OPEN", "RESOLVED").is_ok());
    }

    #[test]
    fn under_review_can_stay_or_resolve_but_not_reopen() {
        assert!(validate_feedback_status_transition("UNDER_REVIEW", "UNDER_REVIEW").is_ok());
        assert!(validate_feedback_status_transition("UNDER_REVIEW", "RESOLVED").is_ok());
        assert!(validate_feedback_status_transition("UNDER_REVIEW", "OPEN").is_err());
    }

    #[test]
    fn resolved_is_terminal() {
        assert!(validate_feedback_status_transition("RESOLVED", "RESOLVED").is_ok());
        assert!(validate_feedback_status_transition("RESOLVED", "OPEN").is_err());
        assert!(validate_feedback_status_transition("RESOLVED", "UNDER_REVIEW").is_err());
    }
}
