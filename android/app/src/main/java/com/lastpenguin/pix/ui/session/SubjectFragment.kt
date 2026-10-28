// AI-generated with ChatGPT Codex and Claude Code, 2026-10-28, reviewed by Joonhyung Han, Jaewan Park, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.camera.ExposureScale
import com.lastpenguin.pix.ui.camera.PinchZoomController
import com.lastpenguin.pix.ui.camera.PreviewArea
import com.lastpenguin.pix.ui.camera.REFERENCE_MARGIN_DP
import com.lastpenguin.pix.ui.camera.REFERENCE_SMALL_DP
import com.lastpenguin.pix.ui.camera.ReferenceArea
import com.lastpenguin.pix.ui.camera.ReferenceDragController
import com.lastpenguin.pix.ui.camera.TOP_BAR_OVERHANG_DP
import com.lastpenguin.pix.ui.camera.TrackedSliderController
import com.lastpenguin.pix.ui.camera.ZOOM_ROW_MARGIN_DP
import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
import com.lastpenguin.pix.ui.camera.placeReference
import com.lastpenguin.pix.ui.camera.placeZoomRow
import com.lastpenguin.pix.ui.camera.previewArea
import com.lastpenguin.pix.ui.camera.previewAspect
import com.lastpenguin.pix.ui.camera.setReferenceActions
import com.lastpenguin.pix.ui.camera.showReference
import java.text.DecimalFormat
import kotlin.math.roundToInt
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
 * brightness slider and the flash button are the photographer's controls on this phone (FR-7.3), and the guide's
 * reference photo sits in a corner as on their Camera screen (FR-3.9). *Guide* opens Add a pose guide, whose guide
 * goes to the photographer (FR-6.15, Design 2.6.5). System back asks first (R&S 6.9).
 * Owner: Real-time (#8, #10, #57), with the guide from #9 and Iter3-P9.
 */
class SubjectFragment : Fragment(R.layout.fragment_subject) {

    private val viewModel: SubjectViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val zoomFormat = DecimalFormat("0.0")
    private var pinchZoom: PinchZoomController? = null
    private var brightnessSlider: TrackedSliderController? = null
    private var renderer: SurfaceViewRenderer? = null
    private var watchdog: FrameWatchdog? = null
    private var referenceDrag: ReferenceDragController? = null

    // Where the video is, and how much of it the top bar and the controls cover (previewArea).
    private var videoArea = PreviewArea(0f, 0f, 0f)

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
        binding.videoContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.liveDot.placeIn(binding.videoContainer, viewModel.uiState.value.aspect.frameAspect)
        }
        // The video's place follows the screen's height and the bars' (R&S 6.2, 6.6), known only after layout.
        val placeVideo = View.OnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                view.post {
                    if (view.isAttachedToWindow) layoutVideo(binding, viewModel.uiState.value.aspect)
                }
            }
        }
        // The zoom row stays above the brightness slider as it opens and closes.
        binding.bottomOverlays.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeZoomRow(binding) }
        binding.root.addOnLayoutChangeListener(placeVideo)
        binding.topBar.addOnLayoutChangeListener(placeVideo)
        binding.bottomPanel.addOnLayoutChangeListener(placeVideo)
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
        binding.guideButton.setOnClickListener { findNavController().navigate(R.id.action_subject_to_addGuide) }

        setupReference(binding)

        // A session that ended while Add a pose guide was open has no transition left for this screen to see.
        (sessionViewModel.state.value as? SessionState.Ended)?.let { view.post { onTransition(it) } }

        binding.leaveButton.setOnClickListener { leaveNow() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { confirmLeave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        binding.liveDot.contentDescription = ui.photographerName
                            ?.let { getString(R.string.subject_title, it) }
                            ?: getString(R.string.subject_title_placeholder)
                        layoutVideo(binding, ui.aspect)
                        renderReference(binding, ui)
                        binding.liveDot.placeIn(binding.videoContainer, ui.aspect.frameAspect)
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
                        // Checked before enabled: MaterialButton ignores setChecked while it is disabled, so a
                        // hidden guide would leave the toggle drawn as on (found by SubjectFragmentRenderTest).
                        binding.editGuideButton.isChecked = ui.guideEditing
                        binding.editGuideButton.isEnabled = ui.connected && ui.guideState.visible
                        // Guide-colored once a guide is on the video; it then replaces the guide on both phones.
                        binding.guideButton.isActivated = ui.guide != null
                        binding.guideButton.isEnabled = ui.connected
                        binding.guideButton.contentDescription = getString(
                            if (ui.guide == null) R.string.guide_add_description else R.string.guide_change_description,
                        )
                        renderCameraControls(binding, ui)
                        val ratio = zoomFormat.format(ui.zoom)
                        binding.zoomReadout.text = getString(R.string.zoom_ratio, ratio)
                        binding.zoomReadout.contentDescription = getString(R.string.camera_zoom_description, ratio)
                        binding.zoomReadout.isEnabled = ui.canZoom
                        if (!ui.canZoom) pinchZoom?.finishInteraction()
                    }
                }
                launch {
                    // "Connecting…" until the session is up and its first frame has arrived; "camera paused" only when
                    // frames stop after that (Design 2.8). A session is Connected before WebRTC delivers the first
                    // frame, so counting the stall from the watchdog's creation showed "paused" before any video.
                    while (true) {
                        val connected = sessionViewModel.state.value is SessionState.Connected
                        val streaming = connected && frames.hasFrame()
                        val stalled = streaming && frames.nanosSinceLastFrame() > STALL_NANOS
                        binding.connectingText.isVisible = !streaming || stalled
                        binding.liveDot.isVisible = streaming && !stalled
                        binding.connectingText.setText(if (stalled) R.string.camera_paused else R.string.connecting)
                        delay(STALL_CHECK_MS)
                    }
                }
                launch {
                    sessionViewModel.transitions.collect { state -> onTransition(state) }
                }
                launch {
                    sessionViewModel.guideOfferFailed.collect {
                        Toast.makeText(requireContext(), R.string.guide_offer_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    sessionViewModel.mobileDataPending.collect { pending ->
                        if (!pending) return@collect
                        Toast.makeText(requireContext(), R.string.mobile_data_notice, Toast.LENGTH_LONG).show()
                        sessionViewModel.mobileDataNoticeShown()
                    }
                }
            }
        }
    }

    override fun onPause() {
        pinchZoom?.finishInteraction()
        brightnessSlider?.finishInteraction()
        super.onPause()
    }

    /**
     * Puts the video where previewArea says for [aspect], as on the Camera screen (R&S 6.2, 6.6), and the zoom row at
     * the bottom of its image. The caption below gets a dark gradient while it lies over the video.
     */
    private fun layoutVideo(binding: FragmentSubjectBinding, aspect: AspectRatio) {
        val root = binding.root
        if (root.width == 0 || root.height == 0) return
        val area = previewArea(
            root.width.toFloat(),
            root.height.toFloat(),
            binding.topBar.height - TOP_BAR_OVERHANG_DP * resources.displayMetrics.density,
            binding.bottomPanel.height.toFloat(),
            aspect.previewAspect,
        )
        videoArea = area
        val container = binding.videoContainer
        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        val top = area.top.roundToInt()
        val bottom = (root.height - area.bottom).roundToInt()
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            container.layoutParams = params
        }
        binding.bottomPanel.setBackgroundResource(if (area.underControls > 0f) R.drawable.bg_scrim_bottom else 0)
        placeZoomRow(binding)
    }

    /** The zoom row at the bottom of the video as it shows (1:1 inside the 4:3 area), above the slider and caption. */
    private fun placeZoomRow(binding: FragmentSubjectBinding) {
        val area = videoArea
        if (area.bottom <= area.top) return
        val frame = fitViewfinderFrame(
            binding.root.width.toFloat(),
            area.bottom - area.top,
            viewModel.uiState.value.aspect.frameAspect,
        )
        val imageBottom = minOf(area.top + frame.bottom, area.bottom - area.underControls)
        val margin = ZOOM_ROW_MARGIN_DP * resources.displayMetrics.density
        placeZoomRow(binding.zoomRow, binding.bottomOverlays, imageBottom, margin)
    }

    /**
     * The photographer's reference photo (FR-3.9), as on their Camera screen: a tap enlarges it or makes it small,
     * and a drag drops it into the nearest corner of this phone's video, which the photographer never sees.
     */
    private fun setupReference(binding: FragmentSubjectBinding) {
        val thumbnail = binding.referenceThumbnail
        referenceDrag = ReferenceDragController(
            thumbnail,
            area = { referenceArea(binding) },
            onTap = { viewModel.setReferenceExpanded(!viewModel.uiState.value.referenceExpanded) },
        ) { corner ->
            corner?.let(viewModel::setReferenceCorner)
            renderReference(binding, viewModel.uiState.value)
        }
        thumbnail.setReferenceActions(
            expanded = { viewModel.uiState.value.referenceExpanded },
            corner = { viewModel.uiState.value.referenceCorner },
            moveTo = viewModel::setReferenceCorner,
        )
        // The corners follow the video's size and the slider above the caption, known only after layout.
        val relayout = View.OnLayoutChangeListener {
                _,
                left,
                top,
                right,
                bottom,
                oldLeft,
                oldTop,
                oldRight,
                oldBottom,
            ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                thumbnail.post { if (thumbnail.isAttachedToWindow) renderReference(binding, viewModel.uiState.value) }
            }
        }
        binding.videoContainer.addOnLayoutChangeListener(relayout)
        binding.bottomOverlays.addOnLayoutChangeListener(relayout)
    }

    /** The thumbnail in its corner, small or enlarged, while the photographer's reference photo is here. */
    private fun renderReference(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val container = binding.videoContainer
        binding.referenceThumbnail.showReference(ui.reference) { photo ->
            if (referenceDrag?.isDragging == true || container.width == 0) {
                null
            } else {
                placeReference(
                    aspect = photo.width.toFloat() / photo.height,
                    expanded = ui.referenceExpanded,
                    corner = ui.referenceCorner,
                    smallLongSide = (REFERENCE_SMALL_DP * resources.displayMetrics.density).roundToInt(),
                    previewWidth = container.width,
                    area = referenceArea(binding),
                )
            }
        }
    }

    /** Inside the video's margins and above the zoom row, the slider, and the caption. */
    private fun referenceArea(binding: FragmentSubjectBinding): ReferenceArea {
        val margin = (REFERENCE_MARGIN_DP * resources.displayMetrics.density).roundToInt()
        val container = binding.videoContainer
        val zoomTop = (binding.zoomRow.top + binding.zoomRow.translationY).roundToInt()
        val bottom = minOf(zoomTop - container.top, container.height - videoArea.underControls.roundToInt()) - margin
        return ReferenceArea(margin, margin, container.width - margin, bottom)
    }

    override fun onDestroyView() {
        referenceDrag = null
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
        // A tap steps through the quick zoom steps, such as 0.6×, 1×, and 2× (FR-1.3).
        readout.setOnClickListener { viewModel.onZoomTap() }
        ViewCompat.replaceAccessibilityAction(
            readout,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.zoom_next_step),
        ) { _, _ ->
            viewModel.onZoomTap()
            true
        }
    }

    private fun onTransition(state: SessionState) {
        if (state !is SessionState.Ended) return
        when (state.reason) {
            EndReason.CONNECTION_LOST -> {
                // The action is Subject view's: close Add a pose guide first if it is open over this screen.
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.subjectFragment) nav.popBackStack(R.id.subjectFragment, false)
                nav.navigate(R.id.action_subject_to_connectionLost)
            }

            EndReason.PEER_LEFT -> {
                val name = viewModel.uiState.value.photographerName
                Toast.makeText(
                    requireContext(),
                    name?.let { getString(R.string.peer_ended_session, it) }
                        ?: getString(R.string.peer_ended_session_unnamed),
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
        @Volatile
        private var lastFrameNanos = 0L

        override fun onFrame(frame: VideoFrame) {
            lastFrameNanos = System.nanoTime()
            renderer.onFrame(frame)
        }

        /** Whether any frame has arrived since the screen opened. */
        fun hasFrame(): Boolean = lastFrameNanos != 0L

        /** Since the last frame; meaningful only once [hasFrame]. */
        fun nanosSinceLastFrame(): Long = System.nanoTime() - lastFrameNanos
    }

    private companion object {
        const val STALL_NANOS = 2_000_000_000L
        const val STALL_CHECK_MS = 500L
    }
}
