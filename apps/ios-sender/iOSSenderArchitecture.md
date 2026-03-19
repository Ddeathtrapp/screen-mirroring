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
- `ScreenMirroringSenderApp.swift`: app entry point.
- `SenderHomeView.swift`: main UI shell for backend URL entry, pairing-code claim, and honest status display.
- `SenderViewModel.swift`: UI state, claim orchestration, and temporary session-ticket storage.
- `SenderModels.swift`: small enums and data types used by the app shell.

## TODOs for later

- `TODO(Signaling)`: add the sender socket and carry the session token after claim.
- `TODO(ReplayKit)`: add a Broadcast Upload Extension target and connect it to screen capture.
- `TODO(WebRTC)`: add the sender peer connection, ICE handling, and media publishing.
