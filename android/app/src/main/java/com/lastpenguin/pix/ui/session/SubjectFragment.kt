// AI-generated with ChatGPT Codex and Claude Code, 2026-10-18, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.ui.session

import android.os.Bundle
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
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.camera.ExposureScale
import com.lastpenguin.pix.ui.camera.PinchZoomController
import com.lastpenguin.pix.ui.camera.TrackedSliderController
import java.text.DecimalFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Subject view (R&S 6.6): the photographer's live video, fitted to their frame, with their name and *Leave*.
 * A pinch on the video zooms the photographer's camera while guide-edit mode is off, with the Camera screen's
 * readout (FR-7.1, AD-17); with the mode on, drags and pinches move the photographer's guide (FR-7.2). The
 * brightness slider and the flash button are the photographer's controls on this phone (FR-7.3). System back asks
 * first (R&S 6.9). Owner: Real-time (#8, #10, #57), with the guide from #9.
 */
class SubjectFragment : Fragment(R.layout.fragment_subject) {

    private val viewModel: SubjectViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val zoomFormat = DecimalFormat("0.0")
    private var pinchZoom: PinchZoomController? = null
    private var brightnessSlider: TrackedSliderController? = null
    private var renderer: SurfaceViewRenderer? = null
    private var watchdog: FrameWatchdog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSubjectBinding.bind(view)
        binding.guideOverlay.editable = false
        binding.guideOverlay.onGesture = viewModel::onGuideGesture
        view.keepScreenOn = true

        val live = binding.liveView
        live.init(viewModel.video.eglContext, null)
        live.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        live.setEnableHardwareScaler(true)
        val frames = FrameWatchdog(live)
        viewModel.video.attach(frames)
        renderer = live
        watchdog = frames
        // The whole area takes the pinch, the margins beside a video of another shape too.
        pinchZoom = PinchZoomController(
            binding.videoContainer,
            range = { viewModel.uiState.value.let { if (it.canZoom) it.minZoom..it.maxZoom else null } },
            current = { viewModel.uiState.value.zoom },
            onZoom = viewModel::onZoomGesture,
        )
        setupZoomAccessibility(binding.zoomReadout)
        binding.editGuideButton.setOnClickListener {
            viewModel.setGuideEditing(!viewModel.uiState.value.guideEditing)
        }
        // The photographer's brightness and flash from this phone (FR-7.3), on the Camera screen's scale.
        brightnessSlider = TrackedSliderController(
            binding.brightnessSlider,
            { ExposureScale.formatEv(sliderIndex(it), viewModel.uiState.value.exposureStep) },
        ) { value, final -> viewModel.onExposureGesture(sliderIndex(value), final) }
        binding.brightnessButton.setOnClickListener {
            viewModel.setBrightnessOpen(!viewModel.uiState.value.brightnessOpen)
        }
        binding.flashButton.setOnClickListener { viewModel.onFlashTap() }

        binding.leaveButton.setOnClickListener { leaveNow() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { confirmLeave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        binding.photographerName.text = ui.photographerName
                            ?.let { getString(R.string.subject_title, it) }
                            ?: getString(R.string.subject_title_placeholder)
                        // The video fits itself to the stream; the grid and the guide fit to the same frame (AD-10).
                        binding.compositionOverlay.frameAspect = ui.aspect.frameAspect
                        binding.guideOverlay.frameAspect = ui.aspect.frameAspect
                        binding.guideOverlay.render(ui.guide, ui.guideState)
                        binding.compositionOverlay.isVisible = ui.connected
                        // Guide-edit mode (AD-17): the overlay owns the video's touches while on; the pinch zooms while off.
                        binding.guideOverlay.editable = ui.guideEditing
                        pinchZoom?.enabled = !ui.guideEditing
                        // The toggle sits beside the readout while a guide is on the video; a hidden guide cannot be edited.
                        binding.editGuideButton.isVisible = ui.guide != null
                        binding.editGuideButton.isEnabled = ui.connected && ui.guideState.visible
                        binding.editGuideButton.isChecked = ui.guideEditing
                        renderCameraControls(binding, ui)
                        val ratio = zoomFormat.format(ui.zoom)
                        binding.zoomReadout.text = getString(R.string.zoom_ratio, ratio)
                        binding.zoomReadout.contentDescription = getString(R.string.camera_zoom_description, ratio)
                        binding.zoomReadout.isEnabled = ui.canZoom
                        if (!ui.canZoom) pinchZoom?.finishInteraction()
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

    override fun onPause() {
        pinchZoom?.finishInteraction()
        brightnessSlider?.finishInteraction()
        super.onPause()
    }

    override fun onDestroyView() {
        pinchZoom?.finishInteraction()
        pinchZoom = null
        brightnessSlider?.finishInteraction()
        brightnessSlider = null
        watchdog?.let(viewModel.video::detach)
        watchdog = null
        renderer?.release()
        renderer = null
        super.onDestroyView()
    }

    /** Brightness and flash, hidden when the photographer's phone has no range or no flash (FR-7.3). */
    private fun renderCameraControls(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        binding.brightnessButton.isVisible = ui.hasBrightness
        binding.brightnessButton.isChecked = ui.brightnessOpen
        binding.brightnessButton.isEnabled = ui.connected
        binding.brightnessPanel.isVisible = ui.brightnessOpen
        brightnessSlider?.render(
            1f,
            ExposureScale.sliderMax(ui.exposureRange),
            ExposureScale.sliderValue(ui.exposure, ui.exposureRange),
            enabled = ui.connected && ui.hasBrightness,
        )
        binding.brightnessValue.text = ExposureScale.formatEv(ui.exposure, ui.exposureStep)
        binding.flashButton.isVisible = ui.hasFlash
        binding.flashButton.isEnabled = ui.connected
        binding.flashButton.setIconResource(
            when (ui.flash) {
                FlashMode.OFF -> R.drawable.ic_flash_off
                FlashMode.AUTO -> R.drawable.ic_flash_auto
                FlashMode.ON -> R.drawable.ic_flash_on
            },
        )
        binding.flashButton.contentDescription = getString(
            when (ui.flash) {
                FlashMode.OFF -> R.string.flash_off
                FlashMode.AUTO -> R.string.flash_auto
                FlashMode.ON -> R.string.flash_on
            },
        )
    }

    /** The exposure index for a brightness slider position (ExposureScale, shared with the Camera screen). */
    private fun sliderIndex(value: Float): Int = ExposureScale.index(value, viewModel.uiState.value.exposureRange)

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
        pinchZoom?.finishInteraction()
        brightnessSlider?.finishInteraction()
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
