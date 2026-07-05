import SwiftUI

private struct NotificationCatalogEntry {
    let key: String
    let label: String
    let description: String
    let isBroadcast: Bool

    init(key: String, label: String, description: String, isBroadcast: Bool = false) {
        self.key = key
        self.label = label
        self.description = description
        self.isBroadcast = isBroadcast
    }
}

// Labels/descriptions only — the actual message templates live in
// notification-service's NotificationService.java. Kept in the same order as
// the web catalog (AdminSettingsPage.jsx) so both surfaces list identically.
private let notificationCatalog: [NotificationCatalogEntry] = [
    .init(key: "BOOKING_CONFIRMED", label: "Booking Confirmed",
          description: "Sent when a student's payment is verified and their seat is booked."),
    .init(key: "STUDENT_ID_CARD", label: "Student ID Card",
          description: "The ID card sent right after booking confirmation. Hindi only affects the emailed copy."),
    .init(key: "USER_REGISTERED", label: "Welcome / New Registration",
          description: "Sent right after a student creates their account."),
    .init(key: "RENEWAL_REMINDER", label: "Renewal Reminder",
          description: "Sent 7 and 3 days before a membership expires, or manually from Admin → Reminders."),
    .init(key: "PENDING_FEE_REMINDER", label: "Pending Fee Reminder",
          description: "Manual admin reminder for a student with an outstanding cash balance."),
    .init(key: "GRACE_DUES_REMINDER", label: "Grace Dues Reminder",
          description: "Manual admin reminder for a student in grace period with unpaid dues."),
    .init(key: "MEMBERSHIP_GRACE", label: "Membership Entered Grace Period",
          description: "Fired automatically the moment a membership lapses into grace."),
    .init(key: "PENDING_FEE_CLEARED", label: "Pending Fee Cleared",
          description: "Confirmation sent after a cash balance is cleared."),
    .init(key: "GRACE_DUES_CLEARED", label: "Grace Dues Cleared",
          description: "Sent when an admin clears a grace-period student's dues. Ships bilingual by default."),
    .init(key: "PAYMENT_RECEIPT", label: "Payment Receipt",
          description: "The receipt sent after any confirmed payment."),
    .init(key: "ADMIN_BROADCAST", label: "Broadcast Messages",
          description: "The ad-hoc message an admin sends to active members from Admin → Broadcast.",
          isBroadcast: true),
]

struct AdminSettingsView: View {
    @ObservedObject var vm: AdminViewModel

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        Text("Turn notifications on/off, choose who receives them, and optionally add a Hindi translation appended to the same WhatsApp/email message.")
                            .font(.bodySmall).foregroundColor(.textSub)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        if vm.notificationSettings.isEmpty {
                            LoadingView().frame(height: 200)
                        } else {
                            ForEach(notificationCatalog, id: \.key) { entry in
                                if let row = vm.notificationSettings.first(where: { $0.notificationKey == entry.key }) {
                                    NotificationSettingCard(entry: entry, row: row, vm: vm)
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Notification Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadNotificationSettings() }
    }
}

private struct NotificationSettingCard: View {
    let entry: NotificationCatalogEntry
    let row: NotificationSetting
    @ObservedObject var vm: AdminViewModel

    @State private var sendToStudent: Bool
    @State private var sendToAdmin: Bool
    @State private var hindiEnabled: Bool
    @State private var hindiTextStudent: String
    @State private var hindiTextAdmin: String

    init(entry: NotificationCatalogEntry, row: NotificationSetting, vm: AdminViewModel) {
        self.entry = entry
        self.row = row
        self.vm = vm
        _sendToStudent    = State(initialValue: row.sendToStudent)
        _sendToAdmin      = State(initialValue: row.sendToAdmin)
        _hindiEnabled     = State(initialValue: row.hindiEnabled)
        _hindiTextStudent = State(initialValue: row.hindiTextStudent ?? "")
        _hindiTextAdmin   = State(initialValue: row.hindiTextAdmin ?? "")
    }

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(entry.label).font(.labelLarge).foregroundColor(.textPrimary)
                Text(entry.description).font(.bodySmall).foregroundColor(.textSub)

                if entry.isBroadcast {
                    toggleRow("Enable Broadcast Feature", isOn: $sendToStudent, enabled: true)
                } else {
                    toggleRow("Send to Student", isOn: $sendToStudent, enabled: row.studentEditable)
                    toggleRow(row.adminEditable ? "Send to Admin" : "Send to Admin (not available)",
                              isOn: $sendToAdmin, enabled: row.adminEditable)
                }

                if row.hindiEditable {
                    Divider().background(Color.dividerColor)
                    toggleRow("Include Hindi translation", isOn: $hindiEnabled, enabled: true)
                    if hindiEnabled {
                        if sendToStudent {
                            hindiField("Student message — Hindi", text: $hindiTextStudent)
                        }
                        if sendToAdmin {
                            hindiField("Admin message — Hindi", text: $hindiTextAdmin)
                        }
                    }
                }

                Button {
                    vm.updateNotificationSetting(
                        key: entry.key, sendToStudent: sendToStudent, sendToAdmin: sendToAdmin,
                        hindiEnabled: hindiEnabled,
                        hindiTextStudent: hindiTextStudent.isEmpty ? nil : hindiTextStudent,
                        hindiTextAdmin: hindiTextAdmin.isEmpty ? nil : hindiTextAdmin)
                } label: {
                    Text("Save")
                        .font(.labelMedium).foregroundColor(.amber)
                        .padding(.horizontal, 14).padding(.vertical, 6)
                        .overlay(Capsule().stroke(Color.amber.opacity(0.5)))
                        .clipShape(Capsule())
                }
            }
        }
    }

    private func toggleRow(_ label: String, isOn: Binding<Bool>, enabled: Bool) -> some View {
        Button {
            if enabled { isOn.wrappedValue.toggle() }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isOn.wrappedValue ? "checkmark.square.fill" : "square")
                    .foregroundColor(enabled ? (isOn.wrappedValue ? .amber : .textMuted) : .textMuted.opacity(0.5))
                Text(label).font(.bodySmall).foregroundColor(enabled ? .textSub : .textMuted)
            }
        }
        .disabled(!enabled)
    }

    private func hindiField(_ label: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.labelSmall).foregroundColor(.textMuted)
            TextEditor(text: text)
                .frame(minHeight: 70)
                .padding(6)
                .background(Color.navyMid)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .foregroundColor(.textPrimary)
                .font(.bodySmall)
        }
    }
}
