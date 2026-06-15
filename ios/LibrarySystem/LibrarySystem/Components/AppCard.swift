import SwiftUI

struct AppCard<Content: View>: View {
    let accentColor: Color?
    let content: () -> Content

    init(accentColor: Color? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.accentColor = accentColor
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let accent = accentColor {
                Rectangle()
                    .fill(accent)
                    .frame(height: 3)
                    .clipShape(RoundedCorner(radius: 12, corners: [.topLeft, .topRight]))
            }
            content()
                .padding(16)
        }
        .background(Color.cardBg)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.cardBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat
    var corners: UIRectCorner

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
