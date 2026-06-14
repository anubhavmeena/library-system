package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.*

class AdminRepository {
    private val api = ApiClient.service

    suspend fun getStats(): Result<AdminStats> = runCatching {
        val res = api.getAdminStats()
        res.body()?.data ?: throw Exception("Failed to load stats")
    }

    suspend fun getStudents(page: Int = 0, status: String? = null, membershipStatus: String? = null, search: String? = null): Result<List<StudentSummary>> = runCatching {
        val res = api.getStudents(page = page, status = status?.takeIf { it.isNotBlank() }, membershipStatus = membershipStatus?.takeIf { it.isNotBlank() }, search = search?.takeIf { it.isNotBlank() })
        res.body()?.data ?: emptyList()
    }

    suspend fun toggleStudentStatus(id: String, active: Boolean): Result<Unit> = runCatching {
        api.toggleStudentStatus(id, ToggleStatusRequest(active = active))
    }

    suspend fun changeSeat(membershipId: String, seatNumber: String): Result<Unit> = runCatching {
        api.changeSeat(membershipId, ChangeSeatRequest(seatNumber = seatNumber))
    }

    suspend fun getExpiringMemberships(withinDays: Int): Result<List<ReminderStudent>> = runCatching {
        val res = api.getExpiringMemberships(withinDays = withinDays)
        res.body()?.data ?: emptyList()
    }

    suspend fun sendReminders(userIds: List<String>): Result<String> = runCatching {
        val res = api.sendReminders(SendReminderRequest(userIds = userIds))
        res.body()?.data ?: "Reminders sent"
    }

    suspend fun getAllFeedback(): Result<List<FeedbackItem>> = runCatching {
        val res = api.getAllFeedback()
        res.body()?.data ?: emptyList()
    }

    suspend fun sendBroadcast(message: String, targetGroup: String): Result<String> = runCatching {
        val res = api.sendBroadcast(BroadcastRequest(message = message, targetGroup = targetGroup))
        res.body()?.data ?: "Broadcast sent"
    }

    suspend fun createMembership(req: CreateMembershipRequest): Result<Membership> = runCatching {
        val res = api.createMembership(req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to create membership")
    }
}
