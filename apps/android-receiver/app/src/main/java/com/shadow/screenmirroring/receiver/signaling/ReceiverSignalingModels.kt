package com.shadow.screenmirroring.receiver.signaling

import com.shadow.screenmirroring.receiver.backend.ReceiverBackendProtocolError
import org.json.JSONObject

sealed class ReceiverSignalingLifecycleEvent {
  object Connecting : ReceiverSignalingLifecycleEvent()
  object Connected : ReceiverSignalingLifecycleEvent()
  object Disconnected : ReceiverSignalingLifecycleEvent()
  data class Failed(val reason: String) : ReceiverSignalingLifecycleEvent()
}

sealed class ReceiverSignalingMessage {
  data class SessionJoined(val sessionId: String, val role: String, val state: String) : ReceiverSignalingMessage()
  data class SessionState(val sessionId: String, val state: String, val reason: String?) : ReceiverSignalingMessage()
  data class SessionError(val sessionId: String?, val error: ReceiverBackendProtocolError) : ReceiverSignalingMessage()
  data class SignalOffer(val sessionId: String, val sdp: String) : ReceiverSignalingMessage()
  data class SignalAnswer(val sessionId: String, val sdp: String) : ReceiverSignalingMessage()
  data class SignalIceCandidate(val sessionId: String, val candidate: ReceiverSignalingIceCandidate) : ReceiverSignalingMessage()
  data class Unknown(val type: String) : ReceiverSignalingMessage()

  val summary: String
    get() = when (this) {
      is SessionJoined -> "Joined signaling session $sessionId as $role ($state)."
      is SessionState -> if (!reason.isNullOrBlank()) {
        "Session $sessionId state $state: $reason"
      } else {
        "Session $sessionId state $state."
      }
      is SessionError -> {
        val prefix = sessionId?.let { "Session $it" } ?: "Signaling"
        "$prefix error: ${error.code} - ${error.message}"
      }
      is SignalOffer -> "Received offer for session $sessionId."
      is SignalAnswer -> "Received answer for session $sessionId."
      is SignalIceCandidate -> "Received ICE candidate for session $sessionId."
      is Unknown -> "Unhandled signaling message $type."
    }

  companion object {
    fun parse(raw: String): ReceiverSignalingMessage? {
      val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return null
      return when (envelope.optString("type")) {
        "session.joined" -> {
          val sessionId = envelope.optString("sessionId")
          val role = envelope.optString("role")
          val state = envelope.optString("state")
          if (sessionId.isBlank() || role.isBlank() || state.isBlank()) null
          else SessionJoined(sessionId, role, state)
        }

        "session.state" -> {
          val sessionId = envelope.optString("sessionId")
          val state = envelope.optString("state")
          if (sessionId.isBlank() || state.isBlank()) null
          else SessionState(sessionId, state, envelope.optString("reason").takeIf { it.isNotBlank() })
        }

        "session.error" -> {
          val errorJson = envelope.optJSONObject("error") ?: return null
          SessionError(
            sessionId = envelope.optString("sessionId").takeIf { it.isNotBlank() },
            error = ReceiverBackendProtocolError(
              code = errorJson.optString("code", "INVALID_MESSAGE"),
              message = errorJson.optString("message", "Unknown signaling error."),
            ),
          )
        }

        "signal.offer" -> {
          val sessionId = envelope.optString("sessionId")
          val sdp = envelope.optString("sdp")
          if (sessionId.isBlank() || sdp.isBlank()) null else SignalOffer(sessionId, sdp)
        }

        "signal.answer" -> {
          val sessionId = envelope.optString("sessionId")
          val sdp = envelope.optString("sdp")
          if (sessionId.isBlank() || sdp.isBlank()) null else SignalAnswer(sessionId, sdp)
        }

        "signal.ice-candidate" -> {
          val sessionId = envelope.optString("sessionId")
          val candidateJson = envelope.optJSONObject("candidate") ?: return null
          if (sessionId.isBlank()) {
            null
          } else {
            SignalIceCandidate(sessionId, ReceiverSignalingIceCandidate.fromJson(candidateJson))
          }
        }

        else -> Unknown(envelope.optString("type").ifBlank { "unknown" })
      }
    }
  }
}

sealed class ReceiverSignalingOutboundMessage {
  data class SignalAnswer(val sessionId: String, val sdp: String) : ReceiverSignalingOutboundMessage()
  data class SignalIceCandidate(val sessionId: String, val candidate: ReceiverSignalingIceCandidate) : ReceiverSignalingOutboundMessage()
}

data class ReceiverSignalingIceCandidate(
  val candidate: String?,
  val sdpMid: String?,
  val sdpMLineIndex: Int?,
  val usernameFragment: String?,
) {
  companion object {
    fun fromJson(json: JSONObject): ReceiverSignalingIceCandidate {
      return ReceiverSignalingIceCandidate(
        candidate = json.optString("candidate").takeIf { it.isNotBlank() },
        sdpMid = json.optString("sdpMid").takeIf { it.isNotBlank() },
        sdpMLineIndex = if (json.has("sdpMLineIndex") && !json.isNull("sdpMLineIndex")) json.optInt("sdpMLineIndex") else null,
        usernameFragment = json.optString("usernameFragment").takeIf { it.isNotBlank() },
      )
    }
  }

  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("candidate", candidate)
      put("sdpMid", sdpMid)
      if (sdpMLineIndex != null) {
        put("sdpMLineIndex", sdpMLineIndex)
      }
      if (usernameFragment != null) {
        put("usernameFragment", usernameFragment)
      }
    }
  }
}

fun ReceiverSignalingOutboundMessage.toJson(): JSONObject {
  return when (this) {
    is ReceiverSignalingOutboundMessage.SignalAnswer -> JSONObject().apply {
      put("type", "signal.answer")
      put("sessionId", sessionId)
      put("sdp", sdp)
    }
    is ReceiverSignalingOutboundMessage.SignalIceCandidate -> JSONObject().apply {
      put("type", "signal.ice-candidate")
      put("sessionId", sessionId)
      put("candidate", candidate.toJson())
    }
  }
}
