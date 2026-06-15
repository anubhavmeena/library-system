import SwiftUI
import WebKit

struct RazorpayWebView: UIViewRepresentable {
    let order: PaymentOrder
    let onSuccess: (String, String, String) -> Void
    let onDismiss: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onSuccess: onSuccess, onDismiss: onDismiss)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "razorpayBridge")
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.backgroundColor = UIColor(Color.navyDeep)
        wv.isOpaque = false

        if let html = buildHTML() {
            // base URL must be Razorpay's domain so checkout.js same-origin checks pass
            wv.loadHTMLString(html, baseURL: URL(string: "https://api.razorpay.com"))
        }
        return wv
    }

    func updateUIView(_ wv: WKWebView, context: Context) {}

    private func buildHTML() -> String? {
        guard let path = Bundle.main.path(forResource: "razorpay_checkout", ofType: "html"),
              var html = try? String(contentsOfFile: path, encoding: .utf8) else { return nil }
        let amountPaise = Int(order.amount * 100)
        html = html
            .replacingOccurrences(of: "{{ORDER_ID}}",    with: order.orderId)
            .replacingOccurrences(of: "{{AMOUNT_PAISE}}", with: "\(amountPaise)")
            .replacingOccurrences(of: "{{KEY_ID}}",      with: order.razorpayKeyId)
            .replacingOccurrences(of: "{{MEMBERSHIP_ID}}", with: order.membershipId)
        return html
    }

    class Coordinator: NSObject, WKScriptMessageHandler {
        let onSuccess: (String, String, String) -> Void
        let onDismiss: () -> Void

        init(onSuccess: @escaping (String, String, String) -> Void,
             onDismiss: @escaping () -> Void) {
            self.onSuccess = onSuccess
            self.onDismiss = onDismiss
        }

        func userContentController(_ controller: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: String] else { return }
            let type = body["type"] ?? ""
            switch type {
            case "success":
                let paymentId  = body["paymentId"]  ?? ""
                let orderId    = body["orderId"]     ?? ""
                let signature  = body["signature"]   ?? ""
                DispatchQueue.main.async { self.onSuccess(paymentId, orderId, signature) }
            default:
                DispatchQueue.main.async { self.onDismiss() }
            }
        }
    }
}
