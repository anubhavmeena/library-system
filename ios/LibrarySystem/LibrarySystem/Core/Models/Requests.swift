import Foundation

struct SendOtpRequest: Codable {
    let contact: String
    let contactType: String = "MOBILE"
}

struct VerifyOtpRequest: Codable {
    let contact: String
    let otp: String
}

struct RegisterRequest: Codable {
    let name: String
    let email: String?
    let sessionToken: String
    let dateOfBirth: String?
    let gender: String?
    let address: String?
}

struct LoginRequest: Codable {
    let sessionToken: String
}

struct AdminLoginRequest: Codable {
    let contact: String
    let otp: String
}

struct CreateOrderRequest: Codable {
    let planId: String
    let seatNumber: String
    let shift: String
}

struct VerifyPaymentRequest: Codable {
    let gatewayOrderId: String
    let gatewayPaymentId: String
    let signature: String
    let membershipId: String
}

struct BookSeatRequest: Codable {
    let seatNumber: String
    let membershipId: String
    let shift: String
    let startDate: String
    let endDate: String
}

struct UpdateProfileRequest: Codable {
    let name: String
    let fatherName: String?
    let address: String?
    let gender: String?
    let dateOfBirth: String?
    let email: String?
}

struct ToggleStatusRequest: Codable {
    let active: Bool
}

struct ChangeSeatRequest: Codable {
    let seatNumber: String
}

struct SendReminderRequest: Codable {
    let userIds: [String]
}

struct BroadcastRequest: Codable {
    let message: String
    let targetGroup: String
}

struct SubmitFeedbackRequest: Codable {
    let type: String
    let subject: String
    let description: String
}

struct UpdateFeedbackRequest: Codable {
    let status: String
    let adminNotes: String?
}

struct CreateMembershipRequest: Codable {
    let userId: String
    let planId: String
    let seatNumber: String
    let shift: String
    let startDate: String
}
