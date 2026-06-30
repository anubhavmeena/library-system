use crate::{
    app_state::AppState,
    handlers::{admin, auth, gallery, membership, payment, seat, user, visitor},
};
use axum::{
    extract::DefaultBodyLimit,
    routing::{delete, get, patch, post},
    Router,
};
use std::sync::Arc;
use tower_http::{cors::CorsLayer, services::ServeDir, trace::TraceLayer};

pub fn build_router(state: Arc<AppState>) -> Router {
    let upload_dir = state.config.upload_dir.clone();

    Router::new()
        // ── Auth ──────────────────────────────────────────────────────────────
        .route("/api/auth/send-otp",   post(auth::send_otp))
        .route("/api/auth/verify-otp", post(auth::verify_otp))
        .route("/api/auth/register",   post(auth::register))
        .route("/api/auth/login",      post(auth::login))
        .route("/api/auth/refresh",    post(auth::refresh_token))
        .route("/api/auth/admin/login", post(auth::admin_login))

        // ── Plans (public) ────────────────────────────────────────────────────
        .route("/api/plans", get(membership::list_plans))

        // ── Users ─────────────────────────────────────────────────────────────
        .route("/api/users/admin-contact",   get(user::get_admin_contact))
        .route("/api/users/me",              get(user::get_me).patch(user::update_me))
        .route("/api/users/me/photo",        post(user::upload_photo).delete(user::delete_photo)
            .layer(DefaultBodyLimit::max(10 * 1024 * 1024)))
        .route("/api/users/me/aadhaar",      post(user::upload_aadhaar).delete(user::delete_aadhaar)
            .layer(DefaultBodyLimit::max(10 * 1024 * 1024)))
        .route("/api/users/:id",             get(user::get_user))
        .route("/api/users/feedback",        post(user::submit_feedback))
        .route("/api/users/feedback/my",     get(user::get_my_feedback))

        // ── Gallery ───────────────────────────────────────────────────────────
        .route("/api/gallery",    get(gallery::list_gallery).post(gallery::upload_gallery_photo)
            .layer(DefaultBodyLimit::max(10 * 1024 * 1024)))
        .route("/api/gallery/:id", delete(gallery::delete_gallery_photo))

        // ── Memberships ───────────────────────────────────────────────────────
        .route("/api/memberships/my",            get(membership::get_my_membership))
        .route("/api/memberships/my/all",         get(membership::get_my_all_memberships))
        .route("/api/memberships/my/queued",      get(membership::get_my_queued_membership))
        .route("/api/memberships/my/call-admin",  post(membership::call_admin))
        .route("/api/memberships/my/id-card",     get(membership::download_id_card))

        // ── Payments ──────────────────────────────────────────────────────────
        .route("/api/payments/my",           get(payment::get_payment_history))
        .route("/api/payments/create-order", post(payment::create_order))
        .route("/api/payments/verify",       post(payment::verify_payment))

        // ── Seats ─────────────────────────────────────────────────────────────
        .route("/api/seats/availability",       get(seat::get_availability))
        .route("/api/seats/book",               post(seat::book_seat))
        .route("/api/seats/my",                 get(seat::get_my_bookings))
        .route("/api/seats/release/:id",        delete(seat::release_booking))
        .route("/api/seats/admin/bookings",     get(seat::get_admin_bookings))

        // ── Admin ─────────────────────────────────────────────────────────────
        .route("/api/admin/dashboard",                       get(admin::dashboard))
        .route("/api/admin/students",                        get(admin::list_students))
        .route("/api/admin/students/pending-fees",           get(admin::get_pending_fees))
        .route("/api/admin/students/import",                  post(admin::bulk_import))
        .route("/api/admin/students/import/single",          post(admin::import_student))
        .route("/api/admin/students/:id",                    get(admin::get_student).patch(admin::update_student).delete(admin::delete_student))
        .route("/api/admin/students/:id/status",             patch(admin::update_student_status))
        .route("/api/admin/students/:id/payments",           get(admin::get_student_payments))
        .route("/api/admin/students/:id/clear-pending-fees", patch(admin::clear_pending_fees))
        .route("/api/admin/students/:id/message",            post(admin::send_direct_message))
        .route("/api/admin/seats/map",                       get(admin::seat_map))
        .route("/api/admin/memberships/expiring",            get(admin::expiring_memberships))
        .route("/api/admin/memberships/cash",                post(admin::create_cash_membership))
        .route("/api/admin/memberships/:id/seat",            patch(admin::change_membership_seat))
        .route("/api/admin/memberships/:id/plan",            patch(admin::update_membership_plan))
        .route("/api/admin/reminders/send",                  post(admin::send_reminders))
        .route("/api/admin/reminders/pending-fees",          post(admin::send_pending_fee_reminders))
        .route("/api/admin/broadcast",                       post(admin::broadcast))
        .route("/api/admin/broadcast/history",               get(admin::broadcast_history))
        .route("/api/admin/feedback",                        get(admin::list_feedback))
        .route("/api/admin/feedback/:id",                    patch(admin::update_feedback))
        .route("/api/admin/reports/revenue",                 get(admin::revenue_report))
        .route("/api/admin/reports/payments/breakdown",      get(admin::payment_breakdown))
        .route("/api/admin/reports/payments/daily",          get(admin::daily_payments))
        .route("/api/admin/expenses",                        get(admin::get_expenses).post(admin::save_expense))

        // ── Visitor tracking (public) ─────────────────────────────────────────
        .route("/api/visitor/track", post(visitor::track))

        // ── Static file serving for uploads ──────────────────────────────────
        .nest_service("/uploads", ServeDir::new(&upload_dir))

        .with_state(state)
        .layer(TraceLayer::new_for_http())
        .layer(
            CorsLayer::new()
                .allow_origin(tower_http::cors::Any)
                .allow_methods(tower_http::cors::Any)
                .allow_headers(tower_http::cors::Any),
        )
}
