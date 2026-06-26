import SwiftUI

struct ContactAdminView: View {
    @ObservedObject var vm: StudentViewModel

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        contactCard
                        if let m = vm.membership, m.status == "ACTIVE", !m.seatNumber.isEmpty {
                            callAdminCard(m.seatNumber)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Contact Admin")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadAdminContact() }
    }

    // MARK: - Admin contact info card

    private var contactCard: some View {
        AppCard {
            HStack {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 22))
                    .foregroundColor(.amber)
                Text("Admin Contact")
                    .font(.headlineSmall)
                    .foregroundColor(.textPrimary)
            }
            .padding(.bottom, 4)

            Divider().background(Color.white.opacity(0.1))
                .padding(.vertical, 4)

            if let contact = vm.adminContact {
                VStack(spacing: 12) {
                    if let name = contact.name {
                        contactRow(label: "Name", value: name, actions: [])
                    }
                    if let mobile = contact.mobile {
                        contactRow(label: "Mobile", value: mobile, actions: [
                            ContactAction(label: "Call", icon: "phone.fill", color: .blueSoft,
                                          url: "tel:\(mobile)"),
                            ContactAction(label: "WhatsApp", icon: "message.fill", color: .emerald,
                                          url: "https://wa.me/91\(mobile)"),
                        ])
                    }
                    if let email = contact.email {
                        contactRow(label: "Email", value: email, actions: [
                            ContactAction(label: "Email", icon: "envelope.fill", color: .amber,
                                          url: "mailto:\(email)"),
                        ])
                    }
                    if contact.mobile == nil && contact.email == nil {
                        Text("Contact details not available.")
                            .font(.bodySmall)
                            .foregroundColor(.textMuted)
                    }
                }
            } else {
                VStack(spacing: 8) {
                    ForEach(0..<3, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Color.white.opacity(0.08))
                            .frame(height: 14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }

    private func contactRow(label: String, value: String, actions: [ContactAction]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(label)
                    .font(.bodySmall)
                    .foregroundColor(.textMuted)
                    .frame(width: 56, alignment: .leading)
                Text(value)
                    .font(.bodyMedium)
                    .foregroundColor(.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !actions.isEmpty {
                HStack(spacing: 8) {
                    Spacer().frame(width: 56)
                    ForEach(actions) { action in
                        Button {
                            if let url = URL(string: action.url) {
                                UIApplication.shared.open(url)
                            }
                        } label: {
                            Label(action.label, systemImage: action.icon)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(action.color)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(action.color.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }
                }
            }
        }
    }

    // MARK: - Call Admin to Seat card

    private func callAdminCard(_ seatNumber: String) -> some View {
        AppCard {
            HStack(alignment: .top, spacing: 12) {
                Text("🙋")
                    .font(.system(size: 28))

                VStack(alignment: .leading, spacing: 6) {
                    Text("Need help at your seat?")
                        .font(.headlineSmall)
                        .foregroundColor(.blueSoft)

                    Text("Seat \(seatNumber) — tap to notify the admin via WhatsApp. They'll come to you.")
                        .font(.bodySmall)
                        .foregroundColor(.textSub)
                        .fixedSize(horizontal: false, vertical: true)

                    Button {
                        vm.callAdmin()
                    } label: {
                        HStack(spacing: 6) {
                            if vm.callAdminSent {
                                Image(systemName: "checkmark")
                                Text("Admin Notified")
                            } else {
                                Image(systemName: "bell.fill")
                                Text("Call Admin to My Seat")
                            }
                        }
                        .font(.bodyMedium.weight(.semibold))
                        .foregroundColor(Color.navyDeep)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(vm.callAdminSent ? Color.blueSoft.opacity(0.5) : Color.blueSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .disabled(vm.callAdminSent)
                    .padding(.top, 4)
                }
            }
        }
    }
}

private struct ContactAction: Identifiable {
    let id = UUID()
    let label: String
    let icon: String
    let color: Color
    let url: String
}
