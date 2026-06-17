package com.targetzone.library.ui.student

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetzone.library.data.TokenManager
import com.targetzone.library.data.api.BASE_URL
import com.targetzone.library.data.model.*
import com.targetzone.library.data.repository.GalleryRepository
import com.targetzone.library.data.repository.MembershipRepository
import com.targetzone.library.data.repository.SeatRepository
import com.targetzone.library.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class StudentViewModel(
    private val membershipRepo: MembershipRepository = MembershipRepository(),
    private val seatRepo: SeatRepository = SeatRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val galleryRepo: GalleryRepository = GalleryRepository(),
    private val tokenManager: TokenManager? = null
) : ViewModel() {

    val membership    = MutableStateFlow<Membership?>(null)
    val plans         = MutableStateFlow<List<Plan>>(emptyList())
    val seats         = MutableStateFlow<List<Seat>>(emptyList())
    val selectedSeat  = MutableStateFlow<Seat?>(null)
    val profile       = MutableStateFlow<User?>(null)
    val myFeedback    = MutableStateFlow<List<FeedbackItem>>(emptyList())
    val membershipHistory = MutableStateFlow<List<Membership>>(emptyList())
    val galleryPhotos = MutableStateFlow<List<GalleryPhoto>>(emptyList())

    val isLoading     = MutableStateFlow(false)
    val error         = MutableStateFlow<String?>(null)

    // Payment flow: emit PaymentOrder when ready, MainActivity triggers Razorpay
    private val _paymentOrder = MutableSharedFlow<PaymentOrder>()
    val paymentOrder = _paymentOrder.asSharedFlow()

    val bookingSuccess = MutableStateFlow(false)

    fun loadDashboard() = viewModelScope.launch {
        membershipRepo.getMyMembership()
            .onSuccess { membership.value = it }
    }

    fun loadPlans() = viewModelScope.launch {
        membershipRepo.getPlans().onSuccess { plans.value = it }
    }

    fun loadSeats(shift: String) = viewModelScope.launch {
        isLoading.value = true
        seatRepo.getAvailability(shift)
            .onSuccess { seats.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun selectSeat(seat: Seat?) { selectedSeat.value = seat }

    fun startPayment(planId: String, seatNumber: String, shift: String) = viewModelScope.launch {
        isLoading.value = true
        error.value = null
        membershipRepo.createOrder(planId, seatNumber, shift)
            .onSuccess { order ->
                isLoading.value = false
                if (order.orderId.startsWith("dev_")) {
                    // Dev mode: skip Razorpay, verify directly
                    verifyPayment(order.orderId, "dev_pay_${System.currentTimeMillis()}", "dev_sig", order.membershipId)
                } else {
                    _paymentOrder.emit(order)
                }
            }
            .onFailure { isLoading.value = false; error.value = it.message }
    }

    fun verifyPayment(gatewayOrderId: String, paymentId: String, signature: String, membershipId: String) = viewModelScope.launch {
        isLoading.value = true
        membershipRepo.verifyPayment(VerifyPaymentRequest(gatewayOrderId, paymentId, signature, membershipId))
            .onSuccess { m ->
                membership.value = m
                isLoading.value = false
                bookingSuccess.value = true
            }
            .onFailure { isLoading.value = false; error.value = it.message }
    }

    fun loadProfile() = viewModelScope.launch {
        userRepo.getProfile().onSuccess { profile.value = it }
    }

    fun updateProfile(req: UpdateProfileRequest) = viewModelScope.launch {
        isLoading.value = true
        userRepo.updateProfile(req)
            .onSuccess { profile.value = it; isLoading.value = false }
            .onFailure { error.value = it.message; isLoading.value = false }
    }

    fun uploadPhoto(file: File) = viewModelScope.launch {
        isLoading.value = true
        userRepo.uploadPhoto(file)
            .onSuccess { loadProfile(); isLoading.value = false }
            .onFailure { error.value = it.message; isLoading.value = false }
    }

    fun uploadAadhaar(file: File) = viewModelScope.launch {
        isLoading.value = true
        userRepo.uploadAadhaar(file)
            .onSuccess { loadProfile(); isLoading.value = false }
            .onFailure { error.value = it.message; isLoading.value = false }
    }

    fun downloadIdCard(context: Context) = viewModelScope.launch {
        val token = tokenManager?.getTokenBlocking() ?: return@launch
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse("${BASE_URL}memberships/my/id-card"))
            .addRequestHeader("Authorization", "Bearer $token")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "library-id-card.pdf")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/pdf")
        dm.enqueue(request)
    }

    fun loadFeedback() = viewModelScope.launch {
        membershipRepo.getMyFeedback().onSuccess { myFeedback.value = it }
    }

    fun submitFeedback(type: String, subject: String, description: String, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        membershipRepo.submitFeedback(type, subject, description)
            .onSuccess { isLoading.value = false; loadFeedback(); onDone() }
            .onFailure { error.value = it.message; isLoading.value = false }
    }

    fun loadMembershipHistory() = viewModelScope.launch {
        membershipRepo.getMembershipHistory().onSuccess { membershipHistory.value = it }
    }

    fun loadGallery() = viewModelScope.launch {
        galleryRepo.getAll()
            .onSuccess { galleryPhotos.value = it }
            .onFailure { error.value = it.message }
    }

    fun resetBooking() { selectedSeat.value = null; bookingSuccess.value = false; error.value = null }
    fun clearError() { error.value = null }
}
