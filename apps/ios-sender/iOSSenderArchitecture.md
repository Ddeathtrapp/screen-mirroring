# iOS Sender Architecture Notes

This folder is intentionally a small SwiftUI foundation for the future iOS sender app.

## v1 direction

- Use a single SwiftUI app target for the sender shell.
- Keep pairing/session UI in the main app target.
- Add ReplayKit capture later through a Broadcast Upload Extension.
- Add WebRTC later only after the signaling and pairing flow is in place.

## Proposed module split

- `SenderBackendClient.swift`: minimal backend claim client and claim response parsing.
- `SenderBackendModels.swift`: backend configuration, claim models, and backend error types.
- `SenderSignalingClient.swift`: source-only sender WebSocket client for the backend signaling URL returned by claim.
- `SenderSignalingModels.swift`: signaling lifecycle and message parsing helpers.
- `ScreenMirroringSenderApp.swift`: app entry point.
- `SenderHomeView.swift`: main UI shell for backend URL entry, pairing-code claim, signaling connect/disconnect, and honest status display.
- `SenderViewModel.swift`: UI state, claim orchestration, signaling connection state, and temporary session-ticket storage.
- `SenderModels.swift`: connection state and session-ticket model types.

## TODOs for later

- `TODO(Signaling)`: add offer/answer and ICE handling on top of the existing sender socket.
- `TODO(Auth)`: persist sender token and session state for the next stage.
- `TODO(ReplayKit)`: add a Broadcast Upload Extension target and connect it to screen capture.
- `TODO(WebRTC)`: add the sender peer connection, ICE handling, and media publishing.
