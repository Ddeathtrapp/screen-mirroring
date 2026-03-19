import "./style.css";
import {
  restEndpoints,
  type ClientToServerMessage,
  type CreatePairingCodeResponse,
  type SessionState,
} from "@screen-mirroring/protocol";
import { SignalingClient } from "./signaling";
import { ReceiverSession } from "./webrtc";

const DEFAULT_BACKEND_URL = "http://localhost:8787";

const app = document.querySelector<HTMLDivElement>("#app");

if (!app) {
  throw new Error("App root not found");
}

app.innerHTML = `
  <main class="shell">
    <section class="panel">
      <p class="eyebrow">Web Receiver</p>
      <h1>Create a pairing code</h1>
      <p class="lede">Start a receiver session in the browser, show the pairing code to the sender later, and wait for an incoming WebRTC offer.</p>

      <form id="create-form" class="form">
        <label>
          Backend URL
          <input id="backend-url" name="backend-url" type="url" value="${DEFAULT_BACKEND_URL}" />
        </label>

        <label>
          Receiver name
          <input id="receiver-name" name="receiver-name" type="text" maxlength="40" placeholder="Desktop Browser" />
        </label>

        <button type="submit">Create pairing code</button>
      </form>

      <div class="pairing-card">
        <p class="status-label">Pairing code</p>
        <p id="pairing-code" class="pairing-code">Not created</p>
        <p id="session-id" class="pairing-meta">Session not started.</p>
      </div>

      <div class="status-grid">
        <div>
          <p class="status-label">Connection</p>
          <p id="connection-status" class="status-value">Idle</p>
        </div>
        <div>
          <p class="status-label">Peer</p>
          <p id="peer-status" class="status-value">Waiting</p>
        </div>
        <div>
          <p class="status-label">Media</p>
          <p id="media-status" class="status-value">Not started</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="video-header">
        <p class="eyebrow">Receiver view</p>
        <button id="disconnect-button" type="button" disabled>Disconnect</button>
      </div>
      <video id="remote-video" class="video" autoplay playsinline controls></video>
      <pre id="log" class="log" aria-live="polite"></pre>
    </section>
  </main>
`;

const form = document.querySelector<HTMLFormElement>("#create-form");
const backendUrlInput = document.querySelector<HTMLInputElement>("#backend-url");
const receiverNameInput = document.querySelector<HTMLInputElement>("#receiver-name");
const pairingCodeDisplay = document.querySelector<HTMLParagraphElement>("#pairing-code");
const sessionIdDisplay = document.querySelector<HTMLParagraphElement>("#session-id");
const connectionStatus = document.querySelector<HTMLParagraphElement>("#connection-status");
const peerStatus = document.querySelector<HTMLParagraphElement>("#peer-status");
const mediaStatus = document.querySelector<HTMLParagraphElement>("#media-status");
const disconnectButton = document.querySelector<HTMLButtonElement>("#disconnect-button");
const logPanel = document.querySelector<HTMLPreElement>("#log");
const remoteVideo = document.querySelector<HTMLVideoElement>("#remote-video");

if (
  !form ||
  !backendUrlInput ||
  !receiverNameInput ||
  !pairingCodeDisplay ||
  !sessionIdDisplay ||
  !connectionStatus ||
  !peerStatus ||
  !mediaStatus ||
  !disconnectButton ||
  !logPanel ||
  !remoteVideo
) {
  throw new Error("Required UI elements are missing");
}

const log = (message: string) => {
  const timestamp = new Date().toLocaleTimeString();
  logPanel.textContent = `[${timestamp}] ${message}\n${logPanel.textContent}`;
};

let signaling: SignalingClient | null = null;
let receiver: ReceiverSession | null = null;
let activeSession: CreatePairingCodeResponse | null = null;

const resetSession = (reason = "Disconnected") => {
  const activeSignaling = signaling;
  const activeReceiver = receiver;

  signaling = null;
  receiver = null;
  activeSession = null;

  activeSignaling?.close();
  activeReceiver?.close();
  pairingCodeDisplay.textContent = "Not created";
  sessionIdDisplay.textContent = "Session not started.";
  connectionStatus.textContent = reason;
  peerStatus.textContent = "Waiting";
  mediaStatus.textContent = "Not started";
  disconnectButton.disabled = true;
};

