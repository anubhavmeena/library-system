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

struct UpdateStudentRequest: Codable {
    let name: String
    let mobile: String?
    let email: String?
    let address: String?
    let gender: String?
    let dateOfBirth: String?
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

// Maps to backend CreateCashMembershipRequest
struct CreateCashMembershipRequest: Codable {
    let studentId: String
    let planId: String
    let seatNumber: String
    let shift: String
    let startDate: String
    let paidAmount: Double?
    let pendingAmount: Double?
}

struct SaveExpenseRequest: Codable {
    let year: Int
    let month: Int
    let waterTankerQty: Int
    let waterTankerPrice: Double
    let electricityBill: Double
    let internetBill: Double
    let miscItems: [MiscItemRequest]
}

struct MiscItemRequest: Codable {
    let description: String
    let amount: Double
}

struct ReplyRequest: Codable {
    let body: String
}

struct ManualImportRequest: Codable {
    let name: String
    let phone: String
    let fees: String?
    let date: String?
    let seatNumber: String
}
