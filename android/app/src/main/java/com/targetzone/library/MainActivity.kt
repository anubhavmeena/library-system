package com.targetzone.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.targetzone.library.ui.navigation.AppNavigation
import com.targetzone.library.ui.theme.LibraryTheme

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    // Callback invoked by AppNavigation's BookingScreen via a shared event
    var onPaymentResult: ((success: Boolean, paymentId: String?, orderId: String?, signature: String?, error: String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm Razorpay
        Checkout.preload(applicationContext)

        val tokenManager = (application as LibraryApp).tokenManager

        setContent {
            LibraryTheme {
                AppNavigation(tokenManager = tokenManager)
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        onPaymentResult?.invoke(
            true,
            razorpayPaymentId,
            data?.orderId,
            data?.signature,
            null
        )
    }

    override fun onPaymentError(code: Int, description: String?, data: PaymentData?) {
        onPaymentResult?.invoke(false, null, null, null, description ?: "Payment failed")
    }
}
