import Foundation

enum APIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case serverError(String)
    case unauthorized
    case decodingError(Error)
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:           return "Invalid URL"
        case .invalidResponse:      return "Invalid server response"
        case .serverError(let msg): return msg
        case .unauthorized:         return "Session expired. Please login again."
        case .decodingError(let e): return "Data error: \(e.localizedDescription)"
        case .networkError(let e):  return e.localizedDescription
        }
    }
}

actor APIClient {
    static let shared = APIClient()

    private let baseURL = URL(string: "https://targetzone.co.in/api/")!

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        return d
    }()

    private init() {}

    private func performRequest(_ endpoint: Endpoint, token: String?) async throws -> Data {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(endpoint.path), resolvingAgainstBaseURL: true) else {
            throw APIError.invalidURL
        }
        if !endpoint.queryItems.isEmpty {
            components.queryItems = endpoint.queryItems
        }
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = endpoint.body

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        if http.statusCode == 401 {
            throw APIError.unauthorized
        }
        return data
    }

    func request<T: Decodable>(_ endpoint: Endpoint, token: String? = nil) async throws -> T {
        let data = try await performRequest(endpoint, token: token)
        do {
            let envelope = try decoder.decode(ApiResponse<T>.self, from: data)
            if let payload = envelope.data, envelope.success {
                return payload
            }
            throw APIError.serverError(envelope.message ?? "Request failed")
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.decodingError(error)
        }
    }

    // For endpoints that return no data payload (just success/message). Decoded
    // separately from request<T> above: the backend's ApiResponse::ok(...) omits
    // the "data" key entirely (skip_serializing_if = "Option::is_none") rather
    // than sending null, so requiring `data` to be present — as request<T> does,
    // correctly, for endpoints that DO promise a payload — would treat every
    // successful void response as a failure and surface its own success message
    // as a thrown error. Here, `envelope.success` alone is the source of truth.
    func requestVoid(_ endpoint: Endpoint, token: String? = nil) async throws {
        let data = try await performRequest(endpoint, token: token)
        do {
            let envelope = try decoder.decode(ApiResponse<AnyCodable>.self, from: data)
            guard envelope.success else {
                throw APIError.serverError(envelope.message ?? "Request failed")
            }
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.decodingError(error)
        }
    }

    // Raw data download (ID card PDF)
    func download(_ endpoint: Endpoint, token: String? = nil) async throws -> Data {
        guard let url = baseURL.appendingPathComponent(endpoint.path) as URL? else {
            throw APIError.invalidURL
        }
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.httpMethod = "GET"
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, _) = try await URLSession.shared.data(for: request)
        return data
    }

    // Multipart upload — returns a key→value map
    func uploadMultipart(path: String, fieldName: String, fileName: String,
                         mimeType: String, data: Data, token: String?) async throws -> String {
        guard let url = URL(string: baseURL.absoluteString + path) else {
            throw APIError.invalidURL
        }
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (responseData, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, http.statusCode == 401 {
            throw APIError.unauthorized
        }
        do {
            let envelope = try decoder.decode(ApiResponse<[String: String]>.self, from: responseData)
            guard envelope.success else {
                throw APIError.serverError(envelope.message ?? "Upload failed")
            }
            // Backend returns {"url": "..."} — photoUrl/aadhaarUrl kept only as a fallback
            // in case an older server build is still returning those keys.
            return envelope.data?["url"] ?? envelope.data?["photoUrl"] ?? envelope.data?["aadhaarUrl"] ?? ""
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.decodingError(error)
        }
    }

    // Multipart upload that decodes the response payload into any Decodable type
    // (e.g. bulk CSV/XLSX import results), rather than the fixed [String: String]
    // map used by uploadMultipart above.
    func uploadMultipartDecoded<T: Decodable>(path: String, fieldName: String, fileName: String,
                                              mimeType: String, data: Data, token: String?) async throws -> T {
        guard let url = URL(string: baseURL.absoluteString + path) else {
            throw APIError.invalidURL
        }
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (responseData, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, http.statusCode == 401 {
            throw APIError.unauthorized
        }
        do {
            let envelope = try decoder.decode(ApiResponse<T>.self, from: responseData)
            if let payload = envelope.data, envelope.success {
                return payload
            }
            throw APIError.serverError(envelope.message ?? "Request failed")
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.decodingError(error)
        }
    }

    // Multipart upload with extra text fields alongside a single optional file part
    // (e.g. name/phone + optional photo). Unlike uploadMultipart/uploadMultipartDecoded
    // above (file-only), this supports arbitrary text fields in the same request.
    func uploadMultipartWithFields<T: Decodable>(path: String, fields: [String: String],
                                                 fileFieldName: String, fileName: String,
                                                 mimeType: String, fileData: Data?,
                                                 token: String?) async throws -> T {
        guard let url = URL(string: baseURL.absoluteString + path) else {
            throw APIError.invalidURL
        }
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        var body = Data()
        for (key, value) in fields {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }
        if let fileData {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(fileFieldName)\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
            body.append(fileData)
            body.append("\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (responseData, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, http.statusCode == 401 {
            throw APIError.unauthorized
        }
        do {
            let envelope = try decoder.decode(ApiResponse<T>.self, from: responseData)
            if let payload = envelope.data, envelope.success {
                return payload
            }
            throw APIError.serverError(envelope.message ?? "Request failed")
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.decodingError(error)
        }
    }
}

// Placeholder for void responses
struct AnyCodable: Codable {}
