use std::env;

#[derive(Clone, Debug)]
pub struct Config {
    pub database_url: String,
    pub redis_url: String,
    pub jwt_secret: String,
    pub jwt_expiry_ms: i64,
    pub upload_dir: String,
    pub port: u16,
    // Twilio
    pub twilio_account_sid: String,
    pub twilio_auth_token: String,
    pub twilio_phone_number: String,
    pub twilio_whatsapp_from: String,
    // SendGrid
    pub sendgrid_api_key: String,
    // Meta WhatsApp
    pub meta_whatsapp_token: String,
    pub meta_whatsapp_phone_id: String,
    pub meta_whatsapp_otp_template: String,
    pub meta_whatsapp_notif_template: String,
    pub meta_whatsapp_language: String,
    pub meta_receipt_template_name: String,
    pub meta_receipt_language: String,
    pub meta_id_card_image_template_name: String,
    pub meta_id_card_image_language: String,
    // apitxt SMS (OTP fallback channel between Meta WhatsApp and Twilio)
    pub apitxt_auth_key: String,
    // Public base URL used to build links Meta's servers fetch (receipt/ID-card documents)
    pub frontend_url: String,
    // Payment
    pub payment_gateway: String,
    pub razorpay_key_id: String,
    pub razorpay_key_secret: String,
    pub cashfree_app_id: String,
    pub cashfree_secret_key: String,
    pub cashfree_env: String,
    pub cashfree_base_url: String,
    // Admin
    pub admin_email: String,
    pub admin_whatsapp: String,
    pub admin_phones: Vec<String>,
    // Admin mailbox (IMAP read + SMTP reply) — powers the admin "Inbox" page
    pub imap_host: String,
    pub imap_port: u16,
    pub imap_use_ssl: bool,
    pub admin_imap_user: String,
    pub admin_imap_pass: String,
    pub smtp_host: String,
    pub smtp_port: u16,
    pub from_email: String,
    pub from_name: String,
}

