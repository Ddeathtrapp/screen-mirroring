package com.shadow.screenmirroring.receiver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shadow.screenmirroring.receiver.ReceiverController

@Composable
fun ReceiverHomeScreen(controller: ReceiverController) {
  val state = controller.uiState

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = "Android TV Receiver",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "Pairing, signaling, and a basic remote video rendering shell. Playback is not polished yet.",
      style = MaterialTheme.typography.bodyLarge,
    )

    ReceiverVideoPanel(
      controller = controller,
      statusText = state.renderingStatusMessage,
      modifier = Modifier.fillMaxWidth(),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(text = "Backend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
          value = state.backendBaseUrl,
          onValueChange = controller::updateBackendUrl,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Backend URL") },
          singleLine = true,
        )
        OutlinedTextField(
          value = state.receiverName,
          onValueChange = controller::updateReceiverName,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Receiver name") },
          singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(
            onClick = controller::createPairingCode,
            enabled = controller.canCreatePairingCode,
            modifier = Modifier.sizeIn(minHeight = 56.dp),
          ) {
            Text("Create pairing code")
          }
          Button(
            onClick = controller::connectSignaling,
            enabled = controller.canConnectSignaling,
            modifier = Modifier.sizeIn(minHeight = 56.dp),
          ) {
            Text("Connect signaling")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(
            onClick = controller::disconnectSignaling,
            enabled = controller.canDisconnectSignaling,
            modifier = Modifier.sizeIn(minHeight = 56.dp),
          ) {
            Text("Disconnect")
          }
          Button(
            onClick = controller::clearSession,
            modifier = Modifier.sizeIn(minHeight = 56.dp),
          ) {
            Text("Clear session")
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = "Connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = state.connectionState.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = state.statusMessage)
        Text(text = state.signalingStatusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = state.renderingStatusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = "Session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = controller.claimedSessionSummary)
        state.sessionTicket?.let { session ->
          Text(text = "Session ID: ${session.sessionId}")
          Text(text = "Pairing code: ${session.pairingCode}")
          Text(text = "Receiver token: ${session.receiverToken.take(8)}...")
          Text(text = "Backend state: ${session.state}")
          Text(text = "Expires at: ${session.expiresAt}")
          Text(text = "Signaling URL: ${session.signalingUrl}")
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = "Activity log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (state.logLines.isEmpty()) {
          Text(text = "No events yet.")
        } else {
          state.logLines.asReversed().take(12).forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = "Next native work", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = "TODO(WebRTC): harden reconnect and ICE retry behavior.")
        Text(text = "TODO(Media): polish rendering, scaling, and playback behavior after the basic remote track path is stable.")
      }
    }
  }
}
