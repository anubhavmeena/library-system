import SwiftUI

struct AdminRemindersView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var activeTab    = 0   // 0 = Expiring, 1 = Pending Fees, 2 = Grace Dues, 3 = No Seat
    @State private var withinDays   = 7
    @State private var selectedIds  = Set<String>()
    @State private var selectAll    = false

    // Shared "clear amount" prompt — used by both Pending Fees and Grace Dues rows.
    @State private var clearTarget:      StudentDetail?
    @State private var clearAmountText   = ""
    @State private var clearIsGraceDues  = false

    private let dayOptions = [3, 5, 7]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    tabBar
                    if activeTab == 0 {
                        expiringContent
                    } else if activeTab == 1 {
                        pendingFeesContent
                    } else if activeTab == 2 {
                        graceDuesContent
                    } else {
                        orphanedSeatsContent
                    }
                }
            }
            .navigationTitle("Reminders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear {
            vm.loadExpiring(withinDays: withinDays)
            vm.loadPendingFeeStudents()
            vm.loadGraceDuesStudents()
            vm.loadOrphanedSeatStudents()
        }
        .alert(clearIsGraceDues ? "Clear Dues" : "Clear Pending Fees",
               isPresented: Binding(get: { clearTarget != nil }, set: { if !$0 { clearTarget = nil } })) {
            TextField("Amount", text: $clearAmountText).keyboardType(.decimalPad)
            Button("Cancel", role: .cancel) { clearTarget = nil }
            Button("Clear") {
                guard let target = clearTarget, let amount = Double(clearAmountText), amount > 0 else { return }
                if clearIsGraceDues {
                    if let membershipId = target.membershipId {
                        vm.clearDues(membershipId: membershipId, amountCleared: amount, userId: target.id)
                    }
                } else {
                    vm.clearPendingFees(userId: target.id, amountCleared: amount)
                }
                clearTarget = nil
            }
        } message: {
            if let target = clearTarget {
                let outstanding = clearIsGraceDues ? (target.duesAmount ?? 0) : (target.pendingAmount ?? 0)
                Text("Record a payment for \(target.name). Outstanding: ₹\(String(format: "%.0f", outstanding)). Any amount not cleared stays on record as a pending balance.")
            }
        }
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            tabButton(title: "Expiring", index: 0)
            tabButton(title: "Pending Fees", index: 1)
            tabButton(title: "Grace Dues", index: 2)
            tabButton(title: "No Seat", index: 3)
        }
        .background(Color.navyMid.opacity(0.3))
    }

    private func tabButton(title: String, index: Int) -> some View {
        Button {
            activeTab = index
            selectedIds.removeAll()
            selectAll = false
        } label: {
            VStack(spacing: 4) {
                Text(title).font(.labelMedium)
                    .foregroundColor(activeTab == index ? .amber : .textMuted)
                    .frame(maxWidth: .infinity)
                Rectangle().frame(height: 2)
                    .foregroundColor(activeTab == index ? .amber : .clear)
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Expiring Tab

    private var expiringContent: some View {
        VStack(spacing: 0) {
            expiringControls
            if vm.isLoading {
                LoadingView()
            } else {
                expiringList
            }
        }
    }

    private var expiringControls: some View {
        VStack(spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(dayOptions, id: \.self) { days in
                        let selected = withinDays == days
                        Button {
                            withinDays = days
                            selectedIds.removeAll()
                            vm.loadExpiring(withinDays: days)
                        } label: {
                            Text("Within \(days) days")
                                .font(.labelMedium)
                                .foregroundColor(selected ? .navyDeep : .textSub)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .background(selected ? Color.amber : Color.cardBg)
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(selected ? Color.amber : Color.cardBorder))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }

            HStack {
                Toggle("Select All", isOn: $selectAll)
                    .toggleStyle(CheckboxToggleStyle())
                    .foregroundColor(.textSub)
                    .onChange(of: selectAll) { val in
                        selectedIds = val ? Set(vm.expiring.map(\.id)) : []
                    }
                Spacer()
                PrimaryButton(selectedIds.isEmpty ? "Send to All" : "Send (\(selectedIds.count))") {
                    let ids = selectedIds.isEmpty ? vm.expiring.map(\.id) : Array(selectedIds)
                    vm.sendReminders(userIds: ids)
                }
                .frame(width: 160)
            }
            .padding(.horizontal, 16)

            if let msg = vm.successMsg {
                HStack { Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                    Text(msg).font(.bodySmall).foregroundColor(.textPrimary) }
                    .padding(.horizontal, 16)
            }
        }
        .padding(.vertical, 10)
        .background(Color.navyMid.opacity(0.2))
    }

    private var expiringList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if vm.expiring.isEmpty {
                    Text("No expiring memberships").foregroundColor(.textMuted)
                        .frame(maxWidth: .infinity).padding(.top, 60)
                } else {
                    ForEach(vm.expiring) { student in
                        AppCard {
                            HStack(spacing: 12) {
                                Image(systemName: selectedIds.contains(student.id) ? "checkmark.square.fill" : "square")
                                    .foregroundColor(selectedIds.contains(student.id) ? .amber : .textMuted)
                                    .font(.title3)
                                    .onTapGesture {
                                        if selectedIds.contains(student.id) { selectedIds.remove(student.id) }
                                        else { selectedIds.insert(student.id) }
                                    }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(student.name).font(.labelLarge).foregroundColor(.textPrimary)
                                    Text(student.mobile).font(.bodySmall).foregroundColor(.textSub)
                                    HStack(spacing: 8) {
                                        Label("\(student.daysLeft) days left", systemImage: "clock")
                                            .font(.labelSmall)
                                            .foregroundColor(student.daysLeft <= 3 ? .redAlert : .amber)
                                        if let seat = student.seatNumber {
                                            Text("· Seat \(seat)").font(.labelSmall).foregroundColor(.textMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    // MARK: - Pending Fees Tab

    private var pendingFeesContent: some View {
        VStack(spacing: 0) {
            pendingFeesControls
            if vm.isLoading {
                LoadingView()
            } else {
                pendingFeesList
            }
        }
    }

    private var pendingFeesControls: some View {
        HStack {
            Toggle("Select All", isOn: $selectAll)
                .toggleStyle(CheckboxToggleStyle())
                .foregroundColor(.textSub)
                .onChange(of: selectAll) { val in
                    selectedIds = val ? Set(vm.pendingFeeStudents.map(\.id)) : []
                }
            Spacer()
            PrimaryButton(selectedIds.isEmpty ? "Remind All" : "Remind (\(selectedIds.count))") {
                let ids = selectedIds.isEmpty ? vm.pendingFeeStudents.map(\.id) : Array(selectedIds)
                vm.sendPendingFeeReminders(userIds: ids)
            }
            .frame(width: 160)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(Color.navyMid.opacity(0.2))
    }

    private var pendingFeesList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if vm.pendingFeeStudents.isEmpty {
                    Text("No students with pending fees").foregroundColor(.textMuted)
                        .frame(maxWidth: .infinity).padding(.top, 60)
                } else {
                    ForEach(vm.pendingFeeStudents) { student in
                        AppCard {
                            HStack(spacing: 12) {
                                Image(systemName: selectedIds.contains(student.id) ? "checkmark.square.fill" : "square")
                                    .foregroundColor(selectedIds.contains(student.id) ? .amber : .textMuted)
                                    .font(.title3)
                                    .onTapGesture {
                                        if selectedIds.contains(student.id) { selectedIds.remove(student.id) }
                                        else { selectedIds.insert(student.id) }
                                    }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(student.name).font(.labelLarge).foregroundColor(.textPrimary)
                                    Text(student.mobile).font(.bodySmall).foregroundColor(.textSub)
                                    HStack(spacing: 8) {
                                        Label("₹\(String(format: "%.0f", student.pendingAmount ?? 0)) pending",
                                              systemImage: "exclamationmark.circle")
                                            .font(.labelSmall).foregroundColor(.redAlert)
                                        if let seat = student.seatNumber {
                                            Text("· Seat \(seat)").font(.labelSmall).foregroundColor(.textMuted)
                                        }
                                    }
                                }

                                Spacer()

                                Button {
                                    clearIsGraceDues = false
                                    clearAmountText = String(format: "%.0f", student.pendingAmount ?? 0)
                                    clearTarget = student
                                } label: {
                                    Text("Clear")
                                        .font(.labelSmall).foregroundColor(.emerald)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .background(Color.emeraldFaint)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    // MARK: - Grace Dues Tab

    private var graceDuesContent: some View {
        VStack(spacing: 0) {
            graceDuesControls
            if vm.isLoading {
                LoadingView()
            } else {
                graceDuesList
            }
        }
    }

    private var graceDuesControls: some View {
        HStack {
            Toggle("Select All", isOn: $selectAll)
                .toggleStyle(CheckboxToggleStyle())
                .foregroundColor(.textSub)
                .onChange(of: selectAll) { val in
                    selectedIds = val ? Set(vm.graceDuesStudents.map(\.id)) : []
                }
            Spacer()
            PrimaryButton(selectedIds.isEmpty ? "Remind All" : "Remind (\(selectedIds.count))") {
                let ids = selectedIds.isEmpty ? vm.graceDuesStudents.map(\.id) : Array(selectedIds)
                vm.sendGraceDuesReminders(userIds: ids)
            }
            .frame(width: 160)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(Color.navyMid.opacity(0.2))
    }

    private func daysOverdue(_ endDate: String?) -> Int {
        guard let endDate else { return 0 }
        let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"
        guard let end = fmt.date(from: endDate) else { return 0 }
        let days = Calendar.current.dateComponents([.day], from: end, to: Date()).day ?? 0
        return max(0, days)
    }

    private var graceDuesList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if vm.graceDuesStudents.isEmpty {
                    Text("No dues to clear").foregroundColor(.textMuted)
                        .frame(maxWidth: .infinity).padding(.top, 60)
                } else {
                    ForEach(vm.graceDuesStudents) { student in
                        AppCard {
                            HStack(spacing: 12) {
                                Image(systemName: selectedIds.contains(student.id) ? "checkmark.square.fill" : "square")
                                    .foregroundColor(selectedIds.contains(student.id) ? .amber : .textMuted)
                                    .font(.title3)
                                    .onTapGesture {
                                        if selectedIds.contains(student.id) { selectedIds.remove(student.id) }
                                        else { selectedIds.insert(student.id) }
                                    }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(student.name).font(.labelLarge).foregroundColor(.textPrimary)
                                    Text(student.mobile).font(.bodySmall).foregroundColor(.textSub)
                                    HStack(spacing: 8) {
                                        Label("₹\(String(format: "%.0f", student.duesAmount ?? 0)) dues",
                                              systemImage: "hourglass")
                                            .font(.labelSmall).foregroundColor(.redAlert)
                                        Text("· \(daysOverdue(student.membershipEnd))d overdue")
                                            .font(.labelSmall).foregroundColor(.amber)
                                        if let seat = student.seatNumber {
                                            Text("· Seat \(seat)").font(.labelSmall).foregroundColor(.textMuted)
                                        }
                                    }
                                }

                                Spacer()

                                Button {
                                    clearIsGraceDues = true
                                    clearAmountText = String(format: "%.0f", student.duesAmount ?? 0)
                                    clearTarget = student
                                } label: {
                                    Text("Clear")
                                        .font(.labelSmall).foregroundColor(.emerald)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .background(Color.emeraldFaint)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    // MARK: - No Seat Tab

    private var orphanedSeatsContent: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if vm.orphanedSeatStudents.isEmpty {
                    Text("Everyone has a seat").foregroundColor(.textMuted)
                        .frame(maxWidth: .infinity).padding(.top, 60)
                } else {
                    ForEach(vm.orphanedSeatStudents) { student in
                        NavigationLink {
                            AdminStudentDetailView(vm: vm, studentId: student.id)
                        } label: {
                            AppCard {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(student.name).font(.labelLarge).foregroundColor(.textPrimary)
                                        Text(student.mobile).font(.bodySmall).foregroundColor(.textSub)
                                        if let plan = student.planName {
                                            Text(plan).font(.labelSmall).foregroundColor(.amber)
                                        }
                                        if let end = student.membershipEnd {
                                            Text("Ends \(end)").font(.labelSmall).foregroundColor(.textMuted)
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").foregroundColor(.textMuted)
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }
}

struct CheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 8) {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .foregroundColor(configuration.isOn ? .amber : .textMuted)
                configuration.label
            }
        }
        .buttonStyle(.plain)
    }
}
