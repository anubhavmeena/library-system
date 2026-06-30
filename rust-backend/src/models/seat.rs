use chrono::{NaiveDate, NaiveDateTime};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use std::collections::HashMap;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Seat {
    pub id: Uuid,
    pub seat_number: String,
    pub row_label: String,
    pub seat_index: i32,
    pub is_active: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[serde(rename_all = "camelCase")]
pub struct SeatBooking {
    pub id: Uuid,
    pub seat_id: Uuid,
    pub user_id: Uuid,
    pub membership_id: Uuid,
    pub shift: String,
    pub booking_date: NaiveDate,
    pub end_date: NaiveDate,
    pub status: String,
    pub created_at: Option<NaiveDateTime>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StudentSeatBooking {
    pub id: Uuid,
    pub seat_id: Uuid,
    pub seat_number: String,
    pub row_label: String,
    pub user_id: Uuid,
    pub membership_id: Uuid,
    pub shift: String,
    pub booking_date: NaiveDate,
    pub end_date: NaiveDate,
    pub status: String,
    pub created_at: Option<NaiveDateTime>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SeatAvailability {
    pub seat_id: Uuid,
    pub seat_number: String,
    pub row_label: String,
    pub seat_index: i32,
    pub is_active: bool,
    pub is_booked: bool,
    pub booked_by: Option<Uuid>,
    pub booked_by_name: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SeatAvailabilityResponse {
    pub shift: String,
    pub date: NaiveDate,
    pub seats: Vec<SeatAvailability>,
    pub seats_by_row: HashMap<String, Vec<SeatAvailability>>,
    pub available_count: usize,
    pub booked_count: usize,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BookSeatRequest {
    pub membership_id: Uuid,
    pub seat_number: String,
    pub shift: String,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
}

#[derive(Debug, Deserialize)]
pub struct SeatAvailabilityQuery {
    pub shift: String,
    pub date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct AdminBookingsQuery {
    pub shift: String,
    pub date: NaiveDate,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSeatBooking {
    pub booking_id: Uuid,
    pub seat_number: String,
    pub shift: String,
    pub booking_date: NaiveDate,
    pub end_date: NaiveDate,
    pub user_id: Uuid,
    pub user_name: String,
    pub user_mobile: Option<String>,
    pub membership_id: Uuid,
    pub status: String,
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn seat_availability_serializes_camel_case() {
        let avail = SeatAvailability {
            seat_id: Uuid::new_v4(),
            seat_number: "A1".to_string(),
            row_label: "A".to_string(),
            seat_index: 1,
            is_active: true,
            is_booked: false,
            booked_by: None,
            booked_by_name: None,
        };
        let json = serde_json::to_string(&avail).unwrap();
        assert!(json.contains("seatId"), "Expected camelCase seatId");
        assert!(json.contains("seatNumber"), "Expected camelCase seatNumber");
        assert!(json.contains("rowLabel"), "Expected camelCase rowLabel");
        assert!(json.contains("isActive"), "Expected camelCase isActive");
        assert!(json.contains("isBooked"), "Expected camelCase isBooked");
        assert!(!json.contains("\"seat_id\""), "Should not contain snake_case seat_id");
    }

    #[test]
    fn book_seat_request_deserializes_camel_case() {
        let mid = Uuid::new_v4();
        let json = format!(
            r#"{{"membershipId":"{mid}","seatNumber":"B5","shift":"MORNING","startDate":"2025-01-15","endDate":"2025-02-14"}}"#
        );
        let req: BookSeatRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(req.seat_number, "B5");
        assert_eq!(req.shift, "MORNING");
        assert_eq!(req.membership_id, mid);
        assert_eq!(req.start_date, NaiveDate::from_ymd_opt(2025, 1, 15).unwrap());
        assert_eq!(req.end_date, NaiveDate::from_ymd_opt(2025, 2, 14).unwrap());
    }

    #[test]
    fn valid_shift_values() {
        let shifts = ["MORNING", "EVENING", "FULL_DAY"];
        for s in shifts {
            assert!(!s.is_empty());
            assert!(s.chars().all(|c| c.is_uppercase() || c == '_'));
        }
    }

    #[test]
    fn valid_booking_status_values() {
        // Status values used in ON CONFLICT … WHERE status != 'ACTIVE'
        let statuses = ["ACTIVE", "RELEASED", "CANCELLED"];
        for s in statuses {
            assert!(!s.is_empty());
        }
    }

    #[test]
    fn seat_availability_booked_fields_present_when_booked() {
        let user_id = Uuid::new_v4();
        let avail = SeatAvailability {
            seat_id: Uuid::new_v4(),
            seat_number: "C10".to_string(),
            row_label: "C".to_string(),
            seat_index: 10,
            is_active: true,
            is_booked: true,
            booked_by: Some(user_id),
            booked_by_name: Some("Alice".to_string()),
        };
        let json = serde_json::to_string(&avail).unwrap();
        assert!(json.contains("Alice"));
        assert!(json.contains(&user_id.to_string()));
    }

    #[test]
    fn seat_booking_serializes_camel_case() {
        let booking = SeatBooking {
            id: Uuid::new_v4(),
            seat_id: Uuid::new_v4(),
            user_id: Uuid::new_v4(),
            membership_id: Uuid::new_v4(),
            shift: "MORNING".to_string(),
            booking_date: NaiveDate::from_ymd_opt(2025, 1, 1).unwrap(),
            end_date: NaiveDate::from_ymd_opt(2025, 1, 31).unwrap(),
            status: "ACTIVE".to_string(),
            created_at: None,
        };
        let json = serde_json::to_string(&booking).unwrap();
        assert!(json.contains("seatId"), "Expected camelCase seatId");
        assert!(json.contains("userId"), "Expected camelCase userId");
        assert!(json.contains("membershipId"), "Expected camelCase membershipId");
        assert!(json.contains("bookingDate"), "Expected camelCase bookingDate");
        assert!(json.contains("endDate"), "Expected camelCase endDate");
    }
}
