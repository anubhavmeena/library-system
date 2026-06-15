import SwiftUI

struct PrimaryButton: View {
    let title: String
    let isLoading: Bool
    let action: () -> Void

    init(_ title: String, isLoading: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.isLoading = isLoading
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                if isLoading {
                    ProgressView().tint(.navyDeep)
                } else {
                    Text(title)
                        .font(.headlineSmall)
                        .foregroundColor(.navyDeep)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 50)
        }
        .background(Color.amber)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .disabled(isLoading)
    }
}

struct OutlineButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.headlineSmall)
                .foregroundColor(.amber)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.amber, lineWidth: 1.5)
        )
    }
}
