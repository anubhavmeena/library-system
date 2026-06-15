import SwiftUI

struct AdminLoginView: View {
    @StateObject private var vm = AuthViewModel()
    @State private var contact = ""
    @State private var otp     = ""

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 32) {
                    VStack(spacing: 12) {
                        Image(systemName: "shield.checkered")
                            .font(.system(size: 48))
                            .foregroundColor(.amber)
                        Text("Admin Login")
                            .font(.headlineLarge)
                            .foregroundColor(.textPrimary)
                        Text("Enter your email or mobile")
                            .font(.bodyMedium)
                            .foregroundColor(.textSub)
                    }
                    .padding(.top, 60)

                    VStack(spacing: 16) {
                        if !vm.otpSent {
                            AppTextField(label: "Email / Mobile", text: $contact,
                                         placeholder: "admin@example.com",
                                         keyboardType: .emailAddress,
                                         leadingIcon: "person.badge.key")

                            PrimaryButton("Send OTP", isLoading: vm.isLoading) {
                                guard !contact.isEmpty else { return }
                                vm.sendAdminOtp(contact: contact)
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("OTP sent to \(contact)")
                                    .font(.bodySmall)
                                    .foregroundColor(.textSub)
                                Button("Change") { vm.resetOtpState(); contact = "" }
                                    .font(.labelSmall)
                                    .foregroundColor(.amber)
                            }

                            AppTextField(label: "Enter OTP", text: $otp,
                                         placeholder: "6-digit code",
                                         keyboardType: .numberPad,
                                         leadingIcon: "key")

                            PrimaryButton("Login", isLoading: vm.isLoading) {
                                guard otp.count >= 4 else { return }
                                vm.adminLogin(otp: otp)
                            }
                        }

                        if let err = vm.error {
                            ErrorBanner(message: err)
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }
        }
        .navigationTitle("Admin")
        .navigationBarTitleDisplayMode(.inline)
    }
}
