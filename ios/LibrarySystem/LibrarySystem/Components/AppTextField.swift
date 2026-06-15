import SwiftUI

struct AppTextField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var keyboardType: UIKeyboardType = .default
    var isSecure: Bool = false
    var leadingIcon: String? = nil

    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.labelMedium)
                .foregroundColor(.textSub)

            HStack(spacing: 8) {
                if let icon = leadingIcon {
                    Image(systemName: icon)
                        .foregroundColor(focused ? .amber : .textMuted)
                        .frame(width: 20)
                }
                Group {
                    if isSecure {
                        SecureField(placeholder, text: $text)
                    } else {
                        TextField(placeholder, text: $text)
                            .keyboardType(keyboardType)
                    }
                }
                .font(.bodyLarge)
                .foregroundColor(.textPrimary)
            }
            .padding(12)
            .background(Color.cardBg)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(focused ? Color.amber : Color.cardBorder, lineWidth: focused ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .focused($focused)
        }
    }
}
