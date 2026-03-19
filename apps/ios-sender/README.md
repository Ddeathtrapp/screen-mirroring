# iOS Sender

DEV foundation for the future ReplayKit-based sender app.

A minimal Xcode project and workspace now live in `apps/ios-sender`, so this shell can be opened and built as a native SwiftUI app. It is still not a streaming sender implementation.

## What is here

- SwiftUI app shell that launches to the current home screen
- minimal backend client and request/response models that can claim a pairing code from the existing backend
- backend URL, pairing-code, and sender-name inputs
- honest loading, success, and failure states for the claim flow
- a stored sender session ticket with `sessionId`, `senderToken`, backend `state`, and `signalingURL`
- a minimal signaling client that can join the backend WebSocket as the sender and report lifecycle events
- honest signaling status in the UI after claim, connect, and disconnect
- local-development app config for `http://localhost` or another LAN backend during simulator-first testing
- crisp notes for the future ReplayKit Broadcast Upload Extension and WebRTC integration

## What is not here yet

- sender auth/session continuity beyond the basic signaling join
- ReplayKit screen capture
- WebRTC media capture or transport
- offer/answer and ICE handling beyond the current signaling shell
- native backend integration beyond the claim and signaling join steps
- any real end-to-end iOS streaming

## Xcode launch flow

1. Open [ScreenMirroringSender.xcworkspace](c:\Users\Shadow\projects-vscode\screen-mirroring\apps\ios-sender\ScreenMirroringSender.xcworkspace) in Xcode. The project file at [ScreenMirroringSender.xcodeproj](c:\Users\Shadow\projects-vscode\screen-mirroring\apps\ios-sender\ScreenMirroringSender.xcodeproj) is also usable, but the workspace is the preferred entry point.
2. Select the `ScreenMirroringSender` scheme.
3. For the lowest-friction first run, choose an iPhone simulator. A physical device will need your own code-signing setup and a backend URL reachable from that device.
4. Start the existing backend from the repo root.
5. Run the app from Xcode. It should launch to the current sender home screen.
6. In the app, enter the backend URL and a receiver-created pairing code.
7. If you are on a simulator, `http://localhost:8787` should work for local backend development. If you are on a physical device, use your computer's LAN IP instead of `localhost`.
8. Tap `Claim session`.
9. Tap `Connect signaling`.
10. Watch the state move through `claiming`, `claimed`, `connecting-signaling`, and `signaling-connected`.
11. Tap `Disconnect signaling`.
12. Tap `Clear claim` only if you want to remove the stored session ticket and start over.

## Honest status

- This slice is meant to launch a real native shell and exercise claim plus signaling-connect UI flow only.
- It does not capture the screen, publish media, or complete WebRTC offer/answer exchange yet.
- I did not run `xcodebuild` in this environment, so the project setup was reviewed statically rather than runtime-verified here.

## Next step

Add the first real WebRTC signaling actions on top of the existing sender socket, but still stop short of ReplayKit and media publishing.