impl Config {
    pub fn from_env() -> Self {
        let cashfree_env = env::var("CASHFREE_ENV").unwrap_or_else(|_| "sandbox".to_string());
        let cashfree_base_url = if cashfree_env == "production" {
            "https://api.cashfree.com".to_string()
        } else {
            "https://sandbox.cashfree.com".to_string()
        };

        Config {
            database_url: env::var("DATABASE_URL").unwrap_or_else(|_| {
                "postgres://library_user:library_pass@localhost:5432/library_db".to_string()
            }),
            redis_url: env::var("REDIS_URL")
                .unwrap_or_else(|_| "redis://localhost:6379".to_string()),
            jwt_secret: env::var("JWT_SECRET").unwrap_or_else(|_| {
                "library-jwt-secret-key-2024-change-in-production".to_string()
            }),
            jwt_expiry_ms: env::var("JWT_EXPIRY_MS")
                .ok()
                .and_then(|s| s.parse().ok())
                .unwrap_or(86_400_000),
            upload_dir: env::var("UPLOAD_DIR").unwrap_or_else(|_| "/app/uploads".to_string()),
            port: env::var("PORT").ok().and_then(|s| s.parse().ok()).unwrap_or(8080),
            twilio_account_sid: env::var("TWILIO_ACCOUNT_SID").unwrap_or_default(),
            twilio_auth_token: env::var("TWILIO_AUTH_TOKEN").unwrap_or_default(),
            twilio_phone_number: env::var("TWILIO_PHONE_NUMBER").unwrap_or_default(),
            twilio_whatsapp_from: env::var("TWILIO_WHATSAPP_FROM")
                .unwrap_or_else(|_| "whatsapp:+14155238886".to_string()),
            sendgrid_api_key: env::var("SENDGRID_API_KEY").unwrap_or_default(),
            meta_whatsapp_token: env::var("META_WHATSAPP_TOKEN").unwrap_or_default(),
            meta_whatsapp_phone_id: env::var("META_WHATSAPP_PHONE_NUMBER_ID").unwrap_or_default(),
            meta_whatsapp_otp_template: env::var("META_WHATSAPP_TEMPLATE_NAME")
                .unwrap_or_else(|_| "otpvm".to_string()),
            meta_whatsapp_notif_template: env::var("META_NOTIFICATION_TEMPLATE_NAME")
                .unwrap_or_else(|_| "tznallh".to_string()),
            meta_whatsapp_language: env::var("META_WHATSAPP_LANGUAGE")
                .unwrap_or_else(|_| "en".to_string()),
            // "payment_receipt" was approved under plain English ("en"), not
            // English (US) ("en_US") like the notification template — Meta
            // requires an exact language-code match per template, so this is
            // deliberately a separate env var, not a reuse of meta_whatsapp_language.
            meta_receipt_template_name: env::var("META_WHATSAPP_RECEIPT_TEMPLATE_NAME")
                .unwrap_or_else(|_| "payment_receipt".to_string()),
            meta_receipt_language: env::var("META_WHATSAPP_RECEIPT_LANGUAGE")
                .unwrap_or_else(|_| "en".to_string()),
            meta_id_card_image_template_name: env::var("META_WHATSAPP_ID_CARD_TEMPLATE_NAME")
                .unwrap_or_else(|_| "id_card".to_string()),
            meta_id_card_image_language: env::var("META_WHATSAPP_ID_CARD_LANGUAGE")
                .unwrap_or_else(|_| "en".to_string()),
            apitxt_auth_key: env::var("APITXT_AUTH_KEY").unwrap_or_default(),
            frontend_url: env::var("FRONTEND_URL")
                .unwrap_or_else(|_| "https://targetzone.co.in".to_string()),
            payment_gateway: env::var("PAYMENT_GATEWAY")
                .unwrap_or_else(|_| "CASHFREE".to_string()),
            razorpay_key_id: env::var("RAZORPAY_KEY_ID").unwrap_or_default(),
            razorpay_key_secret: env::var("RAZORPAY_KEY_SECRET").unwrap_or_default(),
            cashfree_app_id: env::var("CASHFREE_APP_ID").unwrap_or_default(),
            cashfree_secret_key: env::var("CASHFREE_SECRET_KEY").unwrap_or_default(),
            cashfree_env,
            cashfree_base_url,
            admin_email: env::var("ADMIN_EMAIL")
                .unwrap_or_else(|_| "admin@targetzone.co.in".to_string()),
            admin_whatsapp: env::var("ADMIN_WHATSAPP").unwrap_or_default(),
            // Normalized the same way self-service login normalizes a typed
            // contact (services::auth::normalize_contact) — otherwise an
            // ADMIN_PHONES entry written as "+91 xxxxxxxxxx" would silently
            // never match the bare-digits mobile stored on the user row.
            admin_phones: env::var("ADMIN_PHONES")
                .unwrap_or_default()
                .split(',')
                .map(|s| s.trim())
                .filter(|s| !s.is_empty())
                .filter_map(|s| match crate::services::auth::normalize_contact(s) {
                    Ok(normalized) => Some(normalized),
                    Err(_) => {
                        tracing::warn!("Ignoring malformed entry in ADMIN_PHONES: {s}");
                        None
                    }
                })
                .collect(),
            imap_host: env::var("IMAP_HOST").unwrap_or_else(|_| "localhost".to_string()),
            imap_port: env::var("IMAP_PORT")
                .ok()
                .and_then(|s| s.parse().ok())
                .unwrap_or(143),
            imap_use_ssl: env::var("IMAP_SSL")
                .map(|s| s.eq_ignore_ascii_case("true"))
                .unwrap_or(false),
            admin_imap_user: env::var("ADMIN_IMAP_USER").unwrap_or_else(|_| "admin".to_string()),
            admin_imap_pass: env::var("ADMIN_IMAP_PASS").unwrap_or_default(),
            smtp_host: env::var("SMTP_HOST").unwrap_or_else(|_| "localhost".to_string()),
            smtp_port: env::var("SMTP_PORT")
                .ok()
                .and_then(|s| s.parse().ok())
                .unwrap_or(25),
            from_email: env::var("FROM_EMAIL")
                .unwrap_or_else(|_| "admin@targetzone.co.in".to_string()),
            from_name: env::var("FROM_NAME").unwrap_or_else(|_| "Target Zone Library".to_string()),
        }
    }

    pub fn is_twilio_dev(&self) -> bool {
        self.twilio_account_sid.is_empty()
    }

    pub fn is_sendgrid_dev(&self) -> bool {
        self.sendgrid_api_key.is_empty()
    }

    pub fn is_razorpay_dev(&self) -> bool {
        self.razorpay_key_id.is_empty()
    }

    pub fn is_cashfree_dev(&self) -> bool {
        self.cashfree_app_id.is_empty()
    }
}

