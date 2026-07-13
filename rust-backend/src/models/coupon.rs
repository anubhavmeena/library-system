use chrono::NaiveDateTime;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Coupon {
    pub id: Uuid,
    pub code: String,
    pub discount_percent: i16,
    pub is_active: bool,
    pub created_at: Option<NaiveDateTime>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateCouponRequest {
    /// Blank/absent auto-generates an 8-char code.
    pub code: Option<String>,
    pub discount_percent: i16,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCouponRequest {
    pub discount_percent: Option<i16>,
    pub is_active: Option<bool>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ValidateCouponRequest {
    pub code: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn coupon_serializes_camel_case() {
        let c = Coupon {
            id: Uuid::new_v4(),
            code: "SAVE10".into(),
            discount_percent: 10,
            is_active: true,
            created_at: None,
        };
        let json = serde_json::to_string(&c).unwrap();
        assert!(json.contains("discountPercent"));
        assert!(json.contains("isActive"));
        assert!(!json.contains("discount_percent"));
    }

    #[test]
    fn create_coupon_request_code_is_optional() {
        let req: CreateCouponRequest = serde_json::from_str(r#"{"discountPercent": 20}"#).unwrap();
        assert_eq!(req.code, None);
        assert_eq!(req.discount_percent, 20);
    }
}
