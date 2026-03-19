# Android TV Receiver

DEV foundation for the future Android TV receiver app.

This slice adds the first receiver-side shell for pairing, signaling, and basic remote video rendering. It is still not polished media playback.

## What is here

- Kotlin receiver shell source for Android TV-first use
- backend URL and receiver-name entry
- create-pairing-code flow against the existing backend
- stored receiver session info including `sessionId`, `pairingCode`, `receiverToken`, `state`, `expiresAt`, and `signalingUrl`
- signaling connection shell that joins the backend WebSocket using the returned `signalingUrl`
- first WebRTC receiver peer connection that answers sender offers and forwards ICE
- visible TV video surface that attaches the remote track when it arrives
- honest state labels for idle, creating code, code created, connecting signaling, signaling connected, negotiating, remote track attached, rendering video, signaling failed, and WebRTC failed
- plain remote-friendly UI with session summary, video panel, and activity log
- crisp TODOs for reconnect hardening and playback polish

## What is not here yet

- reconnect and ICE restart hardening
- playback polish
- durable local persistence

## Local smoke test

1. Start the existing backend.
2. Open the Android receiver project in Android Studio.
3. Run it on an Android TV emulator or device.
4. For an emulator, use `http://10.0.2.2:8787` as the backend URL.
5. For a physical device, use a backend URL reachable from that device, such as your machine's LAN IP.
6. Enter a receiver name.
7. Tap `Create pairing code`.
8. Confirm the UI shows the returned session information.
9. Tap `Connect signaling`.
10. Watch the state move through `creating code`, `code created`, `connecting signaling`, `signaling connected`, `negotiating`, `remote track attached`, and `rendering video` when the sender offer arrives and the remote track binds.
11. Confirm the video panel shows the incoming stream if the sender is active.
12. Tap `Disconnect`.
13. Tap `Clear session` to reset the shell and start over.

## Honest status

- This is a packaged Android Studio app target with source-level Android TV receiver wiring, not a finished media receiver.
- I did not run Android Studio or Gradle in this environment, so the build is reviewed statically here rather than runtime-verified by me.
- The shell currently exercises pairing, signaling, WebRTC answer creation, ICE relay, and basic remote track rendering only.
