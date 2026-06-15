import SwiftUI

struct SplashView: View {
    let onComplete: () -> Void

    @State private var iconOpacity = 0.0
    @State private var iconScale   = 0.7
    @State private var textWidth   = 0.0
    @State private var textOpacity = 0.0

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()

            VStack(spacing: 20) {
                Image(systemName: "books.vertical.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.amber)
                    .opacity(iconOpacity)
                    .scaleEffect(iconScale)

                HStack(spacing: 0) {
                    Text("TARGET")
                        .font(.system(size: 26, weight: .black))
                        .foregroundColor(.textPrimary)
                    Text("ZONE")
                        .font(.system(size: 26, weight: .black))
                        .foregroundColor(.amber)
                }
                .opacity(textOpacity)
                .clipShape(Rectangle().offset(x: -(1 - textWidth) * 200))

                Text("Library Management")
                    .font(.bodyMedium)
                    .foregroundColor(.textSub)
                    .opacity(textOpacity)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5)) {
                iconOpacity = 1
                iconScale   = 1
            }
            withAnimation(.easeOut(duration: 0.6).delay(0.4)) {
                textOpacity = 1
                textWidth   = 1
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                onComplete()
            }
        }
    }
}
