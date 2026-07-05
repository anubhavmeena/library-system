import SwiftUI
import WebKit

// Mirrors RazorpayWebView's WKWebView + JS-bridge pattern, but drives
// Cashfree's v3 JS SDK (loaded from Cashfree's CDN) instead of Razorpay's
// checkout.js. Cashfree's own signature-less flow means success only ever
// reports back an outcome — the caller already knows orderId/membershipId
// from the PaymentOrder it was given, and verification happens server-side
// via a status poll (see PaymentService.verifyGatewayPayment), not a
// client-supplied signature.
struct CashfreeWebView: UIViewRepresentable {
    let order: PaymentOrder
    let mode: String // "sandbox" | "production"
    let onSuccess: () -> Void
    let onFailure: (String) -> Void
    let onDismiss: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onSuccess: onSuccess, onFailure: onFailure, onDismiss: onDismiss)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "cashfreeBridge")
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.backgroundColor = UIColor(Color.navyDeep)
        wv.isOpaque = false

        if let html = buildHTML() {
            // base URL must be Cashfree's domain so the SDK's own same-origin
            // checks (and any redirect_target handling) behave as expected
            wv.loadHTMLString(html, baseURL: URL(string: "https://sdk.cashfree.com"))
        }
        return wv
    }

    func updateUIView(_ wv: WKWebView, context: Context) {}

    private func buildHTML() -> String? {
        guard let path = Bundle.main.path(forResource: "cashfree_checkout", ofType: "html"),
              var html = try? String(contentsOfFile: path, encoding: .utf8) else { return nil }
        html = html
            .replacingOccurrences(of: "{{MODE}}", with: mode)
            .replacingOccurrences(of: "{{PAYMENT_SESSION_ID}}", with: order.paymentSessionId ?? "")
        return html
    }

    class Coordinator: NSObject, WKScriptMessageHandler {
        let onSuccess: () -> Void
        let onFailure: (String) -> Void
        let onDismiss: () -> Void

        init(onSuccess: @escaping () -> Void, onFailure: @escaping (String) -> Void,
             onDismiss: @escaping () -> Void) {
            self.onSuccess = onSuccess
            self.onFailure = onFailure
            self.onDismiss = onDismiss
        }

        func userContentController(_ controller: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: String] else { return }
            switch body["type"] ?? "" {
            case "success":
                DispatchQueue.main.async { self.onSuccess() }
            case "failed":
                DispatchQueue.main.async { self.onFailure(body["error"] ?? "Payment failed") }
            default:
                DispatchQueue.main.async { self.onDismiss() }
            }
        }
    }
}
