package com.targetzone.library.data.repository

import com.targetzone.library.data.TokenManager
import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.*

class AuthRepository(private val tokenManager: TokenManager) {
    private val api = ApiClient.service

    suspend fun sendOtp(mobile: String): Result<Unit> = runCatching {
        val res = api.sendOtp(SendOtpRequest(contact = mobile))
        if (!res.isSuccessful) throw Exception(res.errorBody()?.string() ?: "Failed to send OTP")
    }

    suspend fun verifyOtp(mobile: String, otp: String): Result<OtpVerifyResponse> = runCatching {
        val res = api.verifyOtp(VerifyOtpRequest(contact = mobile, otp = otp))
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Invalid OTP")
    }

    suspend fun register(name: String, email: String?, sessionToken: String, dateOfBirth: String? = null, gender: String? = null, address: String? = null): Result<AuthResponse> = runCatching {
        val res = api.register(RegisterRequest(name = name, email = email, sessionToken = sessionToken, dateOfBirth = dateOfBirth, gender = gender, address = address))
        val auth = res.body()?.data ?: throw Exception(res.body()?.message ?: "Registration failed")
        tokenManager.saveToken(auth.accessToken)
        tokenManager.saveUser(auth.user)
        auth
    }

    suspend fun login(sessionToken: String): Result<AuthResponse> = runCatching {
        val res = api.login(LoginRequest(sessionToken = sessionToken))
        val auth = res.body()?.data ?: throw Exception(res.body()?.message ?: "Login failed")
        tokenManager.saveToken(auth.accessToken)
        tokenManager.saveUser(auth.user)
        auth
    }

    suspend fun adminLogin(email: String, password: String): Result<AuthResponse> = runCatching {
        val res = api.adminLogin(AdminLoginRequest(email = email, password = password))
        val auth = res.body()?.data ?: throw Exception(res.body()?.message ?: "Admin login failed")
        tokenManager.saveToken(auth.accessToken)
        tokenManager.saveUser(auth.user)
        auth
    }

    suspend fun logout() = tokenManager.clear()
}
