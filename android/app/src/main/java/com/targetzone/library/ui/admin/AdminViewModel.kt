package com.targetzone.library.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetzone.library.data.model.*
import com.targetzone.library.data.repository.AdminRepository
import com.targetzone.library.data.repository.GalleryRepository
import com.targetzone.library.data.repository.MembershipRepository
import com.targetzone.library.data.repository.SeatRepository
import okhttp3.MultipartBody
import okhttp3.RequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminRepo: AdminRepository = AdminRepository(),
    private val seatRepo: SeatRepository = SeatRepository(),
    private val membershipRepo: MembershipRepository = MembershipRepository(),
    private val galleryRepo: GalleryRepository = GalleryRepository()
) : ViewModel() {

    val stats           = MutableStateFlow<AdminStats?>(null)
    val students        = MutableStateFlow<List<StudentSummary>>(emptyList())
    val totalStudents   = MutableStateFlow(0)
    val selectedStudent = MutableStateFlow<StudentDetail?>(null)
    val seats        = MutableStateFlow<List<Seat>>(emptyList())    // student-facing availability
    val adminSeats   = MutableStateFlow<List<Seat>>(emptyList())    // admin seat map with student details
    val expiring     = MutableStateFlow<List<ReminderStudent>>(emptyList())
    val feedback     = MutableStateFlow<List<FeedbackItem>>(emptyList())
    val plans        = MutableStateFlow<List<Plan>>(emptyList())

    val expense      = MutableStateFlow<MonthlyExpense?>(null)
    val importResult = MutableStateFlow<ImportResult?>(null)
    val galleryPhotos = MutableStateFlow<List<GalleryPhoto>>(emptyList())

    // Pending fees
    val pendingFeeStudents = MutableStateFlow<List<StudentDetail>>(emptyList())

    // Broadcast history
    val broadcastHistory = MutableStateFlow<List<BroadcastHistory>>(emptyList())

    // Revenue reports
    val revenueReport   = MutableStateFlow<RevenueReport?>(null)
    val dailyPayments   = MutableStateFlow<List<DailyPayment>>(emptyList())

    // Student payment history (admin view)
    val studentPayments = MutableStateFlow<List<StudentPayment>>(emptyList())

    // Inbox
    val inboxMessages      = MutableStateFlow<List<InboxSummary>>(emptyList())
    val selectedInboxMsg   = MutableStateFlow<InboxMessage?>(null)

    val isLoading    = MutableStateFlow(false)
    val error        = MutableStateFlow<String?>(null)
    val successMsg   = MutableStateFlow<String?>(null)

    fun loadStats() = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStats()
            .onSuccess { stats.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadStudentDetail(studentId: String) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudentDetail(studentId)
            .onSuccess { selectedStudent.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadStudents(page: Int = 0, status: String? = null, membershipStatus: String? = null, search: String? = null) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudents(page, status, membershipStatus, search)
            .onSuccess { (list, total) -> students.value = list; totalStudents.value = total }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun toggleStudentStatus(id: String, currentActive: Boolean, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        adminRepo.toggleStudentStatus(id, !currentActive)
            .onSuccess { if (onDone != null) onDone() else loadStudents() }
            .onFailure { error.value = it.message }
    }

    fun loadSeats(shift: String, date: String? = null) = viewModelScope.launch {
        isLoading.value = true
        seatRepo.getAvailability(shift, date)
            .onSuccess { seats.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun changeSeat(membershipId: String, seatNumber: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.changeSeat(membershipId, seatNumber)
            .onSuccess { successMsg.value = "Seat changed to $seatNumber"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun updateMembershipPlan(membershipId: String, planId: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.updateMembershipPlan(membershipId, planId)
            .onSuccess { successMsg.value = "Plan updated"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun loadExpiring(withinDays: Int = 7) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getExpiringMemberships(withinDays)
            .onSuccess { expiring.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun sendReminders(userIds: List<String>) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.sendReminders(userIds)
            .onSuccess { successMsg.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadAdminSeats(shift: String, date: String? = null) = viewModelScope.launch {
        isLoading.value = true
        adminSeats.value = emptyList()
        adminRepo.getAdminSeatMap(shift, date)
            .onSuccess { adminSeats.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadFeedback() = viewModelScope.launch {
        adminRepo.getAllFeedback()
            .onSuccess { feedback.value = it }
            .onFailure { error.value = it.message }
    }

    fun updateFeedback(id: String, status: String, adminNotes: String?, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.updateFeedback(id, status, adminNotes)
            .onSuccess { loadFeedback(); onDone() }
            .onFailure { error.value = it.message }
    }

    fun sendBroadcast(message: String, targetGroup: String, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.sendBroadcast(message, targetGroup)
            .onSuccess { successMsg.value = it; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun sendMessageToStudent(id: String, message: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.sendMessageToStudent(id, message)
            .onSuccess { successMsg.value = "Message sent"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun loadPlans() = viewModelScope.launch {
        membershipRepo.getPlans().onSuccess { plans.value = it }
    }

    fun createCashMembership(req: CreateCashMembershipRequest, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.createCashMembership(req)
            .onSuccess { successMsg.value = "Membership created for seat ${it.seatNumber}"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadPendingFeeStudents() = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudentsWithPendingFees()
            .onSuccess { pendingFeeStudents.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun clearPendingFees(id: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.clearPendingFees(id)
            .onSuccess {
                pendingFeeStudents.value = pendingFeeStudents.value.filter { s -> s.id != id }
                successMsg.value = "Fees cleared"
                onDone()
            }
            .onFailure { error.value = it.message }
    }

    fun sendPendingFeeReminders() = viewModelScope.launch {
        isLoading.value = true
        adminRepo.sendPendingFeeReminders()
            .onSuccess { successMsg.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadBroadcastHistory() = viewModelScope.launch {
        adminRepo.getBroadcastHistory()
            .onSuccess { broadcastHistory.value = it }
            .onFailure { /* non-critical */ }
    }

    fun loadRevenueReport(from: String, to: String) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getRevenueReport(from, to)
            .onSuccess { revenueReport.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadDailyPayments(date: String) = viewModelScope.launch {
        adminRepo.getDailyPayments(date)
            .onSuccess { dailyPayments.value = it }
            .onFailure { error.value = it.message }
    }

    fun loadStudentPayments(userId: String) = viewModelScope.launch {
        adminRepo.getStudentPayments(userId)
            .onSuccess { studentPayments.value = it }
            .onFailure { /* non-critical */ }
    }

    fun loadInbox() = viewModelScope.launch {
        adminRepo.getInbox()
            .onSuccess { inboxMessages.value = it }
            .onFailure { error.value = it.message }
    }

    fun loadInboxMessage(messageNumber: Int) = viewModelScope.launch {
        adminRepo.getInboxMessage(messageNumber)
            .onSuccess { selectedInboxMsg.value = it }
            .onFailure { error.value = it.message }
    }

    fun replyToMessage(messageNumber: Int, body: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.replyToMessage(messageNumber, body)
            .onSuccess { successMsg.value = "Reply sent"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun deleteInboxMessage(messageNumber: Int, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.deleteInboxMessage(messageNumber)
            .onSuccess {
                inboxMessages.value = inboxMessages.value.filter { it.messageNumber != messageNumber }
                selectedInboxMsg.value = null
                onDone()
            }
            .onFailure { error.value = it.message }
    }

    fun importSingleStudent(req: ManualImportRequest, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.importSingleStudent(req)
            .onSuccess { successMsg.value = "${req.name} registered successfully"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun updateStudentProfile(id: String, req: UpdateStudentRequest, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.updateStudent(id, req)
            .onSuccess { selectedStudent.value = it; successMsg.value = "Profile updated"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun importStudents(file: okhttp3.MultipartBody.Part) = viewModelScope.launch {
        isLoading.value = true
        importResult.value = null
        adminRepo.importStudents(file)
            .onSuccess { importResult.value = it; successMsg.value = "Imported ${it.imported} of ${it.totalRows} rows" }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadExpenses(year: Int, month: Int) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getExpenses(year, month)
            .onSuccess { expense.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun saveExpenses(req: SaveExpenseRequest, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.saveExpenses(req)
            .onSuccess { expense.value = it; successMsg.value = "Expenses saved"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun clearMessages() { error.value = null; successMsg.value = null }

    fun loadGallery() = viewModelScope.launch {
        galleryRepo.getAll()
            .onSuccess { galleryPhotos.value = it }
            .onFailure { error.value = it.message }
    }

    fun uploadGalleryPhoto(file: MultipartBody.Part, caption: RequestBody?, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        galleryRepo.upload(file, caption)
            .onSuccess { loadGallery(); successMsg.value = "Photo uploaded"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun deleteGalleryPhoto(id: String, onDone: () -> Unit) = viewModelScope.launch {
        galleryRepo.delete(id)
            .onSuccess { galleryPhotos.value = galleryPhotos.value.filter { it.id != id }; onDone() }
            .onFailure { error.value = it.message }
    }
}
