import SwiftUI

struct AdminImportView: View {
    @State private var name       = ""
    @State private var phone      = ""
    @State private var fees       = ""
    @State private var date       = ""
    @State private var seatNumber = ""

    @State private var isLoading  = false
    @State private var error:     String?
    @State private var success:   String?

    private var api: APIClient { APIClient.shared }
    private var token: String? { TokenManager.shared.token }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        instructionCard
                        formSection
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Import Student")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private var instructionCard: some View {
        AppCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "info.circle.fill")
                    .foregroundColor(.amber).font(.title3)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Manual Student Entry")
                        .font(.labelLarge).foregroundColor(.textPrimary)
                    Text("Register a student directly without the app. The seat and membership will be created based on the closest matching plan.")
                        .font(.bodySmall).foregroundColor(.textSub)
                }
            }
        }
    }

    private var formSection: some View {
        AppCard {
            VStack(spacing: 16) {
                AppTextField(label: "Full Name *", text: $name,
                             placeholder: "Student name", leadingIcon: "person")
                AppTextField(label: "Phone Number *", text: $phone,
                             placeholder: "10-digit mobile number", leadingIcon: "phone",
                             keyboardType: .phonePad)
                AppTextField(label: "Seat Number *", text: $seatNumber,
                             placeholder: "e.g. A1, B12", leadingIcon: "chair")
                AppTextField(label: "Fees (optional)", text: $fees,
                             placeholder: "Amount — matches nearest plan",
                             leadingIcon: "indianrupeesign", keyboardType: .decimalPad)
                AppTextField(label: "Start Date (optional)", text: $date,
                             placeholder: "YYYY-MM-DD — defaults to today",
                             leadingIcon: "calendar")

                if let err = error { ErrorBanner(message: err) }
                if let suc = success {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                        Text(suc).font(.labelMedium).foregroundColor(.emerald)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.emerald.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }

                PrimaryButton("Register Student", isLoading: isLoading) { submit() }
            }
        }
    }

    private func submit() {
        error = nil; success = nil
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Name is required"; return
        }
        guard !phone.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Phone number is required"; return
        }
        guard !seatNumber.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Seat number is required"; return
        }

        isLoading = true
        let req = ManualImportRequest(
            name: name.trimmingCharacters(in: .whitespaces),
            phone: phone.trimmingCharacters(in: .whitespaces),
            fees: fees.isEmpty ? nil : fees.trimmingCharacters(in: .whitespaces),
            date: date.isEmpty ? nil : date.trimmingCharacters(in: .whitespaces),
            seatNumber: seatNumber.trimmingCharacters(in: .whitespaces).uppercased()
        )
        Task {
            do {
                try await api.requestVoid(.importSingleStudent(req), token: token)
                success = "\(name) has been registered successfully."
                name = ""; phone = ""; fees = ""; date = ""; seatNumber = ""
            } catch {
                self.error = error.localizedDescription
            }
            isLoading = false
        }
    }
}
