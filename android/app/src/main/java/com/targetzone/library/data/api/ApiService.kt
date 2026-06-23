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

    @GET("memberships/my/queued")
    suspend fun getQueuedMembership(): Response<ApiResponse<Membership>>

    // Plans
    @GET("plans")
    suspend fun getPlans(): Response<ApiResponse<List<Plan>>>

    // Payments
    @POST("payments/create-order")
    suspend fun createOrder(@Body req: CreateOrderRequest): Response<ApiResponse<PaymentOrder>>

    @POST("payments/verify")
    suspend fun verifyPayment(@Body req: VerifyPaymentRequest): Response<ApiResponse<Membership>>

    @GET("payments/my")
    suspend fun getPaymentHistory(): Response<ApiResponse<List<StudentPayment>>>

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
    ): Response<ApiResponse<StudentListResponse>>

    @GET("admin/students/{id}")
    suspend fun getStudentDetail(@Path("id") id: String): Response<ApiResponse<StudentDetail>>

    @GET("admin/students/{id}/payments")
    suspend fun getAdminStudentPayments(@Path("id") id: String): Response<ApiResponse<List<StudentPayment>>>

    @PATCH("admin/students/{id}")
    suspend fun updateStudent(
        @Path("id") id: String,
        @Body req: UpdateStudentRequest
    ): Response<ApiResponse<StudentDetail>>

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

    @POST("admin/memberships/cash")
    suspend fun createCashMembership(@Body req: CreateCashMembershipRequest): Response<ApiResponse<Membership>>

    @Multipart
    @POST("admin/students/import")
    suspend fun importStudents(@Part file: MultipartBody.Part): Response<ApiResponse<ImportResult>>

    @POST("admin/students/import/single")
    suspend fun importSingleStudent(@Body req: ManualImportRequest): Response<ApiResponse<Any?>>

    @GET("admin/students/pending-fees")
    suspend fun getStudentsWithPendingFees(): Response<ApiResponse<List<StudentDetail>>>

    @POST("admin/students/{id}/clear-fees")
    suspend fun clearPendingFees(@Path("id") id: String): Response<ApiResponse<StudentDetail>>

    @POST("admin/reminders/pending-fees")
    suspend fun sendPendingFeeReminders(): Response<ApiResponse<String?>>

    @GET("admin/broadcast/history")
    suspend fun getBroadcastHistory(): Response<ApiResponse<List<BroadcastHistory>>>

    @GET("admin/reports/revenue")
    suspend fun getRevenueReport(
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<ApiResponse<RevenueReport>>

    @GET("admin/reports/payments")
    suspend fun getDailyPayments(
        @Query("date") date: String
    ): Response<ApiResponse<List<DailyPayment>>>

    @GET("admin/inbox")
    suspend fun getInbox(): Response<ApiResponse<List<InboxSummary>>>

    @GET("admin/inbox/{messageNumber}")
    suspend fun getInboxMessage(@Path("messageNumber") messageNumber: Int): Response<ApiResponse<InboxMessage>>

    @POST("admin/inbox/{messageNumber}/reply")
    suspend fun replyToMessage(
        @Path("messageNumber") messageNumber: Int,
        @Body req: ReplyRequest
    ): Response<ApiResponse<Any?>>

    @DELETE("admin/inbox/{messageNumber}")
    suspend fun deleteInboxMessage(@Path("messageNumber") messageNumber: Int): Response<ApiResponse<Any?>>

    @GET("admin/expenses")
    suspend fun getExpenses(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<ApiResponse<MonthlyExpense>>

    @POST("admin/expenses")
    suspend fun saveExpenses(@Body req: SaveExpenseRequest): Response<ApiResponse<MonthlyExpense>>

    // Gallery
    @GET("gallery")
    suspend fun getGallery(): Response<ApiResponse<List<GalleryPhoto>>>

    @Multipart
    @POST("gallery")
    suspend fun uploadGalleryPhoto(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: okhttp3.RequestBody? = null
    ): Response<ApiResponse<GalleryPhoto>>

    @DELETE("gallery/{id}")
    suspend fun deleteGalleryPhoto(@Path("id") id: String): Response<ApiResponse<String?>>
}
