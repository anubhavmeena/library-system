import Foundation

struct AdminRepository {
    static let shared = AdminRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    // MARK: - Dashboard
    func getStats() async throws -> AdminStats {
        try await api.request(.getAdminStats, token: token)
    }

    // MARK: - Students
    func getStudents(page: Int = 0, size: Int = 20, status: String? = nil,
                     membershipStatus: String? = nil, search: String? = nil) async throws -> StudentListResponse {
        try await api.request(.getStudents(page: page, size: size, status: status,
                                           membershipStatus: membershipStatus, search: search),
                              token: token)
    }

    func getStudentDetail(id: String) async throws -> StudentDetail {
        try await api.request(.getStudentDetail(id: id), token: token)
    }

    func getStudentPayments(userId: String) async throws -> [PaymentHistoryItem] {
        try await api.request(.getStudentPayments(userId: userId), token: token)
    }

    func toggleStudentStatus(id: String, active: Bool) async throws {
        let req = ToggleStatusRequest(active: active)
        try await api.requestVoid(.toggleStudentStatus(id: id, req: req), token: token)
    }

    func updateStudent(id: String, req: UpdateStudentRequest) async throws -> StudentDetail {
        try await api.request(.updateStudent(id: id, req: req), token: token)
    }

    func getStudentsWithPendingFees() async throws -> [StudentDetail] {
        try await api.request(.getStudentsWithPendingFees, token: token)
    }

    func clearPendingFees(userId: String) async throws {
        try await api.requestVoid(.clearPendingFees(userId: userId), token: token)
    }

    // MARK: - Memberships & Seats
    func changeSeat(membershipId: String, seatNumber: String) async throws {
        let req = ChangeSeatRequest(seatNumber: seatNumber)
        try await api.requestVoid(.changeSeat(membershipId: membershipId, req: req), token: token)
    }

    func updateMembershipPlan(membershipId: String, planId: String) async throws {
        let req = UpdateMembershipPlanRequest(planId: planId)
        try await api.requestVoid(.updateMembershipPlan(membershipId: membershipId, req: req), token: token)
    }

    func getExpiringMemberships(withinDays: Int = 7) async throws -> [ReminderStudent] {
        try await api.request(.getExpiringMemberships(withinDays: withinDays), token: token)
    }

    func createCashMembership(studentId: String, planId: String, seatNumber: String,
                              shift: String, startDate: String,
                              paidAmount: Double?, pendingAmount: Double?) async throws -> Membership {
        let req = CreateCashMembershipRequest(studentId: studentId, planId: planId,
                                              seatNumber: seatNumber, shift: shift,
                                              startDate: startDate, paidAmount: paidAmount,
                                              pendingAmount: pendingAmount)
        return try await api.request(.createCashMembership(req), token: token)
    }

    func getAdminSeatMap(shift: String, date: String? = nil) async throws -> SeatMapDto {
        try await api.request(.getAdminSeatMap(shift: shift, date: date), token: token)
    }

    // MARK: - Reminders & Broadcast
    func sendReminders(userIds: [String]) async throws {
        let req = SendReminderRequest(userIds: userIds)
        try await api.requestVoid(.sendReminders(req), token: token)
    }

    func sendPendingFeeReminders(userIds: [String]) async throws {
        let req = SendReminderRequest(userIds: userIds)
        try await api.requestVoid(.sendPendingFeeReminders(req), token: token)
    }

    func sendBroadcast(message: String, targetGroup: String = "ALL") async throws {
        let req = BroadcastRequest(message: message, targetGroup: targetGroup)
        try await api.requestVoid(.sendBroadcast(req), token: token)
    }

    func sendMessageToStudent(id: String, message: String) async throws {
        let req = BroadcastRequest(message: message, targetGroup: "ALL")
        try await api.requestVoid(.sendMessageToStudent(id, req), token: token)
    }

    func getBroadcastHistory() async throws -> [BroadcastHistory] {
        try await api.request(.getBroadcastHistory, token: token)
    }

    // MARK: - Feedback
    func getAllFeedback() async throws -> [FeedbackItem] {
        try await api.request(.getAllFeedback, token: token)
    }

    func updateFeedback(id: String, status: String, adminNotes: String?) async throws -> FeedbackItem {
        let req = UpdateFeedbackRequest(status: status, adminNotes: adminNotes)
        return try await api.request(.updateFeedback(id: id, req: req), token: token)
    }

    // MARK: - Revenue Reports
    func getRevenueReport(from: String, to: String) async throws -> RevenueReport {
        try await api.request(.getRevenueReport(from: from, to: to), token: token)
    }

    func getDailyPayments(date: String) async throws -> [DailyPayment] {
        try await api.request(.getDailyPayments(date: date), token: token)
    }

    // MARK: - Expenses
    func getExpenses(year: Int? = nil, month: Int? = nil) async throws -> MonthlyExpense {
        try await api.request(.getExpenses(year: year, month: month), token: token)
    }

    func saveExpenses(year: Int, month: Int, waterTankerQty: Int, waterTankerPrice: Double,
                      electricityBill: Double, internetBill: Double,
                      miscItems: [MiscItemRequest]) async throws -> MonthlyExpense {
        let req = SaveExpenseRequest(year: year, month: month, waterTankerQty: waterTankerQty,
                                     waterTankerPrice: waterTankerPrice, electricityBill: electricityBill,
                                     internetBill: internetBill, miscItems: miscItems)
        return try await api.request(.saveExpenses(req), token: token)
    }

    // MARK: - Inbox
    func getInbox() async throws -> [InboxSummary] {
        try await api.request(.getInbox, token: token)
    }

    func getInboxMessage(_ number: Int) async throws -> InboxMessage {
        try await api.request(.getInboxMessage(number), token: token)
    }

    func replyToMessage(_ number: Int, body: String) async throws {
        let req = ReplyRequest(body: body)
        try await api.requestVoid(.replyToMessage(number, req: req), token: token)
    }

    func deleteInboxMessage(_ number: Int) async throws {
        try await api.requestVoid(.deleteInboxMessage(number), token: token)
    }

    // MARK: - Plans
    func getPlans() async throws -> [Plan] {
        try await api.request(.getPlans)
    }
}
