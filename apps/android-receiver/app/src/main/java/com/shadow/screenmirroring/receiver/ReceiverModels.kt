package com.shadow.screenmirroring.receiver

enum class ReceiverConnectionState {
  Idle,
  CreatingCode,
  CodeCreated,
  ConnectingSignaling,
  SignalingConnected,
  SignalingFailed,
  Disconnecting,

  ;

  val title: String
    get() = when (this) {
      Idle -> "Idle"
      CreatingCode -> "Creating code"
      CodeCreated -> "Code created"
      ConnectingSignaling -> "Connecting signaling"
      SignalingConnected -> "Signaling connected"
      SignalingFailed -> "Signaling failed"
      Disconnecting -> "Disconnecting"
    }
}

data class ReceiverSessionTicket(
  val sessionId: String,
  val pairingCode: String,
  val receiverName: String?,
  val receiverToken: String,
  val state: String,
  val expiresAt: String,
  val signalingUrl: String,
)

data class ReceiverUiState(
  val backendBaseUrl: String = "http://10.0.2.2:8787",
  val receiverName: String = "Android TV",
  val connectionState: ReceiverConnectionState = ReceiverConnectionState.Idle,
  val statusMessage: String = "Enter a backend URL and receiver name to create a pairing code.",
  val signalingStatusMessage: String = "No receiver session yet.",
  val sessionTicket: ReceiverSessionTicket? = null,
  val logLines: List<String> = emptyList(),
)

