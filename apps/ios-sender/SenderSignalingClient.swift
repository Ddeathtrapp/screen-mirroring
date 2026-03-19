import Foundation

enum SenderSignalingClientError: LocalizedError, Equatable {
  case invalidURL(String)

  var errorDescription: String? {
    switch self {
    case .invalidURL(let value):
      return "Invalid signaling URL: \(value)"
    }
  }
}

final class SenderSignalingClient {
  var onLifecycleEvent: ((SenderSignalingLifecycleEvent) -> Void)?
  var onMessage: ((SenderSignalingMessage) -> Void)?

  private let sessionTicket: SenderSessionTicket
  private let urlSession: URLSession
  private let jsonEncoder = JSONEncoder()
  private var webSocketTask: URLSessionWebSocketTask?
  private var receiveTask: Task<Void, Never>?
  private var heartbeatTask: Task<Void, Never>?
  private var didFinish = false
  private var disconnectRequested = false

  init(sessionTicket: SenderSessionTicket, urlSession: URLSession = .shared) {
    self.sessionTicket = sessionTicket
    self.urlSession = urlSession
  }

  func connect() throws {
    guard webSocketTask == nil else {
      return
    }

    guard let webSocketURL = makeWebSocketURL(from: sessionTicket.signalingURL) else {
      throw SenderSignalingClientError.invalidURL(sessionTicket.signalingURL.absoluteString)
    }

    didFinish = false
    disconnectRequested = false

    onLifecycleEvent?(.connecting)

    let task = urlSession.webSocketTask(with: webSocketURL)
    webSocketTask = task
    task.resume()

    receiveTask = Task { [weak self] in
      await self?.receiveLoop()
    }
  }

  func disconnect(reason: String = "sender disconnect") {
    disconnectRequested = true
    heartbeatTask?.cancel()
    heartbeatTask = nil
    receiveTask?.cancel()
    receiveTask = nil
    webSocketTask?.cancel(with: .goingAway, reason: Data(reason.utf8))
    finish(expected: true, reason: reason)
  }

  private func receiveLoop() async {
    guard let webSocketTask else {
      finish(expected: disconnectRequested, reason: "Signaling disconnected.")
      return
    }

    do {
      while !Task.isCancelled {
        let message = try await webSocketTask.receive()
        guard let parsed = parse(message) else {
          continue
        }

        handle(parsed)
      }

      finish(expected: disconnectRequested, reason: "Signaling disconnected.")
    } catch {
      if disconnectRequested || Task.isCancelled {
        finish(expected: true, reason: "Signaling disconnected.")
      } else {
        let reason = error.localizedDescription
        finish(expected: false, reason: reason)
      }
    }
  }

  private func handle(_ message: SenderSignalingMessage) {
    notifyOnMainActor {
      self.onMessage?(message)
    }

    switch message {
    case .sessionJoined:
      notifyOnMainActor {
        self.onLifecycleEvent?(.connected)
      }
      // TODO(WebRTC): this is where offer/answer wiring will start once media publishing exists.
      startHeartbeatIfNeeded()
    case .sessionError(_, let error):
      let reason = "\(error.code): \(error.message)"
      disconnectRequested = true
      heartbeatTask?.cancel()
      heartbeatTask = nil
      webSocketTask?.cancel(with: .goingAway, reason: Data(reason.utf8))
      finish(expected: false, reason: reason)
    case .sessionState, .signalOffer, .signalAnswer, .signalIceCandidate, .unknown:
      break
    }
  }

  private func startHeartbeatIfNeeded() {
    guard heartbeatTask == nil else {
      return
    }

    // TODO(Signaling): revisit heartbeat cadence once durable session continuity exists on the backend.
    heartbeatTask = Task { [weak self] in
      await self?.heartbeatLoop()
    }
  }

  private func heartbeatLoop() async {
    while !Task.isCancelled {
      do {
        try await Task.sleep(nanoseconds: 30_000_000_000)
        try await send(SenderSignalingHeartbeatMessage(
          sessionId: sessionTicket.id,
          timestamp: ISO8601DateFormatter().string(from: Date())
        ))
      } catch {
        if Task.isCancelled || disconnectRequested {
          return
        }

        finish(expected: false, reason: error.localizedDescription)
        return
      }
    }
  }

  private func send<T: Encodable>(_ value: T) async throws {
    guard let webSocketTask else {
      throw SenderSignalingClientError.invalidURL(sessionTicket.signalingURL.absoluteString)
    }

    let data = try jsonEncoder.encode(value)
    try await webSocketTask.send(.data(data))
  }

  private func parse(_ message: URLSessionWebSocketTask.Message) -> SenderSignalingMessage? {
    let data: Data
    switch message {
    case .string(let string):
      data = Data(string.utf8)
    case .data(let payload):
      data = payload
    @unknown default:
      return nil
    }

    return SenderSignalingMessage.decode(from: data)
  }

  private func finish(expected: Bool, reason: String?) {
    guard !didFinish else {
      return
    }

    didFinish = true
    heartbeatTask?.cancel()
    heartbeatTask = nil
    receiveTask?.cancel()
    receiveTask = nil
    webSocketTask = nil

    notifyOnMainActor {
      if expected {
        self.onLifecycleEvent?(.disconnected)
      } else {
        self.onLifecycleEvent?(.failed(reason ?? "Signaling failed."))
      }
    }
  }

  private func makeWebSocketURL(from url: URL) -> URL? {
    guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
      return nil
    }

    switch components.scheme?.lowercased() {
    case "http":
      components.scheme = "ws"
    case "https":
      components.scheme = "wss"
    case "ws", "wss":
      break
    default:
      components.scheme = "ws"
    }

    return components.url
  }

  private func notifyOnMainActor(_ block: @escaping () -> Void) {
    DispatchQueue.main.async(execute: block)
  }
}
