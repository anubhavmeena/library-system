import SwiftUI

struct RegisterView: View {
    @ObservedObject var vm: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name        = ""
    @State private var email       = ""
    @State private var dateOfBirth = ""
    @State private var gender      = ""
    @State private var address     = ""

    private let genders = ["", "MALE", "FEMALE", "OTHER"]

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 24) {
                    VStack(spacing: 8) {
                        Image(systemName: "person.badge.plus")
                            .font(.system(size: 40))
                            .foregroundColor(.amber)
                        Text("Complete Your Profile")
                            .font(.headlineLarge)
                            .foregroundColor(.textPrimary)
                        Text("Required fields are marked *")
                            .font(.bodySmall)
                            .foregroundColor(.textMuted)
                    }
                    .padding(.top, 40)

                    VStack(spacing: 16) {
                        AppTextField(label: "Full Name *", text: $name,
                                     placeholder: "Your name", leadingIcon: "person")

                        AppTextField(label: "Email (optional)", text: $email,
                                     placeholder: "you@example.com",
                                     keyboardType: .emailAddress,
                                     leadingIcon: "envelope")

                        AppTextField(label: "Date of Birth (yyyy-MM-dd)", text: $dateOfBirth,
                                     placeholder: "2000-01-31", leadingIcon: "calendar")

                        // Gender picker
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Gender")
                                .font(.labelMedium)
                                .foregroundColor(.textSub)
                            Picker("Gender", selection: $gender) {
                                Text("Select gender").tag("")
                                Text("Male").tag("MALE")
                                Text("Female").tag("FEMALE")
                                Text("Other").tag("OTHER")
                            }
                            .pickerStyle(.menu)
                            .tint(.amber)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Color.cardBg)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }

                        AppTextField(label: "Address (optional)", text: $address,
                                     placeholder: "Your address", leadingIcon: "mappin")

                        PrimaryButton("Create Account", isLoading: vm.isLoading) {
                            guard !name.isEmpty else { return }
                            vm.register(
                                name: name,
                                email: email.isEmpty ? nil : email,
                                dateOfBirth: dateOfBirth.isEmpty ? nil : dateOfBirth,
                                gender: gender.isEmpty ? nil : gender,
                                address: address.isEmpty ? nil : address
                            )
                        }

                        if let err = vm.error {
                            ErrorBanner(message: err)
                        }
                    }
                    .padding(.horizontal, 24)
                }
                .padding(.bottom, 40)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
