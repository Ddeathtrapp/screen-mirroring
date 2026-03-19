# iOS Sender Architecture Notes

This folder is intentionally a small SwiftUI foundation for the future iOS sender app.

## v1 direction

- Use a single SwiftUI app target for the sender shell.
- Keep pairing/session UI in the main app target.
- Add ReplayKit capture later through a Broadcast Upload Extension.
- Add WebRTC later only after the signaling and pairing flow is in place.

## Proposed module split

- `ScreenMirroringSenderApp.swift`: app entry point.
- `SenderHomeView.swift`: main UI shell and placeholder pairing flow.
- `SenderViewModel.swift`: UI state and future backend orchestration.
- `SenderModels.swift`: small enums and data types used by the app shell.

## TODOs for later

- `TODO(ReplayKit)`: add a Broadcast Upload Extension target and connect it to screen capture.
- `TODO(WebRTC)`: add the sender peer connection, ICE handling, and media publishing.
- `TODO(Signaling)`: replace placeholder connect/disconnect actions with backend-backed pairing and signaling.