disconnectButton.addEventListener("click", () => {
  if (activeSession && signaling) {
    const message: ClientToServerMessage = {
      type: "session.end",
      sessionId: activeSession.sessionId,
      reason: "Receiver disconnected from web app.",
    };
    signaling.send(message);
  }

  resetSession("Disconnected");
  log("Session closed.");
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  const backendUrl = normalizeBackendUrl(backendUrlInput.value.trim() || DEFAULT_BACKEND_URL);
  const receiverName = receiverNameInput.value.trim();

  resetSession("Creating session");
  connectionStatus.textContent = "Creating session...";
  peerStatus.textContent = "Waiting for sender";
  mediaStatus.textContent = "Waiting for offer";
  log(`Creating receiver session via ${backendUrl}`);

  try {
    activeSession = await createPairingCode(backendUrl, receiverName);
  } catch (error) {
    log(error instanceof Error ? error.message : "Failed to create pairing code.");
    connectionStatus.textContent = "Create failed";
    return;
  }

  pairingCodeDisplay.textContent = activeSession.pairingCode;
  sessionIdDisplay.textContent = `Session ${activeSession.sessionId}`;

  const signalingUrl = buildWebSocketUrl(backendUrl, activeSession.signalingUrl);
  log(`Pairing code ${activeSession.pairingCode} created. Connecting to signaling.`);

  signaling = new SignalingClient(signalingUrl);
  receiver = new ReceiverSession({
    videoElement: remoteVideo,
    onStatus: (status) => {
      peerStatus.textContent = status;
    },
    onTrack: () => {
      mediaStatus.textContent = "Receiving media";
      log("Remote track attached.");
    },
    onLocalIceCandidate: (candidate) => {
      if (!activeSession) {
        return;
      }

      const message: ClientToServerMessage = {
        type: "signal.ice-candidate",
        sessionId: activeSession.sessionId,
        candidate,
      };
      signaling?.send(message);
    }
  });

  signaling.onStatus = (status) => {
    connectionStatus.textContent = status === "connected" ? "Receiver connected" : status;
  };

  signaling.onMessage = async (message) => {
    switch (message.type) {
      case "session.joined":
        log(`Receiver joined signaling for session ${message.sessionId}.`);
        break;
      case "session.state":
        peerStatus.textContent = renderPeerStatus(message.state);
        if (message.reason) {
          log(`Session state: ${message.state} (${message.reason})`);
        } else {
          log(`Session state: ${message.state}`);
        }
        if (message.state === "ended") {
          resetSession("Session ended");
        }
        break;
      case "signal.offer": {
        const answer = await receiver?.acceptOffer(message.sdp);
        if (answer && activeSession) {
          const response: ClientToServerMessage = {
            type: "signal.answer",
            sessionId: activeSession.sessionId,
            sdp: answer,
          };
          signaling?.send(response);
          log("Answer sent.");
        }
        break;
      }
      case "signal.ice-candidate":
        await receiver?.addRemoteIceCandidate(message.candidate);
        break;
      case "signal.answer":
        log("Received unexpected answer on receiver socket.");
        break;
      case "session.error":
        log(`Backend error: ${message.error.code} - ${message.error.message}`);
        break;
      default:
        log(`Unhandled signaling message: ${message.type}`);
        break;
    }
  };

  signaling.onClose = () => {
    if (signaling) {
      resetSession("Disconnected");
    }
  };

  try {
    await signaling.connect();
    await receiver.prepare();
    disconnectButton.disabled = false;
    connectionStatus.textContent = "Waiting for sender";
  } catch (error) {
    log(error instanceof Error ? error.message : "Failed to connect.");
    resetSession("Connection failed");
  }
});

function normalizeBackendUrl(value: string): string {
  return value.replace(/\/+$/, "");
}

function buildWebSocketUrl(backendUrl: string, signalingPath: string): string {
  const httpUrl = new URL(signalingPath, `${backendUrl}/`);
  httpUrl.protocol = httpUrl.protocol === "https:" ? "wss:" : "ws:";
  return httpUrl.toString();
}

async function createPairingCode(backendUrl: string, receiverName: string): Promise<CreatePairingCodeResponse> {
  const response = await fetch(new URL(restEndpoints.createPairingCode.path, `${backendUrl}/`), {
    method: restEndpoints.createPairingCode.method,
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify({
      receiverName: receiverName || undefined,
    }),
  });

  if (!response.ok) {
    throw new Error(`Backend returned ${response.status} while creating the pairing code.`);
  }

  return (await response.json()) as CreatePairingCodeResponse;
}

function renderPeerStatus(state: SessionState): string {
  switch (state) {
    case "pending":
      return "Waiting for sender";
    case "paired":
      return "Sender claimed code";
    case "negotiating":
      return "Negotiating";
    case "connected":
      return "Connected";
    case "reconnecting":
      return "Reconnecting";
    case "ended":
      return "Ended";
    default:
      return state;
  }
}
