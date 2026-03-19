package com.shadow.screenmirroring.receiver.backend

import java.io.IOException
import java.net.URL

data class ReceiverBackendConfiguration(val baseUrl: URL) {
  companion object {
    fun fromString(value: String): ReceiverBackendConfiguration {
      val trimmed = value.trim()
      require(trimmed.isNotEmpty()) { "Backend URL is required." }

      val normalized = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
      } else {
        "http://$trimmed"
      }

      val url = URL(if (normalized.endsWith("/")) normalized else "$normalized/")
      return ReceiverBackendConfiguration(url)
    }
  }
}

data class ReceiverBackendProtocolError(
  val code: String,
  val message: String,
)

class ReceiverBackendException(message: String) : IOException(message)

