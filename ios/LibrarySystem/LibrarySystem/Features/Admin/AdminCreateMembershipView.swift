import SwiftUI

struct AdminCreateMembershipView: View {
    @ObservedObject var vm: AdminViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var studentId   = ""
    @State private var selectedPlan: Plan?
    @State private var selectedShift = "MORNING"
    @State private var seatNumber  = ""
    @State private var startDate   = ""
    @State private var seats:       [Seat] = []
    @State private var selectedSeat: String?
    @State private var showSeatGrid = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        AppTextField(label: "Student ID (UUID)", text: $studentId,
                                     placeholder: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                                     leadingIcon: "person")

                        // Plan selection
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Select Plan").font(.labelMedium).foregroundColor(.textSub)
                            if vm.plans.isEmpty {
                                ProgressView().tint(.amber)
                            } else {
                                ForEach(vm.plans.filter { $0.isActive }) { plan in
                                    let selected = selectedPlan?.id == plan.id
                                    Button { selectedPlan = plan } label: {
                                        HStack {
                                            VStack(alignment: .leading) {
                                                Text(plan.name).font(.labelLarge).foregroundColor(.textPrimary)
                                                Text("₹\(String(format: "%.0f", plan.price)) · \(plan.durationDays)d · \(plan.planType)")
                                                    .font(.bodySmall).foregroundColor(.textSub)
                                            }
                                            Spacer()
                                            if selected { Image(systemName: "checkmark.circle.fill").foregroundColor(.amber) }
                                        }
                                        .padding(12)
                                        .background(selected ? Color.amberFaint : Color.cardBg)
                                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.amber : Color.cardBorder))
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                    }
                                }
                            }
                        }

                        // Shift (only for HALF_DAY)
                        if selectedPlan?.planType == "HALF_DAY" {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Shift").font(.labelMedium).foregroundColor(.textSub)
                                HStack(spacing: 8) {
                                    ForEach(["MORNING","EVENING"], id: \.self) { shift in
                                        let sel = selectedShift == shift
                                        Button { selectedShift = shift } label: {
                                            Text(shift.capitalized)
                                                .font(.labelMedium)
                                                .foregroundColor(sel ? .navyDeep : .textSub)
                                                .padding(.horizontal, 16).padding(.vertical, 8)
                                                .background(sel ? Color.amber : Color.cardBg)
                                                .clipShape(Capsule())
                                        }
                                    }
                                }
                            }
                        }

                        // Seat
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Seat Number").font(.labelMedium).foregroundColor(.textSub)
                            HStack {
                                AppTextField(label: "", text: .constant(selectedSeat ?? ""),
                                             placeholder: "e.g. A12", leadingIcon: "chair.lounge")
                                Button("Pick") { loadSeatsAndShow() }
                                    .foregroundColor(.amber).font(.labelMedium)
                            }
                        }

                        AppTextField(label: "Start Date (yyyy-MM-dd)", text: $startDate,
                                     placeholder: "2025-01-01", leadingIcon: "calendar")

                        if let err = vm.error { ErrorBanner(message: err) }
                        if let msg = vm.successMsg {
                            HStack {
                                Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                                Text(msg).foregroundColor(.textPrimary)
                            }
                        }

                        PrimaryButton("Create Membership", isLoading: vm.isLoading) {
                            guard !studentId.isEmpty, let plan = selectedPlan,
                                  let seat = selectedSeat, !seat.isEmpty else { return }
                            let shift = plan.planType == "FULL_DAY" ? "FULL_DAY" : selectedShift
                            let today = ISO8601DateFormatter().string(from: Date()).prefix(10).description
                            vm.createMembership(userId: studentId, planId: plan.id,
                                                seatNumber: seat, shift: shift,
                                                startDate: startDate.isEmpty ? today : startDate)
                        }
                    }
                    .padding(16)
                }
                .sheet(isPresented: $showSeatGrid) {
                    seatPickerSheet
                }
            }
            .navigationTitle("Create Membership")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }.foregroundColor(.amber)
                }
            }
        }
        .onAppear { vm.loadPlans() }
    }

    private var seatPickerSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    SeatGridView(seats: seats, selectedSeat: selectedSeat) { seat in
                        selectedSeat = seat.seatNumber
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Pick Seat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { showSeatGrid = false }.foregroundColor(.amber)
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showSeatGrid = false }.foregroundColor(.amber)
                }
            }
        }
    }

    private func loadSeatsAndShow() {
        let shift = selectedPlan?.planType == "FULL_DAY" ? "FULL_DAY" : selectedShift
        Task {
            seats = (try? await SeatRepository.shared.getAvailability(shift: shift)) ?? []
            showSeatGrid = true
        }
    }
}
