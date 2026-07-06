package com.targetzone.library.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetzone.library.data.model.User
import com.targetzone.library.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val otpSent: Boolean = false,
    val otpVerified: Boolean = false,
    val sessionToken: String? = null,
    val isNewUser: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    // OTP resend / SMS-fallback timing — mirrors the web LoginPage and the
    // backend's 10s resend cooldown (AuthService.checkCooldownAndGenerateOtp).
    // otpSendCount tracks how many times an OTP has been (re)sent;
    // secondsSinceSend counts up to 10 after each send, driving both "Resend
    // OTP" availability and the "Send via SMS instead" offer (shown once a
    // resend has also gone 10s without verification).
    val otpSendCount: Int = 0,
    val secondsSinceSend: Int = 0,
    val smsOptionUsed: Boolean = false
)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    private var resendTimerJob: Job? = null

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        _state.value = _state.value.copy(secondsSinceSend = 0)
        resendTimerJob = viewModelScope.launch {
            while (_state.value.secondsSinceSend < 10) {
                delay(1000)
                _state.value = _state.value.copy(secondsSinceSend = _state.value.secondsSinceSend + 1)
            }
        }
    }

    fun sendOtp(contact: String, contactType: String = "MOBILE") = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.sendOtp(contact, contactType)
            .onSuccess {
                _state.value = _state.value.copy(isLoading = false, otpSent = true, otpSendCount = 1)
                startResendTimer()
            }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun resendOtp(contact: String, contactType: String = "MOBILE") = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.sendOtp(contact, contactType)
            .onSuccess {
                _state.value = _state.value.copy(isLoading = false, otpSendCount = _state.value.otpSendCount + 1)
                startResendTimer()
            }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun sendOtpViaSms(contact: String, contactType: String = "MOBILE") = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.sendOtp(contact, contactType, channel = "SMS")
            .onSuccess {
                _state.value = _state.value.copy(isLoading = false, smsOptionUsed = true, otpSendCount = _state.value.otpSendCount + 1)
                startResendTimer()
            }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun verifyOtp(mobile: String, otp: String) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.verifyOtp(mobile, otp)
            .onSuccess { res ->
                _state.value = _state.value.copy(
                    isLoading = false, otpVerified = true,
                    sessionToken = res.sessionToken, isNewUser = res.newUser
                )
            }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun register(name: String, email: String?, sessionToken: String, dateOfBirth: String? = null, gender: String? = null, address: String? = null) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.register(name, email, sessionToken, dateOfBirth, gender, address)
            .onSuccess { auth -> _state.value = _state.value.copy(isLoading = false, isLoggedIn = true, user = auth.user) }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun login(sessionToken: String) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.login(sessionToken)
            .onSuccess { auth -> _state.value = _state.value.copy(isLoading = false, isLoggedIn = true, user = auth.user) }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun adminLogin(contact: String, otp: String) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.adminLogin(contact, otp)
            .onSuccess { auth -> _state.value = _state.value.copy(isLoading = false, isLoggedIn = true, user = auth.user) }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
    }

    fun resetOtpState() {
        resendTimerJob?.cancel()
        _state.value = AuthState()
    }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
