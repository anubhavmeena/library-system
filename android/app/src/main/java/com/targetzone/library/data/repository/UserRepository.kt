package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.UpdateProfileRequest
import com.targetzone.library.data.model.User
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UserRepository {
    private val api = ApiClient.service

    suspend fun getProfile(): Result<User> = runCatching {
        val res = api.getProfile()
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to load profile")
    }

    suspend fun updateProfile(req: UpdateProfileRequest): Result<User> = runCatching {
        val res = api.updateProfile(req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Update failed")
    }

    suspend fun uploadPhoto(file: File): Result<String> = runCatching {
        val mimeType = when (file.extension.lowercase()) {
            "png"  -> "image/png"
            "webp" -> "image/webp"
            else   -> "image/jpeg"
        }
        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val res = api.uploadPhoto(part)
        res.body()?.data?.get("photoUrl") ?: throw Exception("Upload failed")
    }

    suspend fun uploadAadhaar(file: File): Result<String> = runCatching {
        val requestFile = file.asRequestBody("*/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val res = api.uploadAadhaar(part)
        res.body()?.data?.get("photoUrl") ?: throw Exception("Upload failed")
    }
}
