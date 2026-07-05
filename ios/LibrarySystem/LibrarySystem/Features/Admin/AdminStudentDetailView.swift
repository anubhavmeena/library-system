import SwiftUI

struct AdminStudentDetailView: View {
    @ObservedObject var vm: AdminViewModel
    let studentId: String
    @Environment(\.dismiss) private var dismiss

    @State private var showChangeSeat   = false
    @State private var seatMapSeats:    [Seat] = []
    @State private var selectedNewSeat: String?
    @State private var showPayments     = false
    @State private var showSeatHistory  = false
    @State private var showMessage      = false
    @State private var messageText      = ""
    @State private var showEdit         = false

    private enum ClearKind { case fees, dues }
    @State private var clearKind:       ClearKind?
    @State private var clearAmountText = ""
    @State private var showReleaseConfirm = false
    @State private var showReleaseNotifyPrompt = false
    @State private var showRenewConfirm = false
    @State private var showDeleteConfirm = false

    @State private var showChangeStatus     = false
    @State private var changeStatusTarget   = "PENDING" // PENDING | GRACE
    @State private var pendingAmountInput   = ""

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
                            if s.pendingAmount ?? 0 > 0 {
                                pendingFeesSection(s)
                            }
                            if s.membershipStatus == "GRACE" && (s.duesAmount ?? 0) > 0 {
                                graceDuesSection(s)
                            }
                            actionsSection(s)
                            if showPayments {
                                paymentHistorySection
                            }
                            if showSeatHistory {
                                seatHistorySection
                            }
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
            .sheet(isPresented: $showMessage) {
                messageSheet
            }
            .sheet(isPresented: $showEdit) {
                if let s = vm.selectedStudent { editSheet(s) }
            }
            .sheet(isPresented: $showChangeStatus) {
                if let s = vm.selectedStudent { changeStatusSheet(s) }
            }
        }
        .navigationTitle("Student Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.navyMid, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onAppear { vm.loadStudentDetail(id: studentId) }
        .onChange(of: vm.studentDeleted) { deleted in
            if deleted { vm.studentDeleted = false; dismiss() }
        }
        .alert(clearKind == .dues ? "Clear Dues" : "Clear Pending Fees",
               isPresented: Binding(get: { clearKind != nil }, set: { if !$0 { clearKind = nil } })) {
            TextField("Amount", text: $clearAmountText).keyboardType(.decimalPad)
            Button("Cancel", role: .cancel) { clearKind = nil }
            Button("Clear") {
                guard let s = vm.selectedStudent, let amount = Double(clearAmountText), amount > 0 else { return }
                if clearKind == .dues {
                    if let membershipId = s.membershipId {
                        vm.clearDues(membershipId: membershipId, amountCleared: amount, userId: s.id)
                    }
                } else {
                    vm.clearPendingFees(userId: s.id, amountCleared: amount)
                }
                clearKind = nil
            }
        } message: {
            if let s = vm.selectedStudent {
                let outstanding = clearKind == .dues ? (s.duesAmount ?? 0) : (s.pendingAmount ?? 0)
                Text("Outstanding: ₹\(String(format: "%.0f", outstanding)). Any amount not cleared stays on record as a pending balance.")
            }
        }
        .alert("Release Seat", isPresented: $showReleaseConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Release", role: .destructive) { showReleaseNotifyPrompt = true }
        } message: {
            if let s = vm.selectedStudent {
                Text("Release seat \(s.seatNumber ?? "—") for \(s.name)? Dues of ₹\(String(format: "%.0f", s.duesAmount ?? 0)) remain on record. This cannot be undone.")
            }
        }
        .alert("Notify Student?", isPresented: $showReleaseNotifyPrompt) {
            Button("Notify") { releaseSeat(notify: true) }
            Button("Release Quietly") { releaseSeat(notify: false) }
        } message: {
            Text("Send a notification that their seat has expired due to non-payment and been released?")
        }
        .alert("Renew Seat", isPresented: $showRenewConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Renew") {
                if let s = vm.selectedStudent, let membershipId = s.membershipId {
                    vm.renewSeat(membershipId: membershipId, userId: s.id)
                }
            }
        } message: {
            if let s = vm.selectedStudent {
                Text("Renew \(s.name)'s seat by one month?")
            }
        }
        .alert("Delete Student", isPresented: $showDeleteConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                if let s = vm.selectedStudent { vm.deleteStudent(id: s.id) }
            }
        } message: {
            if let s = vm.selectedStudent {
                Text("Permanently delete \(s.name) and all their memberships, payments, and seat bookings? This cannot be undone.")
            }
        }
    }

    private func releaseSeat(notify: Bool) {
        guard let s = vm.selectedStudent, let membershipId = s.membershipId else { return }
        vm.releaseSeat(membershipId: membershipId, notifyStudent: notify, userId: s.id)
    }

    private func profileHeader(_ s: StudentDetail) -> some View {
        AppCard(accentColor: .amber) {
            VStack(spacing: 12) {
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

    private func pendingFeesSection(_ s: StudentDetail) -> some View {
        AppCard(accentColor: .redAlert) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label("Pending Fees", systemImage: "exclamationmark.circle.fill")
                        .font(.headlineSmall).foregroundColor(.redAlert)
                    Spacer()
                    Text("₹\(String(format: "%.0f", s.pendingAmount ?? 0))")
                        .font(.headlineMedium).foregroundColor(.redAlert)
                }
                Text("Outstanding balance on cash membership")
                    .font(.bodySmall).foregroundColor(.textSub)
                Button {
                    clearKind = .fees
                    clearAmountText = String(format: "%.0f", s.pendingAmount ?? 0)
                } label: {
                    HStack {
                        Image(systemName: "checkmark.circle")
                        Text("Mark as Cleared")
                    }
                    .font(.labelMedium).foregroundColor(.emerald)
                    .frame(maxWidth: .infinity).padding(10)
                    .background(Color.emeraldFaint)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.emerald.opacity(0.4)))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }

    private func graceDuesSection(_ s: StudentDetail) -> some View {
        AppCard(accentColor: .amber) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label("Grace Period Dues", systemImage: "hourglass")
                        .font(.headlineSmall).foregroundColor(.amber)
                    Spacer()
                    Text("₹\(String(format: "%.0f", s.duesAmount ?? 0))")
                        .font(.headlineMedium).foregroundColor(.amber)
                }
                Text("Seat is being held — clear dues to reactivate as Paid.")
                    .font(.bodySmall).foregroundColor(.textSub)
                Button {
                    clearKind = .dues
                    clearAmountText = String(format: "%.0f", s.duesAmount ?? 0)
                } label: {
                    HStack {
                        Image(systemName: "checkmark.circle")
                        Text("Clear Dues")
                    }
                    .font(.labelMedium).foregroundColor(.emerald)
                    .frame(maxWidth: .infinity).padding(10)
                    .background(Color.emeraldFaint)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.emerald.opacity(0.4)))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }

    private func actionsSection(_ s: StudentDetail) -> some View {
        VStack(spacing: 10) {
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

            OutlineButton("Edit Profile") { showEdit = true }

            if s.membershipId != nil {
                OutlineButton("Change Seat") { showChangeSeat = true }
            }

            if s.membershipId != nil && (s.membershipStatus == "ACTIVE" || s.membershipStatus == "GRACE") {
                Button {
                    showReleaseConfirm = true
                } label: {
                    HStack {
                        Image(systemName: "chair.lounge")
                        Text("Release Seat")
                    }
                    .font(.labelLarge).foregroundColor(.redAlert)
                    .frame(maxWidth: .infinity).padding(14)
                    .background(Color.redFaint)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.redAlert.opacity(0.4)))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }

            if s.membershipId != nil && s.displayStatus == "PAID" {
                OutlineButton("Renew Seat") { showRenewConfirm = true }
            }

            if s.membershipId != nil && s.membershipStatus == "ACTIVE" {
                OutlineButton("Change Status") {
                    changeStatusTarget = "PENDING"
                    pendingAmountInput = ""
                    showChangeStatus = true
                }
            }

            OutlineButton("Send Message") { showMessage = true }

            Button {
                showPayments.toggle()
                if showPayments { vm.loadStudentPayments(userId: s.id) }
            } label: {
                HStack {
                    Image(systemName: "creditcard")
                    Text(showPayments ? "Hide Payments" : "View Payments")
                }
                .font(.labelLarge).foregroundColor(.blueSoft)
                .frame(maxWidth: .infinity).padding(14)
                .background(Color.blueFaint)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.blueSoft.opacity(0.4)))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Button {
                showSeatHistory.toggle()
                if showSeatHistory { vm.loadStudentSeatHistory(userId: s.id) }
            } label: {
                HStack {
                    Image(systemName: "clock.arrow.circlepath")
                    Text(showSeatHistory ? "Hide Seat History" : "View Seat History")
                }
                .font(.labelLarge).foregroundColor(.blueSoft)
                .frame(maxWidth: .infinity).padding(14)
                .background(Color.blueFaint)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.blueSoft.opacity(0.4)))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Button {
                showDeleteConfirm = true
            } label: {
                HStack {
                    Image(systemName: "trash")
                    Text("Delete Student")
                }
                .font(.labelLarge).foregroundColor(.redAlert)
                .frame(maxWidth: .infinity).padding(14)
                .background(Color.redFaint)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.redAlert.opacity(0.4)))
                .clipShape(RoundedRectangle(cornerRadius: 12))
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

    private var successPayments: [PaymentHistoryItem] {
        vm.studentPayments.filter { $0.status == "SUCCESS" }
    }

    private var paymentHistorySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Payment History").font(.headlineSmall).foregroundColor(.textPrimary)
            if successPayments.isEmpty {
                Text("No payments found").font(.bodySmall).foregroundColor(.textMuted)
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
            } else {
                ForEach(successPayments) { p in
                    AppCard {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("₹\(String(format: "%.0f", p.amount))")
                                    .font(.labelLarge).foregroundColor(.textPrimary)
                                Spacer()
                                StatusChip(status: p.status)
                            }
                            let isCash = p.paymentGateway == "CASH"
                            Text(isCash ? "Cash" : "Online")
                                .font(.labelSmall)
                                .foregroundColor(isCash ? .amber : .emerald)
                            if let ref = p.gatewayOrderId {
                                Text("Ref: \(ref)").font(.bodySmall).foregroundColor(.textMuted)
                                    .lineLimit(1)
                            }
                            if let paidAt = p.paidAt {
                                Text(paidAt.prefix(16).description)
                                    .font(.bodySmall).foregroundColor(.textSub)
                            }
                        }
                    }
                }
            }
        }
    }

    private var seatHistorySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Seat History").font(.headlineSmall).foregroundColor(.textPrimary)
            if vm.studentSeatHistory.isEmpty {
                Text("No seat history found").font(.bodySmall).foregroundColor(.textMuted)
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
            } else {
                ForEach(vm.studentSeatHistory) { h in
                    AppCard {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(h.seatNumber ?? "—")
                                    .font(.labelLarge).foregroundColor(.textPrimary)
                                Spacer()
                                if let status = h.status { StatusChip(status: status) }
                            }
                            Text("\(h.startDate ?? "—") → \(h.endDate ?? "—") · \((h.shift ?? "").capitalized)")
                                .font(.bodySmall).foregroundColor(.textSub)
                            if let plan = h.planName {
                                Text(plan).font(.bodySmall).foregroundColor(.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    private func changeStatusSheet(_ s: StudentDetail) -> some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 16) {
                    Text("Correct \(s.name)'s wrongly-marked-Paid membership. This deletes their last payment record and cannot be undone.")
                        .font(.bodySmall).foregroundColor(.textSub)

                    HStack(spacing: 10) {
                        statusTargetButton("Pending", value: "PENDING")
                        statusTargetButton("Grace", value: "GRACE")
                    }

                    if changeStatusTarget == "PENDING" {
                        AppTextField(label: "Pending Amount (₹)", text: $pendingAmountInput,
                                    placeholder: "e.g. 200", keyboardType: .decimalPad,
                                    leadingIcon: "indianrupeesign")
                    } else {
                        Text("Sets dues to the full plan price and resets their expiry date to today, so days-overdue counts from now.")
                            .font(.bodySmall).foregroundColor(.textSub)
                    }

                    Spacer()

                    PrimaryButton(changeStatusTarget == "PENDING" ? "Mark Pending" : "Mark Grace") {
                        guard let membershipId = s.membershipId else { return }
                        if changeStatusTarget == "PENDING" {
                            guard let amount = Double(pendingAmountInput), amount > 0 else { return }
                            vm.markPending(membershipId: membershipId, pendingAmount: amount, userId: s.id)
                        } else {
                            vm.markGrace(membershipId: membershipId, userId: s.id)
                        }
                        showChangeStatus = false
                    }
                    .disabled(changeStatusTarget == "PENDING" && (Double(pendingAmountInput) ?? 0) <= 0)
                }
                .padding(16)
            }
            .navigationTitle("Change Status")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showChangeStatus = false }.foregroundColor(.amber)
                }
            }
        }
    }

    private func statusTargetButton(_ title: String, value: String) -> some View {
        let selected = changeStatusTarget == value
        return Button { changeStatusTarget = value } label: {
            Text(title)
                .font(.labelMedium)
                .foregroundColor(selected ? .navyDeep : .textSub)
                .frame(maxWidth: .infinity).padding(.vertical, 10)
                .background(selected ? Color.amber : Color.cardBg)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.amber : Color.cardBorder))
        }
    }

    @ViewBuilder
    private func editSheet(_ s: StudentDetail) -> some View {
        EditStudentSheet(s: s, vm: vm, onDismiss: { showEdit = false })
    }

    private var messageSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 16) {
                    if let student = vm.selectedStudent {
                        Text("To: \(student.name)").font(.bodySmall).foregroundColor(.textMuted)
                    }
                    TextEditor(text: $messageText)
                        .frame(minHeight: 120)
                        .padding(10)
                        .background(Color.navyMid)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .foregroundColor(.textPrimary)
                        .font(.bodyMedium)

                    PrimaryButton("Send via WhatsApp") {
                        if let id = vm.selectedStudent?.id {
                            vm.sendMessageToStudent(id: id, message: messageText.trimmingCharacters(in: .whitespacesAndNewlines))
                            showMessage = false
                            messageText = ""
                        }
                    }
                    .disabled(messageText.trimmingCharacters(in: .whitespacesAndNewlines).count < 5)
                }
                .padding(16)
                .frame(maxHeight: .infinity, alignment: .top)
            }
            .navigationTitle("Send Message")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showMessage = false; messageText = "" }.foregroundColor(.amber)
                }
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

