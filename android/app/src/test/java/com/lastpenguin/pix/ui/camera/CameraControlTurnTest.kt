// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.app.Application
import android.graphics.drawable.RotateDrawable
import android.os.Looper
import android.text.Spanned
import android.view.View
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.OwnActivities
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * The Camera screen's icons turning with the phone (FR-1.15, #207, R&S 6.2, Design 2.6.2) on the Galaxy S22's app
 * area, 360 x 705 dp.
 * Partitions (equivalence): the phone held at 0, 90, 180, and 270 degrees. What turns: the top bar's icons (the camera
 * menu's and the camera switch's among them), the flash and the timer beside the menu's button, the ratio, the zoom
 * readout, and the timer's countdown as words, Shoot together's icon, the guide's icon buttons, the tip's, the
 * guide-edit toggle's, the camera menu's items and Guide with their labels, and the gallery thumbnail (FR-1.17). What
 * stays: Shoot together's label, the shutter, the preview and what is drawn over it, the reference thumbnail, and every
 * control's place and size. A turn as the screen opens, at once; a later one, all together and smoothly; with the
 * phone's animations off, at once. A new icon, the scene photo light's next one, keeps the turn.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class CameraControlTurnTest {
    @get:Rule
    val activities = OwnActivities()

    private lateinit var modules: TestAppContainer
    private lateinit var fragment: CameraFragment

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun play(ms: Long) {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        ShadowChoreographer.setPaused(true)
    }

    /** The Camera screen, with the phone held at [turn] as it opens; frames paused before it opens when [paused]. */
    private fun camera(turn: Int = 0, paused: Boolean = false): FragmentCameraBinding {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        modules = TestAppContainer(camera = FakeCameraController(hasFlash = true)).install()
        modules.turns.set(turn)
        val activity = activities.start(R.style.Theme_Pix)
        val nav = TestNavHostController(activity).apply { setGraph(R.navigation.nav_graph) }
        Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
        if (paused) ShadowChoreographer.setPaused(true)
        fragment = CameraFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idle()
        return FragmentCameraBinding.bind(fragment.requireView())
    }

    /** The controls that turn what they draw: the buttons' icons, the ratio, and the zoom readout. */
    private fun FragmentCameraBinding.turningButtons(): List<View> = listOf(
        zoomReadout,
        sceneBackButton,
        cameraMenuButton,
        aspectButton,
        switchCameraButton,
        shootTogetherButton,
        hideGuideButton,
        saveGuideButton,
        removeGuideButton,
        tipButton,
        editGuideButton,
        countdownNumber,
    )

    /** The icons beside the menu's button, and Guide and the menu's items, each turning with its label as one block. */
    private fun FragmentCameraBinding.turningViews(): List<View> = listOf(
        flashIndicator,
        timerIndicator,
        menuFlash,
        menuTimer,
        menuGrid,
        menuLevel,
        menuSettings,
        guideButton,
        galleryThumbnail,
    )

    /** Views that never turn as a whole: the buttons whose icon or label turns, the readout, the shutter, the preview. */
    private fun FragmentCameraBinding.stayingViews(): List<View> = listOf(
        shootTogetherButton,
        aspectButton,
        zoomReadout,
        shutterButton,
        switchCameraButton,
        galleryButton,
        previewContainer,
        guideOverlay,
        compositionOverlay,
        exposureBar,
        referenceThumbnail,
        topBar,
        bottomPanel,
    )

    /** Every control's place and size, which a turn never changes. */
    private fun FragmentCameraBinding.places(): List<List<Int>> =
        (turningButtons() + turningViews() + stayingViews()).map { listOf(it.left, it.top, it.right, it.bottom) }

    private fun TurningButton.drawnAt(): Float = (icon as RotateDrawable).level * 360f / 10_000f

    private fun FragmentCameraBinding.assertIconsAt(degrees: Float) {
        for (control in turningButtons()) {
            val name = resources().getResourceEntryName(control.id)
            assertEquals(name, degrees, (control as Turnable).turn, 0f)
            if (control is TurningButton && control.icon != null) {
                assertEquals(name, Math.floorMod(degrees.toInt(), 360).toFloat(), control.drawnAt(), 0.05f)
            }
        }
        for (view in turningViews()) {
            assertEquals(resources().getResourceEntryName(view.id), degrees, view.rotation, 0f)
        }
    }

    private fun FragmentCameraBinding.resources() = root.resources

    @Test
    fun heldEachWay_theIconsStandUpright_andTheTextsTheShutterAndThePlacesStay() {
        val screen = camera()
        val places = screen.places()
        val texts = listOf(screen.shootTogetherButton.text, screen.aspectButton.text, screen.zoomReadout.text)

        for ((turn, degrees) in listOf(90 to -90f, 180 to 180f, 270 to 90f, 0 to 0f)) {
            modules.turns.set(turn)
            play(Motion.TURN_MS * 2)

            screen.assertIconsAt(degrees)
            for (view in screen.stayingViews()) {
                assertEquals("$turn ${screen.resources().getResourceEntryName(view.id)}", 0f, view.rotation, 0f)
            }
            assertEquals("$turn", places, screen.places())
            assertEquals(
                "$turn",
                texts,
                listOf(screen.shootTogetherButton.text, screen.aspectButton.text, screen.zoomReadout.text),
            )
        }
    }

    @Test
    fun theRatioAndTheZoomReadout_turnAsWords_whileShootTogethersLabelStays() {
        val screen = camera()

        assertTrue(screen.aspectButton.turnsLabel)
        assertFalse(screen.shootTogetherButton.turnsLabel)
        assertEquals("Shoot together", screen.shootTogetherButton.text.toString())
        assertFalse(screen.shootTogetherButton.text is Spanned)
        for (text in listOf(screen.aspectButton.text, screen.zoomReadout.text)) {
            assertEquals(1, (text as Spanned).getSpans(0, text.length, TurnedLabelSpan::class.java).size)
        }
    }

    @Test
    fun aScreenOpenedSideways_hasItsIconsTurnedAtOnce() {
        val screen = camera(turn = 270, paused = true)

        screen.assertIconsAt(90f)
    }

    @Test
    fun aTurn_turnsTheIconsTogether_andSmoothly() {
        val screen = camera()
        ShadowChoreographer.setPaused(true)

        modules.turns.set(90)
        idle()
        checkNotNull(fragment.turningIcons?.turning).setCurrentFraction(0.5f)

        val halfway = screen.galleryThumbnail.rotation
        assertTrue("$halfway", halfway > -90f && halfway < 0f)
        for (control in screen.turningButtons()) assertEquals(halfway, (control as Turnable).turn, 0f)
        for (view in screen.turningViews()) assertEquals(halfway, view.rotation, 0f)
        play(Motion.TURN_MS)
        screen.assertIconsAt(-90f)
    }

    @Test
    fun withAnimationsOff_theIconsAreThereAtOnce() {
        val screen = camera()
        setAnimatorScale(0f)
        ShadowChoreographer.setPaused(true)

        modules.turns.set(90)
        idle()

        screen.assertIconsAt(-90f)
    }

    @Test
    fun theScenePhotosLight_fromTheMenu_keepsTheTurn() {
        val screen = camera(turn = 90)
        fragment.requireActivity().viewModel<GenerationViewModel>().beginScene()
        idle()

        screen.menuFlash.performClick()
        idle()

        // The light's indicator beside the menu's button comes in turned as well.
        assertTrue(screen.flashIndicator.isShown)
        screen.assertIconsAt(-90f)
    }
}
