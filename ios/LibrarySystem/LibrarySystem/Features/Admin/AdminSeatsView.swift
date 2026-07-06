import SwiftUI

struct AdminSeatsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var selectedShift = "FULL_DAY"
    @State private var selectedDate  = Date()
    @State private var showDatePicker = false
    @State private var tappedSeat:   SeatInfoItem?
    @State private var showSeatHistory = false

    private let shifts = ["MORNING", "EVENING", "FULL_DAY"]

    // Mirrors the web admin seat map (frontend/src/pages/admin/AdminSeatsPage.jsx)
    // exactly: a fixed A-D row layout, each row split into a left block (seats
    // 1-14) and a right block (15-28) with an aisle between them, and each
    // block arranged as two stacked sub-rows of 7 (back-to-back desk pairs) —
    // not one long row of 14, which is what made the previous layout too wide
    // to fit a phone screen. Two seats are physically blocked (pillars) on
    // both clients regardless of what the backend returns for them.
    private let seatMapRowLabels = ["A", "B", "C", "D"]
    private let inactiveSeatNumbers: Set<String> = ["B8", "B18"]
    private let leftTopSeats     = [13, 11, 9, 7, 5, 3, 1]
    private let leftBottomSeats  = [14, 12, 10, 8, 6, 4, 2]
    private let rightTopSeats    = [15, 17, 19, 21, 23, 25, 27]
    private let rightBottomSeats = [16, 18, 20, 22, 24, 26, 28]

    private var dateString: String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: selectedDate)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    controls
                    if vm.isLoading {
                        LoadingView()
                    } else if let map = vm.seatMap {
                        seatContent(map)
                    }
                }
            }
            .navigationTitle("Seat Map")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .sheet(isPresented: $showDatePicker) { datePickerSheet }
            .sheet(item: $tappedSeat, onDismiss: { showSeatHistory = false; vm.seatHistory = [] }) { seat in seatDetailSheet(seat) }
        }
        .onAppear { vm.loadSeatMap(shift: selectedShift, date: dateString) }
    }

    private var controls: some View {
        VStack(spacing: 10) {
            // Shift chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(shifts, id: \.self) { shift in
                        let selected = selectedShift == shift
                        Button {
                            selectedShift = shift
                            vm.loadSeatMap(shift: shift, date: dateString)
                        } label: {
                            Text(shift.replacingOccurrences(of: "_", with: " ").capitalized)
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

            // Date picker button
            Button { showDatePicker = true } label: {
                HStack {
                    Image(systemName: "calendar").foregroundColor(.amber)
                    Text(dateString).font(.labelMedium).foregroundColor(.textPrimary)
                    Image(systemName: "chevron.down").font(.caption).foregroundColor(.textMuted)
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(Color.cardBg)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
            }
        }
        .padding(.vertical, 10)
        .background(Color.navyMid.opacity(0.2))
    }

    private func seatContent(_ map: SeatMapDto) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                // Stats row
                HStack(spacing: 10) {
                    StatCard(label: "Total",     value: "\(map.totalSeats)",    accent: .blueSoft)
                    StatCard(label: "Occupied",  value: "\(map.occupiedSeats)", accent: .redAlert)
                    StatCard(label: "Available", value: "\(map.availableSeats)", accent: .emerald)
                }
                .padding(.horizontal, 16)

                // Seat grid (admin read-only version — tap to see occupant).
                // Kept in its own horizontal scroll as a safety net for
                // smaller devices/Dynamic Type, but the compact cell size
                // below is sized to fit a full row on one screen normally.
                ScrollView(.horizontal, showsIndicators: false) {
                    adminSeatGrid(map)
                        .padding(16)
                }
            }
            .padding(.bottom, 24)
        }
    }

    // Looks up a specific seat number in the row's data; any seat number the
    // backend didn't return (shouldn't normally happen, but matches the web
    // client's own fallback) is treated as available rather than omitted.
    private func seat(row: String, number: Int, in rowSeats: [SeatInfoItem]) -> SeatInfoItem {
        let seatNumber = "\(row)\(number)"
        return rowSeats.first(where: { $0.seatNumber == seatNumber })
            ?? SeatInfoItem(seatNumber: seatNumber, isOccupied: false, studentName: nil,
                            studentMobile: nil, shift: nil, membershipEnd: nil)
    }

    private func adminSeatGrid(_ map: SeatMapDto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            // Legend
            HStack(spacing: 16) {
                legendItem(color: .cardBg, border: .cardBorder, label: "Available")
                legendItem(color: .redFaint, border: .redAlert, label: "Occupied")
            }

            ForEach(seatMapRowLabels, id: \.self) { row in
                let rowSeats = map.seatsByRow[row] ?? []
                HStack(alignment: .top, spacing: 6) {
                    Text(row).font(.labelSmall).foregroundColor(.textMuted)
                        .frame(width: 14).padding(.top, 4)

                    VStack(spacing: 3) {
                        HStack(spacing: 3) {
                            ForEach(leftTopSeats, id: \.self) { n in seatCell(row: row, number: n, rowSeats: rowSeats) }
                        }
                        HStack(spacing: 3) {
                            ForEach(leftBottomSeats, id: \.self) { n in seatCell(row: row, number: n, rowSeats: rowSeats) }
                        }
                    }

                    Rectangle().fill(Color.clear).frame(width: 14)

                    VStack(spacing: 3) {
                        HStack(spacing: 3) {
                            ForEach(rightTopSeats, id: \.self) { n in seatCell(row: row, number: n, rowSeats: rowSeats) }
                        }
                        HStack(spacing: 3) {
                            ForEach(rightBottomSeats, id: \.self) { n in seatCell(row: row, number: n, rowSeats: rowSeats) }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func seatCell(row: String, number: Int, rowSeats: [SeatInfoItem]) -> some View {
        let seatNumber = "\(row)\(number)"
        if inactiveSeatNumbers.contains(seatNumber) {
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.navyMid.opacity(0.5))
                .frame(width: 22, height: 22)
        } else {
            adminSeatCell(seat(row: row, number: number, in: rowSeats))
        }
    }

    private func adminSeatCell(_ seat: SeatInfoItem) -> some View {
        Button { if seat.isOccupied { tappedSeat = seat } } label: {
            Text(String(seat.seatNumber.dropFirst()))
                .font(.system(size: 7, weight: .medium))
                .foregroundColor(seat.isOccupied ? .redAlert : .textSub)
                .frame(width: 22, height: 22)
                .background(seat.isOccupied ? Color.redFaint : Color.cardBg)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(
                    seat.isOccupied ? Color.redAlert : Color.cardBorder, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
        .disabled(!seat.isOccupied)
    }

    private func legendItem(color: Color, border: Color, label: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 3).fill(color)
                .overlay(RoundedRectangle(cornerRadius: 3).stroke(border, lineWidth: 1))
                .frame(width: 14, height: 14)
            Text(label).font(.caption).foregroundColor(.textMuted)
        }
    }

    private var datePickerSheet: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                DatePicker("Date", selection: $selectedDate, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(.amber)
                    .padding()
                    .colorScheme(.dark)
            }
            .navigationTitle("Select Date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Apply") {
                        showDatePicker = false
                        vm.loadSeatMap(shift: selectedShift, date: dateString)
                    }
                    .foregroundColor(.amber)
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showDatePicker = false }.foregroundColor(.amber)
                }
            }
        }
    }

    private func seatDetailSheet(_ seat: SeatInfoItem) -> some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        AppCard(accentColor: .amber) {
                            VStack(spacing: 12) {
                                Text("Seat \(seat.seatNumber)").font(.headlineLarge).foregroundColor(.textPrimary)
                                Divider().background(Color.dividerColor)
                                if let name = seat.studentName { InfoRow(label: "Student",  value: name) }
                                if let mob  = seat.studentMobile { InfoRow(label: "Mobile",  value: mob) }
                                if let end  = seat.membershipEnd { InfoRow(label: "Expires", value: end) }
                                if let sh   = seat.shift { InfoRow(label: "Shift", value: sh.capitalized) }
                            }
                        }

                        Button {
                            showSeatHistory.toggle()
                            if showSeatHistory { vm.loadSeatHistory(seatNumber: seat.seatNumber) }
                        } label: {
                            HStack {
                                Text(showSeatHistory ? "Hide Seat History" : "View Seat History")
                                Spacer()
                                Image(systemName: showSeatHistory ? "chevron.up" : "chevron.down")
                            }
                            .font(.labelMedium).foregroundColor(.blueSoft)
                            .padding(12)
                            .background(Color.blueFaint)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.blueSoft.opacity(0.4)))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }

                        if showSeatHistory {
                            if vm.seatHistory.isEmpty {
                                Text("No booking history for this seat").font(.bodySmall).foregroundColor(.textMuted)
                                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                            } else {
                                ForEach(vm.seatHistory) { h in
                                    AppCard {
                                        VStack(alignment: .leading, spacing: 6) {
                                            HStack {
                                                Text(h.studentName ?? "—").font(.labelLarge).foregroundColor(.textPrimary)
                                                Spacer()
                                                if let status = h.status { StatusChip(status: status) }
                                            }
                                            if let mob = h.studentMobile {
                                                Text(mob).font(.bodySmall).foregroundColor(.textSub)
                                            }
                                            Text("\(h.startDate ?? "—") → \(h.endDate ?? "—") · \((h.shift ?? "").capitalized)")
                                                .font(.bodySmall).foregroundColor(.textMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(24)
                }
            }
            .navigationTitle("Seat Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { tappedSeat = nil }.foregroundColor(.amber)
                }
            }
        }
    }
}

extension SeatInfoItem: Identifiable {
    var id: String { seatNumber }
}
