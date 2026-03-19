# Screen Mirroring

Private, ad-free screen mirroring across iPhone/iPad sender, Android/Android TV receiver, web receiver, and a lightweight signaling backend.

## Repo Layout

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
```

## Current State

Milestone 1 now has a small working foundation:

- monorepo skeleton with npm workspaces
- shared protocol package
- backend scaffold with in-memory pairing/session state
- minimal web receiver that creates a pairing code, connects to signaling, and waits for a sender offer

There is no sender app yet, so media will not flow end to end until the next implementation slice.

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

4. Open the Vite URL shown in the terminal, usually:

```bash
http://localhost:5173
```

5. In the web UI:

- leave the backend URL as `http://localhost:8787`
- optionally enter a receiver name
- click `Create pairing code`
- confirm that a 6-digit pairing code appears and the receiver connects to signaling

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
