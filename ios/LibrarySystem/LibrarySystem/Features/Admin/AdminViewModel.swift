import Foundation

@MainActor
final class AdminViewModel: ObservableObject {
    @Published var stats              = AdminStats()
    @Published var students:          [StudentSummary] = []
    @Published var studentsTotal:     Int = 0
    @Published var selectedStudent:   StudentDetail?
    @Published var studentPayments:   [PaymentHistoryItem] = []
    @Published var seatMap:           SeatMapDto?
    @Published var expiring:          [ReminderStudent] = []
    @Published var pendingFeeStudents: [StudentDetail] = []
    @Published var graceDuesStudents: [StudentDetail] = []
    @Published var orphanedSeatStudents: [StudentDetail] = []
    @Published var notificationSettings: [NotificationSetting] = []
    @Published var seatHistory: [SeatHistoryEntry] = []
    @Published var studentSeatHistory: [SeatHistoryEntry] = []
    @Published var paymentBreakdown: [PaymentBreakdownItem] = []
    @Published var appSettings = AppSettings()
    @Published var appSettingsLoaded = false
    @Published var importResult: ImportResult?
    @Published var studentDeleted = false
    @Published var feedback:          [FeedbackItem] = []
    @Published var plans:             [Plan] = []
    @Published var broadcastHistory:  [BroadcastHistory] = []
    @Published var revenueReport:     RevenueReport = RevenueReport()
    @Published var dailyPayments:     [DailyPayment] = []
    @Published var expense:           MonthlyExpense = MonthlyExpense()
    @Published var inboxMessages:     [InboxSummary] = []
    @Published var selectedMessage:   InboxMessage?

    @Published var isLoading  = false
    @Published var error:      String?
    @Published var successMsg: String?

    private let repo = AdminRepository.shared

