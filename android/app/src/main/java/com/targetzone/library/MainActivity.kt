package com.targetzone.library

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.targetzone.library.ui.navigation.AppNavigation
import com.targetzone.library.ui.theme.LibraryTheme

class MainActivity : AppCompatActivity(), PaymentResultWithDataListener, CFCheckoutResponseCallback {

    // Razorpay callback
    var onPaymentResult: ((success: Boolean, paymentId: String?, orderId: String?, signature: String?, error: String?) -> Unit)? = null
    // Cashfree callback
    var onCashfreeResult: ((success: Boolean, orderId: String?, error: String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm Razorpay
        Checkout.preload(applicationContext)
        // Initialize and register Cashfree callback
        CFPaymentGatewayService.initialize(applicationContext)
        try {
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)
        } catch (e: Exception) {
            Log.e("Cashfree", "Failed to register checkout callback: ${e.message}")
        }

        val tokenManager = (application as LibraryApp).tokenManager

        setContent {
            LibraryTheme {
                AppNavigation(tokenManager = tokenManager)
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        onPaymentResult?.invoke(true, razorpayPaymentId, data?.orderId, data?.signature, null)
    }

    override fun onPaymentError(code: Int, description: String?, data: PaymentData?) {
        onPaymentResult?.invoke(false, null, null, null, description ?: "Payment failed")
    }

    // Cashfree callbacks
    override fun onPaymentVerify(orderID: String) {
        Log.i("Cashfree", "onPaymentVerify: orderID=$orderID")
        onCashfreeResult?.invoke(true, orderID, null)
    }

    override fun onPaymentFailure(cfErrorResponse: CFErrorResponse, orderID: String) {
        val msg = "[${cfErrorResponse.code}] ${cfErrorResponse.message} (type=${cfErrorResponse.type}, status=${cfErrorResponse.status})"
        Log.e("Cashfree", "onPaymentFailure: orderID=$orderID $msg")
        onCashfreeResult?.invoke(false, null, cfErrorResponse.message ?: cfErrorResponse.code ?: "Payment failed")
    }
}
