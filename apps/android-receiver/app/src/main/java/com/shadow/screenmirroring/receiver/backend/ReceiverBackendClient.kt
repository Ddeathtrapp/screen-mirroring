package com.shadow.screenmirroring.receiver.backend

import com.shadow.screenmirroring.receiver.ReceiverSessionTicket
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ReceiverBackendClient(private val configuration: ReceiverBackendConfiguration) {
  fun createPairingCode(receiverName: String?): ReceiverSessionTicket {
    val endpoint = URL(configuration.baseUrl, "v1/pairing-codes")
    val connection = openJsonConnection(endpoint, "POST")

    val requestBody = JSONObject()
    if (!receiverName.isNullOrBlank()) {
      requestBody.put("receiverName", receiverName.trim())
    }
    writeBody(connection, requestBody.toString())

    val responseCode = connection.responseCode
    val responseBody = readBody(connection, responseCode)

    if (responseCode !in 200..299) {
      throw backendException(responseBody, responseCode)
    }

    val json = JSONObject(responseBody)
    return ReceiverSessionTicket(
      sessionId = json.getString("sessionId"),
      pairingCode = json.getString("pairingCode"),
      receiverName = receiverName?.takeIf { it.isNotBlank() }?.trim(),
      receiverToken = json.getString("receiverToken"),
      state = json.getString("state"),
      expiresAt = json.getString("expiresAt"),
      signalingUrl = URL(configuration.baseUrl, json.getString("signalingUrl")).toString(),
    )
  }

  private fun openJsonConnection(url: URL, method: String): HttpURLConnection {
    return (url.openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 10_000
      readTimeout = 10_000
      doInput = true
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
    }
  }

  private fun writeBody(connection: HttpURLConnection, body: String) {
    connection.outputStream.use { output ->
      output.write(body.toByteArray(Charsets.UTF_8))
    }
  }

  private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
    if (stream == null) {
      return ""
    }

    return stream.use { input ->
      BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
        reader.readText()
      }
    }
  }

  private fun backendException(body: String, responseCode: Int): ReceiverBackendException {
    val message = runCatching {
      val json = JSONObject(body)
      val code = if (json.has("code")) json.optString("code") else json.optJSONObject("error")?.optString("code")
      val errorMessage = if (json.has("message")) json.optString("message") else json.optJSONObject("error")?.optString("message")
      if (!code.isNullOrBlank() || !errorMessage.isNullOrBlank()) {
        "${code ?: "BACKEND_ERROR"}: ${errorMessage ?: "Unknown backend error."}"
      } else {
        json.optString("message", "Backend returned HTTP $responseCode.")
      }
    }.getOrElse {
      if (body.isNotBlank()) {
        "Backend returned HTTP $responseCode: ${body.trim()}"
      } else {
        "Backend returned HTTP $responseCode."
      }
    }

    return ReceiverBackendException(message)
  }
}
