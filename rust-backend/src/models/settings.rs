use chrono::NaiveDateTime;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AppSettings {
    pub id: i64,
    pub wifi_name: Option<String>,
    pub wifi_password: Option<String>,
    pub upi_id: Option<String>,
    pub grace_days: i32,
    pub convenience_fee: Decimal,
    pub water_tanker_rate: Decimal,
    pub updated_at: Option<NaiveDateTime>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveAppSettingsRequest {
    pub wifi_name: Option<String>,
    pub wifi_password: Option<String>,
    pub upi_id: Option<String>,
    pub grace_days: i32,
    pub convenience_fee: Decimal,
    pub water_tanker_rate: Decimal,
}

#[derive(Debug, Clone, FromRow)]
pub struct NotificationSettingRow {
    pub notification_key: String,
    pub send_to_student: bool,
    pub send_to_admin: bool,
    pub hindi_enabled: bool,
    pub hindi_text_student: Option<String>,
    pub hindi_text_admin: Option<String>,
    #[allow(dead_code)]
    pub updated_at: Option<NaiveDateTime>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationSettingDto {
    pub notification_key: String,
    pub send_to_student: bool,
    pub send_to_admin: bool,
    pub hindi_enabled: bool,
    pub hindi_text_student: Option<String>,
    pub hindi_text_admin: Option<String>,
    pub student_editable: bool,
    pub admin_editable: bool,
    pub hindi_editable: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateNotificationSettingRequest {
    pub send_to_student: bool,
    pub send_to_admin: bool,
    pub hindi_enabled: bool,
    pub hindi_text_student: Option<String>,
    pub hindi_text_admin: Option<String>,
}

/// One entry per notification key, ordered to match the admin Notification
/// Settings page. `hindi_editable = false` means the key has no Hindi concept
/// at all (e.g. ADMIN_BROADCAST's body is authored fresh each send, not a
/// template) — the UI hides the Hindi fields entirely for those.
pub struct NotificationCatalogEntry {
    pub key: &'static str,
    pub default_send_to_student: bool,
    pub default_send_to_admin: bool,
    pub student_editable: bool,
    pub admin_editable: bool,
    pub hindi_editable: bool,
    pub default_hindi_text_student: Option<&'static str>,
    pub default_hindi_text_admin: Option<&'static str>,
}

pub const NOTIFICATION_CATALOG: &[NotificationCatalogEntry] = &[
    NotificationCatalogEntry { key: "BOOKING_CONFIRMED", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "STUDENT_ID_CARD", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "USER_REGISTERED", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "RENEWAL_REMINDER", default_send_to_student: true, default_send_to_admin: false, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "PENDING_FEE_REMINDER", default_send_to_student: true, default_send_to_admin: false, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "GRACE_DUES_REMINDER", default_send_to_student: true, default_send_to_admin: false, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "MEMBERSHIP_GRACE", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "PENDING_FEE_CLEARED", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "PAYMENT_RECEIPT", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "ADMIN_BROADCAST", default_send_to_student: true, default_send_to_admin: true, student_editable: false, admin_editable: false, hindi_editable: false, default_hindi_text_student: None, default_hindi_text_admin: None },
    NotificationCatalogEntry { key: "GRACE_DUES_CLEARED", default_send_to_student: true, default_send_to_admin: true, student_editable: true, admin_editable: true, hindi_editable: true,
        default_hindi_text_student: Some("आपकी बकाया राशि जमा कर दी गई है और आपकी सदस्यता फिर से सक्रिय कर दी गई है।"),
        default_hindi_text_admin: Some("छात्र की बकाया राशि जमा कर दी गई है।") },
];

pub fn catalog_entry(key: &str) -> Option<&'static NotificationCatalogEntry> {
    NOTIFICATION_CATALOG.iter().find(|e| e.key == key)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_entry_finds_known_key() {
        let e = catalog_entry("BOOKING_CONFIRMED").unwrap();
        assert!(e.default_send_to_student);
        assert!(e.default_send_to_admin);
    }

    #[test]
    fn catalog_entry_unknown_key_is_none() {
        assert!(catalog_entry("NOT_A_REAL_KEY").is_none());
    }

    #[test]
    fn admin_broadcast_has_no_hindi_concept() {
        let e = catalog_entry("ADMIN_BROADCAST").unwrap();
        assert!(!e.hindi_editable);
        assert!(!e.student_editable);
        assert!(!e.admin_editable);
    }

    #[test]
    fn grace_dues_cleared_ships_default_hindi_text() {
        let e = catalog_entry("GRACE_DUES_CLEARED").unwrap();
        assert!(e.default_hindi_text_student.is_some());
        assert!(e.default_hindi_text_admin.is_some());
    }

    #[test]
    fn every_catalog_key_is_unique() {
        let mut keys: Vec<&str> = NOTIFICATION_CATALOG.iter().map(|e| e.key).collect();
        let len_before = keys.len();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), len_before, "duplicate notification catalog key");
    }

    #[test]
    fn app_settings_serializes_camel_case() {
        let s = AppSettings {
            id: 1,
            wifi_name: Some("lib-wifi".into()),
            wifi_password: None,
            upi_id: Some("lib@ybl".into()),
            grace_days: 10,
            convenience_fee: Decimal::from(20),
            water_tanker_rate: Decimal::from(150),
            updated_at: None,
        };
        let json = serde_json::to_string(&s).unwrap();
        assert!(json.contains("wifiName"));
        assert!(json.contains("graceDays"));
        assert!(json.contains("convenienceFee"));
        assert!(json.contains("waterTankerRate"));
        assert!(!json.contains("wifi_name"));
    }

    #[test]
    fn save_app_settings_request_deserializes_camel_case() {
        let json = r#"{"wifiName":"x","wifiPassword":null,"upiId":"a@b","graceDays":7,"convenienceFee":"15","waterTankerRate":"100"}"#;
        let req: SaveAppSettingsRequest = serde_json::from_str(json).unwrap();
        assert_eq!(req.wifi_name, Some("x".to_string()));
        assert_eq!(req.grace_days, 7);
        assert_eq!(req.convenience_fee, Decimal::from(15));
    }

    #[test]
    fn notification_setting_dto_serializes_camel_case() {
        let dto = NotificationSettingDto {
            notification_key: "X".into(),
            send_to_student: true,
            send_to_admin: false,
            hindi_enabled: true,
            hindi_text_student: None,
            hindi_text_admin: None,
            student_editable: true,
            admin_editable: false,
            hindi_editable: true,
        };
        let json = serde_json::to_string(&dto).unwrap();
        assert!(json.contains("notificationKey"));
        assert!(json.contains("sendToStudent"));
        assert!(json.contains("studentEditable"));
    }
}
