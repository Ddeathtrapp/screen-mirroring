import Foundation

struct SenderBackendConfiguration: Equatable {
  let baseURL: URL

  init(baseURLString: String) throws {
    let trimmed = baseURLString.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
      throw SenderBackendError.invalidBaseURL(trimmed)
    }

    if trimmed.lowercased().hasPrefix("http://") || trimmed.lowercased().hasPrefix("https://") {
      guard let url = URL(string: trimmed) else {
        throw SenderBackendError.invalidBaseURL(trimmed)
      }
      self.baseURL = Self.normalized(url)
      return
    }

    guard let url = URL(string: "http://\(trimmed)") else {
      throw SenderBackendError.invalidBaseURL(trimmed)
    }
    self.baseURL = Self.normalized(url)
  }

  init(baseURL: URL) {
    self.baseURL = Self.normalized(baseURL)
  }

  private static func normalized(_ url: URL) -> URL {
    if url.absoluteString.hasSuffix("/") {
      return url
    }

    return URL(string: url.absoluteString + "/") ?? url
  }
}

struct SenderBackendClaimRequest: Encodable {
  let senderName: String?
}

struct SenderBackendClaimResponse: Decodable {
  let sessionId: String
  let senderToken: String
  let state: String
  let signalingUrl: String
}

struct SenderBackendProtocolError: Decodable, Equatable {
  let code: String
  let message: String
}

enum SenderBackendError: LocalizedError, Equatable {
  case invalidBaseURL(String)
  case invalidPairingCode
  case invalidResponse
  case serverRejected(SenderBackendProtocolError)
  case httpStatus(Int)

  var errorDescription: String? {
    switch self {
    case .invalidBaseURL(let value):
      return "Invalid backend URL: \(value)"
    case .invalidPairingCode:
      return "Pairing code is required."
    case .invalidResponse:
      return "Backend returned an unexpected response."
    case .serverRejected(let error):
      return "\(error.code): \(error.message)"
    case .httpStatus(let status):
      return "Backend returned HTTP \(status)."
    }
  }
}
