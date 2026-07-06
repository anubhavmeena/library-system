import Foundation

struct SeatRepository {
    static let shared = SeatRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    // GET /api/seats/availability returns an envelope object (SeatAvailabilityDto:
    // shift/date/totalSeats/seats/seatsByRow), not a bare array — decoding straight
    // to [Seat] was a type mismatch that failed on every call. Every caller wraps
    // this in `try?`, so the failure was silent: it always fell back to an empty
    // array, which every picker's own placeholder-synthesis logic then rendered as
    // "every seat available" — no seat could ever show as booked.
    func getAvailability(shift: String, date: String? = nil) async throws -> [Seat] {
        let response: SeatAvailability = try await api.request(.getSeatAvailability(shift: shift, date: date), token: token)
        return response.seats
    }

    func bookSeat(seatNumber: String, membershipId: String, shift: String,
                  startDate: String, endDate: String) async throws {
        let req = BookSeatRequest(seatNumber: seatNumber, membershipId: membershipId,
                                  shift: shift, startDate: startDate, endDate: endDate)
        let _: AnyCodable = try await api.request(.bookSeat(req), token: token)
    }
}
