use crate::app_state::AppState;
use crate::services::{ids, settings};
use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

pub struct BookingInfo {
    pub user_id: Uuid,
    pub membership_id: Uuid,
    pub user_name: String,
    pub user_mobile: Option<String>,
    pub user_email: Option<String>,
    pub plan_name: String,
    pub plan_type: String,
    pub seat_number: Option<String>,
    pub shift: String,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub amount_paid: Decimal,
}

pub struct PaymentReceiptInfo {
    pub user_id: Uuid,
    pub user_name: String,
    pub user_mobile: Option<String>,
    pub user_email: Option<String>,
    pub invoice_id: String,
    pub amount_paid: Decimal,
    pub amount_pending: Decimal,
    pub plan_name: String,
    pub seat_number: Option<String>,
    pub valid_upto: Option<NaiveDate>,
    pub payment_method: String,
}

fn format_shift(shift: &str) -> &'static str {
    match shift.to_uppercase().as_str() {
        "MORNING" => "Morning (6AM-2PM)",
        "EVENING" => "Evening (2PM-10PM)",
        _ => "Full Day (6AM-10PM)",
    }
}

pub async fn send_booking_confirmed(state: &Arc<AppState>, info: &BookingInfo) {
    let settings = settings::setting_for(state, "BOOKING_CONFIRMED").await;
    let name = if info.user_name.is_empty() { "Student" } else { &info.user_name };
    let shift_label = format_shift(&info.shift);
    let seat = info.seat_number.as_deref().unwrap_or("N/A");

    let whatsapp_msg = settings::apply_hindi(&format!(
        "Booking Confirmed! Hi {}, Target Zone Library - Membership Details: \
Plan: {} | Seat: {} | Shift: {} | From: {} | To: {} | Paid: Rs.{:.0}. \
Please carry a valid ID on your first visit. Happy studying! - Target Zone Library",
        name, info.plan_name, seat, shift_label, info.start_date, info.end_date, info.amount_paid
    ), &settings, true);

    let email_body = settings::apply_hindi(&format!(
        "Dear {},\n\nYour library membership has been confirmed!\n\n\
MEMBERSHIP DETAILS\n\
------------------\n\
Plan        : {}\n\
Seat Number : {}\n\
Shift       : {}\n\
Start Date  : {}\n\
End Date    : {}\n\
Amount Paid : Rs.{:.0}\n\n\
Library Timings:\n\
  Morning Shift : 6:00 AM - 2:00 PM\n\
  Evening Shift : 2:00 PM - 10:00 PM\n\n\
Please carry a valid photo ID on your first visit.\n\n\
Best regards,\n\
Target Zone Library Team\n\
https://targetzone.co.in",
        name, info.plan_name, seat, shift_label, info.start_date, info.end_date, info.amount_paid
    ), &settings, true);

    if settings.send_to_student {
        if let Some(ref mobile) = info.user_mobile {
            send_whatsapp(state, mobile, &whatsapp_msg).await;
        }
        if let Some(ref email) = info.user_email {
            send_email(state, email, "Your Library Seat is Confirmed!", &email_body).await;
        }
    }

    let admin_msg = settings::apply_hindi(&format!(
        "New Booking! Student: {} | Seat: {} | Plan: {} | Shift: {} | Amount: Rs.{:.0}",
        name, seat, info.plan_name, shift_label, info.amount_paid
    ), &settings, false);
    if settings.send_to_admin {
        if !state.config.admin_whatsapp.is_empty() {
            send_whatsapp(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
        }
        send_email(
            state,
            &state.config.admin_email.clone(),
            &format!("New Booking - {} | Seat {}", name, seat),
            &admin_msg,
        ).await;
    }

    // Best-effort — a rendering/upload/Meta-template failure here must never
    // affect the plain-text booking confirmation the student already got above.
    send_student_id_card_best_effort(state, info).await;
}

async fn send_student_id_card_best_effort(state: &Arc<AppState>, info: &BookingInfo) {
    let Some(ref mobile) = info.user_mobile else { return };
    if let Err(e) = send_student_id_card(
        state, info.user_id, info.membership_id, &info.user_name,
        Some(mobile.as_str()), info.user_email.as_deref(),
    ).await {
        tracing::warn!("{e}");
    }
}

/// Renders and sends a student's ID card (WhatsApp image template + email PDF
/// attachment). Shared by the automatic post-booking send above and the
/// admin-triggered on-demand resend in `services::admin::send_id_card`.
pub async fn send_student_id_card(
    state: &Arc<AppState>,
    user_id: Uuid,
    membership_id: Uuid,
    user_name: &str,
    mobile: Option<&str>,
    email: Option<&str>,
) -> Result<(), String> {
    let pdf = crate::services::idcard::generate(state, user_id)
        .await
        .map_err(|e| format!("ID card generation failed for {user_id}: {e}"))?;

    let settings = settings::setting_for(state, "STUDENT_ID_CARD").await;
    let id_number = ids::member_id(membership_id);
    let pdf_name = format!("{id_number}_id_card.pdf");

    // WhatsApp's "image" header template requires an actual image content-type,
    // not a PDF — rendered separately from the PDF (used for email/download).
    let image_link = match crate::services::idcard::generate_image(state, user_id).await {
        Ok(png) => {
            let png_name = format!("{id_number}_id_card.png");
            host_generated_file(state, "id-cards", &png_name, png).await
        }
        Err(e) => { tracing::warn!("ID card image generation failed for {user_id}: {e}"); None }
    };

    if let Some(link) = image_link {
        let params = vec![user_name.to_string()];
        if settings.send_to_student {
            if let Some(mobile) = mobile {
                send_image_template(
                    state, mobile, &link, &params,
                    &state.config.meta_id_card_image_template_name.clone(),
                    &state.config.meta_id_card_image_language.clone(),
                    Some(user_id), "STUDENT_ID_CARD",
                ).await;
            }
        }
        if settings.send_to_admin {
            for number in admin_whatsapp_numbers(state) {
                send_image_template(
                    state, &number, &link, &params,
                    &state.config.meta_id_card_image_template_name.clone(),
                    &state.config.meta_id_card_image_language.clone(),
                    None, "STUDENT_ID_CARD_ADMIN",
                ).await;
            }
        }
    } else {
        tracing::warn!("ID card image not hosted for membership {membership_id} — skipping WhatsApp send");
    }

    if settings.send_to_student {
        if let Some(email) = email {
            let body = settings::apply_hindi(
                &format!("Dear {user_name},\n\nPlease find your library ID card attached.\n\nID No.: {id_number}"),
                &settings, true,
            );
            send_email_with_attachment(state, email, "Your Target Zone Library ID Card", &body, &pdf, &pdf_name).await;
        }
    }

    Ok(())
}

fn admin_whatsapp_numbers(state: &Arc<AppState>) -> Vec<String> {
    if state.config.admin_whatsapp.is_empty() {
        return vec![];
    }
    state.config.admin_whatsapp.split(',').map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect()
}

/// Writes generated bytes (receipt/ID-card) under the shared uploads dir and
/// returns a public HTTPS link Meta's servers can fetch — since this is a
/// single binary (unlike Java's pod-to-pod upload to user-service), we just
/// write straight to the same directory `tower_http::ServeDir` already serves
/// at `/uploads`.
async fn host_generated_file(state: &Arc<AppState>, subdir: &str, filename: &str, bytes: Vec<u8>) -> Option<String> {
    let dir = format!("{}/{subdir}", state.config.upload_dir.trim_end_matches('/'));
    if tokio::fs::create_dir_all(&dir).await.is_err() {
        return None;
    }
    let path = format!("{dir}/{filename}");
    if tokio::fs::write(&path, &bytes).await.is_err() {
        return None;
    }
    Some(format!("{}/uploads/{subdir}/{filename}", state.config.frontend_url.trim_end_matches('/')))
}

// ── Payment Receipt ───────────────────────────────────────────────────────────
// Triggered after a payment is confirmed (online, cash, or dues clearance).
// Generates a PDF, hosts it locally, sends it as a real WhatsApp document
// attachment via the approved "payment_receipt" template, and emails it as a
// real attachment too.

pub async fn send_payment_receipt(state: &Arc<AppState>, info: &PaymentReceiptInfo) {
    send_payment_receipt_typed(state, info, "PAYMENT_RECEIPT").await;
}

pub async fn send_payment_receipt_typed(state: &Arc<AppState>, info: &PaymentReceiptInfo, _receipt_type: &str) {
    let settings = settings::setting_for(state, "PAYMENT_RECEIPT").await;

    let pdf = match crate::services::receipt::build_receipt_pdf(info) {
        Ok(bytes) => bytes,
        Err(e) => { tracing::error!("Receipt PDF generation failed for invoice {}: {e}", info.invoice_id); return; }
    };
    let attachment_name = format!("{}.pdf", if info.invoice_id.is_empty() { "receipt" } else { &info.invoice_id });
    let receipt_link = host_generated_file(state, "receipts", &attachment_name, pdf.clone()).await;

    let paid_str = info.amount_paid.normalize().to_string();
    let pending_str = info.amount_pending.normalize().to_string();
    let today = chrono::Local::now().date_naive();

    // "payment_receipt" template's {{1}}..{{5}} order: name, amount paid
    // (numeric only), invoice number, payment date (DD/MM/YYYY), pending amount.
    let body_params = vec![
        if info.user_name.is_empty() { "Student".to_string() } else { info.user_name.clone() },
        paid_str.clone(),
        info.invoice_id.clone(),
        today.format("%d/%m/%Y").to_string(),
        pending_str.clone(),
    ];

    if let Some(ref link) = receipt_link {
        if settings.send_to_student {
            if let Some(ref mobile) = info.user_mobile {
                send_document_template(
                    state, mobile, link, &attachment_name, &body_params,
                    &state.config.meta_receipt_template_name.clone(),
                    &state.config.meta_receipt_language.clone(),
                    Some(info.user_id), "PAYMENT_RECEIPT",
                ).await;
            }
        }
        if settings.send_to_admin {
            for number in admin_whatsapp_numbers(state) {
                send_document_template(
                    state, &number, link, &attachment_name, &body_params,
                    &state.config.meta_receipt_template_name.clone(),
                    &state.config.meta_receipt_language.clone(),
                    None, "PAYMENT_RECEIPT_ADMIN",
                ).await;
            }
        }
    } else {
        // Couldn't host the PDF — the document-header template requires a
        // link, so fall back to a plain-text message rather than dropping
        // the notification entirely.
        tracing::warn!("Receipt PDF not hosted for invoice {} — WhatsApp falling back to text-only", info.invoice_id);
        let fallback = format!(
            "Payment Receipt\n\nInvoice : {}\nDate    : {}\nPaid    : Rs.{paid_str}\nPending : Rs.{pending_str}\n\nTarget Zone Library",
            info.invoice_id, today.format("%d/%m/%Y"),
        );
        if settings.send_to_student {
            if let Some(ref mobile) = info.user_mobile {
                send_whatsapp(state, mobile, &fallback).await;
            }
        }
        if settings.send_to_admin {
            for number in admin_whatsapp_numbers(state) {
                send_whatsapp(state, &number, &fallback).await;
            }
        }
    }

    if settings.send_to_student {
        if let Some(ref email) = info.user_email {
            let body = settings::apply_hindi(
                &format!(
                    "Dear {},\n\nPlease find attached your payment receipt.\n\nInvoice No.: {}\nAmount Paid: Rs.{paid_str}\nAmount Pending: Rs.{pending_str}",
                    info.user_name, info.invoice_id
                ),
                &settings, true,
            );
            send_email_with_attachment(state, email, &format!("Payment Receipt — Invoice {}", info.invoice_id), &body, &pdf, &attachment_name).await;
        }
    }
}

/// Meta template send with a document-header attachment (e.g. "payment_receipt").
/// `url` must be a public HTTPS link Meta's servers can fetch; `body_params`
/// must match the template's {{1}}..{{n}} order exactly.
pub async fn send_document_template(
    state: &Arc<AppState>,
    mobile: &str,
    url: &str,
    filename: &str,
    body_params: &[String],
    template_name: &str,
    language: &str,
    user_id: Option<Uuid>,
    event: &str,
) {
    send_meta_document_or_image(state, mobile, "document", url, Some(filename), body_params, template_name, language, user_id, event).await;
}

/// Meta template send with an image-header attachment (e.g. "id_card").
pub async fn send_image_template(
    state: &Arc<AppState>,
    mobile: &str,
    url: &str,
    body_params: &[String],
    template_name: &str,
    language: &str,
    user_id: Option<Uuid>,
    event: &str,
) {
    send_meta_document_or_image(state, mobile, "image", url, None, body_params, template_name, language, user_id, event).await;
}

async fn send_meta_document_or_image(
    state: &Arc<AppState>,
    mobile: &str,
    header_kind: &str,
    url: &str,
    filename: Option<&str>,
    body_params: &[String],
    template_name: &str,
    language: &str,
    user_id: Option<Uuid>,
    event: &str,
) {
    if state.config.meta_whatsapp_token.is_empty() {
        tracing::info!("[DEV] WhatsApp ({template_name} template) → {mobile} | Event: {event} | url={url} params={body_params:?}");
        return;
    }

    let clean_to = mobile.trim_start_matches('+');
    let to = if clean_to.len() == 10 { format!("91{clean_to}") } else { clean_to.to_string() };

    let mut header_media = json!({ "type": header_kind, header_kind: { "link": url } });
    if header_kind == "document" {
        if let Some(name) = filename {
            header_media[header_kind]["filename"] = json!(name);
        }
    }

    let sanitized_params: Vec<_> = body_params.iter().map(|p| sanitize_param(p)).collect();
    let body_parameters: Vec<_> = sanitized_params.iter().map(|p| json!({ "type": "text", "text": p })).collect();

    let body = json!({
        "messaging_product": "whatsapp",
        "to": to,
        "type": "template",
        "template": {
            "name": template_name,
            "language": { "code": language },
            "components": [
                { "type": "header", "parameters": [ header_media ] },
                { "type": "body", "parameters": body_parameters }
            ]
        }
    });

    let url_meta = format!("https://graph.facebook.com/v18.0/{}/messages", state.config.meta_whatsapp_phone_id);
    match state.http.post(&url_meta).bearer_auth(&state.config.meta_whatsapp_token).json(&body).send().await {
        Err(e) => tracing::error!("Meta {template_name} send network error to {mobile}: {e}"),
        Ok(r) if !r.status().is_success() => {
            let status = r.status();
            let resp_body = r.text().await.unwrap_or_default();
            tracing::error!("Meta {template_name} rejected: {status} for {mobile} — {resp_body}");
        }
        Ok(r) => {
            let resp_body = r.text().await.unwrap_or_default();
            tracing::info!("Meta {template_name} template sent to {mobile} | Event: {event} | response: {resp_body}");
        }
    }
    let _ = user_id;
}

/// Sends the "seat_renewal_confirmation" Meta template (Yes/No quick-reply
/// poll) 3 days before a membership's end_date. Unlike the other Meta send
/// functions in this file, this one returns the response's wamid so the
/// caller can persist it onto the `renewal_polls` row and later correlate
/// an inbound webhook button-tap reply back to this exact send. The header
/// ("Seat Renewal") is static text baked into the approved template and the
/// Yes/No buttons have no configured payload, so only a body component is
/// needed — no header/button components like send_meta_document_or_image.
pub async fn send_renewal_poll(
    state: &Arc<AppState>,
    mobile: &str,
    student_name: &str,
) -> Option<String> {
    if state.config.meta_whatsapp_token.is_empty() {
        tracing::info!("[DEV] WhatsApp (renewal poll) → {mobile} | student={student_name}");
        return None;
    }

    let clean_to = mobile.trim_start_matches('+');
    let to = if clean_to.len() == 10 { format!("91{clean_to}") } else { clean_to.to_string() };

    let body = json!({
        "messaging_product": "whatsapp",
        "to": to,
        "type": "template",
        "template": {
            "name": state.config.meta_whatsapp_poll_template_name,
            "language": { "code": state.config.meta_whatsapp_poll_language },
            "components": [
                { "type": "body", "parameters": [{ "type": "text", "text": sanitize_param(student_name) }] }
            ]
        }
    });

    let url = format!("https://graph.facebook.com/v18.0/{}/messages", state.config.meta_whatsapp_phone_id);
    match state.http.post(&url).bearer_auth(&state.config.meta_whatsapp_token).json(&body).send().await {
        Err(e) => { tracing::error!("Meta renewal poll send network error to {mobile}: {e}"); None }
        Ok(r) if !r.status().is_success() => {
            let status = r.status();
            let resp_body = r.text().await.unwrap_or_default();
            tracing::error!("Meta renewal poll rejected: {status} for {mobile} — {resp_body}");
            None
        }
        Ok(r) => {
            let resp_body = r.text().await.unwrap_or_default();
            let wamid = serde_json::from_str::<serde_json::Value>(&resp_body)
                .ok()
                .and_then(|v| v["messages"][0]["id"].as_str().map(|s| s.to_string()));
            tracing::info!("Meta renewal poll sent to {mobile} | wamid={wamid:?}");
            wamid
        }
    }
}

fn sanitize_param(param: &str) -> String {
    param.replace(['\n', '\r', '\t'], " ").split_whitespace().collect::<Vec<_>>().join(" ")
}

pub async fn send_welcome(state: &Arc<AppState>, name: &str, mobile: Option<&str>, email: Option<&str>) {
    let settings = settings::setting_for(state, "USER_REGISTERED").await;
    let msg = settings::apply_hindi(&format!(
        "👋 Welcome to Target Zone, {}!\nYour account has been created. Start your study journey today!",
        name
    ), &settings, true);
    if settings.send_to_student {
        if let Some(m) = mobile {
            send_whatsapp(state, m, &msg).await;
        }
        if let Some(e) = email {
            send_email(state, e, "Welcome to Target Zone", &msg).await;
        }
    }

    let admin_msg = settings::apply_hindi(&format!(
        "🆕 New Student Registered!\nName: {}\nMobile: {}\nEmail: {}",
        name,
        mobile.unwrap_or("N/A"),
        email.unwrap_or("N/A"),
    ), &settings, false);
    if settings.send_to_admin {
        if !state.config.admin_whatsapp.is_empty() {
            send_whatsapp(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
        }
        send_email(state, &state.config.admin_email.clone(), &format!("New Registration — {name}"), &admin_msg).await;
    }
}

pub async fn send_seat_assistance(
    state: &Arc<AppState>,
    user_name: &str,
    seat_number: &str,
) {
    let msg = format!(
        "🚨 Seat Assistance Request\nStudent: {}\nSeat: {}\nPlease attend to this student.",
        user_name, seat_number
    );
    if !state.config.admin_whatsapp.is_empty() {
        send_whatsapp(state, &state.config.admin_whatsapp.clone(), &msg).await;
    }
    send_email(state, &state.config.admin_email.clone(), "Seat Assistance Request", &msg).await;
}

pub async fn send_renewal_reminder(
    state: &Arc<AppState>,
    name: &str,
    mobile: Option<&str>,
    email: Option<&str>,
    days_left: i64,
    end_date: NaiveDate,
) {
    let settings = settings::setting_for(state, "RENEWAL_REMINDER").await;
    let urgency = if days_left <= 3 { "⚠️ URGENT" } else { "⏰ Reminder" };
    let msg = settings::apply_hindi(&format!(
        "{} — Hi {}! Your library membership expires on {}. Only {} day(s) left. Please renew to keep your seat.",
        urgency, name, end_date, days_left
    ), &settings, true);
    if settings.send_to_student {
        if let Some(m) = mobile {
            send_whatsapp(state, m, &msg).await;
        }
        if let Some(e) = email {
            send_email(state, e, "Membership Renewal Reminder", &msg).await;
        }
    }
}

pub async fn send_direct_message(
    state: &Arc<AppState>,
    mobile: Option<&str>,
    email: Option<&str>,
    message: &str,
) {
    if let Some(m) = mobile {
        send_whatsapp(state, m, message).await;
    }
    if let Some(e) = email {
        send_email(state, e, "Message from Target Zone", message).await;
    }
}

pub async fn send_seat_expired(state: &Arc<AppState>, user_name: &str, seat_number: &str) {
    let msg = format!(
        "🪑 Seat Now Available!\nStudent: {}\nSeat: {} has been freed up and is available for new booking.",
        user_name, seat_number
    );
    if !state.config.admin_whatsapp.is_empty() {
        send_whatsapp(state, &state.config.admin_whatsapp.clone(), &msg).await;
    }
    send_email(state, &state.config.admin_email.clone(), "Seat Now Available", &msg).await;
}

pub async fn send_broadcast(
    state: &Arc<AppState>,
    recipients: &[(Option<String>, Option<String>)],
    message: &str,
) -> usize {
    let settings = settings::setting_for(state, "ADMIN_BROADCAST").await;
    let body = settings::apply_hindi(message, &settings, true);

    let mut count = 0;
    if settings.send_to_student {
        for (mobile, email) in recipients {
            if let Some(ref m) = mobile {
                send_whatsapp(state, m, &body).await;
                count += 1;
            }
            if let Some(ref e) = email {
                send_email(state, e, "Announcement from Target Zone", &body).await;
            }
        }
    }

    let echo = format!("📢 Broadcast sent to {count} student(s):\n{message}");
    if !state.config.admin_whatsapp.is_empty() {
        send_whatsapp(state, &state.config.admin_whatsapp.clone(), &echo).await;
    }
    // ADMIN_WHATSAPP is a single "ops" number and is often left unset (as it
    // currently is in prod); ADMIN_PHONES is the actual admin-login allowlist,
    // so mirror the echo there too — every real admin gets a WhatsApp copy,
    // not just whoever's mailbox ADMIN_EMAIL points at.
    for phone in &state.config.admin_phones {
        if phone != &state.config.admin_whatsapp {
            send_whatsapp(state, phone, &echo).await;
        }
    }
    send_email(state, &state.config.admin_email.clone(), "Broadcast Sent", &echo).await;

    count
}

pub async fn send_otp_sms(state: &Arc<AppState>, mobile: &str, otp: &str) {
    let msg = format!("Your Target Zone OTP is: {}. Valid for 5 minutes.", otp);
    if !state.config.twilio_account_sid.is_empty() {
        send_twilio_sms(state, mobile, &msg).await;
    }
}

pub async fn send_otp_email(state: &Arc<AppState>, email: &str, otp: &str) {
    let msg = format!("Your Target Zone OTP is: {}. Valid for 5 minutes.", otp);
    send_email(state, email, "Your OTP - Target Zone", &msg).await;
}

/// Returns `true` on a confirmed send so callers can fall through to the
/// next channel in the OTP fallback chain (apitxt, then Twilio) on failure.
pub async fn send_meta_otp(state: &Arc<AppState>, mobile: &str, otp: &str) -> bool {
    let clean_to = mobile.trim_start_matches('+');
    let to = if clean_to.len() == 10 {
        format!("91{clean_to}")
    } else {
        clean_to.to_string()
    };
    let url = format!(
        "https://graph.facebook.com/v18.0/{}/messages",
        state.config.meta_whatsapp_phone_id
    );
    let body = json!({
        "messaging_product": "whatsapp",
        "to": to,
        "type": "template",
        "template": {
            "name": state.config.meta_whatsapp_otp_template,
            "language": { "code": state.config.meta_whatsapp_language },
            "components": [
                {
                    "type": "body",
                    "parameters": [{ "type": "text", "text": otp }]
                },
                {
                    "type": "button",
                    "sub_type": "url",
                    "index": "0",
                    "parameters": [{ "type": "text", "text": otp }]
                }
            ]
        }
    });
    tracing::info!("Meta OTP → {to} via template '{}'", state.config.meta_whatsapp_otp_template);
    match state
        .http
        .post(&url)
        .bearer_auth(&state.config.meta_whatsapp_token)
        .json(&body)
        .send()
        .await
    {
        Err(e) => { tracing::error!("Meta OTP network error: {e}"); false }
        Ok(r) if !r.status().is_success() => {
            let status = r.status();
            let body = r.text().await.unwrap_or_default();
            tracing::error!("Meta OTP rejected: {status} — {body}");
            false
        }
        Ok(_) => { tracing::info!("Meta OTP sent to {to}"); true }
    }
}

/// apitxt SMS OTP — the default SMS channel when configured (falls back to
/// Twilio if this isn't set up or the request fails). Returns `true` on a
/// confirmed send.
pub async fn send_apitxt_otp_sms(state: &Arc<AppState>, mobile: &str, otp: &str) -> bool {
    if state.config.apitxt_auth_key.is_empty() {
        return false;
    }
    let digits = mobile
        .strip_prefix("+91")
        .or_else(|| mobile.strip_prefix('+'))
        .unwrap_or(mobile);
    let url = format!(
        "https://apitxt.com/api/sendOTP?authkey={}&mobile=91{digits}&otp={otp}",
        state.config.apitxt_auth_key
    );
    let resp = match state.http.get(&url).send().await {
        Ok(r) => r,
        Err(e) => { tracing::error!("apitxt network error: {e}"); return false; }
    };
    if !resp.status().is_success() {
        tracing::error!("apitxt HTTP error: {}", resp.status());
        return false;
    }
    match resp.json::<serde_json::Value>().await {
        Ok(body) => {
            let ok = matches!(body.get("status"), Some(v) if v == 200 || v == "200");
            if ok {
                tracing::info!("SMS OTP sent to {mobile} via apitxt (request_id={:?})", body.get("request_id"));
            } else {
                tracing::error!("apitxt error: {body}");
            }
            ok
        }
        Err(e) => { tracing::error!("apitxt response parse error: {e}"); false }
    }
}

pub async fn send_whatsapp_to(state: &Arc<AppState>, to: &str, message: &str) {
    send_whatsapp(state, to, message).await;
}

pub async fn send_email_to(state: &Arc<AppState>, to: &str, subject: &str, body: &str) {
    send_email(state, to, subject, body).await;
}

async fn send_whatsapp(state: &Arc<AppState>, to: &str, message: &str) {
    if !state.config.meta_whatsapp_token.is_empty() {
        send_meta_whatsapp(state, to, message).await;
    } else if !state.config.twilio_account_sid.is_empty() {
        send_twilio_whatsapp(state, to, message).await;
    } else {
        tracing::info!("DEV WhatsApp → {to}: {message}");
    }
}

async fn send_email(state: &Arc<AppState>, to: &str, subject: &str, body: &str) {
    if !state.config.sendgrid_api_key.is_empty() {
        send_sendgrid_email(state, to, subject, body).await;
    } else {
        tracing::info!("DEV Email → {to} [{subject}]: {body}");
    }
}

async fn send_twilio_sms(state: &Arc<AppState>, to: &str, message: &str) {
    let url = format!(
        "https://api.twilio.com/2010-04-01/Accounts/{}/Messages.json",
        state.config.twilio_account_sid
    );
    let params = [
        ("From", state.config.twilio_phone_number.as_str()),
        ("To", to),
        ("Body", message),
    ];
    match state
        .http
        .post(&url)
        .basic_auth(&state.config.twilio_account_sid, Some(&state.config.twilio_auth_token))
        .form(&params)
        .send()
        .await
    {
        Err(e) => tracing::error!("Twilio SMS network error: {e}"),
        Ok(r) if !r.status().is_success() => tracing::error!("Twilio SMS rejected: {} for {to}", r.status()),
        Ok(_) => tracing::info!("Twilio SMS sent to {to}"),
    }
}

async fn send_twilio_whatsapp(state: &Arc<AppState>, to: &str, message: &str) {
    let to_wa = if to.starts_with("whatsapp:") {
        to.to_string()
    } else {
        format!("whatsapp:{to}")
    };
    let url = format!(
        "https://api.twilio.com/2010-04-01/Accounts/{}/Messages.json",
        state.config.twilio_account_sid
    );
    let params = [
        ("From", state.config.twilio_whatsapp_from.as_str()),
        ("To", &to_wa),
        ("Body", message),
    ];
    match state
        .http
        .post(&url)
        .basic_auth(&state.config.twilio_account_sid, Some(&state.config.twilio_auth_token))
        .form(&params)
        .send()
        .await
    {
        Err(e) => tracing::error!("Twilio WhatsApp network error: {e}"),
        Ok(r) if !r.status().is_success() => tracing::error!("Twilio WhatsApp rejected: {} for {to}", r.status()),
        Ok(_) => tracing::info!("Twilio WhatsApp sent to {to}"),
    }
}

async fn send_meta_whatsapp(state: &Arc<AppState>, to: &str, message: &str) {
    // Strip newlines/tabs — Meta template params reject them
    let param = message
        .replace(['\n', '\r', '\t'], " ")
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");

    let clean_to = to.trim_start_matches('+');
    let to_num = if clean_to.len() == 10 {
        format!("91{clean_to}")
    } else {
        clean_to.to_string()
    };

    let url = format!(
        "https://graph.facebook.com/v18.0/{}/messages",
        state.config.meta_whatsapp_phone_id
    );
    let body = json!({
        "messaging_product": "whatsapp",
        "to": to_num,
        "type": "template",
        "template": {
            "name": state.config.meta_whatsapp_notif_template,
            "language": { "code": state.config.meta_whatsapp_language },
            "components": [
                {
                    "type": "body",
                    "parameters": [{ "type": "text", "text": param }]
                }
            ]
        }
    });
    match state
        .http
        .post(&url)
        .bearer_auth(&state.config.meta_whatsapp_token)
        .json(&body)
        .send()
        .await
    {
        Err(e) => tracing::error!("Meta WhatsApp network error: {e}"),
        Ok(r) if !r.status().is_success() => {
            let status = r.status();
            let resp_body = r.text().await.unwrap_or_default();
            tracing::error!("Meta WhatsApp rejected: {status} for {to} — {resp_body}");
        }
        Ok(r) => {
            let resp_body = r.text().await.unwrap_or_default();
            tracing::info!("Meta WhatsApp sent to {to} | response: {resp_body}");
        }
    }
}

async fn send_sendgrid_email(state: &Arc<AppState>, to: &str, subject: &str, body: &str) {
    let payload = json!({
        "personalizations": [{ "to": [{ "email": to }] }],
        "from": { "email": state.config.admin_email },
        "subject": subject,
        "content": [{ "type": "text/plain", "value": body }]
    });
    match state
        .http
        .post("https://api.sendgrid.com/v3/mail/send")
        .bearer_auth(&state.config.sendgrid_api_key)
        .json(&payload)
        .send()
        .await
    {
        Err(e) => tracing::error!("SendGrid network error: {e}"),
        Ok(r) if !r.status().is_success() => tracing::error!("SendGrid rejected: {} for {to} [{subject}]", r.status()),
        Ok(_) => tracing::info!("SendGrid email sent to {to} [{subject}]"),
    }
}

/// SendGrid email with a real attachment (base64-encoded per their API) —
/// used for receipts and ID cards, which must reach the inbox as a proper
/// PDF, not just a link.
pub async fn send_email_with_attachment(
    state: &Arc<AppState>,
    to: &str,
    subject: &str,
    body: &str,
    attachment_bytes: &[u8],
    attachment_filename: &str,
) {
    if state.config.sendgrid_api_key.is_empty() {
        tracing::info!("[DEV] Email → {to} [{subject}] with attachment {attachment_filename} ({} bytes): {body}", attachment_bytes.len());
        return;
    }

    use base64::Engine;
    let encoded = base64::engine::general_purpose::STANDARD.encode(attachment_bytes);

    let payload = json!({
        "personalizations": [{ "to": [{ "email": to }] }],
        "from": { "email": state.config.admin_email },
        "subject": subject,
        "content": [{ "type": "text/plain", "value": body }],
        "attachments": [{
            "content": encoded,
            "filename": attachment_filename,
            "type": "application/pdf",
            "disposition": "attachment"
        }]
    });

    match state
        .http
        .post("https://api.sendgrid.com/v3/mail/send")
        .bearer_auth(&state.config.sendgrid_api_key)
        .json(&payload)
        .send()
        .await
    {
        Err(e) => tracing::error!("SendGrid (attachment) network error: {e}"),
        Ok(r) if !r.status().is_success() => tracing::error!("SendGrid (attachment) rejected: {} for {to} [{subject}]", r.status()),
        Ok(_) => tracing::info!("SendGrid email with attachment sent to {to} [{subject}]"),
    }
}

pub async fn log_notification(
    db: &sqlx::PgPool,
    user_id: Option<uuid::Uuid>,
    recipient: &str,
    message: &str,
    event: &str,
    channel: &str,
    status: &str,
    error: Option<&str>,
) {
    let _ = sqlx::query(
        "INSERT INTO notification_logs (user_id, recipient, message, event, channel, status, error_message)
         VALUES ($1, $2, $3, $4, $5, $6, $7)",
    )
    .bind(user_id)
    .bind(recipient)
    .bind(message)
    .bind(event)
    .bind(channel)
    .bind(status)
    .bind(error)
    .execute(db)
    .await;
}

#[cfg(test)]
mod pure_fn_tests {
    use super::*;

    #[test]
    fn format_shift_known_values() {
        assert_eq!(format_shift("MORNING"), "Morning (6AM-2PM)");
        assert_eq!(format_shift("EVENING"), "Evening (2PM-10PM)");
        assert_eq!(format_shift("FULL_DAY"), "Full Day (6AM-10PM)");
    }

    #[test]
    fn format_shift_is_case_insensitive() {
        assert_eq!(format_shift("morning"), "Morning (6AM-2PM)");
        assert_eq!(format_shift("Evening"), "Evening (2PM-10PM)");
    }

    #[test]
    fn format_shift_unknown_value_falls_back_to_full_day() {
        assert_eq!(format_shift("NOT_A_SHIFT"), "Full Day (6AM-10PM)");
        assert_eq!(format_shift(""), "Full Day (6AM-10PM)");
    }

    #[test]
    fn sanitize_param_collapses_newlines_and_tabs_to_single_spaces() {
        assert_eq!(sanitize_param("Row A\nSeat 5\r\n"), "Row A Seat 5");
        assert_eq!(sanitize_param("a\tb\tc"), "a b c");
    }

    #[test]
    fn sanitize_param_collapses_repeated_whitespace() {
        assert_eq!(sanitize_param("too    many     spaces"), "too many spaces");
    }

    #[test]
    fn sanitize_param_trims_leading_and_trailing_whitespace() {
        assert_eq!(sanitize_param("  padded  "), "padded");
    }

    #[test]
    fn sanitize_param_empty_input_stays_empty() {
        assert_eq!(sanitize_param(""), "");
        assert_eq!(sanitize_param("   "), "");
    }
}
