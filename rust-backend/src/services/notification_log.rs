//! Per-student record of every student-facing notification (reminders,
//! receipts, direct messages, ...) in `notification_logs`, backing the send
//! logs on the admin Reminders tabs and the Student detail page.
//!
//! One row per notification, not per channel: `channel` lists every channel
//! attempted ("WHATSAPP,EMAIL") and `status` is SENT only if all of them went
//! out. Recording is best-effort, like `activity_log::log_activity` — a
//! logging failure must never affect the send it describes.

use crate::{app_state::AppState, error::AppError, models::notification_log::NotificationLogEntry};
use std::sync::Arc;
use uuid::Uuid;

pub const RENEWAL_REMINDER: &str = "RENEWAL_REMINDER";
pub const RENEWAL_POLL: &str = "RENEWAL_POLL";
pub const PENDING_FEE_REMINDER: &str = "PENDING_FEE_REMINDER";
pub const GRACE_DUES_REMINDER: &str = "GRACE_DUES_REMINDER";
pub const DIRECT_MESSAGE: &str = "DIRECT_MESSAGE";
pub const BROADCAST: &str = "BROADCAST";
pub const BOOKING_CONFIRMED: &str = "BOOKING_CONFIRMED";
pub const PAYMENT_RECEIPT: &str = "PAYMENT_RECEIPT";
pub const ID_CARD: &str = "ID_CARD";
pub const WELCOME: &str = "WELCOME";
pub const PENDING_FEE_CLEARED: &str = "PENDING_FEE_CLEARED";
pub const GRACE_DUES_CLEARED: &str = "GRACE_DUES_CLEARED";
pub const MEMBERSHIP_GRACE: &str = "MEMBERSHIP_GRACE";
pub const SEAT_RELEASED: &str = "SEAT_RELEASED";
pub const PAYMENT_CLAIM: &str = "PAYMENT_CLAIM";

/// Who a notification was for and why. `sent_by: None` means the system sent
/// it on its own (AUTO); `Some(admin name)` means an admin explicitly
/// triggered this send (MANUAL).
#[derive(Debug, Clone)]
pub struct LogMeta {
    pub user_id: Uuid,
    pub event: &'static str,
    pub sent_by: Option<String>,
}

impl LogMeta {
    pub fn auto(user_id: Uuid, event: &'static str) -> Self {
        Self { user_id, event, sent_by: None }
    }

    pub fn manual(user_id: Uuid, event: &'static str, admin_name: &str) -> Self {
        Self { user_id, event, sent_by: Some(admin_name.to_string()) }
    }

    /// `manual` when an admin name is given, `auto` otherwise.
    pub fn by(user_id: Uuid, event: &'static str, sent_by: Option<&str>) -> Self {
        Self { user_id, event, sent_by: sent_by.map(str::to_string) }
    }
}

/// Each channel's (recipient, delivered?) — `None` means not attempted.
#[derive(Debug, Default)]
pub struct Delivery {
    pub whatsapp: Option<(String, bool)>,
    pub email: Option<(String, bool)>,
}

impl Delivery {
    pub fn ok(&self) -> bool {
        let attempted = self.whatsapp.is_some() || self.email.is_some();
        attempted
            && self.whatsapp.as_ref().is_none_or(|(_, ok)| *ok)
            && self.email.as_ref().is_none_or(|(_, ok)| *ok)
    }

    fn channel(&self) -> String {
        let mut parts = Vec::new();
        if self.whatsapp.is_some() { parts.push("WHATSAPP"); }
        if self.email.is_some() { parts.push("EMAIL"); }
        parts.join(",")
    }

    fn recipient(&self) -> String {
        [&self.whatsapp, &self.email]
            .into_iter()
            .flatten()
            .map(|(to, _)| to.as_str())
            .collect::<Vec<_>>()
            .join(", ")
    }

    fn error(&self) -> Option<String> {
        if self.whatsapp.is_none() && self.email.is_none() {
            return Some("No mobile or email on file".into());
        }
        let mut failed = Vec::new();
        if matches!(self.whatsapp, Some((_, false))) { failed.push("WhatsApp"); }
        if matches!(self.email, Some((_, false))) { failed.push("Email"); }
        (!failed.is_empty()).then(|| format!("{} delivery failed", failed.join(" and ")))
    }
}

