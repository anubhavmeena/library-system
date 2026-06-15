import SwiftUI

struct AdminStudentDetailView: View {
    @ObservedObject var vm: AdminViewModel
    let studentId: String

    @State private var showChangeSeat   = false
    @State private var newSeat          = ""
    @State private var seatMapSeats:    [Seat] = []
    @State private var selectedNewSeat: String?

    private let baseURL = "https://targetzone.co.in"

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()
            Group {
                if vm.isLoading && vm.selectedStudent == nil {
                    LoadingView()
                } else if let s = vm.selectedStudent {
                    ScrollView {
                        VStack(spacing: 16) {
                            profileHeader(s)
                            membershipSection(s)
                            actionsSection(s)
                        }
                        .padding(16)
                    }
                } else {
                    ErrorView(message: "Failed to load student")
                }
            }
            .sheet(isPresented: $showChangeSeat) {
                changeSeatSheet
            }
        }
        .navigationTitle("Student Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.navyMid, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onAppear { vm.loadStudentDetail(id: studentId) }
    }

    private func profileHeader(_ s: StudentDetail) -> some View {
        AppCard(accentColor: .amber) {
            VStack(spacing: 12) {
                // Avatar
                if let urlStr = s.photoUrl, let url = URL(string: baseURL + urlStr) {
                    AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                    placeholder: { Color.navyLight }
                        .frame(width: 72, height: 72).clipShape(Circle())
                        .overlay(Circle().stroke(Color.amber, lineWidth: 2))
                } else {
                    Circle().fill(Color.navyLight).frame(width: 72, height: 72)
                        .overlay(Text(String(s.name.prefix(1))).font(.headlineLarge).foregroundColor(.amber))
                }

                Text(s.name).font(.headlineMedium).foregroundColor(.textPrimary)
                StatusChip(status: s.isActive ? "ACTIVE" : "INACTIVE")

                Divider().background(Color.dividerColor)
                InfoRow(label: "Mobile",   value: s.mobile)
                if let em = s.email       { InfoRow(label: "Email",   value: em) }
                if let g  = s.gender      { InfoRow(label: "Gender",  value: g.capitalized) }
                if let dob = s.dateOfBirth { InfoRow(label: "DOB",    value: dob) }
                if let addr = s.address   { InfoRow(label: "Address", value: addr) }
                if let joined = s.joinedAt { InfoRow(label: "Joined", value: joined.prefix(10).description) }
            }
        }
    }

    private func membershipSection(_ s: StudentDetail) -> some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Membership").font(.headlineSmall).foregroundColor(.textPrimary)
                Divider().background(Color.dividerColor)
                if let plan = s.planName {
                    InfoRow(label: "Plan",    value: plan)
                    InfoRow(label: "Type",    value: (s.planType ?? "").replacingOccurrences(of: "_", with: " "))
                    InfoRow(label: "Seat",    value: s.seatNumber ?? "—")
                    InfoRow(label: "Shift",   value: (s.shift ?? "").capitalized)
                    InfoRow(label: "Start",   value: s.membershipStart ?? "—")
                    InfoRow(label: "End",     value: s.membershipEnd ?? "—")
                    if let status = s.membershipStatus { InfoRow(label: "Status", value: status) }
                    if s.daysRemaining > 0 {
                        InfoRow(label: "Days Left", value: "\(s.daysRemaining)",
                                valueColor: s.daysRemaining <= 3 ? .redAlert : .emerald)
                    }
                    if let mode = s.paymentMode { InfoRow(label: "Payment", value: mode) }
                } else {
                    Text("No active membership").font(.bodySmall).foregroundColor(.textMuted)
                }
            }
        }
    }

    private func actionsSection(_ s: StudentDetail) -> some View {
        VStack(spacing: 10) {
            // Toggle status
            Button {
                vm.toggleStudentStatus(id: s.id, active: !s.isActive)
            } label: {
                HStack {
                    Image(systemName: s.isActive ? "person.fill.xmark" : "person.fill.checkmark")
                    Text(s.isActive ? "Deactivate Student" : "Activate Student")
                }
                .font(.labelLarge)
                .foregroundColor(s.isActive ? .redAlert : .emerald)
                .frame(maxWidth: .infinity).padding(14)
                .background(s.isActive ? Color.redFaint : Color.emeraldFaint)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(s.isActive ? Color.redAlert.opacity(0.4) : Color.emerald.opacity(0.4)))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            // Change seat (only if membership exists)
            if s.membershipId != nil {
                OutlineButton("Change Seat") { showChangeSeat = true }
            }

            if let err = vm.error { ErrorBanner(message: err) }
            if let msg = vm.successMsg {
                HStack { Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                    Text(msg).font(.bodySmall).foregroundColor(.textPrimary)
                }
                .padding(12).background(Color.emeraldFaint)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var changeSeatSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 16) {
                    Text("Select New Seat")
                        .font(.headlineSmall).foregroundColor(.textPrimary)

                    if seatMapSeats.isEmpty {
                        LoadingView().frame(height: 200)
                    } else {
                        ScrollView {
                            SeatGridView(seats: seatMapSeats, selectedSeat: selectedNewSeat) { seat in
                                selectedNewSeat = seat.seatNumber
                            }
                            .padding(16)
                        }
                    }

                    if let seat = selectedNewSeat {
                        PrimaryButton("Confirm: Seat \(seat)") {
                            if let membershipId = vm.selectedStudent?.membershipId {
                                vm.changeSeat(membershipId: membershipId, seatNumber: seat)
                                showChangeSeat = false
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
            }
            .navigationTitle("Change Seat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showChangeSeat = false }.foregroundColor(.amber)
                }
            }
        }
        .onAppear {
            let shift = vm.selectedStudent?.shift ?? "FULL_DAY"
            Task {
                seatMapSeats = (try? await SeatRepository.shared.getAvailability(shift: shift)) ?? []
            }
        }
    }
}
