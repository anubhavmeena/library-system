import SwiftUI

// Reads CFBundleShortVersionString/CFBundleVersion, which Info.plist wires to
// the Xcode project's MARKETING_VERSION/CURRENT_PROJECT_VERSION build
// settings — so this always reflects whatever was actually built, no manual
// syncing needed.
struct VersionFooter: View {
    private var version: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—" }
    private var build: String { Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—" }

    var body: some View {
        Text("Version \(version) (\(build))")
            .font(.caption)
            .foregroundColor(.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.top, 4)
    }
}
