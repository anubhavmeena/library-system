use crate::error::AppError;
use chrono::Utc;
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,
    pub role: String,
    pub name: String,
    pub email: Option<String>,
    pub mobile: Option<String>,
    pub exp: usize,
    pub iat: usize,
}

pub fn create_token(
    user_id: Uuid,
    role: &str,
    name: &str,
    email: Option<&str>,
    mobile: Option<&str>,
    secret: &str,
    expiry_ms: i64,
) -> crate::error::Result<String> {
    let now = Utc::now().timestamp() as usize;
    let exp = now + (expiry_ms / 1000) as usize;

    let claims = Claims {
        sub: user_id.to_string(),
        role: role.to_string(),
        name: name.to_string(),
        email: email.map(|s| s.to_string()),
        mobile: mobile.map(|s| s.to_string()),
        exp,
        iat: now,
    };

    encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(secret.as_bytes()),
    )
    .map_err(|e| AppError::Internal(format!("JWT encode error: {e}")))
}

pub fn decode_token(token: &str, secret: &str) -> crate::error::Result<Claims> {
    let mut validation = Validation::default();
    validation.validate_exp = true;

    decode::<Claims>(
        token,
        &DecodingKey::from_secret(secret.as_bytes()),
        &validation,
    )
    .map(|data| data.claims)
    .map_err(|_| AppError::Unauthorized)
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    const SECRET: &str = "library-jwt-secret-key-2024-change-in-production";
    const EXPIRY_MS: i64 = 86_400_000;

    #[test]
    fn create_and_decode_roundtrip() {
        let user_id = Uuid::new_v4();
        let token = create_token(
            user_id, "STUDENT", "Alice",
            Some("alice@test.com"), Some("9876543210"),
            SECRET, EXPIRY_MS,
        ).unwrap();
        let claims = decode_token(&token, SECRET).unwrap();
        assert_eq!(claims.sub, user_id.to_string());
        assert_eq!(claims.role, "STUDENT");
        assert_eq!(claims.name, "Alice");
        assert_eq!(claims.email, Some("alice@test.com".to_string()));
        assert_eq!(claims.mobile, Some("9876543210".to_string()));
    }

    #[test]
    fn admin_role_preserved_in_token() {
        let user_id = Uuid::new_v4();
        let token = create_token(
            user_id, "ADMIN", "Admin User",
            Some("admin@test.com"), None,
            SECRET, EXPIRY_MS,
        ).unwrap();
        let claims = decode_token(&token, SECRET).unwrap();
        assert_eq!(claims.role, "ADMIN");
        assert_eq!(claims.mobile, None);
    }

    #[test]
    fn wrong_secret_returns_unauthorized() {
        let user_id = Uuid::new_v4();
        let token = create_token(user_id, "STUDENT", "Alice", None, None, SECRET, EXPIRY_MS).unwrap();
        let result = decode_token(&token, "completely-different-secret-key-at-least-32chars!!");
        assert!(matches!(result, Err(crate::error::AppError::Unauthorized)));
    }

    #[test]
    fn garbage_string_returns_unauthorized() {
        let result = decode_token("not.a.valid.jwt.at.all", SECRET);
        assert!(matches!(result, Err(crate::error::AppError::Unauthorized)));
    }

    #[test]
    fn expired_token_returns_unauthorized() {
        use jsonwebtoken::{encode, EncodingKey, Header};
        let claims = Claims {
            sub: Uuid::new_v4().to_string(),
            role: "STUDENT".to_string(),
            name: "Alice".to_string(),
            email: None,
            mobile: None,
            exp: 1000,  // Unix timestamp 1000 = Jan 1970, always expired
            iat: 0,
        };
        let token = encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(SECRET.as_bytes()),
        ).unwrap();
        let result = decode_token(&token, SECRET);
        assert!(matches!(result, Err(crate::error::AppError::Unauthorized)));
    }

    #[test]
    fn optional_fields_none_when_not_provided() {
        let user_id = Uuid::new_v4();
        let token = create_token(user_id, "STUDENT", "Bob", None, None, SECRET, EXPIRY_MS).unwrap();
        let claims = decode_token(&token, SECRET).unwrap();
        assert_eq!(claims.email, None);
        assert_eq!(claims.mobile, None);
    }

    #[test]
    fn iat_and_exp_are_set_correctly() {
        let before = chrono::Utc::now().timestamp() as usize;
        let user_id = Uuid::new_v4();
        let token = create_token(user_id, "STUDENT", "Alice", None, None, SECRET, EXPIRY_MS).unwrap();
        let claims = decode_token(&token, SECRET).unwrap();
        let after = chrono::Utc::now().timestamp() as usize + 1;
        assert!(claims.iat >= before && claims.iat <= after);
        assert!(claims.exp > claims.iat);
        assert_eq!(claims.exp - claims.iat, (EXPIRY_MS / 1000) as usize);
    }

    #[test]
    fn sub_roundtrips_as_uuid() {
        let user_id = Uuid::new_v4();
        let token = create_token(user_id, "STUDENT", "Alice", None, None, SECRET, EXPIRY_MS).unwrap();
        let claims = decode_token(&token, SECRET).unwrap();
        assert_eq!(claims.sub.parse::<Uuid>().unwrap(), user_id);
    }
}
