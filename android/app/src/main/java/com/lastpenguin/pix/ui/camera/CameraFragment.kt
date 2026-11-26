// AI-generated with ChatGPT Codex and Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.PhotoAccess
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.camera.manageMediaSettings
import com.lastpenguin.pix.camera.photoPermissions
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PoseStartStep
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.ShutterOutcome
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.FlowProgressView
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.NoticeAction
import com.lastpenguin.pix.ui.NoticeAnchor
import com.lastpenguin.pix.ui.NoticeLength
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.fadeTo
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.generation.SceneHandOff
import com.lastpenguin.pix.ui.generation.framingHint
import com.lastpenguin.pix.ui.reveal
import com.lastpenguin.pix.ui.session.RemoteFocus
import com.lastpenguin.pix.ui.session.SessionCaption
import com.lastpenguin.pix.ui.session.SessionNotice
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.TutorialFragment
import com.lastpenguin.pix.ui.settings.followLook
import com.lastpenguin.pix.ui.settings.renderLatency
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import com.lastpenguin.pix.ui.showNotice
import com.lastpenguin.pix.ui.showNoticeOnScreen
import java.text.DecimalFormat
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Camera screen. Guide gestures belong to #6; the scene photo for poses belongs to #7; live session UI belongs to #8. */
class CameraFragment :
    Fragment(R.layout.fragment_camera),
    NoticeAnchor {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private var binding: FragmentCameraBinding? = null
    private var sceneBack: OnBackPressedCallback? = null
    private var aspectsBack: OnBackPressedCallback? = null
    private var menuBack: OnBackPressedCallback? = null
    private var photosBack: OnBackPressedCallback? = null
    private var photoViewer: PhotoViewer? = null
    private var endSessionDialog: AlertDialog? = null

    /** Remove guide's notice, while its Undo can still put the guide back (#298). */
    private var undoNotice: Snackbar? = null
    private var permissionRequested = false
    private val zoomFormat = DecimalFormat("0.0")
    private var pinchZoom: PinchZoomController? = null
    private var referenceDrag: ReferenceDragController? = null
    private var opacitySlider: TrackedSliderController? = null

    /** The icons that turn with the phone (FR-1.15), while the view is there; a test looks inside a turn. */
    internal var turningIcons: TurningIcons? = null
        private set
    private var previewLogged = false
    private var noticeJob: Job? = null

    /** The AI notice that the subject's start of poses asked this phone's user, while it is up (#276). */
    private var askedForSubject: AlertDialog? = null

    // The session whose subject asked for it: the question goes when that session ends, or another begins.
    private var askedInSession: String? = null

    /** The notices shown without a time of their own ([holdNotice]), each until it is let go. */
    private val heldNotices = EnumMap<HeldNotice, String>(HeldNotice::class.java)

    /** The held notice that shows: the first of [HeldNotice] that is held. */
    private val heldNotice: String? get() = HeldNotice.entries.firstNotNullOfOrNull { heldNotices[it] }

    /** The notice that the subject left or dropped, while it shows; a photo not sent waits for it (#238). */
    private var peerGoneNotice: Job? = null
    private var toggleLabelJob: Job? = null
    private var pinchModeJob: Job? = null

    // The focus box and the brightness bar beside it, as Subject view has them too (FR-1.4, FR-1.11, Design 2.8).
    private var focusBrightness: FocusBrightness? = null

    // The last frame stays on screen through a ratio change or a lens switch, until the rebound preview streams
    // again (NFR-19, Design 2.6.2, 2.6.5).
    private var previewHold: PreviewHold? = null
    private var stillJob: Job? = null

    // The frame's ratio and place when the held frame was taken, while it is on screen.
    private var stillAspect: AspectRatio? = null
    private var stillPlace: FramePlace? = null

    // Whether the held frame waits dimmed and blurred (veilStill), for a new ratio or the other camera; a lens change of
    // the zoom, which goes on under the fingers, leaves it as it is.
    private var stillVeiled = false
    private var blur: ValueAnimator? = null

    // The held frame turning over for the other camera (flipStill).
    private var flip: ValueAnimator? = null

    // The held frame's blur in pixels, which a test cannot read back from the view.
    @VisibleForTesting
    internal var stillBlur = 0f
        private set

    // From a new ratio over a held frame until that frame has gone: the grid and the guide wait for the new frame, so
    // the guide is never drawn where it does not belong (FR-3.7, AD-10).
    private var reframing = false

    // Where the preview shows and for which ratio; a new ratio over a held frame moves it there (reframe).
    private var shownAspect: AspectRatio? = null
    private var shown: FramePlace? = null
    private var move: ValueAnimator? = null

    /** Whether the scene photo step showed at the last render; null before this view's first, which animates nothing. */
    private var sceneShown: Boolean? = null

    /** Whether the panel that says what to frame was to show at the last render; null before this view's first. */
    private var sceneGuideShown: Boolean? = null

    /** The panel is fading out after a touch; other renders of the step leave the fade alone. */
    private var sceneGuideFading = false

    private var shutterFade: ValueAnimator? = null

    // The shutter's ring while the timer counts down (FR-1.17), made with the first countdown of this view.
    private var countdownRing: CountdownRing? = null

    // The move under way, which a test sets to a point between its ends: the display's paused frames catch up at once.
    @VisibleForTesting
    internal val frameMove: ValueAnimator? get() = move

    @VisibleForTesting
    internal val shutterTurn: ValueAnimator? get() = shutterFade

    @VisibleForTesting
    internal val shutterRing: CountdownRing? get() = countdownRing

    @VisibleForTesting
    internal val stillFlip: ValueAnimator? get() = flip

    // The preview's last frame for PreviewHold; a test stands in for PreviewView, which has no surface on the JVM.
    @VisibleForTesting
    internal var previewFrame: () -> Bitmap? = { binding?.previewView?.bitmap }

    // The last photo's thumbnail, off the main thread; a test stands in for MediaStore, which has no photos on the JVM.
    @VisibleForTesting
    internal var thumbnailOf: (Uri) -> Bitmap? = { uri -> requireContext().photoThumbnail(uri) }

    // A session photo in the viewer, the same way.
    @VisibleForTesting
    internal var photoOf: (Uri) -> Bitmap? = { uri -> requireContext().photoToView(uri) }

    // A square of a photo in the viewer's grid, the px given on each side, the same way.
    @VisibleForTesting
    internal var cellOf: (Uri, Int) -> Bitmap? = { uri, px -> requireContext().photoCell(uri, px) }

    // Where the preview is, and how much of it the top bar and the controls cover (previewArea).
    private var previewArea = PreviewArea(0f, 0f, 0f)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (binding != null) {
            render(viewModel.uiState.value)
            if (granted) bindCamera()
        }
    }

    // The phone's photos (FR-1.6), asked from the thumbnail and the grid; whatever the answer, they are read again.
    private val photoPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.photoAccessAnswered()
    }

    // Android's own question before another app's photo is deleted (FR-1.16).
    private val deleteQuestion = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.deleteAnswered(it.resultCode == Activity.RESULT_OK)
    }

    // Android's screen that lets Pix manage the phone's media (FR-1.16): the photo asked for goes once it closes.
    private val mediaAccess = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.accessAnswered()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val controls = FragmentCameraBinding.bind(view)
        binding = controls
        permissionRequested = savedInstanceState?.getBoolean(PERMISSION_REQUESTED) ?: permissionRequested
        previewHold = PreviewHold(show = ::showStill, hide = ::hideStill)
        controls.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        // TextureView composes predictably with guide and permission/error layers above it.
        controls.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        // Far enough that the held frame turning over stays inside the preview instead of reaching out of the screen.
        controls.aspectStill.cameraDistance = FLIP_CAMERA_DISTANCE * resources.displayMetrics.density
        // PreviewView fits the viewport's crop but still draws the camera image around it: show only the frame, or,
        // for 1:1, the whole 4:3 area with the rest dimmed.
        controls.previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitToFrame(controls) }
        // The preview's place follows the screen's height and the bars' (R&S 6.2), known only after layout.
        val placePreview = View.OnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                view.post { binding?.let(::placePreview) }
            }
        }
        controls.root.addOnLayoutChangeListener(placePreview)
        controls.topBar.addOnLayoutChangeListener(placePreview)
        controls.bottomPanel.addOnLayoutChangeListener(placePreview)
        // The zoom row stays above the panels over the shutter as they open and close.
        controls.bottomOverlays.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            placeZoomRow(controls)
            // The line that says who watches fits the width once it is known.
            if (right - left != oldRight - oldLeft) renderPeerCaption(controls)
        }
        val previewStreaming = MutableStateFlow(false)
        controls.previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            Timings.mark("preview.stream", state.name)
            previewStreaming.value = state == PreviewView.StreamState.STREAMING
            if (state == PreviewView.StreamState.STREAMING && !previewLogged) {
                previewLogged = true
                Timings.mark("camera.preview")
            }
            val hold = previewHold
            if (hold != null) {
                val holding = hold.holding
                hold.onStream(state == PreviewView.StreamState.STREAMING)
                if (hold.holding != holding) render(viewModel.uiState.value)
            }
        }
        // A pinch on the preview zooms while guide-edit mode is off (AD-17); each step is applied as it comes.
        // A tap focuses there (FR-1.4), and a drag up or down sets the brightness (FR-1.11); the pinch controller owns
        // the preview's touches, so it reports both.
        pinchZoom = PinchZoomController(
            controls.previewView,
            range = ::zoomRange,
            current = { viewModel.uiState.value.zoom },
            onTap = ::onPreviewTap,
            onDrag = previewBrightnessDrag,
        ) { ratio, final -> viewModel.onZoomChanged(ratio, final) }
        opacitySlider = TrackedSliderController(
            controls.opacitySlider,
            { getString(R.string.guide_opacity_percent, (it * 100f).roundToInt()) },
            viewModel::onOpacityChange,
        )
        // Brightness beside the focus (FR-1.11): each step of a drag is applied as it comes, and the bar stays up while
        // a finger is on it.
        controls.exposureBar.describe = ::formatEv
        controls.exposureBar.onChange = viewModel::onExposureChange
        focusBrightness = FocusBrightness(controls.focusRing, controls.exposureBar, viewLifecycleOwner.lifecycleScope) {
            viewModel.setBrightnessOpen(false)
        }
        setupMenu(controls)
        // The bar's texts keep their line beside the bar's icons at every font size (#298), and the ratio's pill the
        // icons' height (#272).
        capPillText(controls.shootTogetherButton)
        capPillText(controls.endSessionButton)
        capPillText(controls.aspectButton)
        // The ratios open under the top bar (FR-1.13); a touch anywhere else closes them (setupTip), and so does Back.
        controls.aspectButton.setOnClickListener {
            viewModel.setAspectsOpen(!viewModel.uiState.value.aspectsOpen)
        }
        for ((aspect, option) in aspectOptions(controls)) {
            option.contentDescription = getString(R.string.aspect_ratio_description, aspectLabel(aspect))
            option.setOnClickListener { chooseAspect(aspect) }
        }
        aspectsBack = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            viewModel.setAspectsOpen(false)
        }
        setupZoomAccessibility(controls.zoomReadout)
        setupCompositionLevel(controls.compositionOverlay)
        viewModel.watchPhoneTurn(viewLifecycleOwner)
        // The icons turn with the phone (FR-1.15), and so do the ratio, the zoom readout, the countdown, Guide with its
        // label, and the menu's items with theirs; the other texts, the shutter, and the layout stay.
        controls.aspectButton.turnsLabel = true
        turningIcons = TurningIcons(
            controls = listOf(
                controls.sceneBackButton,
                controls.cameraMenuButton,
                controls.aspectButton,
                controls.switchCameraButton,
                controls.shootTogetherButton,
                controls.hideGuideButton,
                controls.saveGuideButton,
                controls.removeGuideButton,
                controls.tipButton,
                controls.editGuideButton,
                controls.zoomReadout,
                controls.countdownNumber,
            ),
            views = listOf(
                controls.flashIndicator,
                controls.timerIndicator,
                controls.menuFlash,
                controls.menuTimer,
                controls.menuGrid,
                controls.menuLevel,
                controls.menuSettings,
                controls.guideButton,
                controls.galleryThumbnail,
            ),
        )
        controls.cameraActionButton.setOnClickListener {
            if (hasCameraPermission()) bindCamera() else openAppSettings()
        }
        controls.requestPermissionButton.setOnClickListener {
            permissionRequested = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        controls.guideButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_addGuide)
        }
        // Who takes the photo? first, then that phone's ways to connect (FR-5.2, R&S 6.7).
        controls.shootTogetherButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_shootTogether)
        }
        controls.shutterButton.setOnClickListener {
            // While the scene photo for poses is framed, the shutter takes that photo and saves nothing.
            if (generationViewModel.uiState.value.phase == GenerationUiState.Phase.FRAMING) {
                // While the scene photo's frame is being taken, after its countdown, the shutter waits: another tap
                // would start a second countdown over the photo's check.
                if (generationViewModel.uiState.value.shooting) return@setOnClickListener
                // The ratios close, as at the camera's own shutter: a shutter that no touch came with, as with
                // TalkBack, would leave them open for Back from the review (#231). The timer counts down for the scene
                // photo too (FR-1.17), which is taken only if the scene is still framed at its end.
                viewModel.setAspectsOpen(false)
                viewModel.onSceneShutter {
                    if (generationViewModel.uiState.value.phase == GenerationUiState.Phase.FRAMING) {
                        generationViewModel.takeScene()
                    }
                }
            } else if (viewModel.onShutter()) {
                // The preview blinks as the photo is taken (FR-1.5).
                blinkPreview(controls.previewScrim)
            }
        }
        setupScenePhoto(controls)
        controls.galleryButton.setOnClickListener { onThumbnailTap() }
        photoViewer =
            PhotoViewer(
                controls.photoViewerHolder,
                viewLifecycleOwner.lifecycleScope,
                load = { photoOf(it) },
                thumbnail = { thumbnailOf(it) },
                onBack = viewModel::closePhotos,
                onDelete = viewModel::deletePhoto,
                onPick = viewModel::pickPhoto,
                asksFirst = viewModel::asksBeforeDeleting,
                // Once, at the first photo of another app's that Android would ask about (FR-1.16).
                offersAccess = { uri -> viewModel.needsAccessToDelete(uri) && !settingsViewModel.mediaAccessOffered() },
                onAccessOffered = settingsViewModel::markMediaAccessOffered,
                onAllowAndDelete = ::allowAndDelete,
                onAccess = ::changePhotoAccess,
                cell = { uri, px -> cellOf(uri, px) },
                onTab = viewModel::chooseGalleryTab,
                onOpenAlbum = viewModel::openAlbum,
                opensFrom = controls.galleryButton,
            )
        // After the other screens' Backs, so it comes first: the viewer is over all of them.
        photosBack = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            viewModel.closePhotos()
        }
        // Selfie mode (FR-1.14): the last frame stays up, blurred, and turns over while the other camera opens.
        controls.switchCameraButton.setOnClickListener {
            previewHold?.hold()
            veilStill(flip = true)
            viewModel.onSwitchCamera()
        }
        controls.guideOverlay.onGesture = viewModel::onGuideGesture
        controls.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        controls.hideGuideButton.setOnClickListener { viewModel.onHideToggle() }
        controls.saveGuideButton.setOnClickListener { viewModel.onSaveGuide() }
        setupReference(controls)
        setupTip(controls)
        controls.editGuideButton.setOnClickListener {
            viewModel.setGuideEditing(!viewModel.uiState.value.guideEditing)
            showPinchMode(controls, viewModel.uiState.value.guideEditing)
        }
        controls.endSessionButton.setOnClickListener { confirmEndSession() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // While this screen is in front it takes the photos the subject's shutter asks for (FR-7.8, #314):
                // only here is the camera bound, and the shutter the camera's own.
                launch {
                    val taker = { cancel: Boolean -> takeForSubject(controls, cancel) }
                    // And it begins the scene photo for the situation the subject picked (FR-6.15, #276).
                    val starter = { situation: Situation, answer: (PoseStartStep) -> Unit ->
                        startPosesForSubject(situation, answer)
                    }
                    sessionViewModel.setRemoteShutter(taker)
                    sessionViewModel.setRemotePoseStarter(starter)
                    try {
                        awaitCancellation()
                    } finally {
                        sessionViewModel.dropRemoteShutter(taker)
                        sessionViewModel.dropRemotePoseStarter(starter)
                    }
                }
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.phoneTurn.collect { turn ->
                        turningIcons?.turnTo(turn)
                        photoViewer?.turnTo(turn)
                    }
                }
                launch { generationViewModel.uiState.collect { render(viewModel.uiState.value) } }
                // The menu's grid and horizon bar are the settings' (FR-1.9, FR-1.10).
                launch {
                    combine(settingsViewModel.showGrid, settingsViewModel.showLevel) { _, _ -> }
                        .collect { renderMenu(controls, viewModel.uiState.value) }
                }
                // The timer's photo blinks the preview as the shutter's does (FR-1.5, FR-1.17).
                launch { viewModel.timedShots.collect { blinkPreview(controls.previewScrim) } }
                launch {
                    viewModel.menuNotices.collect { notice ->
                        showNotice(controls, menuNoticeText(notice))
                    }
                }
                launch {
                    viewModel.guideRemoved.collect {
                        undoNotice = showNoticeOnScreen(
                            getString(R.string.guide_removed_from_camera),
                            NoticeLength.UNDO,
                            NoticeAction(R.string.undo) { viewModel.onUndoRemoveGuide() },
                        )
                    }
                }
                launch {
                    viewModel.photoNotDeleted.collect {
                        showNotice(R.string.photo_not_deleted)
                    }
                }
                launch {
                    viewModel.deleteQuestions.collect { question ->
                        deleteQuestion.launch(IntentSenderRequest.Builder(question).build())
                    }
                }
                // The guide in the settings' color and thickness (FR-3.10).
                launch {
                    controls.guideOverlay.followLook(settingsViewModel.guideLook, viewModel.uiState.map { it.guide })
                }
                launch {
                    combine(
                        settingsViewModel.showLatency,
                        sessionViewModel.state,
                        sessionViewModel.latencyMs,
                    ) { _, _, _ -> }.collect { renderLatency(controls) }
                }
                // Camera's tutorial tips once the preview really shows, so nothing comes before it (FR-1.1, FR-10.4);
                // READY has no picture yet. Camera's own outside a session, and the guide's once a visible guide is on, whose ring needs it; neither
                // during the scene photo or under another screen, such as a link's, which opens over Camera at a start.
                launch {
                    val tips = combine(
                        settingsViewModel.tutorialDue(TutorialScreen.CAMERA),
                        settingsViewModel.tutorialDue(TutorialScreen.GUIDE_ON_CAMERA),
                        combine(previewStreaming, generationViewModel.uiState) { streaming, scene ->
                            streaming && !isScenePhotoActive(scene)
                        },
                        sessionViewModel.state.map { it is SessionState.Idle || it is SessionState.Ended },
                        viewModel.uiState.map { it.guide != null && it.guideState.visible },
                    ) { camera, guide, showing, alone, guided ->
                        when {
                            !showing -> null
                            camera && alone -> TutorialScreen.CAMERA
                            guide && guided -> TutorialScreen.GUIDE_ON_CAMERA
                            else -> null
                        }
                    }
                    showTutorialWhenDue(tips, TutorialFragment.AFTER_PICTURE_MS) { screen ->
                        findNavController().currentDestination?.id == R.id.cameraFragment && !isScenePhotoActive() &&
                            (
                                screen != TutorialScreen.GUIDE_ON_CAMERA ||
                                    viewModel.uiState.value.let { it.guide != null && it.guideState.visible }
                                )
                    }
                }
                // The photographer's side of a session: the live light, End session, and "left" notices (FR-6.8, FR-6.10);
                // who watches the camera is a notice too (#271).
                // While the link reconnects the light is off, and the session can still be ended (Design 2.6.7).
                launch {
                    sessionViewModel.state.collect { state ->
                        val connected = state as? SessionState.Connected
                        val live = connected != null || state is SessionState.Reconnecting
                        renderLiveDot(controls, connected)
                        renderPeerCaption(controls)
                        if (connected != null) takeDownPeerGoneNotice(controls)
                        // Nothing is left to end once the subject has gone.
                        if (!live) dismissEndSession()
                        // Nor to agree to for the subject's poses, once the session that asked is over or another's.
                        if (askedForSubject != null && sessionId(state) != askedInSession) dismissAskedForSubject()
                        // The scene photo's top bar has only Back, the light, the ratio, its title, and Use.
                        controls.endSessionButton.isVisible = live && !isScenePhotoActive()
                        controls.shootTogetherButton.isVisible = !live && !isScenePhotoActive()
                        view.keepScreenOn = live
                    }
                }
                launch {
                    sessionViewModel.notices.collect { notice ->
                        val text = when (notice) {
                            is SessionNotice.PeerGone -> getString(
                                if (notice.reason ==
                                    EndReason.CONNECTION_LOST
                                ) {
                                    R.string.peer_disconnected
                                } else {
                                    R.string.peer_left
                                },
                                notice.name,
                            )

                            is SessionNotice.RemoteZoom ->
                                getString(
                                    R.string.remote_zoom_notice,
                                    notice.name,
                                    DecimalFormat("0.#").format(notice.ratio),
                                )

                            is SessionNotice.RemoteGuideMove -> getString(
                                R.string.remote_guide_move_notice,
                                notice.name,
                            )

                            is SessionNotice.RemoteGuideVisible -> if (notice.visible) {
                                getString(R.string.remote_guide_show_notice, notice.name)
                            } else {
                                getString(R.string.remote_guide_hide_notice, notice.name)
                            }

                            is SessionNotice.RemoteGuideOpacity -> getString(
                                R.string.remote_guide_opacity_notice,
                                notice.name,
                                (notice.opacity * 100f).roundToInt(),
                            )

                            is SessionNotice.RemoteGuideRestore ->
                                getString(R.string.remote_guide_restore_notice, notice.name)

                            is SessionNotice.RemoteBrightness ->
                                getString(R.string.remote_brightness_notice, notice.name, formatEv(notice.index))

                            is SessionNotice.RemoteFlash -> getString(
                                when (notice.mode) {
                                    FlashMode.OFF -> R.string.remote_flash_off_notice
                                    FlashMode.AUTO -> R.string.remote_flash_auto_notice
                                    FlashMode.ON -> R.string.remote_flash_on_notice
                                },
                                notice.name,
                            )

                            is SessionNotice.RemoteGuide -> getString(R.string.remote_guide_notice, notice.name)

                            is SessionNotice.RemoteFocus -> getString(R.string.remote_focus_notice, notice.name)

                            is SessionNotice.RemoteTimer -> if (notice.timer == ShutterTimer.OFF) {
                                getString(R.string.remote_timer_off_notice, notice.name)
                            } else {
                                getString(R.string.remote_timer_notice, notice.name, notice.timer.seconds)
                            }

                            is SessionNotice.RemoteShutter -> getString(R.string.remote_shutter_notice, notice.name)

                            is SessionNotice.RemoteShutterPress ->
                                getString(R.string.remote_shutter_pressed_notice, notice.name)

                            is SessionNotice.RemoteTimerCancel ->
                                getString(R.string.remote_timer_cancel_notice, notice.name)

                            is SessionNotice.RemotePoseStart ->
                                getString(R.string.remote_pose_start_notice, notice.name)
                        }
                        when (notice) {
                            is SessionNotice.PeerGone ->
                                peerGoneNotice = showNotice(controls, text, CameraViewModel.PEER_GONE_NOTICE_MS)

                            // The focus box says the same, so its line is short (FR-7.7).
                            is SessionNotice.RemoteFocus -> showNotice(controls, text, SessionViewModel.FOCUS_NOTICE_MS)

                            else -> showNotice(controls, text)
                        }
                    }
                }
                launch {
                    // The subject's ✕, as this phone's own, with Undo, which puts the guide back where it was (FR-7.2);
                    // none once a guide is on the camera again, as after a screen that was stopped meanwhile.
                    sessionViewModel.guideRemovals.collect { removal ->
                        if (viewModel.uiState.value.guide != null) return@collect
                        undoNotice = showNoticeOnScreen(
                            getString(R.string.remote_guide_remove_notice, removal.name),
                            NoticeLength.UNDO,
                            NoticeAction(R.string.undo) { viewModel.onUndoRemoteRemove(removal.guide, removal.state) },
                        )
                    }
                }
                launch { sessionViewModel.remoteFocus.collect { showRemoteFocus(controls, it) } }
                launch {
                    // The subject's pinch is named from its first step to its end (#292); the ratio's notice replaces it.
                    sessionViewModel.subjectZooming.collect { name ->
                        if (name != null) {
                            holdNotice(controls, HeldNotice.ZOOMING, getString(R.string.zoom_held_label, name))
                        } else {
                            letHeldNoticeGo(controls, HeldNotice.ZOOMING)
                        }
                    }
                }
                launch {
                    // A dropped link is named for as long as the session tries to restore it (#293); "… disconnected"
                    // replaces it when the session gives up.
                    sessionViewModel.reconnectingWith.collect { name ->
                        if (name != null) {
                            holdNotice(controls, HeldNotice.RECONNECTING, getString(R.string.peer_reconnecting, name))
                        } else {
                            letHeldNoticeGo(controls, HeldNotice.RECONNECTING)
                        }
                    }
                }
                launch {
                    sessionViewModel.photosNotSent.collect { notSent ->
                        val text = if (notSent.count == 1) {
                            getString(R.string.session_photo_not_sent, notSent.name)
                        } else {
                            getString(R.string.session_photos_not_sent, notSent.count, notSent.name)
                        }
                        // The session's end comes with these, and its notice keeps its full time (#238).
                        peerGoneNotice?.join()
                        showNotice(controls, text)
                        // Each for its full time: the next would replace this one before it can be read.
                        delay(CameraViewModel.NOTICE_MS)
                    }
                }
                launch {
                    sessionViewModel.mobileDataPending.collect { pending ->
                        if (!pending) return@collect
                        showNotice(controls, getString(R.string.mobile_data_notice))
                        sessionViewModel.mobileDataNoticeShown()
                    }
                }
                launch {
                    viewModel.guideSaveNotices.collect { notice ->
                        val text = when (notice) {
                            GuideSaveNotice.SAVED -> getString(R.string.guide_saved_notice)
                            GuideSaveNotice.REMOVED -> getString(R.string.guide_removed_notice)
                            GuideSaveNotice.FAILED -> getString(R.string.guide_save_failed_notice)
                        }
                        showToggleLabel(controls, text, guideSaveIcon(notice), guideSaveTint(notice))
                    }
                }
                launch {
                    viewModel.toggleLabels.collect { label ->
                        when (label) {
                            ToggleLabel.GuideHidden -> showToggleLabel(
                                controls,
                                getString(R.string.hint_guide_hidden),
                                R.drawable.ic_visibility_off_small,
                            )

                            ToggleLabel.GuideShown -> showToggleLabel(
                                controls,
                                getString(R.string.guide_shown_label),
                                R.drawable.ic_visibility,
                                R.color.pix_guide,
                            )

                            is ToggleLabel.Zoom -> showToggleLabel(
                                controls,
                                getString(R.string.zoom_ratio, DecimalFormat("0.#").format(label.ratio)),
                                R.drawable.ic_zoom_small,
                                R.color.pix_accent,
                            )

                            // The subject's pinch holds the zoom (FR-7.6, #236).
                            ToggleLabel.ZoomHeld -> {
                                val connected = sessionViewModel.state.value as? SessionState.Connected
                                val name = connected?.peer?.displayName ?: return@collect
                                showToggleLabel(
                                    controls,
                                    getString(R.string.zoom_held_label, name),
                                    R.drawable.ic_zoom_small,
                                )
                            }
                        }
                    }
                }
                launch {
                    // A new photo, taken here or received, grows into the thumbnail (FR-1.6); one edited in place is read
                    // again.
                    showPhotoThumbnails(
                        viewModel.uiState.map { it.lastPhoto },
                        controls.galleryThumbnail,
                        viewModel.uiState.map { it.photoEdits },
                    ) { thumbnailOf(it) }
                }
            }
        }
        render(viewModel.uiState.value)
        // A bar left up when the screen was made again still goes after a few seconds.
        if (viewModel.uiState.value.brightnessOpen) focusBrightness?.hold()
    }

    override fun onStart() {
        super.onStart()
        // The photo permission may have changed in the phone's settings while Pix was away (FR-1.6).
        viewModel.readPhotosAgain()
    }

    override fun onResume() {
        super.onResume()
        render(viewModel.uiState.value)
        if (hasCameraPermission()) {
            bindCamera()
        } else if (!permissionRequested) {
            permissionRequested = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PERMISSION_REQUESTED, permissionRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        pinchZoom?.finishInteraction()
        opacitySlider?.finishInteraction()
        binding?.exposureBar?.finishDrag()
        super.onPause()
    }

    override fun onStop() {
        // Leaving the screen, for another or for the background, cancels the timer's countdown (FR-1.17): the photo
        // would be taken with nobody looking, or not at all once the camera stops.
        viewModel.cancelCountdown()
        super.onStop()
    }

    override fun onDestroyView() {
        dismissEndSession()
        dismissUndo()
        // The question for the subject's poses goes with the screen, which answers it as "Not now".
        askedForSubject?.dismiss()
        photoViewer?.stop()
        photoViewer = null
        photoEditsShown = null
        photosBack = null
        pinchZoom?.finishInteraction()
        pinchZoom = null
        referenceDrag = null
        opacitySlider?.finishInteraction()
        opacitySlider = null
        turningIcons?.stop()
        turningIcons = null
        focusBrightness?.clear()
        focusBrightness = null
        move?.let {
            move = null
            it.cancel()
        }
        // Without its hold, the screen takes the held frame down at once, also one a cancelled move left up.
        val hold = previewHold
        previewHold = null
        hold?.release()
        binding?.let(::clearStill)
        reframing = false
        shown = null
        shownAspect = null
        sceneShown = null
        sceneGuideShown = null
        sceneGuideFading = false
        shutterFade?.cancel()
        shutterFade = null
        countdownRing?.stop()
        countdownRing = null
        binding = null
        sceneBack = null
        aspectsBack = null
        menuBack = null
        previewLogged = false
        super.onDestroyView()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val controls = binding ?: return
        viewModel.bindCamera(viewLifecycleOwner, controls.previewView.surfaceProvider)
    }

    /** A pinch starts while the camera is ready and continues through a lens switch's STARTING, as the slider did. */
    private fun zoomRange(): ClosedFloatingPointRange<Float>? {
        val state = viewModel.uiState.value
        if (!hasCameraPermission() || state.maxZoom <= state.minZoom) return null
        val continuing = state.cameraStatus == CameraStatus.STARTING && pinchZoom?.isTracking == true
        return if (state.cameraStatus == CameraStatus.READY || continuing) state.minZoom..state.maxZoom else null
    }

    private fun setupZoomAccessibility(readout: View) {
        fun adjust(factor: Float): Boolean {
            val state = viewModel.uiState.value
            if (state.cameraStatus != CameraStatus.READY) return false
            val ratio = (state.zoom * factor).coerceIn(state.minZoom, state.maxZoom)
            if (ratio == state.zoom) return false
            viewModel.onZoomChanged(ratio)
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

    /**
     * The reference thumbnail (FR-3.9): a tap enlarges it or makes it small again, and a drag drops it into the
     * nearest corner, or hides it at that corner's side when it is let go mostly out of the preview or flung there
     * (#237). A hidden one leaves a strip, which a tap or a drag brings back. TalkBack gets the same tap, an action
     * for each other corner, and one to hide it.
     */
    private fun setupReference(controls: FragmentCameraBinding) {
        val thumbnail = controls.referenceThumbnail
        val density = resources.displayMetrics.density
        referenceDrag = ReferenceDragController(
            thumbnail,
            area = { referenceArea(controls) },
            previewWidth = { controls.previewContainer.width },
            peek = (REFERENCE_PEEK_DP * density).roundToInt(),
            flingSpeed = REFERENCE_FLING_DP * density,
            onTap = viewModel::onReferenceTap,
        ) { drop ->
            drop?.let(viewModel::onReferenceDropped)
            renderReference(controls, viewModel.uiState.value)
        }.also { it.listenOn(controls.referenceHandle) }
        thumbnail.setReferenceActions(
            expanded = { viewModel.uiState.value.referenceExpanded },
            corner = { viewModel.uiState.value.referenceCorner },
            hidden = { viewModel.uiState.value.referenceHidden },
            moveTo = viewModel::setReferenceCorner,
            setHidden = viewModel::setReferenceHidden,
        )
        // The corners follow the preview's size and the panels at its bottom, which grow when brightness or a notice
        // opens; both are known only after layout.
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
                thumbnail.post { binding?.let { renderReference(it, viewModel.uiState.value) } }
            }
        }
        controls.previewContainer.addOnLayoutChangeListener(relayout)
        controls.bottomOverlays.addOnLayoutChangeListener(relayout)
    }

    /** Inside the preview's margins and above the zoom row, the panels over the shutter, and the controls. */
    private fun referenceArea(controls: FragmentCameraBinding): ReferenceArea {
        val margin = (REFERENCE_MARGIN_DP * resources.displayMetrics.density).roundToInt()
        val container = controls.previewContainer
        val zoomTop = (controls.zoomRow.top + controls.zoomRow.translationY).roundToInt()
        val bottom = minOf(zoomTop - container.top, container.height - previewArea.underControls.roundToInt()) - margin
        return ReferenceArea(margin, margin, container.width - margin, bottom)
    }

    /** Where previewArea puts the image of [aspect] on this screen (R&S 6.2); null before layout. */
    private fun placeFor(controls: FragmentCameraBinding, aspect: AspectRatio): FramePlace? {
        val root = controls.root
        if (root.width == 0 || root.height == 0) return null
        return framePlace(
            root.width.toFloat(),
            root.height.toFloat(),
            controls.topBar.height - TOP_BAR_OVERHANG_DP * resources.displayMetrics.density,
            controls.bottomPanel.height.toFloat(),
            aspect,
        )
    }

    /** After the screen or a bar changed size: the preview in its ratio's place at once, a move ended. */
    private fun placePreview(controls: FragmentCameraBinding) {
        endMove()
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        val place = placeFor(controls, aspect) ?: return
        shownAspect = aspect
        showPlace(controls, place)
    }

    /**
     * Shows the preview in [aspect]'s place. A new ratio over a held frame moves there in [Motion.MOVE_MS] while the
     * camera rebinds (Design 2.6.2): the frame's edges, and the held frame with the scene, to where the new ratio's
     * preview will show it. Otherwise, and while animations are off, the preview is there at once.
     */
    private fun reframe(controls: FragmentCameraBinding, aspect: AspectRatio) {
        if (aspect == shownAspect) return
        val to = placeFor(controls, aspect) ?: return
        endMove()
        val from = shown
        shownAspect = aspect
        if (from == null || previewHold?.holding != true || !controls.aspectStill.isVisible) {
            showPlace(controls, to)
            return
        }
        reframing = true
        if (!Motion.on()) {
            moveStep(controls, from, to, 1f)
            return
        }
        move = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.MOVE_MS
            interpolator = Motion.STANDARD
            addUpdateListener { step -> binding?.let { moveStep(it, from, to, step.animatedValue as Float) } }
            doOnEnd { ended ->
                if (move !== ended) return@doOnEnd
                move = null
                // The camera may stream again already: the held frame goes now that the frame is in its place.
                stillSettled()
            }
            start()
        }
    }

    /** Ends a move at its place, as when it is replaced or the screen changes size. */
    private fun endMove() {
        move?.end()
    }

    /** [fraction] of a ratio change's move: the frame's edges, and the held frame with the scene (sceneMove). */
    private fun moveStep(controls: FragmentCameraBinding, from: FramePlace, to: FramePlace, fraction: Float) {
        val place = lerp(from, to, fraction)
        showPlace(controls, place)
        val still = controls.aspectStill
        val taken = stillPlace
        if (!still.isVisible || taken == null) return
        val scene = sceneMove(taken, place)
        // The still is in the area's coordinates, as the preview was where it was taken.
        still.imageMatrix = Matrix().apply {
            setTranslate(-scene.fromX, -(scene.fromY - taken.area.top))
            postScale(scene.scale, scene.scale)
            postTranslate(scene.x, scene.y - place.area.top)
        }
        still.clipBounds = place.clip()
    }

    /**
     * Puts the preview at [place] (R&S 6.2): the area's margins, the clip, the live light, and the zoom row at the
     * bottom of its image. The controls get a dark gradient while they lie over it. Margins change only when the
     * place does, as the screen renders on every zoom step of a pinch.
     */
    private fun showPlace(controls: FragmentCameraBinding, place: FramePlace) {
        shown = place
        previewArea = place.area
        val container = controls.previewContainer
        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        val top = place.area.top.roundToInt()
        val bottom = (controls.root.height - place.area.bottom).roundToInt()
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            container.layoutParams = params
        }
        controls.bottomPanel.setBackgroundResource(if (place.area.underControls > 0f) R.drawable.bg_scrim_bottom else 0)
        fitToFrame(controls)
        placeZoomRow(controls)
    }

    /** The zoom row at the bottom of the image as it shows (1:1 inside the 4:3 area), above the panels and controls. */
    private fun placeZoomRow(controls: FragmentCameraBinding) {
        val place = shown ?: return
        val area = place.area
        if (area.bottom <= area.top) return
        val imageBottom = minOf(place.frame.bottom, area.bottom - area.underControls)
        val margin = ZOOM_ROW_MARGIN_DP * resources.displayMetrics.density
        placeZoomRow(controls.zoomRow, controls.bottomOverlays, imageBottom, margin, controls.noticeLine)
        controls.pinchModeLabel.translationY = controls.zoomRow.translationY
    }

    /**
     * The grid and the horizon bar, each as Settings says (FR-1.9, FR-1.10). The bar's sensor runs only while the
     * screen is resumed and the bar is on.
     */
    private fun setupCompositionLevel(overlay: CameraCompositionView) {
        // This is a view-scoped sensor adapter, like ScaleGestureDetector, not shared camera state.
        val monitor = CameraLevelMonitor(requireContext())
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.showGrid.collect { overlay.showGrid = it }
            }
        }
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    settingsViewModel.showLevel.collect { show ->
                        overlay.showLevel = show
                        if (show) monitor.start(overlay::setLevel) else stopLevel(monitor, overlay)
                    }
                } finally {
                    stopLevel(monitor, overlay)
                }
            }
        }
    }

    private fun stopLevel(monitor: CameraLevelMonitor, overlay: CameraCompositionView) {
        monitor.stop()
        overlay.setLevel(null)
    }

    /**
     * The bar during the scene photo (#210): the camera's own, its height and its sizes, so the image stays where it is,
     * with Back and the title as far from the edge as the steps after it have them, and the menu and the ratio at its
     * end, where their action is. Otherwise the camera's own bar, the menu at its start.
     */
    private fun placeSceneBar(controls: FragmentCameraBinding, active: Boolean) {
        val bar = controls.topBar
        val plain = plainBar ?: intArrayOf(bar.paddingStart, bar.paddingEnd).also { plainBar = it }
        val edges = if (active) {
            intArrayOf(
                resources.getDimensionPixelSize(R.dimen.scene_bar_start),
                resources.getDimensionPixelSize(R.dimen.scene_bar_end),
            )
        } else {
            plain
        }
        if (bar.paddingStart != edges[0] || bar.paddingEnd != edges[1]) {
            bar.setPaddingRelative(edges[0], bar.paddingTop, edges[1], bar.paddingBottom)
        }
        val params = controls.cameraSettings.layoutParams as ConstraintLayout.LayoutParams
        val start = if (active) ConstraintLayout.LayoutParams.UNSET else R.id.sceneBackButton
        val end = if (active) R.id.topBarActions else ConstraintLayout.LayoutParams.UNSET
        if (params.startToEnd == start && params.endToStart == end) return
        params.startToEnd = start
        params.endToStart = end
        controls.cameraSettings.layoutParams = params
    }

    /** The photos' edits (`CameraUiState.photoEdits`) at the last render; null before the first. */
    private var photoEditsShown: Int? = null

    /** The top bar's own start and end padding, from its layout, before the scene photo's. */
    private var plainBar: IntArray? = null

    /** Back of the scene photo step (R&S 6.3, FR-4.1, #199); the step has no other button since #296. */
    private fun setupScenePhoto(controls: FragmentCameraBinding) {
        controls.sceneBackButton.setOnClickListener { sceneStepBack() }
        // System back does what Back does (R&S 6.9). It is enabled only during this step, so back leaves the app from
        // the plain camera as before.
        sceneBack = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            sceneStepBack()
        }
    }

    /**
     * Back during the scene photo: while the photo is checked, the live camera again; from the live camera, the camera.
     * It waits while the timer counts down, as every way off the camera does (FR-1.17).
     */
    private fun sceneStepBack() {
        if (viewModel.uiState.value.countdown != null) return
        if (generationViewModel.uiState.value.phase == GenerationUiState.Phase.CHECKING) {
            generationViewModel.retakeScene()
        } else {
            generationViewModel.cancel()
        }
    }

    /**
     * The scene photo has a person in it: Generate poses asks what to pose, with no step between (#296, FR-4.8). Once,
     * from this screen; the photo stays held here while that screen comes in and shows it shrinking into its place.
     */
    private fun openGeneratePoses(scene: GenerationUiState) {
        if (scene.phase != GenerationUiState.Phase.DESCRIBING || !scene.sceneArriving) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.cameraFragment) return
        val controls = binding ?: return
        // After the layout this state asks for, in the same frame, so what is handed over is what the screen shows.
        controls.root.doOnLayout {
            if (binding !== controls || navController.currentDestination?.id != R.id.cameraFragment) return@doOnLayout
            SceneHandOff.pending = handOffOf(controls)
            navController.navigate(R.id.action_camera_to_describePose)
        }
    }

    /**
     * How the step looks now, for Generate poses to start as this and unfold from it (#210): Back's arrow, the title,
     * the flow's bar, and the held photo's frame, in the window's pixels. Null while any of them is not shown.
     */
    private fun handOffOf(controls: FragmentCameraBinding): SceneHandOff? {
        val back = controls.sceneBackButton as MaterialButton
        val title = controls.sceneTitle
        val still = controls.sceneStill
        val image = still.drawable
        if (!back.isShown || !title.isShown || !still.isShown || image == null || title.width == 0) return null
        fun View.window() = IntArray(2).also(::getLocationInWindow)
        val backAt = back.window()
        val titleAt = title.window()
        val stillAt = still.window()
        // The photo's frame: the image as the view fits it, where the view is.
        val frame = RectF(0f, 0f, image.intrinsicWidth.toFloat(), image.intrinsicHeight.toFloat())
        still.imageMatrix.mapRect(frame)
        frame.offset((stillAt[0] + still.paddingLeft).toFloat(), (stillAt[1] + still.paddingTop).toFloat())
        return SceneHandOff(
            // The arrow is in the middle of the pill, which is drawn between the button's insets.
            back = PointF(
                backAt[0] + back.width / 2f,
                backAt[1] + (back.insetTop + back.height - back.insetBottom) / 2f,
            ),
            backIcon = back.iconSize.toFloat(),
            title = title.drawToBitmap(),
            titleBox = RectF(
                titleAt[0].toFloat(),
                titleAt[1].toFloat(),
                (titleAt[0] + title.width).toFloat(),
                (titleAt[1] + title.height).toFloat(),
            ),
            titleSize = title.textSize,
            progressTop = controls.sceneProgress.window()[1].toFloat(),
            photo = Rect(
                frame.left.roundToInt(),
                frame.top.roundToInt(),
                frame.right.roundToInt(),
                frame.bottom.roundToInt(),
            ),
        )
    }

    private fun render(state: CameraUiState) {
        val controls = binding ?: return
        // Undo has nothing to put back once a guide is on the camera, the removed one or another (#298).
        if (state.guide != null) dismissUndo()
        val granted = hasCameraPermission()
        val ready = granted && state.cameraStatus == CameraStatus.READY
        val unavailable = state.cameraStatus == CameraStatus.UNAVAILABLE
        // First, so a frame held from here on also hides the spinner below.
        previewHold?.onStatus(state.cameraStatus)
        // A new ratio over a held frame moves there while the camera rebinds; otherwise the preview is there at once.
        reframe(controls, state.aspect)
        // Over a held frame of the same ratio (a lens switch) the grid and the guide stay; through a ratio change they
        // wait for the new frame.
        val overlays = !reframing && (ready || previewHold?.holding == true)
        controls.compositionOverlay.isVisible = overlays
        // The grid and the guide follow the chosen frame (AD-10).
        controls.compositionOverlay.frameAspect = state.aspect.frameAspect
        controls.guideOverlay.frameAspect = state.aspect.frameAspect
        fitToFrame(controls)
        controls.guideOverlay.render(state.guide, state.guideState)
        controls.guideOverlay.isVisible = overlays
        // Camera + guide (R&S 6.2). Visibility follows the guide alone: a lens switch briefly leaves READY,
        // and hiding the bar then would move the controls under an active touch.
        val guideEditable = granted && !unavailable
        controls.guideControls.isVisible = state.guide != null
        val opacityEnabled = guideEditable && state.guide != null && state.guideState.visible
        opacitySlider?.render(GuideState.MIN_OPACITY, GuideState.MAX_OPACITY, state.guideState.opacity, opacityEnabled)
        controls.opacityValue.isEnabled = opacityEnabled
        controls.opacityValue.text =
            getString(R.string.guide_opacity_percent, (state.guideState.opacity * 100f).roundToInt())
        controls.removeGuideButton.isEnabled = guideEditable
        val hidden = !state.guideState.visible
        setIconOnce(controls.hideGuideButton, if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
        controls.hideGuideButton.contentDescription =
            getString(if (hidden) R.string.show_guide else R.string.hide_guide)
        controls.hideGuideButton.isEnabled = guideEditable
        // Save guide (FR-3.8): a ♥, filled red once a favorite (#297); another tap takes it out of Favorites again.
        setIconOnce(
            controls.saveGuideButton,
            if (state.guideSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart,
        )
        controls.saveGuideButton.iconTint = requireContext().getColorStateList(saveGuideTint(state.guideSaved))
        controls.saveGuideButton.contentDescription =
            getString(if (state.guideSaved) R.string.guide_saved else R.string.save_guide)
        controls.saveGuideButton.isEnabled = guideEditable && !state.savingGuide
        // Guide-edit mode (AD-17): the overlay owns the preview's touches while on; the pinch zooms while off.
        // Its toggle sits beside the zoom readout, so it stays when the guide's panel changes (Iter2-P11).
        controls.editGuideButton.isVisible = state.guide != null
        // Checkable, so TalkBack reads the mode as on or off; the ViewModel decides the state after a tap.
        controls.editGuideButton.isChecked = state.guideEditing
        controls.editGuideButton.isEnabled = guideEditable && state.guideState.visible
        controls.guideOverlay.editable = state.guideEditing
        controls.guideOverlay.filled = state.guideEditing
        // Computed once per render: while the scene photo is framed the guide is hidden, so the pinch zooms whatever
        // the mode says, and while the photo taken is held there is no zoom. Flipping it off and on again in one render
        // would end a pinch in progress, which the detector cannot restart in the same touch stream.
        val scene = generationViewModel.uiState.value
        pinchZoom?.enabled = if (isScenePhotoActive(scene)) {
            scene.phase == GenerationUiState.Phase.FRAMING
        } else {
            !state.guideEditing
        }
        // Guide-colored once a guide is on the camera; it then changes the guide instead of adding one.
        controls.guideButton.isActivated = state.guide != null
        controls.guideButton.contentDescription =
            getString(if (state.guide == null) R.string.guide_add_description else R.string.guide_change_description)
        // The tutorial shows how to add a guide and move it (FR-10.4), so the chip says only what the screen does not:
        // that the guide is hidden. It says nothing about the pinch, which zooms as on any camera (#306).
        if (state.guide != null && !state.guideEditing && !state.guideState.visible) {
            showHint(controls.hintText, R.string.hint_guide_hidden, R.drawable.ic_visibility_off_small)
        } else {
            showHint(controls.hintText, null)
        }
        controls.cameraMessagePanel.isVisible = !granted || unavailable
        // The permission and camera messages fill the preview; nothing over it applies then.
        controls.zoomRow.isInvisible = controls.cameraMessagePanel.isVisible
        renderLiveDot(controls, sessionViewModel.state.value as? SessionState.Connected)
        controls.cameraMessage.setText(if (granted) R.string.camera_unavailable else R.string.camera_permission_message)
        controls.cameraActionButton.setText(if (granted) R.string.try_again else R.string.open_settings)
        controls.requestPermissionButton.isVisible =
            !granted && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        // The held last frame stands for a camera that is about to stream again, so it shows no spinner.
        controls.cameraProgress.isVisible = granted && !ready && !unavailable && previewHold?.holding != true
        // Free again once the camera took the photo, while it is saved (NFR-19); the ways off Camera and the camera's
        // changes wait for the save, since leaving or rebinding would lose the photo.
        // While the timer counts down, the shutter cancels it, and the other ways off the camera and its changes wait,
        // dimmed (FR-1.17); zoom and focus still work.
        val counting = state.countdown != null
        controls.shutterButton.isEnabled = (ready && !state.shutterBusy) || counting
        controls.shutterButton.contentDescription = when {
            counting -> getString(R.string.shutter_cancel_timer)
            state.timer != ShutterTimer.OFF -> getString(R.string.shutter_timer, state.timer.seconds)
            else -> getString(R.string.shutter)
        }
        controls.guideButton.isEnabled = !state.isSaving && !counting
        controls.shootTogetherButton.isEnabled = !state.isSaving && !counting
        // In a session End session takes Shoot together's place, and waits as well: ended during the count, the session
        // would be gone before the photo it was counting for.
        controls.endSessionButton.isEnabled = !counting
        controls.galleryButton.isEnabled = state.opensPhotos && !counting
        val dim = if (counting) COUNTDOWN_DIM else 1f
        for (control in listOf(
            controls.cameraSettings,
            controls.switchCameraButton,
            controls.shootTogetherButton,
            controls.endSessionButton,
            controls.guideButton,
            controls.galleryButton,
        )) {
            control.alpha = dim
        }
        // A photo changed in place since the last render: the viewer's pictures kept by URI are stale.
        if (photoEditsShown != null && photoEditsShown != state.photoEdits) photoViewer?.forgetPictures()
        photoEditsShown = state.photoEdits
        photoViewer?.render(
            state.viewerPhotos,
            state.photosOpen,
            grid = state.photosGrid,
            at = state.pickedPhoto,
            access = state.photoAccess,
            gallery = Gallery(state.galleryTab, state.photoAlbums, state.album).takeIf { state.sessionPhotos == null },
        )
        photosBack?.isEnabled = state.photosOpen
        // On any phone with a front camera, in a session too; not while the scene photo for poses is framed.
        controls.switchCameraButton.isVisible = state.canSwitchCamera
        controls.switchCameraButton.isEnabled = granted && !state.isSaving && !counting
        controls.switchCameraButton.contentDescription = getString(
            if (state.facing == CameraFacing.BACK) R.string.switch_to_front_camera else R.string.switch_to_rear_camera,
        )
        controls.countdownPanel.isVisible = counting
        if (counting) controls.countdownNumber.text = state.countdown.toString()

        renderCameraSettings(controls, state, ready)

        val ratio = zoomFormat.format(state.zoom)
        controls.zoomReadout.text = getString(R.string.zoom_ratio, ratio)
        controls.zoomReadout.contentDescription = getString(R.string.camera_zoom_description, ratio)
        controls.zoomReadout.isEnabled = ready && state.maxZoom > state.minZoom
        // Saving shows nothing, as on the phone's own camera: the thumbnail is the result (FR-1.6). A failure says why.
        when (val notice = state.captureNotice) {
            null -> controls.captureStatus.isVisible = false

            else -> showFailure(
                controls.captureStatus,
                when (notice) {
                    CaptureNotice.STORAGE_FAILED -> R.string.photo_storage_failed
                    CaptureNotice.CAMERA_UNAVAILABLE -> R.string.photo_camera_closed
                    CaptureNotice.PERMISSION_REQUIRED -> R.string.camera_permission_message
                    CaptureNotice.SAVE_FAILED -> R.string.photo_save_failed
                },
            )
        }
        renderReference(controls, state)
        renderTip(controls, state)
        renderPeerCaption(controls)
        renderScenePhoto(controls, generationViewModel.uiState.value, state, ready)
        renderCountdownRing(state)
        renderLatency(controls)
        // The mode's label goes with its toggle.
        if (!controls.editGuideButton.isVisible) controls.pinchModeLabel.isVisible = false
    }

    /**
     * What a touch anywhere closes, and the pose tip (FR-4.10), which only its button opens and closes: a touch anywhere
     * else, on the banner too, leaves it as it is.
     */
    private fun setupTip(controls: FragmentCameraBinding) {
        (controls.root as TouchWatchingLayout).onTouchEnd = { event ->
            // The ratios close at a touch anywhere but on them or their button, which opens and closes them itself.
            val onRatios = controls.aspectButton.isUnder(event, controls.root) ||
                controls.aspectPanel.isUnder(event, controls.root)
            if (!onRatios) viewModel.setAspectsOpen(false)
            // So does the camera menu (FR-1.17), whose button and the icons beside it open and close it.
            if (!controls.cameraSettings.isUnder(event, controls.root) &&
                !controls.cameraMenu.isUnder(event, controls.root)
            ) {
                viewModel.setMenuOpen(false)
            }
            // The scene photo's panel goes at a touch anywhere, which still does what it does there (#231).
            generationViewModel.dismissSceneHint()
        }
        // Clickable as well, so TalkBack can take it away.
        controls.sceneGuide.setOnClickListener { generationViewModel.dismissSceneHint() }
        controls.tipButton.setOnClickListener { viewModel.onTipButton() }
        // A tap on the open tip neither closes it nor focuses the image under it.
        controls.tipBanner.keepTouches()
    }

    /**
     * The pose tip of the guide on the camera (FR-4.10): hidden with the guide, and the button only while a tip is
     * coming or there, lit while the tip is open and named for what its tap does. The scene photo step hides both
     * (renderScenePhoto).
     */
    private fun renderTip(controls: FragmentCameraBinding, state: CameraUiState) {
        val tip = state.tip?.takeIf { state.guide != null && state.guideState.visible }
        val available = tip != null && tip.state != TipState.Failed
        val open = tip?.open == true
        controls.tipButtonFrame.isVisible = available
        controls.tipDot.isVisible = available && tip.unseen
        controls.tipButton.isActivated = open
        controls.tipButton.contentDescription = getString(if (open) R.string.close_tip else R.string.show_tip)
        controls.tipBanner.isVisible = open
        controls.tipText.text = when (val shown = tip?.state) {
            TipState.Preparing -> getString(R.string.tip_preparing)
            is TipState.Ready -> shown.tip.text
            TipState.Failed -> getString(R.string.tip_failed)
            null -> null
        }
    }

    /**
     * The guide's reference in its corner while there is one (FR-3.9): small, or half the preview's width when
     * enlarged. It glides to a new place, and stays while the guide is hidden; the scene photo step hides it and
     * shows its own hint. Layout changes call this alone, so it applies every rule of its own, the scene photo's too.
     */
    private fun renderReference(controls: FragmentCameraBinding, state: CameraUiState) {
        // The scene photo frames a new scene, which an earlier guide's reference is not part of.
        val reference = state.guide?.reference?.takeUnless { isScenePhotoActive() }
        // The enlarged photo reaches under the chips: the hint steps aside, and a capture status stays on top. A
        // toggle's label takes the hint's place while it shows.
        controls.hintText.isInvisible = controls.hintText.text.isNullOrEmpty() ||
            (reference != null && state.referenceExpanded) || controls.toggleLabel.isVisible
        val dragging = referenceDrag?.isDragging == true
        var placed: ReferencePlacement? = null
        controls.referenceThumbnail.showReference(reference) { photo ->
            if (dragging || controls.previewContainer.width == 0) {
                null
            } else {
                val density = resources.displayMetrics.density
                placeReference(
                    aspect = photo.width.toFloat() / photo.height,
                    expanded = state.referenceExpanded,
                    corner = state.referenceCorner,
                    smallLongSide = (REFERENCE_SMALL_DP * density).roundToInt(),
                    previewWidth = controls.previewContainer.width,
                    area = referenceArea(controls),
                    hidden = state.referenceHidden,
                    peek = (REFERENCE_PEEK_DP * density).roundToInt(),
                ).also { placed = it }
            }
        }
        // The handle stays as it is through a drag, which may have begun on it.
        if (!dragging) {
            controls.referenceHandle.showReferenceHandle(
                placement = placed.takeIf { reference != null && state.referenceHidden },
                corner = state.referenceCorner,
                previewWidth = controls.previewContainer.width,
                area = referenceArea(controls),
            )
        }
    }

    /** The latency indicator (FR-10.3) in the top bar, except while the scene photo's title takes its place. */
    private fun renderLatency(controls: FragmentCameraBinding) {
        controls.latencyChip.renderLatency(
            settingsViewModel.showLatency.value && !isScenePhotoActive(),
            sessionViewModel.state.value,
            sessionViewModel.latencyMs.value,
        )
    }

    /**
     * Whether the scene photo for poses is being framed, or was just taken and is held; the top bar is its title
     * then. Held too while Generate poses comes in, so the camera's own controls do not show under that change.
     */
    private fun isScenePhotoActive(scene: GenerationUiState = generationViewModel.uiState.value): Boolean =
        scene.phase == GenerationUiState.Phase.FRAMING || isScenePhotoHeld(scene)

    /**
     * Whether the scene photo just taken is held over the preview (#296): while the phone looks for a person in it
     * (FR-4.13), and, with one, until Generate poses has taken it.
     */
    private fun isScenePhotoHeld(scene: GenerationUiState): Boolean =
        scene.phase == GenerationUiState.Phase.CHECKING ||
            (scene.phase == GenerationUiState.Phase.DESCRIBING && scene.sceneArriving)

    /**
     * The scene photo for poses is taken on this screen (R&S 6.3, FR-4.1): the live camera with the shutter, then the
     * photo held where the preview showed it for the moment the phone looks for a person in it. With one, Generate
     * poses opens by itself; without one, the camera is live again and the panel above the shutter says so (#296;
     * until Iteration 5 the photo waited here for *Use*). Applied last, over the camera's own controls, so that the
     * step shows only what it needs.
     */
    private fun renderScenePhoto(
        controls: FragmentCameraBinding,
        scene: GenerationUiState,
        camera: CameraUiState,
        ready: Boolean,
    ) {
        val held = isScenePhotoHeld(scene)
        val active = isScenePhotoActive(scene)
        sceneBack?.isEnabled = active
        controls.sceneTitle.isVisible = active
        controls.sceneBackButton.isVisible = active
        val session = sessionViewModel.state.value
        val live = session is SessionState.Connected || session is SessionState.Reconnecting
        controls.shootTogetherButton.isVisible = !active && !live
        // End session too leaves the scene photo's bar to its title, and comes back after the step.
        controls.endSessionButton.isVisible = !active && live
        // Guide and the thumbnail keep their place, so the shutter stays where it is. The camera is the situation's (FR-4.8):
        // the front one for a selfie, the rear one otherwise, so the switch waits too. The camera menu gives way to the
        // light (renderMenu).
        controls.guideButton.isInvisible = active
        controls.galleryButton.isInvisible = active
        if (active) controls.switchCameraButton.isVisible = false
        controls.sceneStill.isVisible = held
        // As the preview showed it and where it showed it, so the image seems to stop, also for a photo taken sideways.
        val photo = scene.framed.takeIf { held }
        if (controls.sceneStill.tag !== photo) {
            controls.sceneStill.setImageBitmap(photo)
            controls.sceneStill.tag = photo
        }
        // The photo is taken: no zoom until it goes on or is shot again (the pinch's enabled state is set in render).
        controls.zoomReadout.isInvisible = held
        // The scene photo is a frame of the live preview: a photo's flash and brightness do not apply to it. While the
        // scene is framed, the flash button keeps the phone's light on for it instead (FR-4.1). It says on only once
        // the camera has the light on, so it never claims a light that is not. The ratio is the frame's, so it is
        // chosen here as on Camera (#231), until the photo is taken.
        placeSceneBar(controls, active)
        if (active) {
            controls.cameraSettings.isVisible = !held
            if (held) {
                controls.aspectPanel.reveal(false)
                controls.aspectButton.isActivated = false
                aspectsBack?.isEnabled = false
            }
            controls.sceneBackButton.isEnabled = camera.countdown == null
            controls.sceneBackButton.alpha = if (camera.countdown == null) 1f else COUNTDOWN_DIM
            focusBrightness?.show(false)
        }
        if (active) {
            controls.editGuideButton.isVisible = false
            controls.tipButtonFrame.isVisible = false
        }
        // Kept in place while the photo is held, so the bottom panel, which places the preview, keeps its height.
        controls.captureControls.isInvisible = held
        // Where the step is in the pose flow, under the camera's own bar, so the image stays where it is (#231). While
        // framing, what to frame and what for, on the panel above the shutter until a touch takes it away, and the
        // shutter in the accent. Starting or ending the step plays as a change (Motion): the title fades in, the
        // flow's bar fills to its step, the panel rises in, and the shutter turns.
        val before = sceneShown
        val changed = before != null && before != active
        sceneShown = active
        controls.sceneProgress.isVisible = active
        when {
            // The screen made anew, as Back from the next step brings it: the bar empties from where that step had it.
            before == null -> if (active) {
                controls.sceneProgress.arrive(SCENE_STEP)
            } else {
                controls.sceneProgress.step =
                    SCENE_STEP
            }

            changed && active -> {
                FlowProgressView.shownStep = 0
                controls.sceneProgress.arrive(SCENE_STEP)
            }

            else -> controls.sceneProgress.moveTo(SCENE_STEP)
        }
        if (changed && active) {
            controls.sceneTitle.alpha = 0f
            controls.sceneTitle.fadeTo(1f, Motion.FADE_MS)
        }
        // The panel above the shutter while framing: what to frame (#210), or that the photo just taken had no one in
        // it (FR-4.13, #296).
        val framing = active && !held
        val panel = framing && (scene.sceneHint || scene.noPerson)
        showSceneGuide(controls, scene, shown = panel, animate = before != null)
        turnShutter(controls, framing, counting = camera.countdown != null, animate = changed)
        if (!active) return

        controls.sceneTitle.setText(R.string.scene_title)
        // The hint chip over the image stays empty: the panel says what there is to say.
        showHint(controls.hintText, null)
        // An earlier guide is not part of the scene photo and would be in the way of framing it.
        controls.guideOverlay.isVisible = false
        controls.guideControls.isVisible = false
        controls.tipBanner.isVisible = false
        controls.peerCaption.isVisible = false
        if (scene.shotFailed) {
            showFailure(controls.captureStatus, R.string.scene_shot_failed)
        } else {
            controls.captureStatus.isVisible = false
        }
        openGeneratePoses(scene)
    }

    /**
     * The panel that says what to frame (R&S 6.3): it rises in when the step starts, and fades out when a touch takes
     * it away while the scene is still framed ([GenerationUiState.sceneHint], #231); it goes at once with the step.
     * After a photo without a person it rises in again with an alert, to say so and that another is needed
     * ([GenerationUiState.noPerson], FR-4.13, #296), until a touch or the next shot.
     */
    private fun showSceneGuide(
        controls: FragmentCameraBinding,
        scene: GenerationUiState,
        shown: Boolean,
        animate: Boolean,
    ) {
        val panel = controls.sceneGuide
        if (shown) {
            val noPerson = scene.noPerson
            controls.sceneGuideText.setText(
                if (noPerson) R.string.scene_no_person_title else scene.situation.framingHint,
            )
            controls.sceneGuideNext.setText(if (noPerson) R.string.scene_no_person_body else R.string.scene_guide_next)
            controls.sceneGuideIcon.setImageResource(
                if (noPerson) R.drawable.ic_alert_small else R.drawable.ic_lightbulb,
            )
            ImageViewCompat.setImageTintList(
                controls.sceneGuideIcon,
                ColorStateList.valueOf(
                    requireContext().getColor(if (noPerson) R.color.pix_danger else R.color.pix_text),
                ),
            )
            if (sceneGuideShown == true) return
            sceneGuideShown = true
            sceneGuideFading = false
            panel.animate().cancel()
            panel.alpha = 1f
            panel.isVisible = true
            if (animate) riseIn(panel)
            return
        }
        val was = sceneGuideShown
        sceneGuideShown = false
        val framing = scene.phase == GenerationUiState.Phase.FRAMING
        when {
            // A touch took it away while the scene is still framed.
            was == true && framing && panel.isVisible -> {
                sceneGuideFading = true
                panel.fadeTo(0f, Motion.HIDE_MS) {
                    sceneGuideFading = false
                    if (sceneGuideShown == false) panel.isVisible = false
                }
            }

            // The same touch changed something else on the step, the light or the ratios, which renders it again:
            // the fade goes on.
            sceneGuideFading && framing -> Unit

            // The step ended or its photo was taken: gone at once. Nothing to do while it is already gone.
            was != false || panel.isVisible -> {
                sceneGuideFading = false
                panel.animate().cancel()
                panel.isVisible = false
            }
        }
    }

    /** [view] coming in: up from [RISE_DP] below as it fades in, in [Motion.MOVE_MS]; at once while animations are off. */
    private fun riseIn(view: View) {
        view.animate().cancel()
        if (!Motion.on()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = RISE_DP * resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setDuration(Motion.MOVE_MS).setInterpolator(Motion.STANDARD).start()
    }

    /**
     * The shutter in the accent while the scene photo is framed (#210), white otherwise, and while the timer counts down
     * ([counting], FR-1.17) a stop square in the timer's ring, on either. As the step starts or ends ([animate]), the
     * new one fades in over the old in [Motion.MOVE_MS]; the countdown's comes and goes at once. Its tag names the
     * drawable it shows, [COUNTDOWN_SHUTTER] for the ring.
     */
    private fun turnShutter(controls: FragmentCameraBinding, scene: Boolean, counting: Boolean, animate: Boolean) {
        val button = controls.shutterButton
        if (counting) {
            if (button.tag == COUNTDOWN_SHUTTER) return
            button.tag = COUNTDOWN_SHUTTER
            shutterFade?.cancel()
            shutterFade = null
            button.background = ring().drawable
            return
        }
        val want = if (scene) R.drawable.bg_shutter_scene else R.drawable.bg_shutter
        if (button.tag == want) return
        val fromCountdown = button.tag == COUNTDOWN_SHUTTER
        button.tag = want
        shutterFade?.cancel()
        shutterFade = null
        val from = button.background
        val to = AppCompatResources.getDrawable(requireContext(), want)?.mutate()
        if (!animate || fromCountdown || !Motion.on() || from == null || to == null) {
            button.setBackgroundResource(want)
            return
        }
        to.alpha = 0
        button.background = LayerDrawable(arrayOf(from, to))
        // An animator rather than a TransitionDrawable, whose fade ignores the phone's animation speed.
        shutterFade = ValueAnimator.ofInt(0, 255).apply {
            duration = Motion.MOVE_MS
            interpolator = Motion.STANDARD
            addUpdateListener { to.alpha = it.animatedValue as Int }
            doOnEnd { if (button.tag == want) button.setBackgroundResource(want) }
            start()
        }
    }

    /**
     * The timer's ring on the shutter (FR-1.17), which turnShutter puts there while the timer counts down: it fills
     * through the countdown's seconds, the timer's whole length, and is full at the photo.
     */
    private fun renderCountdownRing(state: CameraUiState) {
        val left = state.countdown
        if (left == null) {
            countdownRing?.stop()
            return
        }
        ring().show(left, state.timer.seconds)
    }

    private fun ring(): CountdownRing = countdownRing ?: CountdownRing(requireContext()).also { countdownRing = it }

    /** The camera menu with the flash, aspect ratio, and brightness (FR-1.11–1.13, FR-1.17). */
    private fun renderCameraSettings(controls: FragmentCameraBinding, state: CameraUiState, ready: Boolean) {
        controls.cameraSettings.isVisible = true
        controls.aspectButton.isVisible = true
        renderMenu(controls, state)
        val aspect = aspectLabel(state.aspect)
        controls.aspectButton.text = aspect
        controls.aspectButton.contentDescription = getString(R.string.aspect_ratio_description, aspect)
        // A rebind waits for a photo being saved, so the ratio cannot change under the shutter; nor while the timer
        // counts down (FR-1.17), nor while the scene photo's shutter waits for its frame, which a rebind would take
        // away.
        val choosable = ready && !state.isSaving && state.countdown == null &&
            !generationViewModel.uiState.value.shooting
        controls.aspectButton.isEnabled = choosable
        // The ratios under the top bar, the chosen one selected (FR-1.13), with the ratio in the bar's pill while they
        // are open, as the camera menu's button.
        controls.aspectPanel.reveal(state.aspectsOpen)
        controls.aspectButton.isActivated = state.aspectsOpen
        aspectsBack?.isEnabled = state.aspectsOpen
        // The ratios cover the top of the image, where a toggle's label shows: opening them takes the label down.
        if (state.aspectsOpen && controls.toggleLabel.isVisible) {
            toggleLabelJob?.cancel()
            controls.toggleLabel.isVisible = false
            renderReference(controls, state)
        }
        for ((choice, option) in aspectOptions(controls)) {
            option.isSelected = choice == state.aspect
            option.isEnabled = choosable
        }

        val range = state.exposureRange
        val hasBrightness = range.first < range.last
        controls.exposureBar.render(range, state.exposureStep, state.exposure, enabled = ready && hasBrightness)
        focusBrightness?.place(imageFrame(controls, state))
        focusBrightness?.show(state.brightnessOpen && hasBrightness)
    }

    /**
     * The camera menu (FR-1.17): its button and the icons beside it open and close it. The flash and the timer go to
     * their next choice at each tap and the grid and the horizon bar on or off, leaving the menu open; Settings closes it
     * and opens that screen. Back closes it, and so does a touch anywhere else (setupTip).
     */
    private fun setupMenu(controls: FragmentCameraBinding) {
        controls.cameraMenuButton.setOnClickListener { viewModel.setMenuOpen(!viewModel.uiState.value.menuOpen) }
        // The flash's and the timer's icons beside the button are 16 dp, too small to take a tap of their own
        // (NFR-16): the button's area reaches over them, so a tap on either is a tap on the button (#318). Set at each
        // layout of their row, which an icon coming or going asks for.
        controls.cameraSettings.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reachOverIndicators(controls) }
        // During the scene photo, the phone's light, which a frame of the preview takes, where a photo's flash would not
        // reach it (FR-4.1).
        controls.menuFlash.setOnClickListener {
            if (isScenePhotoActive()) generationViewModel.toggleLight() else viewModel.onFlashTap()
        }
        controls.menuTimer.setOnClickListener { viewModel.onTimerTap() }
        controls.menuGrid.setOnClickListener { settingsViewModel.toggleShowGrid() }
        controls.menuLevel.setOnClickListener { settingsViewModel.toggleShowLevel() }
        controls.menuSettings.setOnClickListener {
            viewModel.setMenuOpen(false)
            findNavController().navigate(R.id.action_camera_to_settings)
        }
        menuBack = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            viewModel.setMenuOpen(false)
        }
    }

    /**
     * Lets the menu's button take the taps on the icons beside it: from the button to the last icon that shows, over
     * the row's height, and no further, so the ratio after them keeps its own. Nothing to reach over while neither
     * shows.
     */
    private fun reachOverIndicators(controls: FragmentCameraBinding) {
        val row = controls.cameraSettings
        val button = controls.cameraMenuButton
        val last = listOf(controls.timerIndicator, controls.flashIndicator).firstOrNull { it.isVisible }
        row.touchDelegate = if (last == null || !button.isVisible) {
            null
        } else {
            TouchDelegate(Rect(button.left, 0, last.right, row.height), button)
        }
    }

    /**
     * The camera menu's button, the flash and the timer beside it while either is on, and the menu's items, each in the
     * accent while what it sets is on (FR-1.17). During the scene photo the flash is the phone's light, on only once the
     * camera has it on, so it never claims a light that is not (FR-4.1), and Settings, which would leave the step, is
     * not there; while the photo is checked, none of it is.
     */
    private fun renderMenu(controls: FragmentCameraBinding, state: CameraUiState) {
        val scene = isScenePhotoActive()
        val held = scene && isScenePhotoHeld(generationViewModel.uiState.value)
        val counting = state.countdown != null
        val ready = hasCameraPermission() && state.cameraStatus == CameraStatus.READY
        val flashOn = state.hasFlash && if (scene) state.torch else state.flash != FlashMode.OFF
        val timerOn = state.timer != ShutterTimer.OFF
        controls.cameraMenuButton.isActivated = state.menuOpen
        controls.cameraMenuButton.isEnabled = !counting
        controls.flashIndicator.isVisible = flashOn
        controls.timerIndicator.isVisible = timerOn
        setImageOnce(controls.flashIndicator, if (scene) R.drawable.ic_flash_on else flashIcon(state.flash))
        setImageOnce(controls.timerIndicator, timerIcon(state.timer))
        val open = state.menuOpen && !held
        controls.cameraMenu.reveal(open)
        menuBack?.isEnabled = open
        // The flash only on a camera with one (FR-1.12).
        controls.menuFlash.isVisible = state.hasFlash
        controls.menuFlash.isEnabled = ready
        controls.menuFlash.isActivated = flashOn
        if (scene) {
            setImageOnce(controls.menuFlashIcon, if (state.torch) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
            controls.menuFlash.contentDescription =
                getString(if (state.torch) R.string.scene_light_on else R.string.scene_light_off)
        } else {
            setImageOnce(controls.menuFlashIcon, menuFlashIcon(state.flash))
            controls.menuFlash.contentDescription = getString(flashDescription(state.flash))
        }
        controls.menuSettings.isVisible = !scene
        controls.menuSettingsDivider.isVisible = !scene
        controls.menuTimer.isActivated = timerOn
        setImageOnce(controls.menuTimerIcon, menuTimerIcon(state.timer))
        controls.menuTimer.contentDescription = timerText(state.timer)
        val grid = settingsViewModel.showGrid.value
        controls.menuGrid.isActivated = grid
        controls.menuGrid.contentDescription = getString(if (grid) R.string.grid_on else R.string.grid_off)
        val level = settingsViewModel.showLevel.value
        controls.menuLevel.isActivated = level
        controls.menuLevel.contentDescription = getString(if (level) R.string.level_on else R.string.level_off)
        // Settings leaves Camera, which waits for a photo being saved.
        controls.menuSettings.isEnabled = !state.isSaving
        // The menu covers the top of the image, where a toggle's label shows: opening it takes the label down.
        if (open && controls.toggleLabel.isVisible) {
            toggleLabelJob?.cancel()
            controls.toggleLabel.isVisible = false
            renderReference(controls, state)
        }
    }

    /**
     * A text in the top bar's pills, *Shoot together*, *End session*, or the ratio, which grows with the phone's font only
     * up to [PILL_MAX_FONT_SCALE]. Larger, at 200 %, *Shoot together* would reach the ratio and the camera menu's icons,
     * and the ratio's label would make its pill and the bar taller than the icons' (#298, #272).
     */
    private fun capPillText(pill: TextView) {
        val scale = resources.configuration.fontScale
        if (scale > PILL_MAX_FONT_SCALE) {
            pill.setTextSize(TypedValue.COMPLEX_UNIT_PX, pill.textSize / scale * PILL_MAX_FONT_SCALE)
        }
    }

    /** What a choice in the camera menu changed, in its notice (FR-1.17). */
    private fun menuNoticeText(notice: MenuNotice): String = when (notice) {
        is MenuNotice.Flash -> getString(flashDescription(notice.mode))
        is MenuNotice.Timer -> timerText(notice.timer)
    }

    private fun timerText(timer: ShutterTimer): String =
        if (timer ==
            ShutterTimer.OFF
        ) {
            getString(R.string.timer_off)
        } else {
            getString(R.string.timer_seconds, timer.seconds)
        }

    @DrawableRes
    private fun flashIcon(mode: FlashMode): Int = when (mode) {
        FlashMode.OFF -> R.drawable.ic_flash_off
        FlashMode.AUTO -> R.drawable.ic_flash_auto
        FlashMode.ON -> R.drawable.ic_flash_on
    }

    /** The flash in the camera menu, in the menu's thinner line. */
    @DrawableRes
    private fun menuFlashIcon(mode: FlashMode): Int = when (mode) {
        FlashMode.OFF -> R.drawable.ic_menu_flash_off
        FlashMode.AUTO -> R.drawable.ic_menu_flash_auto
        FlashMode.ON -> R.drawable.ic_menu_flash_on
    }

    @StringRes
    private fun flashDescription(mode: FlashMode): Int = when (mode) {
        FlashMode.OFF -> R.string.flash_off
        FlashMode.AUTO -> R.string.flash_auto
        FlashMode.ON -> R.string.flash_on
    }

    /** The timer beside the camera menu's button, at the weight of the top bar's icons; never off, which shows none. */
    @DrawableRes
    private fun timerIcon(timer: ShutterTimer): Int =
        if (timer == ShutterTimer.TEN) R.drawable.ic_timer_10 else R.drawable.ic_timer_3

    /** The timer in the camera menu, in the menu's thinner line. */
    @DrawableRes
    private fun menuTimerIcon(timer: ShutterTimer): Int = when (timer) {
        ShutterTimer.OFF -> R.drawable.ic_menu_timer_off
        ShutterTimer.THREE -> R.drawable.ic_menu_timer_3
        ShutterTimer.TEN -> R.drawable.ic_menu_timer_10
    }

    /** Sets [image]'s drawable only when it changes, which the tag remembers: a render comes with every zoom step. */
    private fun setImageOnce(image: ImageView, @DrawableRes icon: Int) {
        if (image.tag == icon) return
        image.tag = icon
        image.setImageResource(icon)
    }

    /** The camera's image in the preview, in the chosen ratio (AD-10). */
    private fun imageFrame(controls: FragmentCameraBinding, state: CameraUiState): ViewfinderFrame {
        val preview = controls.previewView
        return fitViewfinderFrame(preview.width.toFloat(), preview.height.toFloat(), state.aspect.frameAspect)
    }

    /** The ratios under the top bar in their order: the tallest first (FR-1.13). */
    private fun aspectOptions(controls: FragmentCameraBinding): List<Pair<AspectRatio, View>> = listOf(
        AspectRatio.R16_9 to controls.aspect916Option,
        AspectRatio.R4_3 to controls.aspect34Option,
        AspectRatio.R1_1 to controls.aspect11Option,
    )

    /** A ratio as the phone is held, upright: 3:4, 9:16, or 1:1. */
    private fun aspectLabel(aspect: AspectRatio): String = getString(
        when (aspect) {
            AspectRatio.R4_3 -> R.string.aspect_3_4
            AspectRatio.R16_9 -> R.string.aspect_9_16
            AspectRatio.R1_1 -> R.string.aspect_1_1
        },
    )

    /**
     * A ratio chosen under the top bar (FR-1.13): a new one holds the preview's last frame while the camera rebinds, as
     * the ratio's change moves it (reframe); the chosen one only closes the ratios.
     */
    private fun chooseAspect(aspect: AspectRatio) {
        val state = viewModel.uiState.value
        if (aspect != state.aspect && !state.isSaving) {
            previewHold?.hold()
            veilStill(flip = false)
        }
        viewModel.onAspectChosen(aspect)
    }

    /** An exposure index in EV, such as "+0.7" or "0". */
    private fun formatEv(index: Int): String = ExposureScale.formatEv(index, viewModel.uiState.value.exposureStep)

    /**
     * A drag up or down on the preview (FR-1.11, Design 2.8): with the bar up after a tap to focus, it moves the
     * brightness on from there; without it, the drag focuses where it started first, as a tap would, so it starts
     * from the base brightness. Either way it moves the bar's sun as a drag on the bar would.
     */
    private val previewBrightnessDrag = object : PinchZoomController.VerticalDrag {
        override fun onStart(x: Float, y: Float): Boolean {
            val controls = binding ?: return false
            val state = viewModel.uiState.value
            if (state.exposureRange.first >= state.exposureRange.last || isScenePhotoActive()) return false
            if (!state.brightnessOpen || !controls.exposureBar.isVisible) {
                onPreviewTap(x, y)
                if (!viewModel.uiState.value.brightnessOpen) return false
                // The focus went back to the base brightness; the sun starts there, before the camera's echo.
                val base = viewModel.baseExposureIndex() ?: return false
                controls.exposureBar.render(state.exposureRange, state.exposureStep, base, enabled = true)
            }
            if (!viewModel.uiState.value.brightnessOpen) return false
            controls.exposureBar.startDrag()
            return controls.exposureBar.isDragging
        }

        override fun onMove(up: Float) {
            binding?.exposureBar?.dragTo(up)
        }

        override fun onEnd() {
            binding?.exposureBar?.finishDrag()
        }
    }

    /**
     * A tap on the preview inside the camera image: focus there and show where (FR-1.4), and bring the brightness bar
     * beside it, back at the base brightness (FR-1.11).
     */
    private fun onPreviewTap(x: Float, y: Float) {
        val controls = binding ?: return
        val state = viewModel.uiState.value
        val hasBrightness = state.exposureRange.first < state.exposureRange.last
        if (state.cameraStatus != CameraStatus.READY || isScenePhotoActive()) return
        if (!state.supportsFocusPoint && !hasBrightness) return
        val frame = imageFrame(controls, state)
        if (frame.width <= 0f || x !in frame.left..frame.right || y !in frame.top..frame.bottom) return
        viewModel.onFocusTap((x - frame.left) / frame.width, (y - frame.top) / frame.height)
        val focusBrightness = focusBrightness ?: return
        focusBrightness.tap(x, y, focuses = state.supportsFocusPoint, brightness = hasBrightness)
        if (hasBrightness) {
            viewModel.setBrightnessOpen(true)
            focusBrightness.place(frame)
            focusBrightness.hold()
        }
    }

    /**
     * The subject's tap to focus (FR-7.7): the focus box where they tapped, alone, since the bar comes up for this
     * phone's own tap. A bar this phone has up goes: the camera no longer focuses beside it, and the brightness is the
     * base again. While a finger is on that bar nothing moves under it, and not during the scene photo, which shows no
     * box for this phone's taps either.
     */
    private fun showRemoteFocus(controls: FragmentCameraBinding, focus: RemoteFocus) {
        if (controls.exposureBar.isHeld || isScenePhotoActive()) return
        val frame = imageFrame(controls, viewModel.uiState.value)
        if (frame.width <= 0f || frame.height <= 0f) return
        // The box first: the bar's going then leaves it alone.
        focusBrightness?.showBoxAlone(frame.left + focus.x * frame.width, frame.top + focus.y * frame.height)
        viewModel.setBrightnessOpen(false)
    }

    /**
     * While a subject watches (R&S 6.2): the line right above the shutter, as Subject view's that names the camera,
     * "Joonhyung · Watching your camera". The open tip takes its place, as on Subject view, since at the largest font
     * the two do not fit above the shutter together; the scene photo's step hides it (renderScenePhoto).
     */
    private fun renderPeerCaption(controls: FragmentCameraBinding) {
        val connected = sessionViewModel.state.value as? SessionState.Connected
        val name = connected?.takeIf { it.role == Role.PHOTOGRAPHER }?.peer?.displayName
        val shown = name != null && !controls.tipBanner.isVisible && !isScenePhotoActive()
        controls.peerCaption.isVisible = shown
        if (!shown) return
        val overlays = controls.bottomOverlays
        SessionCaption(
            controls.peerCaption,
            controls.peerCaptionDot,
            controls.peerCaptionTitle,
            controls.peerCaptionSeparator,
            controls.peerCaptionStatus,
        ) { getString(R.string.peer_caption_title, it.orEmpty()) }
            .fit(
                overlays.width - overlays.paddingLeft - overlays.paddingRight,
                name,
                getString(R.string.peer_caption_status),
                live = true,
            )
    }

    /**
     * The live light (FR-6.8): on while a session is [connected] and the camera streams to it, so not over a camera
     * message. A rebind (a lens or a ratio) keeps it, as the held frame stands for a camera about to stream again.
     */
    private fun renderLiveDot(controls: FragmentCameraBinding, connected: SessionState.Connected?) {
        controls.liveDot.isVisible = connected != null && !controls.cameraMessagePanel.isVisible
        connected?.let { controls.liveDot.contentDescription = getString(R.string.live_badge, it.peer.displayName) }
    }

    /** Puts the preview's last frame over it while the camera rebinds (PreviewHold); false without a frame. */
    private fun showStill(): Boolean {
        val controls = binding ?: return false
        val frame = previewFrame() ?: return false
        endMove()
        // One still fading out is replaced.
        clearStill(controls)
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        controls.aspectStill.setImageBitmap(
            heldFrame(frame, aspect, controls.aspectStill.context.getColor(R.color.camera_dim)),
        )
        // The bitmap is the whole camera image; show the old frame only, as the preview did.
        controls.aspectStill.clipBounds = controls.previewView.clipBounds
        controls.aspectStill.isVisible = true
        controls.stillGround.isVisible = true
        // The grid stays over a held frame of the same ratio; a 1:1 one has its outside dimmed already.
        controls.compositionOverlay.dimOutside = false
        stillAspect = aspect
        stillPlace = shown
        Timings.mark("preview.still", "on")
        stillJob?.cancel()
        // If the stream never reports a restart, the old frame still goes.
        stillJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(STILL_TIMEOUT_MS)
            previewHold?.release()
            render(viewModel.uiState.value)
        }
        return true
    }

    private fun hideStill() {
        Timings.mark("preview.still", "off")
        stillJob?.cancel()
        stillJob = null
        // A frame still moving or turning goes once it is still (stillSettled).
        if (move == null && flip == null) dropStill()
    }

    /** A move or a turn has ended: the held frame goes now if the camera streams again. */
    private fun stillSettled() {
        if (move != null || flip != null || previewHold?.holding == true) return
        dropStill()
        render(viewModel.uiState.value)
    }

    /**
     * How the held frame waits while the camera rebinds for a new ratio or the other camera (Design 2.6.2): dimmed,
     * and blurred where Android can (12 and later); for the other camera it also turns over, top over bottom
     * ([flip]). A lens change of the zoom leaves it as it is.
     */
    private fun veilStill(flip: Boolean) {
        val controls = binding ?: return
        if (previewHold?.holding != true || !controls.aspectStill.isVisible) return
        if (flip) flipStill(controls.aspectStill)
        if (stillVeiled) return
        stillVeiled = true
        val scrim = controls.previewScrim
        if (!scrim.isVisible) {
            scrim.alpha = 0f
            scrim.isVisible = true
        }
        scrim.fadeTo(HOLD_DIM, Motion.DIM_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurStill(controls.aspectStill)
    }

    /**
     * Turns the held frame over, top over bottom, in [Motion.FLIP_MS], as the other camera opens (FR-1.14): to its edge,
     * then from the other edge back to flat, so its face shows throughout. Not while animations are off.
     */
    private fun flipStill(still: ImageView) {
        if (!Motion.on()) return
        flip?.let {
            flip = null
            it.cancel()
        }
        flip = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.FLIP_MS
            interpolator = Motion.STANDARD
            addUpdateListener {
                val turned = it.animatedValue as Float
                still.rotationX = if (turned < 0.5f) HALF_TURN * turned else HALF_TURN * turned - HALF_TURN
            }
            doOnEnd { ended ->
                if (flip !== ended) return@doOnEnd
                flip = null
                still.rotationX = 0f
                stillSettled()
            }
            start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurStill(still: ImageView) {
        val radius = STILL_BLUR_DP * resources.displayMetrics.density
        if (!Motion.on()) {
            setStillBlur(still, radius)
            return
        }
        blur = ValueAnimator.ofFloat(0f, radius).apply {
            duration = Motion.DIM_MS
            addUpdateListener { setStillBlur(still, it.animatedValue as Float) }
            start()
        }
    }

    private fun setStillBlur(still: ImageView, radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || stillBlur == radius) return
        stillBlur = radius
        still.setRenderEffect(
            if (radius < MIN_BLUR_PX) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
    }

    /**
     * Takes the held frame down for the camera's own preview: faded after a new ratio or the other camera, with the
     * grid and the guide fading back in after a ratio change; at once after a lens change of the zoom, which goes on
     * under the fingers, while animations are off, and when the screen goes.
     */
    private fun dropStill() {
        val controls = binding ?: return
        val overlaysBack = reframing
        reframing = false
        val still = controls.aspectStill
        if (!still.isVisible) return
        if (!stillVeiled || previewHold == null || !Motion.on()) {
            clearStill(controls)
            return
        }
        // The new preview shows through as the held frame fades.
        controls.stillGround.isVisible = false
        if (overlaysBack) {
            for (overlay in listOf(controls.guideOverlay, controls.compositionOverlay)) {
                overlay.alpha = 0f
                overlay.fadeTo(1f, Motion.FADE_MS)
            }
        } else if (stillAspect?.let { it.previewAspect != it.frameAspect } == true) {
            // A lens switch in 1:1: the grid dims the outside again at once, so the frame fades inside it only.
            still.clipBounds = shown?.frameClip()
        }
        still.fadeTo(0f, Motion.FADE_MS) { binding?.let(::clearStill) }
        controls.previewScrim.fadeTo(0f, Motion.FADE_MS)
    }

    private fun clearStill(controls: FragmentCameraBinding) {
        blur?.let {
            blur = null
            it.cancel()
        }
        flip?.let {
            flip = null
            it.cancel()
        }
        val still = controls.aspectStill
        setStillBlur(still, 0f)
        still.animate().cancel()
        still.isVisible = false
        still.setImageDrawable(null)
        still.imageMatrix = null
        still.alpha = 1f
        still.rotationX = 0f
        controls.stillGround.isVisible = false
        controls.previewScrim.animate().cancel()
        controls.previewScrim.alpha = 0f
        controls.previewScrim.isVisible = false
        stillVeiled = false
        stillAspect = null
        stillPlace = null
    }

    /** The preview blinks as a photo is taken (FR-1.5), down and back in [Motion.BLINK_MS]; not without animations. */
    private fun blinkPreview(scrim: View) {
        if (!Motion.on() || binding?.aspectStill?.isVisible == true) return
        scrim.alpha = 0f
        scrim.isVisible = true
        scrim.fadeTo(SHUTTER_DIM, Motion.BLINK_MS / 3) {
            scrim.fadeTo(0f, Motion.BLINK_MS * 2 / 3) { scrim.isVisible = false }
        }
    }

    /**
     * Clips the preview to where its image shows (AD-10): the frame, or for 1:1 the whole 4:3 area with the rest
     * dimmed (R&S 6.2), and puts the live light in the frame's corner. Nothing is clipped before layout.
     */
    private fun fitToFrame(controls: FragmentCameraBinding) {
        val place = shown
        val clip = place?.clip()
        if (controls.previewView.clipBounds != clip) controls.previewView.clipBounds = clip
        if (controls.previewScrim.clipBounds != clip) controls.previewScrim.clipBounds = clip
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        // A held 1:1 frame has its outside dimmed already (heldFrame), and the grid stays over it during a lens switch.
        controls.compositionOverlay.dimOutside =
            aspect.previewAspect != aspect.frameAspect && previewHold?.holding != true
        place?.let { controls.liveDot.placeAt(it.frame.inArea(it.area)) }
    }

    /** What of the area shows, in the area's coordinates; null when all of it does. */
    private fun FramePlace.clip(): Rect? = if (showsArea) null else shows.inArea(area).toRect()

    /** The frame alone, in the area's coordinates. */
    private fun FramePlace.frameClip(): Rect = frame.inArea(area).toRect()

    private fun ViewfinderFrame.inArea(area: PreviewArea) = copy(top = top - area.top)

    private fun ViewfinderFrame.toRect() =
        Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

    /** The hint chip over the preview: what to do next, with an icon in the color of what it is about. */
    private fun showHint(
        chip: TextView,
        @StringRes text: Int?,
        @DrawableRes icon: Int = 0,
        @ColorRes tint: Int = R.color.pix_text,
    ) {
        if (text == null) chip.text = null else chip.setText(text)
        chip.isInvisible = text == null
        setChipIcon(chip, icon)
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(requireContext().getColor(tint)))
    }

    /** Sets [icon] at the start of [chip], at the size of the top bar's icons, or removes it for 0. */
    private fun setChipIcon(chip: TextView, @DrawableRes icon: Int) {
        val size = (CHIP_ICON_DP * resources.displayMetrics.density).roundToInt()
        val drawable = if (icon == 0) null else AppCompatResources.getDrawable(requireContext(), icon)?.mutate()
        drawable?.setBounds(0, 0, size, size)
        chip.setCompoundDrawablesRelative(drawable, null, null, null)
    }

    /** The status chip: why a photo failed, in the danger color. */
    private fun showFailure(chip: TextView, @StringRes text: Int) {
        val context = requireContext()
        chip.isVisible = true
        chip.setText(text)
        chip.setBackgroundResource(R.drawable.bg_chip_danger)
        chip.setTextColor(context.getColor(R.color.pix_danger_text))
        setChipIcon(chip, R.drawable.ic_alert_small)
        // The alert takes the text color.
        TextViewCompat.setCompoundDrawableTintList(
            chip,
            ColorStateList.valueOf(context.getColor(R.color.pix_danger_text)),
        )
    }

    /**
     * Sets [icon] on [button] only when it differs from the one set last: each call inflates a new drawable and lays
     * the button out again, and the screen renders on every zoom step of a pinch.
     */
    private fun setIconOnce(button: MaterialButton, @DrawableRes icon: Int) {
        if (button.getTag(R.id.icon_resource) == icon) return
        button.setIconResource(icon)
        button.setTag(R.id.icon_resource, icon)
    }

    /**
     * What a toggle just did, at the top of the image for [CameraViewModel.NOTICE_MS] (Design 2.8): a new label
     * replaces the one showing at once, and the hint steps aside meanwhile.
     */
    private fun showToggleLabel(
        controls: FragmentCameraBinding,
        text: String,
        @DrawableRes icon: Int,
        @ColorRes tint: Int = R.color.pix_text,
    ) {
        val chip = controls.toggleLabel
        chip.text = text
        setLabelIcon(chip, icon, tint)
        chip.isVisible = true
        renderReference(controls, viewModel.uiState.value)
        toggleLabelJob?.cancel()
        toggleLabelJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(CameraViewModel.NOTICE_MS)
            chip.isVisible = false
            binding?.let { renderReference(it, viewModel.uiState.value) }
        }
    }

    /** Asks before ending the session, as Subject view's Leave and Back do (#298): the subject's view closes for good. */
    private fun confirmEndSession() {
        if (endSessionDialog?.isShowing == true) return
        endSessionDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.end_session_title)
            .setPositiveButton(R.string.end_session) { _, _ ->
                val state = sessionViewModel.state.value
                if (state is SessionState.Connected || state is SessionState.Reconnecting) sessionViewModel.leave()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dismissEndSession() {
        endSessionDialog?.dismiss()
        endSessionDialog = null
    }

    private fun dismissUndo() {
        undoNotice?.dismiss()
        undoNotice = null
    }

    /**
     * The media access offer's *Allow access* (FR-1.16): Android's screen that lets Pix manage the phone's media, then the
     * photo is deleted, without Android's question once allowed. A phone without that screen asks Android's question at
     * once.
     */
    private fun allowAndDelete(uri: Uri) {
        viewModel.deleteAfterAccess(uri)
        val settings = requireContext().manageMediaSettings()
        try {
            if (settings != null) mediaAccess.launch(settings) else viewModel.accessAnswered()
        } catch (_: ActivityNotFoundException) {
            viewModel.accessAnswered()
        }
    }

    /**
     * Notices take the zoom row's place, on the line it stands on, so they cover the readout and the buttons beside it
     * but never the shutter or the controls under the image (#298); above the controls while the row is hidden. Over
     * the photo viewer, they sit where it says, not over the photo.
     */
    override val noticeAnchor: View?
        get() {
            val controls = binding ?: return null
            photoViewer?.takeIf { it.isOpen }?.let { return it.noticeAnchor }
            return if (controls.zoomRow.isShown) controls.noticeLine else controls.bottomPanel
        }

    /**
     * Whether the camera itself is what the photographer looks at. Under a sheet, such as Add a pose guide, this screen
     * stays started with its camera running, and so it does under its own photos and under a dialog of its own, the
     * AI notice for the subject's poses, "End the session?", or the tutorial's tips: none of them is "in front", and
     * what the subject asks of the camera is refused then (#314, #276). A new dialog of this screen belongs here too.
     */
    private fun cameraInFront(): Boolean {
        // Without a navigation controller, as in a screen test, nothing can cover it.
        val destination = runCatching { findNavController().currentDestination?.id }.getOrNull()
        val covered = askedForSubject != null || endSessionDialog?.isShowing == true ||
            TutorialFragment.over(this) != null
        return (destination == null || destination == R.id.cameraFragment) && !viewModel.uiState.value.photosOpen &&
            !covered
    }

    /**
     * The subject's shutter (FR-7.8, #314): this screen's own shutter, pressed for them, with this phone's timer when
     * it is on (FR-1.17), or, with [cancel], the stop of its countdown. Refused when the camera is not in front
     * ([cameraInFront]), or when it is not ready or still takes the photo before. A pose's scene photo, framed or
     * held, is the photographer's own step and never taken from the other phone; its countdown is the timer's like any
     * other, so the subject's shutter stops it too.
     */
    private fun takeForSubject(controls: FragmentCameraBinding, cancel: Boolean): ShutterOutcome {
        if (!cameraInFront()) return ShutterOutcome.REFUSED
        if (isScenePhotoActive()) {
            return if (cancel) viewModel.onSubjectShutter(cancel = true) else ShutterOutcome.REFUSED
        }
        val outcome = viewModel.onSubjectShutter(cancel)
        if (outcome == ShutterOutcome.TAKEN) blinkPreview(controls.previewScrim)
        return outcome
    }

    /**
     * The subject picked a situation for AI poses (FR-6.15, #276): the scene photo's step begins here, as if this
     * phone's user had picked the tile, and [answer] hears whether it began. Only from the camera itself
     * ([cameraInFront]): a request that comes over a step, a sheet, the photos, or the question below is refused, and
     * so is a selfie on a phone without a front camera, and one that comes while the timer counts down, when this
     * phone's own Guide waits too (FR-1.17). This phone sends the photo, so without its user's agreement (FR-4.2) the
     * notice asks first, saying who wants the poses; the step begins once they agree, and "Not now" refuses.
     */
    private fun startPosesForSubject(situation: Situation, answer: (PoseStartStep) -> Unit) {
        val counting = viewModel.uiState.value.countdown != null
        val free = cameraInFront() && !isScenePhotoActive() && !counting
        if (!free || !generationViewModel.chooseSituation(situation)) return answer(PoseStartStep.REFUSED)
        if (!generationViewModel.needsConsent) {
            generationViewModel.beginScene()
            return answer(PoseStartStep.STARTED)
        }
        // The subject's phone hears that the question is up, and waits for its answer however long it takes.
        answer(PoseStartStep.ASKING)
        val name = (sessionViewModel.state.value as? SessionState.Connected)?.peer?.displayName
        val words = listOfNotNull(
            name?.let { getString(R.string.pose_start_asked, it) },
            getString(R.string.consent_message),
        ).joinToString("\n\n")
        var agreed = false
        askedInSession = sessionId(sessionViewModel.state.value)
        askedForSubject = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.consent_title)
            .setMessage(words)
            .setPositiveButton(R.string.consent_agree) { _, _ ->
                agreed = true
                generationViewModel.onConsentGiven()
                generationViewModel.beginScene()
                answer(PoseStartStep.STARTED)
            }
            .setNegativeButton(R.string.not_now, null)
            .setOnDismissListener {
                askedForSubject = null
                if (!agreed) answer(PoseStartStep.REFUSED)
            }
            .show()
    }

    /** The id of the session [state] is, running or reconnecting; null outside one. */
    private fun sessionId(state: SessionState): String? = when (state) {
        is SessionState.Connected -> state.sessionId
        is SessionState.Reconnecting -> state.sessionId
        else -> null
    }

    /**
     * Takes the question for the subject's poses down unanswered, which refuses the request: its session is over, so
     * agreeing would begin the step for no one, or in another subject's session.
     */
    private fun dismissAskedForSubject() {
        askedForSubject?.dismiss()
        askedForSubject = null
        askedInSession = null
    }

    /** Over the guide-edit mode toggle for [CameraViewModel.NOTICE_MS]: what a pinch moves now (Design 2.8). */
    private fun showPinchMode(controls: FragmentCameraBinding, guide: Boolean) {
        showPinchModeLabel(controls.pinchModeLabel, guide)
        pinchModeJob?.cancel()
        pinchModeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(CameraViewModel.NOTICE_MS)
            controls.pinchModeLabel.isVisible = false
        }
    }

    /** Shows [text] for [durationMs], in place of the notice showing; returns the job that takes it down. */
    private fun showNotice(
        controls: FragmentCameraBinding,
        text: String,
        durationMs: Long = CameraViewModel.NOTICE_MS,
    ): Job {
        controls.noticeText.text = text
        controls.noticeText.isVisible = true
        noticeJob?.cancel()
        return viewLifecycleOwner.lifecycleScope.launch {
            delay(durationMs)
            // What still goes on, a wait for the link or the subject's pinch, has its line back, which this notice
            // took the place of.
            val held = heldNotice
            if (held != null) controls.noticeText.text = held else controls.noticeText.isVisible = false
        }.also { noticeJob = it }
    }

    /**
     * A notice that stays while what it says goes on, as while the subject pinches (#292) or the link is tried again
     * (#293): it has no time of its own, and goes with [letHeldNoticeGo] or when another notice takes its place. One
     * that comes later in [HeldNotice] waits under an earlier one.
     */
    private fun holdNotice(controls: FragmentCameraBinding, kind: HeldNotice, text: String) {
        heldNotices[kind] = text
        if (HeldNotice.entries.first { it in heldNotices } != kind) return
        controls.noticeText.text = text
        controls.noticeText.isVisible = true
        noticeJob?.cancel()
    }

    /**
     * What a held notice said is over: it goes, and one that waited under it shows, unless another notice has taken its
     * place and keeps its own time.
     */
    private fun letHeldNoticeGo(controls: FragmentCameraBinding, kind: HeldNotice) {
        val shown = heldNotice
        heldNotices.remove(kind) ?: return
        if (noticeJob?.isActive == true || controls.noticeText.text.toString() != shown) return
        val next = heldNotice
        if (next != null) controls.noticeText.text = next else controls.noticeText.isVisible = false
    }

    /** The subject is back before the notice that it left ended, so the notice no longer holds. */
    private fun takeDownPeerGoneNotice(controls: FragmentCameraBinding) {
        val notice = peerGoneNotice ?: return
        peerGoneNotice = null
        // A later notice has already replaced it.
        if (!notice.isActive) return
        notice.cancel()
        controls.noticeText.isVisible = false
    }

    /**
     * The thumbnail (FR-1.6): its photos over the camera, inside Pix: the session's in a session, at the newest, so the
     * session and the subject's video go on, and the phone's otherwise, as a grid of them all first, which the phone's
     * gallery could show only one photo of. While Pix may read only its own photos, the tap asks for the others first;
     * Android shows its question until it was refused twice, and then answers at once.
     */
    private fun onThumbnailTap() {
        viewModel.setAspectsOpen(false)
        val state = viewModel.uiState.value
        if (state.sessionPhotos == null && state.photoAccess == PhotoAccess.OWN && state.countdown == null) {
            photoPermission.launch(photoPermissions())
        } else {
            viewModel.openPhotos()
        }
    }

    /**
     * The grid's line under its header (FR-1.6): the chosen photos are chosen again in Android's question; refused,
     * Android asks once more, and after that only the phone's settings can allow them.
     */
    private fun changePhotoAccess() {
        val ask = viewModel.uiState.value.photoAccess == PhotoAccess.CHOSEN ||
            shouldShowRequestPermissionRationale(photoPermissions().first())
        if (ask) photoPermission.launch(photoPermissions()) else openAppSettings()
    }

    /** Pix's page in the phone's settings, where its permissions are. */
    private fun openAppSettings() {
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    }

    /** What a notice without a time of its own says, the one that shows first when several are held. */
    private enum class HeldNotice { RECONNECTING, ZOOMING }

    companion object {
        private const val PERMISSION_REQUESTED = "camera.permissionRequested"
        private const val STILL_TIMEOUT_MS = 2_000L
        private const val HOLD_DIM = 0.5f
        private const val SHUTTER_DIM = 0.6f
        private const val STILL_BLUR_DP = 16f
        private const val MIN_BLUR_PX = 0.5f

        private const val HALF_TURN = 180f
        private const val FLIP_CAMERA_DISTANCE = 8_000f
        private const val CHIP_ICON_DP = 14f

        /** The controls that wait while the timer counts down, dimmed this much (FR-1.17). */
        private const val COUNTDOWN_DIM = 0.35f

        /** The shutter's tag while it shows the timer's ring (turnShutter); no drawable's id is 0. */
        @VisibleForTesting
        internal const val COUNTDOWN_SHUTTER = 0

        /** The largest font scale the top bar's pill follows (capPillText). */
        private const val PILL_MAX_FONT_SCALE = 1.3f

        /** The pose flow's step while the scene photo is framed, and while the photo taken is held (FlowProgressView, #210). */
        private const val SCENE_STEP = 1

        /** How far below its place the scene photo's panel starts as it rises in (#210). */
        private const val RISE_DP = 12f

        /** How long the brightness bar stays up without a touch (FR-1.11). */
        const val BRIGHTNESS_IDLE_MS = FocusBrightness.IDLE_MS
    }
}