    // MARK: - Dashboard
    func loadStats() {
        Task {
            do { stats = try await repo.getStats() }
            catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Students
    func loadStudents(page: Int = 0, size: Int = 20, status: String? = nil,
                      membershipStatus: String? = nil, search: String? = nil) {
        isLoading = true
        Task {
            do {
                let result = try await repo.getStudents(page: page, size: size, status: status,
                                                        membershipStatus: membershipStatus,
                                                        search: search)
                students = result.students
                studentsTotal = result.total
            }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func loadStudentDetail(id: String) {
        isLoading = true
        Task {
            do { selectedStudent = try await repo.getStudentDetail(id: id) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func loadStudentPayments(userId: String) {
        Task {
            do { studentPayments = try await repo.getStudentPayments(userId: userId) }
            catch { self.error = error.localizedDescription }
        }
    }

    func toggleStudentStatus(id: String, active: Bool) {
        Task {
            do {
                try await repo.toggleStudentStatus(id: id, active: active)
                successMsg = "Status updated"
                loadStudentDetail(id: id)
            } catch { self.error = error.localizedDescription }
        }
    }

    func updateStudent(id: String, req: UpdateStudentRequest) {
        Task {
            do {
                let updated = try await repo.updateStudent(id: id, req: req)
                selectedStudent = updated
                successMsg = "Profile updated"
            } catch { self.error = error.localizedDescription }
        }
    }

    func changeSeat(membershipId: String, seatNumber: String) {
        Task {
            do {
                try await repo.changeSeat(membershipId: membershipId, seatNumber: seatNumber)
                successMsg = "Seat changed to \(seatNumber)"
            } catch { self.error = error.localizedDescription }
        }
    }

    func updateMembershipPlan(membershipId: String, planId: String) {
        Task {
            do {
                try await repo.updateMembershipPlan(membershipId: membershipId, planId: planId)
                successMsg = "Plan updated"
            } catch { self.error = error.localizedDescription }
        }
    }

    func loadPendingFeeStudents() {
        isLoading = true
        Task {
            do { pendingFeeStudents = try await repo.getStudentsWithPendingFees() }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func clearPendingFees(userId: String, amountCleared: Double) {
        Task {
            do {
                try await repo.clearPendingFees(userId: userId, amountCleared: amountCleared)
                successMsg = "₹\(Int(amountCleared)) cleared"
                loadPendingFeeStudents()
                if selectedStudent?.id == userId { loadStudentDetail(id: userId) }
            } catch { self.error = error.localizedDescription }
        }
    }

    func loadGraceDuesStudents() {
        isLoading = true
        Task {
            do { graceDuesStudents = try await repo.getStudentsInGraceWithDues() }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func clearDues(membershipId: String, amountCleared: Double, userId: String) {
        Task {
            do {
                try await repo.clearDues(membershipId: membershipId, amountCleared: amountCleared)
                successMsg = "₹\(Int(amountCleared)) cleared"
                loadGraceDuesStudents()
                if selectedStudent?.id == userId { loadStudentDetail(id: userId) }
            } catch { self.error = error.localizedDescription }
        }
    }

    func releaseSeat(membershipId: String, notifyStudent: Bool, userId: String) {
        Task {
            do {
                try await repo.releaseSeat(membershipId: membershipId, notifyStudent: notifyStudent)
                successMsg = "Seat released\(notifyStudent ? " — student notified" : "")"
                loadStudentDetail(id: userId)
            } catch { self.error = error.localizedDescription }
        }
    }

    func markPending(membershipId: String, pendingAmount: Double, userId: String) {
        Task {
            do {
                try await repo.markPending(membershipId: membershipId, pendingAmount: pendingAmount)
                successMsg = "Marked as Pending"
                loadStudentDetail(id: userId)
            } catch { self.error = error.localizedDescription }
        }
    }

    func markGrace(membershipId: String, userId: String) {
        Task {
            do {
                try await repo.markGrace(membershipId: membershipId)
                successMsg = "Marked as Grace"
                loadStudentDetail(id: userId)
            } catch { self.error = error.localizedDescription }
        }
    }

    func renewSeat(membershipId: String, userId: String) {
        Task {
            do {
                try await repo.renewSeat(membershipId: membershipId)
                successMsg = "Seat renewed"
                loadStudentDetail(id: userId)
            } catch { self.error = error.localizedDescription }
        }
    }

    func deleteStudent(id: String) {
        Task {
            do {
                try await repo.deleteStudent(id: id)
                successMsg = "Student deleted"
                studentDeleted = true
            } catch { self.error = error.localizedDescription }
        }
    }

    func loadOrphanedSeatStudents() {
        Task {
            do { orphanedSeatStudents = try await repo.getStudentsWithOrphanedSeats() }
            catch { self.error = error.localizedDescription }
        }
    }

    func loadSeatHistory(seatNumber: String) {
        Task {
            do { seatHistory = try await repo.getSeatHistory(seatNumber: seatNumber) }
            catch { self.error = error.localizedDescription }
        }
    }

    func loadStudentSeatHistory(userId: String) {
        Task {
            do { studentSeatHistory = try await repo.getStudentSeatHistory(userId: userId) }
            catch { self.error = error.localizedDescription }
        }
    }

    func runExpiryCheck() {
        isLoading = true
        Task {
            do { successMsg = try await repo.runExpiryCheck() }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Bulk Import
    func importStudentsCSV(data: Data, fileName: String, mimeType: String) {
        isLoading = true
        Task {
            do { importResult = try await repo.importStudentsCSV(data: data, fileName: fileName, mimeType: mimeType) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - App Settings
    func loadAppSettings() {
        Task {
            do { appSettings = try await repo.getAppSettings() }
            catch { self.error = error.localizedDescription }
            appSettingsLoaded = true
        }
    }

    func saveAppSettings(wifiName: String?, wifiPassword: String?, graceDays: Int,
                        convenienceFee: Double, waterTankerRate: Double) {
        isLoading = true
        Task {
            do {
                appSettings = try await repo.saveAppSettings(
                    wifiName: wifiName, wifiPassword: wifiPassword, graceDays: graceDays,
                    convenienceFee: convenienceFee, waterTankerRate: waterTankerRate)
                successMsg = "Settings saved"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Seat Map
    func loadSeatMap(shift: String, date: String? = nil) {
        isLoading = true
        Task {
            do { seatMap = try await repo.getAdminSeatMap(shift: shift, date: date) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Reminders
    func loadExpiring(withinDays: Int = 7) {
        isLoading = true
        Task {
            do { expiring = try await repo.getExpiringMemberships(withinDays: withinDays) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func sendReminders(userIds: [String]) {
        isLoading = true
        Task {
            do {
                try await repo.sendReminders(userIds: userIds)
                successMsg = "Reminders sent"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func sendPendingFeeReminders(userIds: [String]) {
        isLoading = true
        Task {
            do {
                try await repo.sendPendingFeeReminders(userIds: userIds)
                successMsg = "Pending fee reminders sent"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func sendGraceDuesReminders(userIds: [String]) {
        isLoading = true
        Task {
            do {
                try await repo.sendGraceDuesReminders(userIds: userIds)
                successMsg = "Grace dues reminders sent"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Notification Settings
    func loadNotificationSettings() {
        Task {
            do { notificationSettings = try await repo.getNotificationSettings() }
            catch { self.error = error.localizedDescription }
        }
    }

    func updateNotificationSetting(key: String, sendToStudent: Bool, sendToAdmin: Bool,
                                   hindiEnabled: Bool, hindiTextStudent: String?, hindiTextAdmin: String?) {
        Task {
            do {
                let updated = try await repo.updateNotificationSetting(
                    key: key, sendToStudent: sendToStudent, sendToAdmin: sendToAdmin,
                    hindiEnabled: hindiEnabled, hindiTextStudent: hindiTextStudent,
                    hindiTextAdmin: hindiTextAdmin)
                if let idx = notificationSettings.firstIndex(where: { $0.notificationKey == key }) {
                    notificationSettings[idx] = updated
                }
                successMsg = "Settings saved"
            } catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Feedback
    func loadFeedback() {
        Task {
            do { feedback = try await repo.getAllFeedback() }
            catch { self.error = error.localizedDescription }
        }
    }

    func updateFeedback(id: String, status: String, adminNotes: String?) {
        Task {
            do {
                let updated = try await repo.updateFeedback(id: id, status: status, adminNotes: adminNotes)
                if let idx = feedback.firstIndex(where: { $0.id == id }) {
                    feedback[idx] = updated
                }
                successMsg = "Feedback updated"
            } catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Direct Message
    func sendMessageToStudent(id: String, message: String) {
        Task {
            do {
                try await repo.sendMessageToStudent(id: id, message: message)
                successMsg = "Message sent"
            } catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Broadcast
    func sendBroadcast(message: String, targetGroup: String) {
        isLoading = true
        Task {
            do {
                try await repo.sendBroadcast(message: message, targetGroup: targetGroup)
                successMsg = "Broadcast sent"
                loadBroadcastHistory()
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func loadBroadcastHistory() {
        Task {
            do { broadcastHistory = try await repo.getBroadcastHistory() }
            catch { /* history is non-critical */ }
        }
    }

    // MARK: - Create Membership
    func loadPlans() {
        Task {
            do { plans = try await repo.getPlans() }
            catch { self.error = error.localizedDescription }
        }
    }

    func createCashMembership(studentId: String, planId: String, seatNumber: String,
                              shift: String, startDate: String,
                              paidAmount: Double?, pendingAmount: Double?) {
        isLoading = true
        Task {
            do {
                try await repo.createCashMembership(
                    studentId: studentId, planId: planId, seatNumber: seatNumber,
                    shift: shift, startDate: startDate,
                    paidAmount: paidAmount, pendingAmount: pendingAmount)
                successMsg = "Membership created"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Revenue
    func loadRevenueReport(from: String, to: String) {
        isLoading = true
        Task {
            do {
                revenueReport = try await repo.getRevenueReport(from: from, to: to)
                paymentBreakdown = try await repo.getPaymentBreakdown(from: from, to: to)
            }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func loadDailyPayments(date: String) {
        isLoading = true
        Task {
            do { dailyPayments = try await repo.getDailyPayments(date: date) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Expenses
    func loadExpenses(year: Int? = nil, month: Int? = nil) {
        isLoading = true
        Task {
            do { expense = try await repo.getExpenses(year: year, month: month) }
            catch { /* no expense record is normal */ }
            isLoading = false
        }
    }

    func saveExpenses(year: Int, month: Int, waterTankerQty: Int, waterTankerPrice: Double,
                      electricityBill: Double, internetBill: Double, miscItems: [MiscItemRequest]) {
        isLoading = true
        Task {
            do {
                expense = try await repo.saveExpenses(
                    year: year, month: month, waterTankerQty: waterTankerQty,
                    waterTankerPrice: waterTankerPrice, electricityBill: electricityBill,
                    internetBill: internetBill, miscItems: miscItems)
                successMsg = "Expenses saved"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Inbox
    func loadInbox() {
        isLoading = true
        Task {
            do { inboxMessages = try await repo.getInbox() }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func loadInboxMessage(_ number: Int) {
        isLoading = true
        Task {
            do { selectedMessage = try await repo.getInboxMessage(number) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func replyToMessage(_ number: Int, body: String) {
        isLoading = true
        Task {
            do {
                try await repo.replyToMessage(number, body: body)
                successMsg = "Reply sent"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func deleteInboxMessage(_ number: Int) {
        Task {
            do {
                try await repo.deleteInboxMessage(number)
                inboxMessages.removeAll { $0.messageNumber == number }
                successMsg = "Message deleted"
            } catch { self.error = error.localizedDescription }
        }
    }

    func clearError()   { error = nil }
    func clearSuccess() { successMsg = nil }
}
