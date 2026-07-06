import SwiftUI

struct MembershipView: View {
    @ObservedObject var vm: StudentViewModel

    @State private var showQueueFlow: Bool = false
    @State private var selectedQueuePlan: Plan?
    @State private var showQueuePaymentSheet = false
    @State private var showDuesPaymentSheet = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        if let m = vm.membership, m.status == "ACTIVE" {
                            activeMembershipCard(m)
                            downloadButton
                            if vm.queuedMembership == nil, let days = membershipDaysLeft(m), days <= 7 {
                                renewSeatPrompt
                            }
                        } else if let m = vm.membership, m.status == "GRACE" || m.status == "EXPIRED" {
                            graceExpiredCard(m)
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
                .sheet(isPresented: $showQueueFlow) {
                    queuePlanSheet
                }
                .sheet(isPresented: $showQueuePaymentSheet) {
                    queuePaymentSheet
                }
                .sheet(isPresented: $showDuesPaymentSheet) {
                    duesPaymentSheet
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
        .onChange(of: vm.queueOrder) { order in showQueuePaymentSheet = order != nil }
        .onChange(of: vm.duesOrder) { order in showDuesPaymentSheet = order != nil }
    }

    private var renewSeatPrompt: some View {
        AppCard(accentColor: .amber) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Your membership is expiring soon — renew your seat now to keep it. It activates automatically when your current plan expires.")
                    .font(.bodySmall).foregroundColor(.textSub)
                OutlineButton("Renew Seat") {
                    selectedQueuePlan = nil
                    vm.loadPlans()
                    showQueueFlow = true
                }
            }
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

    private func graceExpiredCard(_ m: Membership) -> some View {
        AppCard(accentColor: .redAlert) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(m.status == "EXPIRED" ? "Grace Period Expired" : "Membership in Grace Period")
                        .font(.headlineMedium).foregroundColor(.textPrimary)
                    Spacer()
                    StatusChip(status: m.status)
                }
                Text(m.planName).font(.bodySmall).foregroundColor(.textSub)
                Divider().background(Color.dividerColor)
                InfoRow(label: "Seat",        value: m.seatNumber.isEmpty ? "—" : m.seatNumber)
                InfoRow(label: "Expiry Date", value: m.endDate)
                if let days = membershipDaysLeft(m) {
                    InfoRow(label: "Days Left", value: "\(days)", valueColor: .redAlert)
                }
                Text("Your plan expired, but we're holding seat \(m.seatNumber) for you.")
                    .font(.bodySmall).foregroundColor(.textSub)
                Text("Pay ₹\(String(format: "%.0f", m.duesAmount ?? m.planPrice ?? 0)) to continue your plan — your seat may be released if it remains unpaid.")
                    .font(.labelMedium).foregroundColor(.redAlert)
                PrimaryButton("Pay Fee Due (₹\(String(format: "%.0f", m.duesAmount ?? m.planPrice ?? 0)))",
                             isLoading: vm.payingDues) {
                    vm.startDuesPayment()
                }
            }
        }
    }

    private func membershipDaysLeft(_ m: Membership) -> Int? {
        let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"
        guard let end = fmt.date(from: m.endDate) else { return nil }
        return Calendar.current.dateComponents([.day], from: Date(), to: end).day ?? 0
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

    private var queuePlanSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        if vm.plans.isEmpty {
                            LoadingView().frame(height: 120)
                        } else {
                            ForEach(vm.plans.filter { $0.isActive }) { plan in
                                Button { selectedQueuePlan = plan } label: {
                                    AppCard(accentColor: selectedQueuePlan?.id == plan.id ? .amber : nil) {
                                        HStack {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(plan.name).font(.headlineSmall).foregroundColor(.textPrimary)
                                                Text(plan.description).font(.bodySmall).foregroundColor(.textSub)
                                            }
                                            Spacer()
                                            Text("₹\(String(format: "%.0f", plan.price))")
                                                .font(.headlineMedium).foregroundColor(.amber)
                                        }
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        if let plan = selectedQueuePlan {
                            PrimaryButton("Pay ₹\(String(format: "%.0f", plan.price))", isLoading: vm.queuePaying) {
                                vm.startQueuePayment(planId: plan.id)
                            }
                        }

                        if let err = vm.error { ErrorBanner(message: err) }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Renew Seat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showQueueFlow = false }.foregroundColor(.amber)
                }
            }
        }
    }

    @ViewBuilder
    private var queuePaymentSheet: some View {
        if let order = vm.queueOrder {
            if order.orderId.hasPrefix("dev_") {
                VStack(spacing: 20) {
                    Color.navyDeep.ignoresSafeArea()
                    Text("Dev Mode Payment").font(.headlineLarge).foregroundColor(.textPrimary)
                    Text("Order: \(order.orderId)").foregroundColor(.textSub)
                    PrimaryButton("Confirm (Dev)") {
                        showQueuePaymentSheet = false
                        showQueueFlow = false
                        vm.devVerifyQueuePayment(membershipId: order.membershipId)
                    }
                    OutlineButton("Cancel") {
                        showQueuePaymentSheet = false
                        vm.resetQueuePayment()
                    }
                }
                .padding(24)
                .background(Color.navyDeep)
            } else if order.gateway == "CASHFREE" {
                CashfreeWebView(order: order, mode: "sandbox") {
                    showQueuePaymentSheet = false
                    showQueueFlow = false
                    vm.verifyQueuePayment(gatewayOrderId: order.orderId, gatewayPaymentId: order.orderId,
                                         signature: "", membershipId: order.membershipId)
                } onFailure: { message in
                    showQueuePaymentSheet = false
                    vm.error = message
                    vm.resetQueuePayment()
                } onDismiss: {
                    showQueuePaymentSheet = false
                }
                .ignoresSafeArea()
            } else {
                RazorpayWebView(order: order) { paymentId, orderId, signature in
                    showQueuePaymentSheet = false
                    showQueueFlow = false
                    vm.verifyQueuePayment(gatewayOrderId: orderId, gatewayPaymentId: paymentId,
                                         signature: signature, membershipId: order.membershipId)
                } onDismiss: {
                    showQueuePaymentSheet = false
                }
                .ignoresSafeArea()
            }
        }
    }

    @ViewBuilder
    private var duesPaymentSheet: some View {
        if let order = vm.duesOrder {
            if order.orderId.hasPrefix("dev_") {
                VStack(spacing: 20) {
                    Color.navyDeep.ignoresSafeArea()
                    Text("Dev Mode Payment").font(.headlineLarge).foregroundColor(.textPrimary)
                    Text("Order: \(order.orderId)").foregroundColor(.textSub)
                    PrimaryButton("Confirm (Dev)") {
                        showDuesPaymentSheet = false
                        vm.devVerifyDuesPayment(membershipId: order.membershipId)
                    }
                    OutlineButton("Cancel") {
                        showDuesPaymentSheet = false
                        vm.resetDuesPayment()
                    }
                }
                .padding(24)
                .background(Color.navyDeep)
            } else if order.gateway == "CASHFREE" {
                CashfreeWebView(order: order, mode: "sandbox") {
                    showDuesPaymentSheet = false
                    vm.verifyDuesPayment(gatewayOrderId: order.orderId, gatewayPaymentId: order.orderId,
                                        signature: "", membershipId: order.membershipId)
                } onFailure: { message in
                    showDuesPaymentSheet = false
                    vm.error = message
                    vm.resetDuesPayment()
                } onDismiss: {
                    showDuesPaymentSheet = false
                }
                .ignoresSafeArea()
            } else {
                RazorpayWebView(order: order) { paymentId, orderId, signature in
                    showDuesPaymentSheet = false
                    vm.verifyDuesPayment(gatewayOrderId: orderId, gatewayPaymentId: paymentId,
                                        signature: signature, membershipId: order.membershipId)
                } onDismiss: {
                    showDuesPaymentSheet = false
                }
                .ignoresSafeArea()
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
