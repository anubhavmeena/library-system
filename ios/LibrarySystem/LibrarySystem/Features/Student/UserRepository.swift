import Foundation

struct UserRepository {
    static let shared = UserRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    func getProfile() async throws -> User {
        try await api.request(.getProfile, token: token)
    }

    func getAdminContact() async throws -> AdminContact {
        try await api.request(.getAdminContact, token: token)
    }

    func updateProfile(name: String, fatherName: String?, address: String?,
                       gender: String?, dateOfBirth: String?, email: String?) async throws -> User {
        let req = UpdateProfileRequest(name: name, fatherName: fatherName, address: address,
                                       gender: gender, dateOfBirth: dateOfBirth, email: email)
        return try await api.request(.updateProfile(req), token: token)
    }

    func uploadPhoto(data: Data) async throws -> String {
        let userId = TokenManager.shared.currentUser?.id ?? "unknown"
        return try await api.uploadMultipart(
            path: "users/me/photo",
            fieldName: "file",
            fileName: "photo_\(userId).jpg",
            mimeType: "image/jpeg",
            data: data,
            token: token
        )
    }

    func uploadAadhaar(data: Data) async throws -> String {
        let userId = TokenManager.shared.currentUser?.id ?? "unknown"
        return try await api.uploadMultipart(
            path: "users/me/aadhaar",
            fieldName: "file",
            fileName: "aadhaar_\(userId).jpg",
            mimeType: "image/jpeg",
            data: data,
            token: token
        )
    }
}
