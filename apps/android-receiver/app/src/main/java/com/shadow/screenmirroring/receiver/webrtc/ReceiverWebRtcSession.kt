package com.shadow.screenmirroring.receiver.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.shadow.screenmirroring.receiver.ReceiverConnectionState
import com.shadow.screenmirroring.receiver.signaling.ReceiverSignalingIceCandidate
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.RendererCommon
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ReceiverWebRtcSession(
  private val appContext: Context,
  private val onStateChange: (ReceiverConnectionState, String) -> Unit,
  private val onLocalIceCandidate: (ReceiverSignalingIceCandidate) -> Unit,
) {
  private val eglBase = EglBase.create()
  private val audioDeviceModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
  private val factory: PeerConnectionFactory = createFactory()

  private val mainHandler = Handler(Looper.getMainLooper())

  private var peerConnection: PeerConnection? = null
  private var remoteVideoTrack: VideoTrack? = null
  private var renderer: SurfaceViewRenderer? = null
  private var rendererReady = false
  private val disposed = AtomicBoolean(false)
  private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()

  fun bindRenderer(renderer: SurfaceViewRenderer) {
    if (this.renderer === renderer && rendererReady) {
      if (remoteVideoTrack != null) {
        onStateChange(ReceiverConnectionState.RenderingVideo, "Rendering remote video.")
      }
      return
    }

    val previousRenderer = this.renderer
    if (previousRenderer != null && previousRenderer !== renderer) {
      remoteVideoTrack?.removeSink(previousRenderer)
      releaseRendererOnMainThread(previousRenderer)
    }

    initRendererOnMainThread(renderer)

    this.renderer = renderer
    rendererReady = true
    remoteVideoTrack?.addSink(renderer)

    if (remoteVideoTrack != null) {
      onStateChange(ReceiverConnectionState.RenderingVideo, "Rendering remote video.")
    }
  }

  fun unbindRenderer(renderer: SurfaceViewRenderer) {
    if (this.renderer !== renderer) {
      return
    }

    remoteVideoTrack?.removeSink(renderer)
    this.renderer = null
    rendererReady = false
    releaseRendererOnMainThread(renderer)
  }

  fun acceptOffer(sdp: String): String {
    val connection = ensurePeerConnection()
    onStateChange(ReceiverConnectionState.Negotiating, "Applying sender offer and creating answer.")

    setRemoteDescription(connection, sdp)
    flushPendingRemoteIceCandidates(connection)
    val answer = createAnswer(connection)
    setLocalDescription(connection, answer)

    if (remoteVideoTrack != null && rendererReady && renderer != null) {
      onStateChange(ReceiverConnectionState.RenderingVideo, "Rendering remote video.")
    }

    return connection.localDescription?.description
      ?: throw IllegalStateException("Receiver did not create a local answer.")
  }

  fun addRemoteIceCandidate(candidate: ReceiverSignalingIceCandidate) {
    val candidateText = candidate.candidate ?: return
    val iceCandidate = IceCandidate(
      candidate.sdpMid,
      candidate.sdpMLineIndex ?: 0,
      candidateText,
    )
    val connection = peerConnection
    if (connection == null) {
      synchronized(pendingRemoteIceCandidates) {
        pendingRemoteIceCandidates.add(iceCandidate)
      }
      return
    }

    if (!connection.addIceCandidate(iceCandidate)) {
      synchronized(pendingRemoteIceCandidates) {
        pendingRemoteIceCandidates.add(iceCandidate)
      }
    }
  }

  fun dispose() {
    if (!disposed.compareAndSet(false, true)) {
      return
    }

    renderer?.let { currentRenderer ->
      runCatching { remoteVideoTrack?.removeSink(currentRenderer) }
      releaseRendererOnMainThread(currentRenderer)
    }
    remoteVideoTrack = null
    renderer = null
    rendererReady = false
    pendingRemoteIceCandidates.clear()
    peerConnection?.close()
    peerConnection?.dispose()
    peerConnection = null
    audioDeviceModule.release()
    factory.dispose()
    eglBase.release()
  }

  private fun ensurePeerConnection(): PeerConnection {
    peerConnection?.let { return it }

    val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
      rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
    }

    val connection = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
      override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) = Unit

      override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        if (newState == PeerConnection.PeerConnectionState.FAILED) {
          onStateChange(ReceiverConnectionState.WebRtcFailed, "WebRTC peer connection failed.")
        }
      }

      override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        if (newState == PeerConnection.IceConnectionState.FAILED) {
          onStateChange(ReceiverConnectionState.WebRtcFailed, "WebRTC ICE connection failed.")
        }
      }

      override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

      override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

      override fun onIceCandidate(candidate: IceCandidate?) {
        candidate ?: return
        onLocalIceCandidate(
          ReceiverSignalingIceCandidate(
            candidate = candidate.sdp,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            usernameFragment = null,
          ),
        )
      }

      override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

      override fun onAddStream(stream: MediaStream?) = Unit

      override fun onRemoveStream(stream: MediaStream?) = Unit

      override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) = Unit

      override fun onRenegotiationNeeded() = Unit

      override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit

      override fun onTrack(transceiver: RtpTransceiver?) {
        val track = transceiver?.receiver?.track()
        if (track is VideoTrack) {
          remoteVideoTrack?.let { previousTrack ->
            renderer?.let(previousTrack::removeSink)
          }
          remoteVideoTrack = track
          onStateChange(ReceiverConnectionState.RemoteTrackAttached, "Remote track attached.")
          val currentRenderer = renderer
          if (currentRenderer != null) {
            track.addSink(currentRenderer)
            onStateChange(ReceiverConnectionState.RenderingVideo, "Rendering remote video.")
          }
        }
      }
    }) ?: throw IllegalStateException("Unable to create WebRTC peer connection.")

    connection.addTransceiver(
      MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
      RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
    )
    connection.addTransceiver(
      MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
      RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
    )

    peerConnection = connection
    return connection
  }

  private fun flushPendingRemoteIceCandidates(connection: PeerConnection) {
    val pending = synchronized(pendingRemoteIceCandidates) {
      if (pendingRemoteIceCandidates.isEmpty()) {
        return
      }

      val snapshot = pendingRemoteIceCandidates.toList()
      pendingRemoteIceCandidates.clear()
      snapshot
    }
    pending.forEach { iceCandidate ->
      connection.addIceCandidate(iceCandidate)
    }
  }

  private fun setRemoteDescription(connection: PeerConnection, sdp: String) {
    val latch = CountDownLatch(1)
    var failure: String? = null

    connection.setRemoteDescription(object : SdpObserver {
      override fun onCreateSuccess(desc: SessionDescription?) = Unit

      override fun onSetSuccess() {
        latch.countDown()
      }

      override fun onCreateFailure(error: String?) = Unit

      override fun onSetFailure(error: String?) {
        failure = error
        latch.countDown()
      }
    }, SessionDescription(SessionDescription.Type.OFFER, sdp))

    awaitLatch(latch, failure, "Failed to set remote description.")
  }

  private fun createAnswer(connection: PeerConnection): SessionDescription {
    val latch = CountDownLatch(1)
    val result = arrayOfNulls<SessionDescription>(1)
    var failure: String? = null

    connection.createAnswer(object : SdpObserver {
      override fun onCreateSuccess(desc: SessionDescription?) {
        result[0] = desc
        latch.countDown()
      }

      override fun onSetSuccess() = Unit

      override fun onCreateFailure(error: String?) {
        failure = error
        latch.countDown()
      }

      override fun onSetFailure(error: String?) = Unit
    }, MediaConstraints())

    awaitLatch(latch, failure, "Failed to create answer.")
    return result[0] ?: throw IllegalStateException("WebRTC did not return an answer.")
  }

  private fun setLocalDescription(connection: PeerConnection, answer: SessionDescription) {
    val latch = CountDownLatch(1)
    var failure: String? = null

    connection.setLocalDescription(object : SdpObserver {
      override fun onCreateSuccess(desc: SessionDescription?) = Unit

      override fun onSetSuccess() {
        latch.countDown()
      }

      override fun onCreateFailure(error: String?) = Unit

      override fun onSetFailure(error: String?) {
        failure = error
        latch.countDown()
      }
    }, answer)

    awaitLatch(latch, failure, "Failed to set local description.")
  }

  private fun awaitLatch(latch: CountDownLatch, failure: String?, defaultMessage: String) {
    if (!latch.await(10, TimeUnit.SECONDS)) {
      throw IllegalStateException(defaultMessage)
    }

    if (!failure.isNullOrBlank()) {
      throw IllegalStateException(failure)
    }
  }

  private fun createFactory(): PeerConnectionFactory {
    if (peerConnectionFactoryInitialized.compareAndSet(false, true)) {
      PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(appContext)
          .createInitializationOptions(),
      )
    }

    val videoEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
    val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

    return PeerConnectionFactory.builder()
      .setAudioDeviceModule(audioDeviceModule)
      .setVideoEncoderFactory(videoEncoderFactory)
      .setVideoDecoderFactory(videoDecoderFactory)
      .createPeerConnectionFactory()
  }

  private companion object {
    val peerConnectionFactoryInitialized = AtomicBoolean(false)
  }

  private fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      mainHandler.post(block)
    }
  }

  private fun initRendererOnMainThread(renderer: SurfaceViewRenderer) {
    runOnMainThread {
      renderer.init(eglBase.eglBaseContext, null)
      renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
      renderer.setEnableHardwareScaler(true)
      renderer.setMirror(false)
    }
  }

  private fun releaseRendererOnMainThread(renderer: SurfaceViewRenderer) {
    runOnMainThread {
      runCatching { renderer.release() }
    }
  }
}
