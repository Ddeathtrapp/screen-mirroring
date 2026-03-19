package com.shadow.screenmirroring.receiver.signaling

import com.shadow.screenmirroring.receiver.ReceiverSessionTicket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class ReceiverSignalingClient(
  private val sessionTicket: ReceiverSessionTicket,
) {
  var onLifecycleEvent: ((ReceiverSignalingLifecycleEvent) -> Unit)? = null
  var onMessage: ((ReceiverSignalingMessage) -> Unit)? = null

  @Volatile
  private var socket: Socket? = null
  @Volatile
  private var readerThread: Thread? = null
  @Volatile
  private var didFinish = false
  @Volatile
  private var disconnectRequested = false

  fun connect() {
    if (readerThread != null) {
      return
    }

    disconnectRequested = false
    didFinish = false
    onLifecycleEvent?.invoke(ReceiverSignalingLifecycleEvent.Connecting)

    val thread = Thread {
      runConnection()
    }
    thread.name = "receiver-signaling"
    readerThread = thread
    thread.start()
  }

  fun disconnect(reason: String = "receiver disconnect") {
    disconnectRequested = true
    runCatching {
      sendCloseFrame(reason)
    }
    closeSocket()
    finish(expected = true, reason = reason)
  }

  private fun runConnection() {
    try {
      val uri = toWebSocketUri(sessionTicket.signalingUrl)
      val openedSocket = openSocket(uri)
      socket = openedSocket

      val input = BufferedInputStream(openedSocket.getInputStream())
      val output = BufferedOutputStream(openedSocket.getOutputStream())
      performHandshake(uri, input, output)
      readLoop(input, output)
      finish(expected = disconnectRequested, reason = "Signaling disconnected.")
    } catch (error: Exception) {
      if (disconnectRequested) {
        finish(expected = true, reason = error.message ?: "Signaling disconnected.")
      } else {
        finish(expected = false, reason = error.message ?: "Failed to connect to signaling.")
      }
    } finally {
      closeSocket()
      readerThread = null
    }
  }

  private fun performHandshake(uri: URI, input: InputStream, output: OutputStream) {
    val nonce = ByteArray(16)
    SecureRandom().nextBytes(nonce)
    val webSocketKey = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
    val path = buildRequestPath(uri)
    val hostHeader = buildHostHeader(uri)

    val request = buildString {
      append("GET $path HTTP/1.1\r\n")
      append("Host: $hostHeader\r\n")
      append("Upgrade: websocket\r\n")
      append("Connection: Upgrade\r\n")
      append("Sec-WebSocket-Key: $webSocketKey\r\n")
      append("Sec-WebSocket-Version: 13\r\n")
      append("\r\n")
    }

    output.write(request.toByteArray(Charsets.US_ASCII))
    output.flush()

    val statusLine = readLine(input) ?: throw IllegalStateException("Missing websocket response.")
    if (!statusLine.startsWith("HTTP/1.1 101")) {
      throw IllegalStateException("Unexpected websocket response: $statusLine")
    }

    var acceptHeader: String? = null
    while (true) {
      val line = readLine(input) ?: throw IllegalStateException("Unexpected websocket response end.")
      if (line.isEmpty()) {
        break
      }

      val separator = line.indexOf(':')
      if (separator <= 0) {
        continue
      }

      val name = line.substring(0, separator).trim().lowercase(Locale.US)
      val value = line.substring(separator + 1).trim()
      if (name == "sec-websocket-accept") {
        acceptHeader = value
      }
    }

    val expectedAccept = createAcceptValue(webSocketKey)
    if (acceptHeader != expectedAccept) {
      throw IllegalStateException("WebSocket handshake failed.")
    }
  }

  private fun readLoop(input: InputStream, output: OutputStream) {
    while (!disconnectRequested) {
      val frame = readFrame(input) ?: return
      when (frame.opcode) {
        0x1 -> {
          val text = frame.payload.toString(Charsets.UTF_8)
          val message = ReceiverSignalingMessage.parse(text) ?: continue
          onMessage?.invoke(message)
          if (message is ReceiverSignalingMessage.SessionJoined) {
            onLifecycleEvent?.invoke(ReceiverSignalingLifecycleEvent.Connected)
          }
          if (message is ReceiverSignalingMessage.SessionError) {
            disconnectRequested = true
            finish(expected = false, reason = "${message.error.code}: ${message.error.message}")
            return
          }
        }

        0x8 -> return
        0x9 -> writeFrame(output, opcode = 0xA, payload = frame.payload)
        0xA -> Unit
        else -> Unit
      }
    }
  }

  private fun readFrame(input: InputStream): Frame? {
    val first = input.read()
    if (first == -1) {
      return null
    }

    val second = input.read()
    if (second == -1) {
      return null
    }

    val opcode = first and 0x0F
    val masked = (second and 0x80) != 0
    var length = second and 0x7F
    if (length == 126) {
      val high = input.read()
      val low = input.read()
      if (high == -1 || low == -1) {
        return null
      }
      length = ((high and 0xFF) shl 8) or (low and 0xFF)
    } else if (length == 127) {
      var longLength = 0L
      repeat(8) {
        val next = input.read()
        if (next == -1) {
          return null
        }
        longLength = (longLength shl 8) or (next.toLong() and 0xFF)
      }
      length = longLength.toInt()
    }

    val mask = if (masked) ByteArray(4).also { readFully(input, it) } else null
    val payload = ByteArray(length)
    readFully(input, payload)

    if (mask != null) {
      for (index in payload.indices) {
        payload[index] = (payload[index].toInt() xor (mask[index % 4].toInt() and 0xFF)).toByte()
      }
    }

    return Frame(opcode = opcode, payload = payload)
  }

  private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray = ByteArray(0)) {
    val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
    val header = mutableListOf<Byte>()
    header.add((0x80 or (opcode and 0x0F)).toByte())

    val length = payload.size
    when {
      length <= 125 -> header.add((0x80 or length).toByte())
      length <= 0xFFFF -> {
        header.add((0x80 or 126).toByte())
        header.add(((length shr 8) and 0xFF).toByte())
        header.add((length and 0xFF).toByte())
      }
      else -> {
        header.add((0x80 or 127).toByte())
        var remaining = length.toLong()
        val lengthBytes = ByteArray(8)
        for (index in 7 downTo 0) {
          lengthBytes[index] = (remaining and 0xFF).toByte()
          remaining = remaining shr 8
        }
        lengthBytes.forEach { header.add(it) }
      }
    }

    output.write(header.toByteArray())
    output.write(mask)

    val maskedPayload = payload.copyOf()
    for (index in maskedPayload.indices) {
      maskedPayload[index] = (maskedPayload[index].toInt() xor (mask[index % 4].toInt() and 0xFF)).toByte()
    }
    output.write(maskedPayload)
    output.flush()
  }

  private fun sendCloseFrame(reason: String) {
    val currentSocket = socket ?: return
    val output = BufferedOutputStream(currentSocket.getOutputStream())
    val payload = reason.toByteArray(Charsets.UTF_8)
    writeFrame(output, opcode = 0x8, payload = payload)
  }

  private fun openSocket(uri: URI): Socket {
    val port = if (uri.port != -1) uri.port else defaultPort(uri.scheme)
    return when (uri.scheme?.lowercase(Locale.US)) {
      "wss", "https" -> {
        val factory = SSLSocketFactory.getDefault()
        val socket = factory.createSocket(uri.host, port) as SSLSocket
        socket.startHandshake()
        socket
      }
      else -> Socket(uri.host, port)
    }
  }

  private fun toWebSocketUri(value: String): URI {
    val uri = URI(value)
    val scheme = when (uri.scheme?.lowercase(Locale.US)) {
      "http" -> "ws"
      "https" -> "wss"
      "ws", "wss" -> uri.scheme
      else -> "ws"
    }
    return URI(
      scheme,
      uri.userInfo,
      uri.host,
      uri.port,
      uri.path,
      uri.query,
      uri.fragment,
    )
  }

  private fun buildRequestPath(uri: URI): String {
    val path = if (uri.path.isNullOrBlank()) "/" else uri.path
    return if (uri.query.isNullOrBlank()) path else "$path?${uri.query}"
  }

  private fun buildHostHeader(uri: URI): String {
    val port = uri.port
    return if (port == -1 || port == defaultPort(uri.scheme)) {
      uri.host
    } else {
      "${uri.host}:$port"
    }
  }

  private fun defaultPort(scheme: String?): Int {
    return when (scheme?.lowercase(Locale.US)) {
      "wss", "https" -> 443
      else -> 80
    }
  }

  private fun readLine(input: InputStream): String? {
    val buffer = StringBuilder()
    while (true) {
      val value = input.read()
      if (value == -1) {
        return if (buffer.isEmpty()) null else buffer.toString()
      }

      if (value == '\r'.code) {
        val next = input.read()
        if (next != '\n'.code && next != -1) {
          // Best effort only; websocket servers should terminate headers with CRLF.
        }
        return buffer.toString()
      }

      if (value == '\n'.code) {
        return buffer.toString()
      }

      buffer.append(value.toChar())
    }
  }

  private fun readFully(input: InputStream, buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
      val read = input.read(buffer, offset, buffer.size - offset)
      if (read == -1) {
        throw IllegalStateException("Unexpected end of websocket stream.")
      }
      offset += read
    }
  }

  private fun createAcceptValue(key: String): String {
    val sha1 = MessageDigest.getInstance("SHA-1")
    val digest = sha1.digest((key + magicGuid).toByteArray(Charsets.US_ASCII))
    return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
  }

  private fun finish(expected: Boolean, reason: String) {
    if (didFinish) {
      return
    }

    didFinish = true
    socket = null

    if (expected) {
      onLifecycleEvent?.invoke(ReceiverSignalingLifecycleEvent.Disconnected)
    } else {
      onLifecycleEvent?.invoke(ReceiverSignalingLifecycleEvent.Failed(reason))
    }
  }

  private fun closeSocket() {
    runCatching { socket?.close() }
    socket = null
  }

  private data class Frame(
    val opcode: Int,
    val payload: ByteArray,
  )

  private companion object {
    const val magicGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
  }
}
