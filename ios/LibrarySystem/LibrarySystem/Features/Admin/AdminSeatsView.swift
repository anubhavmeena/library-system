import SwiftUI

struct AdminSeatsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var selectedShift = "FULL_DAY"
    @State private var selectedDate  = Date()
    @State private var showDatePicker = false
    @State private var tappedSeat:   SeatInfoItem?

    private let shifts = ["MORNING", "EVENING", "FULL_DAY"]

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
            .sheet(item: $tappedSeat) { seat in seatDetailSheet(seat) }
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

                // Seat grid (admin read-only version — tap to see occupant)
                adminSeatGrid(map)
                    .padding(16)
            }
            .padding(.bottom, 24)
        }
    }

    private func adminSeatGrid(_ map: SeatMapDto) -> some View {
        let rows: [(label: String, count: Int)] = [("A",28),("B",28),("C",28),("D",26)]

        return VStack(alignment: .leading, spacing: 8) {
            // Legend
            HStack(spacing: 16) {
                legendItem(color: .cardBg, border: .cardBorder, label: "Available")
                legendItem(color: .redFaint, border: .redAlert, label: "Occupied")
            }

            ForEach(rows, id: \.label) { row in
                let rowSeats = map.seatsByRow[row.label] ?? []
                let half = (row.count + 1) / 2
                HStack(alignment: .top, spacing: 0) {
                    Text(row.label).font(.labelSmall).foregroundColor(.textMuted).frame(width: 18).padding(.top, 4)
                    HStack(spacing: 4) {
                        ForEach(0..<half, id: \.self) { i in
                            if i < rowSeats.count { adminSeatCell(rowSeats[i]) }
                        }
                    }
                    Rectangle().fill(Color.clear).frame(width: 20)
                    HStack(spacing: 4) {
                        ForEach(half..<row.count, id: \.self) { i in
                            if i < rowSeats.count { adminSeatCell(rowSeats[i]) }
                        }
                    }
                }
            }
        }
    }

    private func adminSeatCell(_ seat: SeatInfoItem) -> some View {
        Button { if seat.isOccupied { tappedSeat = seat } } label: {
            Text(String(seat.seatNumber.dropFirst()))
                .font(.system(size: 8, weight: .medium))
                .foregroundColor(seat.isOccupied ? .redAlert : .textSub)
                .frame(width: 28, height: 28)
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
                VStack(spacing: 0) {
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
                    .padding(24)
                    Spacer()
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
