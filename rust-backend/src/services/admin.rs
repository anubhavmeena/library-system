use crate::{
    app_state::AppState,
    error::AppError,
    models::{
        admin::*,
        membership::{Membership, MembershipPlan},
        user::User,
    },
    services::{ids, notification, settings},
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

    let occupied_seats: i64 = sqlx::query_scalar(
        "SELECT COUNT(DISTINCT seat_id) FROM seat_bookings WHERE status = 'ACTIVE'
         AND booking_date <= CURRENT_DATE AND end_date >= CURRENT_DATE",
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
        total_seats,
        occupied_seats,
        available_seats: total_seats - occupied_seats,
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
             WHEN p.payment_gateway IS NOT NULL THEN 'ONLINE'
             ELSE NULL END AS payment_mode,
        ps.pending_amount,
        m.dues_amount,
        cur.status AS current_status, cur.end_date AS current_end_date,
        le.status AS latest_ever_status
    FROM users u
    LEFT JOIN LATERAL (
        SELECT * FROM memberships WHERE user_id = u.id
        ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, created_at DESC
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
        SELECT status FROM memberships WHERE user_id = u.id
        ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, created_at DESC
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
        Some("EXPIRED") => extra.push(format!("cur.status = 'GRACE' AND (CURRENT_DATE - cur.end_date) > {grace_days}")),
        Some("PENDING") => extra.push("cur.status IS NOT NULL AND cur.status != 'GRACE' AND COALESCE(ps.pending_amount, 0) > 0".into()),
        Some("PAID") => extra.push("cur.status IS NOT NULL AND cur.status != 'GRACE' AND COALESCE(ps.pending_amount, 0) <= 0".into()),
        _ => {}
    }

    let filter = if extra.is_empty() { String::new() } else { format!("AND {}", extra.join(" AND ")) };

    if let Some(ref pat) = pattern {
        let sql = format!(
            "{STUDENT_SELECT} WHERE u.role = 'STUDENT' {filter}
             AND (u.name ILIKE $3 OR u.mobile ILIKE $3 OR u.email ILIKE $3)
             ORDER BY {order_col} {order_dir} NULLS LAST LIMIT $1 OFFSET $2"
        );
        let mut users = sqlx::query_as::<_, StudentListItem>(&sql)
            .bind(size).bind(offset).bind(pat)
            .fetch_all(&state.db).await?;

        let count_sql = format!(
            "{STUDENT_COUNT_FROM} WHERE u.role = 'STUDENT' {filter}
             AND (u.name ILIKE $1 OR u.mobile ILIKE $1 OR u.email ILIKE $1)"
        );
        let total: i64 = sqlx::query_scalar(&count_sql)
            .bind(pat).fetch_one(&state.db).await?;

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
               SELECT id AS membership_id, seat_number, end_date FROM memberships
               WHERE user_id = u.id
               ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, created_at DESC
               LIMIT 1
           ) m ON true
           WHERE p.pending_amount > 0 AND p.status = 'SUCCESS'
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
) -> crate::error::Result<()> {
    sqlx::query(
        "UPDATE payments SET pending_amount = 0, updated_at = NOW() WHERE user_id = $1",
    )
    .bind(user_id)
    .execute(&state.db)
    .await?;
    Ok(())
}

pub async fn import_student(
    state: &Arc<AppState>,
    req: &ImportStudentRequest,
) -> crate::error::Result<User> {
    sqlx::query_as::<_, User>(
        "INSERT INTO users (name, mobile, email, address, gender, date_of_birth, role, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, 'STUDENT', NOW()) RETURNING *",
    )
    .bind(&req.name)
    .bind(&req.mobile)
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

pub async fn bulk_import_students(
    state: &Arc<AppState>,
    data: &[u8],
) -> crate::error::Result<ImportResult> {
    let mut reader = csv::ReaderBuilder::new()
        .has_headers(true)
        .flexible(true)
        .trim(csv::Trim::All)
        .from_reader(data);

    let headers = reader.headers().map_err(|e| AppError::BadRequest(e.to_string()))?.clone();
    let col = |name: &str| -> Option<usize> {
        headers.iter().position(|h| h.to_lowercase() == name)
    };
    let name_col   = col("name");
    let phone_col  = col("phone").or_else(|| col("mobile"));
    let email_col  = col("email");
    let gender_col = col("gender");

    let mut imported = 0i32;
    let mut skipped  = 0i32;
    let mut total    = 0i32;

    for record in reader.records() {
        let record = match record {
            Ok(r) => r,
            Err(_) => { skipped += 1; total += 1; continue }
        };
        total += 1;

        let name = name_col.and_then(|i| record.get(i)).unwrap_or("").trim().to_string();
        if name.is_empty() { skipped += 1; continue; }

        let mobile = phone_col.and_then(|i| record.get(i)).map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
        let email  = email_col.and_then(|i| record.get(i)).map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
        let gender = gender_col.and_then(|i| record.get(i)).map(|s| s.trim().to_string()).filter(|s| !s.is_empty());

        let result = sqlx::query(
            "INSERT INTO users (name, mobile, email, gender, role, created_at)
             VALUES ($1, $2, $3, $4, 'STUDENT', NOW())
             ON CONFLICT DO NOTHING",
        )
        .bind(&name)
        .bind(&mobile)
        .bind(&email)
        .bind(&gender)
        .execute(&state.db)
        .await;

        match result {
            Ok(r) if r.rows_affected() > 0 => imported += 1,
            _ => skipped += 1,
        }
    }

    Ok(ImportResult { imported, skipped, total_rows: total })
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

    let rows: Vec<(Uuid, String, Option<String>, Option<String>, NaiveDate, bool)> = if let Some(ids) = &user_ids {
        sqlx::query_as(
            "SELECT m.id, u.name, u.mobile, u.email, m.end_date, m.reminder_sent
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'ACTIVE' AND m.user_id = ANY($1::uuid[])",
        )
        .bind(ids)
        .fetch_all(&state.db)
        .await?
    } else {
        sqlx::query_as(
            "SELECT m.id, u.name, u.mobile, u.email, m.end_date, m.reminder_sent
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'ACTIVE'
               AND m.end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
               AND m.reminder_sent = false",
        )
        .fetch_all(&state.db)
        .await?
    };

    let mut count = 0i64;
    let today = chrono::Local::now().date_naive();

    for (mid, name, mobile, email, end_date, _) in &rows {
        let days_left = (*end_date - today).num_days();
        if user_ids.is_some() || days_left == 7 || days_left == 3 {
            let s = state.clone();
            let n = name.clone();
            let m = mobile.clone();
            let e = email.clone();
            let ed = *end_date;
            let dl = days_left;
            tokio::spawn(async move {
                notification::send_renewal_reminder(&s, &n, m.as_deref(), e.as_deref(), dl, ed).await;
            });

            if user_ids.is_none() {
                sqlx::query("UPDATE memberships SET reminder_sent = true WHERE id = $1")
                    .bind(mid)
                    .execute(&state.db)
                    .await
                    .ok();
            }
            count += 1;
        }
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

    let rows: Vec<(String, Option<String>, Option<String>, Decimal)> = if let Some(ref ids) = ids_filter {
        sqlx::query_as(
            "SELECT u.name, u.mobile, u.email, COALESCE(m.dues_amount, 0)
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'GRACE' AND u.id = ANY($1)",
        )
        .bind(ids)
        .fetch_all(&state.db)
        .await?
    } else {
        sqlx::query_as(
            "SELECT u.name, u.mobile, u.email, COALESCE(m.dues_amount, 0)
             FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'GRACE'",
        )
        .fetch_all(&state.db)
        .await?
    };

    let count = rows.len() as i64;
    let setting = settings::setting_for(state, "GRACE_DUES_REMINDER").await;
    for (name, mobile, email, dues) in &rows {
        let msg = settings::apply_hindi(
            &format!(
                "Grace Period Reminder - Hi {name}, your library membership is in its grace period with Rs.{dues:.0} \
in outstanding dues. Please clear your dues soon to avoid losing your seat. - Target Zone Library Team"
            ),
            &setting, true,
        );
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

    let rows: Vec<(String, Option<String>, Option<String>, Option<Decimal>)> = if let Some(ref ids) = ids {
        sqlx::query_as(
            "SELECT u.name, u.mobile, u.email, SUM(p.pending_amount)
             FROM users u JOIN payments p ON p.user_id = u.id
             WHERE p.pending_amount > 0 AND p.status = 'SUCCESS'
               AND u.id = ANY($1)
             GROUP BY u.name, u.mobile, u.email",
        )
        .bind(ids)
        .fetch_all(&state.db)
        .await?
    } else {
        sqlx::query_as(
            "SELECT u.name, u.mobile, u.email, SUM(p.pending_amount)
             FROM users u JOIN payments p ON p.user_id = u.id
             WHERE p.pending_amount > 0 AND p.status = 'SUCCESS'
             GROUP BY u.name, u.mobile, u.email",
        )
        .fetch_all(&state.db)
        .await?
    };

    let count = rows.len() as i64;
    for (name, mobile, email, pending) in &rows {
        let amount = pending.unwrap_or_default();
        let msg = format!(
            "Pending Fee Reminder - Hi {name}, you have a pending library fee of Rs.{amount:.0}. \
Please visit the library or contact us to clear your dues. - Target Zone Library Team"
        );
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
    let users: Vec<(Option<String>, Option<String>)> = sqlx::query_as(
        "SELECT u.mobile, u.email FROM users u
         JOIN memberships m ON m.user_id = u.id
         WHERE m.status = 'ACTIVE' AND u.is_active = true",
    )
    .fetch_all(&state.db)
    .await?;

    let recipient_count = users.iter().filter(|(m, _)| m.is_some()).count() as i32;

    let bcast = sqlx::query_as::<_, BroadcastMessage>(
        "INSERT INTO broadcast_messages (id, message, recipient_count) VALUES (gen_random_uuid(), $1, $2) RETURNING *",
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
        "SELECT * FROM broadcast_messages ORDER BY sent_at DESC",
    )
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

// ── Cash membership ───────────────────────────────────────────────────────────

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
         VALUES ($1, $2, $3, $4, 'CASH', $5, 'SUCCESS')",
    )
    .bind(membership.id)
    .bind(req.user_id)
    .bind(req.amount)
    .bind(req.pending_amount)
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
            payment_method: "CASH".into(),
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

pub async fn update_membership_plan(
    state: &Arc<AppState>,
    membership_id: Uuid,
    req: &UpdatePlanRequest,
) -> crate::error::Result<Membership> {
    if let Some(new_plan_id) = req.plan_id {
        sqlx::query("UPDATE memberships SET plan_id = $2 WHERE id = $1")
            .bind(membership_id)
            .bind(new_plan_id)
            .execute(&state.db)
            .await?;
    }
    if let Some(extra_days) = req.additional_days {
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

    sqlx::query_as::<_, Membership>("SELECT * FROM memberships WHERE id = $1")
        .bind(membership_id)
        .fetch_one(&state.db)
        .await
        .map_err(AppError::Database)
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
        // Bounded invalidation only — a GRACE booking's end_date may be the
        // far-future sentinel, which would hang the day-by-day cache-busting
        // loop, so cap it at "today" instead of trusting the stored value.
        let today = chrono::Local::now().date_naive();
        let capped_end = b.end_date.min(today);
        crate::services::seat::invalidate_seat_cache(state, &b.shift, b.booking_date.min(capped_end), capped_end).await;

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
    let far_future = NaiveDate::from_ymd_opt(9999, 12, 31).expect("valid sentinel date");

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
        // Bounded invalidation — never the sentinel as an upper bound.
        crate::services::seat::invalidate_seat_cache(state, shift, today, today).await;
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

    let remainder = dues - amount_cleared;
    let new_end = membership.end_date.checked_add_months(chrono::Months::new(1))
        .ok_or_else(|| AppError::Internal("Date overflow computing dues clearance".into()))?;

    let invoice_id = ids::generate_invoice_id();
    sqlx::query(
        "INSERT INTO payments (membership_id, user_id, amount, pending_amount, payment_gateway, invoice_id, status)
         VALUES ($1, $2, $3, $4, 'CASH', $5, 'SUCCESS')",
    )
    .bind(membership_id)
    .bind(membership.user_id)
    .bind(amount_cleared)
    .bind(remainder)
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
        payment_method: "CASH".into(),
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

pub async fn update_feedback(
    state: &Arc<AppState>,
    feedback_id: Uuid,
    req: &UpdateFeedbackRequest,
) -> crate::error::Result<crate::models::user::Feedback> {
    sqlx::query_as::<_, crate::models::user::Feedback>(
        "UPDATE feedbacks SET
            status     = COALESCE($2, status),
            admin_notes = COALESCE($3, admin_notes),
            updated_at = NOW()
         WHERE id = $1 RETURNING *",
    )
    .bind(feedback_id)
    .bind(&req.status)
    .bind(&req.admin_notes)
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)
}

// ── Revenue ───────────────────────────────────────────────────────────────────

pub async fn get_revenue(
    state: &Arc<AppState>,
    from: Option<NaiveDate>,
    to: Option<NaiveDate>,
) -> crate::error::Result<RevenueReport> {
    let from = from.unwrap_or_else(|| chrono::Local::now().date_naive() - chrono::Duration::days(30));
    let to = to.unwrap_or_else(|| chrono::Local::now().date_naive());

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
    from: Option<NaiveDate>,
    to: Option<NaiveDate>,
) -> crate::error::Result<Vec<PaymentBreakdownItem>> {
    let from = from.unwrap_or_else(|| chrono::Local::now().date_naive() - chrono::Duration::days(30));
    let to = to.unwrap_or_else(|| chrono::Local::now().date_naive());

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

    let misc_items = sqlx::query_as::<_, MiscExpenseItem>(
        "SELECT * FROM misc_expense_items WHERE monthly_expense_id = $1 ORDER BY sort_order",
    )
    .bind(expense.id)
    .fetch_all(&state.db)
    .await?;

    let total = expense.electricity_bill
        + expense.internet_bill
        + expense.water_tanker_price
        + expense.miscellaneous
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
                "INSERT INTO misc_expense_items (monthly_expense_id, description, amount, sort_order)
                 VALUES ($1, $2, $3, $4) RETURNING *",
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
        + expense.water_tanker_price
        + expense.miscellaneous
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

        sqlx::query("UPDATE seat_bookings SET end_date = $2 WHERE membership_id = $1 AND status = 'ACTIVE'")
            .bind(mem_id)
            .bind(far_future)
            .execute(&state.db)
            .await?;

        if let Some(shift) = shift {
            crate::services::seat::invalidate_seat_cache(state, shift, today, today).await;
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