private struct EditStudentSheet: View {
    let s: StudentDetail
    @ObservedObject var vm: AdminViewModel
    let onDismiss: () -> Void

    @State private var name:         String
    @State private var mobile:       String
    @State private var email:        String
    @State private var address:      String
    @State private var gender:       String
    @State private var dateOfBirth:  String
    @State private var joinedAt:     String
    @State private var seatNumber:   String
    @State private var selectedPlanId: String

    init(s: StudentDetail, vm: AdminViewModel, onDismiss: @escaping () -> Void) {
        self.s = s
        self.vm = vm
        self.onDismiss = onDismiss
        _name           = State(initialValue: s.name)
        _mobile         = State(initialValue: s.mobile)
        _email          = State(initialValue: s.email ?? "")
        _address        = State(initialValue: s.address ?? "")
        _gender         = State(initialValue: s.gender ?? "")
        _dateOfBirth    = State(initialValue: s.dateOfBirth ?? "")
        _joinedAt       = State(initialValue: String(s.joinedAt?.prefix(10) ?? ""))
        _seatNumber     = State(initialValue: s.seatNumber ?? "")
        _selectedPlanId = State(initialValue: s.membershipPlanId ?? "")
    }

    private let genderOptions = ["", "Male", "Female", "Other"]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 12) {
                        editField("Name",          text: $name)
                        editField("Mobile",        text: $mobile,      keyboard: .phonePad)
                        editField("Email",         text: $email,       keyboard: .emailAddress)
                        editField("Address",       text: $address,     lines: 2)
                        editField("Date of Birth", text: $dateOfBirth, placeholder: "yyyy-MM-dd")
                        editField("Joined Date",   text: $joinedAt,    placeholder: "yyyy-MM-dd")

                        // Gender picker
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Gender").font(.labelSmall).foregroundColor(.textMuted)
                            Picker("", selection: $gender) {
                                ForEach(genderOptions, id: \.self) { opt in
                                    Text(opt.isEmpty ? "—" : opt).tag(opt)
                                }
                            }
                            .pickerStyle(.segmented)
                        }
                        .padding(12)
                        .background(Color.navyMid)
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                        if s.membershipId != nil {
                            editField("Seat Number", text: $seatNumber)

                            // Plan picker
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Plan").font(.labelSmall).foregroundColor(.textMuted)
                                Picker("", selection: $selectedPlanId) {
                                    Text("— no change —").tag(s.membershipPlanId ?? "")
                                    ForEach(vm.plans, id: \.id) { plan in
                                        Text(plan.name).tag(plan.id)
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(.amber)
                                .padding(8)
                                .background(Color.navyMid)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                        }

                        PrimaryButton("Save Changes") {
                            let req = UpdateStudentRequest(
                                name:        name.trimmingCharacters(in: .whitespaces),
                                mobile:      mobile.trimmingCharacters(in: .whitespaces).isEmpty ? nil : mobile.trimmingCharacters(in: .whitespaces),
                                email:       email.trimmingCharacters(in: .whitespaces).isEmpty  ? nil : email.trimmingCharacters(in: .whitespaces),
                                address:     address.trimmingCharacters(in: .whitespaces).isEmpty ? nil : address.trimmingCharacters(in: .whitespaces),
                                gender:      gender.isEmpty ? nil : gender,
                                dateOfBirth: dateOfBirth.trimmingCharacters(in: .whitespaces).isEmpty ? nil : dateOfBirth.trimmingCharacters(in: .whitespaces),
                                joinedAt:    joinedAt.trimmingCharacters(in: .whitespaces).isEmpty ? nil : joinedAt.trimmingCharacters(in: .whitespaces)
                            )
                            vm.updateStudent(id: s.id, req: req)
                            if let mid = s.membershipId {
                                let trimSeat = seatNumber.trimmingCharacters(in: .whitespaces)
                                if !trimSeat.isEmpty && trimSeat != s.seatNumber {
                                    vm.changeSeat(membershipId: mid, seatNumber: trimSeat)
                                }
                                if !selectedPlanId.isEmpty && selectedPlanId != s.membershipPlanId {
                                    vm.updateMembershipPlan(membershipId: mid, planId: selectedPlanId)
                                }
                            }
                            onDismiss()
                        }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Edit Profile")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { vm.loadPlans() }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel", action: onDismiss).foregroundColor(.amber)
                }
            }
        }
    }

    @ViewBuilder
    private func editField(_ label: String, text: Binding<String>, placeholder: String = "", keyboard: UIKeyboardType = .default, lines: Int = 1) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.labelSmall).foregroundColor(.textMuted)
            if lines > 1 {
                TextEditor(text: text)
                    .frame(minHeight: CGFloat(lines) * 44)
                    .padding(8)
                    .background(Color.navyMid)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .foregroundColor(.textPrimary)
                    .font(.bodyMedium)
            } else {
                TextField(placeholder.isEmpty ? label : placeholder, text: text)
                    .keyboardType(keyboard)
                    .padding(12)
                    .background(Color.navyMid)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .foregroundColor(.textPrimary)
                    .font(.bodyMedium)
            }
        }
    }
}
