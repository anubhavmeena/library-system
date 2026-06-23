package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.*

class MembershipRepository {
    private val api = ApiClient.service

    suspend fun getMyMembership(): Result<Membership?> = runCatching {
        val res = api.getMyMembership()
        res.body()?.data
    }

    suspend fun getMembershipHistory(): Result<List<Membership>> = runCatching {
        val res = api.getMembershipHistory()
        res.body()?.data ?: emptyList()
    }

    suspend fun getPlans(): Result<List<Plan>> = runCatching {
        val res = api.getPlans()
        res.body()?.data ?: emptyList()
    }

    suspend fun createOrder(planId: String, seatNumber: String, shift: String): Result<PaymentOrder> = runCatching {
        val res = api.createOrder(CreateOrderRequest(planId = planId, seatNumber = seatNumber, shift = shift))
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to create order")
    }

    suspend fun verifyPayment(req: VerifyPaymentRequest): Result<Membership> = runCatching {
        val res = api.verifyPayment(req)
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Payment verification failed")
    }

    suspend fun submitFeedback(type: String, subject: String, description: String): Result<FeedbackItem> = runCatching {
        val res = api.submitFeedback(SubmitFeedbackRequest(type = type, subject = subject, description = description))
        res.body()?.data ?: throw Exception(res.body()?.message ?: "Failed to submit feedback")
    }

    suspend fun getMyFeedback(): Result<List<FeedbackItem>> = runCatching {
        val res = api.getMyFeedback()
        res.body()?.data ?: emptyList()
    }

    suspend fun getQueuedMembership(): Result<Membership?> = runCatching {
        val res = api.getQueuedMembership()
        res.body()?.data
    }

    suspend fun getMyPayments(): Result<List<StudentPayment>> = runCatching {
        val res = api.getPaymentHistory()
        res.body()?.data ?: emptyList()
    }
}
