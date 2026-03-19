type ReceiverSessionConfig = {
  videoElement: HTMLVideoElement;
  onStatus: (status: string) => void;
  onTrack: (stream: MediaStream) => void;
  onLocalIceCandidate: (candidate: RTCIceCandidateInit) => void;
};

export class ReceiverSession {
  private pc: RTCPeerConnection | null = null;
  private remoteStream = new MediaStream();

  constructor(private readonly config: ReceiverSessionConfig) {}

  async prepare(): Promise<void> {
    if (this.pc) {
      return;
    }

    this.config.onStatus("Preparing peer connection");
    const pc = new RTCPeerConnection({
      iceServers: []
    });

    pc.addTransceiver("video", { direction: "recvonly" });
    pc.addTransceiver("audio", { direction: "recvonly" });

    pc.onconnectionstatechange = () => {
      this.config.onStatus(`Peer ${pc.connectionState}`);
    };

    pc.oniceconnectionstatechange = () => {
      this.config.onStatus(`ICE ${pc.iceConnectionState}`);
    };

    pc.ontrack = (event) => {
      this.remoteStream.addTrack(event.track);
      this.config.videoElement.srcObject = this.remoteStream;
      void this.config.videoElement.play().catch(() => undefined);
      this.config.onTrack(this.remoteStream);
    };

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.config.onLocalIceCandidate(event.candidate.toJSON());
      }
    };

    this.pc = pc;
    this.config.onStatus("Ready");
  }

  async acceptOffer(sdp: string): Promise<string | null> {
    if (!this.pc) {
      await this.prepare();
    }

    if (!this.pc) {
      return null;
    }

    this.config.onStatus("Applying offer");
    await this.pc.setRemoteDescription({ type: "offer", sdp });
    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);
    this.config.onStatus("Answer created");
    return this.pc.localDescription?.sdp ?? null;
  }

  async addRemoteIceCandidate(candidate: RTCIceCandidateInit): Promise<void> {
    if (!this.pc) {
      await this.prepare();
    }

    if (!this.pc) {
      return;
    }

    await this.pc.addIceCandidate(candidate);
  }

  close(): void {
    this.pc?.close();
    this.pc = null;
    this.remoteStream = new MediaStream();
    this.config.videoElement.srcObject = null;
    this.config.onStatus("Closed");
  }
}
