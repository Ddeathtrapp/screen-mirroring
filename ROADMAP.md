# Roadmap

## Build order

1. Lock the protocol and session contract.
2. Build the backend control plane.
3. Build the web receiver.
4. Build the iOS sender and prove iPhone/iPad to web casting.
5. Build the Android TV receiver on the same contract.
6. Harden reconnect, TURN fallback, and observability.

## Smallest milestone plan

### Milestone 1: Foundation

Goal: make session setup real before touching native capture.

Deliverables:

- Repo skeleton in the agreed monorepo layout.
- Shared protocol spec for REST, WebSocket messages, session states, and error codes.
- Backend endpoints for receiver registration, pairing-code creation, sender claim, and heartbeat expiry.
- WebSocket signaling channel with session auth.
- Local coturn config for development.

Exit criteria:

- A receiver can request a pairing code.
- A sender can claim the code.
- Both sides can connect to signaling and exchange mock SDP/ICE payloads.

### Milestone 2: First working stream

Goal: prove the full architecture with the cheapest receiver surface first.

Deliverables:

- Web receiver UI for pairing and playback.
- iOS sender shell with ReplayKit broadcast extension.
- End-to-end iPhone/iPad to desktop browser stream over WebRTC.
- Basic connection-state UI and clean failure messages.

Exit criteria:

- Time to first frame is acceptable on a normal home network.
- P2P path works on a realistic test matrix.
- Session teardown is clean.

### Milestone 3: Living-room receiver

Goal: add the native TV receiver without changing the core backend design.

Deliverables:

- Android TV receiver app built on the same signaling/session contract.
- Pairing screen, full-screen playback, reconnect handling, and remote-friendly status UI.
- Shared Android core module for signaling and session management.

Exit criteria:

- iOS sender can cast to Android TV.
- Session join and stop flows are stable.
- TV playback is smooth on target hardware.

### Milestone 4: Beta hardening

Goal: make the prototype reliable enough for private testing.

Deliverables:

- ICE restart and short reconnect window.
- TURN fallback with short-lived credentials.
- Basic metrics: setup success, time to first frame, reconnect success, TURN usage.
- Crash/error logging and device-level smoke test checklist.

Exit criteria:

- Sessions recover from common Wi-Fi drops.
- TURN fallback works on at least one restrictive network.
- Open v1 risks are documented with known workarounds or explicit non-goals.

## Deferred until after v1

- Multi-receiver streaming.
- Android sender mode.
- Handheld Android receiver mode if it adds UI scope.
- iOS receiver mode.
- User accounts and saved device graph.
- Remote control and advanced session management.
- Any server-side media topology beyond TURN relay.
