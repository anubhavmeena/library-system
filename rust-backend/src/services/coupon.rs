use crate::{
    app_state::AppState,
    error::AppError,
    models::coupon::{Coupon, CreateCouponRequest, UpdateCouponRequest},
    services::settings,
};
use rand::Rng;
use std::sync::Arc;
use uuid::Uuid;

// Excludes visually-ambiguous characters (0/O, 1/I/L) so a printed/spoken
// code is unambiguous.
const CODE_CHARS: &[u8] = b"ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LEN: usize = 8;
const MAX_GENERATE_ATTEMPTS: usize = 10;

fn random_code() -> String {
    let mut rng = rand::thread_rng();
    (0..CODE_LEN)
        .map(|_| CODE_CHARS[rng.gen_range(0..CODE_CHARS.len())] as char)
        .collect()
}

fn validate_discount_percent(pct: i16) -> crate::error::Result<()> {
    if !(1..=100).contains(&pct) {
        return Err(AppError::BadRequest("discountPercent must be between 1 and 100".into()));
    }
    Ok(())
}

pub async fn list_coupons(state: &Arc<AppState>) -> crate::error::Result<Vec<Coupon>> {
    sqlx::query_as::<_, Coupon>("SELECT * FROM coupons ORDER BY created_at DESC")
        .fetch_all(&state.db)
        .await
        .map_err(AppError::Database)
}

/// Student-facing list — only coupons that are individually active AND the
/// global kill switch is on. Empty whenever `coupons_enabled = false`, which
/// is what keeps "no coupons ever created" behaving exactly as before.
pub async fn list_active_coupons(state: &Arc<AppState>) -> crate::error::Result<Vec<Coupon>> {
    let enabled = settings::get_app_settings(state).await?.coupons_enabled;
    if !enabled {
        return Ok(vec![]);
    }
    sqlx::query_as::<_, Coupon>("SELECT * FROM coupons WHERE is_active = true ORDER BY created_at DESC")
        .fetch_all(&state.db)
        .await
        .map_err(AppError::Database)
}

pub async fn create_coupon(
    state: &Arc<AppState>,
    req: &CreateCouponRequest,
) -> crate::error::Result<Coupon> {
    validate_discount_percent(req.discount_percent)?;

    if let Some(code) = req.code.as_deref() {
        let code = code.trim().to_uppercase();
        if code.is_empty() {
            return Err(AppError::BadRequest("code cannot be blank".into()));
        }
        return sqlx::query_as::<_, Coupon>(
            "INSERT INTO coupons (code, discount_percent) VALUES ($1, $2)
             ON CONFLICT (code) DO NOTHING RETURNING *",
        )
        .bind(&code)
        .bind(req.discount_percent)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::Conflict(format!("Coupon code {code} already exists")));
    }

    // Auto-generate: ON CONFLICT DO NOTHING + retry is atomic per attempt —
    // safe under concurrent admin clicks, unlike a check-then-insert.
    for _ in 0..MAX_GENERATE_ATTEMPTS {
        let code = random_code();
        if let Some(coupon) = sqlx::query_as::<_, Coupon>(
            "INSERT INTO coupons (code, discount_percent) VALUES ($1, $2)
             ON CONFLICT (code) DO NOTHING RETURNING *",
        )
        .bind(&code)
        .bind(req.discount_percent)
        .fetch_optional(&state.db)
        .await?
        {
            return Ok(coupon);
        }
    }
    Err(AppError::Internal("Failed to generate a unique coupon code".into()))
}

pub async fn update_coupon(
    state: &Arc<AppState>,
    id: Uuid,
    req: &UpdateCouponRequest,
) -> crate::error::Result<Coupon> {
    if let Some(pct) = req.discount_percent {
        validate_discount_percent(pct)?;
    }
    let existing = sqlx::query_as::<_, Coupon>("SELECT * FROM coupons WHERE id = $1")
        .bind(id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::NotFound("Coupon not found".into()))?;

    let discount_percent = req.discount_percent.unwrap_or(existing.discount_percent);
    let is_active = req.is_active.unwrap_or(existing.is_active);

    sqlx::query_as::<_, Coupon>(
        "UPDATE coupons SET discount_percent = $2, is_active = $3 WHERE id = $1 RETURNING *",
    )
    .bind(id)
    .bind(discount_percent)
    .bind(is_active)
    .fetch_one(&state.db)
    .await
    .map_err(AppError::Database)
}

pub async fn delete_coupon(state: &Arc<AppState>, id: Uuid) -> crate::error::Result<()> {
    let result = sqlx::query("DELETE FROM coupons WHERE id = $1")
        .bind(id)
        .execute(&state.db)
        .await?;
    if result.rows_affected() == 0 {
        return Err(AppError::NotFound("Coupon not found".into()));
    }
    Ok(())
}

/// Validates a code for student use at checkout — must be individually active
/// and the global toggle must be on. Returns the coupon (for its discount
/// percent) or a `BadRequest` explaining why it can't be applied.
pub async fn validate_coupon_code(state: &Arc<AppState>, code: &str) -> crate::error::Result<Coupon> {
    let enabled = settings::get_app_settings(state).await?.coupons_enabled;
    if !enabled {
        return Err(AppError::BadRequest("Coupons are not currently available".into()));
    }
    let code = code.trim().to_uppercase();
    sqlx::query_as::<_, Coupon>("SELECT * FROM coupons WHERE code = $1 AND is_active = true")
        .bind(&code)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::BadRequest("Invalid or inactive coupon code".into()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn random_code_has_expected_length_and_charset() {
        let code = random_code();
        assert_eq!(code.len(), CODE_LEN);
        assert!(code.chars().all(|c| CODE_CHARS.contains(&(c as u8))));
    }

    #[test]
    fn validate_discount_percent_rejects_out_of_range() {
        assert!(validate_discount_percent(0).is_err());
        assert!(validate_discount_percent(101).is_err());
        assert!(validate_discount_percent(1).is_ok());
        assert!(validate_discount_percent(100).is_ok());
    }
}
