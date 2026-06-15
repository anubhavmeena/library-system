import SwiftUI

struct BookingView: View {
    @ObservedObject var vm: StudentViewModel
    @State private var step = 1  // 1=plan, 2=seat, 3=confirm
    @State private var showPayment = false

    private let shifts = ["MORNING", "EVENING", "FULL_DAY"]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        stepIndicator

                        switch step {
                        case 1: planStep
                        case 2: seatStep
                        default: confirmStep
                        }
                    }
                    .padding(16)
                }
                .sheet(isPresented: $showPayment) {
                    paymentSheet
                }
            }
            .navigationTitle("Book a Seat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadPlans() }
        .onChange(of: vm.membership) { _ in step = 1 }
    }

    // MARK: - Step Indicator
    private var stepIndicator: some View {
        HStack(spacing: 0) {
            ForEach(1...3, id: \.self) { i in
                HStack(spacing: 0) {
                    Circle()
                        .fill(i <= step ? Color.amber : Color.cardBg)
                        .overlay(Circle().stroke(i <= step ? Color.amber : Color.cardBorder))
                        .frame(width: 28, height: 28)
                        .overlay(
                            Text("\(i)")
                                .font(.labelSmall)
                                .foregroundColor(i <= step ? .navyDeep : .textMuted)
                        )
                    if i < 3 {
                        Rectangle()
                            .fill(i < step ? Color.amber : Color.cardBorder)
                            .frame(height: 2)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    // MARK: - Step 1: Select Plan
    private var planStep: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Select a Plan")
                .font(.headlineSmall)
                .foregroundColor(.textPrimary)

            if vm.plans.isEmpty {
                LoadingView().frame(height: 120)
            } else {
                ForEach(vm.plans.filter { $0.isActive }) { plan in
                    planCard(plan)
                }
            }

            // Shift selector (only for HALF_DAY plans)
            if let plan = vm.selectedPlan, plan.planType == "HALF_DAY" {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Select Shift")
                        .font(.headlineSmall)
                        .foregroundColor(.textPrimary)
                    HStack(spacing: 10) {
                        ForEach(["MORNING", "EVENING"], id: \.self) { shift in
                            shiftChip(shift, selected: vm.selectedShift == shift) {
                                vm.selectedShift = shift
                            }
                        }
                    }
                }
            }

            if vm.selectedPlan != nil {
                PrimaryButton("Choose Seat") {
                    let shift = vm.selectedPlan?.planType == "FULL_DAY" ? "FULL_DAY" : vm.selectedShift
                    vm.loadSeats(shift: shift)
                    step = 2
                }
            }
        }
    }

    private func planCard(_ plan: Plan) -> some View {
        let isSelected = vm.selectedPlan?.id == plan.id
        return Button { vm.selectedPlan = plan } label: {
            AppCard(accentColor: isSelected ? .amber : nil) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(plan.name)
                            .font(.headlineSmall)
                            .foregroundColor(.textPrimary)
                        Text(plan.description)
                            .font(.bodySmall)
                            .foregroundColor(.textSub)
                        Text("\(plan.durationDays) days · \(plan.planType.replacingOccurrences(of: "_", with: " "))")
                            .font(.labelSmall)
                            .foregroundColor(.textMuted)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("₹\(String(format: "%.0f", plan.price))")
                            .font(.headlineMedium)
                            .foregroundColor(.amber)
                        if isSelected {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.amber)
                        }
                    }
                }
            }
        }
    }

    private func shiftChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label.capitalized)
                .font(.labelMedium)
                .foregroundColor(selected ? .navyDeep : .textSub)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(selected ? Color.amber : Color.cardBg)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? Color.amber : Color.cardBorder))
        }
    }

    // MARK: - Step 2: Select Seat
    private var seatStep: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button { step = 1 } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.amber)
                    Text("Back")
                        .foregroundColor(.amber)
                }
                Spacer()
                Text("Pick a Seat")
                    .font(.headlineSmall)
                    .foregroundColor(.textPrimary)
                Spacer()
                Color.clear.frame(width: 60)
            }

            if vm.isLoading {
                LoadingView().frame(height: 200)
            } else {
                SeatGridView(seats: vm.seats, selectedSeat: vm.selectedSeat) { seat in
                    vm.selectSeat(seat.seatNumber)
                }

                if vm.selectedSeat != nil {
                    PrimaryButton("Confirm Seat") { step = 3 }
                }
            }
        }
    }

    // MARK: - Step 3: Confirm & Pay
    private var confirmStep: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button { step = 2 } label: {
                    Image(systemName: "chevron.left").foregroundColor(.amber)
                    Text("Back").foregroundColor(.amber)
                }
                Spacer()
                Text("Confirm Booking").font(.headlineSmall).foregroundColor(.textPrimary)
                Spacer()
                Color.clear.frame(width: 60)
            }

            AppCard(accentColor: .amber) {
                VStack(spacing: 12) {
                    Text("Order Summary")
                        .font(.headlineSmall)
                        .foregroundColor(.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Divider().background(Color.dividerColor)
                    if let plan = vm.selectedPlan {
                        InfoRow(label: "Plan",   value: plan.name)
                        InfoRow(label: "Amount", value: "₹\(String(format: "%.0f", plan.price))")
                    }
                    InfoRow(label: "Seat",  value: vm.selectedSeat ?? "—")
                    InfoRow(label: "Shift", value: (vm.selectedPlan?.planType == "FULL_DAY" ? "FULL_DAY" : vm.selectedShift).capitalized)
                }
            }

            if let err = vm.error { ErrorBanner(message: err) }

            PrimaryButton("Pay Now", isLoading: vm.isLoading) {
                guard let plan = vm.selectedPlan, let seat = vm.selectedSeat else { return }
                let shift = plan.planType == "FULL_DAY" ? "FULL_DAY" : vm.selectedShift
                vm.startPayment(planId: plan.id, seatNumber: seat, shift: shift)
            }
            .onChange(of: vm.pendingOrder) { order in
                if order != nil { showPayment = true }
            }
        }
    }

    // MARK: - Payment Sheet
    @ViewBuilder
    private var paymentSheet: some View {
        if let order = vm.pendingOrder {
            if order.orderId.hasPrefix("dev_") {
                // Dev mode: skip Razorpay
                VStack(spacing: 20) {
                    Color.navyDeep.ignoresSafeArea()
                    Text("Dev Mode Payment").font(.headlineLarge).foregroundColor(.textPrimary)
                    Text("Order: \(order.orderId)").foregroundColor(.textSub)
                    PrimaryButton("Confirm (Dev)") {
                        showPayment = false
                        vm.devVerifyPayment(membershipId: order.membershipId)
                    }
                    OutlineButton("Cancel") { showPayment = false; vm.resetBooking() }
                }
                .padding(24)
                .background(Color.navyDeep)
            } else {
                RazorpayWebView(order: order) { paymentId, orderId, signature in
                    showPayment = false
                    vm.verifyPayment(gatewayOrderId: orderId, gatewayPaymentId: paymentId,
                                     signature: signature, membershipId: order.membershipId)
                } onDismiss: {
                    showPayment = false
                }
                .ignoresSafeArea()
            }
        }
    }
}
