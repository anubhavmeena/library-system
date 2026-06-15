import SwiftUI

struct LoadingView: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .tint(.amber)
                .scaleEffect(1.4)
            Text("Loading...")
                .font(.bodyMedium)
                .foregroundColor(.textSub)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ErrorView: View {
    let message: String
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundColor(.redAlert)
            Text(message)
                .font(.bodyMedium)
                .foregroundColor(.textSub)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            if let retry = onRetry {
                OutlineButton("Retry", action: retry)
                    .frame(width: 140)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(.redAlert)
            Text(message)
                .font(.bodySmall)
                .foregroundColor(.textPrimary)
            Spacer()
        }
        .padding(12)
        .background(Color.redFaint)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.redAlert.opacity(0.4), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
