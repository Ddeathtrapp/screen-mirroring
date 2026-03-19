import Foundation
import SwiftUI

@MainActor
final class SenderViewModel: ObservableObject {
  @Published var backendBaseURL: String = "http://localhost:8787"
  @Published var pairingCode: String = ""
  @Published var senderName: String = ""
  @Published private(set) var connectionState: SenderConnectionState = .idle
  @Published private(set) var statusMessage: String = "Enter a backend URL and pairing code to claim a session."
  @Published private(set) var signalingStatusMessage: String = "No claimed session yet."
  @Published private(set) var sessionTicket: SenderSessionTicket?

  private var signalingClient: SenderSignalingClient?

  var canClaim: Bool {
    sessionTicket == nil
      && signalingClient == nil
      && [.idle, .readyToClaim, .signalingFailed, .failed].contains(connectionState)
      && !normalizedBackendURL.isEmpty
      && !normalizedPairingCode.isEmpty
  }

  var canConnectSignaling: Bool {
    sessionTicket != nil
      && signalingClient == nil
      && [.claimed, .signalingFailed].contains(connectionState)
  }

  var canDisconnectSignaling: Bool {
    signalingClient != nil
      || [.connectingSignaling, .signalingConnected, .disconnectingSignaling, .signalingFailed].contains(connectionState)
  }

  var claimedSessionSummary: String {
    guard let sessionTicket else {
      return "No claimed session yet."
    }

    let senderNameText = sessionTicket.senderName ?? "unnamed sender"
    return "Session \(sessionTicket.id) | code \(sessionTicket.pairingCode) | \(senderNameText)"
  }

  var normalizedPairingCode: String {
    pairingCode
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .replacingOccurrences(of: " ", with: "")
      .uppercased()
  }

