package com.shadow.screenmirroring.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
  private val controller by lazy { ReceiverController(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ReceiverApp(controller = controller)
    }
  }

  override fun onDestroy() {
    controller.dispose()
    super.onDestroy()
  }
}
