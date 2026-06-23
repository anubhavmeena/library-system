import SwiftUI

struct AdminTabView: View {
    @StateObject private var vm = AdminViewModel()
    @State private var showCreateMembership = false

    var body: some View {
        TabView {
            AdminDashboardView(vm: vm)
                .tabItem { Label("Dashboard", systemImage: "chart.bar.fill") }

            AdminStudentsView(vm: vm)
                .tabItem { Label("Students",  systemImage: "person.3.fill") }

            AdminSeatsView(vm: vm)
                .tabItem { Label("Seats",     systemImage: "chair.lounge.fill") }

            AdminRemindersView(vm: vm)
                .tabItem { Label("Reminders", systemImage: "bell.fill") }

            adminMoreTab
                .tabItem { Label("More",      systemImage: "ellipsis.circle.fill") }
        }
        .tint(.amber)
        .toolbarBackground(Color.navyMid, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showCreateMembership) {
            AdminCreateMembershipView(vm: vm)
        }
    }

    private var adminMoreTab: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 12) {
                        NavigationLink { AdminBroadcastView(vm: vm) } label: {
                            moreActionRow(icon: "megaphone.fill", label: "Broadcast Message")
                        }

                        NavigationLink { AdminFeedbackView(vm: vm) } label: {
                            moreActionRow(icon: "text.bubble.fill", label: "Feedback & Complaints")
                        }

                        NavigationLink { AdminRevenueView(vm: vm) } label: {
                            moreActionRow(icon: "chart.line.uptrend.xyaxis", label: "Revenue & Reports")
                        }

                        NavigationLink { AdminExpensesView(vm: vm) } label: {
                            moreActionRow(icon: "list.bullet.rectangle", label: "Monthly Expenses")
                        }

                        NavigationLink { AdminInboxView(vm: vm) } label: {
                            moreActionRow(icon: "tray.full.fill", label: "Inbox")
                        }

                        NavigationLink { AdminGalleryView() } label: {
                            moreActionRow(icon: "photo.fill.on.rectangle.fill", label: "Photo Gallery")
                        }

                        NavigationLink { AdminImportView() } label: {
                            moreActionRow(icon: "person.badge.plus", label: "Import Student")
                        }

                        Button { showCreateMembership = true } label: {
                            moreActionRow(icon: "plus.circle.fill", label: "Create Membership")
                        }

                        logoutButton
                    }
                    .padding(16)
                }
            }
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private func moreActionRow(icon: String, label: String) -> some View {
        AppCard {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.system(size: 22)).foregroundColor(.amber).frame(width: 30)
                Text(label).font(.bodyLarge).foregroundColor(.textPrimary)
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(.textMuted)
            }
        }
    }

    private var logoutButton: some View {
        Button { TokenManager.shared.clear() } label: {
            AppCard {
                HStack(spacing: 14) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 22)).foregroundColor(.redAlert).frame(width: 30)
                    Text("Logout").font(.bodyLarge).foregroundColor(.redAlert)
                    Spacer()
                }
            }
        }
    }
}
