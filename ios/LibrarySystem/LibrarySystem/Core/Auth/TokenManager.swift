import Foundation
import Combine

final class TokenManager: ObservableObject {
    static let shared = TokenManager()

    @Published private(set) var token: String?
    @Published private(set) var currentUser: User?

    private init() {
        token = KeychainHelper.read(key: "jwt")
        if let data = KeychainHelper.readData(key: "user") {
            currentUser = try? JSONDecoder().decode(User.self, from: data)
        }
    }

    func save(token: String, user: User) {
        KeychainHelper.save(token, key: "jwt")
        if let data = try? JSONEncoder().encode(user) {
            KeychainHelper.saveData(data, key: "user")
        }
        self.token = token
        self.currentUser = user
    }

    func clear() {
        KeychainHelper.delete(key: "jwt")
        KeychainHelper.delete(key: "user")
        token = nil
        currentUser = nil
    }
}
