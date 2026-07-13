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

    func updateStudentPhoto(id: String, photoData: Data) async throws -> String {
        try await api.uploadMultipart(path: "admin/students/\(id)/photo", fieldName: "file",
                                      fileName: "student_\(id).jpg", mimeType: "image/jpeg",
                                      data: photoData, token: token)
    }

    func updateStudent(id: String, req: UpdateStudentRequest) async throws -> StudentDetail {
        try await api.request(.updateStudent(id: id, req: req), token: token)
    }

    func getStudentsWithPendingFees() async throws -> [StudentDetail] {
        try await api.request(.getStudentsWithPendingFees, token: token)
    }

    func getStudentsWithOrphanedSeats() async throws -> [StudentDetail] {
        try await api.request(.getStudentsWithOrphanedSeats, token: token)
    }

    func getStudentSeatHistory(userId: String) async throws -> [SeatHistoryEntry] {
        try await api.request(.getStudentSeatHistory(userId: userId), token: token)
    }

    func deleteStudent(id: String) async throws {
        try await api.requestVoid(.deleteStudent(id: id), token: token)
    }

    func importStudentsCSV(data: Data, fileName: String, mimeType: String) async throws -> ImportResult {
        try await api.uploadMultipartDecoded(path: "admin/students/import", fieldName: "file",
                                             fileName: fileName, mimeType: mimeType,
                                             data: data, token: token)
    }

    func importSingleStudentWithPhoto(name: String, phone: String, photoData: Data) async throws -> ManualImportWithPhotoResponse {
        try await api.uploadMultipartWithFields(
            path: "admin/students/import/single-with-photo",
            fields: ["name": name, "phone": phone],
            fileFieldName: "photo", fileName: "student.jpg", mimeType: "image/jpeg",
            fileData: photoData, token: token)
    }

    func clearPendingFees(userId: String, amountCleared: Double, paymentMode: String? = nil) async throws {
        let req = ClearAmountRequest(amountCleared: amountCleared, paymentMode: paymentMode)
        try await api.requestVoid(.clearPendingFees(userId: userId, req: req), token: token)
    }

    func getStudentsInGraceWithDues() async throws -> [StudentDetail] {
        try await api.request(.getStudentsInGraceWithDues, token: token)
    }

    func clearDues(membershipId: String, amountCleared: Double, paymentMode: String? = nil) async throws {
        let req = ClearAmountRequest(amountCleared: amountCleared, paymentMode: paymentMode)
        try await api.requestVoid(.clearDues(membershipId: membershipId, req: req), token: token)
    }

    func releaseSeat(membershipId: String, notifyStudent: Bool) async throws {
        let req = ReleaseSeatRequest(notifyStudent: notifyStudent)
        try await api.requestVoid(.releaseSeat(membershipId: membershipId, req: req), token: token)
    }

    func markPending(membershipId: String, pendingAmount: Double) async throws {
        let req = MarkPendingRequest(pendingAmount: pendingAmount)
        try await api.requestVoid(.markPending(membershipId: membershipId, req: req), token: token)
    }

    func markGrace(membershipId: String) async throws {
        try await api.requestVoid(.markGrace(membershipId: membershipId), token: token)
    }

    func renewSeat(membershipId: String) async throws {
        try await api.requestVoid(.renewSeat(membershipId: membershipId), token: token)
    }

    func runExpiryCheck() async throws -> String {
        try await api.request(.runExpiryCheck, token: token)
    }

    func getSeatHistory(seatNumber: String) async throws -> [SeatHistoryEntry] {
        try await api.request(.getSeatHistory(seatNumber: seatNumber), token: token)
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

    // admin-service's MembershipDto for this endpoint has no planType field (and
    // none of the other callers of this method use the returned membership either
    // — the response body is only ever a success/failure signal in practice), so
    // decoding it against the full Membership model (which requires planType)
    // failed every time: the membership was created successfully server-side, but
    // the client-side decode of the response threw, surfacing as a "Data error"
    // instead of the actual success message. requestVoid sidesteps the mismatch
    // entirely rather than making Membership.planType optional for every other
    // caller that genuinely relies on it being present.
    func createCashMembership(studentId: String, planId: String, seatNumber: String,
                              shift: String, startDate: String,
                              paidAmount: Double?, pendingAmount: Double?,
                              paymentMode: String? = nil) async throws {
        let req = CreateCashMembershipRequest(studentId: studentId, planId: planId,
                                              seatNumber: seatNumber, shift: shift,
                                              startDate: startDate, paidAmount: paidAmount,
                                              pendingAmount: pendingAmount, paymentMode: paymentMode)
        try await api.requestVoid(.createCashMembership(req), token: token)
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

    func sendGraceDuesReminders(userIds: [String]) async throws {
        let req = SendReminderRequest(userIds: userIds)
        try await api.requestVoid(.sendGraceDuesReminders(req), token: token)
    }

    // MARK: - Notification Settings
    func getNotificationSettings() async throws -> [NotificationSetting] {
        try await api.request(.getNotificationSettings, token: token)
    }

    func updateNotificationSetting(key: String, sendToStudent: Bool, sendToAdmin: Bool,
                                   hindiEnabled: Bool, hindiTextStudent: String?,
                                   hindiTextAdmin: String?) async throws -> NotificationSetting {
        let req = UpdateNotificationSettingRequest(sendToStudent: sendToStudent, sendToAdmin: sendToAdmin,
                                                   hindiEnabled: hindiEnabled,
                                                   hindiTextStudent: hindiTextStudent,
                                                   hindiTextAdmin: hindiTextAdmin)
        return try await api.request(.updateNotificationSetting(key: key, req: req), token: token)
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

    func getPaymentBreakdown(from: String, to: String) async throws -> [PaymentBreakdownItem] {
        try await api.request(.getPaymentBreakdown(from: from, to: to), token: token)
    }

    // MARK: - App Settings
    func getAppSettings() async throws -> AppSettings {
        try await api.request(.getAppSettings, token: token)
    }

    func saveAppSettings(wifiName: String?, wifiPassword: String?, upiId: String?, graceDays: Int,
                        convenienceFee: Double, waterTankerRate: Double, couponsEnabled: Bool) async throws -> AppSettings {
        let req = SaveAppSettingsRequest(wifiName: wifiName, wifiPassword: wifiPassword, upiId: upiId,
                                         graceDays: graceDays, convenienceFee: convenienceFee,
                                         waterTankerRate: waterTankerRate, couponsEnabled: couponsEnabled)
        return try await api.request(.saveAppSettings(req), token: token)
    }

    // MARK: - Coupons (admin CRUD)
    func getCoupons() async throws -> [Coupon] {
        try await api.request(.getAdminCoupons, token: token)
    }

    func createCoupon(code: String?, discountPercent: Int) async throws -> Coupon {
        let req = CreateCouponRequest(code: code, discountPercent: discountPercent)
        return try await api.request(.createCoupon(req), token: token)
    }

    func updateCoupon(id: String, discountPercent: Int? = nil, isActive: Bool? = nil) async throws -> Coupon {
        let req = UpdateCouponRequest(discountPercent: discountPercent, isActive: isActive)
        return try await api.request(.updateCoupon(id: id, req: req), token: token)
    }

    func deleteCoupon(id: String) async throws {
        try await api.requestVoid(.deleteCoupon(id: id), token: token)
    }

    // MARK: - UPI Pay Links & Payment Verification
    func createPayLink(studentId: String, amount: Double) async throws -> String {
        let req = PayLinkRequest(studentId: studentId, amount: amount)
        let res: PayLinkResponse = try await api.request(.createPayLink(req), token: token)
        return res.link
    }

    func getPaymentClaims(status: String? = nil) async throws -> [PaymentClaim] {
        try await api.request(.getPaymentClaims(status: status), token: token)
    }

    func reviewPaymentClaim(id: String, status: String) async throws -> PaymentClaim {
        let req = PaymentClaimReviewRequest(status: status)
        return try await api.request(.reviewPaymentClaim(id: id, req: req), token: token)
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
