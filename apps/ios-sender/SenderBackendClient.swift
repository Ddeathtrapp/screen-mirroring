import Foundation

final class SenderBackendClient {
  private let configuration: SenderBackendConfiguration
  private let jsonEncoder = JSONEncoder()
  private let jsonDecoder = JSONDecoder()

  init(configuration: SenderBackendConfiguration) {
    self.configuration = configuration
  }

  func claimSenderSession(pairingCode: String, senderName: String?) async throws -> SenderSessionTicket {
    let cleanedPairingCode = pairingCode.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !cleanedPairingCode.isEmpty else {
      throw SenderBackendError.invalidPairingCode
    }

    let endpointURL = try claimEndpointURL(for: cleanedPairingCode)
    var request = URLRequest(url: endpointURL)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")

    let cleanedSenderName = senderName?.trimmingCharacters(in: .whitespacesAndNewlines)
    let payload = SenderBackendClaimRequest(senderName: cleanedSenderName?.isEmpty == true ? nil : cleanedSenderName)
    request.httpBody = try jsonEncoder.encode(payload)

    let (data, response) = try await URLSession.shared.data(for: request)
    guard let httpResponse = response as? HTTPURLResponse else {
      throw SenderBackendError.invalidResponse
    }

    guard (200...299).contains(httpResponse.statusCode) else {
      if let backendError = try? jsonDecoder.decode(SenderBackendProtocolError.self, from: data) {
        throw SenderBackendError.serverRejected(backendError)
      }

      throw SenderBackendError.httpStatus(httpResponse.statusCode)
    }

    let decoded = try jsonDecoder.decode(SenderBackendClaimResponse.self, from: data)
    guard let signalingURL = URL(string: decoded.signalingUrl, relativeTo: configuration.baseURL)?.absoluteURL else {
      throw SenderBackendError.invalidResponse
    }

    return SenderSessionTicket(
      id: decoded.sessionId,
      pairingCode: cleanedPairingCode,
      senderName: payload.senderName,
      senderToken: decoded.senderToken,
      state: decoded.state,
      signalingURL: signalingURL
    )
  }

  private func claimEndpointURL(for pairingCode: String) throws -> URL {
    guard let url = URL(string: "v1/pairing-codes/\(pairingCode)/claim", relativeTo: configuration.baseURL)?.absoluteURL else {
      throw SenderBackendError.invalidResponse
    }

    return url
  }
}
