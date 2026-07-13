import Foundation
import Combine

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var error: String?
    @Published var otpSent = false
    @Published var sessionToken: String?
    @Published var isNewUser = false

    // OTP resend / SMS-fallback timing — mirrors the web LoginPage and the
    // backend's 10s resend cooldown (AuthService.checkCooldownAndGenerateOtp).
    // otpSendCount tracks how many times an OTP has been (re)sent;
    // secondsSinceSend counts up to 10 after each send, driving both "Resend
    // OTP" availability and the "Send via SMS instead" offer (shown once a
    // resend has also gone 10s without verification).
    @Published var otpSendCount = 0
    @Published var secondsSinceSend = 0
    @Published var smsOptionUsed = false
    private var resendTimerTask: Task<Void, Never>?

    var canResend: Bool { secondsSinceSend >= 10 }
    var secondsLeft: Int { max(0, 10 - secondsSinceSend) }
    // SMS fallback only makes sense for a phone contact — mirrors the web
    // LoginPage's showSmsOption, which gates the same way.
    var showSmsOption: Bool { contactType == "MOBILE" && !smsOptionUsed && otpSendCount >= 2 && canResend }

    private let repo = AuthRepository.shared
    private let tokenManager = TokenManager.shared

    var contact = ""
    var contactType = "MOBILE"   // "MOBILE" | "EMAIL" — mirrors the web LoginPage's toggle

    func sendOtp(contact: String, contactType: String = "MOBILE") {
        self.contact = contact
        self.contactType = contactType
        isLoading = true; error = nil
        Task {
            do {
                try await repo.sendOtp(contact: contact, contactType: contactType)
                otpSent = true
                otpSendCount = 1
                startResendTimer()
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func resendOtp() {
        isLoading = true; error = nil
        Task {
            do {
                try await repo.sendOtp(contact: contact, contactType: contactType)
                otpSendCount += 1
                startResendTimer()
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func sendOtpViaSms() {
        isLoading = true; error = nil
        Task {
            do {
                try await repo.sendOtp(contact: contact, contactType: contactType, channel: "SMS")
                smsOptionUsed = true
                otpSendCount += 1
                startResendTimer()
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    private func startResendTimer() {
        resendTimerTask?.cancel()
        secondsSinceSend = 0
        resendTimerTask = Task { [weak self] in
            while let self = self, self.secondsSinceSend < 10 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                self.secondsSinceSend += 1
            }
        }
    }

    func verifyOtp(otp: String) {
        isLoading = true; error = nil
        Task {
            do {
                let resp = try await repo.verifyOtp(contact: contact, otp: otp)
                sessionToken = resp.sessionToken
                isNewUser = resp.newUser
                if !resp.newUser { await login(sessionToken: resp.sessionToken) }
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func register(name: String, email: String?, dateOfBirth: String?,
                  gender: String?, address: String?) {
        guard let token = sessionToken else { return }
        isLoading = true; error = nil
        Task {
            do {
                let auth = try await repo.register(name: name, email: email,
                                                   sessionToken: token, dateOfBirth: dateOfBirth,
                                                   gender: gender, address: address)
                tokenManager.save(token: auth.accessToken, user: auth.user)
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    private func login(sessionToken: String) async {
        do {
            let auth = try await repo.login(sessionToken: sessionToken)
            tokenManager.save(token: auth.accessToken, user: auth.user)
        } catch { self.error = error.localizedDescription }
    }

    func sendAdminOtp(contact: String) {
        self.contact = contact
        isLoading = true; error = nil
        Task {
            do {
                try await repo.sendOtp(contact: contact)
                otpSent = true
                otpSendCount = 1
                startResendTimer()
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func adminLogin(otp: String) {
        isLoading = true; error = nil
        Task {
            do {
                let auth = try await repo.adminLogin(contact: contact, otp: otp)
                tokenManager.save(token: auth.accessToken, user: auth.user)
            } catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    func resetOtpState() {
        resendTimerTask?.cancel()
        otpSent = false
        sessionToken = nil
        isNewUser = false
        error = nil
        contact = ""
        contactType = "MOBILE"
        otpSendCount = 0
        secondsSinceSend = 0
        smsOptionUsed = false
    }
}
