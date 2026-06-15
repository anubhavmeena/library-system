import SwiftUI

struct AdminRemindersView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var withinDays   = 7
    @State private var selectedIds  = Set<String>()
    @State private var selectAll    = false

    private let dayOptions = [3, 5, 7]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    controls
                    if vm.isLoading {
                        LoadingView()
                    } else {
                        studentList
                    }
                }
            }
            .navigationTitle("Reminders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadExpiring(withinDays: withinDays) }
    }

    private var controls: some View {
        VStack(spacing: 10) {
            // Day filter
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

    private var studentList: some View {
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
