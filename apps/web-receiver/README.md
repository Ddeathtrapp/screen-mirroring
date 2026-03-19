# Web Receiver

Minimal web receiver scaffold for the screen-mirroring project.

## Run locally

```bash
npm install
npm run dev:web
```

## Notes

- The app defaults to `http://localhost:8787` for the backend.
- The receiver creates a pairing code first, then connects to the backend-provided signaling URL.
- The current WebRTC logic is a receive-only placeholder that expects a sender to relay offer and ICE messages through the backend.
