import SwiftUI

// Mirrors the web admin flow (frontend/src/pages/admin/AdminCreateMembershipPage.jsx):
// a 4-step wizard — find/select student, pick plan+shift+date, pick a seat off the
// live grid, then review & confirm cash payment — instead of a single form where the
// admin had to paste a student UUID by hand and type a seat number blind.
struct AdminCreateMembershipView: View {
    @ObservedObject var vm: AdminViewModel
    @Environment(\.dismiss) private var dismiss
    private let repo = AdminRepository.shared

    @State private var step = 1

    // Step 1 — student
    @State private var search             = ""
    @State private var selectedStudent:    StudentSummary?
    @State private var bookingType:        String?   // nil | "renewal" | "new"
    @State private var renewalLoading      = false
    @State private var studentsLoading     = false
    @State private var studentsError:      String?

    // Step 2 — plan / shift / date
    @State private var selectedPlan:       Plan?
    @State private var selectedShift       = ""
    @State private var startDate           = AdminCreateMembershipView.today
    @State private var plansError:         String?

    // Step 3 — seat
    @State private var seats:              [Seat] = []
    @State private var seatsLoading        = false
    @State private var selectedSeat:       Seat?

    // Step 4 — payment
    @State private var paidAmountStr       = ""
    @State private var pendingAmountStr    = "0"
    @State private var cashConfirmed       = false
    @State private var paymentMode         = "CASH"   // "CASH" | "UPI-QR"
    @State private var showQrSheet         = false
    @State private var sendingRequest      = false
    @State private var paymentRequestMsg   = ""
    @State private var paymentRequestError = ""

