import SwiftUI

struct MembershipView: View {
    @ObservedObject var vm: StudentViewModel

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        if let m = vm.membership, m.status == "ACTIVE" {
                            activeMembershipCard(m)
                            downloadButton
                        } else {
                            noMembershipCard
                        }

                        if let q = vm.queuedMembership {
                            queuedMembershipCard(q)
                        }

                        if !vm.myPayments.isEmpty {
                            paymentHistorySection
                        }

                        if !vm.membershipHistory.isEmpty {
                            historySection
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
                .sheet(isPresented: $vm.showIdCardShare) {
                    if let data = vm.idCardData {
                        ShareSheet(items: [data])
                    }
                }
            }
            .navigationTitle("Membership")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear {
            vm.loadDashboard()
            vm.loadMembershipHistory()
            vm.loadMyPayments()
        }
    }

    private func activeMembershipCard(_ m: Membership) -> some View {
        AppCard(accentColor: .amber) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(m.planName)
                        .font(.headlineMedium)
                        .foregroundColor(.textPrimary)
                    Spacer()
                    StatusChip(status: m.status)
                }

                if let progress = membershipProgress(m) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Plan Progress")
                            .font(.labelSmall)
                            .foregroundColor(.textMuted)
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.navyLight)
                                    .frame(height: 8)
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.amber)
                                    .frame(width: geo.size.width * progress, height: 8)
                            }
                        }
                        .frame(height: 8)
                    }
                }

                Divider().background(Color.dividerColor)
                InfoRow(label: "Plan Type",   value: m.planType.replacingOccurrences(of: "_", with: " "))
                InfoRow(label: "Seat",        value: m.seatNumber.isEmpty ? "—" : m.seatNumber)
                InfoRow(label: "Shift",       value: m.shift.capitalized)
                InfoRow(label: "Start Date",  value: m.startDate)
                InfoRow(label: "End Date",    value: m.endDate)
                InfoRow(label: "Amount Paid", value: "₹\(String(format: "%.0f", m.displayAmount))")
            }
        }
    }

    private func queuedMembershipCard(_ m: Membership) -> some View {
        AppCard(accentColor: .blueSoft) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label("Queued Plan", systemImage: "clock.badge.checkmark")
                        .font(.headlineSmall)
                        .foregroundColor(.blueSoft)
                    Spacer()
                    StatusChip(status: "QUEUED")
                }
                Divider().background(Color.dividerColor)
                InfoRow(label: "Plan",   value: m.planName)
                InfoRow(label: "Type",   value: m.planType.replacingOccurrences(of: "_", with: " "))
                InfoRow(label: "Seat",   value: m.seatNumber.isEmpty ? "—" : m.seatNumber)
                InfoRow(label: "Shift",  value: m.shift.capitalized)
                if !m.startDate.isEmpty { InfoRow(label: "Starts", value: m.startDate) }
                if !m.endDate.isEmpty   { InfoRow(label: "Ends",   value: m.endDate) }
            }
        }
    }

    private var downloadButton: some View {
        OutlineButton("Download ID Card") { vm.downloadIdCard() }
    }

    private var noMembershipCard: some View {
        AppCard {
            VStack(spacing: 12) {
                Image(systemName: "creditcard.trianglebadge.exclamationmark")
                    .font(.system(size: 40))
                    .foregroundColor(.textMuted)
                Text("No Active Membership")
                    .font(.headlineSmall)
                    .foregroundColor(.textPrimary)
                Text("Purchase a plan from the Booking tab")
                    .font(.bodySmall)
                    .foregroundColor(.textSub)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
        }
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("History")
                .font(.headlineSmall)
                .foregroundColor(.textPrimary)

            ForEach(vm.membershipHistory) { m in
                AppCard {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(m.planName)
                                .font(.labelLarge)
                                .foregroundColor(.textPrimary)
                            Spacer()
                            StatusChip(status: m.status)
                        }
                        Text("\(m.startDate) → \(m.endDate)")
                            .font(.bodySmall)
                            .foregroundColor(.textSub)
                        Text("₹\(String(format: "%.0f", m.displayAmount)) · \(m.shift.capitalized) · Seat \(m.seatNumber)")
                            .font(.bodySmall)
                            .foregroundColor(.textMuted)
                    }
                }
            }
        }
    }

    private var paymentHistorySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Payment History").font(.headlineSmall).foregroundColor(.textPrimary)
            ForEach(vm.myPayments) { p in
                AppCard {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text("₹\(String(format: "%.0f", p.amount))")
                                    .font(.labelLarge).foregroundColor(.textPrimary)
                                if let gw = p.paymentGateway {
                                    Text("·").foregroundColor(.textMuted)
                                    Text(gw).font(.labelSmall).foregroundColor(.textMuted)
                                }
                            }
                            if let date = p.createdAt {
                                Text(date.prefix(10).description)
                                    .font(.bodySmall).foregroundColor(.textSub)
                            }
                            if let ref = p.gatewayOrderId {
                                Text(ref).font(.labelSmall).foregroundColor(.textMuted).lineLimit(1)
                            }
                        }
                        Spacer()
                        StatusChip(status: p.status)
                    }
                }
            }
        }
    }

    private func membershipProgress(_ m: Membership) -> CGFloat? {
        let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"
        guard let start = fmt.date(from: m.startDate),
              let end   = fmt.date(from: m.endDate)   else { return nil }
        let total    = end.timeIntervalSince(start)
        let elapsed  = Date().timeIntervalSince(start)
        guard total > 0 else { return nil }
        return CGFloat(min(max(elapsed / total, 0), 1))
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
