import SwiftUI

struct DashboardView: View {
    @ObservedObject var vm: StudentViewModel
    @ObservedObject private var tokenManager = TokenManager.shared

    private var user: User? { tokenManager.currentUser }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        // Greeting
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Hello, \(user?.name.components(separatedBy: " ").first ?? "Student")")
                                    .font(.headlineLarge)
                                    .foregroundColor(.textPrimary)
                                Text("Welcome back!")
                                    .font(.bodySmall)
                                    .foregroundColor(.textSub)
                            }
                            Spacer()
                            Image(systemName: "books.vertical.fill")
                                .font(.system(size: 28))
                                .foregroundColor(.amber)
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 16)

                        if vm.isLoading {
                            LoadingView().frame(height: 200)
                        } else if let membership = vm.membership, membership.status == "ACTIVE" {
                            activeMembershipSection(membership)
                        } else {
                            noMembershipSection
                        }

                        quickActionsSection
                    }
                    .padding(.bottom, 24)
                }
            }
            .navigationTitle("Dashboard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadDashboard() }
    }

    private func activeMembershipSection(_ m: Membership) -> some View {
        VStack(spacing: 12) {
            // Expiry warning
            if let days = daysRemaining(m.endDate), days <= 7 {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.amber)
                    Text("Membership expires in \(days) day\(days == 1 ? "" : "s")")
                        .font(.bodySmall)
                        .foregroundColor(.textPrimary)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.amberFaint)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.4)))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 16)
            }

            AppCard(accentColor: .amber) {
                VStack(spacing: 12) {
                    HStack {
                        Text(m.planName)
                            .font(.headlineMedium)
                            .foregroundColor(.textPrimary)
                        Spacer()
                        StatusChip(status: m.status)
                    }
                    Divider().background(Color.dividerColor)
                    InfoRow(label: "Seat",    value: m.seatNumber.isEmpty ? "—" : m.seatNumber)
                    InfoRow(label: "Shift",   value: m.shift.capitalized)
                    InfoRow(label: "Starts",  value: m.startDate)
                    InfoRow(label: "Expires", value: m.endDate)
                    if let days = daysRemaining(m.endDate) {
                        InfoRow(label: "Days Left", value: "\(days)", valueColor: days <= 3 ? .redAlert : .emerald)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private var noMembershipSection: some View {
        AppCard {
            VStack(spacing: 12) {
                Image(systemName: "calendar.badge.exclamationmark")
                    .font(.system(size: 36))
                    .foregroundColor(.textMuted)
                Text("No Active Membership")
                    .font(.headlineSmall)
                    .foregroundColor(.textPrimary)
                Text("Book a seat to get started")
                    .font(.bodySmall)
                    .foregroundColor(.textSub)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .padding(.horizontal, 16)
    }

    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Quick Actions")
                .font(.headlineSmall)
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                quickAction(icon: "chair.lounge.fill",  label: "Book Seat",    color: .amber)
                quickAction(icon: "creditcard.fill",    label: "Membership",   color: .emerald)
                quickAction(icon: "person.fill",        label: "Profile",      color: .blueSoft)
                quickAction(icon: "info.circle.fill",   label: "Facilities",   color: .textSub)
            }
            .padding(.horizontal, 16)
        }
    }

    private func quickAction(icon: String, label: String, color: Color) -> some View {
        AppCard {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 28))
                    .foregroundColor(color)
                Text(label)
                    .font(.labelMedium)
                    .foregroundColor(.textPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
    }

    private func daysRemaining(_ endDate: String) -> Int? {
        let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"
        guard let end = fmt.date(from: endDate) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: Date(), to: end).day ?? 0
        return max(0, days)
    }
}
