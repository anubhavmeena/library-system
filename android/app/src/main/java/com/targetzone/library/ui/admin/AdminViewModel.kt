package com.targetzone.library.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetzone.library.data.model.*
import com.targetzone.library.data.repository.AdminRepository
import com.targetzone.library.data.repository.MembershipRepository
import com.targetzone.library.data.repository.SeatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminRepo: AdminRepository = AdminRepository(),
    private val seatRepo: SeatRepository = SeatRepository(),
    private val membershipRepo: MembershipRepository = MembershipRepository()
) : ViewModel() {

    val stats        = MutableStateFlow<AdminStats?>(null)
    val students     = MutableStateFlow<List<StudentSummary>>(emptyList())
    val seats        = MutableStateFlow<List<Seat>>(emptyList())    // student-facing availability
    val adminSeats   = MutableStateFlow<List<Seat>>(emptyList())    // admin seat map with student details
    val expiring     = MutableStateFlow<List<ReminderStudent>>(emptyList())
    val feedback     = MutableStateFlow<List<FeedbackItem>>(emptyList())
    val plans        = MutableStateFlow<List<Plan>>(emptyList())

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

    fun loadStudents(page: Int = 0, status: String? = null, membershipStatus: String? = null, search: String? = null) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.getStudents(page, status, membershipStatus, search)
            .onSuccess { students.value = it }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun toggleStudentStatus(id: String, currentActive: Boolean) = viewModelScope.launch {
        adminRepo.toggleStudentStatus(id, !currentActive)
            .onSuccess { loadStudents() }
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

    fun loadPlans() = viewModelScope.launch {
        membershipRepo.getPlans().onSuccess { plans.value = it }
    }

    fun createMembership(req: CreateMembershipRequest, onDone: () -> Unit) = viewModelScope.launch {
        isLoading.value = true
        adminRepo.createMembership(req)
            .onSuccess { successMsg.value = "Membership created for seat ${it.seatNumber}"; onDone() }
            .onFailure { error.value = it.message }
        isLoading.value = false
    }

    fun clearMessages() { error.value = null; successMsg.value = null }
}
