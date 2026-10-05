package com.lastpenguin.pix.ui.session

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import java.text.DecimalFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Subject view (R&S 6.6): the photographer's live video, fitted to the 3:4 frame, with their name and *Leave*.
 * Pinching the video zooms the photographer's camera, with the Camera screen's readout (FR-7.1). System back asks
 * first (R&S 6.9). Owner: Real-time (#8, #10), with the guide from #9.
 */
class SubjectFragment : Fragment(R.layout.fragment_subject) {

    private val viewModel: SubjectViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val zoomFormat = DecimalFormat("0.0")
    private var renderer: SurfaceViewRenderer? = null
    private var watchdog: FrameWatchdog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSubjectBinding.bind(view)
        binding.guideOverlay.editable = false
        view.keepScreenOn = true

        val live = binding.liveView
        live.init(viewModel.video.eglContext, null)
        live.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        live.setEnableHardwareScaler(true)
        val frames = FrameWatchdog(live)
        viewModel.video.attach(frames)
        renderer = live
        watchdog = frames
        setupPinchZoom(live)
        setupZoomAccessibility(binding.zoomRatio)

        binding.leaveButton.setOnClickListener { leaveNow() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { confirmLeave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        binding.photographerName.text = ui.photographerName
                            ?.let { getString(R.string.subject_title, it) }
                            ?: getString(R.string.subject_title_placeholder)
                        // TODO(#9): binding.guideOverlay.render(ui.guide, ui.guideState).
                        val ratio = zoomFormat.format(ui.zoom)
                        binding.zoomRatio.text = getString(R.string.zoom_ratio, ratio)
                        binding.zoomRatio.contentDescription = getString(R.string.camera_zoom_description, ratio)
                        binding.zoomRatio.isEnabled = ui.canZoom
                    }
                }
                launch {
                    // "Connecting…" until the session is up, then "camera paused" whenever frames stop (Design 2.8).
                    while (true) {
                        val connected = sessionViewModel.state.value is SessionState.Connected
                        val stalled = connected && frames.nanosSinceLastFrame() > STALL_NANOS
                        binding.connectingText.isVisible = !connected || stalled
                        binding.connectingText.setText(if (connected) R.string.camera_paused else R.string.connecting)
                        delay(STALL_CHECK_MS)
                    }
                }
                launch {
                    sessionViewModel.transitions.collect { state -> onTransition(state) }
                }
            }
        }
    }

    override fun onDestroyView() {
        watchdog?.let(viewModel.video::detach)
        watchdog = null
        renderer?.release()
        renderer = null
        super.onDestroyView()
    }

    // The same gesture as CameraFragment's, aimed at the photographer's camera. The guide overlay above the video
    // does not take touches on the subject's phone in Iteration 1 (editable = false).
    @SuppressLint("ClickableViewAccessibility") // The zoom readout exposes accessible zoom actions.
    private fun setupPinchZoom(video: View) {
        var requestedZoom = 1f
        val detector =
            ScaleGestureDetector(
                requireContext(),
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        val ui = viewModel.uiState.value
                        requestedZoom = ui.zoom
                        return ui.canZoom
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val ui = viewModel.uiState.value
                        val factor = detector.scaleFactor
                        if (!ui.canZoom || !factor.isFinite() || factor <= 0f) return false
                        // Accumulate the gesture locally; the echo, not the step, decides what the readout keeps.
                        requestedZoom = (requestedZoom * factor).coerceIn(ui.minZoom, ui.maxZoom)
                        viewModel.onZoomGesture(requestedZoom, final = false)
                        return true
                    }

                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                        viewModel.onZoomGesture(requestedZoom, final = true)
                    }
                },
            ).apply {
                isQuickScaleEnabled = false
                isStylusScaleEnabled = false
            }
        video.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
    }

    private fun setupZoomAccessibility(readout: View) {
        fun adjust(factor: Float): Boolean {
            val ui = viewModel.uiState.value
            if (!ui.canZoom) return false
            val ratio = (ui.zoom * factor).coerceIn(ui.minZoom, ui.maxZoom)
            if (ratio == ui.zoom) return false
            viewModel.onZoomGesture(ratio, final = true)
            return true
        }
        ViewCompat.replaceAccessibilityAction(
            readout,
            AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
            getString(R.string.zoom_in),
        ) { _, _ -> adjust(1.1f) }
        ViewCompat.replaceAccessibilityAction(
            readout,
            AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
            getString(R.string.zoom_out),
        ) { _, _ -> adjust(1f / 1.1f) }
    }

    private fun onTransition(state: SessionState) {
        if (state !is SessionState.Ended) return
        when (state.reason) {
            EndReason.CONNECTION_LOST -> findNavController().navigate(R.id.action_subject_to_connectionLost)

            EndReason.PEER_LEFT -> {
                val name = viewModel.uiState.value.photographerName ?: getString(R.string.subject_title_placeholder)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.peer_ended_session, name),
                    Toast.LENGTH_SHORT,
                ).show()
                backToCamera()
            }

            EndReason.LEFT, EndReason.CANCELLED -> backToCamera()

            else -> {
                Toast.makeText(requireContext(), R.string.session_ended, Toast.LENGTH_SHORT).show()
                backToCamera()
            }
        }
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.leave_session_title)
            .setPositiveButton(R.string.leave) { _, _ -> leaveNow() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun leaveNow() {
        sessionViewModel.leave()
        backToCamera()
    }

    private fun backToCamera() {
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    /** Passes frames to the renderer and remembers when the last one came, to detect a paused camera. */
    private class FrameWatchdog(private val renderer: VideoSink) : VideoSink {
        private val createdNanos = System.nanoTime()

        @Volatile
        private var lastFrameNanos = 0L

        override fun onFrame(frame: VideoFrame) {
            lastFrameNanos = System.nanoTime()
            renderer.onFrame(frame)
        }

        /** Since the last frame, or since the watchdog was created if none came yet. */
        fun nanosSinceLastFrame(): Long = System.nanoTime() - maxOf(lastFrameNanos, createdNanos)
    }

    private companion object {
        const val STALL_NANOS = 2_000_000_000L
        const val STALL_CHECK_MS = 500L
    }
}
