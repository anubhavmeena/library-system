import SwiftUI

struct LoginView: View {
    @StateObject private var vm = AuthViewModel()
    @State private var contact     = ""
    @State private var contactType = "MOBILE"   // "MOBILE" | "EMAIL"
    @State private var otp         = ""

    private var isContactValid: Bool {
        contactType == "MOBILE" ? contact.count == 10 : contact.contains("@")
    }

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 32) {
                    // Header
                    VStack(spacing: 12) {
                        Image(systemName: "books.vertical.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.amber)
                        Text("TargetZone Library")
                            .font(.headlineLarge)
                            .foregroundColor(.textPrimary)
                        Text("Sign in to continue")
                            .font(.bodyMedium)
                            .foregroundColor(.textSub)
                    }
                    .padding(.top, 60)

                    // Form
                    VStack(spacing: 16) {
                        if !vm.otpSent {
                            Picker("Contact Type", selection: $contactType) {
                                Text("Mobile").tag("MOBILE")
                                Text("Email").tag("EMAIL")
                            }
                            .pickerStyle(.segmented)
                            .onChange(of: contactType) { _ in contact = "" }

                            AppTextField(label: contactType == "MOBILE" ? "Mobile Number" : "Email",
                                         text: $contact,
                                         placeholder: contactType == "MOBILE" ? "10-digit mobile" : "you@example.com",
                                         keyboardType: contactType == "MOBILE" ? .numberPad : .emailAddress,
                                         leadingIcon: contactType == "MOBILE" ? "phone" : "envelope")

                            PrimaryButton("Send OTP", isLoading: vm.isLoading) {
                                guard isContactValid else { return }
                                vm.sendOtp(contact: contact, contactType: contactType)
                            }

                            NavigationLink("Login as Admin") {
                                AdminLoginView()
                            }
                            .font(.labelMedium)
                            .foregroundColor(.textSub)
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

                            PrimaryButton("Verify OTP", isLoading: vm.isLoading) {
                                guard otp.count == 6 else { return }
                                vm.verifyOtp(otp: otp)
                            }

                            Button(vm.canResend ? "Resend OTP" : "Resend in \(vm.secondsLeft)s") {
                                vm.resendOtp()
                            }
                            .font(.labelMedium)
                            .foregroundColor(vm.canResend ? .amber : .textMuted)
                            .disabled(!vm.canResend || vm.isLoading)

                            if vm.showSmsOption {
                                Button("Still no OTP? Send via SMS instead") {
                                    vm.sendOtpViaSms()
                                }
                                .font(.labelMedium)
                                .foregroundColor(.blueSoft)
                                .disabled(vm.isLoading)
                            }
                        }

                        if let err = vm.error {
                            ErrorBanner(message: err)
                        }
                    }
                    .padding(.horizontal, 24)
                }
                .padding(.bottom, 40)
            }
            .navigationDestination(isPresented: .init(
                get: { vm.otpSent && vm.sessionToken != nil && vm.isNewUser },
                set: { _ in }
            )) {
                RegisterView(vm: vm)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .dismissKeyboardOnTap()
        .navigationBarHidden(true)
    }
}
