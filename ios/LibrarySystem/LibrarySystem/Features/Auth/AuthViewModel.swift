import Foundation
import Combine

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var error: String?
    @Published var otpSent = false
    @Published var sessionToken: String?
    @Published var isNewUser = false

    private let repo = AuthRepository.shared
    private let tokenManager = TokenManager.shared

    var contact = ""

    func sendOtp(contact: String) {
        self.contact = contact
        isLoading = true; error = nil
        Task {
            do {
                try await repo.sendOtp(contact: contact)
                otpSent = true
            } catch { self.error = error.localizedDescription }
            isLoading = false
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
        otpSent = false
        sessionToken = nil
        isNewUser = false
        error = nil
        contact = ""
    }
}
