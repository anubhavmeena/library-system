import SwiftUI

struct AdminBroadcastView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var message     = ""
    @State private var targetGroup = "ALL"
    private let maxChars = 1000

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        // Warning card
                        HStack(spacing: 10) {
                            Image(systemName: "megaphone.fill").foregroundColor(.amber)
                            Text("This message will be sent via WhatsApp to selected students.")
                                .font(.bodySmall).foregroundColor(.textSub)
                        }
                        .padding(12)
                        .background(Color.amberFaint)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.4)))
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                        // Target group
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Send To").font(.labelMedium).foregroundColor(.textSub)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach([("ALL","All Students"),("ACTIVE","Active Only"),("EXPIRING","Expiring Soon")], id: \.0) { g in
                                        let selected = targetGroup == g.0
                                        Button { targetGroup = g.0 } label: {
                                            Text(g.1)
                                                .font(.labelSmall)
                                                .foregroundColor(selected ? .navyDeep : .textSub)
                                                .padding(.horizontal, 12).padding(.vertical, 6)
                                                .background(selected ? Color.amber : Color.cardBg)
                                                .clipShape(Capsule())
                                                .overlay(Capsule().stroke(selected ? Color.amber : Color.cardBorder))
                                        }
                                    }
                                }
                            }
                        }

                        // Message input
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("Message").font(.labelMedium).foregroundColor(.textSub)
                                Spacer()
                                Text("\(message.count)/\(maxChars)")
                                    .font(.caption)
                                    .foregroundColor(message.count > maxChars * 9 / 10 ? .redAlert : .textMuted)
                            }
                            TextEditor(text: $message)
                                .font(.bodyMedium).foregroundColor(.textPrimary)
                                .scrollContentBackground(.hidden).background(Color.cardBg)
                                .frame(minHeight: 160).padding(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .onChange(of: message) { if $0.count > maxChars { message = String($0.prefix(maxChars)) } }
                        }

                        if let err = vm.error { ErrorBanner(message: err) }
                        if let msg = vm.successMsg {
                            HStack { Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                                Text(msg).foregroundColor(.textPrimary) }
                        }

                        PrimaryButton("Send Broadcast", isLoading: vm.isLoading) {
                            guard message.count >= 5 else { return }
                            vm.sendBroadcast(message: message, targetGroup: targetGroup)
                            message = ""
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Broadcast")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}
