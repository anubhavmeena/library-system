use crate::{
    app_state::AppState,
    handlers::{admin, auth, gallery, mailbox, membership, payment, payment_claim, seat, user, visitor, webhook},
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
        .route("/api/memberships/my/status",      get(membership::get_my_status))
        .route("/api/memberships/my/call-admin",  post(membership::call_admin))
        .route("/api/memberships/my/id-card",     get(membership::download_id_card))

        // ── Payments ──────────────────────────────────────────────────────────
        .route("/api/payments/my",                 get(payment::get_payment_history))
        .route("/api/payments/create-order",       post(payment::create_order))
        .route("/api/payments/verify",             post(payment::verify_payment))
        .route("/api/payments/dues/create-order",  post(payment::create_dues_order))
        .route("/api/payments/dues/verify",        post(payment::verify_dues_payment))
        .route("/api/payments/pending/create-order", post(payment::create_pending_order))
        .route("/api/payments/pending/verify",       post(payment::verify_pending_payment))
        .route("/api/payments/coupons/active",       get(payment::list_active_coupons))
        .route("/api/payments/validate-coupon",      post(payment::validate_coupon))
        .route("/api/payments/claims",               post(payment_claim::submit_claim)
            .layer(DefaultBodyLimit::max(10 * 1024 * 1024)))
        .route("/api/pay/:id",                        get(payment_claim::get_pay_link))

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
        .route("/api/admin/students/grace-dues",             get(admin::grace_dues_students))
        .route("/api/admin/students/orphaned-seats",         get(admin::orphaned_seats))
        .route("/api/admin/students/import",                  post(admin::bulk_import))
        .route("/api/admin/students/import/single",          post(admin::import_student))
        .route("/api/admin/students/import/single-with-photo", post(admin::import_student_with_photo)
            .layer(DefaultBodyLimit::max(10 * 1024 * 1024)))
        .route("/api/admin/students/:id",                    get(admin::get_student).patch(admin::update_student).delete(admin::delete_student))
        .route("/api/admin/students/:id/photo",              post(admin::upload_student_photo)
            .layer(DefaultBodyLimit::max(10 * 1024 * 1024)))
        .route("/api/admin/students/:id/status",             patch(admin::update_student_status))
        .route("/api/admin/students/:id/payments",           get(admin::get_student_payments))
        .route("/api/admin/students/:id/seat-history",       get(admin::student_seat_history))
        .route("/api/admin/students/:id/clear-pending-fees", patch(admin::clear_pending_fees))
        .route("/api/admin/students/:id/message",            post(admin::send_direct_message))
        .route("/api/admin/students/:id/send-receipt",       post(admin::send_receipt))
        .route("/api/admin/students/:id/send-id-card",       post(admin::send_id_card))
        .route("/api/admin/seats/map",                       get(admin::seat_map))
        .route("/api/admin/seats/:seatNumber/history",       get(admin::seat_history))
        .route("/api/admin/memberships/expiring",            get(admin::expiring_memberships))
        .route("/api/admin/memberships/cash",                post(admin::create_cash_membership))
        .route("/api/admin/memberships/run-expiry-check",    post(admin::run_expiry_check))
        .route("/api/admin/memberships/:id/seat",            patch(admin::change_membership_seat))
        .route("/api/admin/memberships/:id/swap-seat",       post(admin::swap_membership_seat))
        .route("/api/admin/memberships/:id/plan",            patch(admin::update_membership_plan))
        .route("/api/admin/memberships/:id/release",         patch(admin::release_seat))
        .route("/api/admin/memberships/:id/renew",           patch(admin::renew_seat))
        .route("/api/admin/memberships/:id/mark-pending",    patch(admin::mark_membership_pending))
        .route("/api/admin/memberships/:id/mark-grace",      patch(admin::mark_membership_grace))
        .route("/api/admin/memberships/:id/clear-dues",      patch(admin::clear_dues))
        .route("/api/admin/reminders/send",                  post(admin::send_reminders))
        .route("/api/admin/reminders/pending-fees",          post(admin::send_pending_fee_reminders))
        .route("/api/admin/reminders/grace-dues",            post(admin::send_grace_dues_reminders))
        .route("/api/admin/broadcast",                       post(admin::broadcast))
        .route("/api/admin/broadcast/history",               get(admin::broadcast_history))
        .route("/api/admin/broadcast/:id",                   delete(admin::delete_broadcast))
        .route("/api/admin/feedback",                        get(admin::list_feedback))
        .route("/api/admin/feedback/:id",                    patch(admin::update_feedback))
        .route("/api/admin/reports/revenue",                 get(admin::revenue_report))
        .route("/api/admin/reports/payments/breakdown",      get(admin::payment_breakdown))
        .route("/api/admin/reports/payments/daily",          get(admin::daily_payments))
        .route("/api/admin/expenses",                        get(admin::get_expenses).post(admin::save_expense))
        .route("/api/admin/settings",                        get(admin::get_app_settings).post(admin::save_app_settings))
        .route("/api/admin/notification-settings",           get(admin::get_notification_settings))
        .route("/api/admin/notification-settings/:key",      patch(admin::update_notification_setting))
        .route("/api/admin/coupons",                         get(admin::list_coupons).post(admin::create_coupon))
        .route("/api/admin/coupons/:id",                     patch(admin::update_coupon).delete(admin::delete_coupon))
        .route("/api/admin/activity-logs",                   get(admin::list_activity_logs))
        .route("/api/admin/renewal-polls",                   get(admin::list_renewal_polls))
        .route("/api/admin/renewal-polls/:id/resend",        post(admin::resend_renewal_poll))
        .route("/api/admin/payment-claims",                  get(payment_claim::list_claims))
        .route("/api/admin/payment-claims/:id",               patch(payment_claim::review_claim))
        .route("/api/admin/pay-links",                       post(payment_claim::create_pay_link))

        // ── Admin mailbox (IMAP) ──────────────────────────────────────────────
        .route("/api/admin/inbox",                           get(mailbox::list_messages))
        .route("/api/admin/inbox/:messageNumber",             get(mailbox::get_message).delete(mailbox::delete_message))
        .route("/api/admin/inbox/:messageNumber/reply",       post(mailbox::reply))
        .route("/api/admin/inbox/:messageNumber/attachments/:index", get(mailbox::get_attachment))

        // ── Visitor tracking (public) ─────────────────────────────────────────
        .route("/api/visitor/track", post(visitor::track))

        // ── WhatsApp webhook (public — Meta calls this directly, no JWT) ───────
        .route("/api/whatsapp/webhook", get(webhook::verify_webhook).post(webhook::receive_webhook))

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
