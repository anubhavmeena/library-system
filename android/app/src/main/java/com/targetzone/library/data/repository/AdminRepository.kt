package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AdminRepository {
    private val api = ApiClient.service

    suspend fun getStats(): Result<AdminStats> = runCatching {
        val res = api.getAdminStats()
        res.body()?.data ?: throw Exception("Failed to load stats")
    }

    suspend fun getStudents(page: Int = 0, status: String? = null, membershipStatus: String? = null, search: String? = null, size: Int = 20): Result<Pair<List<StudentSummary>, Int>> = runCatching {
        val res = api.getStudents(page = page, size = size, status = status?.takeIf { it.isNotBlank() }, membershipStatus = membershipStatus?.takeIf { it.isNotBlank() }, search = search?.takeIf { it.isNotBlank() })
        val data = res.body()?.data ?: StudentListResponse()
        Pair(data.students, data.total)
    }

    suspend fun toggleStudentStatus(id: String, active: Boolean): Result<Unit> = runCatching {
        api.toggleStudentStatus(id, ToggleStatusRequest(active = active))
    }

    suspend fun changeSeat(membershipId: String, seatNumber: String): Result<Unit> = runCatching {
        api.changeSeat(membershipId, ChangeSeatRequest(seatNumber = seatNumber))
    }

    suspend fun swapSeat(membershipId: String, otherUserId: String): Result<Unit> = runCatching {
        val res = api.swapSeat(membershipId, SwapSeatRequest(otherUserId = otherUserId))
        // Business-logic failures (e.g. "other student doesn't have a seat") come back
        // as a real 4xx with an { success:false, message } body — Retrofit/Gson only
        // auto-parses res.body() for 2xx, so a non-2xx has to be read from errorBody().
        if (!res.isSuccessful) {
            val message = res.errorBody()?.string()?.let {
                runCatching { com.google.gson.JsonParser.parseString(it).asJsonObject.get("message")?.asString }.getOrNull()
            } ?: "Seat exchange failed"
            throw Exception(message)
        }
        if (res.body()?.success == false) throw Exception(res.body()?.message ?: "Seat exchange failed")
    }

    suspend fun updateMembershipPlan(membershipId: String, planId: String): Result<Unit> = runCatching {
        api.updateMembershipPlan(membershipId, UpdateMembershipPlanRequest(planId = planId))
    }

    suspend fun renewSeat(membershipId: String): Result<Unit> = runCatching {
        api.renewSeat(membershipId)
    }

    suspend fun releaseSeat(membershipId: String, notifyStudent: Boolean): Result<Unit> = runCatching {
        api.releaseSeat(membershipId, ReleaseSeatRequest(notifyStudent))
    }

    suspend fun clearDues(membershipId: String, amountCleared: Double, paymentMode: String = "CASH"): Result<Unit> = runCatching {
        api.clearDues(membershipId, ClearAmountRequest(amountCleared, paymentMode))
    }

    suspend fun markPending(membershipId: String, pendingAmount: Double): Result<Unit> = runCatching {
        api.markPending(membershipId, MarkPendingRequest(pendingAmount))
    }

    suspend fun markGrace(membershipId: String): Result<Unit> = runCatching {
        api.markGrace(membershipId)
    }

    suspend fun deleteStudent(id: String): Result<Unit> = runCatching {
        api.deleteStudent(id)
    }

    suspend fun getStudentsInGraceWithDues(): Result<List<StudentDetail>> = runCatching {
        val res = api.getStudentsInGraceWithDues()
        res.body()?.data ?: emptyList()
    }

    suspend fun getStudentsWithOrphanedSeats(): Result<List<StudentDetail>> = runCatching {
        val res = api.getStudentsWithOrphanedSeats()
        res.body()?.data ?: emptyList()
    }

    suspend fun sendGraceDuesReminders(userIds: List<String>): Result<String> = runCatching {
        val res = api.sendGraceDuesReminders(SendReminderRequest(userIds = userIds))
        res.body()?.data ?: res.body()?.message ?: "Reminders sent"
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
                    studentId = s.studentId,
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

    suspend fun sendMessageToStudent(id: String, message: String): Result<Unit> = runCatching {
        api.sendMessageToStudent(id, BroadcastRequest(message = message))
    }

    suspend fun createCashMembership(req: CreateCashMembershipRequest): Result<Membership> = runCatching {
        val res = api.createCashMembership(req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to create membership")
    }

    suspend fun getStudentsWithPendingFees(): Result<List<StudentDetail>> = runCatching {
        val res = api.getStudentsWithPendingFees()
        res.body()?.data ?: emptyList()
    }

    suspend fun clearPendingFees(id: String, amountCleared: Double, paymentMode: String = "CASH"): Result<Unit> = runCatching {
        api.clearPendingFees(id, ClearAmountRequest(amountCleared, paymentMode))
    }

    suspend fun sendPendingFeeReminders(userIds: List<String>): Result<String> = runCatching {
        val res = api.sendPendingFeeReminders(SendReminderRequest(userIds = userIds))
        res.body()?.data ?: res.body()?.message ?: "Reminders sent"
    }

    suspend fun getBroadcastHistory(): Result<List<BroadcastHistory>> = runCatching {
        val res = api.getBroadcastHistory()
        res.body()?.data ?: emptyList()
    }

    suspend fun getRevenueReport(from: String, to: String): Result<RevenueReport> = runCatching {
        val res = api.getRevenueReport(from, to)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to load report")
    }

    suspend fun getDailyPayments(date: String): Result<List<DailyPayment>> = runCatching {
        val res = api.getDailyPayments(date)
        res.body()?.data ?: emptyList()
    }

    suspend fun getInbox(): Result<List<InboxSummary>> = runCatching {
        val res = api.getInbox()
        res.body()?.data ?: emptyList()
    }

    suspend fun getInboxMessage(messageNumber: Int): Result<InboxMessage> = runCatching {
        val res = api.getInboxMessage(messageNumber)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Message not found")
    }

    suspend fun replyToMessage(messageNumber: Int, body: String): Result<Unit> = runCatching {
        api.replyToMessage(messageNumber, ReplyRequest(body))
    }

    suspend fun deleteInboxMessage(messageNumber: Int): Result<Unit> = runCatching {
        api.deleteInboxMessage(messageNumber)
    }

    suspend fun importSingleStudent(name: String, phone: String, photo: java.io.File?): Result<Unit> = runCatching {
        val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
        val phoneBody = phone.toRequestBody("text/plain".toMediaTypeOrNull())
        val photoPart = photo?.let {
            val body = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
            okhttp3.MultipartBody.Part.createFormData("photo", it.name, body)
        }
        val res = api.importSingleStudentWithPhoto(nameBody, phoneBody, photoPart)
        if (res.body()?.success == false) throw Exception(res.body()?.message ?: "Import failed")
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

    suspend fun getStudentPayments(userId: String): Result<List<StudentPayment>> = runCatching {
        val res = api.getAdminStudentPayments(userId)
        res.body()?.data ?: emptyList()
    }
}