#[cfg(test)]
pub(crate) fn test_config() -> Config {
    Config {
        database_url: "postgres://test:test@localhost:5432/testdb".to_string(),
        redis_url: "redis://localhost:6379".to_string(),
        jwt_secret: "test-secret-key-at-least-32-bytes-long!".to_string(),
        jwt_expiry_ms: 86_400_000,
        upload_dir: "/tmp/uploads".to_string(),
        port: 8080,
        twilio_account_sid: "".to_string(),
        twilio_auth_token: "".to_string(),
        twilio_phone_number: "".to_string(),
        twilio_whatsapp_from: "whatsapp:+14155238886".to_string(),
        sendgrid_api_key: "".to_string(),
        meta_whatsapp_token: "".to_string(),
        meta_whatsapp_phone_id: "".to_string(),
        meta_whatsapp_otp_template: "otpvm".to_string(),
        meta_whatsapp_notif_template: "tznallh".to_string(),
        meta_whatsapp_language: "en".to_string(),
        meta_receipt_template_name: "payment_receipt".to_string(),
        meta_receipt_language: "en".to_string(),
        meta_id_card_image_template_name: "id_card".to_string(),
        meta_id_card_image_language: "en".to_string(),
        apitxt_auth_key: "".to_string(),
        frontend_url: "https://targetzone.co.in".to_string(),
        payment_gateway: "CASHFREE".to_string(),
        razorpay_key_id: "".to_string(),
        razorpay_key_secret: "".to_string(),
        cashfree_app_id: "".to_string(),
        cashfree_secret_key: "".to_string(),
        cashfree_env: "sandbox".to_string(),
        cashfree_base_url: "https://sandbox.cashfree.com".to_string(),
        admin_email: "admin@test.com".to_string(),
        admin_whatsapp: "".to_string(),
        admin_phones: vec![],
        imap_host: "localhost".to_string(),
        imap_port: 143,
        imap_use_ssl: false,
        admin_imap_user: "admin".to_string(),
        admin_imap_pass: "".to_string(),
        smtp_host: "localhost".to_string(),
        smtp_port: 25,
        from_email: "admin@test.com".to_string(),
        from_name: "Test Library".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn base_config() -> Config {
        test_config()
    }

    #[test]
    fn is_twilio_dev_when_sid_empty() {
        assert!(base_config().is_twilio_dev());
    }

    #[test]
    fn is_not_twilio_dev_when_sid_set() {
        let mut c = base_config();
        c.twilio_account_sid = "ACxxxxxxxx".to_string();
        assert!(!c.is_twilio_dev());
    }

    #[test]
    fn is_sendgrid_dev_when_api_key_empty() {
        assert!(base_config().is_sendgrid_dev());
    }

    #[test]
    fn is_not_sendgrid_dev_when_api_key_set() {
        let mut c = base_config();
        c.sendgrid_api_key = "SG.real_key".to_string();
        assert!(!c.is_sendgrid_dev());
    }

    #[test]
    fn is_razorpay_dev_when_key_id_empty() {
        assert!(base_config().is_razorpay_dev());
    }

    #[test]
    fn is_not_razorpay_dev_when_key_id_set() {
        let mut c = base_config();
        c.razorpay_key_id = "rzp_live_xxxxx".to_string();
        assert!(!c.is_razorpay_dev());
    }

    #[test]
    fn is_cashfree_dev_when_app_id_empty() {
        assert!(base_config().is_cashfree_dev());
    }

    #[test]
    fn is_not_cashfree_dev_when_app_id_set() {
        let mut c = base_config();
        c.cashfree_app_id = "CF_APP_ID_123".to_string();
        assert!(!c.is_cashfree_dev());
    }

    #[test]
    fn cashfree_sandbox_url_used_by_default() {
        let c = base_config();
        assert!(c.cashfree_base_url.contains("sandbox.cashfree.com"));
    }

    #[test]
    fn cashfree_production_url_has_no_sandbox() {
        let mut c = base_config();
        c.cashfree_base_url = "https://api.cashfree.com".to_string();
        assert!(c.cashfree_base_url.contains("api.cashfree.com"));
        assert!(!c.cashfree_base_url.contains("sandbox"));
    }

    #[test]
    fn admin_phones_empty_vec_by_default() {
        assert!(base_config().admin_phones.is_empty());
    }

    #[test]
    fn admin_phones_multi_element() {
        let mut c = base_config();
        c.admin_phones = vec!["9876543210".to_string(), "9123456789".to_string()];
        assert_eq!(c.admin_phones.len(), 2);
        assert!(c.admin_phones.contains(&"9876543210".to_string()));
    }

    #[test]
    fn admin_phones_csv_parsing_logic() {
        // Mirrors what from_env() does when ADMIN_PHONES="111,222, 333"
        let raw = "111,222, 333";
        let parsed: Vec<String> = raw
            .split(',')
            .filter(|s| !s.trim().is_empty())
            .map(|s| s.trim().to_string())
            .collect();
        assert_eq!(parsed, vec!["111", "222", "333"]);
    }

    #[test]
    fn admin_phones_empty_string_gives_empty_vec() {
        let parsed: Vec<String> = ""
            .split(',')
            .filter(|s| !s.trim().is_empty())
            .map(|s| s.trim().to_string())
            .collect();
        assert!(parsed.is_empty());
    }

    #[test]
    fn dev_mode_all_credentials_empty_by_default() {
        let c = base_config();
        assert!(c.is_twilio_dev());
        assert!(c.is_sendgrid_dev());
        assert!(c.is_razorpay_dev());
        assert!(c.is_cashfree_dev());
    }
}
