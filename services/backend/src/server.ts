import cors from "@fastify/cors";
import Fastify from "fastify";
import { WebSocket, WebSocketServer, type RawData } from "ws";

import {
  errorCodes,
  restEndpoints,
  type ParticipantRole,
  type ClientToServerMessage,
  type ClaimPairingCodeRequest,
  type CreatePairingCodeRequest,
  type EndSessionRequest,
  type SessionHeartbeatRequest,
  type ProtocolError,
  type SessionState,
  type ServerToClientMessage,
  type SessionErrorMessage,
} from "@screen-mirroring/protocol";

import { InMemorySessionStore, isParticipantRole } from "./store.js";

const port = Number.parseInt(process.env.PORT ?? "8787", 10);
const host = process.env.HOST ?? "0.0.0.0";

const app = Fastify({ logger: true });
const store = new InMemorySessionStore();
const wss = new WebSocketServer({ noServer: true });

type AuthedSocket = WebSocket & {
  sessionId: string;
  role: ParticipantRole;
  token: string;
};

await app.register(cors, {
  origin: true,
});

app.get(restEndpoints.healthz.path, async () => ({ ok: true }));

app.post(restEndpoints.createPairingCode.path, async (request, reply) => {
  const body = (request.body ?? {}) as CreatePairingCodeRequest;
  const created = store.createPairingCode(body);
  app.log.info({
    sessionId: created.sessionId,
    pairingCode: created.pairingCode,
    receiverName: body.receiverName ?? null,
  }, "Created pairing code");
  return reply.code(201).send(created);
});

app.post(restEndpoints.claimPairingCode.path, async (request, reply) => {
  const params = request.params as { code: string };
  const body = (request.body ?? {}) as ClaimPairingCodeRequest;

  try {
    const claimed = store.claimPairingCode(params.code, body);
    app.log.info({
      sessionId: claimed.sessionId,
      pairingCode: params.code,
      senderName: body.senderName ?? null,
    }, "Sender claimed pairing code");
    broadcastSessionState(claimed.sessionId, claimed.state, "Sender claimed the pairing code.");
    return reply.code(200).send(claimed);
  } catch (error) {
    return sendHttpError(reply, error);
  }
});

app.post(restEndpoints.sessionHeartbeat.path, async (request, reply) => {
  const params = request.params as { sessionId: string };
  const body = (request.body ?? {}) as Partial<SessionHeartbeatRequest>;

  try {
    if (!body.token || !body.role || !isParticipantRole(body.role)) {
      return reply.code(400).send(createError(errorCodes.invalidMessage, "Missing token or role."));
    }

    const heartbeat = store.heartbeat({
      sessionId: params.sessionId,
      token: body.token,
      role: body.role,
    });
    return reply.code(200).send(heartbeat);
  } catch (error) {
    return sendHttpError(reply, error);
  }
});

app.post(restEndpoints.sessionEnd.path, async (request, reply) => {
  const params = request.params as { sessionId: string };
  const body = (request.body ?? {}) as Partial<EndSessionRequest>;

  try {
    if (!body.token || !body.role || !isParticipantRole(body.role)) {
      return reply.code(400).send(createError(errorCodes.invalidMessage, "Missing token or role."));
    }

    const ended = store.endSession({
      sessionId: params.sessionId,
      token: body.token,
      role: body.role,
      reason: body.reason,
    });
    app.log.info({
      sessionId: ended.sessionId,
      role: body.role,
      reason: body.reason ?? null,
    }, "Session ended via HTTP endpoint");
    broadcastSessionState(ended.sessionId, ended.state, body.reason ?? "Session ended.");
    return reply.code(200).send(ended);
  } catch (error) {
    return sendHttpError(reply, error);
  }
});

app.server.on("upgrade", (request, socket, head) => {
  const url = new URL(request.url ?? "", "http://localhost");
  if (url.pathname !== restEndpoints.signaling.path) {
    socket.destroy();
    return;
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit("connection", ws, request);
  });
});

