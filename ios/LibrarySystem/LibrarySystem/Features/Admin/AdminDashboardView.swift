import SwiftUI

struct AdminDashboardView: View {
    @ObservedObject var vm: AdminViewModel

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        // Top greeting
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Admin Dashboard")
                                    .font(.headlineLarge)
                                    .foregroundColor(.textPrimary)
                                Text("TargetZone Library")
                                    .font(.bodySmall)
                                    .foregroundColor(.textSub)
                            }
                            Spacer()
                            Image(systemName: "shield.checkered")
                                .font(.system(size: 28))
                                .foregroundColor(.amber)
                        }
                        .padding(.horizontal, 16).padding(.top, 16)

                        // Student stats
                        statsSection

                        // Seat occupancy
                        occupancySection

                        // Revenue
                        revenueSection

                        // Visitor stats
                        visitorsSection
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
        .onAppear { vm.loadStats() }
    }

    private var statsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Students").font(.headlineSmall).foregroundColor(.textPrimary).padding(.horizontal, 16)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                StatCard(label: "Total Students",   value: "\(vm.stats.totalStudents)",   accent: .blueSoft)
                StatCard(label: "Active Students",  value: "\(vm.stats.activeStudents)",  accent: .emerald)
                StatCard(label: "Active Memberships",value: "\(vm.stats.activeMemberships)", accent: .amber)
                StatCard(label: "Expiring This Week",value: "\(vm.stats.expiringThisWeek)", accent: .redAlert)
            }
            .padding(.horizontal, 16)
        }
    }

    private var occupancySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Seat Occupancy").font(.headlineSmall).foregroundColor(.textPrimary).padding(.horizontal, 16)
            AppCard {
                VStack(spacing: 12) {
                    HStack {
                        StatCard(label: "Total Seats",    value: "\(vm.stats.totalSeats)",    accent: .blueSoft)
                        StatCard(label: "Occupied",       value: "\(vm.stats.occupiedSeats)", accent: .amber)
                        StatCard(label: "Available",      value: "\(vm.stats.availableSeats)",accent: .emerald)
                    }
                    if vm.stats.totalSeats > 0 {
                        let pct = Double(vm.stats.occupiedSeats) / Double(vm.stats.totalSeats)
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("Occupancy Rate").font(.labelSmall).foregroundColor(.textMuted)
                                Spacer()
                                Text("\(Int(pct * 100))%").font(.labelMedium).foregroundColor(.amber)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 4).fill(Color.navyLight).frame(height: 8)
                                    RoundedRectangle(cornerRadius: 4).fill(Color.amber)
                                        .frame(width: geo.size.width * pct, height: 8)
                                }
                            }
                            .frame(height: 8)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private var revenueSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Revenue").font(.headlineSmall).foregroundColor(.textPrimary).padding(.horizontal, 16)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                StatCard(label: "Today's Revenue",
                         value: "₹\(String(format: "%.0f", vm.stats.revenueToday))",
                         accent: .emerald)
                StatCard(label: "This Month",
                         value: "₹\(String(format: "%.0f", vm.stats.revenueThisMonth))",
                         accent: .amber)
                StatCard(label: "Payments This Month",
                         value: "\(vm.stats.paymentsThisMonth)",
                         accent: .blueSoft)
            }
            .padding(.horizontal, 16)
        }
    }

    private var visitorsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Visitors").font(.headlineSmall).foregroundColor(.textPrimary).padding(.horizontal, 16)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                StatCard(label: "Today's Visitors",
                         value: "\(vm.stats.visitorsToday)",
                         accent: .emerald)
                StatCard(label: "Total Visitors",
                         value: "\(vm.stats.totalVisitors)",
                         accent: .blueSoft)
            }
            .padding(.horizontal, 16)
        }
    }
}
