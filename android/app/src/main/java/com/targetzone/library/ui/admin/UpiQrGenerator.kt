package com.targetzone.library.ui.admin

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.targetzone.library.data.api.BASE_URL
import java.net.URLEncoder

// Mirrors frontend/src/utils/upiPay.js — kept in sync manually, no shared
// codegen between the web and Android clients here.
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

fun buildUpiDeepLink(vpa: String, payeeName: String, amount: Double, note: String): String {
    val amountStr = "%.2f".format(amount)
    return "upi://pay?pa=${enc(vpa)}&pn=${enc(payeeName)}&am=${enc(amountStr)}&cu=INR&tn=${enc(note)}"
}

fun buildPayRedirectLink(vpa: String, payeeName: String, amount: Double, note: String): String {
    val origin = BASE_URL.removeSuffix("api/").removeSuffix("/")
    val amountStr = "%.2f".format(amount)
    return "$origin/pay?pa=${enc(vpa)}&pn=${enc(payeeName)}&am=${enc(amountStr)}&cu=INR&tn=${enc(note)}"
}

// Encode-only — no scanning/camera dependency, just com.google.zxing:core.
fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
