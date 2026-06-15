import SwiftUI

struct StatusChip: View {
    let status: String

    private var color: Color {
        switch status.uppercased() {
        case "ACTIVE":              return .emerald
        case "PENDING":             return .amber
        case "EXPIRED", "INACTIVE": return .redAlert
        case "OPEN":                return .amber
        case "IN_PROGRESS", "UNDER_REVIEW": return .blueSoft
        case "RESOLVED", "CLOSED": return .emerald
        default:                    return .textMuted
        }
    }

    var body: some View {
        Text(status.replacingOccurrences(of: "_", with: " "))
            .font(.labelSmall)
            .foregroundColor(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(color.opacity(0.15))
            .clipShape(Capsule())
    }
}