pub async fn record(state: &Arc<AppState>, meta: &LogMeta, delivery: &Delivery, message: &str) {
    let status = if delivery.ok() { "SENT" } else { "FAILED" };
    let trigger = if meta.sent_by.is_some() { "MANUAL" } else { "AUTO" };
    let result = sqlx::query(
        "INSERT INTO notification_logs
             (user_id, recipient, message, event, channel, status, error_message, trigger_type, sent_by, sent_at, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())",
    )
    .bind(meta.user_id)
    .bind(delivery.recipient())
    .bind(message)
    .bind(meta.event)
    .bind(delivery.channel())
    .bind(status)
    .bind(delivery.error())
    .bind(trigger)
    .bind(&meta.sent_by)
    .execute(&state.db)
    .await;

    if let Err(e) = result {
        tracing::error!("Failed to record notification log ({}) for {}: {e}", meta.event, meta.user_id);
    }
}

const SELECT: &str = r#"SELECT n.id, n.user_id, u.name AS student_name, u.mobile AS student_mobile,
                               n.recipient, n.message, n.event, n.channel, n.status, n.error_message,
                               n.trigger_type, n.sent_by, COALESCE(n.sent_at, n.created_at) AS sent_at
                        FROM notification_logs n
                        LEFT JOIN users u ON u.id = n.user_id"#;

/// Every notification of the given events sent in the last `days` days,
/// newest first — the Reminders page's per-tab log.
pub async fn list_by_events(
    state: &Arc<AppState>,
    events: &[&str],
    days: i32,
) -> crate::error::Result<Vec<NotificationLogEntry>> {
    let events: Vec<String> = events.iter().map(|e| e.to_string()).collect();
    sqlx::query_as::<_, NotificationLogEntry>(&format!(
        "{SELECT}
         WHERE n.event = ANY($1)
           AND COALESCE(n.sent_at, n.created_at) >= NOW() - make_interval(days => $2)
         ORDER BY COALESCE(n.sent_at, n.created_at) DESC
         LIMIT 2000"
    ))
    .bind(&events)
    .bind(days)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

/// Every notification of any kind sent to a student in the last `days` days,
/// newest first — the Student detail page's notification history.
pub async fn list_for_user(
    state: &Arc<AppState>,
    user_id: Uuid,
    days: i32,
) -> crate::error::Result<Vec<NotificationLogEntry>> {
    sqlx::query_as::<_, NotificationLogEntry>(&format!(
        "{SELECT}
         WHERE n.user_id = $1
           AND COALESCE(n.sent_at, n.created_at) >= NOW() - make_interval(days => $2)
         ORDER BY COALESCE(n.sent_at, n.created_at) DESC
         LIMIT 500"
    ))
    .bind(user_id)
    .bind(days)
    .fetch_all(&state.db)
    .await
    .map_err(AppError::Database)
}

#[cfg(test)]
mod tests {
    use super::Delivery;

    #[test]
    fn nothing_attempted_is_a_failure() {
        let d = Delivery::default();
        assert!(!d.ok());
        assert_eq!(d.error().as_deref(), Some("No mobile or email on file"));
    }

    #[test]
    fn all_channels_ok_is_sent() {
        let d = Delivery { whatsapp: Some(("9999999999".into(), true)), email: Some(("a@b.c".into(), true)) };
        assert!(d.ok());
        assert_eq!(d.error(), None);
        assert_eq!(d.channel(), "WHATSAPP,EMAIL");
        assert_eq!(d.recipient(), "9999999999, a@b.c");
    }

    #[test]
    fn one_failed_channel_fails_the_row() {
        let d = Delivery { whatsapp: Some(("9999999999".into(), false)), email: Some(("a@b.c".into(), true)) };
        assert!(!d.ok());
        assert_eq!(d.error().as_deref(), Some("WhatsApp delivery failed"));
    }
}
