//! Shared harness for integration tests (`#[ignore]`d, DB/Redis-backed).
//!
//! `cargo test` alone stays DB/Redis-free (the existing convention — see
//! CLAUDE.md): every test in this crate that touches Postgres/Redis lives
//! behind `#[ignore]` and is opted into explicitly via
//! `cargo test -- --ignored --test-threads=1`. The `--test-threads=1` part
//! matters: these tests share one persistent local dev database rather than
//! an isolated per-test schema, so parallel runs can collide on unique seat
//! bookings/dates.
//!
//! Deliberately never calls `Config::from_env()` — that would read the
//! repo's real `.env`, which carries live Meta WhatsApp/SendGrid/Cashfree/
//! Razorpay credentials, and firing real notifications or hitting a real
//! payment gateway from a test run would be a serious side effect. Instead
//! this builds on `config::test_config()` (all external credentials already
//! blank there) and only overrides `database_url`/`redis_url` to point at
//! the local dev instance, so every test runs in the same guaranteed-dev-mode
//! posture as the rest of the suite (OTP always "123456", payments always
//! `dev_order_*`, notifications logged not sent).
//!
//! If a real `cargo run` instance happens to be running against this same
//! local DB while these tests execute, its live daily cron jobs (expiry
//! reminders at 9:00 UTC, the grace/expiry sweep at 5:00 IST) can mutate
//! shared membership/seat rows mid-test-run and cause a one-off, otherwise
//! unreproducible failure. That's an environmental collision, not a bug in
//! either the tests or the cron jobs -- if a test fails once and then passes
//! cleanly on retry with no code changes, this is the first thing to suspect.
//!
//! Every user these tests create carries a `69xxxxxxxx` mobile number (see
//! `unique_mobile`), and nothing here ever deletes what it creates -- there's
//! no isolated per-test schema/transaction to roll back, just this one
//! persistent shared dev DB (same tradeoff CLAUDE.md already documents for
//! the ad-hoc manual/Python testing this suite followed). Running the full
//! `--ignored` suite repeatedly therefore keeps consuming seat/date capacity
//! across the 112 real seats and will *eventually* make seat-needing tests
//! fail with "no seat free for this shift" rather than a real assertion
//! failure. When that happens, run `reset_all_integration_test_data` (below)
//! to reclaim every `69%`-mobile row this suite ever created, then retry.
#![cfg(test)]

use crate::{app_state::AppState, config::Config, services::jwt};
use rand::Rng;
use std::sync::Arc;
use uuid::Uuid;

pub fn integration_test_config() -> Config {
    let mut c = crate::config::test_config();
    c.database_url = std::env::var("TEST_DATABASE_URL")
        .unwrap_or_else(|_| "postgres://library_user:library_pass@localhost:5432/library_db".to_string());
    c.redis_url = std::env::var("TEST_REDIS_URL").unwrap_or_else(|_| "redis://localhost:6379".to_string());
    c.upload_dir = std::env::var("TEST_UPLOAD_DIR").unwrap_or_else(|_| "/tmp/library_backend_test_uploads".to_string());
    // A handful of tests exercise the real admin-login flow, which requires
    // the contact to be in the allowlist *or* already ADMIN in the DB. The
    // shared dev DB already seeds a real admin at this mobile (see
    // common-lib/db/seed.sql), so reuse it rather than requiring tests to
    // create their own admin user row.
    c.admin_phones = vec!["9071356842".to_string()];
    c
}

/// Builds a real AppState against the local dev Postgres/Redis, with all
/// external credentials guaranteed blank (dev mode) regardless of the
/// repo's actual `.env` contents.
pub async fn test_state() -> Arc<AppState> {
    let config = integration_test_config();
    let db = sqlx::postgres::PgPoolOptions::new()
        .max_connections(5)
        .connect(&config.database_url)
        .await
        .expect("connect to local test Postgres (see test_support::integration_test_config)");
    let redis = redis::Client::open(config.redis_url.as_str()).expect("redis client");
    tokio::fs::create_dir_all(&config.upload_dir).await.ok();
    Arc::new(AppState::new(db, redis, config))
}

