import type {
  ClientToServerMessage,
  ServerToClientMessage,
  SessionErrorMessage,
} from "@screen-mirroring/protocol";

export type SignalingStatus = "idle" | "connecting" | "connected" | "closed" | "error";

export type SignalingMessage = ServerToClientMessage;

export class SignalingClient {
  private socket: WebSocket | null = null;

  onStatus?: (status: SignalingStatus) => void;
  onMessage?: (message: SignalingMessage) => void;
  onClose?: () => void;

  constructor(private readonly url: string) {}

  async connect(): Promise<void> {
    if (this.socket) {
      return;
    }

    this.onStatus?.("connecting");

    await new Promise<void>((resolve, reject) => {
      let socket: WebSocket;

      try {
        socket = new WebSocket(this.url);
      } catch {
        this.onStatus?.("error");
        reject(new Error(`Invalid signaling URL: ${this.url}`));
        return;
      }

      this.socket = socket;

      socket.addEventListener("open", () => {
        this.onStatus?.("connected");
        resolve();
      });

      socket.addEventListener("message", (event) => {
        const message = this.parseMessage(event.data);
        this.onMessage?.(message);
      });

      socket.addEventListener("close", () => {
        this.onStatus?.("closed");
        this.socket = null;
        this.onClose?.();
      });

      socket.addEventListener("error", () => {
        this.onStatus?.("error");
        reject(new Error(`Unable to connect to signaling server at ${this.url}`));
      });
    });
  }

  send(message: ClientToServerMessage): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error("Signaling socket is not connected");
    }

    this.socket.send(JSON.stringify(message));
  }

  close(): void {
    this.socket?.close();
    this.socket = null;
  }

  private parseMessage(data: unknown): SignalingMessage {
    if (typeof data !== "string") {
      return createParseError("Unsupported signaling payload type");
    }

    try {
      return JSON.parse(data) as SignalingMessage;
    } catch {
      return createParseError("Malformed signaling payload");
    }
  }
}

function createParseError(message: string): SessionErrorMessage {
  return {
    type: "session.error",
    error: {
      code: "INVALID_MESSAGE",
      message,
    },
  };
}
