import SwiftUI

enum SeatState {
    case available, selected, booked
}

// Shared seat-picker grid — used by student booking (BookingView), admin
// "change seat" (AdminStudentDetailView), and admin "Create Membership" (step 3).
// Mirrors the real physical layout (same one AdminSeatsView's read-only admin
// seat map already uses, and the web app's SeatGrid): each row splits into a
// left block (seats 1-14) and a right block (15-28) with an aisle between them,
// each block arranged as two stacked sub-rows of 7 back-to-back desk pairs —
// not one flat strip of 14 seats, which didn't match the actual desk
// arrangement and was wider than a phone screen really needs.
struct SeatGridView: View {
    let seats: [Seat]
    let selectedSeat: String?
    let onSelect: (Seat) -> Void
    var readOnly: Bool = false

    private let rowLabels = ["A", "B", "C", "D"]
    // Physically blocked by pillars — excluded from selection regardless of
    // what the backend returns for them, same as AdminSeatsView.
    private let inactiveSeatNumbers: Set<String> = ["B8", "B18"]
    private let leftTopSeats     = [13, 11, 9, 7, 5, 3, 1]
    private let leftBottomSeats  = [14, 12, 10, 8, 6, 4, 2]
    private let rightTopSeats    = [15, 17, 19, 21, 23, 25, 27]
    private let rightBottomSeats = [16, 18, 20, 22, 24, 26, 28]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Legend
            HStack(spacing: 16) {
                legendItem(color: .emeraldFaint, border: .emerald, label: "Available")
                legendItem(color: .amberFaint, border: .amber, label: "Selected")
                legendItem(color: .redFaint, border: .redAlert, label: "Booked")
            }
            .padding(.bottom, 12)

            ScrollView(.horizontal, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(rowLabels, id: \.self) { row in
                        rowView(rowLabel: row)
                    }
                }
                .padding(4)
            }
        }
    }

    private func rowView(rowLabel: String) -> some View {
        let rowSeats = seatsForRow(rowLabel)
        return HStack(alignment: .top, spacing: 6) {
            Text(rowLabel)
                .font(.labelSmall)
                .foregroundColor(.textMuted)
                .frame(width: 16)
                .padding(.top, 4)

            VStack(spacing: 4) {
                HStack(spacing: 4) {
                    ForEach(leftTopSeats, id: \.self) { n in seatCell(rowLabel: rowLabel, number: n, rowSeats: rowSeats) }
                }
                HStack(spacing: 4) {
                    ForEach(leftBottomSeats, id: \.self) { n in seatCell(rowLabel: rowLabel, number: n, rowSeats: rowSeats) }
                }
            }

            // Aisle
            Rectangle()
                .fill(Color.clear)
                .frame(width: 18)

            VStack(spacing: 4) {
                HStack(spacing: 4) {
                    ForEach(rightTopSeats, id: \.self) { n in seatCell(rowLabel: rowLabel, number: n, rowSeats: rowSeats) }
                }
                HStack(spacing: 4) {
                    ForEach(rightBottomSeats, id: \.self) { n in seatCell(rowLabel: rowLabel, number: n, rowSeats: rowSeats) }
                }
            }
        }
    }

    @ViewBuilder
    private func seatCell(rowLabel: String, number: Int, rowSeats: [Seat]) -> some View {
        let seatNumber = "\(rowLabel)\(number)"
        if inactiveSeatNumbers.contains(seatNumber) {
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.navyMid.opacity(0.5))
                .frame(width: 28, height: 28)
        } else {
            actualSeatCell(seat(rowLabel: rowLabel, number: number, in: rowSeats))
        }
    }

    private func actualSeatCell(_ seat: Seat) -> some View {
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
        .buttonStyle(.plain)
        .disabled(readOnly || state == .booked)
    }

    // Looks up a specific seat number in the row's data; any seat number the
    // backend didn't return (shouldn't normally happen) is treated as
    // available rather than omitted — matches AdminSeatsView's own fallback.
    private func seat(rowLabel: String, number: Int, in rowSeats: [Seat]) -> Seat {
        let seatNumber = "\(rowLabel)\(number)"
        return rowSeats.first(where: { $0.seatNumber == seatNumber })
            ?? Seat(id: seatNumber, seatNumber: seatNumber, row: rowLabel, isBooked: false,
                    studentName: nil, studentMobile: nil, membershipEnd: nil)
    }

    private func seatsForRow(_ rowLabel: String) -> [Seat] {
        seats.filter { $0.row == rowLabel || $0.rowLabel == rowLabel }
    }

    private func seatState(_ seat: Seat) -> SeatState {
        if seat.isBooked { return .booked }
        if seat.seatNumber == selectedSeat { return .selected }
        return .available
    }

    private func backgroundFor(_ state: SeatState) -> Color {
        switch state {
        case .available: return .emeraldFaint
        case .selected:  return .amberFaint
        case .booked:    return .redFaint
        }
    }

    private func borderFor(_ state: SeatState) -> Color {
        switch state {
        case .available: return .emerald
        case .selected:  return .amber
        case .booked:    return .redAlert
        }
    }

    private func foregroundFor(_ state: SeatState) -> Color {
        switch state {
        case .available: return .emerald
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