pub fn test_router(state: Arc<AppState>) -> axum::Router {
    crate::routes::build_router(state)
}

/// Random 10-digit mobile number in a dedicated `69xxxxxxxx` range that real
/// seed/manual-test data never uses, so parallel/serial test runs don't
/// collide with each other or with ad-hoc manual testing on `70xxxxxxxx`/etc.
pub fn unique_mobile() -> String {
    let n: u64 = rand::thread_rng().gen_range(0..100_000_000);
    format!("69{n:08}")
}

pub fn unique_email() -> String {
    format!("test.{}@example.com", Uuid::new_v4().simple())
}

/// Inserts a real `users` row directly (skipping the OTP/register roundtrip)
/// and returns (user_id, a ready-to-use Bearer JWT). Handlers that look the
/// user up by id (almost all of them) need a real row to join against.
pub async fn create_test_user(state: &AppState, role: &str, name: &str) -> (Uuid, String) {
    let mobile = unique_mobile();
    // `created_at`/`updated_at` have no DB-side default in the deployed schema
    // (unlike the aspirational `DEFAULT NOW()` in migrations/001_initial.sql) --
    // omitting them here decodes as NULL into the non-Option `NaiveDateTime`
    // field on `models::user::User` and blows up every handler that reads the
    // row back with a generic "Database error" 500.
    let id: Uuid = sqlx::query_scalar(
        "INSERT INTO users (mobile, name, role, created_at, updated_at) VALUES ($1, $2, $3, NOW(), NOW()) RETURNING id",
    )
    .bind(&mobile)
    .bind(name)
    .bind(role)
    .fetch_one(&state.db)
    .await
    .expect("insert test user");

    let token = jwt::create_token(id, role, name, None, Some(&mobile), &state.config.jwt_secret, state.config.jwt_expiry_ms)
        .expect("mint test token");
    (id, token)
}

/// The real 10s resend cooldown (`otp:cooldown:<contact>`, see services/otp.rs)
/// makes a second `send-otp` for the same contact within one test fail unless
/// cleared first -- tests that need more than one OTP cycle for the same
/// contact (e.g. register then log back in) call this between cycles instead
/// of sleeping out the cooldown.
pub async fn clear_otp_cooldown(state: &AppState, contact: &str) {
    use redis::AsyncCommands;
    if let Ok(mut conn) = state.redis.get_multiplexed_async_connection().await {
        let _: Result<i64, _> = conn.del(format!("otp:cooldown:{contact}")).await;
    }
}

pub async fn admin_user_id(state: &AppState) -> Uuid {
    sqlx::query_scalar("SELECT id FROM users WHERE mobile = '9071356842'")
        .fetch_one(&state.db)
        .await
        .expect("seeded admin user (mobile 9071356842) must exist in the local dev DB")
}

pub async fn admin_token(state: &AppState) -> String {
    let id = admin_user_id(state).await;
    jwt::create_token(id, "ADMIN", "Admin", Some("admin@targetzone.co.in"), Some("9071356842"), &state.config.jwt_secret, state.config.jwt_expiry_ms)
        .expect("mint admin token")
}

/// A random seat number (A1..D28). No exclusivity guarantee -- this shared
/// dev DB has accumulated a lot of ad-hoc occupancy over many earlier manual
/// and automated test sessions, so a random pick routinely collides. Use
/// `free_seat_today` instead when a test actually needs an unoccupied seat;
/// reach for this one only when a test *wants* "some seat, don't care which,
/// possibly already taken" (e.g. to deliberately provoke a conflict).
pub fn unique_seat_number() -> String {
    let rows = ["A", "B", "C", "D"];
    let mut rng = rand::thread_rng();
    let row = rows[rng.gen_range(0..rows.len())];
    let idx = rng.gen_range(1..=28);
    format!("{row}{idx}")
}

