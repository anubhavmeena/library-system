package com.targetzone.library.ui.student

import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.CFTheme
import com.cashfree.pg.ui.api.CFDropCheckoutPayment
import com.razorpay.Checkout
import com.targetzone.library.BuildConfig
import com.targetzone.library.MainActivity
import com.targetzone.library.data.model.PaymentOrder
import org.json.JSONObject

// Shared gateway-launch helpers — used by both BookingScreen (new membership
// payment) and MembershipScreen (GRACE dues payment). Both flows produce the
// same PaymentOrder shape from the backend, so the checkout UI plumbing is
// identical; only the verify call afterward differs per screen.

fun openRazorpay(
    activity: MainActivity?,
    order: PaymentOrder,
    callback: (success: Boolean, paymentId: String?, orderId: String?, signature: String?, error: String?) -> Unit
) {
    activity ?: return
    activity.onPaymentResult = callback
    val checkout = Checkout()
    checkout.setKeyID(order.razorpayKeyId)
    val options = JSONObject().apply {
        put("name", "Target Zone Library")
        put("description", "Library Membership")
        put("order_id", order.orderId)
        put("amount", (order.amount * 100).toLong())
        put("currency", "INR")
        put("theme", JSONObject().put("color", "#F59E0B"))
    }
    checkout.open(activity, options)
}

fun openCashfree(
    activity: MainActivity?,
    order: PaymentOrder,
    callback: (success: Boolean, orderId: String?, error: String?) -> Unit
) {
    activity ?: return
    activity.onCashfreeResult = callback
    try {
        val env = if (BuildConfig.CASHFREE_ENV == "production")
            CFSession.Environment.PRODUCTION else CFSession.Environment.SANDBOX
        val cfSession = CFSession.CFSessionBuilder()
            .setEnvironment(env)
            .setPaymentSessionID(order.paymentSessionId!!)
            .setOrderId(order.orderId)
            .build()
        val cfTheme = CFTheme.CFThemeBuilder()
            .setPrimaryTextColor("#FFFFFF")
            .setSecondaryTextColor("#AAAAAA")
            .setBackgroundColor("#0D1B4B")
            .setNavigationBarBackgroundColor("#0D1B4B")
            .setNavigationBarTextColor("#FFFFFF")
            .setButtonBackgroundColor("#F59E0B")
            .setButtonTextColor("#000000")
            .build()
        val cfPayment = CFDropCheckoutPayment.CFDropCheckoutPaymentBuilder()
            .setSession(cfSession)
            .setCFNativeCheckoutUITheme(cfTheme)
            .build()
        CFPaymentGatewayService.getInstance().doPayment(activity, cfPayment)
    } catch (e: Exception) {
        callback(false, null, e.message ?: "Failed to open payment")
    }
}
