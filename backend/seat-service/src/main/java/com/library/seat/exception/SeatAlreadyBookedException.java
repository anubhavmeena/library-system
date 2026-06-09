package com.library.seat.exception;

public class SeatAlreadyBookedException extends RuntimeException {
    public SeatAlreadyBookedException(String seatNumber, String shift) {
        super("Seat " + seatNumber + " is already booked for the "
                + shift + " shift. Please choose another seat.");
    }
}