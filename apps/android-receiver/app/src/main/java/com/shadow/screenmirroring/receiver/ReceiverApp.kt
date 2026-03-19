package com.shadow.screenmirroring.receiver

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.shadow.screenmirroring.receiver.ui.ReceiverHomeScreen

@Composable
fun ReceiverApp(controller: ReceiverController) {
  MaterialTheme {
    Surface {
      ReceiverHomeScreen(controller = controller)
    }
  }
}

