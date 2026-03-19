import Foundation

enum SenderConnectionState: String, CaseIterable, Identifiable {
  case idle
  case waitingForPairingCode
  case readyToConnect
  case connecting
  case connected
  case disconnecting
  case failed

  var id: String { rawValue }

  var title: String {
    switch self {
    case .idle: return "Idle"
    case .waitingForPairingCode: return "Waiting for pairing code"
    case .readyToConnect: return "Ready to connect"
    case .connecting: return "Connecting"
    case .connected: return "Connected"
    case .disconnecting: return "Disconnecting"
    case .failed: return "Failed"
    }
  }

  var isInteractive: Bool {
    switch self {
    case .idle, .waitingForPairingCode, .readyToConnect, .failed:
      return true
    case .connecting, .connected, .disconnecting:
      return false
    }
  }
}
