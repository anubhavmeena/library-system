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
    val paymentClaims = MutableStateFlow<List<AdminPaymentClaimItem>>(emptyList())
    val plans        = MutableStateFlow<List<Plan>>(emptyList())
    val appSettings  = MutableStateFlow<AppSettings?>(null)

    val expense      = MutableStateFlow<MonthlyExpense?>(null)
    val importResult = MutableStateFlow<ImportResult?>(null)
    val galleryPhotos = MutableStateFlow<List<GalleryPhoto>>(emptyList())

    // Pending fees
    val pendingFeeStudents = MutableStateFlow<List<StudentDetail>>(emptyList())

    // Grace-period dues & orphaned seats (Reminders screen extra tabs)
    val graceDuesStudents    = MutableStateFlow<List<StudentDetail>>(emptyList())
    val orphanedSeatStudents = MutableStateFlow<List<StudentDetail>>(emptyList())

    // Student search for New Membership flow — kept separate from `students`/`totalStudents`
    // (used by the Students list screen) so the two screens never clobber each other's state.
    val eligibleStudents = MutableStateFlow<List<StudentSummary>>(emptyList())

    // Exchange Seat picker — active, currently-seated students to swap with
    // (deliberately separate from eligibleStudents, which is the opposite
    // pool: NEW/RELEASED students with no seat, used by Create Membership).
    val swapCandidates = MutableStateFlow<List<StudentSummary>>(emptyList())

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

    fun searchSwapCandidates(query: String, excludeStudentId: String) = viewModelScope.launch {
        adminRepo.getStudents(page = 0, size = 200, search = query.takeIf { it.isNotBlank() }, membershipStatus = "ACTIVE")
            .onSuccess { (list, _) ->
                swapCandidates.value = list.filter { it.id != excludeStudentId && !it.seatNumber.isNullOrBlank() }
            }
            .onFailure { error.value = it.message }
    }

    fun swapSeat(membershipId: String, otherUserId: String, otherSeatNumber: String, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.swapSeat(membershipId, otherUserId)
            .onSuccess { successMsg.value = "Seat exchanged with seat $otherSeatNumber"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun updateMembershipPlan(membershipId: String, planId: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.updateMembershipPlan(membershipId, planId)
            .onSuccess { successMsg.value = "Plan updated"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun renewSeat(membershipId: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.renewSeat(membershipId)
            .onSuccess { successMsg.value = "Seat renewed"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun releaseSeat(membershipId: String, notifyStudent: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.releaseSeat(membershipId, notifyStudent)
            .onSuccess { successMsg.value = "Seat released"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun clearDues(membershipId: String, amountCleared: Double, paymentMode: String = "CASH", onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.clearDues(membershipId, amountCleared, paymentMode)
            .onSuccess { successMsg.value = "Dues cleared"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun markPending(membershipId: String, pendingAmount: Double, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.markPending(membershipId, pendingAmount)
            .onSuccess { successMsg.value = "Marked as Pending"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun markGrace(membershipId: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.markGrace(membershipId)
            .onSuccess { successMsg.value = "Marked as Grace"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun deleteStudent(id: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.deleteStudent(id)
            .onSuccess { successMsg.value = "Student deleted"; onDone() }
            .onFailure { error.value = it.message }
    }

    fun loadGraceDuesStudents() = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudentsInGraceWithDues()
            .onSuccess { graceDuesStudents.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun loadOrphanedSeatStudents() = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudentsWithOrphanedSeats()
            .onSuccess { orphanedSeatStudents.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun sendGraceDuesReminders(userIds: List<String>) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.sendGraceDuesReminders(userIds)
            .onSuccess { successMsg.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun searchEligibleStudents(query: String) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudents(page = 0, size = 200, search = query.takeIf { it.isNotBlank() })
            .onSuccess { (list, _) -> eligibleStudents.value = list.filter { it.displayStatus == "NEW" || it.displayStatus == "RELEASED" } }
            .onFailure { error.value = it.message }
        isLoading.value = false
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

    fun loadPaymentClaims(status: String? = "PENDING") = viewModelScope.launch {
        adminRepo.listPaymentClaims(status)
            .onSuccess { paymentClaims.value = it }
            .onFailure { error.value = it.message }
    }

    fun reviewPaymentClaim(id: String, status: String, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.reviewPaymentClaim(id, status)
            .onSuccess { successMsg.value = if (status == "VERIFIED") "Payment verified and applied" else "Claim rejected"; loadPaymentClaims(); onDone() }
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

    // Mints a short pay link server-side, then sends it to the student as a
    // WhatsApp message — two network calls chained so the caller (Create
    // Membership's "Send Payment Request" button) only needs one function.
    fun sendPaymentRequest(studentId: String, amount: Double, onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.createPayLink(studentId, amount)
            .onSuccess { link ->
                val message = "💰 Payment Request — Target Zone Library\n\n" +
                    "Please pay ₹${"%.2f".format(amount)} using this link (opens your UPI app):\n$link\n\nThank you!"
                adminRepo.sendMessageToStudent(studentId, message)
                    .onSuccess { successMsg.value = "Payment request sent"; onDone() }
                    .onFailure { error.value = it.message; onDone() }
            }
            .onFailure { error.value = it.message; onDone() }
    }

    fun loadPlans() = viewModelScope.launch {
        membershipRepo.getPlans().onSuccess { plans.value = it }
    }

    fun loadAppSettings() = viewModelScope.launch {
        adminRepo.getAppSettings().onSuccess { appSettings.value = it }
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

    fun clearPendingFees(id: String, amountCleared: Double, paymentMode: String = "CASH", onDone: () -> Unit) = viewModelScope.launch {
        adminRepo.clearPendingFees(id, amountCleared, paymentMode)
            .onSuccess {
                pendingFeeStudents.value = pendingFeeStudents.value.filter { s -> s.id != id }
                successMsg.value = "Fees cleared"
                onDone()
            }
            .onFailure { error.value = it.message }
    }

    fun sendPendingFeeReminders(userIds: List<String>) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.sendPendingFeeReminders(userIds)
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

    fun importSingleStudent(name: String, phone: String, photo: java.io.File?, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.importSingleStudent(name, phone, photo)
            .onSuccess { successMsg.value = "$name registered successfully"; onDone() }
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
