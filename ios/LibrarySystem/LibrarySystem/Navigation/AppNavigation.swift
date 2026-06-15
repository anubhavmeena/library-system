import SwiftUI

struct AppNavigation: View {
    @ObservedObject private var tokenManager = TokenManager.shared
    @State private var showSplash = true

    var body: some View {
        Group {
            if showSplash {
                SplashView { showSplash = false }
            } else if let user = tokenManager.currentUser {
                if user.role == "ADMIN" {
                    AdminTabView()
                } else {
                    StudentTabView()
                }
            } else {
                authFlow
            }
        }
        .animation(.easeInOut(duration: 0.3), value: tokenManager.currentUser == nil)
    }

    private var authFlow: some View {
        NavigationStack {
            LoginView()
        }
    }
}
