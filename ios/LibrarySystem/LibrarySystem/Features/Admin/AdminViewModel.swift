import Foundation

@MainActor
final class AdminViewModel: ObservableObject {
    @Published var stats          = AdminStats()
    @Published var students:        [StudentSummary] = []
    @Published var selectedStudent: StudentDetail?
    @Published var seatMap:         SeatMapDto?
    @Published var expiring:        [ReminderStudent] = []
    @Published var feedback:        [FeedbackItem] = []
    @Published var plans:           [Plan] = []

    @Published var isLoading  = false
    @Published var error:       String?
    @Published var successMsg:  String?

    private let repo = AdminRepository.shared

    // MARK: - Dashboard
    func loadStats() {
        Task {
            do { stats = try await repo.getStats() }
            catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Students
    func loadStudents(page: Int = 0, status: String? = nil,
                      membershipStatus: String? = nil, search: String? = nil) {
        isLoading = true
        Task {
            do { students = try await repo.getStudents(page: page, status: status,
                                                        membershipStatus: membershipStatus,
                                                        search: search) }
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

    func toggleStudentStatus(id: String, active: Bool) {
        Task {
            do {
                try await repo.toggleStudentStatus(id: id, active: active)
                successMsg = "Status updated"
                loadStudentDetail(id: id)
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

    // MARK: - Broadcast
    func sendBroadcast(message: String, targetGroup: String) {
        isLoading = true
        Task {
            do {
                try await repo.sendBroadcast(message: message, targetGroup: targetGroup)
                successMsg = "Broadcast sent"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Create Membership
    func loadPlans() {
        Task {
            do { plans = try await repo.getPlans() }
            catch { self.error = error.localizedDescription }
        }
    }

    func createMembership(userId: String, planId: String, seatNumber: String,
                          shift: String, startDate: String) {
        isLoading = true
        Task {
            do {
                _ = try await repo.createMembership(userId: userId, planId: planId,
                                                     seatNumber: seatNumber,
                                                     shift: shift, startDate: startDate)
                successMsg = "Membership created"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func clearError()   { error = nil }
    func clearSuccess() { successMsg = nil }
}
