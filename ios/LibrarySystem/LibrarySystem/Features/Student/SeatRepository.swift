import Foundation

struct SeatRepository {
    static let shared = SeatRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    func getAvailability(shift: String, date: String? = nil) async throws -> [Seat] {
        try await api.request(.getSeatAvailability(shift: shift, date: date), token: token)
    }

    func bookSeat(seatNumber: String, membershipId: String, shift: String,
                  startDate: String, endDate: String) async throws {
        let req = BookSeatRequest(seatNumber: seatNumber, membershipId: membershipId,
                                  shift: shift, startDate: startDate, endDate: endDate)
        let _: AnyCodable = try await api.request(.bookSeat(req), token: token)
    }
}