wss.on("connection", (socket, request) => {
  const url = new URL(request.url ?? "", "http://localhost");
  const sessionId = url.searchParams.get("sessionId") ?? "";
  const token = url.searchParams.get("token") ?? "";
  const role = url.searchParams.get("role");

  if (!sessionId || !token || !role || !isParticipantRole(role)) {
    socket.send(JSON.stringify(createSessionError(undefined, errorCodes.invalidMessage, "Missing signaling auth.")));
    socket.close(1008, "Missing signaling auth");
    return;
  }

  try {
    const participantRole: ParticipantRole = role;
    const session = store.verifyParticipant(sessionId, token, participantRole);
    store.markConnected(session.sessionId, participantRole);
    app.log.info({
      sessionId: session.sessionId,
      role: participantRole,
    }, "Participant connected to signaling");
    const authedSocket = socket as AuthedSocket;
    authedSocket.sessionId = session.sessionId;
    authedSocket.role = participantRole;
    authedSocket.token = token;

    socket.send(JSON.stringify({
      type: "session.joined",
      sessionId: session.sessionId,
      role: participantRole,
      state: session.state,
    } satisfies ServerToClientMessage));

    broadcastSessionState(session.sessionId, session.state, `Participant ${participantRole} joined.`);
  } catch (error) {
    socket.send(JSON.stringify(createSessionError(sessionId, extractErrorCode(error), extractErrorMessage(error))));
    socket.close(1008, "Session auth failed");
    return;
  }

  socket.on("message", (raw) => {
    const message = parseMessage(raw);
    if (!message) {
      socket.send(JSON.stringify(createSessionError(sessionId, errorCodes.invalidMessage, "Unable to parse message.")));
      return;
    }

    switch (message.type) {
      case "signal.offer":
      case "signal.answer":
      case "signal.ice-candidate":
        app.log.debug({
          sessionId,
          role,
          type: message.type,
        }, "Relaying signaling message");
        relayToPeer(sessionId, role, message);
        break;
      case "session.heartbeat":
        store.heartbeat({ sessionId, token, role });
        break;
      case "session.end":
        store.endSession({ sessionId, token, role, reason: message.reason });
        app.log.info({
          sessionId,
          role,
          reason: message.reason ?? null,
        }, "Session ended via signaling message");
        broadcastSessionState(sessionId, "ended", message.reason ?? "Session ended.");
        break;
      default:
        socket.send(JSON.stringify(createSessionError(sessionId, errorCodes.invalidMessage, "Unsupported message type.")));
    }
  });

  socket.on("close", () => {
    try {
      const session = store.markReconnecting(sessionId);
      app.log.info({
        sessionId: session.sessionId,
        state: session.state,
      }, "Participant disconnected; session entering reconnecting");
      broadcastSessionState(session.sessionId, session.state, "Peer disconnected.");
    } catch {
      // Ignore close events for sessions that were already removed or never authenticated.
    }
  });
});

function relayToPeer(sessionId: string, role: "receiver" | "sender", message: ClientToServerMessage) {
  const peerRole = role === "receiver" ? "sender" : "receiver";
  const peerSocket = findSocket(sessionId, peerRole);
  if (peerSocket && peerSocket.readyState === WebSocket.OPEN) {
    peerSocket.send(JSON.stringify(message satisfies ServerToClientMessage));
    return;
  }

  app.log.warn({
    sessionId,
    from: role,
    to: peerRole,
    type: message.type,
  }, "Dropped signaling relay because peer socket is not open");
}

function findSocket(sessionId: string, role: "receiver" | "sender") {
  for (const client of wss.clients) {
    const current = client as Partial<AuthedSocket>;
    if (current.sessionId === sessionId && current.role === role) {
      return client;
    }
  }
  return undefined;
}

function broadcastSessionState(sessionId: string, state: SessionState, reason?: string) {
  for (const client of wss.clients) {
    const current = client as Partial<AuthedSocket>;
    if (current.sessionId !== sessionId || client.readyState !== WebSocket.OPEN) {
      continue;
    }

    const payload = {
      type: "session.state",
      sessionId,
      state,
      reason,
    } satisfies ServerToClientMessage;
    client.send(JSON.stringify(payload));
  }
}

function parseMessage(raw: RawData): ClientToServerMessage | undefined {
  try {
    const text = typeof raw === "string" ? raw : raw.toString("utf8");
    return JSON.parse(text) as ClientToServerMessage;
  } catch {
    return undefined;
  }
}

function createSessionError(
  sessionId: string | undefined,
  code: ProtocolError["code"],
  message: string,
): SessionErrorMessage {
  return {
    type: "session.error",
    sessionId,
    error: {
      code,
      message,
    },
  };
}

function sendHttpError(reply: { code: (statusCode: number) => { send: (payload: unknown) => unknown } }, error: unknown) {
  const code = extractErrorCode(error);
  const message = extractErrorMessage(error);
  return reply.code(statusCodeForError(code)).send(createError(code, message));
}

function createError(code: ProtocolError["code"], message: string): SessionErrorMessage["error"] {
  return { code, message };
}

function extractErrorCode(error: unknown): ProtocolError["code"] {
  if (error instanceof Error) {
    if (Object.values(errorCodes).includes(error.name as ProtocolError["code"])) {
      return error.name as ProtocolError["code"];
    }
  }
  return errorCodes.invalidMessage;
}

function extractErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Unexpected error.";
}

function statusCodeForError(code: ProtocolError["code"]): number {
  switch (code) {
    case errorCodes.sessionNotFound:
    case errorCodes.pairingCodeNotFound:
      return 404;
    case errorCodes.pairingCodeExpired:
    case errorCodes.sessionEnded:
      return 410;
    case errorCodes.pairingCodeAlreadyClaimed:
      return 409;
    case errorCodes.sessionTokenInvalid:
      return 401;
    default:
      return 400;
  }
}

await app.listen({ port, host });

app.log.info({ port, host }, "Backend listening");
