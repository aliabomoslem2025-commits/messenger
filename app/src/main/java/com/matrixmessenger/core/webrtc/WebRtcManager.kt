package com.matrixmessenger.core.webrtc

import android.content.Context
import org.webrtc.*

/**
 * Wrapper around WebRTC PeerConnection and media management.
 * Handles the low-level WebRTC logic independent from UI.
 */
class WebRtcManager(private val context: Context) {
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var localAudioTrack: LocalAudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var audioRecord: AudioRecord? = null // Simplified, actual impl needs AudioRecord
    
    private val rtcConfig = RTCConfiguration(listOf()).apply {
        iceServers = listOf(
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        sdpSemantics = SdpSemantics.UNIFIED_PLAN
    }
    
    /**
     * Initialize WebRTC PeerConnectionFactory.
     * Must be called before any other method.
     */
    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }
    
    /**
     * Create a PeerConnection with the given observer.
     */
    fun createPeerConnection(observer: PeerConnection.Observer): Result<PeerConnection> {
        val pc = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        return if (pc != null) {
            peerConnection = pc
            Result.success(pc)
        } else {
            Result.failure(IllegalStateException("Failed to create PeerConnection"))
        }
    }
    
    /**
     * Set up local audio track.
     */
    fun createLocalAudioTrack(): LocalAudioTrack? {
        val factory = peerConnectionFactory ?: return null
        val audioSource = factory.createAudioSource(MediaConstraints())
        val track = factory.createAudioTrack("audio", audioSource)
        localAudioTrack = track
        return track
    }
    
    /**
     * Set up local video track with camera capture.
     */
    fun createLocalVideoTrack(
        surfaceTextureHelper: SurfaceTextureHelper,
        videoView: org.webrtc.SurfaceViewRenderer
    ): LocalVideoTrack? {
        val factory = peerConnectionFactory ?: return null
        
        videoCapturer = createCameraCapturer()
        val source = factory.createVideoSource(videoCapturer is CameraVideoCapturer)
        localVideoSource = source
        
        videoCapturer?.initialize(surfaceTextureHelper, context) { capturer ->
            videoCapturer = capturer
        }
        
        val eglBase = org.webrtc.EglBase.createEglBase()
        videoView.init(eglBase.eglBaseContext, null)
        videoView.setMirror(true)
        
        val track = factory.createVideoTrack("video", source)
        track.addSink(videoView)
        localVideoTrack = track
        
        startCapture(videoView.width, videoView.height, 30)
        
        return track
    }
    
    private fun createCameraCapturer(): VideoCapturer {
        val cameraEnumerator = Camera2Enumerator(context)
        val frontCamera = cameraEnumerator.deviceNames.find { 
            cameraEnumerator.isFrontFacing(it) 
        } ?: cameraEnumerator.deviceNames.firstOrNull()
            ?: throw IllegalStateException("No camera found")
        
        return Camera2Enumerator(context).createCapturer(frontCamera, null)
    }
    
    fun startCapture(width: Int, height: Int, fps: Int) {
        (videoCapturer as? CameraVideoCapturer)?.startCapture(width, height, fps)
    }
    
    fun stopCapture() {
        (videoCapturer as? CameraVideoCapturer)?.stopCapture()
    }
    
    /**
     * Toggle microphone mute state.
     */
    fun toggleMicrophone(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }
    
    /**
     * Toggle camera video track.
     */
    fun toggleCamera(enable: Boolean) {
        localVideoTrack?.setEnabled(enable)
    }
    
    /**
     * Switch between front and back camera.
     */
    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }
    
    /**
     * Add remote track to a renderer.
     */
    fun addRemoteTrack(track: MediaStreamTrack, renderer: org.webrtc.SurfaceViewRenderer?) {
        if (track.kind == "video" && renderer != null) {
            (track as? VideoTrack)?.addSink(renderer)
        }
    }
    
    /**
     * Create an Offer SDP.
     */
    fun createOffer(constraints: MediaConstraints, callback: SdpObserver) {
        peerConnection?.createOffer(callback, constraints)
    }
    
    /**
     * Create an Answer SDP.
     */
    fun createAnswer(constraints: MediaConstraints, callback: SdpObserver) {
        peerConnection?.createAnswer(callback, constraints)
    }
    
    /**
     * Set local description.
     */
    fun setLocalDescription(description: SessionDescription, callback: SdpObserver) {
        peerConnection?.setLocalDescription(callback, description)
    }
    
    /**
     * Set remote description.
     */
    fun setRemoteDescription(description: SessionDescription, callback: SdpObserver) {
        peerConnection?.setRemoteDescription(callback, description)
    }
    
    /**
     * Add ICE candidate.
     */
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
        peerConnection = null
        peerConnectionFactory = null
        localVideoTrack = null
        localAudioTrack = null
        videoCapturer = null
    }
}
