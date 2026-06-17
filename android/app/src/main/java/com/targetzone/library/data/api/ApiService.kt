package com.targetzone.library.data.api

import com.targetzone.library.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/send-otp")
    suspend fun sendOtp(@Body req: SendOtpRequest): Response<ApiResponse<Any?>>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body req: VerifyOtpRequest): Response<ApiResponse<OtpVerifyResponse>>

    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/admin/login")
    suspend fun adminLogin(@Body req: AdminLoginRequest): Response<ApiResponse<AuthResponse>>

    // User
    @GET("users/me")
    suspend fun getProfile(): Response<ApiResponse<User>>

    @PATCH("users/me")
    suspend fun updateProfile(@Body req: UpdateProfileRequest): Response<ApiResponse<User>>

    @Multipart
    @POST("users/me/photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<Map<String, String>>>

    @Multipart
    @POST("users/me/aadhaar")
    suspend fun uploadAadhaar(@Part file: MultipartBody.Part): Response<ApiResponse<Map<String, String>>>

    // Membership
    @GET("memberships/my")
    suspend fun getMyMembership(): Response<ApiResponse<Membership>>

    @GET("memberships/my/all")
    suspend fun getMembershipHistory(): Response<ApiResponse<List<Membership>>>

    // Plans
    @GET("plans")
    suspend fun getPlans(): Response<ApiResponse<List<Plan>>>

    // Payments
    @POST("payments/create-order")
    suspend fun createOrder(@Body req: CreateOrderRequest): Response<ApiResponse<PaymentOrder>>

    @POST("payments/verify")
    suspend fun verifyPayment(@Body req: VerifyPaymentRequest): Response<ApiResponse<Membership>>

    @GET("payments/my")
    suspend fun getPaymentHistory(): Response<ApiResponse<List<Map<String, Any>>>>

    // Seats
    @GET("seats/availability")
    suspend fun getSeatAvailability(
        @Query("shift") shift: String,
        @Query("date") date: String? = null
    ): Response<ApiResponse<List<Seat>>>

    @POST("seats/book")
    suspend fun bookSeat(@Body req: BookSeatRequest): Response<ApiResponse<Any?>>

    // Feedback
    @GET("users/feedback/my")
    suspend fun getMyFeedback(): Response<ApiResponse<List<FeedbackItem>>>

    @POST("users/feedback")
    suspend fun submitFeedback(@Body req: SubmitFeedbackRequest): Response<ApiResponse<FeedbackItem>>

    // Admin
    @GET("admin/dashboard")
    suspend fun getAdminStats(): Response<ApiResponse<AdminStats>>

    @GET("admin/students")
    suspend fun getStudents(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("status") status: String? = null,
        @Query("membershipStatus") membershipStatus: String? = null,
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<StudentSummary>>>

    @GET("admin/students/{id}")
    suspend fun getStudentDetail(@Path("id") id: String): Response<ApiResponse<StudentDetail>>

    @PATCH("admin/students/{id}/status")
    suspend fun toggleStudentStatus(
        @Path("id") id: String,
        @Body req: ToggleStatusRequest
    ): Response<ApiResponse<Any?>>

    @PATCH("admin/memberships/{id}/seat")
    suspend fun changeSeat(
        @Path("id") membershipId: String,
        @Body req: ChangeSeatRequest
    ): Response<ApiResponse<Any?>>

    @GET("admin/memberships/expiring")
    suspend fun getExpiringMemberships(
        @Query("withinDays") withinDays: Int = 7
    ): Response<ApiResponse<List<ReminderStudent>>>

    @POST("admin/reminders/send")
    suspend fun sendReminders(@Body req: SendReminderRequest): Response<ApiResponse<String?>>

    @GET("admin/seats/map")
    suspend fun getAdminSeatMap(
        @Query("shift") shift: String,
        @Query("date") date: String? = null
    ): Response<ApiResponse<SeatMapDto>>

    @GET("admin/feedback")
    suspend fun getAllFeedback(): Response<ApiResponse<List<FeedbackItem>>>

    @PATCH("admin/feedback/{id}")
    suspend fun updateFeedback(
        @Path("id") id: String,
        @Body req: UpdateFeedbackRequest
    ): Response<ApiResponse<FeedbackItem>>

    @POST("admin/broadcast")
    suspend fun sendBroadcast(@Body req: BroadcastRequest): Response<ApiResponse<String?>>

    @POST("admin/memberships/create")
    suspend fun createMembership(@Body req: CreateMembershipRequest): Response<ApiResponse<Membership>>

    @GET("admin/expenses")
    suspend fun getExpenses(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<ApiResponse<MonthlyExpense>>

    @POST("admin/expenses")
    suspend fun saveExpenses(@Body req: SaveExpenseRequest): Response<ApiResponse<MonthlyExpense>>
}
