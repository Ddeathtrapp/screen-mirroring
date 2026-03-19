package com.shadow.screenmirroring.receiver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shadow.screenmirroring.receiver.ReceiverController
import org.webrtc.SurfaceViewRenderer

@Composable
fun ReceiverVideoPanel(
  controller: ReceiverController,
  statusText: String,
  modifier: Modifier = Modifier,
) {
  val rendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      rendererRef.value?.let { renderer ->
        controller.unbindVideoRenderer(renderer)
      }
      rendererRef.value = null
    }
  }

  Card(modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      AndroidView(
        factory = { viewContext ->
          SurfaceViewRenderer(viewContext).apply {
            rendererRef.value = this
            controller.bindVideoRenderer(this)
          }
        },
        update = { renderer ->
          rendererRef.value = renderer
          controller.bindVideoRenderer(renderer)
        },
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f),
      )
      Text(
        text = statusText,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
      )
    }
  }
}
