import SwiftUI

struct AdminStudentsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var search           = ""
    @State private var membershipFilter = ""
    @State private var page             = 0
    @State private var showDetail       = false
    @State private var selectedId       = ""
    @State private var searchDebounceTask: Task<Void, Never>?

    private let pageSize = 20
    private let baseURL = "https://targetzone.co.in"

    // Mirrors the web admin students page (frontend/src/pages/admin/AdminStudentsPage.jsx)
    // exactly — a single membership-status filter, not the previous isActive-based
    // "Active/Inactive" + "With/No Membership" chips, which sent a `status` query
    // param the backend doesn't even accept and a `membershipStatus` of "ACTIVE"/
    // "NONE" that never matches a real displayStatus, so those filters were a
    // silent no-op.
    private let membershipFilters: [(String, String)] = [
        ("", "All"), ("NEW", "New"), ("PAID", "Paid"), ("PENDING", "Pending"),
        ("GRACE", "Grace"), ("EXPIRED", "Expired"), ("RELEASED", "Released"),
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    header
                    searchAndFilters
                    studentList
                }
            }
            .navigationTitle("Students")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .navigationDestination(isPresented: $showDetail) {
                AdminStudentDetailView(vm: vm, studentId: selectedId)
            }
            .dismissKeyboardOnTap()
        }
        .onAppear { loadStudents() }
    }

    private var header: some View {
        HStack {
            Text("\(vm.students.count) of \(vm.studentsTotal) students")
                .font(.bodySmall).foregroundColor(.textSub)
            Spacer()
            Button { loadStudents() } label: {
                Text("↻ Refresh")
                    .font(.labelSmall).foregroundColor(.textSub)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Color.cardBg)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Color.cardBorder))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.top, 10)
    }

    private var searchAndFilters: some View {
        VStack(spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(.textMuted)
                TextField("Search by name or mobile...", text: $search)
                    .foregroundColor(.textPrimary)
                    .font(.bodyMedium)
                    .onSubmit { page = 0; loadStudents() }
                if !search.isEmpty {
                    Button { search = ""; page = 0; loadStudents() } label: {
                        Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(10)
            .background(Color.navyMid.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal, 16)
            .onChange(of: search) { _ in
                searchDebounceTask?.cancel()
                searchDebounceTask = Task {
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    guard !Task.isCancelled else { return }
                    page = 0
                    loadStudents()
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(membershipFilters, id: \.0) { tag, label in
                        filterChip(label, tag: tag, binding: $membershipFilter)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.vertical, 10)
        .background(Color.navyMid.opacity(0.2))
    }

    private func filterChip(_ label: String, tag: String, binding: Binding<String>) -> some View {
        let selected = binding.wrappedValue == tag
        return Button {
            binding.wrappedValue = tag
            page = 0; loadStudents()
        } label: {
            Text(label)
                .font(.labelSmall)
                .foregroundColor(selected ? .navyDeep : .textSub)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(selected ? Color.amber : Color.cardBg)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? Color.amber : Color.cardBorder))
        }
        .buttonStyle(.plain)
    }

    private var studentList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if vm.isLoading {
                    LoadingView().frame(height: 200)
                } else if vm.students.isEmpty {
                    Text("No students found").foregroundColor(.textMuted)
                        .frame(maxWidth: .infinity).padding(.top, 60)
                } else {
                    ForEach(vm.students) { student in
                        studentRow(student)
                    }
                    // Pagination
                    HStack(spacing: 20) {
                        if page > 0 {
                            OutlineButton("Previous") { page -= 1; loadStudents() }
                        }
                        if vm.students.count == pageSize {
                            OutlineButton("Next") { page += 1; loadStudents() }
                        }
                    }
                    .padding(.top, 8)
                }
            }
            .padding(16)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    // Mirrors the web row's status badge colors exactly (STATUS_BADGE_CLASSES
    // in AdminStudentsPage.jsx) — distinct from the shared StatusChip component's
    // coarser ACTIVE/PENDING/EXPIRED mapping used elsewhere in the app, since
    // displayStatus has six distinct values here (NEW/PAID/PENDING/GRACE/EXPIRED/RELEASED).
    private func statusColor(_ status: String?) -> Color {
        switch status {
        case "NEW":      return .blueSoft
        case "PAID":     return .emerald
        case "PENDING":  return .yellowWarn
        case "GRACE":    return .orange
        case "EXPIRED":  return .redAlert
        case "RELEASED": return .redDeep
        default:         return .textMuted
        }
    }

    private func parseDate(_ s: String?) -> Date? {
        guard let s else { return nil }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        return f.date(from: s)
    }

    private func studentRow(_ s: StudentSummary) -> some View {
        Button {
            selectedId = s.id
            vm.loadStudentDetail(id: s.id)
            showDetail = true
        } label: {
            AppCard {
                HStack(alignment: .top, spacing: 12) {
                    avatar(s)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(s.name).font(.labelLarge).foregroundColor(.textPrimary)
                            Spacer()
                            if let status = s.displayStatus {
                                Text(status)
                                    .font(.labelSmall).foregroundColor(statusColor(status))
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(statusColor(status).opacity(0.15))
                                    .overlay(Capsule().stroke(statusColor(status).opacity(0.4)))
                                    .clipShape(Capsule())
                            }
                        }
                        if let joined = s.joinedAt {
                            let joinedShort = String(joined.prefix(10))
                            Text("Joined \(joinedShort)").font(.caption).foregroundColor(.textMuted)
                        }
                        Text(s.mobile + (s.email.map { " · \($0)" } ?? ""))
                            .font(.bodySmall).foregroundColor(.textSub)

                        if let seat = s.seatNumber {
                            Text("Seat \(seat) · \(shiftLabel(s.shift))")
                                .font(.labelSmall).foregroundColor(.amber)
                        }

                        membershipLine(s)

                        HStack(spacing: 6) {
                            if let mode = s.paymentMode {
                                Text(mode == "CASH" ? "💵 Cash" : "💳 Online")
                                    .font(.caption).foregroundColor(.indigo)
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(Color.indigoFaint)
                                    .clipShape(Capsule())
                            }
                            if let pending = s.pendingAmount, pending > 0 {
                                Text("Pending ₹\(String(format: "%.0f", pending))")
                                    .font(.caption).foregroundColor(.redAlert)
                            }
                            if let dues = s.duesAmount, dues > 0 {
                                Text("Dues ₹\(String(format: "%.0f", dues))")
                                    .font(.caption).foregroundColor(.redAlert)
                            }
                        }
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func avatar(_ s: StudentSummary) -> some View {
        if let url = s.photoUrl, let imageURL = URL(string: url.hasPrefix("http") ? url : baseURL + url) {
            AsyncImage(url: imageURL) { phase in
                if case .success(let img) = phase {
                    img.resizable().scaledToFill()
                } else {
                    avatarPlaceholder(s)
                }
            }
            .frame(width: 44, height: 44)
            .clipShape(Circle())
        } else {
            avatarPlaceholder(s)
        }
    }

    private func avatarPlaceholder(_ s: StudentSummary) -> some View {
        Circle()
            .fill(Color.navyLight)
            .frame(width: 44, height: 44)
            .overlay(
                Text(String(s.name.prefix(1)))
                    .font(.headlineSmall)
                    .foregroundColor(.amber)
            )
    }

    private func shiftLabel(_ shift: String?) -> String {
        switch shift {
        case "MORNING":  return "Morning"
        case "EVENING":  return "Evening"
        case "FULL_DAY": return "Full Day"
        default:         return "—"
        }
    }

    // Mirrors AdminStudentsPage.jsx's membership column exactly: GRACE shows
    // days-overdue in red (recomputed locally, since the backend's daysRemaining
    // isn't reliable once a membership is past its end date), everything else
    // shows the standard 3-tier days-left coloring.
    @ViewBuilder
    private func membershipLine(_ s: StudentSummary) -> some View {
        if s.membershipStatus == "GRACE", let end = parseDate(s.membershipEnd) {
            let overdue = max(0, Int(ceil(Date().timeIntervalSince(end) / 86400)))
            VStack(alignment: .leading, spacing: 1) {
                Text("Expires \(s.membershipEnd ?? "—")").font(.caption).foregroundColor(.textSub)
                Text("\(overdue)d overdue — \(s.displayStatus == "EXPIRED" ? "expired" : "grace")")
                    .font(.caption).fontWeight(.semibold).foregroundColor(.redAlert)
            }
        } else if let end = s.membershipEnd {
            let color: Color = s.daysRemaining <= 3 ? .redAlert : s.daysRemaining <= 7 ? .amber : .emerald
            VStack(alignment: .leading, spacing: 1) {
                Text("Expires \(end)").font(.caption).foregroundColor(.textSub)
                Text("\(s.daysRemaining) days left").font(.caption).fontWeight(.semibold).foregroundColor(color)
            }
        } else if s.displayStatus == "EXPIRED" {
            Text("Expired").font(.caption).fontWeight(.semibold).foregroundColor(.redAlert)
        } else {
            Text("No plan").font(.caption).foregroundColor(.textMuted)
        }
    }

    private func loadStudents() {
        vm.loadStudents(page: page,
                        membershipStatus: membershipFilter.isEmpty ? nil : membershipFilter,
                        search: search.isEmpty ? nil : search)
    }
}
