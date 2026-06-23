import SwiftUI

struct AdminRevenueView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var fromDate = ""
    @State private var toDate   = ""
    @State private var drillDate: String?
    @State private var showDrillDown = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        dateRangeSection

                        PrimaryButton("Load Report", isLoading: vm.isLoading) {
                            guard !fromDate.isEmpty, !toDate.isEmpty else { return }
                            vm.loadRevenueReport(from: fromDate, to: toDate)
                        }

                        if vm.revenueReport.totalRevenue > 0 || !vm.revenueReport.fromDate.isEmpty {
                            summaryCards
                            breakdownSection
                        }

                        if let err = vm.error { ErrorBanner(message: err) }
                    }
                    .padding(16)
                }
                .sheet(isPresented: $showDrillDown) {
                    drillDownSheet
                }
            }
            .navigationTitle("Revenue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { loadDefaultRange() }
    }

    private var dateRangeSection: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Date Range").font(.labelMedium).foregroundColor(.textSub)
                HStack(spacing: 12) {
                    AppTextField(label: "From", text: $fromDate,
                                 placeholder: "yyyy-MM-dd", leadingIcon: "calendar")
                    AppTextField(label: "To", text: $toDate,
                                 placeholder: "yyyy-MM-dd", leadingIcon: "calendar")
                }
            }
        }
    }

    private var summaryCards: some View {
        let r = vm.revenueReport
        return VStack(alignment: .leading, spacing: 12) {
            Text("Summary").font(.headlineSmall).foregroundColor(.textPrimary)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                StatCard(label: "Total Revenue",
                         value: "₹\(String(format: "%.0f", r.totalRevenue))",
                         accent: .emerald)
                StatCard(label: "Transactions",
                         value: "\(r.totalTransactions)",
                         accent: .blueSoft)
                StatCard(label: "Full Day",
                         value: "₹\(String(format: "%.0f", r.fullDayRevenue))",
                         accent: .amber)
                StatCard(label: "Half Day",
                         value: "₹\(String(format: "%.0f", r.halfDayRevenue))",
                         accent: .textSub)
            }
        }
    }

    private var breakdownSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Daily Breakdown").font(.headlineSmall).foregroundColor(.textPrimary)
            ForEach(vm.revenueReport.dailyBreakdown, id: \.date) { day in
                Button {
                    drillDate = day.date
                    vm.loadDailyPayments(date: day.date)
                    showDrillDown = true
                } label: {
                    AppCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(day.date).font(.labelMedium).foregroundColor(.textPrimary)
                                Text("\(day.count) transaction\(day.count == 1 ? "" : "s")")
                                    .font(.bodySmall).foregroundColor(.textSub)
                            }
                            Spacer()
                            Text("₹\(String(format: "%.0f", day.amount))")
                                .font(.headlineSmall).foregroundColor(.amber)
                            Image(systemName: "chevron.right").foregroundColor(.textMuted)
                        }
                    }
                }
            }
        }
    }

    private var drillDownSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                Group {
                    if vm.isLoading {
                        LoadingView()
                    } else if vm.dailyPayments.isEmpty {
                        Text("No payments on this date").foregroundColor(.textMuted)
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 10) {
                                ForEach(Array(vm.dailyPayments.enumerated()), id: \.offset) { _, p in
                                    AppCard {
                                        VStack(alignment: .leading, spacing: 6) {
                                            HStack {
                                                Text(p.studentName).font(.labelLarge).foregroundColor(.textPrimary)
                                                Spacer()
                                                Text("₹\(String(format: "%.0f", p.amount))")
                                                    .font(.headlineSmall).foregroundColor(.emerald)
                                            }
                                            Text(p.studentMobile).font(.bodySmall).foregroundColor(.textSub)
                                            if let gw = p.paymentGateway {
                                                Text(gw).font(.labelSmall).foregroundColor(.textMuted)
                                            }
                                            if let ref = p.referenceId {
                                                Text("Ref: \(ref)").font(.bodySmall).foregroundColor(.textMuted)
                                                    .lineLimit(1)
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(16)
                        }
                    }
                }
            }
            .navigationTitle(drillDate ?? "Payments")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { showDrillDown = false }.foregroundColor(.amber)
                }
            }
        }
    }

    private func loadDefaultRange() {
        let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"
        let today = Date()
        let cal = Calendar.current
        let firstOfMonth = cal.date(from: cal.dateComponents([.year, .month], from: today))!
        fromDate = fmt.string(from: firstOfMonth)
        toDate   = fmt.string(from: today)
        vm.loadRevenueReport(from: fromDate, to: toDate)
    }
}
