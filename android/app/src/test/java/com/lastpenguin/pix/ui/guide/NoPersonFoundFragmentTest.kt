// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.guide

import android.net.Uri
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * No person found (R&S 6.2, FR-2.4, #294): the top bar has Back alone, with no action and no picker, and no buttons at
 * the bottom. Partitions on where the reference was chosen, one test each: a photo from the camera's Add a pose guide
 * (the line that says to go back; Back opens that sheet again over the camera, with its gallery, and the guide on the
 * camera is the same one), the same from Subject view (its sheet, over Subject view), and a generated pose, outside a session (today's line;
 * Back is Pick a pose) and in one, where the camera is the screen before (Back is the camera, with no sheet). The
 * system's Back does what the top bar's does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoPersonFoundFragmentTest {
    private var scenario: FragmentScenario<NoPersonFoundFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    /**
     * The screen as the app reaches it: [prepare] makes the choice in the activity's ReferenceViewModel, and [path]
     * walks the back stack from the camera to this screen.
     */
    private fun launch(
        modules: TestAppContainer = TestAppContainer(),
        prepare: (FragmentActivity) -> Unit,
        path: (TestNavHostController) -> Unit = {},
    ): TestAppContainer {
        modules.install()
        val (launched, controller) = launchPrepared<NoPersonFoundFragment>(R.id.noPersonFoundFragment) { prepare(it) }
        scenario = launched
        nav = controller
        path(controller)
        idle()
        return modules
    }

    private fun photoPicked(activity: FragmentActivity, fromSubject: Boolean = false) {
        activity.viewModel<ReferenceViewModel>().apply {
            openFor(fromSubject)
            onPhotoPicked(Uri.parse("content://photos/1"))
        }
    }

    private fun <T> onScreen(block: (FragmentFailureBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentFailureBinding.bind(it.requireView())) }
        return result!!
    }

    private fun back() {
        onScreen { it.backButton.performClick() }
        idle()
    }

    private fun assertOnTheSheet(below: Int, fromSubject: Boolean) {
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)
        val arguments = nav.currentBackStackEntry?.arguments
        assertEquals(true, arguments?.getBoolean(AddGuideSheet.ARG_OPEN_GALLERY))
        assertEquals(fromSubject, arguments?.getBoolean(AddGuideSheet.ARG_FROM_SUBJECT))
        assertEquals(below, nav.previousBackStackEntry?.destination?.id)
    }

    @Test
    fun aPhotoFromTheCamerasSheet_saysToGoBack_withBackAlone_andBackOpensTheSheetWithItsGallery() {
        val onCamera = guide()
        val modules = launch(TestAppContainer().apply { guides.setGuide(onCamera) }, ::photoPicked)

        onScreen { binding ->
            assertEquals(string(R.string.no_person_title), binding.failureTitle.text.toString())
            assertEquals(string(R.string.no_person_photo_message), binding.failureMessage.text.toString())
            assertEquals(View.VISIBLE, binding.backButton.visibility)
            assertEquals(View.GONE, binding.topActionButton.visibility)
            assertEquals(View.GONE, binding.primaryButton.visibility)
            assertEquals(View.GONE, binding.secondaryButton.visibility)
        }
        back()

        assertOnTheSheet(below = R.id.cameraFragment, fromSubject = false)
        // This screen opens no picker itself: the sheet does.
        scenario!!.onFragment { assertNull(shadowOf(it.requireActivity()).nextStartedActivityForResult) }
        assertSame(onCamera, modules.guides.guide.value)
    }

    @Test
    fun aPhotoFromSubjectViewsSheet_backOpensThatSheetOverSubjectView() {
        launch(
            prepare = { photoPicked(it, fromSubject = true) },
            path = { controller ->
                controller.setCurrentDestination(R.id.subjectFragment)
                controller.navigate(R.id.action_subject_to_addGuide)
                controller.navigate(R.id.action_addGuide_to_referenceConfirm)
                controller.navigate(R.id.action_referenceConfirm_to_noPersonFound)
            },
        )

        back()

        assertOnTheSheet(below = R.id.subjectFragment, fromSubject = true)
    }

    @Test
    fun aGeneratedPose_keepsItsLine_andBackIsPickAPose() {
        launch(
            prepare = { it.viewModel<ReferenceViewModel>().onCandidatePicked(bitmap()) },
            path = { controller ->
                controller.setCurrentDestination(R.id.pickPoseFragment)
                controller.navigate(R.id.action_pickPose_to_referenceConfirm)
                controller.navigate(R.id.action_referenceConfirm_to_noPersonFound)
            },
        )

        onScreen { binding ->
            assertEquals(string(R.string.no_person_message), binding.failureMessage.text.toString())
            assertEquals(View.GONE, binding.topActionButton.visibility)
        }
        back()

        assertEquals(R.id.pickPoseFragment, nav.currentDestination?.id)
    }

    @Test
    fun aGeneratedPoseInASession_whosePickAPoseIsGone_backIsTheCamera_withoutTheSheet() {
        launch(prepare = { it.viewModel<ReferenceViewModel>().onCandidatePicked(bitmap()) })

        back()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun theSystemsBack_doesWhatBackDoes() {
        launch(prepare = ::photoPicked)

        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        assertOnTheSheet(below = R.id.cameraFragment, fromSubject = false)
    }
}
