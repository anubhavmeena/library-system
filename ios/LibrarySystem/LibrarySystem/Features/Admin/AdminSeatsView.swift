import SwiftUI

struct AdminSeatsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var selectedShift = "FULL_DAY"
    @State private var selectedDate  = Date()
    @State private var showDatePicker = false
    @State private var tappedSeat:   SeatInfoItem?
    @State private var showSeatHistory = false
    @State private var viewMode: SeatViewMode = .standard

    // Pinch-to-zoom / pan for the seat grid specifically (not the legend or
    // the rest of the page) — scale is clamped to [1, 4] and offset resets to
    // zero once zoomed back out to avoid getting "stuck" panned when small.
    @State private var seatMapScale: CGFloat = 1.0
    @State private var seatMapLastScale: CGFloat = 1.0
    @State private var seatMapOffset: CGSize = .zero
    @State private var seatMapLastOffset: CGSize = .zero

    private enum SeatViewMode { case standard, expiry }

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

                // Seat grid (admin read-only version — tap to see occupant).
                // The grid itself lives in a fixed-height, pinch-to-zoom/pan
                // region (zoomableSeatGrid) separate from the legend, which
                // stays fixed size — zooming the legend text wouldn't be
                // useful. Grid already fits at 1x zoom (see the compact cell
                // sizing), so pinch/pan is purely an optional closer look.
                VStack(spacing: 10) {
                    seatMapLegend
                    floorPlanLabel("ENTRY")
                    zoomableSeatGrid(map)
                    HStack(spacing: 8) {
                        floorPlanLabel("EXIT")
                        floorPlanLabel("RO / PANTRY")
                        floorPlanLabel("WASHROOM")
                    }
                    Text("Pinch to zoom · drag to pan · double-tap to reset")
                        .font(.system(size: 10))
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
            ?? SeatInfoItem(seatNumber: seatNumber, isOccupied: false, studentName: nil,
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

    // Pinch-to-zoom + drag-to-pan container for the seat grid. Fixed height
    // via GeometryReader so the scaled/offset content has well-defined
    // clipping bounds instead of blowing out the page layout. Panning only
    // takes effect once zoomed in, so a plain single-finger drag at the
    // default 1x zoom doesn't fight the page's own vertical scroll.
    private func zoomableSeatGrid(_ map: SeatMapDto) -> some View {
        GeometryReader { geo in
            adminSeatGrid(map)
                .padding(16)
                .frame(width: geo.size.width, height: geo.size.height)
                .scaleEffect(seatMapScale)
                .offset(seatMapOffset)
                .clipped()
                .contentShape(Rectangle())
                .gesture(
                    SimultaneousGesture(
                        MagnificationGesture()
                            .onChanged { value in
                                seatMapScale = min(max(seatMapLastScale * value, 1.0), 4.0)
                            }
                            .onEnded { _ in
                                seatMapLastScale = seatMapScale
                                if seatMapScale <= 1.01 {
                                    seatMapScale = 1.0
                                    seatMapLastScale = 1.0
                                    seatMapOffset = .zero
                                    seatMapLastOffset = .zero
                                }
                            },
                        DragGesture()
                            .onChanged { value in
                                guard seatMapScale > 1.0 else { return }
                                seatMapOffset = CGSize(
                                    width: seatMapLastOffset.width + value.translation.width,
                                    height: seatMapLastOffset.height + value.translation.height
                                )
                            }
                            .onEnded { _ in
                                seatMapLastOffset = seatMapOffset
                            }
                    )
                )
                .onTapGesture(count: 2) {
                    withAnimation(.spring()) {
                        seatMapScale = 1.0
                        seatMapLastScale = 1.0
                        seatMapOffset = .zero
                        seatMapLastOffset = .zero
                    }
                }
        }
        .frame(height: 340)
    }

    private func adminSeatGrid(_ map: SeatMapDto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
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

    @ViewBuilder
    private func adminSeatCell(_ seat: SeatInfoItem) -> some View {
        if viewMode == .expiry, seat.isOccupied, let days = daysToExpiry(seat.membershipEnd) {
            let color = expiryColor(days)
            Button { tappedSeat = seat } label: {
                Text("\(days)")
                    .font(.system(size: 7, weight: .bold))
                    .foregroundColor(color)
                    .frame(width: 22, height: 22)
                    .background(color.opacity(0.18))
                    .overlay(Circle().stroke(color, lineWidth: 1))
                    .clipShape(Circle())
            }
        } else {
            // Standard view: available seats are muted emerald, occupied seats
            // are colored by gender (matching the web admin seat map) —
            // female = fuchsia, male/unspecified = red.
            let isFemale = seat.isOccupied && seat.studentGender?.caseInsensitiveCompare("Female") == .orderedSame
            let fg: Color = seat.isOccupied ? (isFemale ? .fuchsia : .redAlert) : .emerald
            let bg: Color = seat.isOccupied ? (isFemale ? Color.fuchsiaFaint : Color.redFaint) : Color.emeraldFaint
            Button { if seat.isOccupied { tappedSeat = seat } } label: {
                Text(viewMode == .expiry ? "" : String(seat.seatNumber.dropFirst()))
                    .font(.system(size: 7, weight: .medium))
                    .foregroundColor(fg)
                    .frame(width: 22, height: 22)
                    .background(bg)
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(fg, lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .disabled(!seat.isOccupied)
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
                                if let name = seat.studentName { InfoRow(label: "Student",  value: name) }
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
