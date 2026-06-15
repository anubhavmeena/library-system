import SwiftUI

enum SeatState {
    case available, selected, booked
}

struct SeatGridView: View {
    let seats: [Seat]
    let selectedSeat: String?
    let onSelect: (Seat) -> Void
    var readOnly: Bool = false

    // Row counts matching the physical library layout
    private let rows: [(label: String, count: Int)] = [
        ("A", 28), ("B", 28), ("C", 28), ("D", 26)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Legend
            HStack(spacing: 16) {
                legendItem(color: .cardBg, border: .cardBorder, label: "Available")
                legendItem(color: .amberFaint, border: .amber, label: "Selected")
                legendItem(color: .redFaint, border: .redAlert, label: "Booked")
            }
            .padding(.bottom, 12)

            ScrollView(.horizontal, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(rows, id: \.label) { row in
                        rowView(rowLabel: row.label, count: row.count)
                    }
                }
                .padding(.horizontal, 4)
            }
        }
    }

    private func rowView(rowLabel: String, count: Int) -> some View {
        let rowSeats = seatsForRow(rowLabel, count: count)
        let half = (count + 1) / 2
        return HStack(alignment: .top, spacing: 0) {
            Text(rowLabel)
                .font(.labelSmall)
                .foregroundColor(.textMuted)
                .frame(width: 18)
                .padding(.top, 4)

            // Left half
            HStack(spacing: 4) {
                ForEach(0..<half, id: \.self) { i in
                    if i < rowSeats.count {
                        seatCell(rowSeats[i])
                    }
                }
            }

            // Aisle
            Rectangle()
                .fill(Color.clear)
                .frame(width: 20)

            // Right half
            HStack(spacing: 4) {
                ForEach(half..<count, id: \.self) { i in
                    if i < rowSeats.count {
                        seatCell(rowSeats[i])
                    }
                }
            }
        }
    }

    private func seatCell(_ seat: Seat) -> some View {
        let state = seatState(seat)
        return Button {
            if !readOnly && state != .booked { onSelect(seat) }
        } label: {
            Text(shortNumber(seat.seatNumber))
                .font(.system(size: 8, weight: .medium))
                .foregroundColor(foregroundFor(state))
                .frame(width: 28, height: 28)
                .background(backgroundFor(state))
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(borderFor(state), lineWidth: state == .selected ? 1.5 : 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
        .disabled(readOnly || state == .booked)
    }

    private func seatsForRow(_ rowLabel: String, count: Int) -> [Seat] {
        let filtered = seats.filter { $0.row == rowLabel || $0.rowLabel == rowLabel }
        if !filtered.isEmpty { return filtered }
        // Synthesize placeholder seats if data has a flat list
        return (1...count).map { i in
            let number = "\(rowLabel)\(i)"
            return seats.first { $0.seatNumber == number }
                ?? Seat(id: number, seatNumber: number, row: rowLabel, isBooked: false,
                        studentName: nil, studentMobile: nil, membershipEnd: nil)
        }
    }

    private func seatState(_ seat: Seat) -> SeatState {
        if seat.isBooked { return .booked }
        if seat.seatNumber == selectedSeat { return .selected }
        return .available
    }

    private func backgroundFor(_ state: SeatState) -> Color {
        switch state {
        case .available: return .cardBg
        case .selected:  return .amberFaint
        case .booked:    return .redFaint
        }
    }

    private func borderFor(_ state: SeatState) -> Color {
        switch state {
        case .available: return .cardBorder
        case .selected:  return .amber
        case .booked:    return .redAlert
        }
    }

    private func foregroundFor(_ state: SeatState) -> Color {
        switch state {
        case .available: return .textSub
        case .selected:  return .amber
        case .booked:    return .redAlert
        }
    }

    private func shortNumber(_ seatNumber: String) -> String {
        String(seatNumber.dropFirst())
    }

    private func legendItem(color: Color, border: Color, label: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .overlay(RoundedRectangle(cornerRadius: 3).stroke(border, lineWidth: 1))
                .frame(width: 14, height: 14)
            Text(label)
                .font(.caption)
                .foregroundColor(.textMuted)
        }
    }
}