/// Queries the DB for a seat genuinely free for `shift` across `today ..
/// today + 400 days` (covers every plan duration these tests ever book, the
/// longest being the 365-day annual plans), so tests that need a
/// guaranteed-available seat don't gamble against however much ad-hoc
/// occupancy already exists in this shared dev DB. Checking only a single
/// day here (rather than the *range* about to be booked) was an earlier bug
/// in this helper itself: a seat "free today" can still have a future
/// booking starting a few weeks out, which the real book_seat/
/// create_cash_membership conflict check (rightly) treats as a conflict as
/// soon as the new booking's range reaches that far. Panics if no seat is
/// free for the whole window.
pub async fn free_seat_today(state: &AppState, shift: &str) -> String {
    let today = chrono::Local::now().date_naive();
    free_seat_for_range(state, shift, today, today + chrono::Duration::days(400)).await
}

/// Same as `free_seat_today` but anchored at an arbitrary start date --
/// needed by tests that book historical/future dates (e.g. bulk CSV import
/// rows). Still checks a generous 400-day window from that start date.
pub async fn free_seat_on(state: &AppState, shift: &str, date: chrono::NaiveDate) -> String {
    free_seat_for_range(state, shift, date, date + chrono::Duration::days(400)).await
}

/// Core range-aware lookup, mirroring the exact overlap predicate
/// `book_seat`/`create_cash_membership` use (`booking_date <= range_end AND
/// end_date >= range_start`) rather than a single-day snapshot.
pub async fn free_seat_for_range(
    state: &AppState, shift: &str, range_start: chrono::NaiveDate, range_end: chrono::NaiveDate,
) -> String {
    sqlx::query_scalar(
        "SELECT s.seat_number FROM seats s
         WHERE s.is_active = true
           AND NOT EXISTS (
               SELECT 1 FROM seat_bookings sb
               WHERE sb.seat_id = s.id AND sb.status = 'ACTIVE'
                 AND sb.booking_date <= $2 AND sb.end_date >= $1
                 AND (sb.shift = $3 OR sb.shift = 'FULL_DAY' OR $3 = 'FULL_DAY')
           )
         ORDER BY random() LIMIT 1",
    )
    .bind(range_start)
    .bind(range_end)
    .bind(shift)
    .fetch_optional(&state.db)
    .await
    .expect("query for a free seat")
    .expect("at least one seat should be free for this shift today")
}

/// Request body helpers ------------------------------------------------------

pub async fn body_json(response: axum::response::Response) -> serde_json::Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("read response body");
    serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null)
}

pub fn json_request(method: &str, uri: &str, token: Option<&str>, body: serde_json::Value) -> axum::http::Request<axum::body::Body> {
    let mut builder = axum::http::Request::builder()
        .method(method)
        .uri(uri)
        .header("content-type", "application/json");
    if let Some(t) = token {
        builder = builder.header("authorization", format!("Bearer {t}"));
    }
    builder.body(axum::body::Body::from(body.to_string())).unwrap()
}

pub fn get_request(uri: &str, token: Option<&str>) -> axum::http::Request<axum::body::Body> {
    let mut builder = axum::http::Request::builder().method("GET").uri(uri);
    if let Some(t) = token {
        builder = builder.header("authorization", format!("Bearer {t}"));
    }
    builder.body(axum::body::Body::empty()).unwrap()
}

pub fn delete_request(uri: &str, token: Option<&str>) -> axum::http::Request<axum::body::Body> {
    let mut builder = axum::http::Request::builder().method("DELETE").uri(uri);
    if let Some(t) = token {
        builder = builder.header("authorization", format!("Bearer {t}"));
    }
    builder.body(axum::body::Body::empty()).unwrap()
}

