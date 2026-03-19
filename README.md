# Screen Mirroring

Private, ad-free screen mirroring across iPhone/iPad sender, Android/Android TV receiver, web receiver, and a lightweight signaling backend.

## Repo Layout

```text
apps/
  ios-sender/
  android-receiver/
  dev-sender/
  web-receiver/
services/
  backend/
packages/
  protocol/
infra/
  docker/
```

## Current State

Milestone 1 now has a small working foundation:

- monorepo skeleton with npm workspaces
- shared protocol package
- backend scaffold with in-memory pairing/session state
- minimal web receiver that creates a pairing code, connects to signaling, and waits for a sender offer
- backend logs for pairing, signaling joins, and relay events
- disposable dev-only browser sender harness that claims a code, opens signaling, and can publish a synthetic canvas stream

The harness is DEV ONLY and exists purely to smoke-test the current backend and receiver before any native sender work.

## Local Setup

Prerequisite: install Node.js 22 or newer.

1. Install workspace dependencies:

```bash
npm install
```

2. Start the backend:

```bash
npm run dev:backend
```

3. In a second terminal, start the web receiver:

```bash
npm run dev:web
```

4. In a third terminal, start the dev sender harness:

```bash
npm run dev:sender
```

5. Open the Vite URLs shown by the receiver and sender terminals. Each app runs its own dev server, so the ports may differ.

6. Smoke-test order:

- open the web receiver first and click `Create pairing code`
- copy the 6-digit pairing code from the receiver
- open the dev sender harness and enter the pairing code
- click `Connect`
- click `Start synthetic stream`
- confirm the receiver shows `Connected` and then displays the remote video
- click `Disconnect` in the sender harness to end the session cleanly

## Useful Checks

Backend health:

```bash
curl http://localhost:8787/healthz
```

Create a pairing code by API:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8787/v1/pairing-codes `
  -ContentType 'application/json' `
  -Body '{"receiverName":"Desktop Browser"}'
```

Run workspace checks:

```bash
npm run check
```

## Notes

- Keep v1 simple.
- One sender, one receiver.
- Backend stays signaling/control-plane only.
- SQLite and TURN are intentionally not implemented in this first scaffold.
- The disposable sender harness proves the current sender-to-receiver control path, but it is not a native sender implementation.
