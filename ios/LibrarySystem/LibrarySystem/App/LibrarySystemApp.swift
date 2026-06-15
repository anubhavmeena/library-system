import SwiftUI

@main
struct LibrarySystemApp: App {
    var body: some Scene {
        WindowGroup {
            AppNavigation()
                .preferredColorScheme(.dark)
                .tint(.amber)
        }
    }
}
