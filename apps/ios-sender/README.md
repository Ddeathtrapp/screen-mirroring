# iOS Sender

DEV foundation for the future ReplayKit-based sender app.

This is currently a source-only SwiftUI scaffold, not a finished Xcode project or a working sender implementation.

## What is here

- SwiftUI app shell
- placeholder pairing-code entry UI
- placeholder connection state model
- notes for the future ReplayKit Broadcast Upload Extension and WebRTC integration

## What is not here yet

- real pairing/signaling client
- ReplayKit screen capture
- WebRTC media capture or transport
- native backend integration

## Suggested structure

```text
apps/ios-sender/
  README.md
  iOSSenderArchitecture.md
  ScreenMirroringSenderApp.swift
  SenderHomeView.swift
  SenderModels.swift
  SenderViewModel.swift
```

## Next step

The next turn should add the actual signaling client and wire this shell into the shared backend contract.