  var normalizedBackendURL: String {
    backendBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  var normalizedSenderName: String? {
    let cleaned = senderName.trimmingCharacters(in: .whitespacesAndNewlines)
    return cleaned.isEmpty ? nil : cleaned
  }

  func updateBackendURL(_ value: String) {
    backendBaseURL = value
    clearClaimedSessionIfNeeded(reason: "Backend URL changed. Claim a new session for the new backend.")
    refreshReadyStateIfNeeded()
  }

  func updatePairingCode(_ value: String) {
    pairingCode = value
    clearClaimedSessionIfNeeded(reason: "Pairing code changed. Claim a new session for the new code.")
    refreshReadyStateIfNeeded()
  }

  func claimTapped() {
    guard canClaim else {
      return
    }

    connectionState = .claiming
    statusMessage = "Claiming pairing code \(normalizedPairingCode)..."
    signalingStatusMessage = "Claim request in flight."

    Task {
      await claimCurrentSession()
    }
  }

  func connectSignalingTapped() {
    guard let ticket = sessionTicket else {
      connectionState = .failed
      statusMessage = "Claim a session before connecting signaling."
      signalingStatusMessage = "No claimed session available."
      return
    }

    guard signalingClient == nil else {
      return
    }

    let client = SenderSignalingClient(sessionTicket: ticket)
    signalingClient = client
    connectionState = .connectingSignaling
    statusMessage = "Connecting signaling for session \(ticket.id)..."
    signalingStatusMessage = "Opening signaling socket at \(ticket.signalingURL.absoluteString)"

    client.onLifecycleEvent = { [weak self] event in
      Task { [weak self] in
        guard let self else {
          return
        }

        await MainActor.run {
          self.handleSignalingLifecycleEvent(event)
        }
      }
    }

    client.onMessage = { [weak self] message in
      Task { [weak self] in
        guard let self else {
          return
        }

        await MainActor.run {
          self.handleSignalingMessage(message)
        }
      }
    }

    do {
      try client.connect()
    } catch {
      signalingClient = nil
      connectionState = .signalingFailed
      statusMessage = "Failed to open signaling: \(error.localizedDescription)"
      signalingStatusMessage = "Signaling connection failed."
    }
  }

  func disconnectSignalingTapped() {
    guard let client = signalingClient else {
      if sessionTicket == nil {
        refreshReadyStateIfNeeded()
      } else {
        connectionState = .claimed
        statusMessage = "Claim retained; signaling is already disconnected."
        signalingStatusMessage = "Signaling disconnected. Claim retained."
      }
      return
    }

    connectionState = .disconnectingSignaling
    statusMessage = "Disconnecting signaling..."
    signalingStatusMessage = "Closing signaling socket."
    signalingClient = nil
    client.disconnect(reason: "User requested signaling disconnect.")
  }

  func clearClaimTapped() {
    guard sessionTicket != nil || signalingClient != nil else {
      refreshReadyStateIfNeeded()
      return
    }

    let client = signalingClient
    signalingClient = nil
    sessionTicket = nil
    client?.disconnect(reason: "Claim cleared by user.")

    refreshReadyStateIfNeeded()
    if connectionState == .idle {
      statusMessage = "Claim cleared."
      signalingStatusMessage = "No claimed session yet."
    } else {
      statusMessage = "Claim cleared. Ready to claim pairing code \(normalizedPairingCode)."
      signalingStatusMessage = "Ready to claim."
    }
  }

  func markFailed(_ message: String) {
    connectionState = .failed
    statusMessage = message
    signalingStatusMessage = "Failed."
  }

  private func claimCurrentSession() async {
    do {
      let configuration = try SenderBackendConfiguration(baseURLString: normalizedBackendURL)
      let client = SenderBackendClient(configuration: configuration)
      let ticket = try await client.claimSenderSession(
        pairingCode: normalizedPairingCode,
        senderName: normalizedSenderName
      )

      sessionTicket = ticket
      connectionState = .claimed
      statusMessage = "Claimed session \(ticket.id). Ready to connect signaling."
      signalingStatusMessage = "Claimed session \(ticket.id) but signaling is not connected yet."
    } catch {
      sessionTicket = nil
      signalingClient = nil
      connectionState = .failed
      statusMessage = friendlyMessage(for: error)
      signalingStatusMessage = "Claim failed."
    }
  }

  private func handleSignalingLifecycleEvent(_ event: SenderSignalingLifecycleEvent) {
    switch event {
    case .connecting:
      connectionState = .connectingSignaling
      statusMessage = "Connecting signaling for session \(sessionTicket?.id ?? "")."
      signalingStatusMessage = "Opening signaling socket."
    case .connected:
      connectionState = .signalingConnected
      statusMessage = "Signaling connected for session \(sessionTicket?.id ?? "")."
      signalingStatusMessage = "Signaling connected."
    case .disconnected:
      signalingClient = nil
      connectionState = sessionTicket == nil ? .idle : .claimed
      statusMessage = sessionTicket == nil ? "Signaling disconnected." : "Claim retained; signaling disconnected."
      signalingStatusMessage = sessionTicket == nil ? "No claimed session yet." : "Signaling disconnected. Claim retained."
    case .failed(let reason):
      signalingClient = nil
      connectionState = .signalingFailed
      statusMessage = "Signaling failed: \(reason)"
      signalingStatusMessage = "Signaling failed: \(reason)"
    }
  }

  private func handleSignalingMessage(_ message: SenderSignalingMessage) {
    switch message {
    case .sessionJoined(let sessionId, let role, let state):
      connectionState = .signalingConnected
      statusMessage = "Joined signaling session \(sessionId) as \(role) (\(state))."
      signalingStatusMessage = message.summary
    case .sessionState(let sessionId, let state, let reason):
      if state.lowercased() == "ended" || state.lowercased() == "closed" || state.lowercased() == "terminated" {
        connectionState = sessionTicket == nil ? .idle : .claimed
      }

      if let reason, !reason.isEmpty {
        statusMessage = "Session \(sessionId) state \(state): \(reason)"
      } else {
        statusMessage = "Session \(sessionId) state \(state)."
      }
      signalingStatusMessage = message.summary
    case .sessionError(_, let error):
      connectionState = .signalingFailed
      statusMessage = "Signaling error: \(error.code) - \(error.message)"
      signalingStatusMessage = message.summary
      signalingClient = nil
    case .signalOffer, .signalAnswer, .signalIceCandidate, .unknown:
      signalingStatusMessage = message.summary
    }
  }

  private func clearClaimedSessionIfNeeded(reason: String) {
    guard sessionTicket != nil || signalingClient != nil else {
      return
    }

    let client = signalingClient
    signalingClient = nil
    sessionTicket = nil
    connectionState = normalizedPairingCode.isEmpty ? .idle : .readyToClaim
    statusMessage = reason
    signalingStatusMessage = normalizedPairingCode.isEmpty ? "No claimed session yet." : "Ready to claim \(normalizedPairingCode)."
    client?.disconnect(reason: reason)
  }

  private func refreshReadyStateIfNeeded() {
    guard sessionTicket == nil && signalingClient == nil else {
      return
    }

    if normalizedPairingCode.isEmpty {
      connectionState = .idle
      statusMessage = "Enter a backend URL and pairing code to claim a session."
      signalingStatusMessage = "No claimed session yet."
    } else {
      connectionState = .readyToClaim
      statusMessage = "Ready to claim pairing code \(normalizedPairingCode)."
      signalingStatusMessage = "Ready to claim."
    }
  }

  private func friendlyMessage(for error: Error) -> String {
    if let localized = error as? LocalizedError, let message = localized.errorDescription {
      return message
    }

    return error.localizedDescription
  }
}

private extension SenderSignalingMessage {
  var summary: String {
    switch self {
    case .sessionJoined(let sessionId, let role, let state):
      return "Joined signaling session \(sessionId) as \(role) (\(state))."
    case .sessionState(let sessionId, let state, let reason):
      if let reason, !reason.isEmpty {
        return "Session \(sessionId) state \(state): \(reason)"
      }
      return "Session \(sessionId) state \(state)."
    case .signalOffer(let sessionId, _):
      return "Received offer for session \(sessionId)."
    case .signalAnswer(let sessionId, _):
      return "Received answer for session \(sessionId)."
    case .signalIceCandidate(let sessionId, _):
      return "Received ICE candidate for session \(sessionId)."
    case .sessionError(let sessionId, let error):
      let prefix = sessionId.map { "Session \($0)" } ?? "Signaling"
      return "\(prefix) error: \(error.code) - \(error.message)"
    case .unknown(let type):
      return "Unhandled signaling message \(type)."
    }
  }
}
