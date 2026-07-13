import SwiftUI
import UIKit

// See AdminImportView's identical PhotoCaptureStage for why camera + crop share one
// fullScreenCover instead of presenting two in sequence.
private enum StudentPhotoCaptureStage {
    case camera
    case cropping(UIImage)
}

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

    private enum ProfileField { case name, mobile, email, gender, dateOfBirth, address, joinedAt }
    @State private var editingField: ProfileField?
    @State private var editBuffer = ""
    @FocusState private var focusedField: ProfileField?

    private enum ClearKind { case fees, dues }
    @State private var clearKind:       ClearKind?
    @State private var clearAmountText = ""
    @State private var clearPaymentMode = "CASH"   // "CASH" | "UPI-QR"
    @State private var showReleaseConfirm = false
    @State private var showReleaseNotifyPrompt = false
    @State private var showRenewConfirm = false
    @State private var showDeleteConfirm = false

    @State private var showChangeStatus     = false
    @State private var changeStatusTarget   = "PENDING" // PENDING | GRACE
    @State private var pendingAmountInput   = ""

    @State private var showPhotoCapture     = false
    @State private var photoCaptureStage: StudentPhotoCaptureStage = .camera
    @State private var photoCaptureError:   String?
    @State private var showPhotoPreview     = false
    // The backend saves every re-upload to the same deterministic filename
    // (photo_student_<id>.jpg), so photoUrl itself never changes on a
    // retake — AsyncImage keys its cache on the URL, so without this it
    // would keep showing the old photo after a successful re-upload.
    // Bumped right before each upload so the displayed URL is always fresh.
    @State private var photoCacheBuster     = UUID().uuidString

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
            .sheet(isPresented: $showChangeStatus) {
                if let s = vm.selectedStudent { changeStatusSheet(s) }
            }
            .sheet(isPresented: Binding(get: { clearKind != nil }, set: { if !$0 { clearKind = nil } })) {
                if let s = vm.selectedStudent { clearAmountSheet(s) }
            }
            .sheet(isPresented: $showPhotoPreview) {
                if let s = vm.selectedStudent { photoPreviewSheet(s) }
            }
            .fullScreenCover(isPresented: $showPhotoCapture) {
                switch photoCaptureStage {
                case .camera:
                    CameraCaptureView { image in
                        photoCaptureStage = .cropping(image)
                    } onCancel: {
                        showPhotoCapture = false
                    }
                case .cropping(let raw):
                    PassportCropView(image: raw, aspect: 1) { cropped in
                        showPhotoCapture = false
                        if let jpeg = cropped.jpegData(compressionQuality: 0.85), let s = vm.selectedStudent {
                            photoCacheBuster = UUID().uuidString
                            vm.uploadStudentPhoto(id: s.id, photoData: jpeg)
                        }
                    } onCancel: {
                        showPhotoCapture = false
                    }
                }
            }
        }
        .dismissKeyboardOnTap()
        .navigationTitle("Student Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.navyMid, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onAppear { vm.loadStudentDetail(id: studentId) }
        .onChange(of: vm.studentDeleted) { deleted in
            if deleted { vm.studentDeleted = false; dismiss() }
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

    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            photoCaptureError = "Camera not available on this device"
            return
        }
        photoCaptureError = nil
        photoCaptureStage = .camera
        showPhotoCapture = true
    }

    private func photoURL(_ urlStr: String) -> URL? {
        URL(string: baseURL + urlStr + "?v=\(photoCacheBuster)")
    }

    // Mirrors AdminPaymentVerificationsView's screenshot preview sheet.
    private func photoPreviewSheet(_ s: StudentDetail) -> some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                if let urlStr = s.photoUrl, let url = photoURL(urlStr) {
                    AsyncImage(url: url) { phase in
                        if case .success(let img) = phase {
                            img.resizable().scaledToFit()
                        } else {
                            Color.navyMid
                        }
                    }
                    .padding(16)
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { showPhotoPreview = false }.foregroundColor(.white)
                }
            }
        }
    }

    private func profileHeader(_ s: StudentDetail) -> some View {
        AppCard(accentColor: .amber) {
            VStack(spacing: 12) {
                ZStack(alignment: .bottomTrailing) {
                    if let urlStr = s.photoUrl, let url = photoURL(urlStr) {
                        AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                        placeholder: { Color.navyLight }
                            .frame(width: 72, height: 72).clipShape(Circle())
                            .overlay(Circle().stroke(Color.amber, lineWidth: 2))
                            .onTapGesture { showPhotoPreview = true }
                    } else {
                        Circle().fill(Color.navyLight).frame(width: 72, height: 72)
                            .overlay(Text(String(s.name.prefix(1))).font(.headlineLarge).foregroundColor(.amber))
                    }

                    Button { openCamera() } label: {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.navyDeep)
                            .padding(6)
                            .background(Color.amber)
                            .clipShape(Circle())
                    }
                }
                if let err = photoCaptureError {
                    Text(err).font(.labelSmall).foregroundColor(.redAlert)
                }

                editableNameHeader(s)
                StatusChip(status: s.isActive ? "ACTIVE" : "INACTIVE")

                Text("Long-press a field below to edit it")
                    .font(.labelSmall).foregroundColor(.textMuted)

                Divider().background(Color.dividerColor)
                editableInfoRow(s, label: "Mobile", value: s.mobile, field: .mobile, keyboard: .phonePad)
                editableInfoRow(s, label: "Email", value: s.email ?? "", field: .email, keyboard: .emailAddress)
                editableGenderRow(s, value: s.gender ?? "")
                editableDateRow(s, label: "DOB", value: s.dateOfBirth ?? "", field: .dateOfBirth)
                editableInfoRow(s, label: "Address", value: s.address ?? "", field: .address)
                editableDateRow(s, label: "Joined", value: String(s.joinedAt?.prefix(10) ?? ""), field: .joinedAt)
            }
        }
    }

    // Switching directly from one field's long-press to another's (without ever
    // tapping away to drop focus first) would otherwise discard whatever was
    // typed into the field being left, since editBuffer/editingField are shared
    // single-field state — commit it before moving on.
    private func beginEditing(_ s: StudentDetail, field: ProfileField, value: String) {
        if editingField != nil, editingField != field { saveEditingField(s) }
        editBuffer = value
        editingField = field
        focusedField = field
    }

    private func editableNameHeader(_ s: StudentDetail) -> some View {
        Group {
            if editingField == .name {
                TextField("Name", text: $editBuffer)
                    .font(.headlineMedium)
                    .padding(8)
                    .background(Color.navyMid)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundColor(.textPrimary)
                    .focused($focusedField, equals: .name)
                    .onSubmit { saveEditingField(s) }
                    .onChange(of: focusedField) { newValue in
                        if newValue != .name && editingField == .name { saveEditingField(s) }
                    }
            } else {
                Text(s.name).font(.headlineMedium).foregroundColor(.textPrimary)
                    .contextMenu {
                        Button {
                            beginEditing(s, field: .name, value: s.name)
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                    }
            }
        }
    }

    // Generic long-press-to-edit row for the plain-text profile fields (mobile,
    // email, DOB, address, joined date) — replaces the old modal "Edit Profile"
    // sheet with inline editing directly on this page. Rows always render (with
    // an em-dash placeholder when empty) so a blank field can still be long-pressed
    // to fill in, not just an already-populated one. No explicit save/cancel
    // buttons: submitting the keyboard (Return) or tapping away (which resigns
    // focus via the screen's existing dismissKeyboardOnTap()) both commit it.
    // Uses .contextMenu (not onLongPressGesture/LongPressGesture, both of which
    // — even as a .simultaneousGesture — compete with the page's ScrollView
    // drag gesture for the same touch and can still block or stutter scrolling)
    // since these rows span most of the visible screen at the top of the page.
    // .contextMenu's long press is implemented via UIContextMenuInteraction,
    // the same system-level mechanism Mail/Photos/Files use for exactly this
    // "long-press for an action inside scrollable content" pattern, which is
    // built to coexist with scrolling rather than race it for the touch.
    private func editableInfoRow(_ s: StudentDetail, label: String, value: String, field: ProfileField,
                                  keyboard: UIKeyboardType = .default, placeholder: String = "") -> some View {
        Group {
            if editingField == field {
                VStack(alignment: .leading, spacing: 6) {
                    Text(label).font(.bodySmall).foregroundColor(.textMuted)
                    TextField(placeholder.isEmpty ? label : placeholder, text: $editBuffer)
                        .keyboardType(keyboard)
                        .padding(8)
                        .background(Color.navyMid)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .foregroundColor(.textPrimary)
                        .focused($focusedField, equals: field)
                        .onSubmit { saveEditingField(s) }
                        .onChange(of: focusedField) { newValue in
                            if newValue != field && editingField == field { saveEditingField(s) }
                        }
                }
                .padding(.vertical, 8)
                Divider().background(Color.dividerColor)
            } else {
                InfoRow(label: label, value: value.isEmpty ? "—" : value)
                    .contentShape(Rectangle())
                    .contextMenu {
                        Button {
                            beginEditing(s, field: field, value: value)
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                    }
            }
        }
    }

    // Gender has no keyboard to submit or dismiss, so selecting a segment commits
    // immediately rather than waiting for a separate save action.
    private func editableGenderRow(_ s: StudentDetail, value: String) -> some View {
        Group {
            if editingField == .gender {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Gender").font(.bodySmall).foregroundColor(.textMuted)
                    Picker("", selection: $editBuffer) {
                        Text("—").tag("")
                        Text("Male").tag("Male")
                        Text("Female").tag("Female")
                        Text("Other").tag("Other")
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: editBuffer) { _ in saveEditingField(s) }
                }
                .padding(.vertical, 8)
                Divider().background(Color.dividerColor)
            } else {
                InfoRow(label: "Gender", value: value.isEmpty ? "—" : value.capitalized)
                    .contentShape(Rectangle())
                    .contextMenu {
                        Button {
                            beginEditing(s, field: .gender, value: value)
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                    }
            }
        }
    }

    // Dates have no keyboard to submit either — like gender, picking a day
    // commits immediately. Free-text yyyy-MM-dd entry (the old editableInfoRow
    // path) let a mistyped date silently fail whatever the backend does with
    // an unparseable string; a DatePicker can't produce an invalid one.
    private func editableDateRow(_ s: StudentDetail, label: String, value: String, field: ProfileField) -> some View {
        Group {
            if editingField == field {
                VStack(alignment: .leading, spacing: 6) {
                    Text(label).font(.bodySmall).foregroundColor(.textMuted)
                    DatePicker("", selection: Binding(
                        get: { Self.parseYMD(editBuffer) ?? Date() },
                        set: { newDate in
                            editBuffer = Self.formatYMD(newDate)
                            saveEditingField(s)
                        }
                    ), in: ...Date(), displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .labelsHidden()
                        .tint(.amber)
                        .colorScheme(.dark)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, 8)
                Divider().background(Color.dividerColor)
            } else {
                InfoRow(label: label, value: value.isEmpty ? "—" : value)
                    .contentShape(Rectangle())
                    .contextMenu {
                        Button {
                            beginEditing(s, field: field, value: value)
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                    }
            }
        }
    }

    private static func parseYMD(_ s: String) -> Date? {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.date(from: s)
    }

    private static func formatYMD(_ date: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.string(from: date)
    }

    private func saveEditingField(_ s: StudentDetail) {
        guard let field = editingField else { return }
        let trimmed = editBuffer.trimmingCharacters(in: .whitespaces)

        var name        = s.name
        var mobile      = s.mobile
        var email       = s.email ?? ""
        var address     = s.address ?? ""
        var gender      = s.gender ?? ""
        var dateOfBirth = s.dateOfBirth ?? ""
        var joinedAt    = String(s.joinedAt?.prefix(10) ?? "")

        switch field {
        case .name:        name = trimmed
        case .mobile:      mobile = trimmed
        case .email:       email = trimmed
        case .address:     address = trimmed
        case .gender:      gender = trimmed
        case .dateOfBirth: dateOfBirth = trimmed
        case .joinedAt:    joinedAt = trimmed
        }

        guard !name.isEmpty else {
            editingField = nil
            focusedField = nil
            return
        }

        let req = UpdateStudentRequest(
            name: name,
            mobile: mobile.isEmpty ? nil : mobile,
            email: email.isEmpty ? nil : email,
            address: address.isEmpty ? nil : address,
            gender: gender.isEmpty ? nil : gender,
            dateOfBirth: dateOfBirth.isEmpty ? nil : dateOfBirth,
            joinedAt: joinedAt.isEmpty ? nil : joinedAt
        )
        vm.updateStudent(id: s.id, req: req)
        editingField = nil
        focusedField = nil
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
                    clearPaymentMode = "CASH"
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
                .buttonStyle(.plain)
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
                    clearPaymentMode = "CASH"
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
                .buttonStyle(.plain)
            }
        }
    }

    private func actionsSection(_ s: StudentDetail) -> some View {
        VStack(spacing: 12) {
            if s.membershipId != nil {
                manageSection(s)
            }
            historyAndCommunicationSection(s)
            dangerZoneSection(s)

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

    private func manageSection(_ s: StudentDetail) -> some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Manage").font(.headlineSmall).foregroundColor(.textPrimary)
                Divider().background(Color.dividerColor)

                if s.membershipId != nil {
                    OutlineButton("Change Seat") { showChangeSeat = true }
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
            }
        }
    }

    private func historyAndCommunicationSection(_ s: StudentDetail) -> some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("History & Communication").font(.headlineSmall).foregroundColor(.textPrimary)
                Divider().background(Color.dividerColor)

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
                .buttonStyle(.plain)

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
                .buttonStyle(.plain)
            }
        }
    }

    private func dangerZoneSection(_ s: StudentDetail) -> some View {
        AppCard(accentColor: .redAlert) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Danger Zone").font(.headlineSmall).foregroundColor(.redAlert)
                Divider().background(Color.dividerColor)

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
                    .buttonStyle(.plain)
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
                .buttonStyle(.plain)
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

    private func clearAmountSheet(_ s: StudentDetail) -> some View {
        let outstanding = clearKind == .dues ? (s.duesAmount ?? 0) : (s.pendingAmount ?? 0)
        return NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 16) {
                    Text("Outstanding: ₹\(String(format: "%.0f", outstanding)). Any amount not cleared stays on record as a pending balance.")
                        .font(.bodySmall).foregroundColor(.textSub)

                    AppTextField(label: "Amount to Clear (₹)", text: $clearAmountText,
                                placeholder: "0", keyboardType: .decimalPad,
                                leadingIcon: "indianrupeesign")

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Payment Mode").font(.labelMedium).foregroundColor(.textSub)
                        HStack(spacing: 10) {
                            clearModeButton("💵 Cash", value: "CASH", color: .amber)
                            clearModeButton("📱 UPI (QR)", value: "UPI-QR", color: .blueSoft)
                        }
                    }

                    Spacer()

                    PrimaryButton("Clear") {
                        guard let amount = Double(clearAmountText), amount > 0 else { return }
                        if clearKind == .dues {
                            if let membershipId = s.membershipId {
                                vm.clearDues(membershipId: membershipId, amountCleared: amount, userId: s.id, paymentMode: clearPaymentMode)
                            }
                        } else {
                            vm.clearPendingFees(userId: s.id, amountCleared: amount, paymentMode: clearPaymentMode)
                        }
                        clearKind = nil
                    }
                    .disabled((Double(clearAmountText) ?? 0) <= 0)
                }
                .padding(16)
            }
            .dismissKeyboardOnTap()
            .navigationTitle(clearKind == .dues ? "Clear Dues" : "Clear Pending Fees")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { clearKind = nil }.foregroundColor(.amber)
                }
            }
        }
    }

    private func clearModeButton(_ title: String, value: String, color: Color) -> some View {
        let selected = clearPaymentMode == value
        return Button { clearPaymentMode = value } label: {
            Text(title)
                .font(.labelSmall)
                .foregroundColor(selected ? color : .textSub)
                .frame(maxWidth: .infinity).padding(.vertical, 8)
                .background(selected ? color.opacity(0.15) : Color.cardBg)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(selected ? color.opacity(0.4) : Color.cardBorder))
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
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
            .dismissKeyboardOnTap()
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
        .buttonStyle(.plain)
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
            .dismissKeyboardOnTap()
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
