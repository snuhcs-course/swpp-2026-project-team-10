// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Dongje Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.text.LineBreaker
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.OwnActivities
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.session.ShadowSurfaceViewRenderer
import com.lastpenguin.pix.ui.session.SubjectFragment
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** How much of a 4:3 image's bottom the scene photo's panel may cover on the S22 when its hint takes two lines, in dp (#231). */
private const val SCENE_PANEL_OVER_IMAGE_DP = 16

/**
 * How wide a step's name may be on the S22: twice what is between its icon's middle and the shutter, less 4 dp
 * (`SubjectFragment.renderPoses`, #314).
 */
private const val STEP_NAME_WIDTH_DP = 2 * ((360 - 32) / 3f - 26 - 36 - 4)

/** The large font scales the layout holds at: Android's own steps up to its largest, 200 % (#298). */
private val LARGE_FONTS = listOf(1.3f, 1.5f, 2f)

/*
 * The camera screens' layout on the Galaxy S22's app area, 360 x 705 dp (R&S 6.2, 6.6; Testing Documentation 2.1.6).
 * Ratios: 4:3, 16:9, and 1:1, each with the preview right below the top bar, the shutter's row in one place, and the
 * zoom row at the bottom of the image; 16:9 with every control of that row above the image's bottom edge. States of
 * the top bar: no guide, a guide with a pose tip, a session, and the flash and the timer on beside the camera menu's
 * button (FR-1.17), at font scale 1, 1.3, 1.5, and 2 (#298), none overlapping; the camera menu's button, the ratio,
 * and the camera switch drawing one 32 dp pill on one line at each of those scales, 50 dp wide at 1 (#272). The camera switch in the top bar
 * (FR-1.14), and the gallery button as far right of the shutter as Guide is left of it. The camera menu under the top
 * bar, each item taking taps on 48 dp, with its labels whole at font scale 1.3, 1.5, and 2 (FR-1.17; in Korean:
 * KoreanScreensTest). A guide's
 * panel in the space below a 4:3 image. The frame held while the ratio changes, moved with the scene into the new
 * ratio's area as the camera rebinds (Iter3-A12; until then it kept its place and the old ratio's area until the new
 * one streamed); a held 1:1 frame dimmed once. The ratios under the top bar, in their shapes, each taking taps on 48 dp.
 * The zoom readout's tap
 * area, the row's height, and every control's, at least 48 dp (NFR-16). Subject view's video placed the same way;
 * its session photo's thumbnail (Iter4-A2) on Guide's icon line, shown before any photo, Guide, the shutter, and the
 * thumbnail where Camera's Guide, shutter, and gallery button are (#314); a step's button beside the thumbnail, which moves
 * aside for it, clear of the shutter with each of its labels at font scale 1, 1.3, 1.5, and 2; right above the shutter,
 * whose camera it is and "Live" (#271), on one line or, where they do not fit, the name over "Live", with a short name, a
 * Galaxy's own name, and a longer one cut short, and while a photo comes, at each font scale; that line gone while the tip
 * is open. At font scale 1.3, 1.5, and 2, every text on both screens whole: none cut short or taller than its view (#298),
 * with the camera menu open and the timer counting down too (FR-1.17).
 * The session photos' viewer over the whole screen,
 * on Subject view and on Camera.
 * The scene photo just taken, held while it is checked (R&S 6.3, #296): a 3:4 photo exactly over the preview, under
 * the flow's bar, which starts at the image's top edge to the pixel while framing and while the photo is held. The
 * step's bar the camera's own, in height, the image where it is, and its icons' and texts' sizes, Back's icon the
 * menu's; Back and the title as far from the edge as every header has them, on the camera's line; the menu and the
 * ratio at the bar's end, every part whole at font scale 1, 1.3, 1.5, and 2 (#204, #210). The camera switch's icon
 * 2 dp larger than the bar's others.
 * A photo without a person: the panel that says so, whole above the shutter (#296).
 * The reference thumbnail hidden at the side (#237), in each corner: 16 dp of it in the preview, and its handle 48 dp
 * wide over that strip, inside the preview and above the zoom row; shown again, whole in its corner.
 * A photo taken with the phone held sideways, held as framed while the photo sent on is turned upright (#206).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    qualifiers = "w360dp-h705dp-xxhdpi",
    shadows = [ShadowSurfaceViewRenderer::class],
    instrumentedPackages = ["org.webrtc"],
)
class CameraLayoutTest {
    private class Camera(
        val activity: AppCompatActivity,
        val fragment: CameraFragment,
        val controls: FragmentCameraBinding,
        val modules: TestAppContainer,
    )

    @get:Rule
    val activities = OwnActivities()

    private fun activity(): AppCompatActivity = activities.start(R.style.Theme_Pix)

    private fun camera(fontScale: Float = 1f): Camera {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer(camera = FakeCameraController(hasFlash = true, exposureRange = -6..6)).install()
        val activity = activity()
        val nav = TestNavHostController(activity).apply { setGraph(R.navigation.nav_graph) }
        Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
        val fragment = CameraFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idle()
        return Camera(activity, fragment, FragmentCameraBinding.bind(fragment.requireView()), modules)
    }

    private fun Camera.ratio(aspect: AspectRatio) {
        val camera = modules.camera as FakeCameraController
        camera.state.value = camera.state.value.copy(aspect = aspect)
        idle()
    }

    /** A generated pose's guide with its tip arrived and the banner closed: the light bulb shows. */
    private fun Camera.guideWithTip() {
        val guide = ReferenceGuide(
            id = "g1",
            mask = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888),
            outline = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) },
            aspect = 0.5f,
            source = GuideSource.GENERATED,
            anchor = NormalizedRect(0.2f, 0.1f, 0.8f, 0.95f),
        )
        modules.tips.prepare(guide, bitmap(), TipSource(bitmap(), "Walk past the railing"))
        modules.guides.setGuide(guide)
        idle()
        modules.tipper.requests.last().tip("Hold the phone at your chest and keep it level.")
        idle()
        val now = SystemClock.uptimeMillis()
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 500f, 900f, 0))
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 500f, 900f, 0))
        idle()
    }

    private fun Camera.session() {
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
    }

    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    /** Where [view] is drawn on the screen, its translation included, in dp. */
    private fun View.box(): Rect {
        val at = IntArray(2)
        getLocationInWindow(at)
        val density = resources.displayMetrics.density
        fun dp(px: Number) = (px.toFloat() / density).roundToInt()
        return Rect(dp(at[0]), dp(at[1]), dp(at[0] + width), dp(at[1] + height))
    }

    // ---- The reference thumbnail hidden at the side (#237) ------------------------------------------

    @Test
    fun aHiddenThumbnail_leavesItsPeekAtItsCornersSide_underAHandleATouchTargetWide() {
        val screen = camera()
        val controls = screen.controls
        // A 3:4 reference photo: small, its thumbnail is 54 x 72 dp.
        screen.modules.guides.setGuide(guide())
        val model = ViewModelProvider(screen.fragment, PixViewModels.Factory)[CameraViewModel::class.java]
        val preview = controls.previewContainer.box()

        for (corner in ReferenceCorner.entries) {
            model.setReferenceCorner(corner)
            model.setReferenceHidden(true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

            val thumbnail = controls.referenceThumbnail.box()
            val strip = Rect(thumbnail).apply { intersect(preview) }
            // Only 16 dp of it are in the preview, at its corner's side and height, and see-through.
            assertEquals("$corner", 16, strip.width())
            assertEquals("$corner", if (corner.isLeft) preview.left else preview.right - 16, strip.left)
            assertEquals("$corner", 72, thumbnail.height())
            assertEquals("$corner", REFERENCE_HIDDEN_ALPHA, controls.referenceThumbnail.alpha, 0f)
            // Its handle: a touch target wide from that side, over the strip, in the preview above the zoom row.
            val handle = controls.referenceHandle.box()
            assertTrue("$corner", controls.referenceHandle.isShown)
            assertEquals("$corner", 48, handle.width())
            assertEquals("$corner", if (corner.isLeft) preview.left else preview.right - 48, handle.left)
            assertTrue("$corner: $handle over $strip", handle.contains(strip))
            assertTrue("$corner", handle.top >= preview.top && handle.bottom <= controls.zoomRow.box().top)
        }

        // Shown again, it is whole in its corner, and nothing lies over the preview's side.
        model.setReferenceHidden(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertFalse(controls.referenceHandle.isShown)
        assertTrue(preview.contains(controls.referenceThumbnail.box()))
        assertEquals(1f, controls.referenceThumbnail.alpha, 0f)
    }

    @Test
    fun aHiddenThumbnailOfAWidePhoto_keepsItsHandleInsideTheThumbnailsArea() {
        val screen = camera()
        val controls = screen.controls
        // A 4:1 panorama: small, its thumbnail is 72 x 18 dp, shorter than the handle's 48 dp.
        screen.modules.guides.setGuide(guide().copy(reference = ScreenTestSupport.bitmap(400, 100)))
        val model = ViewModelProvider(screen.fragment, PixViewModels.Factory)[CameraViewModel::class.java]
        // Where a thumbnail may be: from its top in a top corner to its bottom in a bottom one.
        val shown = ReferenceCorner.entries.associateWith { corner ->
            model.setReferenceCorner(corner)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            controls.referenceThumbnail.box()
        }
        val top = shown.getValue(ReferenceCorner.TOP_LEFT).top
        val bottom = shown.getValue(ReferenceCorner.BOTTOM_LEFT).bottom
        assertEquals(18, shown.getValue(ReferenceCorner.TOP_LEFT).height())

        for (corner in ReferenceCorner.entries) {
            model.setReferenceCorner(corner)
            model.setReferenceHidden(true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

            val strip = controls.referenceThumbnail.box().apply { intersect(controls.previewContainer.box()) }
            val handle = controls.referenceHandle.box()
            // Still a touch target tall and over the strip, with its extra height toward the middle of the preview.
            assertEquals("$corner", 48, handle.height())
            assertTrue("$corner: $handle over $strip", handle.contains(strip))
            assertTrue("$corner: $handle in $top..$bottom", handle.top >= top && handle.bottom <= bottom)
            model.setReferenceHidden(false)
        }
    }

    // ---- The scene photo just taken, held --------------------------------------------------------

    /** The scene photo of [width] × [height] just taken, held while the phone looks for a person in it (#296). */
    private fun Camera.holding(width: Int, height: Int) {
        (modules.camera as FakeCameraController).frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        modules.persons.gate = CompletableDeferred()
        val generation = activity.viewModel<GenerationViewModel>()
        generation.beginScene()
        idle()
        generation.takeScene()
        idle()
    }

    /** Where [this] draws its picture on the screen, in dp. */
    private fun ImageView.pictureBox(): Rect {
        val at = IntArray(2)
        getLocationInWindow(at)
        val drawn = RectF(drawable.bounds).also(imageMatrix::mapRect)
        val density = resources.displayMetrics.density
        fun dp(px: Float) = (px / density).roundToInt()
        return Rect(
            dp(at[0] + paddingLeft + drawn.left),
            dp(at[1] + paddingTop + drawn.top),
            dp(at[0] + paddingLeft + drawn.right),
            dp(at[1] + paddingTop + drawn.bottom),
        )
    }

    @Test
    fun sceneHeld_thePhotoLiesExactlyOverThePreview_underTheFlowsBar() {
        val screen = camera()
        screen.holding(300, 400)

        // Where the preview showed it, so the image seems to stop (#296).
        val photo = screen.controls.sceneStill.pictureBox()
        val preview = screen.controls.previewContainer.box()
        assertEquals(360, photo.width())
        assertEquals(480, photo.height())
        assertTrue("$photo over $preview", abs(photo.top - preview.top) <= 1 && abs(photo.left - preview.left) <= 1)
        // The flow's bar shows over it, at the bar's bottom edge (#210).
        assertTrue(screen.controls.sceneProgress.isShown)
        assertTrue(screen.controls.sceneProgress.z > screen.controls.sceneStill.z)
    }

    /** The middle of [this] on the screen, in dp. */
    private fun View.middle(): Float {
        val at = IntArray(2).also(::getLocationInWindow)
        return (at[1] + height / 2f) / resources.displayMetrics.density
    }

    // The scene photo step (#231, #210): its bar the camera's own, in height and sizes, with the image where it is,
    // while framing and on the review, and after it; Back and the title as far from the edge
    // as a header has them; the menu and the ratio at its end at every font scale; the panel that says what to frame
    // is see-through, below a 4:3 image when its hint is on one line and over its last dp when the hint takes two
    // (every situation's hint), and over a 16:9 one.
    @Test
    fun scenePhoto_theBar_isTheCamerasOwn_inHeightAndSizes_whileFramingAndReviewing_andAfter() {
        val screen = camera()
        val bar = screen.controls.topBar.box()
        val image = screen.controls.previewContainer.box().top
        val generation = screen.activity.viewModel<GenerationViewModel>()

        val menu = screen.controls.cameraMenuButton as MaterialButton
        val density = menu.resources.displayMetrics.density
        val icon = menu.iconSize / density
        val ratio = screen.controls.aspectButton.textSize / menu.resources.displayMetrics.scaledDensity

        generation.beginScene()
        idle()
        // The camera's own sizes: the menu's icon, and the ratio's label.
        assertEquals("framing", icon, menu.iconSize / density, 0.5f)
        assertEquals(
            "framing",
            ratio,
            screen.controls.aspectButton.textSize / menu.resources.displayMetrics.scaledDensity,
            0.5f,
        )
        // The camera's own height, so the image stays where it is; the next step's header is taller (#210).
        assertEquals("framing", bar, screen.controls.topBar.box())
        assertEquals("framing", image, screen.controls.previewContainer.box().top)
        // The flow's bar starts where the image does, to the pixel: no strip of the image above it.
        assertEquals("framing", screen.controls.previewContainer.topPx(), screen.controls.sceneProgress.topPx())

        (screen.modules.camera as FakeCameraController).frame = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        screen.modules.persons.gate = CompletableDeferred()
        generation.takeScene()
        idle()
        assertEquals("the photo held", bar, screen.controls.topBar.box())
        assertEquals("the photo held", screen.controls.previewContainer.topPx(), screen.controls.sceneProgress.topPx())

        // Back to the camera: its own bar, its icons and the ratio at their sizes, and the image where it was.
        generation.cancel()
        idle()
        assertEquals("after", bar, screen.controls.topBar.box())
        assertEquals("after", image, screen.controls.previewContainer.box().top)
        assertEquals("after", icon, menu.iconSize / density, 0.5f)
        assertEquals(
            "after",
            ratio,
            screen.controls.aspectButton.textSize / menu.resources.displayMetrics.scaledDensity,
            0.5f,
        )
    }

    /** Where [this] starts on the screen, its translation included, in px. */
    private fun View.topPx(): Int = IntArray(2).also(::getLocationInWindow)[1]

    @Test
    fun scenePhoto_back_startsWhereAScreensHeaderHasIt_withTheTitleAfterIt_onTheCamerasLine() {
        val screen = camera()
        val generation = screen.activity.viewModel<GenerationViewModel>()
        generation.beginScene()
        idle()
        val back = screen.controls.sceneBackButton as MaterialButton
        val density = back.resources.displayMetrics.density
        // Back 8 dp from the edge, where every screen's header has it (FlowProgressViewTest), and the title 4 dp after
        // it; Back one of the camera bar's buttons, its icon the menu's size, drawing its pill between its insets on the
        // camera's line, above the 4 dp that lie over the image.
        val backMiddle =
            (back.box().top + back.insetTop / density + back.box().bottom - back.insetBottom / density) / 2f
        assertEquals(24f, backMiddle, 1f)
        val menu = screen.controls.cameraMenuButton as MaterialButton
        assertEquals(menu.iconSize, back.iconSize)
        assertEquals(8, back.box().left)
        assertEquals("framing", backMiddle, screen.controls.sceneTitle.middle(), 1f)
        assertEquals(back.box().right + 4, screen.controls.sceneTitle.box().left)
        // The camera bar's 14 sp, smaller only where its words would not fit before the menu and the ratio.
        val title = screen.controls.sceneTitle
        assertEquals(14f, title.autoSizeMaxTextSize / back.resources.displayMetrics.scaledDensity, 0.5f)
        assertEquals(14f, title.textSize / back.resources.displayMetrics.scaledDensity, 0.5f)

        (screen.modules.camera as FakeCameraController).frame = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        screen.modules.persons.gate = CompletableDeferred()
        generation.takeScene()
        idle()
        assertEquals("the photo held", backMiddle, screen.controls.sceneTitle.middle(), 1f)
    }

    @Test
    fun scenePhoto_theBarFits_backTheTitleTheMenuAndTheRatio_theLastTwoAtItsEnd_alsoAtLargeFonts() {
        for (scale in listOf(1f) + LARGE_FONTS) {
            val screen = camera(fontScale = scale)
            screen.activity.viewModel<GenerationViewModel>().beginScene()
            idle()
            val bar = screen.controls.topBar.box()
            val parts = listOf(
                screen.controls.sceneBackButton,
                screen.controls.sceneTitle,
                screen.controls.cameraMenuButton,
                screen.controls.aspectButton,
            )
            // The menu and the ratio at the bar's end, inside its 20 dp, where the next steps' action ends.
            assertEquals("$scale", bar.right - 20, screen.controls.aspectButton.box().right)
            for (part in parts) {
                assertTrue(
                    "$scale: ${part.box()} shown in $bar",
                    part.isShown && bar.contains(part.box()),
                )
            }
            for ((left, right) in parts.zipWithNext()) {
                assertTrue("$scale: ${left.box()} before ${right.box()}", left.box().right <= right.box().left)
            }
            val title = screen.controls.sceneTitle
            assertEquals("$scale: the title on one line", 1, title.lineCount)
            assertEquals("$scale: the title whole", 0, title.layout.getEllipsisCount(0))
        }
    }

    @Test
    fun scenePhoto_thePanel_isSeeThrough_belowA43Image_orOverItsLastDpWhenItsHintTakesTwoLines() {
        val screen = camera()
        val generation = screen.activity.viewModel<GenerationViewModel>()
        val lines = mutableSetOf<Int>()
        for (situation in Situation.entries) {
            generation.chooseSituation(situation)
            generation.beginScene()
            idle()

            val panel = screen.controls.sceneGuide
            val image = screen.controls.previewContainer.box()
            val over = image.bottom - panel.box().top
            val hint = screen.controls.sceneGuideText.lineCount
            lines += hint
            assertEquals(R.drawable.bg_glass_panel, shadowOf(panel.background).createdFromResId)
            // A hint on one line leaves the image whole; a longer one, in English, covers its last dp.
            assertTrue(
                "$situation: ${panel.box()} over $image",
                over <= if (hint == 1) 0 else SCENE_PANEL_OVER_IMAGE_DP,
            )
            assertTrue("$situation: above the shutter", panel.box().bottom <= screen.controls.shutterButton.box().top)
        }
        assertEquals("hints of one line and of two", setOf(1, 2), lines)
    }

    @Test
    fun scenePhoto_thePanel_isOverA169Image_aboveTheShutter() {
        val screen = camera()
        screen.ratio(AspectRatio.R16_9)
        screen.activity.viewModel<GenerationViewModel>().beginScene()
        idle()

        val panel = screen.controls.sceneGuide.box()
        assertTrue("$panel inside the image", screen.controls.previewContainer.box().contains(panel))
        assertTrue("$panel above the shutter", panel.bottom <= screen.controls.shutterButton.box().top)
    }

    @Test
    fun sceneHeld_sideways_showsThePhotoAsFramed_andKeepsItUprightForThePoses() {
        val screen = camera()
        screen.modules.turns.set(90)
        screen.holding(300, 400)
        val generation = screen.activity.viewModel<GenerationViewModel>()
        idleUntil { generation.uiState.value.phase == GenerationUiState.Phase.CHECKING }
        idle()

        val shown = screen.controls.sceneStill.drawable
        assertEquals(300 to 400, shown.intrinsicWidth to shown.intrinsicHeight)
        val scene = checkNotNull(generation.uiState.value.scene)
        assertEquals(400 to 300, scene.width to scene.height)
    }

    @Test
    fun sceneWithoutAPerson_thePanelThatSaysSo_isWholeAboveTheShutter() {
        val screen = camera()
        screen.modules.persons.found = false
        (screen.modules.camera as FakeCameraController).frame = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val generation = screen.activity.viewModel<GenerationViewModel>()
        generation.beginScene()
        idle()
        generation.takeScene()
        idle()

        // Two lines of text at most at this width, in the panel where the framing hint stood (#296).
        val panel = screen.controls.sceneGuide.box()
        assertTrue(screen.controls.sceneGuide.isShown)
        assertEquals(
            screen.activity.getString(R.string.scene_no_person_title),
            screen.controls.sceneGuideText.text.toString(),
        )
        assertTrue("$panel above the shutter", panel.bottom <= screen.controls.shutterButton.box().top)
        assertTrue("$panel on the screen", panel.left >= 0 && panel.right <= 360)
    }

    // ---- Ratios -----------------------------------------------------------------------------

    @Test
    fun eachRatio_thePreviewBelowTheTopBar_andTheShuttersRowInOnePlace() {
        val screen = camera()
        val shutters = mutableListOf<Rect>()
        for ((aspect, height) in listOf(AspectRatio.R4_3 to 480, AspectRatio.R16_9 to 640, AspectRatio.R1_1 to 480)) {
            screen.ratio(aspect)
            val preview = screen.controls.previewContainer.box()
            // The bar draws 48 dp; its 52 dp touch height reaches 4 dp over the image, which starts below that.
            assertEquals("$aspect", screen.controls.topBar.box().bottom - TOP_BAR_OVERHANG_DP.toInt(), preview.top)
            assertEquals("$aspect", 48, preview.top)
            assertEquals("$aspect", 360, preview.width())
            assertEquals("$aspect", height, preview.height())
            shutters += screen.controls.shutterButton.box()
        }
        assertEquals(1, shutters.distinct().size)
        assertEquals(72, shutters.first().height())
        // The row ends 18 dp above the screen's bottom.
        assertEquals(705 - 18, screen.controls.captureControls.box().bottom)
    }

    @Test
    fun sixteenByNine_everyControlBelowEndsAboveTheImagesBottom() {
        val screen = camera()
        screen.ratio(AspectRatio.R16_9)

        val imageBottom = screen.controls.previewContainer.box().bottom
        val row = screen.controls.captureControls
        for (view in listOf(
            screen.controls.guideButton,
            screen.controls.shutterButton,
            screen.controls.galleryButton,
        )) {
            assertTrue(
                "${view.resources.getResourceEntryName(view.id)} ends above $imageBottom",
                view.box().bottom < imageBottom,
            )
        }
        assertTrue(row.box().top > screen.controls.previewContainer.box().top)
    }

    @Test
    fun theCameraSwitch_isInTheTopBar_beforeShootTogether() {
        val screen = camera()

        val bar = screen.controls.topBar.box()
        val switch = screen.controls.switchCameraButton.box()
        val together = screen.controls.shootTogetherButton.box()

        assertTrue("$switch in $bar", screen.controls.switchCameraButton.isShown && bar.contains(switch))
        assertTrue("$switch before $together", switch.right <= together.left)
    }

    @Test
    fun theGallery_isAsFarRightOfTheShutterAsGuideIsLeftOfIt_onTheShuttersCenterLine() {
        val screen = camera()

        val guide = screen.controls.guideButton.box()
        val shutter = screen.controls.shutterButton.box()
        val gallery = screen.controls.galleryButton.box()

        assertEquals(
            (shutter.centerX() - guide.centerX()).toFloat(),
            (gallery.centerX() - shutter.centerX()).toFloat(),
            1f,
        )
        assertEquals(shutter.centerY(), gallery.centerY())
    }

    @Test
    fun theCameraMenu_isUnderTheTopBar_eachItemTakingTapsOn48Dp_withItsLabelWhole_atLargeFonts() {
        for (scale in LARGE_FONTS) assertMenuFits(camera(fontScale = scale), "font $scale")
    }

    private fun assertMenuFits(screen: Camera, font: String) {
        screen.controls.cameraMenuButton.performClick()
        idle()

        val menu = screen.controls.cameraMenu
        val bar = screen.controls.topBar.box()
        assertTrue(font, menu.isShown)
        assertTrue("$font: ${menu.box()} under $bar", menu.box().top >= bar.bottom - TOP_BAR_OVERHANG_DP.toInt())
        val items = listOf(
            screen.controls.menuFlash,
            screen.controls.menuTimer,
            screen.controls.menuGrid,
            screen.controls.menuLevel,
            screen.controls.menuSettings,
        )
        for (item in items) {
            val box = item.box()
            val name = item.resources.getResourceEntryName(item.id)
            assertTrue("$font: $name $box", box.width() >= 48 && box.height() >= 48)
            assertTrue("$font: $name in the menu", menu.box().contains(box))
            val label = (item as ViewGroup).getChildAt(1) as TextView
            assertEquals("$font: $name on one line", 1, label.lineCount)
            assertTrue(
                "$font: $name's label whole",
                label.paint.measureText(label.text.toString()) <= label.width + 0.5f,
            )
        }
        assertTrue(font, items.zipWithNext().all { (a, b) -> a.box().right <= b.box().left })
    }

    @Test
    fun theZoomRow_sitsAtTheBottomOfTheImage_inEachRatio() {
        val screen = camera()
        val margin = ZOOM_ROW_MARGIN_DP.toInt()
        screen.ratio(AspectRatio.R4_3)
        assertEquals(528 - margin, screen.controls.zoomRow.box().bottom)

        // 1:1 shows inside the 4:3 area: the row is at the bottom of the square, not of the area.
        screen.ratio(AspectRatio.R1_1)
        val area = screen.controls.previewContainer.box()
        val square = fitViewfinderFrame(area.width().toFloat(), area.height().toFloat(), AspectRatio.R1_1.frameAspect)
        assertEquals(area.top + square.bottom.roundToInt() - margin, screen.controls.zoomRow.box().bottom)

        // Under 16:9 the controls cover the image's bottom: the row stays above them.
        screen.ratio(AspectRatio.R16_9)
        assertTrue(screen.controls.zoomRow.box().bottom <= screen.controls.bottomPanel.box().top - margin)
    }

    @Test
    fun aGuidesPanel_fitsInTheSpaceBelowAFourByThreeImage_underTheZoomRow() {
        val screen = camera()
        screen.guideWithTip()

        val image = screen.controls.previewContainer.box()
        val panel = screen.controls.guideControls.box()
        assertTrue(panel.top >= image.bottom)
        assertTrue(panel.bottom <= screen.controls.bottomPanel.box().top)
        assertTrue(screen.controls.zoomRow.box().bottom <= image.bottom)
        assertTrue(screen.controls.tipButtonFrame.isShown)
    }

    /** What [view] draws, at its size. */
    private fun drawn(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    @Test
    fun aHeldFrame_movesWithTheScene_toWhereTheNewRatioShowsIt() {
        val screen = camera()
        screen.streaming()
        val area = screen.controls.previewContainer
        val still = screen.controls.aspectStill
        // The preview's last frame, red, as PreviewHold takes it.
        screen.fragment.previewFrame = {
            Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        }

        // 4:3 to 16:9: the frame grows to 640 dp, and the scene a third larger with it, from edge to edge.
        screen.controls.aspect916Option.performClick()
        screen.ratio(AspectRatio.R16_9)
        assertEquals(640, area.box().height())
        drawn(still).let {
            assertEquals(Color.RED, it.getPixel(0, 0))
            assertEquals(Color.RED, it.getPixel(it.width - 1, it.height - 1))
        }
        screen.rebind(CameraStatus.STARTING)
        screen.rebind(CameraStatus.READY)

        // 16:9 to 1:1: three quarters as large, in the middle of the 4:3 area, with the ground at its sides.
        screen.controls.aspect11Option.performClick()
        screen.ratio(AspectRatio.R1_1)
        assertEquals(480, area.box().height())
        drawn(still).let {
            val density = still.resources.displayMetrics.density
            assertEquals(Color.RED, it.getPixel(it.width / 2, it.height / 2))
            assertEquals(still.context.getColor(R.color.pix_ground), it.getPixel((20 * density).toInt(), it.height / 2))
        }
    }

    /** A preview that streams and has a last frame to hold, which PreviewView has not without a surface. */
    @Suppress("UNCHECKED_CAST")
    private fun Camera.streaming() {
        val area = controls.previewContainer
        fragment.previewFrame = { Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888) }
        val field = PreviewView::class.java.getDeclaredField("mPreviewStreamStateLiveData")
        field.isAccessible = true
        (field.get(controls.previewView) as MutableLiveData<PreviewView.StreamState>).value =
            PreviewView.StreamState.STREAMING
        idle()
    }

    /** The camera's status in a rebind: STARTING, then READY, which lets a held frame go while the preview streams. */
    private fun Camera.rebind(status: CameraStatus) {
        (modules.camera as FakeCameraController).setStatus(status)
        idle()
    }

    @Test
    fun aRatioChange_takesTheNewRatiosArea_whileTheHeldFrameWaitsForItsStream() {
        val screen = camera()
        screen.ratio(AspectRatio.R16_9)
        screen.streaming()

        // 16:9 to 1:1: the 480 dp 4:3 area as the camera rebinds, the held frame in it until the new ratio streams.
        screen.controls.aspect11Option.performClick()
        screen.ratio(AspectRatio.R1_1)
        screen.rebind(CameraStatus.STARTING)
        assertTrue(screen.controls.aspectStill.isShown)
        assertEquals(480, screen.controls.previewContainer.box().height())

        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isShown)
        assertEquals(480, screen.controls.previewContainer.box().height())
    }

    @Test
    fun aLensSwitchIn1To1_dimsTheHeldFramesOutsideOnce() {
        val screen = camera()
        screen.ratio(AspectRatio.R1_1)
        screen.streaming()
        assertTrue(screen.controls.compositionOverlay.dimOutside)

        // The held frame carries the dim, and the grid stays over it without its own.
        screen.rebind(CameraStatus.STARTING)
        assertTrue(screen.controls.aspectStill.isShown)
        assertTrue(screen.controls.compositionOverlay.isShown)
        assertFalse(screen.controls.compositionOverlay.dimOutside)

        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isShown)
        assertTrue(screen.controls.compositionOverlay.dimOutside)
    }

    @Test
    fun theRatios_openUnderTheTopBar_inTheirShapes_eachTakingTapsOnAtLeast48dp() {
        val screen = camera()
        screen.controls.aspectButton.performClick()
        idle()

        val panel = screen.controls.aspectPanel.box()
        assertTrue(panel.top >= screen.controls.topBar.box().bottom)
        val options =
            listOf(screen.controls.aspect916Option, screen.controls.aspect34Option, screen.controls.aspect11Option)
        for (option in options) {
            val box = option.box()
            assertTrue(box.width() >= 48 && box.height() >= 48)
            assertTrue(panel.contains(box))
        }
        // Left to right, the tallest first: 9:16, 3:4, 1:1, each drawn in its own shape around its label.
        assertTrue(options.zipWithNext().all { (a, b) -> a.box().left < b.box().left })
        val shapes = options.map { (it as ViewGroup).getChildAt(0) as TextView }
        assertEquals(listOf("9:16", "3:4", "1:1"), shapes.map { it.text.toString() })
        assertEquals(listOf(22 to 29, 22 to 25, 24 to 24), shapes.map { it.box().width() to it.box().height() })
        for (shape in shapes) assertTrue(shape.paint.measureText(shape.text.toString()) <= shape.width)
    }

    // ---- The top bar ------------------------------------------------------------------------

    private fun Camera.assertTopBarFits(state: String) {
        val bar = controls.topBar.box()
        val shown = listOf(
            controls.cameraMenuButton,
            controls.flashIndicator,
            controls.timerIndicator,
            controls.aspectButton,
            controls.switchCameraButton,
            controls.shootTogetherButton,
            controls.endSessionButton,
        ).filter { it.isShown }.map { it.resources.getResourceEntryName(it.id) to it.box() }
        assertTrue(state, shown.size >= 4)
        for ((name, box) in shown) assertTrue("$state: $name inside the bar", bar.contains(box))
        for (i in shown.indices) {
            for (j in i + 1 until shown.size) {
                assertFalse(
                    "$state: ${shown[i].first} and ${shown[j].first}",
                    Rect.intersects(shown[i].second, shown[j].second),
                )
            }
        }
    }

    @Test
    fun theTopBar_fitsWithoutOverlap_inEachState() {
        val screen = camera()
        screen.assertTopBarFits("no guide")
        screen.guideWithTip()
        screen.assertTopBarFits("a guide with a tip")
        screen.session()
        screen.assertTopBarFits("a session")
        screen.flashAndTimerOn()
        screen.assertTopBarFits("the flash and the timer on, in a session")
    }

    // The camera switch draws its icon 2 dp larger than the bar's other icons, whose 16 dp its fine lines read small at.
    @Test
    fun theCameraSwitch_drawsItsIconALittleLargerThanTheBarsOthers() {
        val screen = camera()
        val density = screen.controls.root.resources.displayMetrics.density
        assertEquals(18f, (screen.controls.switchCameraButton as MaterialButton).iconSize / density, 0.5f)
        assertEquals(16f, (screen.controls.cameraMenuButton as MaterialButton).iconSize / density, 0.5f)
    }

    @Test
    fun theTopBar_fitsWithoutOverlap_atLargeFonts() {
        for (scale in LARGE_FONTS) {
            val screen = camera(fontScale = scale)
            screen.guideWithTip()
            screen.assertTopBarFits("a guide with a tip, font $scale")
            screen.session()
            screen.assertTopBarFits("a session, font $scale")
            screen.flashAndTimerOn()
            screen.assertTopBarFits("the flash and the timer on, in a session, font $scale")
        }
    }

    @Test
    fun theTopBarsButtons_drawOnePill_32DpTall_onOneLine_atEveryFont_50DpWideAtFont1() {
        for (scale in listOf(1f) + LARGE_FONTS) {
            val screen = camera(fontScale = scale)
            val buttons = listOf(
                screen.controls.cameraMenuButton,
                screen.controls.aspectButton,
                screen.controls.switchCameraButton,
            )
            val pills = buttons.map { it.resources.getResourceEntryName(it.id) to it.pill() }
            for ((name, pill) in pills) assertEquals("font $scale: $name's pill $pill", 32, pill.height())
            assertEquals("font $scale: $pills", 1, pills.map { it.second.top }.distinct().size)
            if (scale == 1f) {
                for ((name, pill) in pills) assertEquals("$name's pill $pill", 50, pill.width())
            }
        }
    }

    /** What a top bar button draws under a touch, or while its panel is open: its background inside the insets, in dp. */
    private fun MaterialButton.pill(): Rect {
        val box = box()
        val density = resources.displayMetrics.density
        return Rect(
            box.left,
            box.top + (insetTop / density).roundToInt(),
            box.right,
            box.bottom - (insetBottom / density).roundToInt(),
        )
    }

    /** The flash on and the timer at 10 s through the camera menu (FR-1.17), which then closes. */
    private fun Camera.flashAndTimerOn() {
        controls.cameraMenuButton.performClick()
        idle()
        repeat(2) {
            controls.menuFlash.performClick()
            controls.menuTimer.performClick()
            idle()
        }
        controls.cameraMenuButton.performClick()
        idle()
        assertTrue(controls.flashIndicator.isShown && controls.timerIndicator.isShown)
    }

    // ---- The zoom readout -------------------------------------------------------------------

    /** The readout takes a tap across the zoom row's height and at least 48 dp wide; its pill stays 28 dp tall. */
    private fun assertReadoutTapArea(readout: View, row: View) {
        val box = readout.box()
        assertEquals(row.box().height(), box.height())
        assertTrue("${box.width()} dp wide", box.width() >= 48)
        val inset = Rect().also { readout.background.getPadding(it) }
        val density = readout.resources.displayMetrics.density
        assertEquals(28, ((readout.height - inset.top - inset.bottom) / density).roundToInt())
    }

    @Test
    fun theZoomReadout_takesATapAcrossTheRowsHeight_aboveItsPill() {
        val screen = camera()
        assertReadoutTapArea(screen.controls.zoomReadout, screen.controls.zoomRow)

        // 4 dp below the readout's top: above the pill, which starts 10 dp down.
        val readout = IntArray(2).also { screen.controls.zoomReadout.getLocationInWindow(it) }
        val root = IntArray(2).also { screen.controls.root.getLocationInWindow(it) }
        val density = screen.controls.root.resources.displayMetrics.density
        val x = readout[0] - root[0] + screen.controls.zoomReadout.width / 2f
        val y = readout[1] - root[1] + 4 * density
        val now = SystemClock.uptimeMillis()
        screen.controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        screen.controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()

        assertEquals(listOf(2f), (screen.modules.camera as FakeCameraController).requests)
    }

    // ---- Touch targets (NFR-16) -------------------------------------------------------------

    /** Every control shown in [root] takes taps on at least 48 x 48 dp, whatever it draws. */
    private fun assertEveryControlAtLeast48(root: View, state: String) {
        val small = mutableListOf<String>()
        var controls = 0
        fun walk(view: View) {
            if (!view.isShown) return
            if (view.isClickable && view.id != View.NO_ID) {
                controls++
                val box = view.box()
                if (box.width() < 48 || box.height() < 48) {
                    small += "${view.resources.getResourceEntryName(view.id)} ${box.width()} x ${box.height()}"
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        assertTrue("$state: $controls controls", controls >= 5)
        assertTrue("$state: $small", small.isEmpty())
    }

    private fun Camera.assertEveryControlAtLeast48(font: String) {
        assertEveryControlAtLeast48(controls.root, "no guide, $font")
        guideWithTip()
        session()
        assertEveryControlAtLeast48(controls.root, "a guide with a tip and a session, $font")
        // The flash's and the timer's icons beside the menu's button are 16 dp: no control of their own (#318).
        flashAndTimerOn()
        assertEveryControlAtLeast48(controls.root, "the flash and the timer on, $font")
    }

    @Test
    fun everyControl_takesTapsOnAtLeast48dp() = camera().assertEveryControlAtLeast48("font scale 1")

    @Test
    fun everyControl_takesTapsOnAtLeast48dp_atLargeFonts() {
        for (scale in LARGE_FONTS) camera(fontScale = scale).assertEveryControlAtLeast48("font scale $scale")
    }

    // ---- Subject view -----------------------------------------------------------------------

    /** The container of the last [subjectView]. */
    private var subjectModules: TestAppContainer? = null

    /** Subject view connected to a photographer framing in 16:9, with their video streaming. */
    private fun subjectView(fontScale: Float = 1f): FragmentSubjectBinding {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        val modules = TestAppContainer().install()
        subjectModules = modules
        val activity = activity()
        val fragment = SubjectFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f), -6..6, 0.5f, true)),
        )
        modules.session.deliver(
            SessionMessage.CameraStateUpdate(
                zoom = 1f,
                by = Role.PHOTOGRAPHER,
                final = true,
                aspect = AspectRatio.R16_9,
            ),
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        return FragmentSubjectBinding.bind(fragment.requireView())
    }

    @Test
    fun subjectView_placesTheVideoLikeTheCamera_withTheZoomRowAboveTheCaption() {
        val binding = subjectView()

        val video = binding.videoContainer.box()
        assertEquals(binding.topBar.box().bottom - TOP_BAR_OVERHANG_DP.toInt(), video.top)
        assertEquals(640, video.height())
        assertTrue(binding.zoomRow.box().bottom <= binding.bottomPanel.box().top)
        assertTrue(binding.liveDot.isShown)
        assertReadoutTapArea(binding.zoomReadout, binding.zoomRow)

        binding.editGuideButton.visibility = View.VISIBLE
        // The brightness bar beside a tap's focus box (FR-7.3), as wide as a touch target.
        binding.exposureBar.visibility = View.VISIBLE
        binding.galleryButton.visibility = View.VISIBLE
        idle()
        assertEveryControlAtLeast48(binding.root, "Subject view")
    }

    /**
     * Subject view shows the photographer's flash and timer beside its menu's button as Camera shows its own (#318):
     * the same places and sizes, with the flash and the timer on, and with the timer alone.
     */
    @Test
    fun subjectViewsFlashAndTimerIcons_areWhereCamerasAre() {
        val camera = camera()
        camera.flashAndTimerOn()
        val camerasFlash = camera.controls.flashIndicator.box()
        val camerasTimer = camera.controls.timerIndicator.box()
        // The flash off again, which leaves the timer alone beside the button.
        camera.controls.cameraMenuButton.performClick()
        idle()
        camera.controls.menuFlash.performClick()
        camera.controls.cameraMenuButton.performClick()
        idle()
        assertFalse(camera.controls.flashIndicator.isShown)
        val camerasTimerAlone = camera.controls.timerIndicator.box()

        val binding = subjectView()
        fun photographers(flash: FlashMode) {
            subjectModules!!.session.deliver(
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, flash = flash, timer = 10),
            )
            idle()
        }
        photographers(FlashMode.ON)
        assertTrue(binding.flashIndicator.isShown && binding.timerIndicator.isShown)
        assertEquals(camerasFlash, binding.flashIndicator.box())
        assertEquals(camerasTimer, binding.timerIndicator.box())
        // A tap on the icons opens the menu: the area that takes it is 48 dp, over both, inside the bar (NFR-16).
        val touch = binding.indicatorsTouch.box()
        assertTrue("$touch", touch.width() >= 48 && touch.height() >= 48)
        assertTrue("$touch over the icons", touch.contains(binding.flashIndicator.box()))
        assertTrue("$touch over the icons", touch.contains(binding.timerIndicator.box()))
        assertTrue("$touch in the bar", binding.topBar.box().contains(touch))
        assertEveryControlAtLeast48(binding.root, "Subject view with the flash and the timer on")

        photographers(FlashMode.OFF)
        assertFalse(binding.flashIndicator.isShown)
        assertEquals(camerasTimerAlone, binding.timerIndicator.box())
    }

    @Test
    fun subjectViewsFlashAndTimerIcons_stayInTheBar_clearOfLeave_atLargeFonts() {
        for (scale in LARGE_FONTS) {
            val binding = subjectView(fontScale = scale)
            subjectModules!!.session.deliver(
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, flash = FlashMode.ON, timer = 10),
            )
            idle()
            val bar = binding.topBar.box()
            val timer = binding.timerIndicator.box()
            assertTrue("font $scale: $timer in $bar", binding.timerIndicator.isShown && bar.contains(timer))
            assertTrue("font $scale: the flash in $bar", bar.contains(binding.flashIndicator.box()))
            assertTrue("font $scale: $timer before Leave", timer.right <= binding.leaveButton.box().left)
        }
    }

    /** The middle of the row's right third, in dp: the panel's 16 dp of padding and five sixths of the rest. */
    private val rightThird = 16 + (360 - 32) * 5f / 6f

    /**
     * Subject view's bottom row is Camera's (R&S 6.6, #314): Guide and the shutter where Camera's are, at their sizes,
     * and the thumbnail where Camera's gallery button is, in the middle of the right third. While the photographer's
     * step lasts, its button stands beside the thumbnail, which moves aside for it, clear of the shutter with each of
     * its labels.
     */
    private fun assertTheRowIsCameras(fontScale: Float) {
        val camera = camera(fontScale).controls
        val camerasGuide = camera.guideButtonIcon.box()
        val camerasShutter = camera.shutterButton.box()
        val camerasGallery = camera.galleryButton.box()
        val binding = subjectView(fontScale)
        val font = "font $fontScale"
        // Shown from the start, empty until the session's first photo (FR-6.14).
        assertTrue("the thumbnail shows before any photo", binding.galleryButton.isShown)
        // Guide where Camera's is, at its size, within a pixel's rounding (R&S 6.6).
        val guideIcon = binding.guideButtonIcon.box()
        assertTrue(
            "$guideIcon where Camera's is, $camerasGuide",
            abs(guideIcon.centerX() - camerasGuide.centerX()) <= 1,
        )
        assertEquals(camerasGuide.centerY(), guideIcon.centerY())
        assertEquals(camerasGuide.width(), guideIcon.width())
        // The shutter where Camera's is, at its size.
        val shutter = binding.shutterButton.box()
        assertTrue("$font: the shutter shows", binding.shutterButton.isShown)
        assertTrue(
            "$font: $shutter where Camera's is, $camerasShutter",
            abs(shutter.centerX() - camerasShutter.centerX()) <= 1,
        )
        assertEquals("$font: on Camera's line", camerasShutter.centerY(), shutter.centerY())
        assertEquals("$font: Camera's size", camerasShutter.width(), shutter.width())
        assertEquals("$font: round", shutter.width(), shutter.height())
        assertEquals("$font: on Guide's icon line", guideIcon.centerY(), shutter.centerY())
        // The thumbnail alone: as far from the right edge as Guide is from the left, on the icons' line.
        val alone = binding.galleryButton.box()
        assertEquals("$font: the thumbnail on Guide's icon line", guideIcon.centerY(), alone.centerY())
        assertTrue("$font: $alone mirrors $guideIcon", abs((360 - alone.centerX()) - guideIcon.centerX()) <= 1)
        assertTrue(
            "$font: $alone where Camera's gallery button is, $camerasGallery",
            abs(alone.centerX() - camerasGallery.centerX()) <= 1,
        )
        assertCaptionAboveTheShutter(binding, shutter, font)

        // As the screen shows a step: its button beside the thumbnail, which makes room.
        binding.pickPoseButton.visibility = View.VISIBLE
        binding.galleryButton.translationX =
            SubjectFragment.STEP_BESIDE_THUMBNAIL_DP * binding.root.resources.displayMetrics.density
        binding.pickPoseLabel.maxWidth = (STEP_NAME_WIDTH_DP * binding.root.resources.displayMetrics.density).toInt()
        val labels = listOf(
            R.string.pick_pose_title,
            R.string.generating_title,
            R.string.describe_title,
            R.string.pose_guide_title,
        )
        for (label in labels) {
            binding.pickPoseLabel.setText(label)
            idle()
            val thumbnail = binding.galleryButton.box()
            val icon = binding.pickPoseButton.getChildAt(0).box()
            val name = binding.pickPoseLabel.box()
            val text = "$font, ${binding.pickPoseLabel.text}"
            // A name of two lines makes the row taller, and the row's line moves up with all that is on it.
            val line = binding.guideButtonIcon.box().centerY()
            val shutterNow = binding.shutterButton.box()
            // Each 26 dp from the third's middle, the button on the shutter's side.
            assertTrue("$text: $icon left of the third's middle", abs(icon.centerX() - (rightThird - 26)) <= 1)
            assertTrue("$text: $thumbnail right of it", abs(thumbnail.centerX() - (rightThird + 26)) <= 1)
            assertEquals("$text: the step's icon on Guide's icon line", line, icon.centerY())
            assertEquals("$text: the thumbnail on it too", line, thumbnail.centerY())
            assertEquals("$text: and the shutter", line, shutterNow.centerY())
            assertTrue("$text: $icon clear of the thumbnail $thumbnail", icon.right <= thumbnail.left)
            assertTrue("$text: $thumbnail inside the screen", thumbnail.right <= 360 && thumbnail.bottom <= 705)
            // The name may be wider than its icon, never as far as the shutter, and it is whole on the screen.
            assertTrue("$text: the shutter $shutterNow clear of $icon", shutterNow.right <= icon.left)
            assertTrue("$text: the shutter $shutterNow clear of the name $name", shutterNow.right <= name.left)
            assertTrue("$text: the name $name inside the screen", name.right <= 360 && name.bottom <= 705)
        }
        binding.galleryButton.translationX = 0f
    }

    /**
     * The line that says whose camera this is (#271) is right above the shutter (#314): centered on it, between the
     * screen's sides, and whole above it. A name that fits stays whole; a long one, such as a Galaxy's own name, is
     * cut short so "'s camera" stays; where the name and "Live" do not fit one line, the name stands over "Live"; a
     * photo on its way takes the line.
     */
    private fun assertCaptionAboveTheShutter(binding: FragmentSubjectBinding, shutter: Rect, font: String) {
        val modules = checkNotNull(subjectModules)
        val context = binding.root.context
        val names = listOf("Dongje", "길동의 Galaxy S23 Ultra", "Test Account With A Very Long Name")
        val suffix = context.getString(R.string.subject_title, "")
        for (name in names) {
            for (receiving in listOf(false, true)) {
                modules.session.setState(SessionState.Connected("s1", PeerInfo(name), Role.SUBJECT))
                idle()
                if (receiving) {
                    // As the screen shows a photo on its way: the spinner and its words alone.
                    binding.cameraCaption.orientation = LinearLayout.HORIZONTAL
                    binding.cameraCaptionName.visibility = View.GONE
                    binding.cameraCaptionSeparator.visibility = View.GONE
                    binding.cameraCaptionSpinner.visibility = View.VISIBLE
                    binding.cameraCaptionStatus.setText(R.string.subject_receiving_photo)
                    idle()
                }
                val what = "$font, $name, receiving $receiving"
                assertTrue("$what: the line shows", binding.cameraCaption.isShown)
                val caption = binding.cameraCaption.box()
                assertTrue(
                    "$what: $caption centered on the shutter $shutter",
                    abs(caption.centerX() - shutter.centerX()) <= 1,
                )
                assertTrue("$what: $caption above the shutter $shutter", caption.bottom <= shutter.top)
                assertTrue("$what: $caption close to it", shutter.top - caption.bottom <= 24)
                assertTrue("$what: $caption between the screen's sides", caption.left >= 12 && caption.right <= 348)
                val status = binding.cameraCaptionStatus
                assertEquals("$what: \"${status.text}\" whole", 0, status.layout.getEllipsisCount(0))
                assertTrue("$what: the status in its height", status.layout.height <= status.height + 1)
                if (receiving) {
                    assertEquals(context.getString(R.string.subject_receiving_photo), status.text.toString())
                    assertFalse("$what: the name gives way", binding.cameraCaptionTitle.isShown)
                } else {
                    val title = binding.cameraCaptionTitle
                    val text = title.text.toString()
                    if (name == "Dongje") assertEquals(context.getString(R.string.subject_title, name), text)
                    assertTrue("$what: \"$text\" keeps \"$suffix\"", text.endsWith(suffix))
                    assertEquals("$what: \"$text\" not cut at its end", 0, title.layout.getEllipsisCount(0))
                    assertEquals("$what: \"$text\" on one line", 1, title.lineCount)
                    // Within a pixel's rounding, as KoreanScreensTest's check of a cut text.
                    assertTrue("$what: the name in its height", title.layout.height <= title.height + 1)
                    assertTrue("$what: the name inside the line", title.box().right <= caption.right + 1)
                    assertEquals(context.getString(R.string.subject_live), status.text.toString())
                    // One line while both fit, as at this font with a short name; else the name over "Live".
                    val oneLine = binding.cameraCaption.orientation == LinearLayout.HORIZONTAL
                    assertEquals(
                        "$what: the dot between them on one line",
                        oneLine,
                        binding.cameraCaptionSeparator.isShown,
                    )
                    if (!oneLine) assertTrue("$what: the name over Live", title.box().bottom <= status.box().top + 1)
                    if (name == "Dongje" && font == "font 1.0") assertTrue("$what: on one line", oneLine)
                }
            }
        }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        idle()
    }

    @Test
    fun subjectView_theBottomRow_isCamerasRow_guideTheShutterAndTheThumbnail_andAStepsButtonBesideIt() =
        assertTheRowIsCameras(fontScale = 1f)

    @Test
    fun subjectView_theBottomRow_isCamerasRow_atLargeFonts() {
        for (scale in LARGE_FONTS) assertTheRowIsCameras(fontScale = scale)
    }

    /**
     * The photo viewer, shown: over the whole screen, Back at the top left and Share and Delete at the top right, their
     * icons as far from the edges as Back's, each taking taps on 48 dp, and the photo from under the header to 12 dp
     * over the strip of thumbnails, 48 dp tall and 24 dp over the bottom, in place while it is not shown yet.
     */
    private fun assertViewerCoversTheScreen(holder: FrameLayout) {
        val viewer = ViewPhotoViewerBinding.inflate(LayoutInflater.from(holder.context), holder, true)
        viewer.root.visibility = View.VISIBLE
        idle()

        assertEquals(Rect(0, 0, 360, 705), viewer.root.box())
        val back = viewer.photoViewerBack.box()
        assertTrue("$back at the top left", back.left <= 8 && back.top <= 16)
        assertTrue("$back takes taps on 48 dp", back.width() >= 48 && back.height() >= 48)
        val pages = viewer.photoViewerPages.box()
        val strip = viewer.photoViewerStrip.box()
        val share = viewer.photoViewerShare.box()
        val delete = viewer.photoViewerDelete.box()
        assertTrue("$pages under the header", pages.top >= viewer.photoViewerHeader.box().bottom)
        // Share, then Delete at the header's end, on Back's line, as in a gallery app.
        assertEquals("$delete as far from the right as $back from the left", 360 - back.left, delete.right)
        assertTrue("$share before $delete, after $back", share.right <= delete.left && share.left >= back.right)
        for (button in listOf(share, delete)) {
            assertTrue("$button takes taps on 48 dp", button.width() >= 48 && button.height() >= 48)
            assertTrue("$button on Back's line, $back", abs(button.centerY() - back.centerY()) <= 1)
        }
        // The strip of thumbnails at the bottom, each taking taps on 48 dp; in place while there is one photo.
        assertEquals(View.INVISIBLE, viewer.photoViewerStrip.visibility)
        assertEquals(48, strip.height())
        assertEquals(strip.top - 12, pages.bottom)
        assertEquals(705 - 24, strip.bottom)
    }

    @Test
    fun subjectView_thePhotoViewer_coversTheWholeScreen_withBackAtTheTopLeft_andThePhotoUnderIt() =
        assertViewerCoversTheScreen(subjectView().photoViewerHolder)

    @Test
    fun camera_thePhotoViewer_coversTheWholeScreen_withBackAtTheTopLeft_andThePhotoUnderIt() =
        assertViewerCoversTheScreen(camera().controls.photoViewerHolder)

    /** Subject view in a session, with a guide on the video and the photographer's tip of it, its banner closed. */
    private fun subjectWithTip(fontScale: Float = 1f): FragmentSubjectBinding {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        val modules = TestAppContainer().install()
        val activity = activity()
        val fragment = SubjectFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f), -6..6, 0.5f, true)),
        )
        // The widest readout the row shows.
        modules.session.deliver(SessionMessage.CameraStateUpdate(zoom = 10f, by = Role.PHOTOGRAPHER, final = true))
        modules.tipSync.startAsReceiver(fragment.viewLifecycleOwner.lifecycleScope)
        modules.mirror.setGuide(
            ReferenceGuide(
                id = "g1",
                mask = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888),
                outline = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) },
                aspect = 0.5f,
                source = GuideSource.GENERATED,
                anchor = NormalizedRect(0.2f, 0.1f, 0.8f, 0.95f),
            ),
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        // The longest tip the server writes: every sentence it has, one on a line.
        modules.session.deliver(
            SessionMessage.GuideTip(
                "g1",
                "Move until the person fits the guide's outline, then take it.\n" +
                    "Hold the phone above your head, tilted down a little.\n" +
                    "Put the person on the right of the frame.\nLeave almost no room above the head.\n" +
                    "Hold the phone upright so the lines behind the person are level.",
            ),
        )
        idle()
        return FragmentSubjectBinding.bind(fragment.requireView())
    }

    private fun assertTheTipFitsOnSubjectView(binding: FragmentSubjectBinding, font: String) {
        // The row: the tip at its start, clear of the readout; the toggle at its end.
        val row = binding.zoomRow.box()
        val tip = binding.tipButtonFrame.box()
        val readout = binding.zoomReadout.box()
        assertTrue("$font: the tip button shows", binding.tipButtonFrame.isShown)
        // Inside the row's 12 dp of padding.
        assertEquals("$font: the tip button at the row's start", row.left + 12, tip.left)
        assertTrue("$font: $tip clear of the readout $readout", tip.right <= readout.left)
        assertTrue("$font: the readout clear of the toggle", readout.right <= binding.editGuideButton.box().left)
        assertTrue("$font: in the row", tip.top >= row.top && tip.bottom <= row.bottom)
        assertEveryControlAtLeast48(binding.root, "Subject view with a tip, $font")

        // Open, the tip is whole on the screen above the caption, and the row moves above it.
        binding.tipButton.performClick()
        idle()
        val banner = binding.tipBanner.box()
        assertTrue("$font: the banner shows", binding.tipBanner.isShown)
        // Every sentence shows: at least a line each, none cut off at the end.
        val words = binding.tipText
        assertTrue("$font: ${words.lineCount} lines", words.lineCount >= 5)
        assertEquals("$font: the last line is whole", 0, words.layout.getEllipsisCount(words.lineCount - 1))
        assertTrue("$font: the text is as tall as its lines", words.height >= words.layout.height)
        assertTrue("$font: $banner above the shutter's row", banner.bottom <= binding.bottomPanel.box().top)
        // The open tip takes the place of the line that names the camera (#314), which is back once it closes.
        assertFalse("$font: the camera's name gives way to the tip", binding.cameraCaption.isShown)
        assertTrue("$font: $banner under the top bar", banner.top >= binding.topBar.box().bottom)
        assertTrue("$font: the row above the banner", binding.zoomRow.box().bottom <= banner.top)
        // The guide's panel stays with the tip while both fit, as at the usual font, and gives way to it at a font too
        // large for that (FR-7.2).
        if (font == "font scale 1") assertTrue("$font: the panel stays", binding.guideControls.isShown)
        if (binding.guideControls.isShown) {
            assertTrue("$font: the panel under the banner", banner.bottom <= binding.guideControls.box().top)
        }
        assertEveryControlAtLeast48(binding.root, "Subject view with the tip open, $font")

        binding.tipButton.performClick()
        idle()
        assertTrue("$font: the panel is back once the tip closes", binding.guideControls.isShown)
    }

    @Test
    fun subjectView_theTipButtonIsClearOfTheReadout_andItsCardOpensAboveTheCaption() =
        assertTheTipFitsOnSubjectView(subjectWithTip(), "font scale 1")

    @Test
    fun subjectView_theTipButtonIsClearOfTheReadout_andItsCardOpensAboveTheCaption_atLargeFonts() {
        for (scale in LARGE_FONTS) assertTheTipFitsOnSubjectView(subjectWithTip(fontScale = scale), "font scale $scale")
    }

    // ---- Large fonts (#298) -----------------------------------------------------------------

    /**
     * Why the text of [view] does not fit, or null when it does, as KoreanScreensTest's check: ellipsized, its last
     * lines dropped, or taller than the view. A view that ellipsizes on purpose holds a name, which may be cut.
     */
    private fun cutOff(view: TextView): String? {
        val layout = view.layout ?: return null
        if (view.text.isEmpty() || view.width == 0 || view.height == 0) return null
        val height = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
        return when {
            view.ellipsize != null -> null
            (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 } -> "ellipsized"
            layout.getLineEnd(layout.lineCount - 1) < view.text.trimEnd().length -> "cut short"
            layout.height > height + 1 -> "taller than its view (${layout.height} > $height px)"
            else -> null
        }
    }

    /** Every shown text under [root] whole, as "name: why" for each that is not. */
    private fun cutTexts(root: View, state: String): List<String> {
        val cut = mutableListOf<String>()
        fun walk(view: View) {
            if (!view.isShown) return
            if (view is TextView) {
                cutOff(view)?.let {
                    val name = if (view.id == View.NO_ID) "?" else view.resources.getResourceEntryName(view.id)
                    cut += "$state, $name \"${view.text}\": $it"
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return cut
    }

    @Test
    fun everyText_isWhole_onBothScreens_atLargeFonts() {
        val cut = mutableListOf<String>()
        for (scale in LARGE_FONTS) {
            val screen = camera(fontScale = scale)
            cut += cutTexts(screen.controls.root, "Camera, font $scale")
            screen.guideWithTip()
            screen.session()
            cut += cutTexts(screen.controls.root, "Camera with a guide and a session, font $scale")
            // The camera menu open, then the timer's countdown (FR-1.17).
            screen.controls.cameraMenuButton.performClick()
            idle()
            cut += cutTexts(screen.controls.root, "Camera with the menu open, font $scale")
            screen.controls.menuTimer.performClick()
            screen.controls.cameraMenuButton.performClick()
            idle()
            screen.controls.shutterButton.performClick()
            idle()
            cut += cutTexts(screen.controls.root, "Camera counting down, font $scale")
            val binding = subjectView(fontScale = scale)
            cut += cutTexts(binding.root, "Subject view, font $scale")
            binding.pickPoseButton.visibility = View.VISIBLE
            idle()
            cut += cutTexts(binding.root, "Subject view with Pick a pose, font $scale")
            binding.cameraMenuButton.performClick()
            subjectModules!!.session.deliver(SessionMessage.Countdown(3))
            idle()
            cut += cutTexts(binding.root, "Subject view with the menu open and the countdown, font $scale")
        }
        assertEquals(emptyList<String>(), cut)
    }
}
