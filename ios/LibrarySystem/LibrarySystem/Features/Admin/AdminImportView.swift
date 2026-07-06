import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct AdminImportView: View {
    @State private var name       = ""
    @State private var phone      = ""

    @State private var isLoading  = false
    @State private var error:     String?
    @State private var success:   String?

    @State private var showCamera      = false
    @State private var showCropper     = false
    @State private var rawCapturedImage: UIImage?
    @State private var croppedImage:     UIImage?

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
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView { image in
                rawCapturedImage = image
                showCamera = false
                showCropper = true
            } onCancel: {
                showCamera = false
            }
        }
        .fullScreenCover(isPresented: $showCropper) {
            if let raw = rawCapturedImage {
                PassportCropView(image: raw) { cropped in
                    croppedImage = cropped
                    showCropper = false
                } onCancel: {
                    showCropper = false
                }
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
                    Text("Register a student directly by name and phone number, without going through the app. You can optionally take a passport-style photo with the camera.")
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

                photoSection

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

    private var photoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let img = croppedImage {
                HStack(spacing: 12) {
                    Image(uiImage: img)
                        .resizable().scaledToFill()
                        .frame(width: 70, height: 90)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.amber.opacity(0.4)))
                    Button("Retake Photo") { openCamera() }
                        .font(.labelMedium).foregroundColor(.amber)
                        .buttonStyle(.plain)
                    Spacer()
                }
            } else {
                Button { openCamera() } label: {
                    HStack {
                        Image(systemName: "camera")
                        Text("Take Photo (optional)")
                    }
                    .font(.labelMedium).foregroundColor(.amber)
                    .frame(maxWidth: .infinity).padding(12)
                    .background(Color.amberFaint)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.4)))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            error = "Camera not available on this device"
            return
        }
        showCamera = true
    }

    private func submit() {
        error = nil; success = nil
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Name is required"; return
        }
        guard !phone.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Phone number is required"; return
        }

        isLoading = true
        let trimmedName  = name.trimmingCharacters(in: .whitespaces)
        let trimmedPhone = phone.trimmingCharacters(in: .whitespaces)

        Task {
            do {
                if let img = croppedImage, let jpeg = img.jpegData(compressionQuality: 0.8) {
                    _ = try await repo.importSingleStudentWithPhoto(name: trimmedName, phone: trimmedPhone, photoData: jpeg)
                } else {
                    let req = ManualImportRequest(name: trimmedName, phone: trimmedPhone)
                    try await api.requestVoid(.importSingleStudent(req), token: token)
                }
                success = "\(trimmedName) has been registered successfully."
                name = ""; phone = ""; rawCapturedImage = nil; croppedImage = nil
            } catch {
                self.error = error.localizedDescription
            }
            isLoading = false
        }
    }
}
