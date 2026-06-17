package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.GalleryPhoto
import okhttp3.MultipartBody
import okhttp3.RequestBody

class GalleryRepository {
    private val api = ApiClient.service

    suspend fun getAll(): Result<List<GalleryPhoto>> = runCatching {
        api.getGallery().body()?.data ?: emptyList()
    }

    suspend fun upload(file: MultipartBody.Part, caption: RequestBody?): Result<GalleryPhoto> = runCatching {
        api.uploadGalleryPhoto(file, caption).body()?.data
            ?: throw Exception("Upload failed")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        api.deleteGalleryPhoto(id)
    }
}
