import Foundation

struct AdminRepository {
    static let shared = AdminRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    func getStats() async throws -> AdminStats {
        try await api.request(.getAdminStats, token: token)
    }

    func getStudents(page: Int = 0, size: Int = 20, status: String? = nil,
                     membershipStatus: String? = nil, search: String? = nil) async throws -> [StudentSummary] {
        try await api.request(.getStudents(page: page, size: size, status: status,
                                           membershipStatus: membershipStatus, search: search),
                              token: token)
    }

    func getStudentDetail(id: String) async throws -> StudentDetail {
        try await api.request(.getStudentDetail(id: id), token: token)
    }

    func toggleStudentStatus(id: String, active: Bool) async throws {
        let req = ToggleStatusRequest(active: active)
        let _: AnyCodable = try await api.request(.toggleStudentStatus(id: id, req: req), token: token)
    }

    func changeSeat(membershipId: String, seatNumber: String) async throws {
        let req = ChangeSeatRequest(seatNumber: seatNumber)
        let _: AnyCodable = try await api.request(.changeSeat(membershipId: membershipId, req: req), token: token)
    }

    func getExpiringMemberships(withinDays: Int = 7) async throws -> [ReminderStudent] {
        try await api.request(.getExpiringMemberships(withinDays: withinDays), token: token)
    }

    func sendReminders(userIds: [String]) async throws {
        let req = SendReminderRequest(userIds: userIds)
        let _: AnyCodable = try await api.request(.sendReminders(req), token: token)
    }

    func getAdminSeatMap(shift: String, date: String? = nil) async throws -> SeatMapDto {
        try await api.request(.getAdminSeatMap(shift: shift, date: date), token: token)
    }

    func getAllFeedback() async throws -> [FeedbackItem] {
        try await api.request(.getAllFeedback, token: token)
    }

    func updateFeedback(id: String, status: String, adminNotes: String?) async throws -> FeedbackItem {
        let req = UpdateFeedbackRequest(status: status, adminNotes: adminNotes)
        return try await api.request(.updateFeedback(id: id, req: req), token: token)
    }

    func sendBroadcast(message: String, targetGroup: String = "ALL") async throws {
        let req = BroadcastRequest(message: message, targetGroup: targetGroup)
        let _: AnyCodable = try await api.request(.sendBroadcast(req), token: token)
    }

    func createMembership(userId: String, planId: String, seatNumber: String,
                          shift: String, startDate: String) async throws -> Membership {
        let req = CreateMembershipRequest(userId: userId, planId: planId, seatNumber: seatNumber,
                                          shift: shift, startDate: startDate)
        return try await api.request(.createMembership(req), token: token)
    }

    func getPlans() async throws -> [Plan] {
        try await api.request(.getPlans)
    }
}
