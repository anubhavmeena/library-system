import Foundation
import Combine

@MainActor
final class StudentViewModel: ObservableObject {
    // Dashboard
    @Published var membership: Membership?
    @Published var queuedMembership: Membership?
    @Published var membershipHistory: [Membership] = []

    // Plans & Booking
    @Published var plans: [Plan] = []
    @Published var selectedPlan: Plan?
    @Published var selectedShift = "MORNING"
    @Published var seats: [Seat] = []
    @Published var selectedSeat: String?
    @Published var pendingOrder: PaymentOrder?

    // Profile
    @Published var profile: User?
    @Published var photoUrl: String?
    @Published var aadhaarUrl: String?

    // Feedback
    @Published var myFeedback: [FeedbackItem] = []

    // Payment history
    @Published var myPayments: [StudentPayment] = []

    // Gallery
    @Published var galleryPhotos: [GalleryPhoto] = []

    // Contact Admin
    @Published var adminContact: AdminContact?
    @Published var callAdminSent = false

    // UI State
    @Published var isLoading = false
    @Published var error: String?
    @Published var successMsg: String?
    @Published var idCardData: Data?
    @Published var showIdCardShare = false

    private let membershipRepo = MembershipRepository.shared
    private let userRepo       = UserRepository.shared
    private let seatRepo       = SeatRepository.shared

    // MARK: - Dashboard

    func loadDashboard() {
        isLoading = true; error = nil
        Task {
            do { membership = try await membershipRepo.getMyMembership() }
            catch { /* no active membership is a normal 404 */ }
            queuedMembership = await membershipRepo.getQueuedMembership()
            isLoading = false
        }
    }

    func loadPlans() {
        Task {
            do { plans = try await membershipRepo.getPlans() }
            catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Seats

    func loadSeats(shift: String, date: String? = nil) {
        isLoading = true
        Task {
            do { seats = try await seatRepo.getAvailability(shift: shift, date: date) }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func selectSeat(_ seatNumber: String) {
        selectedSeat = seatNumber
    }

    // MARK: - Payment

    func startPayment(planId: String, seatNumber: String, shift: String) {
        isLoading = true; error = nil
        Task {
            do {
                pendingOrder = try await membershipRepo.createOrder(
                    planId: planId, seatNumber: seatNumber, shift: shift)
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func verifyPayment(gatewayOrderId: String, gatewayPaymentId: String,
                       signature: String, membershipId: String) {
        isLoading = true
        Task {
            do {
                membership = try await membershipRepo.verifyPayment(
                    gatewayOrderId: gatewayOrderId,
                    gatewayPaymentId: gatewayPaymentId,
                    signature: signature,
                    membershipId: membershipId
                )
                resetBooking()
                successMsg = "Payment successful!"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func devVerifyPayment(membershipId: String) {
        verifyPayment(gatewayOrderId: pendingOrder?.orderId ?? "",
                      gatewayPaymentId: "dev_pay_\(membershipId)",
                      signature: "dev_sig",
                      membershipId: membershipId)
    }

    func resetBooking() {
        selectedPlan = nil
        selectedShift = "MORNING"
        selectedSeat = nil
        seats = []
        pendingOrder = nil
    }

    // MARK: - Profile

    func loadProfile() {
        Task {
            do {
                profile = try await userRepo.getProfile()
                photoUrl = profile?.photoUrl
                aadhaarUrl = profile?.aadhaarUrl
            } catch { self.error = error.localizedDescription }
        }
    }

    func updateProfile(name: String, fatherName: String?, address: String?,
                       gender: String?, dateOfBirth: String?, email: String?) {
        isLoading = true
        Task {
            do {
                profile = try await userRepo.updateProfile(
                    name: name, fatherName: fatherName, address: address,
                    gender: gender, dateOfBirth: dateOfBirth, email: email)
                successMsg = "Profile updated"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func uploadPhoto(data: Data) {
        Task {
            do {
                let url = try await userRepo.uploadPhoto(data: data)
                photoUrl = url
                successMsg = "Photo updated"
            } catch { self.error = error.localizedDescription }
        }
    }

    func uploadAadhaar(data: Data) {
        Task {
            do {
                let url = try await userRepo.uploadAadhaar(data: data)
                aadhaarUrl = url
                successMsg = "Aadhaar uploaded"
            } catch { self.error = error.localizedDescription }
        }
    }

    func downloadIdCard() {
        Task {
            do {
                idCardData = try await membershipRepo.downloadIdCard()
                showIdCardShare = true
            } catch { self.error = error.localizedDescription }
        }
    }

    // MARK: - Membership History

    func loadMembershipHistory() {
        Task {
            do { membershipHistory = try await membershipRepo.getMembershipHistory() }
            catch { self.error = error.localizedDescription }
        }
    }

    func loadMyPayments() {
        Task {
            do { myPayments = try await membershipRepo.getMyPayments() }
            catch { self.error = error.localizedDescription }
        }
    }

    func loadGallery() {
        Task {
            do { galleryPhotos = try await GalleryRepository.shared.getAll() }
            catch { /* gallery failure is non-critical */ }
        }
    }

    // MARK: - Feedback

    func loadFeedback() {
        Task {
            do { myFeedback = try await membershipRepo.getMyFeedback() }
            catch { self.error = error.localizedDescription }
        }
    }

    func submitFeedback(type: String, subject: String, description: String) {
        isLoading = true
        Task {
            do {
                let item = try await membershipRepo.submitFeedback(
                    type: type, subject: subject, description: description)
                myFeedback.insert(item, at: 0)
                successMsg = "Feedback submitted"
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    // MARK: - Contact Admin

    func loadAdminContact() {
        Task {
            do {
                adminContact = try await userRepo.getAdminContact()
            } catch { /* non-critical */ }
        }
    }

    func callAdmin() {
        guard !callAdminSent else { return }
        callAdminSent = true
        Task {
            try? await membershipRepo.callAdmin()
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            callAdminSent = false
        }
    }

    func clearError() { error = nil }
    func clearSuccess() { successMsg = nil }
}
