# iOS Sender

DEV foundation for the future ReplayKit-based sender app.

This is currently a source-only SwiftUI scaffold, not a finished Xcode project or a working sender implementation.

## What is here

- SwiftUI app shell
- minimal backend client and request/response models that can claim a pairing code from the existing backend
- backend URL, pairing-code, and sender-name inputs
- honest loading, success, and failure states for the claim flow
- a stored sender session ticket with `sessionId`, `senderToken`, backend `state`, and `signalingURL` for the next native step
- notes for the future ReplayKit Broadcast Upload Extension and WebRTC integration

## What is not here yet

- signaling socket
- sender auth/session continuity after the claim step
- ReplayKit screen capture
- WebRTC media capture or transport
- native backend integration beyond the claim step
- any real end-to-end iOS streaming

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
  SenderViewModel.swift
```

## Next step

The next turn should add the sender signaling socket and carry the stored session ticket forward, but still stop short of ReplayKit and media publishing.
