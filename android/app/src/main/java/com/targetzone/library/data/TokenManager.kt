package com.targetzone.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.targetzone.library.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class TokenManager(private val context: Context) {
    private val gson = Gson()
    private val TOKEN_KEY = stringPreferencesKey("token")
    private val USER_KEY = stringPreferencesKey("user")

    fun getToken(): Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }

    fun getTokenBlocking(): String? = runBlocking { getToken().first() }

    fun getUser(): Flow<User?> = context.dataStore.data.map { prefs ->
        prefs[USER_KEY]?.let { gson.fromJson(it, User::class.java) }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun saveUser(user: User) {
        context.dataStore.edit { it[USER_KEY] = gson.toJson(user) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
