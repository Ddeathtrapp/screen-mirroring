import { randomInt, randomUUID } from "node:crypto";

import {
  errorCodes,
  participantRoles,
  restEndpoints,
  type ClaimPairingCodeRequest,
  type ClaimPairingCodeResponse,
  type CreatePairingCodeRequest,
  type CreatePairingCodeResponse,
  type EndSessionRequest,
  type EndSessionResponse,
  type ParticipantRole,
  type SessionHeartbeatRequest,
  type SessionHeartbeatResponse,
  type SessionState,
} from "@screen-mirroring/protocol";

const pairingCodeLifetimeMs = 10 * 60 * 1000;
const reconnectWindowMs = 45 * 1000;

export interface StoredParticipant {
  role: ParticipantRole;
  token: string;
  displayName?: string;
  connectedAt?: string;
  lastSeenAt?: string;
}

export interface StoredSession {
  sessionId: string;
  pairingCode: string;
  state: SessionState;
  createdAt: string;
  expiresAt: string;
  reconnectUntil: string;
  receiver: StoredParticipant;
  sender?: StoredParticipant;
  endedAt?: string;
  endReason?: string;
}

export class InMemorySessionStore {
  private readonly sessionsById = new Map<string, StoredSession>();
  private readonly sessionIdsByPairingCode = new Map<string, string>();

  // TODO(SQLite): replace this in-memory store with durable tables and cleanup jobs.

  createPairingCode(input: CreatePairingCodeRequest): CreatePairingCodeResponse {
    const sessionId = randomUUID();
    const pairingCode = this.generatePairingCode();
    const now = new Date();
    const expiresAt = new Date(now.getTime() + pairingCodeLifetimeMs).toISOString();
    const receiverToken = randomUUID();

    const session: StoredSession = {
      sessionId,
      pairingCode,
      state: "pending",
      createdAt: now.toISOString(),
      expiresAt,
      reconnectUntil: new Date(now.getTime() + reconnectWindowMs).toISOString(),
      receiver: {
        role: "receiver",
        token: receiverToken,
        displayName: input.receiverName,
      },
    };

    this.sessionsById.set(sessionId, session);
    this.sessionIdsByPairingCode.set(pairingCode, sessionId);

    return {
      sessionId,
      pairingCode,
      receiverToken,
      state: session.state,
      expiresAt,
      signalingUrl: this.buildSignalingUrl(sessionId, receiverToken, "receiver"),
    };
  }

  claimPairingCode(
    pairingCode: string,
    input: ClaimPairingCodeRequest,
  ): ClaimPairingCodeResponse {
    const sessionId = this.sessionIdsByPairingCode.get(pairingCode);
    if (!sessionId) {
      throw this.error(errorCodes.pairingCodeNotFound, "Pairing code was not found.");
    }

    const session = this.requireSession(sessionId);
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      throw this.error(errorCodes.pairingCodeExpired, "Pairing code has expired.");
    }

    if (session.state === "ended") {
      throw this.error(errorCodes.sessionEnded, "Session has already ended.");
    }

    if (session.sender) {
      throw this.error(errorCodes.pairingCodeAlreadyClaimed, "Pairing code has already been claimed.");
    }

    const senderToken = randomUUID();
    session.sender = {
      role: "sender",
      token: senderToken,
      displayName: input.senderName,
    };
    session.state = "paired";

    return {
      sessionId: session.sessionId,
      senderToken,
      state: session.state,
      signalingUrl: this.buildSignalingUrl(session.sessionId, senderToken, "sender"),
    };
  }

  getSession(sessionId: string): StoredSession {
    return this.requireSession(sessionId);
  }

  verifyParticipant(sessionId: string, token: string, role: ParticipantRole): StoredSession {
    const session = this.requireSession(sessionId);
    if (session.state === "ended") {
      throw this.error(errorCodes.sessionEnded, "Session has already ended.");
    }
    const participant = role === "receiver" ? session.receiver : session.sender;

    if (!participant) {
      throw this.error(errorCodes.sessionTokenInvalid, "Participant is not registered for this session.");
    }

    if (participant.token !== token) {
      throw this.error(errorCodes.sessionTokenInvalid, "Session token is invalid.");
    }

    return session;
  }

  heartbeat(input: SessionHeartbeatRequest): SessionHeartbeatResponse {
    const session = this.verifyParticipant(input.sessionId, input.token, input.role);
    const participant = input.role === "receiver" ? session.receiver : session.sender;

    if (!participant) {
      throw this.error(errorCodes.sessionTokenInvalid, "Participant is not registered for this session.");
    }

    const now = new Date().toISOString();
    participant.lastSeenAt = now;

    return {
      sessionId: session.sessionId,
      state: session.state,
      lastSeenAt: now,
    };
  }

  endSession(input: EndSessionRequest): EndSessionResponse {
    const session = this.verifyParticipant(input.sessionId, input.token, input.role);
    session.state = "ended";
    session.endedAt = new Date().toISOString();
    session.endReason = input.reason;
    return {
      sessionId: session.sessionId,
      state: session.state,
    };
  }

  markConnected(sessionId: string, role: ParticipantRole): StoredSession {
    const session = this.requireSession(sessionId);
    const participant = role === "receiver" ? session.receiver : session.sender;
    if (!participant) {
      throw this.error(errorCodes.sessionTokenInvalid, "Participant is not registered for this session.");
    }

    participant.connectedAt = new Date().toISOString();
    if (session.receiver.connectedAt && session.sender?.connectedAt && session.state !== "connected") {
      session.state = "connected";
    } else if (session.sender && session.state !== "ended") {
      session.state = "negotiating";
    }

    return session;
  }

  markReconnecting(sessionId: string): StoredSession {
    const session = this.requireSession(sessionId);
    if (session.state !== "ended") {
      session.state = "reconnecting";
    }
    return session;
  }

  private requireSession(sessionId: string): StoredSession {
    const session = this.sessionsById.get(sessionId);
    if (!session) {
      throw this.error(errorCodes.sessionNotFound, "Session was not found.");
    }
    return session;
  }

  private generatePairingCode(): string {
    let code = "";
    do {
      code = String(randomInt(100000, 1000000));
    } while (this.sessionIdsByPairingCode.has(code));
    return code;
  }

  private buildSignalingUrl(sessionId: string, token: string, role: ParticipantRole): string {
    const url = new URL(restEndpoints.signaling.path, "http://localhost");
    url.searchParams.set("sessionId", sessionId);
    url.searchParams.set("token", token);
    url.searchParams.set("role", role);
    return url.pathname + url.search;
  }

  private error(code: (typeof errorCodes)[keyof typeof errorCodes], message: string): Error {
    const error = new Error(message);
    error.name = code;
    return error;
  }
}

export function isParticipantRole(value: string): value is ParticipantRole {
  return (participantRoles as readonly string[]).includes(value);
}
