import Foundation

struct MembershipRepository {
    static let shared = MembershipRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    func getMyMembership() async throws -> Membership {
        try await api.request(.getMyMembership, token: token)
    }

    func getMembershipHistory() async throws -> [Membership] {
        try await api.request(.getMembershipHistory, token: token)
    }

    func getQueuedMembership() async -> Membership? {
        try? await api.request(.getQueuedMembership, token: token)
    }

    func getPlans() async throws -> [Plan] {
        try await api.request(.getPlans)
    }

    func createOrder(planId: String, seatNumber: String, shift: String) async throws -> PaymentOrder {
        let req = CreateOrderRequest(planId: planId, seatNumber: seatNumber, shift: shift)
        return try await api.request(.createOrder(req), token: token)
    }

    func verifyPayment(gatewayOrderId: String, gatewayPaymentId: String,
                       signature: String, membershipId: String) async throws -> Membership {
        let req = VerifyPaymentRequest(gatewayOrderId: gatewayOrderId,
                                       gatewayPaymentId: gatewayPaymentId,
                                       signature: signature, membershipId: membershipId)
        return try await api.request(.verifyPayment(req), token: token)
    }

    func submitFeedback(type: String, subject: String, description: String) async throws -> FeedbackItem {
        let req = SubmitFeedbackRequest(type: type, subject: subject, description: description)
        return try await api.request(.submitFeedback(req), token: token)
    }

    func getMyFeedback() async throws -> [FeedbackItem] {
        try await api.request(.getMyFeedback, token: token)
    }

    func getMyPayments() async throws -> [StudentPayment] {
        try await api.request(.getMyPayments, token: token)
    }

    func downloadIdCard() async throws -> Data {
        try await api.download(.downloadIdCard, token: token)
    }
}
