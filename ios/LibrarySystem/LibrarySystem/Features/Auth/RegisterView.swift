import SwiftUI

struct RegisterView: View {
    @ObservedObject var vm: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name        = ""
    @State private var email       = ""
    @State private var dateOfBirth = ""
    @State private var hasDateOfBirth = false
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

                        dateOfBirthField

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
            .scrollDismissesKeyboard(.interactively)
        }
        .dismissKeyboardOnTap()
        .navigationBarTitleDisplayMode(.inline)
    }

    // Date of birth is optional, but a DatePicker always carries a concrete
    // Date — the toggle is what actually represents "not set" vs. "set",
    // since a plain free-text field let a mistyped yyyy-MM-dd silently fail
    // whatever the backend does with an unparseable string.
    private var dateOfBirthField: some View {
        VStack(alignment: .leading, spacing: 4) {
            Toggle(isOn: $hasDateOfBirth) {
                Text("Date of Birth (optional)").font(.labelMedium).foregroundColor(.textSub)
            }
            .tint(.amber)
            .onChange(of: hasDateOfBirth) { on in
                dateOfBirth = on ? Self.formatYMD(Self.parseYMD(dateOfBirth) ?? Self.defaultDOB) : ""
            }
            if hasDateOfBirth {
                DatePicker("", selection: Binding(
                    get: { Self.parseYMD(dateOfBirth) ?? Self.defaultDOB },
                    set: { dateOfBirth = Self.formatYMD($0) }
                ), in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .labelsHidden()
                    .tint(.amber)
                    .colorScheme(.dark)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(12)
        .background(Color.cardBg)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private static var defaultDOB: Date {
        Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()
    }

    private static func parseYMD(_ s: String) -> Date? {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.date(from: s)
    }

    private static func formatYMD(_ date: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.string(from: date)
    }
}
