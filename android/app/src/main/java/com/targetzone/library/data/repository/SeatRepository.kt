package com.targetzone.library.data.repository

import com.targetzone.library.data.api.ApiClient
import com.targetzone.library.data.model.BookSeatRequest
import com.targetzone.library.data.model.Seat

class SeatRepository {
    private val api = ApiClient.service

    suspend fun getAvailability(shift: String, date: String? = null): Result<List<Seat>> = runCatching {
        val res = api.getSeatAvailability(shift = shift, date = date)
        res.body()?.data?.seats?.map { item ->
            Seat(
                id = item.id,
                seatNumber = item.seatNumber,
                row = item.rowLabel,
                isBooked = item.isBooked
            )
        } ?: emptyList()
    }

    suspend fun bookSeat(req: BookSeatRequest): Result<Unit> = runCatching {
        api.bookSeat(req)
    }
}
