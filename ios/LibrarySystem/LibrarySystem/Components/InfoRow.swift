import SwiftUI

struct InfoRow: View {
    let label: String
    let value: String
    var valueColor: Color = .textPrimary

    var body: some View {
        HStack {
            Text(label)
                .font(.bodySmall)
                .foregroundColor(.textMuted)
            Spacer()
            Text(value)
                .font(.labelMedium)
                .foregroundColor(valueColor)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 8)
        Divider().background(Color.dividerColor)
    }
}
