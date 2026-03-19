import Foundation

enum SenderConnectionState: String, CaseIterable, Identifiable {
  case idle
  case readyToClaim
  case claiming
  case claimed
  case connectingSignaling
  case signalingConnected
  case signalingFailed
  case disconnectingSignaling
  case failed

  var id: String { rawValue }

  var title: String {
    switch self {
    case .idle: return "Idle"
    case .readyToClaim: return "Ready to claim"
    case .claiming: return "Claiming"
    case .claimed: return "Claimed"
    case .connectingSignaling: return "Connecting signaling"
    case .signalingConnected: return "Signaling connected"
    case .signalingFailed: return "Signaling failed"
    case .disconnectingSignaling: return "Disconnecting signaling"
    case .failed: return "Failed"
    }
  }

  var isInteractive: Bool {
    switch self {
    case .idle, .readyToClaim, .claimed, .signalingFailed, .failed:
      return true
    case .claiming, .connectingSignaling, .signalingConnected, .disconnectingSignaling:
      return false
    }
  }
}

struct SenderSessionTicket: Identifiable, Equatable {
  let id: String
  let pairingCode: String
  let senderName: String?
  let senderToken: String
  let state: String
  let signalingURL: URL
}
