export const protocolVersion = "v1" as const;

export const sessionStates = [
  "pending",
  "paired",
  "negotiating",
  "connected",
  "reconnecting",
  "ended",
] as const;

export type SessionState = (typeof sessionStates)[number];

export const participantRoles = ["receiver", "sender"] as const;

export type ParticipantRole = (typeof participantRoles)[number];

export const restEndpoints = {
  createPairingCode: { method: "POST", path: "/v1/pairing-codes" },
  claimPairingCode: { method: "POST", path: "/v1/pairing-codes/:code/claim" },
  sessionHeartbeat: { method: "POST", path: "/v1/sessions/:sessionId/heartbeat" },
  sessionEnd: { method: "POST", path: "/v1/sessions/:sessionId/end" },
  healthz: { method: "GET", path: "/healthz" },
  signaling: { method: "GET", path: "/v1/signaling" },
} as const;

export const errorCodes = {
  pairingCodeNotFound: "PAIRING_CODE_NOT_FOUND",
  pairingCodeExpired: "PAIRING_CODE_EXPIRED",
  pairingCodeAlreadyClaimed: "PAIRING_CODE_ALREADY_CLAIMED",
  sessionNotFound: "SESSION_NOT_FOUND",
  sessionTokenInvalid: "SESSION_TOKEN_INVALID",
  roleMismatch: "ROLE_MISMATCH",
  sessionEnded: "SESSION_ENDED",
  invalidMessage: "INVALID_MESSAGE",
} as const;

export type ErrorCode = (typeof errorCodes)[keyof typeof errorCodes];

export interface ProtocolError {
  code: ErrorCode;
  message: string;
}

export interface CreatePairingCodeRequest {
  receiverName?: string;
}

export interface CreatePairingCodeResponse {
  sessionId: string;
  pairingCode: string;
  receiverToken: string;
  state: SessionState;
  expiresAt: string;
  signalingUrl: string;
}

export interface ClaimPairingCodeRequest {
  senderName?: string;
}

export interface ClaimPairingCodeResponse {
  sessionId: string;
  senderToken: string;
  state: SessionState;
  signalingUrl: string;
}

export interface SessionHeartbeatRequest {
  sessionId: string;
  token: string;
  role: ParticipantRole;
}

export interface SessionHeartbeatResponse {
  sessionId: string;
  state: SessionState;
  lastSeenAt: string;
}

export interface EndSessionRequest {
  sessionId: string;
  token: string;
  role: ParticipantRole;
  reason?: string;
}

export interface EndSessionResponse {
  sessionId: string;
  state: SessionState;
}

export interface SessionJoinMessage {
  type: "session.joined";
  sessionId: string;
  role: ParticipantRole;
  state: SessionState;
}

export interface SessionStateMessage {
  type: "session.state";
  sessionId: string;
  state: SessionState;
  reason?: string;
}

export interface SignalOfferMessage {
  type: "signal.offer";
  sessionId: string;
  sdp: string;
}

export interface SignalAnswerMessage {
  type: "signal.answer";
  sessionId: string;
  sdp: string;
}

export interface SignalIceCandidateMessage {
  type: "signal.ice-candidate";
  sessionId: string;
  candidate: unknown;
}

export interface SessionHeartbeatMessage {
  type: "session.heartbeat";
  sessionId: string;
  timestamp: string;
}

export interface SessionEndMessage {
  type: "session.end";
  sessionId: string;
  reason?: string;
}

export interface SessionErrorMessage {
  type: "session.error";
  sessionId?: string;
  error: ProtocolError;
}

export type ClientToServerMessage =
  | SignalOfferMessage
  | SignalAnswerMessage
  | SignalIceCandidateMessage
  | SessionHeartbeatMessage
  | SessionEndMessage;

export type ServerToClientMessage =
  | SessionJoinMessage
  | SessionStateMessage
  | SignalOfferMessage
  | SignalAnswerMessage
  | SignalIceCandidateMessage
  | SessionErrorMessage;

export interface SessionSummary {
  sessionId: string;
  pairingCode: string;
  state: SessionState;
  receiverName?: string;
  senderName?: string;
  createdAt: string;
  expiresAt: string;
}
