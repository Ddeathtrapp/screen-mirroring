# Recommended Architecture

## Decision summary

Use a lean monorepo with one iOS sender app, one Android receiver app family, one web receiver, and one lightweight backend. Keep media off the server. Let receivers create short-lived pairing codes, let senders join those sessions, and use WebRTC for the media path with TURN only as fallback.

## What v1 is

- One sender to one receiver.
- iPhone/iPad sender only.
- Android TV receiver first, inside one Android app family that can grow into handheld Android receiver support later.
- Desktop web receiver.
- Anonymous install-scoped device identity, but no user accounts.
- Ephemeral sessions, short-lived pairing codes, and no persistent social graph or cloud media storage.

## What v1 is not

- No SFU, MCU, or server-side transcoding.
- No multi-receiver broadcasting.
- No Android sender.
- No iOS receiver.
- No remote control or input injection.
- No DRM bypassing, hidden capture, or unsafe platform workarounds.

## System components

### 1. iOS sender app

- Stack: Swift + SwiftUI + ReplayKit Broadcast Upload Extension + native WebRTC library.
- Responsibilities:
  - Start and stop broadcast.
  - Capture screen and allowed audio through ReplayKit.
  - Join a receiver-created session by code or QR.
  - Publish one outbound video track and optional audio track.
  - Surface permissions, errors, quality state, and reconnect state clearly.

### 2. Android app family

- Stack: Kotlin + native WebRTC library + Android TV UI.
- Responsibilities in v1:
  - Show pairing code or QR.
  - Join signaling and receive one remote stream.
  - Render full-screen playback with remote-friendly UI.
  - Handle reconnects and simple session status overlays.
- Scope decision:
  - Android TV is the main v1 receiver target.
  - Handheld Android receiver support stays out of the first prototype unless it is nearly free from the shared codebase.

### 3. Web receiver

- Stack: Vite + React + TypeScript + browser WebRTC APIs.
- Responsibilities:
  - Show pairing UI.
  - Join a session from desktop/laptop browsers.
  - Render one inbound stream.
  - Support fullscreen and basic fit-to-screen controls.
- Browser target:
  - Chrome and Edge first.
  - Safari and Firefox later after the core path is stable.

### 4. Backend control plane

- Stack: Node 22 + TypeScript + Fastify + `ws` + SQLite WAL.
- Responsibilities:
  - Device registration with install-scoped anonymous identity.
  - Pairing-code generation and claim flow.
  - Session creation, tokens, heartbeats, expiry, and reconnect window.
  - Signaling relay for SDP and ICE.
  - TURN credential minting.
- Non-responsibilities:
  - No media relay unless TURN is being used.
  - No transcoding.
  - No persistent user account system in v1.

### 5. Network edge

- Caddy for TLS and reverse proxy.
- coturn for STUN and TURN.
- One small VM for all of the above in v1.

## Session lifecycle

Use one ephemeral session state machine:

`pending -> paired -> negotiating -> connected -> reconnecting -> ended`

### Flow

1. Receiver opens the app or web page and requests a pairing code.
2. Backend creates an ephemeral session in `pending` state with a short TTL.
3. Receiver displays a 6-digit code and optional QR payload.
4. Sender enters or scans the code.
5. Backend validates the claim, binds sender and receiver, and issues short-lived session tokens.
6. Sender and receiver connect to the signaling WebSocket.
7. Sender offers, receiver answers, ICE candidates trickle through the backend.
8. Media flows directly peer to peer if ICE succeeds.
9. On disconnect, both clients try ICE restart inside a short reconnect window.
10. If recovery fails or capture stops, the session ends and the code expires.

## Media strategy

- One `RTCPeerConnection` per session.
- One video track plus optional audio track.
- One ordered data channel for control and health.
- Prefer H.264 video and Opus audio for the widest practical cross-platform compatibility.
- Start at 720p30.
- Allow 1080p30 later only when device performance and network quality justify it.
- Prefer UDP transport.
- Enable trickle ICE, `max-bundle`, and `rtcp-mux`.
- Attempt ICE restart before ending the session.

## TURN policy

- STUN first, TURN fallback.
- TURN over UDP first.
- TURN over TCP or TLS only as last resort for restrictive networks.
- Mint short-lived TURN credentials per active session.
- Treat TURN usage as an exception path to watch closely, not the default architecture.

## Backend state model

Keep only a small amount of durable state.

### Tables

- `devices`
  - install-scoped device id
  - platform
  - display name
  - last seen
  - token hash
- `pairing_codes`
  - code
  - session id
  - receiver device id
  - expiry
  - claimed at
- `sessions`
  - sender device id
  - receiver device id
  - state
  - created at
  - ended at
  - reconnect deadline
  - relay required flag

### In-memory only

- live WebSocket connections
- pending ICE candidates
- short-lived negotiation buffers

## Repo structure

Use a single monorepo from day one:

```text
apps/
  ios-sender/
  android-receiver/
  web-receiver/
services/
  backend/
packages/
  protocol/
infra/
  docker/
PROJECT_STATUS.md
ARCHITECTURE.md
ROADMAP.md
```

### Why this shape

- It keeps one clear home for each platform split the product already requires.
- It prevents server, web, and protocol logic from drifting early.
- It avoids pretending native clients can share more code than they really can.

## Hosting recommendation

- Start with one small Hetzner Cloud VM in a US region close to initial users.
- Run backend, Caddy, SQLite, and coturn on that single box.
- Deploy with Docker Compose or simple systemd units.
- Snapshot the volume regularly.
- Monitor TURN bandwidth first, because that is the earliest likely cost pressure.

## Biggest risks and the chosen response

- ReplayKit friction on iOS:
  - Accept the system broadcast flow and design the UX around it instead of fighting the platform.
- TURN cost growth:
  - Keep sessions 1:1, prefer direct P2P, and track relay usage before scaling anything else.
- Network failures on hostile Wi-Fi or enterprise networks:
  - Support TURN UDP, then TURN TCP/TLS, plus reconnect with ICE restart.
- Scope creep:
  - Keep v1 to one sender, one receiver, and three concrete client surfaces only.

## Recommended default libraries and tools

- iOS: SwiftUI, ReplayKit, native WebRTC library.
- Android/Android TV: Kotlin, native WebRTC library, Android TV-first UI.
- Web: Vite, React, TypeScript.
- Backend: Fastify, `ws`, SQLite WAL.
- Infra: Caddy, coturn, Docker Compose.
