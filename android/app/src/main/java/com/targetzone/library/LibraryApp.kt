package com.targetzone.library

import android.app.Application
import com.targetzone.library.data.TokenManager
import com.targetzone.library.data.api.ApiClient

class LibraryApp : Application() {
    lateinit var tokenManager: TokenManager
        private set

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        ApiClient.init(tokenManager)
    }
}
