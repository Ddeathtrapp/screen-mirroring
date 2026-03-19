# Project Status

Updated: 2026-03-19

## Current vision

Build a private, ad-free casting system that lets an iPhone or iPad mirror to either an Android/Android TV receiver or a desktop browser with the lowest practical latency. The backend stays small and acts only as the control plane for pairing, signaling, lightweight device registration, and session lifecycle.

## Implemented in this turn

- Root monorepo skeleton with workspace-friendly layout.
- Root `.gitignore`.
- Root `README.md` with exact local run steps.
- Root `package.json` with minimal workspace scripts.
- Initial scaffolding for `apps/ios-sender`, `apps/android-receiver`, `apps/web-receiver`, `apps/dev-sender`, `services/backend`, `packages/protocol`, and `infra/docker`.
- `packages/protocol` with shared session states, pairing endpoints, WebSocket message types, and error codes.
- `services/backend` scaffold with Fastify + `ws`, in-memory session store, pairing-code creation, pairing-code claim, heartbeat, session end, and signaling WebSocket auth/relay.
- `apps/web-receiver` scaffold that creates a receiver pairing code, opens signaling, and prepares a receive-only WebRTC peer connection.
- `apps/dev-sender` disposable browser harness that claims a pairing code, opens signaling, and can publish a synthetic canvas stream.
- `apps/dev-sender` cleanup so the synthetic stream is torn down if the offer path fails.
- Root smoke-test wording that tells you to open both Vite URLs printed by the receiver and sender terminals.
- Clear `TODO(SQLite)` placeholders where durable persistence will replace in-memory state.
- Browser/backend dev wiring for local cross-origin development.
- Backend logs for pairing creation, sender claim, signaling connect, relay attempts, and disconnect transitions.

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

## Next 3 implementation tasks

1. Run the new dev-only smoke test end to end and tighten any protocol/runtime issues that show up.
2. Harden backend session cleanup, reconnect windows, and heartbeat-driven expiry semantics.
3. Replace the in-memory store with SQLite WAL while keeping the protocol and WebSocket contract unchanged.
