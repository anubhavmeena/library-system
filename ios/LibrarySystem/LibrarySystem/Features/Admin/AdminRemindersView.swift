import SwiftUI

struct AdminRemindersView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var activeTab    = 0   // 0 = Expiring, 1 = Pending Fees
    @State private var withinDays   = 7
    @State private var selectedIds  = Set<String>()
    @State private var selectAll    = false

    private let dayOptions = [3, 5, 7]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    tabBar
                    if activeTab == 0 {
                        expiringContent
                    } else {
                        pendingFeesContent
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
        }
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            tabButton(title: "Expiring", index: 0)
            tabButton(title: "Pending Fees", index: 1)
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
                                    vm.clearPendingFees(userId: student.id)
                                } label: {
                                    Text("Clear")
                                        .font(.labelSmall).foregroundColor(.emerald)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .background(Color.emeraldFaint)
                                        .clipShape(Capsule())
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
    }
}
