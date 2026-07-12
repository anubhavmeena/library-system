import SwiftUI

struct AdminSeatsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var selectedShift = "FULL_DAY"
    @State private var selectedDate  = Date()
    @State private var showDatePicker = false
    @State private var tappedSeat:   SeatInfoItem?
    @State private var showSeatHistory = false
    @State private var viewMode: SeatViewMode = .standard
    @State private var showStudentDetail = false
    @State private var tappedStudentId = ""

    private enum SeatViewMode { case standard, expiry }

    private let shifts = ["MORNING", "EVENING", "FULL_DAY"]

    // Mirrors the Android seat map (SeatGrid.kt) exactly: a fixed A-D row
    // layout, each row split into a left block (seats 1-14) and a right block
    // (15-28) with an aisle between them, each block arranged as two stacked
    // sub-rows of 7 (back-to-back desk pairs). The grid has no vertical scroll
    // of its own — all four rows just lay out top to bottom at a fixed,
    // readable cell size — but the row content is wider than the screen, so
    // the whole thing (entry label, rows, footer labels) sits inside one
    // horizontally-scrolling container, exactly like Android's
    // Column.horizontalScroll(). Two seats are physically blocked (pillars)
    // on both clients regardless of what the backend returns for them.
    private let seatMapRowLabels = ["A", "B", "C", "D"]
    private let inactiveSeatNumbers: Set<String> = ["B8", "B18"]
    private let leftTopSeats     = [13, 11, 9, 7, 5, 3, 1]
    private let leftBottomSeats  = [14, 12, 10, 8, 6, 4, 2]
    private let rightTopSeats    = [15, 17, 19, 21, 23, 25, 27]
    private let rightBottomSeats = [16, 18, 20, 22, 24, 26, 28]

    // Matches Android's SEAT_SIZE/SEAT_GAP/AISLE_W/LABEL_W (in dp, used here
    // 1:1 as points).
    private let seatSize: CGFloat = 28
    private let seatGap: CGFloat = 4
    private let aisleWidth: CGFloat = 28
    private let rowLabelWidth: CGFloat = 18
    private var sectionWidth: CGFloat { 7 * seatSize + 6 * seatGap }
    private var sectionHeight: CGFloat { seatSize * 2 + 6 }

    private var dateString: String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: selectedDate)
    }

    // Negative = overdue (membership in GRACE, seat held but past its endDate),
    // matching the web admin seat map's expiry-view semantics exactly.
    private func daysToExpiry(_ membershipEnd: String?) -> Int? {
        guard let membershipEnd else { return nil }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let end = f.date(from: membershipEnd) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: selectedDate, to: end).day ?? 0
        return days
    }

    private func expiryColor(_ days: Int) -> Color {
        if days < 0   { return .redDeep }
        if days <= 3  { return .redAlert }
        if days <= 7  { return .orange }
        if days <= 15 { return .yellowWarn }
        return .emerald
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
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }

            // Date picker + Refresh + Expiry View toggle
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
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
                    .buttonStyle(.plain)

                    Button {
                        vm.loadSeatMap(shift: selectedShift, date: dateString)
                    } label: {
                        Text("↻ Refresh")
                            .font(.labelMedium).foregroundColor(.textSub)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(Color.cardBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                    }
                    .buttonStyle(.plain)

                    Button {
                        viewMode = (viewMode == .expiry) ? .standard : .expiry
                    } label: {
                        Text("📅 Expiry View")
                            .font(.labelMedium)
                            .foregroundColor(viewMode == .expiry ? .amber : .textSub)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(viewMode == .expiry ? Color.amberFaint : Color.cardBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(viewMode == .expiry ? Color.amber : Color.cardBorder))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.vertical, 10)
        .background(Color.navyMid.opacity(0.2))
    }

    private func seatContent(_ map: SeatMapDto) -> some View {
        let pct = map.totalSeats > 0 ? Double(map.occupiedSeats) / Double(map.totalSeats) : 0

        return ScrollView {
            VStack(spacing: 16) {
                // Stats row
                HStack(spacing: 10) {
                    StatCard(label: "Total",     value: "\(map.totalSeats)",    accent: .blueSoft)
                    StatCard(label: "Occupied",  value: "\(map.occupiedSeats)", accent: .redAlert)
                    StatCard(label: "Available", value: "\(map.availableSeats)", accent: .emerald)
                }
                .padding(.horizontal, 16)

                // Occupancy bar
                AppCard {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Occupancy").font(.labelMedium).foregroundColor(.textSub)
                            Spacer()
                            Text("\(Int(pct * 100))%").font(.labelLarge).foregroundColor(.textPrimary)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4).fill(Color.navyLight).frame(height: 10)
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(LinearGradient(colors: [.emerald, .redAlert], startPoint: .leading, endPoint: .trailing))
                                    .frame(width: geo.size.width * pct, height: 10)
                            }
                        }
                        .frame(height: 10)
                    }
                }
                .padding(.horizontal, 16)

                // Seat grid (admin read-only version — tap a booked seat to see
                // its occupant). Fixed vertically — all four rows just lay out
                // top to bottom, no vertical scroll or zoom of their own — but
                // horizontally scrollable, since the row content (14 seats a
                // side plus aisle) is wider than the screen. The entry label
                // and footer labels scroll in sync with the rows since they're
                // all inside the same ScrollView, so they stay lined up with
                // the aisle/seats at any scroll position.
                VStack(spacing: 10) {
                    seatMapLegend
                    seatGridScrollView(map)
                    Text("Tap a booked seat to view student details")
                        .font(.system(size: 11))
                        .foregroundColor(.textMuted)
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
            ?? SeatInfoItem(seatNumber: seatNumber, isOccupied: false, studentId: nil, studentName: nil,
                            studentMobile: nil, studentGender: nil, shift: nil, membershipEnd: nil)
    }

    @ViewBuilder
    private var seatMapLegend: some View {
        if viewMode == .expiry {
            HStack(spacing: 12) {
                legendItem(color: .redDeep, border: .redAlert.opacity(0.6), label: "Overdue")
                legendItem(color: .redFaint, border: .redAlert, label: "≤3d")
                legendItem(color: .orangeFaint, border: .orange, label: "≤7d")
                legendItem(color: .yellowFaint, border: .yellowWarn, label: "≤15d")
                legendItem(color: .emeraldFaint, border: .emerald, label: "Safe")
            }
        } else {
            HStack(spacing: 16) {
                legendItem(color: .emeraldFaint, border: .emerald, label: "Available")
                legendItem(color: .redFaint, border: .redAlert, label: "Male")
                legendItem(color: .fuchsiaFaint, border: .fuchsia, label: "Female")
            }
        }
    }

    // Fixed vertically, scrollable horizontally — matches Android's SeatGrid
    // (a Column with .horizontalScroll(), no vertical scroll or zoom of its
    // own). The entry label, all four rows, and the footer labels are one
    // continuous piece of content here so they all move together as the user
    // swipes left/right; nothing is a separately-positioned sibling that
    // could drift out of alignment with the aisle.
    private func seatGridScrollView(_ map: SeatMapDto) -> some View {
        ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 4) {
                // "ENTRY" label pinned above the aisle column
                HStack(spacing: 0) {
                    Color.clear.frame(width: rowLabelWidth + seatGap)
                    Color.clear.frame(width: sectionWidth)
                    Text("ENTRY")
                        .font(.system(size: 7, weight: .medium))
                        .foregroundColor(.textMuted)
                        .tracking(1)
                        .frame(width: aisleWidth)
                }

                ForEach(seatMapRowLabels, id: \.self) { row in
                    let rowSeats = map.seatsByRow[row] ?? []
                    HStack(alignment: .center, spacing: 0) {
                        Text(row)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.textSub)
                            .frame(width: rowLabelWidth)
                        Color.clear.frame(width: seatGap)

                        seatSection(row: row, topSeats: leftTopSeats, bottomSeats: leftBottomSeats, rowSeats: rowSeats)

                        // Aisle with a vertical divider line, like Android's SeatGrid
                        ZStack {
                            Rectangle().fill(Color.dividerColor.opacity(0.3)).frame(width: 1)
                        }
                        .frame(width: aisleWidth, height: sectionHeight)

                        seatSection(row: row, topSeats: rightTopSeats, bottomSeats: rightBottomSeats, rowSeats: rowSeats)
                    }
                    .padding(.bottom, 20)
                }

                // Footer: labels for what's beyond row D
                HStack(spacing: 4) {
                    Color.clear.frame(width: rowLabelWidth + seatGap)
                    floorPlanLabel("EXIT")
                    floorPlanLabel("RO / PANTRY")
                    floorPlanLabel("WASHROOM")
                }
            }
            .padding(16)
        }
    }

    // One seven-seat-by-two-sub-row block (left or right half of a row), with
    // a thin divider between the sub-rows matching Android's SeatGrid.
    private func seatSection(row: String, topSeats: [Int], bottomSeats: [Int], rowSeats: [SeatInfoItem]) -> some View {
        VStack(spacing: 2) {
            HStack(spacing: seatGap) {
                ForEach(topSeats, id: \.self) { n in seatCell(row: row, number: n, rowSeats: rowSeats) }
            }
            Rectangle().fill(Color.dividerColor.opacity(0.4)).frame(height: 0.5)
            HStack(spacing: seatGap) {
                ForEach(bottomSeats, id: \.self) { n in seatCell(row: row, number: n, rowSeats: rowSeats) }
            }
        }
    }

    @ViewBuilder
    private func seatCell(row: String, number: Int, rowSeats: [SeatInfoItem]) -> some View {
        let seatNumber = "\(row)\(number)"
        if inactiveSeatNumbers.contains(seatNumber) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.navyDeep.opacity(0.5))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.dividerColor.opacity(0.2), lineWidth: 0.5))
                .frame(width: seatSize, height: seatSize)
        } else {
            adminSeatCell(seat(row: row, number: number, in: rowSeats))
        }
    }

    @ViewBuilder
    private func adminSeatCell(_ seat: SeatInfoItem) -> some View {
        // Single tap, matching Android's SeatGrid — there's no zoom gesture
        // here for a tap to conflict with any more.
        if viewMode == .expiry, seat.isOccupied, let days = daysToExpiry(seat.membershipEnd) {
            let color = expiryColor(days)
            Text("\(days)")
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(color)
                .frame(width: seatSize, height: seatSize)
                .background(color.opacity(0.18))
                .overlay(Circle().stroke(color, lineWidth: 1))
                .clipShape(Circle())
                .contentShape(Circle())
                .onTapGesture { tappedSeat = seat }
        } else {
            // Standard view: available seats are muted emerald, occupied seats
            // are colored by gender (matching the web admin seat map) —
            // female = fuchsia, male/unspecified = red.
            let isFemale = seat.isOccupied && seat.studentGender?.caseInsensitiveCompare("Female") == .orderedSame
            let fg: Color = seat.isOccupied ? (isFemale ? .fuchsia : .redAlert) : .emerald
            let bg: Color = seat.isOccupied ? (isFemale ? Color.fuchsiaFaint : Color.redFaint) : Color.emeraldFaint
            Text(viewMode == .expiry ? "" : String(seat.seatNumber.dropFirst()))
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(fg)
                .frame(width: seatSize, height: seatSize)
                .background(bg)
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(fg, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .contentShape(Rectangle())
                .onTapGesture { if seat.isOccupied { tappedSeat = seat } }
        }
    }

    private func floorPlanLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 9, weight: .medium))
            .foregroundColor(.textMuted)
            .tracking(1)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Color.navyMid.opacity(0.4))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder))
            .clipShape(RoundedRectangle(cornerRadius: 6))
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
                                if let name = seat.studentName {
                                    if let sid = seat.studentId {
                                        Button {
                                            tappedStudentId = sid
                                            showStudentDetail = true
                                        } label: {
                                            HStack {
                                                Text("Student").font(.bodySmall).foregroundColor(.textMuted)
                                                Spacer()
                                                Text(name).font(.labelMedium).foregroundColor(.amber)
                                                Image(systemName: "chevron.right").font(.caption).foregroundColor(.textMuted)
                                            }
                                            .padding(.vertical, 8)
                                        }
                                        .buttonStyle(.plain)
                                        Divider().background(Color.dividerColor)
                                    } else {
                                        InfoRow(label: "Student", value: name)
                                    }
                                }
                                if let mob  = seat.studentMobile { InfoRow(label: "Mobile",  value: mob) }
                                if let gen  = seat.studentGender { InfoRow(label: "Gender", value: gen) }
                                if let end  = seat.membershipEnd { InfoRow(label: "Expires", value: end) }
                                if let sh   = seat.shift { InfoRow(label: "Shift", value: sh.capitalized) }
                                if let days = daysToExpiry(seat.membershipEnd) {
                                    InfoRow(label: "Days Left", value: "\(days)", valueColor: expiryColor(days))
                                }
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
                        .buttonStyle(.plain)

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
            .navigationDestination(isPresented: $showStudentDetail) {
                AdminStudentDetailView(vm: vm, studentId: tappedStudentId)
            }
        }
    }
}

extension SeatInfoItem: Identifiable {
    var id: String { seatNumber }
}
