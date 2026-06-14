package com.targetzone.library.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetzone.library.data.model.User
import com.targetzone.library.data.repository.AuthRepository
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
    val user: User? = null
)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    fun sendOtp(contact: String, contactType: String = "MOBILE") = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.sendOtp(contact, contactType)
            .onSuccess { _state.value = _state.value.copy(isLoading = false, otpSent = true) }
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

    fun resetOtpState() { _state.value = AuthState() }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
