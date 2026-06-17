package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.*

class AdminRepository {
    private val api = ApiClient.service

    suspend fun getStats(): Result<AdminStats> = runCatching {
        val res = api.getAdminStats()
        res.body()?.data ?: throw Exception("Failed to load stats")
    }

    suspend fun getStudents(page: Int = 0, status: String? = null, membershipStatus: String? = null, search: String? = null): Result<Pair<List<StudentSummary>, Int>> = runCatching {
        val res = api.getStudents(page = page, status = status?.takeIf { it.isNotBlank() }, membershipStatus = membershipStatus?.takeIf { it.isNotBlank() }, search = search?.takeIf { it.isNotBlank() })
        val data = res.body()?.data ?: StudentListResponse()
        Pair(data.students, data.total)
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
        res.body()?.data ?: res.body()?.message ?: "Reminders sent"
    }

    suspend fun getAdminSeatMap(shift: String, date: String?): Result<List<Seat>> = runCatching {
        val res = api.getAdminSeatMap(shift, date)
        val dto = res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to load seat map")
        dto.seatsByRow.flatMap { (row, seatList) ->
            seatList.map { s ->
                Seat(
                    seatNumber = s.seatNumber,
                    row = row,
                    isBooked = s.isOccupied,
                    studentName = s.studentName,
                    studentMobile = s.studentMobile,
                    studentGender = s.studentGender,
                    membershipEnd = s.membershipEnd
                )
            }
        }
    }

    suspend fun getStudentDetail(studentId: String): Result<StudentDetail> = runCatching {
        val res = api.getStudentDetail(studentId)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to load student details")
    }

    suspend fun getAllFeedback(): Result<List<FeedbackItem>> = runCatching {
        val res = api.getAllFeedback()
        res.body()?.data ?: emptyList()
    }

    suspend fun updateFeedback(id: String, status: String, adminNotes: String?): Result<FeedbackItem> = runCatching {
        val res = api.updateFeedback(id, UpdateFeedbackRequest(status = status, adminNotes = adminNotes))
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Update failed")
    }

    suspend fun sendBroadcast(message: String, targetGroup: String): Result<String> = runCatching {
        val res = api.sendBroadcast(BroadcastRequest(message = message, targetGroup = targetGroup))
        res.body()?.data ?: res.body()?.message ?: "Broadcast sent"
    }

    suspend fun createMembership(req: CreateMembershipRequest): Result<Membership> = runCatching {
        val res = api.createMembership(req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to create membership")
    }

    suspend fun updateStudent(id: String, req: UpdateStudentRequest): Result<StudentDetail> = runCatching {
        val res = api.updateStudent(id, req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Update failed")
    }

    suspend fun importStudents(file: okhttp3.MultipartBody.Part): Result<ImportResult> = runCatching {
        val res = api.importStudents(file)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Import failed")
    }

    suspend fun getExpenses(year: Int, month: Int): Result<MonthlyExpense> = runCatching {
        val res = api.getExpenses(year, month)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to load expenses")
    }

    suspend fun saveExpenses(req: SaveExpenseRequest): Result<MonthlyExpense> = runCatching {
        val res = api.saveExpenses(req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to save expenses")
    }
}
