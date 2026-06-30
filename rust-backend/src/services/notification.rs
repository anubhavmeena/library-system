use crate::app_state::AppState;
use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde_json::json;
use std::sync::Arc;

pub struct BookingInfo {
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

fn format_shift(shift: &str) -> &'static str {
    match shift.to_uppercase().as_str() {
        "MORNING" => "Morning (6AM-2PM)",
        "EVENING" => "Evening (2PM-10PM)",
        _ => "Full Day (6AM-10PM)",
    }
}

pub async fn send_booking_confirmed(state: &Arc<AppState>, info: &BookingInfo) {
    let name = if info.user_name.is_empty() { "Student" } else { &info.user_name };
    let shift_label = format_shift(&info.shift);
    let seat = info.seat_number.as_deref().unwrap_or("N/A");

    let whatsapp_msg = format!(
        "Booking Confirmed! Hi {}, Target Zone Library - Membership Details: \
Plan: {} | Seat: {} | Shift: {} | From: {} | To: {} | Paid: Rs.{:.0}. \
Please carry a valid ID on your first visit. Happy studying! - Target Zone Library",
        name, info.plan_name, seat, shift_label, info.start_date, info.end_date, info.amount_paid
    );

    let email_body = format!(
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
    );

    if let Some(ref mobile) = info.user_mobile {
        send_whatsapp(state, mobile, &whatsapp_msg).await;
    }
    if let Some(ref email) = info.user_email {
        send_email(state, email, "Your Library Seat is Confirmed!", &email_body).await;
    }

    let admin_msg = format!(
        "New Booking! Student: {} | Seat: {} | Plan: {} | Shift: {} | Amount: Rs.{:.0}",
        name, seat, info.plan_name, shift_label, info.amount_paid
    );
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

pub async fn send_welcome(state: &Arc<AppState>, name: &str, mobile: Option<&str>, email: Option<&str>) {
    let msg = format!(
        "👋 Welcome to Target Zone, {}!\nYour account has been created. Start your study journey today!",
        name
    );
    if let Some(m) = mobile {
        send_whatsapp(state, m, &msg).await;
    }
    if let Some(e) = email {
        send_email(state, e, "Welcome to Target Zone", &msg).await;
    }

    let admin_msg = format!(
        "🆕 New Student Registered!\nName: {}\nMobile: {}\nEmail: {}",
        name,
        mobile.unwrap_or("N/A"),
        email.unwrap_or("N/A"),
    );
    if !state.config.admin_whatsapp.is_empty() {
        send_whatsapp(state, &state.config.admin_whatsapp.clone(), &admin_msg).await;
    }
    send_email(state, &state.config.admin_email.clone(), &format!("New Registration — {name}"), &admin_msg).await;
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
    let urgency = if days_left <= 3 { "⚠️ URGENT" } else { "⏰ Reminder" };
    let msg = format!(
        "{} — Hi {}! Your library membership expires on {}. Only {} day(s) left. Please renew to keep your seat.",
        urgency, name, end_date, days_left
    );
    if let Some(m) = mobile {
        send_whatsapp(state, m, &msg).await;
    }
    if let Some(e) = email {
        send_email(state, e, "Membership Renewal Reminder", &msg).await;
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
    let mut count = 0;
    for (mobile, email) in recipients {
        if let Some(ref m) = mobile {
            send_whatsapp(state, m, message).await;
            count += 1;
        }
        if let Some(ref e) = email {
            send_email(state, e, "Announcement from Target Zone", message).await;
        }
    }

    let echo = format!("📢 Broadcast sent to {count} student(s):\n{message}");
    if !state.config.admin_whatsapp.is_empty() {
        send_whatsapp(state, &state.config.admin_whatsapp.clone(), &echo).await;
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

pub async fn send_meta_otp(state: &Arc<AppState>, mobile: &str, otp: &str) {
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
        Err(e) => tracing::error!("Meta OTP network error: {e}"),
        Ok(r) if !r.status().is_success() => {
            let status = r.status();
            let body = r.text().await.unwrap_or_default();
            tracing::error!("Meta OTP rejected: {status} — {body}");
        }
        Ok(_) => tracing::info!("Meta OTP sent to {to}"),
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
        Ok(_) => tracing::info!("Meta WhatsApp sent to {to}"),
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
