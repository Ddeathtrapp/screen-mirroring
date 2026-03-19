import type {
  ClaimPairingCodeResponse,
  ClientToServerMessage,
  EndSessionRequest,
  ServerToClientMessage,
  SessionState,
} from "@screen-mirroring/protocol";
import { restEndpoints } from "@screen-mirroring/protocol";

type DevSenderConfig = {
  backendUrl: string;
  pairingCode: string;
  senderName?: string;
  previewElement: HTMLVideoElement;
  onClaim: (response: ClaimPairingCodeResponse) => void;
  onSignalStatus: (value: string) => void;
  onPeerStatus: (value: string) => void;
  onLog: (message: string) => void;
};

type AuthContext = ClaimPairingCodeResponse & {
  signalingUrl: string;
};

export class DevSenderSession {
  private readonly config: DevSenderConfig;
  private auth: AuthContext | null = null;
  private socket: WebSocket | null = null;
  private peerConnection: RTCPeerConnection | null = null;
  private syntheticStream: MediaStream | null = null;
  private canvas: HTMLCanvasElement | null = null;
  private renderTimer: number | null = null;
  private stopRequested = false;
  private tracksAttached = false;

  constructor(config: DevSenderConfig) {
    this.config = config;
  }

  async claimPairingCode(): Promise<void> {
    const claimPath = restEndpoints.claimPairingCode.path.replace(":code", this.config.pairingCode);
    const response = await fetch(new URL(claimPath, `${normalizeUrl(this.config.backendUrl)}/`), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ senderName: this.config.senderName }),
    });

    if (!response.ok) {
      throw new Error(`Claim failed with status ${response.status}`);
    }

    this.auth = (await response.json()) as AuthContext;
    this.config.onClaim(this.auth);
  }

  async startSyntheticStream(): Promise<void> {
    if (this.syntheticStream) {
      return;
    }

    const canvas = document.createElement("canvas");
    canvas.width = 1280;
    canvas.height = 720;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("Canvas 2D context not available");
    }

    this.canvas = canvas;
    this.syntheticStream = canvas.captureStream(30);
    this.attachTracksToPeerConnection();
    this.config.previewElement.srcObject = this.syntheticStream;
    void this.config.previewElement.play().catch(() => undefined);

    let tick = 0;
    const draw = () => {
      if (!this.canvas || !context) {
        return;
      }
      tick += 1;
      context.fillStyle = "#08111f";
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.fillStyle = "#4f8cff";
      context.fillRect(40, 40, canvas.width - 80, canvas.height - 80);
      context.fillStyle = "#0f172a";
      context.fillRect(80, 80, canvas.width - 160, canvas.height - 160);
      context.fillStyle = "#e2e8f0";
      context.font = "bold 64px ui-sans-serif, system-ui, sans-serif";
      context.fillText("DEV SENDER", 120, 180);
      context.font = "28px ui-sans-serif, system-ui, sans-serif";
      context.fillText(`Frame ${tick}`, 120, 240);
      context.fillText(new Date().toLocaleTimeString(), 120, 290);
      context.fillStyle = tick % 2 === 0 ? "#f59e0b" : "#22c55e";
      context.beginPath();
      context.arc(1040, 250, 80, 0, Math.PI * 2);
      context.fill();
    };

    draw();
    this.renderTimer = window.setInterval(draw, 1000 / 30);
    this.config.onLog("Synthetic canvas stream started.");
  }

  async stopSyntheticStream(): Promise<void> {
    if (this.renderTimer !== null) {
      window.clearInterval(this.renderTimer);
      this.renderTimer = null;
    }

    this.syntheticStream?.getTracks().forEach((track) => track.stop());
    this.syntheticStream = null;
    this.canvas = null;
    this.tracksAttached = false;
    this.config.previewElement.srcObject = null;
  }

  async connectSignaling(): Promise<void> {
    if (!this.auth) {
      throw new Error("Pairing code must be claimed before connecting");
    }

    await this.ensurePeerConnection();
    await this.connectSocket();
  }

  async sendOffer(): Promise<void> {
    if (!this.auth) {
      throw new Error("Pairing code must be claimed before sending an offer");
    }

    if (!this.peerConnection) {
      await this.ensurePeerConnection();
    }

    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error("Signaling socket is not connected");
    }

    if (!this.syntheticStream) {
      await this.startSyntheticStream();
    }

    await this.createAndSendOffer();
  }

  async disconnect(reason = "dev sender disconnect"): Promise<void> {
    this.stopRequested = true;
    const socket = this.socket;
    const auth = this.auth;

    this.socket = null;
    this.auth = null;

    if (auth) {
      const payload: EndSessionRequest = {
        sessionId: auth.sessionId,
        token: auth.senderToken,
        role: "sender",
        reason,
      };

      try {
        await fetch(new URL(restEndpoints.sessionEnd.path.replace(":sessionId", auth.sessionId), `${normalizeUrl(this.config.backendUrl)}/`), {
          method: restEndpoints.sessionEnd.method,
          headers: { "content-type": "application/json" },
          body: JSON.stringify(payload),
        });
      } catch {
        // Ignore cleanup errors for a disposable dev harness.
      }
    }

    socket?.close();
    this.peerConnection?.close();
    this.peerConnection = null;
    await this.stopSyntheticStream();
  }

  private async ensurePeerConnection(): Promise<void> {
    if (this.peerConnection) {
      return;
    }

    const pc = new RTCPeerConnection({ iceServers: [] });
    this.peerConnection = pc;
    this.config.onPeerStatus("Peer created");

    this.attachTracksToPeerConnection();

    pc.onconnectionstatechange = () => {
      this.config.onPeerStatus(`Peer ${pc.connectionState}`);
    };

    pc.oniceconnectionstatechange = () => {
      this.config.onPeerStatus(`ICE ${pc.iceConnectionState}`);
    };

    pc.onicecandidate = (event) => {
      if (!event.candidate || !this.auth) {
        return;
      }

      this.send({
        type: "signal.ice-candidate",
        sessionId: this.auth.sessionId,
        candidate: event.candidate.toJSON(),
      });
    };
  }

  private async connectSocket(): Promise<void> {
    if (!this.auth) {
      throw new Error("Sender is not authenticated");
    }

    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      return;
    }

    const socket = new WebSocket(buildWebSocketUrl(this.config.backendUrl, this.auth.signalingUrl));
    this.socket = socket;
    this.config.onSignalStatus("Connecting");

    await new Promise<void>((resolve, reject) => {
      socket.addEventListener("open", () => {
        this.config.onSignalStatus("Connected");
        resolve();
      });

      socket.addEventListener("message", (event) => {
        void this.handleMessage(event.data);
      });

      socket.addEventListener("close", () => {
        this.config.onSignalStatus("Closed");
        this.socket = null;
        if (!this.stopRequested) {
          this.config.onLog("Signaling socket closed.");
        }
      });

      socket.addEventListener("error", () => {
        this.config.onSignalStatus("Error");
        reject(new Error("Unable to connect to signaling"));
      });
    });
  }

  private async createAndSendOffer(): Promise<void> {
    if (!this.peerConnection || !this.auth) {
      throw new Error("Peer connection is not ready");
    }

    const offer = await this.peerConnection.createOffer();
    await this.peerConnection.setLocalDescription(offer);
    this.send({
      type: "signal.offer",
      sessionId: this.auth.sessionId,
      sdp: offer.sdp ?? "",
    });
    this.config.onLog("Offer sent.");
  }

  private async handleMessage(data: unknown): Promise<void> {
    const message = parseMessage(data);
    if (!message || !this.auth) {
      return;
    }

    switch (message.type) {
      case "session.joined":
        this.config.onLog(`Joined session ${message.sessionId} as ${message.role}.`);
        break;
      case "session.state":
        this.config.onPeerStatus(renderState(message.state));
        break;
      case "signal.answer":
        await this.peerConnection?.setRemoteDescription({ type: "answer", sdp: message.sdp });
        this.config.onLog("Answer applied.");
        break;
      case "signal.ice-candidate":
        await this.peerConnection?.addIceCandidate(message.candidate);
        break;
      case "session.error":
        this.config.onLog(`Backend error: ${message.error.code} - ${message.error.message}`);
        break;
      default:
        this.config.onLog(`Unhandled signaling message: ${(message as { type: string }).type}`);
        break;
    }
  }

  private send(message: ClientToServerMessage): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error("Signaling socket is not connected");
    }

    this.socket.send(JSON.stringify(message));
  }

  private attachTracksToPeerConnection(): void {
    if (!this.peerConnection || !this.syntheticStream || this.tracksAttached) {
      return;
    }

    for (const track of this.syntheticStream.getTracks()) {
      this.peerConnection.addTrack(track, this.syntheticStream);
    }

    this.tracksAttached = true;
  }
}

function parseMessage(data: unknown): ServerToClientMessage | null {
  if (typeof data !== "string") {
    return null;
  }

  try {
    return JSON.parse(data) as ServerToClientMessage;
  } catch {
    return null;
  }
}

function renderState(state: SessionState): string {
  switch (state) {
    case "pending":
      return "Waiting for receiver";
    case "paired":
      return "Receiver paired";
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

function normalizeUrl(value: string): string {
  return value.replace(/\/+$/, "");
}

function buildWebSocketUrl(backendUrl: string, signalingPath: string): string {
  const httpUrl = new URL(signalingPath, `${normalizeUrl(backendUrl)}/`);
  httpUrl.protocol = httpUrl.protocol === "https:" ? "wss:" : "ws:";
  return httpUrl.toString();
}
