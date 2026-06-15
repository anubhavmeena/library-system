import SwiftUI

struct PaymentSuccessView: View {
    @ObservedObject var vm: StudentViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()
            VStack(spacing: 28) {
                Spacer()
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 72))
                    .foregroundColor(.emerald)

                VStack(spacing: 8) {
                    Text("Payment Successful!")
                        .font(.headlineLarge)
                        .foregroundColor(.textPrimary)
                    Text("Your seat has been booked")
                        .font(.bodyMedium)
                        .foregroundColor(.textSub)
                }

                if let m = vm.membership {
                    AppCard(accentColor: .emerald) {
                        VStack(spacing: 12) {
                            InfoRow(label: "Plan",       value: m.planName)
                            InfoRow(label: "Seat",       value: m.seatNumber.isEmpty ? "—" : m.seatNumber)
                            InfoRow(label: "Shift",      value: m.shift.capitalized)
                            InfoRow(label: "Valid From", value: m.startDate)
                            InfoRow(label: "Valid Until",value: m.endDate)
                            InfoRow(label: "Amount Paid",value: "₹\(String(format: "%.0f", m.amountPaid))")
                        }
                    }
                    .padding(.horizontal, 24)
                }

                Spacer()

                PrimaryButton("Go to Dashboard") { dismiss() }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 40)
            }
        }
        .navigationBarHidden(true)
    }
}
