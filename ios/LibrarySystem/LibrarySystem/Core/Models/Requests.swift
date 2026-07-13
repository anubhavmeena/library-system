import Foundation

struct SendOtpRequest: Codable {
    let contact: String
    let contactType: String
    // nil = default routing (Meta WhatsApp -> apitxt -> Twilio SMS);
    // "SMS" forces apitxt/Twilio SMS, skipping WhatsApp — used by the
    // "Send via SMS instead" fallback after a WhatsApp OTP isn't received.
    let channel: String?

    init(contact: String, contactType: String = "MOBILE", channel: String? = nil) {
        self.contact = contact
        self.contactType = contactType
        self.channel = channel
    }
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
    let couponCode: String?

    init(planId: String, seatNumber: String, shift: String, couponCode: String? = nil) {
        self.planId = planId; self.seatNumber = seatNumber; self.shift = shift; self.couponCode = couponCode
    }
}

struct ValidateCouponRequest: Codable {
    let code: String
}

struct CreateCouponRequest: Codable {
    let code: String?
    let discountPercent: Int
}

struct UpdateCouponRequest: Codable {
    let discountPercent: Int?
    let isActive: Bool?
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

struct UpdateStudentRequest: Codable {
    let name: String
    let mobile: String?
    let email: String?
    let address: String?
    let gender: String?
    let dateOfBirth: String?
    let joinedAt: String?
}

struct UpdateMembershipPlanRequest: Codable {
    let planId: String
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
    let paymentMode: String?   // "CASH" | "UPI-QR" — nil defaults to CASH server-side
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
}

// Shared by clear-dues and clear-pending-fees — the admin enters how much is
// actually being collected; any remainder is carried forward as a new
// pending balance rather than being wiped out.
struct ClearAmountRequest: Codable {
    let amountCleared: Double
    let paymentMode: String?   // "CASH" | "UPI-QR" — nil defaults to CASH server-side
}

struct ReleaseSeatRequest: Codable {
    let notifyStudent: Bool
}

struct MarkPendingRequest: Codable {
    let pendingAmount: Double
}

struct SaveAppSettingsRequest: Codable {
    let wifiName: String?
    let wifiPassword: String?
    let upiId: String?
    let graceDays: Int
    let convenienceFee: Double
    let waterTankerRate: Double
    let couponsEnabled: Bool
}

// Backs the admin's ad-hoc "Send Payment Request" on Create Membership —
// mints a short https://.../pay?id=... link server-side rather than
// building a long query-string link on-device.
struct PayLinkRequest: Codable {
    let studentId: String
    let amount: Double
}

struct PayLinkResponse: Codable {
    let link: String
}

struct PaymentClaimReviewRequest: Codable {
    let status: String   // "VERIFIED" | "REJECTED"
}

struct UpdateNotificationSettingRequest: Codable {
    let sendToStudent: Bool
    let sendToAdmin: Bool
    let hindiEnabled: Bool
    let hindiTextStudent: String?
    let hindiTextAdmin: String?
}