    private static let today: String = {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.string(from: Date())
    }()

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    stepIndicator.padding(.horizontal, 16).padding(.vertical, 12)
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            switch step {
                            case 1: step1Content
                            case 2: step2Content
                            case 3: step3Content
                            default: step4Content
                            }
                        }
                        .padding(16)
                    }
                    .scrollDismissesKeyboard(.interactively)
                }
            }
            .dismissKeyboardOnTap()
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
        .onAppear {
            // vm is shared across the whole admin session — clear out any error/success
            // left over from an unrelated screen visited before this sheet was opened,
            // so step 4's error banner can only ever reflect this wizard's own submit.
            vm.clearMessages()
            loadPlans()
            loadStudents()
            vm.loadAppSettings()
        }
        .onChange(of: vm.successMsg) { msg in
            if msg != nil { vm.clearSuccess(); dismiss() }
        }
    }

    // MARK: - Step indicator

    private var stepIndicator: some View {
        HStack(spacing: 6) {
            ForEach(1..<5) { n in
                let done = step > n
                let current = step == n
                HStack(spacing: 6) {
                    ZStack {
                        Circle()
                            .fill(done ? Color.emerald.opacity(0.2) : current ? Color.amberFaint : Color.cardBg)
                            .overlay(Circle().stroke(done ? Color.emerald.opacity(0.4) : current ? Color.amber : Color.cardBorder))
                            .frame(width: 26, height: 26)
                        if done {
                            Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundColor(.emerald)
                        } else {
                            Text("\(n)").font(.labelSmall).fontWeight(.bold)
                                .foregroundColor(current ? .amber : .textMuted)
                        }
                    }
                    if n < 4 {
                        Rectangle().fill(done ? Color.emerald.opacity(0.4) : Color.cardBorder).frame(height: 1)
                    }
                }
            }
        }
    }

    // MARK: - Step 1: Find Student

    private var filteredStudents: [StudentSummary] {
        vm.students.filter { s in
            s.membershipStatus != "ACTIVE" &&
            (search.isEmpty ||
             s.name.localizedCaseInsensitiveContains(search) ||
             s.mobile.localizedCaseInsensitiveContains(search) ||
             (s.email?.localizedCaseInsensitiveContains(search) ?? false))
        }
    }

    // Loads directly via the repo (rather than vm.loadStudents(), which writes any
    // failure into the shared vm.error) so a failure here can't bleed into some
    // later, unrelated step that also happens to display vm.error — e.g. this
    // wizard's own step 4 was showing a stale error from this call days after it
    // actually failed, with no visible connection to student search at all.
    private func loadStudents() {
        studentsError = nil
        studentsLoading = true
        Task {
            do {
                let result = try await repo.getStudents(page: 0, size: 200)
                vm.students = result.students
                vm.studentsTotal = result.total
            } catch {
                studentsError = error.localizedDescription
            }
            studentsLoading = false
        }
    }

    private var step1Content: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundColor(.textMuted)
                TextField("Search by name or mobile...", text: $search)
                    .foregroundColor(.textPrimary).font(.bodyMedium)
                if !search.isEmpty {
                    Button { search = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(10)
            .background(Color.navyMid.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            if studentsLoading && vm.students.isEmpty {
                LoadingView().frame(height: 200)
            } else if let err = studentsError, vm.students.isEmpty {
                VStack(spacing: 10) {
                    ErrorBanner(message: err)
                    OutlineButton("Retry") { loadStudents() }
                }
            } else if filteredStudents.isEmpty {
                Text("No students found").foregroundColor(.textMuted)
                    .frame(maxWidth: .infinity).padding(.vertical, 40)
            } else {
                ForEach(filteredStudents) { s in
                    studentRow(s)
                }
            }

            if selectedStudent?.membershipStatus == "EXPIRED" && bookingType == nil {
                renewalPrompt
            }
        }
    }

    private func studentRow(_ s: StudentSummary) -> some View {
        let selected = selectedStudent?.id == s.id
        return Button {
            selectedStudent = s
            if s.membershipStatus == "EXPIRED" {
                bookingType = nil
            } else {
                bookingType = "new"
                startDate = Self.today
                step = 2
            }
        } label: {
            AppCard(accentColor: selected ? .amber : nil) {
                HStack(spacing: 12) {
                    avatarCircle(s.name)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.name).font(.labelLarge).foregroundColor(.textPrimary)
                        Text(s.mobile.isEmpty ? (s.email ?? "—") : s.mobile)
                            .font(.bodySmall).foregroundColor(.textSub)
                    }
                    Spacer()
                    membershipBadge(s)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func membershipBadge(_ s: StudentSummary) -> some View {
        let (text, color): (String, Color) = {
            if s.membershipEnd != nil { return ("Has Membership", .amber) }
            if s.membershipStatus == "EXPIRED" { return ("Expired", .redAlert) }
            return ("No Plan", .emerald)
        }()
        return Text(text)
            .font(.labelSmall).foregroundColor(color)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(color.opacity(0.15))
            .overlay(Capsule().stroke(color.opacity(0.4)))
            .clipShape(Capsule())
    }

    private var renewalPrompt: some View {
        AppCard(accentColor: .amber) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Previous membership found").font(.labelLarge).foregroundColor(.amber)
                Text("\(selectedStudent?.name ?? "") had a membership that expired. How would you like to proceed?")
                    .font(.bodySmall).foregroundColor(.textSub)
                HStack(spacing: 10) {
                    Button { handleSelectRenewal() } label: {
                        HStack {
                            if renewalLoading { ProgressView().tint(.amber) }
                            else { Text("🔄 Renewal — from last expiry") }
                        }
                        .font(.labelMedium).foregroundColor(.amber)
                        .frame(maxWidth: .infinity).padding(10)
                        .background(Color.amberFaint)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.4)))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .disabled(renewalLoading)

                    Button {
                        bookingType = "new"; startDate = Self.today; step = 2
                    } label: {
                        Text("➕ New Booking")
                            .font(.labelMedium).foregroundColor(.textSub)
                            .frame(maxWidth: .infinity).padding(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func handleSelectRenewal() {
        guard let s = selectedStudent else { return }
        renewalLoading = true
        Task {
            if let detail = try? await repo.getStudentDetail(id: s.id) {
                startDate = detail.membershipEnd.map { addDays($0, 1) } ?? Self.today
            } else {
                startDate = Self.today
            }
            bookingType = "renewal"
            renewalLoading = false
            step = 2
        }
    }

    private func avatarCircle(_ name: String) -> some View {
        Circle().fill(Color.navyLight).frame(width: 40, height: 40)
            .overlay(Text(String(name.prefix(1)).uppercased()).font(.labelLarge).foregroundColor(.amber))
    }

    // MARK: - Step 2: Plan + Shift + Date

    private var resolvedShift: String {
        selectedPlan?.planType == "FULL_DAY" ? "FULL_DAY" : selectedShift
    }

    private var endDate: String {
        guard let plan = selectedPlan else { return "—" }
        return addDays(startDate, plan.durationDays)
    }

    private var canGoStep3: Bool {
        selectedPlan != nil && !startDate.isEmpty &&
        (selectedPlan?.planType == "FULL_DAY" || !selectedShift.isEmpty)
    }

    private var dateBinding: Binding<Date> {
        Binding(get: { parseDate(startDate) ?? Date() },
                set: { startDate = formatDate($0) })
    }

    // Loads plans directly via the repo (rather than vm.loadPlans(), which only
    // populates vm.plans on success and silently leaves it empty with no visible
    // feedback on failure) so a failed fetch shows a retry affordance instead of
    // an indefinite spinner — vm.plans stays empty forever with no way to tell
    // "still loading" from "failed" otherwise.
    private func loadPlans() {
        plansError = nil
        Task {
            do {
                vm.plans = try await repo.getPlans()
            } catch {
                plansError = error.localizedDescription
            }
        }
    }

    private var step2Content: some View {
        VStack(alignment: .leading, spacing: 16) {
            selectedStudentHeader {
                step = 1; selectedStudent = nil; bookingType = nil; startDate = Self.today
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("Select Plan").font(.labelMedium).foregroundColor(.textSub)
                if vm.plans.isEmpty {
                    if let err = plansError {
                        VStack(alignment: .leading, spacing: 10) {
                            ErrorBanner(message: err)
                            OutlineButton("Retry") { loadPlans() }
                        }
                    } else {
                        ProgressView().tint(.amber)
                    }
                } else {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(vm.plans.filter { $0.isActive }) { plan in planCard(plan) }
                    }
                }
            }

            if selectedPlan?.planType == "HALF_DAY" {
                shiftPickerSection
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Start Date").font(.labelMedium).foregroundColor(.textSub)
                if bookingType == "renewal" {
                    HStack(spacing: 10) {
                        Image(systemName: "arrow.triangle.2.circlepath").foregroundColor(.amber)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Renewing from last expiry").font(.caption).foregroundColor(.amber)
                            Text(startDate).font(.bodyMedium).foregroundColor(.textPrimary)
                        }
                    }
                    .padding(12)
                    .background(Color.amberFaint)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.3)))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                } else {
                    DatePicker("", selection: dateBinding, displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .labelsHidden()
                        .tint(.amber)
                        .colorScheme(.dark)
                }
                if selectedPlan != nil {
                    Text("Ends \(endDate)").font(.bodySmall).foregroundColor(.textSub)
                }
            }

            HStack(spacing: 12) {
                OutlineButton("Back") { step = 1 }
                PrimaryButton("Next") { step = 3 }
                    .disabled(!canGoStep3)
                    .opacity(canGoStep3 ? 1 : 0.4)
            }
        }
    }

    private func planCard(_ plan: Plan) -> some View {
        let selected = selectedPlan?.id == plan.id
        return Button {
            selectedPlan = plan; selectedShift = ""
            paidAmountStr = formatAmount(plan.price); pendingAmountStr = "0"
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(plan.name).font(.labelLarge).foregroundColor(.textPrimary)
                Text("₹\(String(format: "%.0f", plan.price))").font(.headlineSmall).foregroundColor(.amber)
                Text("\(plan.durationDays)d · \(plan.planType == "FULL_DAY" ? "Full Day" : "Half Day")")
                    .font(.caption).foregroundColor(.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(selected ? Color.amberFaint : Color.cardBg)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.amber : Color.cardBorder))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private var shiftPickerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Shift").font(.labelMedium).foregroundColor(.textSub)
            HStack(spacing: 8) {
                ForEach(["MORNING", "EVENING"], id: \.self) { shift in
                    let sel = selectedShift == shift
                    Button { selectedShift = shift } label: {
                        Text(shift.capitalized)
                            .font(.labelMedium)
                            .foregroundColor(sel ? .navyDeep : .textSub)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .background(sel ? Color.amber : Color.cardBg)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(sel ? Color.amber : Color.cardBorder))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func selectedStudentHeader(onChange: @escaping () -> Void) -> some View {
        AppCard {
            HStack(spacing: 12) {
                avatarCircle(selectedStudent?.name ?? "?")
                VStack(alignment: .leading, spacing: 2) {
                    Text(selectedStudent?.name ?? "").font(.labelLarge).foregroundColor(.textPrimary)
                    Text(selectedStudent?.mobile ?? selectedStudent?.email ?? "")
                        .font(.bodySmall).foregroundColor(.textSub)
                }
                Spacer()
                Button("Change", action: onChange).font(.labelSmall).foregroundColor(.amber)
            }
        }
    }

    // MARK: - Step 3: Seat Selection

    private var step3Content: some View {
        VStack(alignment: .leading, spacing: 16) {
            summaryBar

            VStack(alignment: .leading, spacing: 10) {
                Text("Select a Seat").font(.labelMedium).foregroundColor(.textSub)
                if seatsLoading {
                    LoadingView().frame(height: 240)
                } else {
                    SeatGridView(seats: seats, selectedSeat: selectedSeat?.seatNumber) { seat in
                        selectedSeat = seat
                    }
                }
                if let seat = selectedSeat {
                    Text("Selected seat: \(seat.seatNumber)").font(.bodySmall).foregroundColor(.amber)
                }
            }

            HStack(spacing: 12) {
                OutlineButton("Back") { step = 2 }
                PrimaryButton("Next") { step = 4 }
                    .disabled(selectedSeat == nil)
                    .opacity(selectedSeat == nil ? 0.4 : 1)
            }
        }
        .onAppear { loadSeats() }
    }

    private var summaryBar: some View {
        AppCard {
            HStack(spacing: 16) {
                summaryItem("Student", selectedStudent?.name ?? "—")
                summaryItem("Plan", selectedPlan?.name ?? "—")
                summaryItem("Shift", resolvedShift)
            }
        }
    }

    private func summaryItem(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundColor(.textMuted)
            Text(value).font(.labelMedium).foregroundColor(.textPrimary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func loadSeats() {
        guard let plan = selectedPlan else { return }
        let shift = plan.planType == "FULL_DAY" ? "FULL_DAY" : selectedShift
        seatsLoading = true
        selectedSeat = nil
        Task {
            seats = (try? await SeatRepository.shared.getAvailability(shift: shift, date: startDate)) ?? []
            seatsLoading = false
        }
    }

    // MARK: - Step 4: Review & Confirm

    private var paidBinding: Binding<String> {
        Binding(
            get: { paidAmountStr },
            set: { newVal in
                paidAmountStr = newVal
                let paid = Double(newVal) ?? 0
                pendingAmountStr = formatAmount(max(0, (selectedPlan?.price ?? 0) - paid))
            }
        )
    }

    private var pendingBinding: Binding<String> {
        Binding(
            get: { pendingAmountStr },
            set: { newVal in
                pendingAmountStr = newVal
                let pending = Double(newVal) ?? 0
                paidAmountStr = formatAmount(max(0, (selectedPlan?.price ?? 0) - pending))
            }
        )
    }

    private var step4Content: some View {
        VStack(alignment: .leading, spacing: 16) {
            AppCard {
                VStack(alignment: .leading, spacing: 10) {
                    reviewRow("Student", "\(selectedStudent?.name ?? "") (\(selectedStudent?.mobile ?? selectedStudent?.email ?? "—"))")
                    reviewRow("Plan", selectedPlan?.name ?? "—")
                    reviewRow("Shift", resolvedShift)
                    reviewRow("Seat", selectedSeat?.seatNumber ?? "—")
                    reviewRow("Start", startDate)
                    reviewRow("End", endDate)
                    reviewRow("Amount", "₹\(String(format: "%.0f", selectedPlan?.price ?? 0))")

                    Divider().overlay(Color.cardBorder)

                    HStack {
                        Text("Paid Amount").font(.bodyMedium).foregroundColor(.textSub)
                        Spacer()
                        amountField(paidBinding)
                    }
                    HStack {
                        Text("Pending Amount").font(.bodyMedium).foregroundColor(.textSub)
                        Spacer()
                        amountField(pendingBinding)
                    }
                    HStack {
                        Text("Payment").font(.bodyMedium).foregroundColor(.textSub)
                        Spacer()
                        Button {
                            paymentMode = paymentMode == "CASH" ? "UPI-QR" : "CASH"
                        } label: {
                            Text(paymentMode == "CASH" ? "💵 Cash" : "📱 UPI (QR)")
                                .font(.labelSmall).foregroundColor(paymentMode == "CASH" ? .amber : .blueSoft)
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(paymentMode == "CASH" ? Color.amberFaint : Color.blueFaint)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }

                    if paymentMode == "UPI-QR" {
                        if let upiId = vm.appSettings.upiId, !upiId.isEmpty {
                            HStack(spacing: 10) {
                                Button { showQrSheet = true } label: {
                                    Text("📱 Show QR Code")
                                        .font(.labelSmall).foregroundColor(.blueSoft)
                                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                                        .background(Color.blueFaint)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.blueSoft.opacity(0.4)))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)

                                Button {
                                    sendPaymentRequest()
                                } label: {
                                    Text(sendingRequest ? "Sending…" : "💬 Send Request")
                                        .font(.labelSmall).foregroundColor(.emerald)
                                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                                        .background(Color.emeraldFaint)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.emerald.opacity(0.4)))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                                .disabled(sendingRequest)
                            }
                            if !paymentRequestMsg.isEmpty {
                                Text(paymentRequestMsg).font(.caption).foregroundColor(.emerald)
                            }
                            if !paymentRequestError.isEmpty {
                                Text(paymentRequestError).font(.caption).foregroundColor(.redAlert)
                            }
                        } else {
                            Text("Set a UPI ID in App Settings first to enable QR/payment request.")
                                .font(.caption).foregroundColor(.yellowWarn)
                        }
                    }
                }
            }

            Button { cashConfirmed.toggle() } label: {
                AppCard(accentColor: .amber) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: cashConfirmed ? "checkmark.square.fill" : "square")
                            .foregroundColor(cashConfirmed ? .amber : .textMuted)
                            .font(.system(size: 20))
                        Text(confirmationText)
                            .font(.bodySmall).foregroundColor(.textSub)
                    }
                }
            }
            .buttonStyle(.plain)

            if let err = vm.error { ErrorBanner(message: err) }

            HStack(spacing: 12) {
                OutlineButton("Back") { step = 3 }
                PrimaryButton(vm.isLoading ? "Creating…" : "Create Membership", isLoading: vm.isLoading) { submit() }
                    .disabled(!cashConfirmed || vm.isLoading)
                    .opacity(cashConfirmed ? 1 : 0.4)
            }
        }
        .sheet(isPresented: $showQrSheet) { qrSheet }
    }

    private var confirmationText: String {
        let paid = Double(paidAmountStr) ?? 0
        let pending = Double(pendingAmountStr) ?? 0
        let name = selectedStudent?.name ?? "the student"
        let method = paymentMode == "UPI-QR" ? "UPI" : "cash"
        if paid <= 0 {
            return "I confirm \(name) is being registered with no \(method) collected — the full ₹\(String(format: "%.0f", pending)) plan price remains as a pending balance."
        }
        if pending > 0 {
            return "I confirm ₹\(String(format: "%.0f", paid)) \(method) payment has been collected from \(name), with ₹\(String(format: "%.0f", pending)) remaining as a pending balance."
        }
        return "I confirm ₹\(String(format: "%.0f", paid)) \(method) payment has been collected from \(name)."
    }

    private func sendPaymentRequest() {
        guard let student = selectedStudent else { return }
        let paid = Double(paidAmountStr) ?? 0
        sendingRequest = true
        paymentRequestMsg = ""
        paymentRequestError = ""
        vm.sendPaymentRequest(studentId: student.id, amount: paid) { result in
            sendingRequest = false
            switch result {
            case .success:
                paymentRequestMsg = "Payment request sent"
            case .failure(let err):
                paymentRequestError = err.localizedDescription
            }
        }
    }

    private var qrSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 16) {
                    Text("Scan to Pay").font(.headlineSmall).foregroundColor(.textPrimary)
                    if let upiId = vm.appSettings.upiId {
                        let paid = Double(paidAmountStr) ?? 0
                        let link = buildUpiDeepLink(vpa: upiId, payeeName: "Target Zone Library",
                                                    amount: paid, note: "Library fee - \(selectedStudent?.name ?? "")")
                        Image(uiImage: generateQrImage(from: link))
                            .interpolation(.none)
                            .resizable()
                            .frame(width: 240, height: 240)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        Text("₹\(String(format: "%.2f", paid)) — scan with any UPI app")
                            .font(.bodySmall).foregroundColor(.textSub)
                    }
                    Spacer()
                }
                .padding(16)
            }
            .navigationTitle("UPI QR")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") { showQrSheet = false }.foregroundColor(.amber)
                }
            }
        }
    }

    private func reviewRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.bodySmall).foregroundColor(.textMuted)
            Spacer()
            Text(value).font(.bodyMedium).foregroundColor(.textPrimary)
        }
    }

    private func amountField(_ binding: Binding<String>) -> some View {
        TextField("0", text: binding)
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.trailing)
            .foregroundColor(.textPrimary)
            .font(.bodyMedium)
            .frame(width: 90)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Color.cardBg)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func submit() {
        guard let student = selectedStudent, let plan = selectedPlan, let seat = selectedSeat else { return }
        vm.clearError()
        vm.createCashMembership(
            studentId: student.id, planId: plan.id, seatNumber: seat.seatNumber,
            shift: resolvedShift, startDate: startDate,
            paidAmount: Double(paidAmountStr), pendingAmount: Double(pendingAmountStr),
            paymentMode: paymentMode)
    }

    // MARK: - Date helpers

    private func formatAmount(_ v: Double) -> String {
        v == v.rounded() ? String(format: "%.0f", v) : String(v)
    }

    private func parseDate(_ s: String) -> Date? {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.date(from: s)
    }

    private func formatDate(_ date: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.string(from: date)
    }

    private func addDays(_ s: String, _ days: Int) -> String {
        guard let d = parseDate(s) else { return s }
        let newDate = Calendar.current.date(byAdding: .day, value: days, to: d) ?? d
        return formatDate(newDate)
    }
}
