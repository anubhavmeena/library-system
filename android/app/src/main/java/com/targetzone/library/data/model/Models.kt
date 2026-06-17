package com.targetzone.library.data.model


data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

data class User(
    val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val email: String? = null,
    val role: String = "STUDENT",
    val isActive: Boolean = true,
    val photoUrl: String? = null,
    val fatherName: String? = null,
    val address: String? = null,
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val aadhaarUrl: String? = null
)

data class AuthResponse(
    val user: User,
    val accessToken: String
)

data class OtpVerifyResponse(
    val sessionToken: String,
    val newUser: Boolean
)

data class Membership(
    val id: String = "",
    val userId: String = "",
    val planId: String = "",
    val planName: String = "",
    val planType: String = "",
    val seatNumber: String = "",
    val shift: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "",
    val amountPaid: Double = 0.0
)

data class Plan(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val planType: String = "",
    val durationDays: Int = 30,
    val isActive: Boolean = true
)

data class Seat(
    val id: String? = null,
    val seatNumber: String = "",
    val row: String = "",
    val isBooked: Boolean = false,
    // populated only from /admin/seats/map
    val studentName: String? = null,
    val studentMobile: String? = null,
    val membershipEnd: String? = null
)

// admin seat-map API response shape
data class SeatMapDto(
    val shift: String = "",
    val date: String = "",
    val totalSeats: Int = 0,
    val occupiedSeats: Int = 0,
    val availableSeats: Int = 0,
    val seatsByRow: Map<String, List<SeatInfoItem>> = emptyMap()
)

data class SeatInfoItem(
    val seatNumber: String = "",
    val isOccupied: Boolean = false,
    val studentName: String? = null,
    val studentMobile: String? = null,
    val shift: String? = null,
    val membershipEnd: String? = null
)

data class PaymentOrder(
    val orderId: String = "",
    val membershipId: String = "",
    val amount: Double = 0.0,
    val razorpayKeyId: String = ""
)

data class AdminStats(
    val totalStudents: Int = 0,
    val activeStudents: Int = 0,
    val activeMemberships: Int = 0,
    val expiringThisWeek: Int = 0,
    val totalSeats: Int = 0,
    val occupiedSeats: Int = 0,
    val availableSeats: Int = 0,
    val revenueToday: Double = 0.0,
    val revenueThisMonth: Double = 0.0,
    val paymentsThisMonth: Int = 0
)

data class StudentSummary(
    val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val email: String? = null,
    val isActive: Boolean = true,
    val membershipId: String? = null,
    val membershipStatus: String? = null,
    val seatNumber: String? = null,
    val shift: String? = null,
    val membershipStart: String? = null,
    val endDate: String? = null,
    val planName: String? = null
)

data class StudentDetail(
    val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val email: String? = null,
    val address: String? = null,
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val photoUrl: String? = null,
    val aadhaarUrl: String? = null,
    val isActive: Boolean = true,
    val joinedAt: String? = null,
    val membershipId: String? = null,
    val planName: String? = null,
    val planType: String? = null,
    val seatNumber: String? = null,
    val shift: String? = null,
    val membershipStart: String? = null,
    val membershipEnd: String? = null,
    val membershipStatus: String? = null,
    val daysRemaining: Int = 0,
    val paymentMode: String? = null
)

data class FeedbackItem(
    val id: String = "",
    val userId: String? = null,
    val type: String = "",          // FEEDBACK or COMPLAINT
    val subject: String = "",
    val description: String = "",
    val status: String = "",        // OPEN, UNDER_REVIEW, RESOLVED
    val adminNotes: String? = null,
    val createdAt: String = "",
    val updatedAt: String? = null,
    // admin-only fields (from /admin/feedback)
    val studentName: String? = null,
    val studentMobile: String? = null
)

data class ReminderStudent(
    val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val email: String? = null,
    val endDate: String = "",
    val daysLeft: Int = 0,
    val seatNumber: String? = null,
    val shift: String? = null
)

// Request bodies
data class SendOtpRequest(val contact: String, val contactType: String = "MOBILE")
data class VerifyOtpRequest(val contact: String, val otp: String)
data class RegisterRequest(val name: String, val email: String?, val sessionToken: String, val dateOfBirth: String? = null, val gender: String? = null, val address: String? = null)
data class LoginRequest(val sessionToken: String)
data class AdminLoginRequest(val contact: String, val otp: String)
data class CreateOrderRequest(val planId: String, val seatNumber: String, val shift: String)
data class VerifyPaymentRequest(val gatewayOrderId: String, val gatewayPaymentId: String, val signature: String, val membershipId: String)
data class BookSeatRequest(val seatNumber: String, val membershipId: String, val shift: String, val startDate: String, val endDate: String)
data class UpdateProfileRequest(val name: String, val fatherName: String?, val address: String?, val gender: String?, val dateOfBirth: String?, val email: String?)
data class ToggleStatusRequest(val active: Boolean)
data class ChangeSeatRequest(val seatNumber: String)
data class SendReminderRequest(val userIds: List<String>)
data class BroadcastRequest(val message: String, val targetGroup: String = "ALL")
data class SubmitFeedbackRequest(val type: String, val subject: String, val description: String)
data class UpdateFeedbackRequest(val status: String, val adminNotes: String?)
data class CreateMembershipRequest(val userId: String, val planId: String, val seatNumber: String, val shift: String, val startDate: String)

data class MonthlyExpense(
    val year: Int = 0,
    val month: Int = 0,
    val waterTankerQty: Int = 0,
    val waterTankerPrice: Double = 0.0,
    val electricityBill: Double = 0.0,
    val internetBill: Double = 0.0,
    val miscellaneous: Double = 0.0,
    val totalExpense: Double = 0.0
)

data class ImportResult(
    val totalRows: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val errors: List<ImportRowError> = emptyList()
)

data class ImportRowError(
    val row: Int = 0,
    val name: String = "",
    val phone: String = "",
    val reason: String = ""
)

data class SaveExpenseRequest(
    val year: Int,
    val month: Int,
    val waterTankerQty: Int,
    val waterTankerPrice: Double,
    val electricityBill: Double,
    val internetBill: Double,
    val miscellaneous: Double
)
