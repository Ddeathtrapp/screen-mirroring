import Combine
import Foundation

@MainActor
final class SenderViewModel: ObservableObject {
  @Published var backendBaseURL: String = "http://localhost:8787"
  @Published var pairingCode: String = ""
  @Published var senderName: String = "iPhone Sender"
  @Published var connectionState: SenderConnectionState = .idle
  @Published var statusMessage: String = "Enter a backend URL and pairing code to begin."
  @Published var sessionTicket: SenderSessionTicket?

  var canClaim: Bool {
    !normalizedPairingCode.isEmpty && !normalizedBackendURL.isEmpty && connectionState.isInteractive
  }

  var normalizedPairingCode: String {
    pairingCode.components(separatedBy: .whitespacesAndNewlines).joined()
  }

  var normalizedBackendURL: String {
    backendBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  var normalizedSenderName: String {
    senderName.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  func updateBackendURL(_ value: String) {
    backendBaseURL = value
    guard connectionState != .claiming else { return }

    sessionTicket = nil
    refreshIdleState()
  }

  func updatePairingCode(_ value: String) {
    pairingCode = value
    guard connectionState != .claiming else { return }

    sessionTicket = nil
    refreshIdleState()
  }

  func claimTapped() {
    guard canClaim else { return }

    Task {
      await claimCurrentSession()
    }
  }

  func disconnectTapped() {
    let hasInputs = !normalizedBackendURL.isEmpty && !normalizedPairingCode.isEmpty
    sessionTicket = nil
    refreshIdleState()
    if hasInputs {
      statusMessage = "Claim cleared. TODO(Signaling): add sender auth/session continuity and teardown later."
    }
  }

  func markFailed(_ message: String) {
    connectionState = .failed
    statusMessage = message
  }

  private func claimCurrentSession() async {
    do {
      connectionState = .claiming
      statusMessage = "Claiming pairing code..."

      let configuration = try SenderBackendConfiguration(baseURLString: normalizedBackendURL)
      let client = SenderBackendClient(configuration: configuration)
      let ticket = try await client.claimSenderSession(
        pairingCode: normalizedPairingCode,
        senderName: normalizedSenderName.isEmpty ? nil : normalizedSenderName
      )

      sessionTicket = ticket
      connectionState = .claimed
      statusMessage = "Claimed session \(ticket.id). TODO(Signaling): open the sender socket next."
    } catch {
      sessionTicket = nil
      connectionState = .failed
      statusMessage = error.localizedDescription
    }
  }

  private func refreshIdleState() {
    if normalizedBackendURL.isEmpty || normalizedPairingCode.isEmpty {
      connectionState = .idle
      statusMessage = "Enter a backend URL and pairing code to begin."
      return
    }

    connectionState = .readyToClaim
    statusMessage = "Ready to claim the pairing code."
  }
}
