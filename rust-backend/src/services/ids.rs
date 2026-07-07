use uuid::Uuid;

/// `INV-YYYYMMDD-XXXXXX` — matches the Java backend's invoice numbering
/// scheme exactly so printed receipts look consistent across backends.
pub fn generate_invoice_id() -> String {
    let today = chrono::Local::now().date_naive();
    let suffix: String = Uuid::new_v4().to_string().chars().take(6).collect();
    format!("INV-{}-{}", today.format("%Y%m%d"), suffix.to_uppercase())
}

/// The member/ID-card number shown on ID cards and receipts: the first 8
/// hex characters of the membership UUID, uppercased.
pub fn member_id(membership_id: Uuid) -> String {
    membership_id.to_string().chars().take(8).collect::<String>().to_uppercase()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn invoice_id_matches_inv_date_suffix_shape() {
        let id = generate_invoice_id();
        let today = chrono::Local::now().date_naive().format("%Y%m%d").to_string();
        assert!(id.starts_with(&format!("INV-{today}-")));
        let suffix = id.rsplit('-').next().unwrap();
        assert_eq!(suffix.len(), 6);
        assert!(suffix.chars().all(|c| c.is_ascii_uppercase() || c.is_ascii_digit()));
    }

    #[test]
    fn invoice_id_is_unique_per_call() {
        assert_ne!(generate_invoice_id(), generate_invoice_id());
    }

    #[test]
    fn member_id_is_first_8_chars_uppercased() {
        let id = Uuid::parse_str("ab3f9c12-1234-5678-9abc-def012345678").unwrap();
        assert_eq!(member_id(id), "AB3F9C12");
    }
}
