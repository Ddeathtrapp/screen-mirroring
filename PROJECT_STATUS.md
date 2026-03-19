# Project Status

Updated: 2026-03-19

## Current vision

Build a private, ad-free casting system that lets an iPhone or iPad mirror to either an Android/Android TV receiver or a desktop browser with the lowest practical latency. The backend stays small and acts only as the control plane for pairing, signaling, lightweight device registration, and session lifecycle.

## Implemented in this turn

- `apps/android-receiver` now has the first Android TV receiver shell packaged as a minimal Android Studio app target, with backend URL entry, receiver name entry, create-pairing-code flow, session display, signaling connect/disconnect, and honest receiver-side status updates.
- `apps/android-receiver` now includes a real WebRTC receiver peer-connection shell that answers sender offers, forwards ICE, and attaches the remote track to an on-screen renderer.
- `apps/android-receiver` UI now distinguishes signaling connected, negotiating, remote track attached, rendering video, signaling failed, and WebRTC failed.
- `apps/android-receiver` docs now describe the receiver-side smoke test, including the emulator-local backend URL (`http://10.0.2.2:8787`) and the remaining playback TODOs.
- `apps/ios-sender` now has a minimal native claim-and-signaling shell plus a small Xcode project/workspace so the app can be opened and launched from Xcode.
- `SenderBackendClient.swift` and `SenderBackendModels.swift` can claim a pairing code against the existing backend and parse the returned session ticket.
- `SenderViewModel.swift` now tracks backend URL, pairing code, sender name, claim loading, claimed success, signaling connection attempts, signaling success, signaling failure, and clean disconnects.
- `SenderHomeView.swift` now exposes backend URL entry, pairing-code entry, claim, connect-signaling, disconnect, and clear-claim actions, plus compact claimed-session and signaling summaries.
- `SenderModels.swift` now holds only the connection-state and session-ticket model types.
- `apps/ios-sender` now includes `Info.plist` local-development allowances so the shell can talk to a local backend during simulator-first claim/signaling testing.
- `apps/ios-sender` docs now describe the buildable iOS shell honestly, including the exact Xcode launch flow, simulator-first run guidance, and the remaining non-streaming TODOs.
- Root monorepo skeleton with workspace-friendly layout.
- Root `.gitignore`.
- Root `README.md` with exact local run steps.
- Root `package.json` with minimal workspace scripts.
- Initial scaffolding for `apps/ios-sender`, `apps/android-receiver`, `apps/web-receiver`, `apps/dev-sender`, `services/backend`, `packages/protocol`, and `infra/docker`.
- `packages/protocol` with shared session states, pairing endpoints, WebSocket message types, and error codes.
- `services/backend` scaffold with Fastify + `ws`, in-memory session store, pairing-code creation, pairing-code claim, heartbeat, session end, and signaling WebSocket auth/relay.
- `apps/web-receiver` scaffold that creates a receiver pairing code, opens signaling, and prepares a receive-only WebRTC peer connection.
- `apps/dev-sender` disposable browser harness that claims a pairing code, opens signaling, and can publish a synthetic canvas stream.
- Browser sender -> web receiver smoke test confirmed end to end on localhost.
- `apps/dev-sender` cleanup so the synthetic stream is torn down if the offer path fails.
- Root smoke-test wording that tells you to open both Vite URLs printed by the receiver and sender terminals.
- Browser smoke-test note that both tabs should stay visible because background-tab throttling can make the dev sender look low-FPS.
- Clear `TODO(SQLite)` placeholders where durable persistence will replace in-memory state.
- Browser/backend dev wiring for local cross-origin development.
- Backend logs for pairing creation, sender claim, signaling connect, relay attempts, and disconnect transitions.
- Low apparent FPS in the dev sender was traced to browser background-tab throttling of the synthetic canvas loop, not to the backend, signaling, or receiver pipeline.
- The iOS shell now includes project/workspace packaging plus the code paths to claim a session and open the backend signaling socket returned by the claim response, but it still does not publish media and has not been run here in Xcode in this environment.
- The Android TV shell is packaged as an Android Studio app target, but I could not run Android Studio from this environment to verify the build at runtime here.

## Chosen v1 architecture

- Transport: WebRTC, one sender to one receiver.
- Sender: iOS/iPadOS app using ReplayKit Broadcast Upload Extension.
- Receivers: Android app family with Android TV as the primary v1 target, plus a desktop web receiver.
- Backend: Node 22 + TypeScript + Fastify + WebSocket signaling + SQLite in WAL mode.
- Infra: one small Hetzner Cloud VM, with Caddy for TLS/reverse proxy and coturn for STUN/TURN.
- Session model: receiver creates a short-lived pairing code, sender joins, backend mints short-lived session tokens, peers exchange SDP/ICE, media goes direct P2P when possible.
- Fallbacks: TURN relay only when direct ICE connectivity fails; ICE restart before forcing re-pair.

## App breakdown

- iOS app family: sender only in v1. Responsibilities are screen capture, permission flow, broadcast start/stop, connection status, and failure handling.
- Android app family: receiver only in v1. Android TV is the main receiver experience first; handheld Android receiver support stays out of the initial milestone unless it falls out cheaply from the same codebase.
- Web app: receiver only in v1 for desktop/laptop browsers, with Chrome and Edge as the first-class targets.

## Backend choice

- Backend stack: Node 22, TypeScript, Fastify, `ws`, SQLite WAL.
- Hosting: one small Hetzner Cloud VM in the US region closest to initial users.
- Supporting services on the same box in v1: Caddy and coturn.
- Why this choice: lowest ops burden, low cost, simple deployment, and enough headroom for early beta as long as most sessions stay P2P.

## Milestone order

1. Protocol and backend control plane.
2. Web receiver and end-to-end session join flow.
3. iOS sender with ReplayKit, streaming to web receiver.
4. Android TV receiver on the same signaling/media contract.
5. Reconnect, TURN fallback, telemetry, and beta hardening.

## Open risks

- iOS ReplayKit requires explicit user-driven broadcast flow and will not capture protected content.
- TURN relay bandwidth can become the main infrastructure cost if too many sessions fail direct P2P.
- Restrictive networks may still fail even with TURN/TCP or TURN/TLS.
- Mobile sender thermal throttling, battery drain, and unstable background behavior can reduce session quality.
- Android TV hardware decode and browser autoplay/codec behavior need device-level testing early.
- This environment does not currently have `node` or `npm` on `PATH`, so in-session runtime verification was limited to static integration review rather than actually launching the services.
- The disposable sender harness now proves the sender-to-receiver control path, but it is not a native sender implementation.
- The iOS sender shell now has the project/workspace needed for Xcode launch and includes local-development HTTP allowances, but I could not run Xcode from this environment to verify the launch at runtime here.
- The iOS sender shell can claim and connect signaling, but it still does not handle offer/answer or ICE beyond the existing signaling-shell handoff.
- The Android TV receiver shell now includes the first real WebRTC peer connection and visible renderer surface, but playback polish and reconnect hardening are still pending.

## Next 3 implementation tasks

1. Harden Android TV reconnect and ICE restart behavior after the basic rendering path.
2. Add the first ReplayKit Broadcast Upload Extension shell without attempting native streaming yet.
3. Add sender offer/answer forwarding and ICE relay on top of the existing iOS signaling socket.
