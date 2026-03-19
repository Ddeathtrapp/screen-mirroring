package com.shadow.screenmirroring.receiver

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReceiverController {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private val logFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

  var uiState by mutableStateOf(ReceiverUiState())
    private set

  private var signalingClient: ReceiverSignalingClient? = null

  val canCreatePairingCode: Boolean
    get() = uiState.backendBaseUrl.isNotBlank() && uiState.connectionState !in setOf(
      ReceiverConnectionState.CreatingCode,
      ReceiverConnectionState.ConnectingSignaling,
      ReceiverConnectionState.Disconnecting,
    )

  val canConnectSignaling: Boolean
    get() = uiState.sessionTicket != null &&
      signalingClient == null &&
      uiState.connectionState !in setOf(
        ReceiverConnectionState.CreatingCode,
        ReceiverConnectionState.ConnectingSignaling,
        ReceiverConnectionState.Disconnecting,
      )

  val canDisconnectSignaling: Boolean
    get() = signalingClient != null ||
      uiState.connectionState in setOf(
        ReceiverConnectionState.ConnectingSignaling,
        ReceiverConnectionState.SignalingConnected,
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
      if (updated.sessionTicket != null || signalingClient != null) {
        clearSession("Backend URL cleared. Cleared the current receiver session.")
      } else {
        uiState = updated.copy(
          connectionState = ReceiverConnectionState.Idle,
          statusMessage = "Enter a backend URL and receiver name to create a pairing code.",
          signalingStatusMessage = "No receiver session yet.",
        )
      }
    } else if (updated.sessionTicket != null || signalingClient != null) {
      clearSession("Backend URL changed. Cleared the current receiver session.")
    }
  }

  fun updateReceiverName(value: String) {
    uiState = uiState.copy(receiverName = value)
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
        setState(
          connectionState = ReceiverConnectionState.CodeCreated,
          statusMessage = "Created pairing code ${session.pairingCode}. Ready to connect signaling.",
          signalingStatusMessage = "Signaling URL is ready but not connected yet.",
          sessionTicket = session,
        )
        appendLog("Created session ${session.sessionId} for pairing code ${session.pairingCode}.")
      } catch (error: Exception) {
        signalingClient?.disconnect("Pairing code creation failed.")
        signalingClient = null
        setState(
          connectionState = ReceiverConnectionState.Idle,
          statusMessage = friendlyMessage(error),
          signalingStatusMessage = "No receiver session yet.",
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

    val client = ReceiverSignalingClient(ticket)
    signalingClient = client
    setState(
      connectionState = ReceiverConnectionState.ConnectingSignaling,
      statusMessage = "Connecting signaling for session ${ticket.sessionId}...",
      signalingStatusMessage = "Opening signaling socket at ${ticket.signalingUrl}.",
    )
    appendLog("Connecting signaling for session ${ticket.sessionId}.")

    client.onLifecycleEvent = { event ->
      when (event) {
        ReceiverSignalingLifecycleEvent.Connecting -> {
          setState(
            connectionState = ReceiverConnectionState.ConnectingSignaling,
            statusMessage = "Connecting signaling for session ${ticket.sessionId}...",
            signalingStatusMessage = "Opening signaling socket at ${ticket.signalingUrl}.",
          )
        }

        ReceiverSignalingLifecycleEvent.Connected -> {
          setState(
            connectionState = ReceiverConnectionState.SignalingConnected,
            statusMessage = "Signaling connected for session ${ticket.sessionId}.",
            signalingStatusMessage = "Signaling connected.",
          )
        }

        ReceiverSignalingLifecycleEvent.Disconnected -> {
          signalingClient = null
          if (uiState.sessionTicket == null) {
            setState(
              connectionState = ReceiverConnectionState.Idle,
              statusMessage = "Signaling disconnected.",
              signalingStatusMessage = "No receiver session yet.",
            )
          } else {
            setState(
              connectionState = ReceiverConnectionState.CodeCreated,
              statusMessage = "Signaling disconnected. Receiver session retained.",
              signalingStatusMessage = "Ready to reconnect signaling.",
            )
          }
        }

        is ReceiverSignalingLifecycleEvent.Failed -> {
          signalingClient = null
          setState(
            connectionState = ReceiverConnectionState.SignalingFailed,
            statusMessage = "Signaling failed: ${event.reason}",
            signalingStatusMessage = "Signaling failed: ${event.reason}",
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
          )
        }

        is ReceiverSignalingMessage.SessionState -> {
          appendLog(message.summary)
          if (message.state.equals("ended", ignoreCase = true)) {
            setState(
              connectionState = ReceiverConnectionState.CodeCreated,
              statusMessage = message.summary,
              signalingStatusMessage = message.summary,
            )
          } else {
            setState(signalingStatusMessage = message.summary)
          }
        }

        is ReceiverSignalingMessage.SessionError -> {
          appendLog(message.summary)
          signalingClient = null
          setState(
            connectionState = ReceiverConnectionState.SignalingFailed,
            statusMessage = "Signaling error: ${message.error.code} - ${message.error.message}",
            signalingStatusMessage = message.summary,
          )
        }

        is ReceiverSignalingMessage.SignalOffer,
        is ReceiverSignalingMessage.SignalAnswer,
        is ReceiverSignalingMessage.SignalIceCandidate,
        is ReceiverSignalingMessage.Unknown,
        -> {
          appendLog(message.summary)
          setState(signalingStatusMessage = message.summary)
        }
      }
    }

    try {
      client.connect()
    } catch (error: Exception) {
      signalingClient = null
      setState(
        connectionState = ReceiverConnectionState.SignalingFailed,
        statusMessage = friendlyMessage(error),
        signalingStatusMessage = "Signaling failed.",
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
        )
      }
      return
    }

    setState(
      connectionState = ReceiverConnectionState.Disconnecting,
      statusMessage = "Disconnecting signaling...",
      signalingStatusMessage = "Closing signaling socket.",
    )
    appendLog("Disconnecting signaling.")
    signalingClient = null
    client.disconnect("User requested receiver disconnect.")
  }

  fun clearSession(reason: String = "Receiver session cleared.") {
    val client = signalingClient
    signalingClient = null
    client?.disconnect(reason)

    setState(
      connectionState = ReceiverConnectionState.Idle,
      statusMessage = reason,
      signalingStatusMessage = "No receiver session yet.",
      sessionTicket = null,
      clearSessionTicket = true,
    )
    appendLog("Cleared receiver session.")
  }

  fun dispose() {
    signalingClient?.disconnect("Receiver controller disposed.")
    signalingClient = null
    executor.shutdownNow()
  }

  private fun setState(
    connectionState: ReceiverConnectionState? = null,
    statusMessage: String? = null,
    signalingStatusMessage: String? = null,
    sessionTicket: ReceiverSessionTicket? = null,
    clearSessionTicket: Boolean = false,
  ) {
    postToMain {
      val current = uiState
      uiState = current.copy(
        connectionState = connectionState ?: current.connectionState,
        statusMessage = statusMessage ?: current.statusMessage,
        signalingStatusMessage = signalingStatusMessage ?: current.signalingStatusMessage,
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
