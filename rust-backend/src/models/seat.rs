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
