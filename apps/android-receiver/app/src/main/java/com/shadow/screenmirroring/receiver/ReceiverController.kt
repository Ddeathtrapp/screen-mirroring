package com.shadow.screenmirroring.receiver

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shadow.screenmirroring.receiver.backend.ReceiverBackendClient
import com.shadow.screenmirroring.receiver.backend.ReceiverBackendConfiguration
import com.shadow.screenmirroring.receiver.signaling.ReceiverSignalingClient
import com.shadow.screenmirroring.receiver.signaling.ReceiverSignalingLifecycleEvent
import com.shadow.screenmirroring.receiver.signaling.ReceiverSignalingMessage
import com.shadow.screenmirroring.receiver.signaling.ReceiverSignalingOutboundMessage
import com.shadow.screenmirroring.receiver.webrtc.ReceiverWebRtcSession
import org.webrtc.SurfaceViewRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReceiverController(private val appContext: Context) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private val logFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

  var uiState by mutableStateOf(ReceiverUiState())
    private set

  private var signalingClient: ReceiverSignalingClient? = null
  private var webRtcSession: ReceiverWebRtcSession? = null
  private var videoRenderer: SurfaceViewRenderer? = null
  private var activeSessionId: String? = null

  val canCreatePairingCode: Boolean
    get() = uiState.backendBaseUrl.isNotBlank() &&
      uiState.connectionState !in setOf(
        ReceiverConnectionState.CreatingCode,
        ReceiverConnectionState.ConnectingSignaling,
        ReceiverConnectionState.SignalingConnected,
        ReceiverConnectionState.Negotiating,
        ReceiverConnectionState.RemoteTrackAttached,
        ReceiverConnectionState.RenderingVideo,
        ReceiverConnectionState.WebRtcFailed,
        ReceiverConnectionState.Disconnecting,
      )

  val canConnectSignaling: Boolean
    get() = uiState.sessionTicket != null &&
      signalingClient == null &&
      uiState.connectionState !in setOf(
        ReceiverConnectionState.CreatingCode,
        ReceiverConnectionState.ConnectingSignaling,
        ReceiverConnectionState.SignalingConnected,
        ReceiverConnectionState.Negotiating,
        ReceiverConnectionState.RemoteTrackAttached,
        ReceiverConnectionState.RenderingVideo,
        ReceiverConnectionState.Disconnecting,
      )

  val canDisconnectSignaling: Boolean
    get() = signalingClient != null ||
      uiState.connectionState in setOf(
        ReceiverConnectionState.ConnectingSignaling,
        ReceiverConnectionState.SignalingConnected,
        ReceiverConnectionState.Negotiating,
        ReceiverConnectionState.RemoteTrackAttached,
        ReceiverConnectionState.RenderingVideo,
        ReceiverConnectionState.WebRtcFailed,
        ReceiverConnectionState.Disconnecting,
      )

  val claimedSessionSummary: String
    get() {
      val session = uiState.sessionTicket ?: return "No receiver session yet."
      val receiverName = session.receiverName ?: uiState.receiverName.ifBlank { "unnamed receiver" }
      return "Session ${session.sessionId} | code ${session.pairingCode} | $receiverName"
    }

  fun updateBackendUrl(value: String) {
    val updated = uiState.copy(backendBaseUrl = value)
    uiState = updated

    if (updated.backendBaseUrl.isBlank()) {
      if (updated.sessionTicket != null || signalingClient != null || webRtcSession != null) {
        clearSession("Backend URL cleared. Cleared the current receiver session.")
      } else {
        uiState = updated.copy(
          connectionState = ReceiverConnectionState.Idle,
          statusMessage = "Enter a backend URL and receiver name to create a pairing code.",
          signalingStatusMessage = "No receiver session yet.",
          renderingStatusMessage = "No remote video attached yet.",
        )
      }
    } else if (updated.sessionTicket != null || signalingClient != null || webRtcSession != null) {
      clearSession("Backend URL changed. Cleared the current receiver session.")
    }
  }

  fun updateReceiverName(value: String) {
    uiState = uiState.copy(receiverName = value)
  }

  fun bindVideoRenderer(renderer: SurfaceViewRenderer) {
    if (videoRenderer === renderer) {
      return
    }

    videoRenderer = renderer
    webRtcSession?.bindRenderer(renderer)

    if (
      uiState.connectionState !in setOf(ReceiverConnectionState.RemoteTrackAttached, ReceiverConnectionState.RenderingVideo) &&
      (uiState.sessionTicket != null || signalingClient != null || webRtcSession != null)
    ) {
      setState(renderingStatusMessage = "Video renderer attached. Waiting for remote track.")
    }
  }

  fun unbindVideoRenderer(renderer: SurfaceViewRenderer) {
    if (videoRenderer !== renderer) {
      return
    }

    if (webRtcSession != null) {
      webRtcSession?.unbindRenderer(renderer)
    } else {
      runCatching { renderer.release() }
    }
    videoRenderer = null
    setState(renderingStatusMessage = "Video renderer detached.")
  }

  fun createPairingCode() {
    if (!canCreatePairingCode) {
      return
    }

    val backendUrl = uiState.backendBaseUrl.trim()
    val receiverName = uiState.receiverName.trim().takeIf { it.isNotBlank() }

    setState(
      connectionState = ReceiverConnectionState.CreatingCode,
      statusMessage = "Creating pairing code...",
      signalingStatusMessage = "Waiting for backend session.",
      renderingStatusMessage = "No remote video attached yet.",
      sessionTicket = null,
      clearSessionTicket = true,
    )
    appendLog("Creating pairing code via $backendUrl")

    executor.execute {
      try {
        val backend = ReceiverBackendClient(ReceiverBackendConfiguration.fromString(backendUrl))
        val session = backend.createPairingCode(receiverName)
        signalingClient?.disconnect("Replaced by a new receiver session.")
        signalingClient = null
        disposeWebRtcSession()
        activeSessionId = session.sessionId
        setState(
          connectionState = ReceiverConnectionState.CodeCreated,
          statusMessage = "Created pairing code ${session.pairingCode}. Ready to connect signaling.",
          signalingStatusMessage = "Signaling URL is ready but not connected yet.",
          renderingStatusMessage = "No remote video attached yet.",
          sessionTicket = session,
        )
        appendLog("Created session ${session.sessionId} for pairing code ${session.pairingCode}.")
      } catch (error: Exception) {
        signalingClient?.disconnect("Pairing code creation failed.")
        signalingClient = null
        disposeWebRtcSession()
        activeSessionId = null
        setState(
          connectionState = ReceiverConnectionState.Idle,
          statusMessage = friendlyMessage(error),
          signalingStatusMessage = "No receiver session yet.",
          renderingStatusMessage = "No remote video attached yet.",
          sessionTicket = null,
          clearSessionTicket = true,
        )
        appendLog("Create pairing code failed: ${friendlyMessage(error)}")
      }
    }
  }

  fun connectSignaling() {
    val ticket = uiState.sessionTicket ?: run {
      setState(
        connectionState = ReceiverConnectionState.Idle,
        statusMessage = "Create a pairing code before connecting signaling.",
        signalingStatusMessage = "No receiver session yet.",
      )
      return
    }

    if (signalingClient != null) {
      return
    }

    activeSessionId = ticket.sessionId
    val client = ReceiverSignalingClient(ticket)
    signalingClient = client
    setState(
      connectionState = ReceiverConnectionState.ConnectingSignaling,
      statusMessage = "Connecting signaling for session ${ticket.sessionId}...",
      signalingStatusMessage = "Opening signaling socket at ${ticket.signalingUrl}.",
      renderingStatusMessage = "Waiting for signaling join.",
    )
    appendLog("Connecting signaling for session ${ticket.sessionId}.")

    client.onLifecycleEvent = { event ->
      when (event) {
        ReceiverSignalingLifecycleEvent.Connecting -> {
          setState(
            connectionState = ReceiverConnectionState.ConnectingSignaling,
            statusMessage = "Connecting signaling for session ${ticket.sessionId}...",
            signalingStatusMessage = "Opening signaling socket at ${ticket.signalingUrl}.",
            renderingStatusMessage = "Waiting for signaling join.",
          )
        }

        ReceiverSignalingLifecycleEvent.Connected -> {
          setState(
            connectionState = ReceiverConnectionState.SignalingConnected,
            statusMessage = "Signaling connected for session ${ticket.sessionId}.",
            signalingStatusMessage = "Signaling connected.",
            renderingStatusMessage = "Waiting for sender offer.",
          )
        }

        ReceiverSignalingLifecycleEvent.Disconnected -> {
          signalingClient = null
          disposeWebRtcSession()
          if (uiState.sessionTicket == null) {
            setState(
              connectionState = ReceiverConnectionState.Idle,
              statusMessage = "Signaling disconnected.",
              signalingStatusMessage = "No receiver session yet.",
              renderingStatusMessage = "No remote video attached yet.",
            )
          } else {
            setState(
              connectionState = ReceiverConnectionState.CodeCreated,
              statusMessage = "Signaling disconnected. Receiver session retained.",
              signalingStatusMessage = "Ready to reconnect signaling.",
              renderingStatusMessage = "Remote video stopped.",
            )
          }
        }

        is ReceiverSignalingLifecycleEvent.Failed -> {
          signalingClient = null
          disposeWebRtcSession()
          setState(
            connectionState = ReceiverConnectionState.SignalingFailed,
            statusMessage = "Signaling failed: ${event.reason}",
            signalingStatusMessage = "Signaling failed: ${event.reason}",
            renderingStatusMessage = "Signaling failed.",
          )
        }
      }
    }

    client.onMessage = { message ->
      when (message) {
        is ReceiverSignalingMessage.SessionJoined -> {
          appendLog(message.summary)
          setState(
            connectionState = ReceiverConnectionState.SignalingConnected,
            statusMessage = "Joined signaling session ${message.sessionId} as ${message.role} (${message.state}).",
            signalingStatusMessage = message.summary,
            renderingStatusMessage = "Waiting for sender offer.",
          )
        }

        is ReceiverSignalingMessage.SessionState -> {
          appendLog(message.summary)
          val lowerState = message.state.lowercase(Locale.US)
          when (lowerState) {
            "negotiating" -> setState(
              connectionState = ReceiverConnectionState.Negotiating,
              statusMessage = message.summary,
              signalingStatusMessage = message.summary,
              renderingStatusMessage = "Negotiating WebRTC answer.",
            )

            "connected" -> setState(
              connectionState = ReceiverConnectionState.SignalingConnected,
              statusMessage = message.summary,
              signalingStatusMessage = message.summary,
            )

            "ended", "closed", "terminated" -> {
              disposeWebRtcSession()
              setState(
                connectionState = ReceiverConnectionState.CodeCreated,
                statusMessage = message.summary,
                signalingStatusMessage = message.summary,
                renderingStatusMessage = "Remote video stopped.",
              )
            }

            else -> setState(
              statusMessage = message.summary,
              signalingStatusMessage = message.summary,
            )
          }
        }

        is ReceiverSignalingMessage.SessionError -> {
          appendLog(message.summary)
          signalingClient = null
          disposeWebRtcSession()
          setState(
            connectionState = ReceiverConnectionState.SignalingFailed,
            statusMessage = "Signaling error: ${message.error.code} - ${message.error.message}",
            signalingStatusMessage = message.summary,
            renderingStatusMessage = "Signaling failed.",
          )
        }

        is ReceiverSignalingMessage.SignalOffer -> {
          appendLog(message.summary)
          setState(
            connectionState = ReceiverConnectionState.Negotiating,
            statusMessage = "Received sender offer for session ${message.sessionId}. Creating answer.",
            signalingStatusMessage = message.summary,
            renderingStatusMessage = "Negotiating WebRTC answer.",
          )

          executor.execute {
            handleOffer(message.sessionId, message.sdp)
          }
        }

        is ReceiverSignalingMessage.SignalIceCandidate -> {
          appendLog(message.summary)
          executor.execute {
            ensureWebRtcSession().addRemoteIceCandidate(message.candidate)
          }
          setState(signalingStatusMessage = message.summary)
        }

        is ReceiverSignalingMessage.SignalAnswer -> {
          appendLog(message.summary)
          setState(signalingStatusMessage = message.summary)
        }

        is ReceiverSignalingMessage.Unknown -> {
          appendLog(message.summary)
          setState(signalingStatusMessage = message.summary)
        }
      }
    }

    try {
      client.connect()
    } catch (error: Exception) {
      signalingClient = null
      disposeWebRtcSession()
      setState(
        connectionState = ReceiverConnectionState.SignalingFailed,
        statusMessage = friendlyMessage(error),
        signalingStatusMessage = "Signaling failed.",
        renderingStatusMessage = "Signaling failed.",
      )
      appendLog("Signaling connect failed: ${friendlyMessage(error)}")
    }
  }

  fun disconnectSignaling() {
    val client = signalingClient ?: run {
      if (uiState.sessionTicket == null) {
        setState(
          connectionState = ReceiverConnectionState.Idle,
          statusMessage = "No receiver session to disconnect.",
          signalingStatusMessage = "No receiver session yet.",
        )
      } else {
        setState(
          connectionState = ReceiverConnectionState.CodeCreated,
          statusMessage = "Receiver session retained.",
          signalingStatusMessage = "Ready to reconnect signaling.",
          renderingStatusMessage = "Remote video stopped.",
        )
      }
      return
    }

    setState(
      connectionState = ReceiverConnectionState.Disconnecting,
      statusMessage = "Disconnecting signaling...",
      signalingStatusMessage = "Closing signaling socket.",
      renderingStatusMessage = "Stopping remote video.",
    )
    appendLog("Disconnecting signaling.")
    signalingClient = null
    disposeWebRtcSession()
    client.disconnect("User requested receiver disconnect.")
  }

  fun clearSession(reason: String = "Receiver session cleared.") {
    val client = signalingClient
    signalingClient = null
    client?.disconnect(reason)
    disposeWebRtcSession()
    activeSessionId = null

    setState(
      connectionState = ReceiverConnectionState.Idle,
      statusMessage = reason,
      signalingStatusMessage = "No receiver session yet.",
      renderingStatusMessage = "No remote video attached yet.",
      sessionTicket = null,
      clearSessionTicket = true,
    )
    appendLog("Cleared receiver session.")
  }

  fun dispose() {
    signalingClient?.disconnect("Receiver controller disposed.")
    signalingClient = null
    disposeWebRtcSession()
    activeSessionId = null
    executor.shutdownNow()
  }

  private fun handleOffer(sessionId: String, sdp: String) {
    try {
      val session = ensureWebRtcSession()
      videoRenderer?.let { session.bindRenderer(it) }
      val answer = session.acceptOffer(sdp)
      signalingClient?.send(
        ReceiverSignalingOutboundMessage.SignalAnswer(
          sessionId = sessionId,
          sdp = answer,
        ),
      )
      appendLog("Sent receiver answer for session $sessionId.")
      setState(renderingStatusMessage = "Answer sent. Waiting for remote video.")
    } catch (error: Exception) {
      signalingClient?.disconnect("WebRTC negotiation failed.")
      signalingClient = null
      disposeWebRtcSession()
      setState(
        connectionState = ReceiverConnectionState.WebRtcFailed,
        statusMessage = "WebRTC failed: ${friendlyMessage(error)}",
        signalingStatusMessage = "WebRTC failed.",
        renderingStatusMessage = "WebRTC failed.",
      )
      appendLog("WebRTC negotiation failed: ${friendlyMessage(error)}")
    }
  }

  private fun ensureWebRtcSession(): ReceiverWebRtcSession {
    webRtcSession?.let { return it }

    val session = ReceiverWebRtcSession(
      appContext = appContext,
      onStateChange = { state, message ->
        setState(
          connectionState = state,
          statusMessage = message,
          renderingStatusMessage = message,
        )
      },
      onLocalIceCandidate = { candidate ->
        activeSessionId?.let { sessionId ->
          try {
            signalingClient?.send(
              ReceiverSignalingOutboundMessage.SignalIceCandidate(
                sessionId = sessionId,
                candidate = candidate,
              ),
            )
          } catch (error: Exception) {
            setState(
              connectionState = ReceiverConnectionState.WebRtcFailed,
              statusMessage = "Failed to send local ICE candidate: ${friendlyMessage(error)}",
              signalingStatusMessage = "WebRTC failed.",
              renderingStatusMessage = "WebRTC failed.",
            )
            appendLog("Failed to send local ICE candidate: ${friendlyMessage(error)}")
          }
        }
      },
    )

    webRtcSession = session
    if (videoRenderer != null) {
      session.bindRenderer(videoRenderer!!)
    }
    return session
  }

  private fun disposeWebRtcSession() {
    webRtcSession?.dispose()
    webRtcSession = null
    setState(renderingStatusMessage = "No remote video attached yet.")
  }

  private fun setState(
    connectionState: ReceiverConnectionState? = null,
    statusMessage: String? = null,
    signalingStatusMessage: String? = null,
    renderingStatusMessage: String? = null,
    sessionTicket: ReceiverSessionTicket? = null,
    clearSessionTicket: Boolean = false,
  ) {
    postToMain {
      val current = uiState
      uiState = current.copy(
        connectionState = connectionState ?: current.connectionState,
        statusMessage = statusMessage ?: current.statusMessage,
        signalingStatusMessage = signalingStatusMessage ?: current.signalingStatusMessage,
        renderingStatusMessage = renderingStatusMessage ?: current.renderingStatusMessage,
        sessionTicket = when {
          clearSessionTicket -> null
          sessionTicket != null -> sessionTicket
          else -> current.sessionTicket
        },
      )
    }
  }

  private fun appendLog(message: String) {
    postToMain {
      val entry = "[${timestamp()}] $message"
      val next = (uiState.logLines + entry).takeLast(80)
      uiState = uiState.copy(logLines = next)
    }
  }

  private fun postToMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      mainHandler.post(block)
    }
  }

  private fun timestamp(): String {
    return logFormat.format(Date())
  }

  private fun friendlyMessage(error: Throwable): String {
    return error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
  }
}
