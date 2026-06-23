import Foundation

struct GalleryRepository {
    static let shared = GalleryRepository()
    private let api = APIClient.shared
    private var token: String? { TokenManager.shared.token }

    func getAll() async throws -> [GalleryPhoto] {
        try await api.request(.getGallery, token: token)
    }

    func upload(imageData: Data, caption: String?) async throws -> GalleryPhoto {
        let boundary = "Boundary-\(UUID().uuidString)"
        guard let url = URL(string: "https://targetzone.co.in/api/gallery") else {
            throw APIError.invalidURL
        }
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let t = token { request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }

        var body = Data()
        let crlf = "\r\n"
        func append(_ s: String) { body.append(s.data(using: .utf8)!) }

        append("--\(boundary)\(crlf)")
        append("Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\(crlf)")
        append("Content-Type: image/jpeg\(crlf)\(crlf)")
        body.append(imageData)
        append("\(crlf)")

        if let cap = caption, !cap.isEmpty {
            append("--\(boundary)\(crlf)")
            append("Content-Disposition: form-data; name=\"caption\"\(crlf)\(crlf)")
            append("\(cap)\(crlf)")
        }

        append("--\(boundary)--\(crlf)")
        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode < 300 else {
            throw APIError.serverError("Upload failed")
        }
        let decoder = JSONDecoder()
        let envelope = try decoder.decode(ApiResponse<GalleryPhoto>.self, from: data)
        guard let photo = envelope.data, envelope.success else {
            throw APIError.serverError(envelope.message ?? "Upload failed")
        }
        return photo
    }

    func delete(id: String) async throws {
        try await api.requestVoid(.deleteGalleryPhoto(id: id), token: token)
    }
}
