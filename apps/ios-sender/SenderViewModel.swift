import Combine
import Foundation

@MainActor
final class SenderViewModel: ObservableObject {
  @Published var pairingCode: String = ""
  @Published var senderName: String = "iPhone Sender"
  @Published var connectionState: SenderConnectionState = .idle
  @Published var statusMessage: String = "Enter a pairing code to begin."

  var canConnect: Bool {
    !normalizedPairingCode.isEmpty && connectionState.isInteractive
  }

  var normalizedPairingCode: String {
    pairingCode.replacingOccurrences(of: " ", with: "")
  }

  func updatePairingCode(_ value: String) {
    pairingCode = value
    if normalizedPairingCode.isEmpty {
      connectionState = .idle
      statusMessage = "Enter a pairing code to begin."
    } else if connectionState == .idle || connectionState == .failed {
      connectionState = .readyToConnect
      statusMessage = "Ready to connect to the receiver."
    }
  }

  func connectTapped() {
    guard canConnect else { return }

    connectionState = .connecting
    statusMessage = "Placeholder connect action. TODO(Signaling): talk to backend."
  }

  func disconnectTapped() {
    connectionState = .disconnecting
    statusMessage = "Placeholder disconnect action. TODO(Signaling): end the session."
    connectionState = normalizedPairingCode.isEmpty ? .idle : .readyToConnect
  }

  func markConnected() {
    connectionState = .connected
    statusMessage = "Connected placeholder state. TODO(WebRTC): publish sender media."
  }

  func markFailed(_ message: String) {
    connectionState = .failed
    statusMessage = message
  }
}
