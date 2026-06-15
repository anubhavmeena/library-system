import Foundation

struct AuthRepository {
    static let shared = AuthRepository()
    private let api = APIClient.shared

    func sendOtp(contact: String) async throws {
        let req = SendOtpRequest(contact: contact)
        let _: AnyCodable = try await api.request(.sendOtp(req))
    }

    func verifyOtp(contact: String, otp: String) async throws -> OtpVerifyResponse {
        let req = VerifyOtpRequest(contact: contact, otp: otp)
        return try await api.request(.verifyOtp(req))
    }

    func register(name: String, email: String?, sessionToken: String,
                  dateOfBirth: String?, gender: String?, address: String?) async throws -> AuthResponse {
        let req = RegisterRequest(name: name, email: email, sessionToken: sessionToken,
                                  dateOfBirth: dateOfBirth, gender: gender, address: address)
        return try await api.request(.register(req))
    }

    func login(sessionToken: String) async throws -> AuthResponse {
        return try await api.request(.login(LoginRequest(sessionToken: sessionToken)))
    }

    func adminLogin(contact: String, otp: String) async throws -> AuthResponse {
        let req = AdminLoginRequest(contact: contact, otp: otp)
        return try await api.request(.adminLogin(req))
    }
}
