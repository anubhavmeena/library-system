package com.targetzone.library.ui.admin

import androidx.compose.ui.graphics.Color
import com.targetzone.library.ui.theme.Amber
import com.targetzone.library.ui.theme.AmberFaint
import com.targetzone.library.ui.theme.BlueFaint
import com.targetzone.library.ui.theme.BlueSoft
import com.targetzone.library.ui.theme.Indigo
import com.targetzone.library.ui.theme.IndigoFaint

// Shared bucketing for the admin-facing "Payment Mode" concept, mirroring
// frontend/src/utils/paymentMode.js. Works on both the bucketed `paymentMode`
// field (CASH / UPI-QR / ONLINE-PG / null) and the raw `paymentGateway` field
// on individual payment rows (CASH / UPI-QR / RAZORPAY / CASHFREE / ...) —
// anything that isn't CASH or UPI-QR falls into the generic "Online" bucket.
data class PaymentModeInfo(val label: String, val color: Color, val faintColor: Color)

fun paymentModeInfo(mode: String?): PaymentModeInfo = when (mode) {
    "CASH" -> PaymentModeInfo("💵 Cash", Amber, AmberFaint)
    "UPI-QR" -> PaymentModeInfo("📱 UPI (QR)", BlueSoft, BlueFaint)
    null -> PaymentModeInfo("—", Indigo, IndigoFaint)
    else -> PaymentModeInfo("💳 Online", Indigo, IndigoFaint)
}

fun paymentModeLabel(mode: String?): String = paymentModeInfo(mode).label
