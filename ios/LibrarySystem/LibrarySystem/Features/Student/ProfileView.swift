import SwiftUI
import PhotosUI

struct ProfileView: View {
    @ObservedObject var vm: StudentViewModel
    @ObservedObject private var tokenManager = TokenManager.shared

    @State private var editMode    = false
    @State private var name        = ""
    @State private var fatherName  = ""
    @State private var email       = ""
    @State private var dateOfBirth = ""
    @State private var gender      = ""
    @State private var address     = ""
    @State private var photoItem:   PhotosPickerItem?
    @State private var aadhaarItem: PhotosPickerItem?
    @State private var showLogoutAlert = false

    private let baseURL = "https://targetzone.co.in"

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        profileHeader
                        if editMode { editForm } else { infoSection }
                        uploadSection
                        logoutButton
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(editMode ? "Done" : "Edit") {
                        if editMode { saveProfile() }
                        editMode.toggle()
                    }
                    .foregroundColor(.amber)
                }
            }
            .alert("Logout", isPresented: $showLogoutAlert) {
                Button("Logout", role: .destructive) { tokenManager.clear() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to logout?")
            }
        }
        .onAppear { vm.loadProfile(); populateFields() }
        .onChange(of: vm.profile) { _ in populateFields() }
        .onChange(of: photoItem) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self) {
                    vm.uploadPhoto(data: data)
                }
            }
        }
        .onChange(of: aadhaarItem) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self) {
                    vm.uploadAadhaar(data: data)
                }
            }
        }
    }

    // MARK: - Sections

    private var profileHeader: some View {
        VStack(spacing: 12) {
            ZStack(alignment: .bottomTrailing) {
                if let urlStr = vm.photoUrl, let url = URL(string: baseURL + urlStr) {
                    AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                    placeholder: { Color.navyLight }
                        .frame(width: 90, height: 90)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.amber, lineWidth: 2))
                } else {
                    Circle()
                        .fill(Color.navyMid)
                        .frame(width: 90, height: 90)
                        .overlay(
                            Text(String((vm.profile?.name ?? "?").prefix(1)))
                                .font(.system(size: 36, weight: .bold))
                                .foregroundColor(.amber)
                        )
                }

                PhotosPicker(selection: $photoItem, matching: .images) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.navyDeep)
                        .padding(6)
                        .background(Color.amber)
                        .clipShape(Circle())
                }
            }

            Text(vm.profile?.name ?? tokenManager.currentUser?.name ?? "")
                .font(.headlineMedium)
                .foregroundColor(.textPrimary)
            Text(vm.profile?.mobile ?? tokenManager.currentUser?.mobile ?? "")
                .font(.bodySmall)
                .foregroundColor(.textSub)
        }
    }

    private var infoSection: some View {
        AppCard {
            VStack(spacing: 0) {
                if let p = vm.profile {
                    if let fn = p.fatherName, !fn.isEmpty { InfoRow(label: "Father's Name", value: fn) }
                    if let em = p.email,      !em.isEmpty { InfoRow(label: "Email",         value: em) }
                    if let dob = p.dateOfBirth             { InfoRow(label: "Date of Birth", value: dob) }
                    if let g = p.gender                    { InfoRow(label: "Gender",        value: g.capitalized) }
                    if let addr = p.address, !addr.isEmpty { InfoRow(label: "Address",       value: addr) }
                }
            }
        }
    }

    private var editForm: some View {
        AppCard {
            VStack(spacing: 14) {
                AppTextField(label: "Full Name",       text: $name,        leadingIcon: "person")
                AppTextField(label: "Father's Name",   text: $fatherName,  leadingIcon: "person.2")
                AppTextField(label: "Email",           text: $email,       keyboardType: .emailAddress, leadingIcon: "envelope")
                AppTextField(label: "Date of Birth",   text: $dateOfBirth, placeholder: "yyyy-MM-dd", leadingIcon: "calendar")
                VStack(alignment: .leading, spacing: 4) {
                    Text("Gender").font(.labelMedium).foregroundColor(.textSub)
                    Picker("", selection: $gender) {
                        Text("Select").tag("")
                        Text("Male").tag("MALE"); Text("Female").tag("FEMALE"); Text("Other").tag("OTHER")
                    }
                    .pickerStyle(.menu).tint(.amber)
                    .padding(12).background(Color.cardBg)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                AppTextField(label: "Address", text: $address, leadingIcon: "mappin")

                if let err = vm.error { ErrorBanner(message: err) }
                PrimaryButton("Save", isLoading: vm.isLoading) { saveProfile() }
            }
        }
    }

    private var uploadSection: some View {
        AppCard {
            VStack(spacing: 12) {
                Text("Documents").font(.headlineSmall).foregroundColor(.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                PhotosPicker(selection: $aadhaarItem, matching: .images) {
                    HStack {
                        Image(systemName: vm.aadhaarUrl != nil ? "checkmark.circle.fill" : "arrow.up.circle")
                            .foregroundColor(vm.aadhaarUrl != nil ? .emerald : .amber)
                        Text(vm.aadhaarUrl != nil ? "Aadhaar Uploaded" : "Upload Aadhaar")
                            .font(.labelMedium)
                            .foregroundColor(.textPrimary)
                        Spacer()
                    }
                    .padding(12)
                    .background(Color.navyMid.opacity(0.3))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
    }

    private var logoutButton: some View {
        Button { showLogoutAlert = true } label: {
            HStack {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                Text("Logout")
            }
            .foregroundColor(.redAlert)
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(Color.redFaint)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.redAlert.opacity(0.4)))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    // MARK: - Actions

    private func populateFields() {
        guard let p = vm.profile else { return }
        name        = p.name
        fatherName  = p.fatherName ?? ""
        email       = p.email ?? ""
        dateOfBirth = p.dateOfBirth ?? ""
        gender      = p.gender ?? ""
        address     = p.address ?? ""
    }

    private func saveProfile() {
        vm.updateProfile(
            name: name, fatherName: fatherName.isEmpty ? nil : fatherName,
            address: address.isEmpty ? nil : address,
            gender: gender.isEmpty ? nil : gender,
            dateOfBirth: dateOfBirth.isEmpty ? nil : dateOfBirth,
            email: email.isEmpty ? nil : email
        )
    }
}
