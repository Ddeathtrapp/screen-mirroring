import Foundation

enum SenderSignalingLifecycleEvent: Equatable {
  case connecting
  case connected
  case disconnected
  case failed(String)
}

enum SenderSignalingMessage: Equatable {
  case sessionJoined(sessionId: String, role: String, state: String)
  case sessionState(sessionId: String, state: String, reason: String?)
  case sessionError(sessionId: String?, error: SenderBackendProtocolError)
  case signalOffer(sessionId: String, sdp: String)
  case signalAnswer(sessionId: String, sdp: String)
  case signalIceCandidate(sessionId: String, candidate: SenderSignalingIceCandidate)
  case unknown(String)
}

struct SenderSignalingIceCandidate: Decodable, Equatable {
  let candidate: String?
  let sdpMid: String?
  let sdpMLineIndex: Int?
  let usernameFragment: String?
}

struct SenderSignalingHeartbeatMessage: Encodable {
  let type: String = "session.heartbeat"
  let sessionId: String
  let timestamp: String
}

struct SenderSignalingEndMessage: Encodable {
  let type: String = "session.end"
  let sessionId: String
  let reason: String?
}

private struct SenderSignalingEnvelope: Decodable {
  let type: String
  let sessionId: String?
  let role: String?
  let state: String?
  let reason: String?
  let sdp: String?
  let candidate: SenderSignalingIceCandidate?
  let error: SenderBackendProtocolError?
}

extension SenderSignalingMessage {
  static func decode(from data: Data) -> SenderSignalingMessage? {
    guard let envelope = try? JSONDecoder().decode(SenderSignalingEnvelope.self, from: data) else {
      return nil
    }

    switch envelope.type {
    case "session.joined":
      guard let sessionId = envelope.sessionId, let role = envelope.role, let state = envelope.state else {
        return nil
      }
      return .sessionJoined(sessionId: sessionId, role: role, state: state)
    case "session.state":
      guard let sessionId = envelope.sessionId, let state = envelope.state else {
        return nil
      }
      return .sessionState(sessionId: sessionId, state: state, reason: envelope.reason)
    case "session.error":
      guard let error = envelope.error else {
        return nil
      }
      return .sessionError(sessionId: envelope.sessionId, error: error)
    case "signal.offer":
      guard let sessionId = envelope.sessionId, let sdp = envelope.sdp else {
        return nil
      }
      return .signalOffer(sessionId: sessionId, sdp: sdp)
    case "signal.answer":
      guard let sessionId = envelope.sessionId, let sdp = envelope.sdp else {
        return nil
      }
      return .signalAnswer(sessionId: sessionId, sdp: sdp)
    case "signal.ice-candidate":
      guard let sessionId = envelope.sessionId, let candidate = envelope.candidate else {
        return nil
      }
      return .signalIceCandidate(sessionId: sessionId, candidate: candidate)
    default:
      return .unknown(envelope.type)
    }
  }
}
