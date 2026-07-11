import SwiftUI
import CoreImage.CIFilterBuiltins

// Mirrors frontend/src/utils/upiPay.js and android/.../UpiQrGenerator.kt —
// kept in sync manually, no shared codegen between the three clients here.
// Uses Core Image's built-in QR generator (part of the standard iOS SDK —
// no third-party dependency needed).
func buildUpiDeepLink(vpa: String, payeeName: String, amount: Double, note: String) -> String {
    var components = URLComponents()
    components.scheme = "upi"
    components.host = "pay"
    components.queryItems = [
        URLQueryItem(name: "pa", value: vpa),
        URLQueryItem(name: "pn", value: payeeName),
        URLQueryItem(name: "am", value: String(format: "%.2f", amount)),
        URLQueryItem(name: "cu", value: "INR"),
        URLQueryItem(name: "tn", value: note),
    ]
    return components.url?.absoluteString ?? "upi://pay"
}

func generateQrImage(from string: String, sizePt: CGFloat = 240) -> UIImage {
    let context = CIContext()
    let filter = CIFilter.qrCodeGenerator()
    filter.message = Data(string.utf8)
    filter.correctionLevel = "M"

    guard let outputImage = filter.outputImage else {
        return UIImage(systemName: "qrcode") ?? UIImage()
    }
    // The raw CI output is only a few pixels across — scale up before
    // rendering so it isn't blurry at display size.
    let scale = sizePt / outputImage.extent.width
    let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

    guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else {
        return UIImage(systemName: "qrcode") ?? UIImage()
    }
    return UIImage(cgImage: cgImage)
}
