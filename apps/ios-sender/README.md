# iOS Sender

DEV foundation for the future ReplayKit-based sender app.

This is currently a source-only SwiftUI scaffold, not a finished Xcode project or a working sender implementation.

## What is here

- SwiftUI app shell
- minimal backend client and request/response models that can claim a pairing code from the existing backend
- backend URL, pairing-code, and sender-name inputs
- honest loading, success, and failure states for the claim flow
- a stored sender session ticket with `sessionId`, `senderToken`, backend `state`, and `signalingURL`
- a source-only signaling client that can join the backend WebSocket as the sender and report lifecycle events
- honest signaling status in the UI after claim, connect, and disconnect
- notes for the future ReplayKit Broadcast Upload Extension and WebRTC integration

## What is not here yet

- sender auth/session continuity beyond the basic signaling join
- ReplayKit screen capture
- WebRTC media capture or transport
- offer/answer and ICE handling
- native backend integration beyond the claim and signaling join steps
- any real end-to-end iOS streaming
- a finished Xcode project/workspace for actually launching this shell

## Suggested structure

```text
apps/ios-sender/
  README.md
  iOSSenderArchitecture.md
  SenderBackendClient.swift
  SenderBackendModels.swift
  ScreenMirroringSenderApp.swift
  SenderHomeView.swift
  SenderModels.swift
  SenderSignalingClient.swift
  SenderSignalingModels.swift
  SenderViewModel.swift
```

## Source-level flow

Once an Xcode project/workspace is added, this shell is intended to support the following manual flow:

1. Start the backend.
2. Launch the iOS sender shell.
3. Enter the backend URL and a receiver-created pairing code.
4. Tap `Claim session`.
5. Tap `Connect signaling`.
6. Keep the sender shell visible while watching the state move through `claiming`, `claimed`, `connecting-signaling`, and `signaling-connected`.
7. Tap `Disconnect signaling`.
8. Tap `Clear claim` only if you want to remove the stored session ticket and start over.

## Next step

Add the first real WebRTC signaling actions on top of the existing sender socket, but still stop short of ReplayKit and media publishing.
