import SwiftUI
import UniformTypeIdentifiers

struct AdminImportView: View {
    @State private var name       = ""
    @State private var phone      = ""
    @State private var fees       = ""
    @State private var date       = ""
    @State private var seatNumber = ""

    @State private var isLoading  = false
    @State private var error:     String?
    @State private var success:   String?

    @State private var showFilePicker = false
    @State private var pickedFileName: String?
    @State private var csvUploading   = false
    @State private var csvError:       String?
    @State private var csvResult:      ImportResult?

    private var api: APIClient { APIClient.shared }
    private var token: String? { TokenManager.shared.token }
    private let repo = AdminRepository.shared

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        instructionCard
                        formSection
                        bulkImportSection
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
        .fileImporter(isPresented: $showFilePicker,
                     allowedContentTypes: [.commaSeparatedText, UTType(filenameExtension: "xlsx") ?? .data,
                                           UTType(filenameExtension: "xls") ?? .data]) { result in
            switch result {
            case .success(let url): uploadCSV(url: url)
            case .failure(let err): csvError = err.localizedDescription
            }
        }
    }

    private var bulkImportSection: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Bulk Import (CSV / XLSX)").font(.labelLarge).foregroundColor(.textPrimary)
                Text("Upload a spreadsheet with name and phone columns to register many students at once.")
                    .font(.bodySmall).foregroundColor(.textSub)

                Button { showFilePicker = true } label: {
                    HStack {
                        Image(systemName: "doc.badge.plus")
                        Text(pickedFileName ?? "Choose File")
                    }
                    .font(.labelMedium).foregroundColor(.amber)
                    .frame(maxWidth: .infinity).padding(12)
                    .background(Color.amberFaint)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.4)))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .disabled(csvUploading)

                if csvUploading {
                    HStack { ProgressView().tint(.amber); Text("Uploading…").font(.bodySmall).foregroundColor(.textSub) }
                }
                if let err = csvError { ErrorBanner(message: err) }
                if let result = csvResult { importResultSummary(result) }
            }
        }
    }

    private func importResultSummary(_ result: ImportResult) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                StatCard(label: "Total",    value: "\(result.totalRows)", accent: .blueSoft)
                StatCard(label: "Imported", value: "\(result.imported)",  accent: .emerald)
                StatCard(label: "Skipped",  value: "\(result.skipped)",   accent: result.skipped > 0 ? .redAlert : .textMuted)
            }
            if let errors = result.errors, !errors.isEmpty {
                Text("Skipped Rows").font(.labelMedium).foregroundColor(.textSub)
                ForEach(errors) { e in
                    HStack {
                        Text("Row \(e.row)").font(.labelSmall).foregroundColor(.textMuted)
                        Text(e.name ?? "—").font(.bodySmall).foregroundColor(.textPrimary)
                        Spacer()
                        Text(e.reason).font(.labelSmall).foregroundColor(.redAlert)
                    }
                }
            }
        }
    }

    private func uploadCSV(url: URL) {
        pickedFileName = url.lastPathComponent
        csvError = nil
        csvResult = nil
        guard url.startAccessingSecurityScopedResource() else {
            csvError = "Could not access the selected file"
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        guard let data = try? Data(contentsOf: url) else {
            csvError = "Could not read the selected file"
            return
        }
        let ext = url.pathExtension.lowercased()
        let mimeType = ext == "csv" ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        csvUploading = true
        Task {
            do {
                csvResult = try await repo.importStudentsCSV(data: data, fileName: url.lastPathComponent, mimeType: mimeType)
            } catch {
                csvError = error.localizedDescription
            }
            csvUploading = false
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
                             placeholder: "10-digit mobile number", keyboardType: .phonePad,
                             leadingIcon: "phone")
                AppTextField(label: "Seat Number *", text: $seatNumber,
                             placeholder: "e.g. A1, B12", leadingIcon: "chair")
                AppTextField(label: "Fees (optional)", text: $fees,
                             placeholder: "Amount — matches nearest plan",
                             keyboardType: .decimalPad, leadingIcon: "indianrupeesign")
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
