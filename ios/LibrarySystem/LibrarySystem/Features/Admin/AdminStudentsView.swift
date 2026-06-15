import SwiftUI

struct AdminStudentsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var search       = ""
    @State private var statusFilter = ""
    @State private var membershipFilter = ""
    @State private var page         = 0
    @State private var showDetail   = false
    @State private var selectedId   = ""

    private let pageSize = 20

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
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
        }
        .onAppear { loadStudents() }
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
                }
            }
            .padding(10)
            .background(Color.navyMid.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    filterChip("All", tag: "", binding: $statusFilter)
                    filterChip("Active", tag: "ACTIVE", binding: $statusFilter)
                    filterChip("Inactive", tag: "INACTIVE", binding: $statusFilter)
                    Divider().frame(height: 20).background(Color.cardBorder)
                    filterChip("With Membership", tag: "ACTIVE", binding: $membershipFilter)
                    filterChip("No Membership", tag: "NONE", binding: $membershipFilter)
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
            binding.wrappedValue = selected ? "" : tag
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
    }

    private func studentRow(_ s: StudentSummary) -> some View {
        Button {
            selectedId = s.id
            vm.loadStudentDetail(id: s.id)
            showDetail = true
        } label: {
            AppCard {
                HStack(spacing: 12) {
                    Circle()
                        .fill(Color.navyLight)
                        .frame(width: 44, height: 44)
                        .overlay(
                            Text(String(s.name.prefix(1)))
                                .font(.headlineSmall)
                                .foregroundColor(.amber)
                        )

                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(s.name).font(.labelLarge).foregroundColor(.textPrimary)
                            Spacer()
                            StatusChip(status: s.isActive ? "ACTIVE" : "INACTIVE")
                        }
                        Text(s.mobile).font(.bodySmall).foregroundColor(.textSub)
                        if let plan = s.planName {
                            HStack(spacing: 6) {
                                Text(plan).font(.labelSmall).foregroundColor(.amber)
                                if let seat = s.seatNumber {
                                    Text("· Seat \(seat)").font(.labelSmall).foregroundColor(.textMuted)
                                }
                                if let status = s.membershipStatus {
                                    StatusChip(status: status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func loadStudents() {
        vm.loadStudents(page: page,
                        status: statusFilter.isEmpty ? nil : statusFilter,
                        membershipStatus: membershipFilter.isEmpty ? nil : membershipFilter,
                        search: search.isEmpty ? nil : search)
    }
}
