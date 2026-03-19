import Foundation

enum SenderConnectionState: String, CaseIterable, Identifiable {
  case idle
  case readyToClaim
  case claiming
  case claimed
  case failed

  var id: String { rawValue }

  var title: String {
    switch self {
    case .idle: return "Idle"
    case .readyToClaim: return "Ready to claim"
    case .claiming: return "Claiming"
    case .claimed: return "Claimed"
    case .failed: return "Failed"
    }
  }

  var isInteractive: Bool {
    switch self {
    case .idle, .readyToClaim, .failed:
      return true
    case .claiming, .claimed:
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
