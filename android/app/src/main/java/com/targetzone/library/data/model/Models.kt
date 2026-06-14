package com.targetzone.library.data.model

import com.google.gson.annotations.SerializedName

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
    val isBooked: Boolean = false
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

data class FeedbackItem(
    val id: String = "",
    val userId: String = "",
    val userName: String? = null,
    val message: String = "",
    val rating: Int = 5,
    val createdAt: String = ""
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
data class RegisterRequest(val name: String, val email: String?, val sessionToken: String)
data class LoginRequest(val sessionToken: String)
data class AdminLoginRequest(val email: String, val password: String)
data class CreateOrderRequest(val planId: String, val seatNumber: String, val shift: String)
data class VerifyPaymentRequest(val gatewayOrderId: String, val gatewayPaymentId: String, val signature: String, val membershipId: String)
data class BookSeatRequest(val seatNumber: String, val membershipId: String, val shift: String, val startDate: String, val endDate: String)
data class UpdateProfileRequest(val name: String, val fatherName: String?, val address: String?, val gender: String?, val dateOfBirth: String?, val email: String?)
data class ToggleStatusRequest(val active: Boolean)
data class ChangeSeatRequest(val seatNumber: String)
data class SendReminderRequest(val userIds: List<String>)
data class BroadcastRequest(val message: String, val targetGroup: String = "ALL")
data class SubmitFeedbackRequest(val message: String, val rating: Int)
data class CreateMembershipRequest(val userId: String, val planId: String, val seatNumber: String, val shift: String, val startDate: String)