/// One multipart text field, hand-rolled per RFC 2388 (axum has no built-in
/// test helper for building multipart *requests*, only for parsing them).
pub struct MultipartField {
    pub name: String,
    pub filename: Option<String>,
    pub content_type: Option<String>,
    pub data: Vec<u8>,
}

pub fn text_field(name: &str, value: &str) -> MultipartField {
    MultipartField { name: name.to_string(), filename: None, content_type: None, data: value.as_bytes().to_vec() }
}

pub fn file_field(name: &str, filename: &str, content_type: &str, data: Vec<u8>) -> MultipartField {
    MultipartField {
        name: name.to_string(),
        filename: Some(filename.to_string()),
        content_type: Some(content_type.to_string()),
        data,
    }
}

/// A minimal valid 1x1 PNG, useful for photo/gallery upload tests.
pub fn tiny_png_bytes() -> Vec<u8> {
    hex_decode(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489\
         0000000a49444154789c6300010000050001a5f645400000000049454e44ae426082",
    )
}

fn hex_decode(s: &str) -> Vec<u8> {
    let clean: String = s.chars().filter(|c| !c.is_whitespace()).collect();
    (0..clean.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&clean[i..i + 2], 16).unwrap())
        .collect()
}

pub fn multipart_request(
    method: &str,
    uri: &str,
    token: Option<&str>,
    fields: Vec<MultipartField>,
) -> axum::http::Request<axum::body::Body> {
    let boundary = format!("----libtestboundary{}", Uuid::new_v4().simple());
    let mut body = Vec::new();
    for f in &fields {
        body.extend_from_slice(format!("--{boundary}\r\n").as_bytes());
        match (&f.filename, &f.content_type) {
            (Some(filename), Some(ct)) => {
                body.extend_from_slice(
                    format!("Content-Disposition: form-data; name=\"{}\"; filename=\"{}\"\r\n", f.name, filename).as_bytes(),
                );
                body.extend_from_slice(format!("Content-Type: {ct}\r\n\r\n").as_bytes());
            }
            _ => {
                body.extend_from_slice(format!("Content-Disposition: form-data; name=\"{}\"\r\n\r\n", f.name).as_bytes());
            }
        }
        body.extend_from_slice(&f.data);
        body.extend_from_slice(b"\r\n");
    }
    body.extend_from_slice(format!("--{boundary}--\r\n").as_bytes());

    let mut builder = axum::http::Request::builder()
        .method(method)
        .uri(uri)
        .header("content-type", format!("multipart/form-data; boundary={boundary}"));
    if let Some(t) = token {
        builder = builder.header("authorization", format!("Bearer {t}"));
    }
    builder.body(axum::body::Body::from(body)).unwrap()
}

/// Deletes every row this integration-test suite has ever created (every
/// `users` row with a `69%` mobile, plus its dependent `seat_bookings` /
/// `payments` / `memberships` / `feedbacks` rows, in FK-safe order), freeing
/// back all seat/date capacity those tests consumed. Not run automatically --
/// invoke it directly (`cargo test test_support::reset_all_integration_test_data
/// -- --ignored --exact`) when repeated suite runs have exhausted enough
/// seats that `free_seat_for_range` starts failing.
#[tokio::test]
#[ignore]
async fn reset_all_integration_test_data() {
    let state = test_state().await;
    let mut deleted = 0i64;
    for table in ["seat_bookings", "payments", "feedbacks", "memberships"] {
        let n = sqlx::query(&format!(
            "DELETE FROM {table} WHERE user_id IN (SELECT id FROM users WHERE mobile LIKE '69%')"
        ))
        .execute(&state.db)
        .await
        .unwrap()
        .rows_affected();
        deleted += n as i64;
    }
    let users_deleted = sqlx::query("DELETE FROM users WHERE mobile LIKE '69%'")
        .execute(&state.db)
        .await
        .unwrap()
        .rows_affected();
    println!("reset_all_integration_test_data: removed {users_deleted} users and {deleted} dependent rows");
}
