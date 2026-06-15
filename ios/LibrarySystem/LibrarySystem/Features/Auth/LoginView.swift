import SwiftUI

struct LoginView: View {
    @StateObject private var vm = AuthViewModel()
    @State private var mobile = ""
    @State private var otp    = ""

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
                            AppTextField(label: "Mobile Number", text: $mobile,
                                         placeholder: "10-digit mobile",
                                         keyboardType: .numberPad,
                                         leadingIcon: "phone")

                            PrimaryButton("Send OTP", isLoading: vm.isLoading) {
                                guard mobile.count == 10 else { return }
                                vm.sendOtp(contact: mobile)
                            }

                            NavigationLink("Login as Admin") {
                                AdminLoginView()
                            }
                            .font(.labelMedium)
                            .foregroundColor(.textSub)
                        } else {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("OTP sent to \(mobile)")
                                    .font(.bodySmall)
                                    .foregroundColor(.textSub)
                                Button("Change") { vm.resetOtpState(); mobile = "" }
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

                            Button("Resend OTP") { vm.sendOtp(contact: mobile) }
                                .font(.labelMedium)
                                .foregroundColor(.amber)
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
        }
        .navigationBarHidden(true)
    }
}
