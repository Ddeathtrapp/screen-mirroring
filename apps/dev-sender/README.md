# DEV Sender

Disposable browser sender harness for validating the current backend and web receiver.

## Run locally

```bash
npm install
npm run dev --workspace @screen-mirroring/dev-sender
```

## Notes

- This is DEV ONLY.
- It uses a synthetic canvas stream by default so camera permission is not required.
- It claims an existing pairing code, connects to the backend signaling URL, sends offer/ICE, and accepts answer/ICE.
