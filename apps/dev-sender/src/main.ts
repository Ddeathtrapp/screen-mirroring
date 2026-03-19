import "./style.css";
import { type ClaimPairingCodeResponse } from "@screen-mirroring/protocol";
import { DevSenderSession } from "./sender";

const DEFAULT_BACKEND_URL = "http://localhost:8787";

const app = document.querySelector<HTMLDivElement>("#app");

if (!app) {
  throw new Error("App root not found");
}

app.innerHTML = `
  <main class="shell">
    <section class="panel">
      <p class="eyebrow">DEV ONLY</p>
      <h1>Sender harness</h1>
      <p class="lede">Disposable browser sender for validating the current backend and web receiver before native apps exist.</p>

      <form id="form" class="form">
        <label>
          Backend URL
          <input id="backend-url" type="url" value="${DEFAULT_BACKEND_URL}" />
        </label>
        <label>
          Pairing code
          <input id="pairing-code" type="text" inputmode="numeric" autocomplete="one-time-code" placeholder="123456" maxlength="12" />
        </label>
        <label>
          Sender name
          <input id="sender-name" type="text" maxlength="40" placeholder="Dev Sender" />
        </label>
      </form>

      <div class="actions">
        <button id="connect" type="button">Connect</button>
        <button id="disconnect" type="button" disabled>Disconnect</button>
        <button id="start-test" type="button" disabled>Start synthetic stream</button>
        <button id="stop-test" type="button" disabled>Stop stream</button>
      </div>

      <div class="status-grid">
        <div>
          <p class="status-label">Claim</p>
          <p id="claim-status" class="status-value">Idle</p>
        </div>
        <div>
          <p class="status-label">Signaling</p>
          <p id="signal-status" class="status-value">Idle</p>
        </div>
        <div>
          <p class="status-label">Peer</p>
          <p id="peer-status" class="status-value">Idle</p>
        </div>
      </div>

      <div class="pairing-meta" id="session-meta">No session</div>
    </section>

    <section class="panel">
      <p class="eyebrow">Preview</p>
      <video id="preview" class="video" autoplay muted playsinline></video>
      <pre id="log" class="log" aria-live="polite"></pre>
    </section>
  </main>
`;

const form = requireElement<HTMLFormElement>("#form");
const backendUrlInput = requireElement<HTMLInputElement>("#backend-url");
const pairingCodeInput = requireElement<HTMLInputElement>("#pairing-code");
const senderNameInput = requireElement<HTMLInputElement>("#sender-name");
const connectButton = requireElement<HTMLButtonElement>("#connect");
const disconnectButton = requireElement<HTMLButtonElement>("#disconnect");
const startButton = requireElement<HTMLButtonElement>("#start-test");
const stopButton = requireElement<HTMLButtonElement>("#stop-test");
const claimStatus = requireElement<HTMLParagraphElement>("#claim-status");
const signalStatus = requireElement<HTMLParagraphElement>("#signal-status");
const peerStatus = requireElement<HTMLParagraphElement>("#peer-status");
const sessionMeta = requireElement<HTMLDivElement>("#session-meta");
const preview = requireElement<HTMLVideoElement>("#preview");
const logPanel = requireElement<HTMLPreElement>("#log");

const log = (message: string) => {
  const timestamp = new Date().toLocaleTimeString();
  logPanel.textContent = `[${timestamp}] ${message}\n${logPanel.textContent}`;
};

let session: DevSenderSession | null = null;

connectButton.addEventListener("click", async () => {
  const previousSession = session;
  const backendUrl = normalizeBackendUrl(backendUrlInput.value.trim() || DEFAULT_BACKEND_URL);
  const pairingCode = pairingCodeInput.value.trim().replace(/\s+/g, "");
  const senderName = senderNameInput.value.trim();

  if (!pairingCode) {
    claimStatus.textContent = "Enter a pairing code";
    return;
  }

  if (previousSession) {
    await previousSession.disconnect("Replacing sender session.");
  }

  disconnectSession("Connecting");
  claimStatus.textContent = "Claiming code...";
  signalStatus.textContent = "Idle";
  peerStatus.textContent = "Idle";
  sessionMeta.textContent = "Creating sender session...";
  log(`Claiming pairing code ${pairingCode} via ${backendUrl}`);

  session = new DevSenderSession({
    backendUrl,
    pairingCode,
    senderName: senderName || undefined,
    previewElement: preview,
    onClaim: (response) => {
      applyClaim(response);
    },
    onSignalStatus: (value) => {
      signalStatus.textContent = value;
    },
    onPeerStatus: (value) => {
      peerStatus.textContent = value;
    },
    onLog: log,
  });

  try {
    await session.claimPairingCode();
    await session.connectSignaling();
    disconnectButton.disabled = false;
    startButton.disabled = false;
  } catch (error) {
    await session?.disconnect("Sender connect failed.");
    log(error instanceof Error ? error.message : "Failed to claim pairing code.");
    disconnectSession("Claim failed");
  }
});

disconnectButton.addEventListener("click", async () => {
  await session?.disconnect("dev sender disconnect");
  disconnectSession("Disconnected");
});

startButton.addEventListener("click", async () => {
  if (!session) {
    return;
  }

  startButton.disabled = true;

  try {
    await session.connectSignaling();
    await session.startSyntheticStream();
    stopButton.disabled = false;
    await session.sendOffer();
  } catch (error) {
    await session.stopSyntheticStream();
    stopButton.disabled = true;
    log(error instanceof Error ? error.message : "Failed to start synthetic stream.");
    startButton.disabled = false;
  }
});

stopButton.addEventListener("click", async () => {
  if (!session) {
    return;
  }

  await session.stopSyntheticStream();
  stopButton.disabled = true;
  startButton.disabled = false;
  log("Synthetic stream stopped.");
});

function disconnectSession(reason: string) {
  session = null;
  disconnectButton.disabled = true;
  startButton.disabled = true;
  stopButton.disabled = true;
  claimStatus.textContent = reason;
  signalStatus.textContent = "Idle";
  peerStatus.textContent = "Idle";
  sessionMeta.textContent = "No session";
  preview.srcObject = null;
}

function applyClaim(response: ClaimPairingCodeResponse) {
  claimStatus.textContent = "Claimed";
  sessionMeta.textContent = `Session ${response.sessionId}`;
  log(`Claimed. Signaling URL: ${response.signalingUrl}`);
}

function normalizeBackendUrl(value: string): string {
  return value.replace(/\/+$/, "");
}

function requireElement<T extends Element>(selector: string): T {
  const element = document.querySelector<T>(selector);
  if (!element) {
    throw new Error(`Missing element: ${selector}`);
  }
  return element;
}
