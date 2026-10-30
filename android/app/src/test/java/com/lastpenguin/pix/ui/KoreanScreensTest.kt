// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han, Jaewan Park, and Sungmin Jo
package com.lastpenguin.pix.ui

import android.Manifest
import android.app.Application
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.Friend
import com.lastpenguin.pix.account.FriendRequest
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.confirmBegin
import com.lastpenguin.pix.session.confirmChunk
import com.lastpenguin.pix.session.confirmEnd
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.ConfirmStep
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.sceneBegin
import com.lastpenguin.pix.session.sceneChunk
import com.lastpenguin.pix.session.sceneEnd
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.account.AccountSheet
import com.lastpenguin.pix.ui.account.AddFriendFragment
import com.lastpenguin.pix.ui.account.FriendsFragment
import com.lastpenguin.pix.ui.account.PixIdSetupFragment
import com.lastpenguin.pix.ui.account.SentRequestsFragment
import com.lastpenguin.pix.ui.account.SignInFragment
import com.lastpenguin.pix.ui.account.SignUpFragment
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.generation.DescribePoseFragment
import com.lastpenguin.pix.ui.generation.GeneratingFragment
import com.lastpenguin.pix.ui.generation.GenerationFailedFragment
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.generation.PickPoseFragment
import com.lastpenguin.pix.ui.guide.AddGuideSheet
import com.lastpenguin.pix.ui.guide.NoPersonFoundFragment
import com.lastpenguin.pix.ui.guide.ReferenceConfirmFragment
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import com.lastpenguin.pix.ui.session.ConnectionLostFragment
import com.lastpenguin.pix.ui.session.InvitationFragment
import com.lastpenguin.pix.ui.session.InviteNotAcceptedFragment
import com.lastpenguin.pix.ui.session.InviteSentSheet
import com.lastpenguin.pix.ui.session.JoinCodeFragment
import com.lastpenguin.pix.ui.session.RoomCodeSheet
import com.lastpenguin.pix.ui.session.SessionNotFoundFragment
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.session.ShadowSurfaceViewRenderer
import com.lastpenguin.pix.ui.session.SubjectConfirmFragment
import com.lastpenguin.pix.ui.session.SubjectDescribeFragment
import com.lastpenguin.pix.ui.session.SubjectFragment
import com.lastpenguin.pix.ui.session.SubjectPickPoseFragment
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Every screen in Korean (FR-10.2, Iter3-P7; Testing Documentation 2.1.6): the real Fragments on the fakes, with the
 * phone in Korean, at the Galaxy S22's app area of 360 x 705 dp. Each screen in the states that show its texts: Camera
 * without camera access, without a guide, with one, taking and reviewing the scene photo, and in a session; Add a pose
 * guide with a saved guide; Reference confirm with the person found, and with the subject's pick; No person found;
 * Generate poses; Generating poses with a candidate ready; Pick a pose, alone and in a session; Couldn't create poses,
 * failed and refused; Friends signed out, and with a friend, a request, and a sent request; Sent requests; Add a
 * friend; Sign in; Sign up; Pix ID setup; Account; the room code; Join with a code; Session not found; Subject view,
 * and while the photographer picks a pose, with a guide to save; the subject's Pick a pose; Connection lost. On each, no visible text, hint, or content
 * description has an English word but what Korean keeps (KEPT) and the test's own data (names, Pix IDs, the server's
 * pose texts), and no text is cut off: none is ellipsized, has lines dropped, or is taller than its view, except a name
 * in a view that ellipsizes on purpose, and no line breaks inside a word. The text styles' font aliases: Pretendard in
 * Korean, Manrope in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
class KoreanScreensTest {
    private var scenario: FragmentScenario<*>? = null
    private val account = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)
    private val friend = Friend("u_2", "test2", "Test Account 2", online = true, inSession = false)
    private val request = FriendRequest("fr_80", PixUser("u_3", "test3", "Test Account 3"), 1_000)
    private val sent = SentRequest("fr_77", PixUser("u_4", "test4", "Test Account 4"), 900)

    // The test's people: their names, their Pix IDs, and the initial their avatars show.
    private val people = arrayOf("Test Account 1", "Test Account 2", "Test Account 3", "Test Account 4", "test", "T")

    // The server's poses, in English like its suggestions and variations; Pick a pose reads them out to TalkBack.
    private val poses = listOf("Lean on the wall", "Walk toward the camera", "Sit on the step", "Wave")

    // Where the subject's pose syncer runs, as the session would run it.
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    // ---- Camera --------------------------------------------------------------------

    private fun camera(granted: Boolean = true): TestAppContainer {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (granted) shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val camera = FakeCameraController(hasFlash = true).apply { frame = bitmap(300, 400) }
        val modules = TestAppContainer(camera = camera).install()
        scenario = launchFragmentInContainer<CameraFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    @Test
    fun camera_withoutCameraAccess_isInKorean() {
        camera(granted = false)
        assertKorean(screen())
    }

    @Test
    fun camera_withoutAGuide_isInKorean() {
        camera()
        assertKorean(screen())
    }

    @Test
    fun camera_withAGuide_isInKorean() {
        val modules = camera()
        modules.guides.setGuide(guide())
        assertKorean(screen())
    }

    @Test
    fun camera_takingAndReviewingTheScenePhoto_isInKorean() {
        camera()
        scenario!!.onFragment { it.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        assertKorean(screen())

        scenario!!.onFragment { FragmentCameraBinding.bind(it.requireView()).shutterButton.performClick() }
        assertKorean(screen())
    }

    @Test
    fun camera_inASession_isInKorean() {
        val modules = camera()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        assertKorean(screen(), *people)
    }

    // ---- Pose guide ------------------------------------------------------------------

    @Test
    fun addGuide_withASavedGuide_isInKorean() {
        val modules = TestAppContainer().install()
        runBlocking { modules.saved.save(guide(), GuideState(guideId = "guide-1")) }
        scenario = launchFragment<AddGuideSheet>(themeResId = R.style.Theme_Pix)
        assertKorean(screen())
    }

    @Test
    fun referenceConfirm_withThePersonFound_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<ReferenceConfirmFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.referenceConfirmFragment)
        }
        scenario!!.onFragment {
            it.requireActivity().viewModel<ReferenceViewModel>().onPhotoPicked(Uri.parse("content://photos/1"))
        }
        idle()
        modules.maker.finish(Result.success(guide()))
        assertKorean(screen())
    }

    @Test
    fun referenceConfirm_withTheSubjectsPick_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<ReferenceConfirmFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.referenceConfirmFragment)
        }
        scenario!!.onFragment {
            it.requireActivity().viewModel<ReferenceViewModel>().onCandidatePicked(
                bitmap(),
                chosenBy = "Test Account 2",
            )
        }
        idle()
        modules.maker.finish(Result.success(guide()))
        assertKorean(screen(), *people)
    }

    @Test
    fun referenceConfirm_ofAChosenPose_inASession_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<ReferenceConfirmFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.referenceConfirmFragment)
        }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        scenario!!.onFragment {
            it.requireActivity().viewModel<ReferenceViewModel>().onCandidatePicked(
                bitmap(),
                chosenBy = "Test Account 2",
            )
        }
        idle()
        modules.maker.finish(Result.success(guide()))
        assertKorean(screen(), *people)
    }

    @Test
    fun noPersonFound_isInKorean() {
        TestAppContainer().install()
        scenario = launchFragmentInContainer<NoPersonFoundFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.noPersonFoundFragment)
        }
        assertKorean(screen())
    }

    // ---- Pose generation ---------------------------------------------------------------

    private fun generationModules(): TestAppContainer =
        TestAppContainer(camera = FakeCameraController().apply { frame = bitmap(300, 400) }).install()

    /** The scene photo taken and confirmed (DESCRIBING). */
    private fun describing(activity: FragmentActivity): GenerationViewModel =
        activity.viewModel<GenerationViewModel>().apply {
            beginScene()
            takeScene()
            useScene()
        }

    /** A set of [poses] planned for "손 흔들기", with [events] already in. */
    private fun generating(modules: TestAppContainer, activity: FragmentActivity, vararg events: CandidateEvent) {
        describing(activity).generate("손 흔들기")
        modules.generator.runs.last().apply {
            plan(poses)
            send(*events)
        }
    }

    private fun finished(modules: TestAppContainer, activity: FragmentActivity, vararg events: CandidateEvent) {
        generating(modules, activity, *events)
        modules.generator.runs.last().finish()
    }

    @Test
    fun describePoses_isInKorean() {
        generationModules()
        scenario = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }.first
        assertKorean(screen())
    }

    @Test
    fun describePoses_whileTheSubjectTypes_isInKorean() {
        val modules = generationModules()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        scenario = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }.first
        idle()
        val id = modules.session.sent.filterIsInstance<SessionMessage.PoseDescription>().last().descriptionId
        modules.session.deliver(SessionMessage.PoseDescriptionEdit(id, holding = true, text = "벽에 기대서 웃기"))
        idle()
        assertKorean(screen(), *people)
    }

    @Test
    fun generatingPoses_withACandidateReady_isInKorean() {
        val modules = generationModules()
        scenario = launchPrepared<GeneratingFragment>(R.id.generatingFragment) {
            generating(modules, it, CandidateEvent.Ready("pose-1", bitmap()))
        }.first
        assertKorean(screen(), *poses.toTypedArray())
    }

    @Test
    fun pickAPose_isInKorean() {
        val modules = generationModules()
        scenario = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) {
            finished(
                modules,
                it,
                CandidateEvent.Ready("pose-1", bitmap()),
                CandidateEvent.Ready("pose-2", bitmap()),
                CandidateEvent.Failed("pose-3", GenerationError.TIMEOUT),
                CandidateEvent.Ready("pose-4", bitmap()),
            )
        }.first
        assertKorean(screen(), *poses.toTypedArray())
    }

    @Test
    fun pickAPose_inASession_isInKorean() {
        val modules = generationModules()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        scenario = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) {
            finished(modules, it, CandidateEvent.Ready("pose-1", bitmap()), CandidateEvent.Ready("pose-2", bitmap()))
        }.first
        assertKorean(screen(), *poses.toTypedArray(), *people)
    }

    private fun allFailed(error: GenerationError) {
        val modules = generationModules()
        scenario = launchPrepared<GenerationFailedFragment>(R.id.generationFailedFragment) { activity ->
            val failed = poses.indices.map { i -> CandidateEvent.Failed("pose-${i + 1}", error) }
            finished(modules, activity, *failed.toTypedArray())
        }.first
    }

    @Test
    fun couldntCreatePoses_isInKorean() {
        allFailed(GenerationError.SERVER)
        assertKorean(screen())
    }

    @Test
    fun couldntCreatePoses_refused_isInKorean() {
        allFailed(GenerationError.REJECTED)
        assertKorean(screen())
    }

    // ---- Friends and accounts --------------------------------------------------------------

    private inline fun <reified F : Fragment> accountScreen(
        destination: Int,
        signedIn: Boolean = true,
        prepare: (TestAppContainer) -> Unit = {},
    ) {
        val modules = TestAppContainer()
        if (signedIn) modules.auth.signInAs(account)
        prepare(modules)
        modules.install()
        scenario = launchFragmentInContainer<F>(themeResId = R.style.Theme_Pix).also { it.attachNav(destination) }
        idle()
    }

    @Test
    fun friends_signedOut_isInKorean() {
        accountScreen<FriendsFragment>(R.id.friendsFragment, signedIn = false)
        assertKorean(screen())
    }

    @Test
    fun friends_withAFriendARequestAndASentRequest_isInKorean() {
        accountScreen<FriendsFragment>(R.id.friendsFragment) { modules ->
            modules.friends.friendsFlow.value = listOf(friend)
            modules.friends.requestsFlow.value = listOf(request)
            modules.friends.sentFlow.value = listOf(sent)
        }
        assertKorean(screen(), *people)
    }

    @Test
    fun sentRequests_isInKorean() {
        accountScreen<SentRequestsFragment>(R.id.sentRequestsFragment) { it.friends.sentFlow.value = listOf(sent) }
        assertKorean(screen(), *people)
    }

    @Test
    fun addAFriend_isInKorean() {
        accountScreen<AddFriendFragment>(R.id.addFriendFragment)
        assertKorean(screen())
    }

    @Test
    fun signIn_isInKorean() {
        accountScreen<SignInFragment>(R.id.signInFragment, signedIn = false)
        assertKorean(screen())
    }

    @Test
    fun signUp_isInKorean() {
        accountScreen<SignUpFragment>(R.id.signUpFragment, signedIn = false)
        assertKorean(screen())
    }

    @Test
    fun pixIdSetup_isInKorean() {
        accountScreen<PixIdSetupFragment>(R.id.pixIdSetupFragment)
        assertKorean(screen())
    }

    @Test
    fun accountSheet_isInKorean() {
        val modules = TestAppContainer()
        modules.auth.signInAs(account)
        modules.install()
        scenario = launchFragment<AccountSheet>(themeResId = R.style.Theme_Pix)
        assertKorean(screen(), *people)
    }

    // ---- Sessions ----------------------------------------------------------------------

    @Test
    fun roomCode_waitingForAFriend_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragment<RoomCodeSheet>(themeResId = R.style.Theme_Pix)
        modules.session.setState(SessionState.Waiting("482915"))
        assertKorean(screen())
    }

    @Test
    fun inviteSent_waitingForAFriend_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragment<InviteSentSheet>(themeResId = R.style.Theme_Pix)
        scenario!!.onFragment { it.requireActivity().viewModel<SessionViewModel>().invite(sent.to) }
        idle()
        modules.session.setState(SessionState.Inviting("inv_1"))
        assertKorean(screen(), *people)
    }

    @Test
    fun inviteNotAccepted_isInKorean() {
        TestAppContainer().install()
        scenario = launchPrepared<InviteNotAcceptedFragment>(R.id.inviteNotAcceptedFragment) {
            val sessionViewModel = it.viewModel<SessionViewModel>()
            sessionViewModel.invite(sent.to)
            idle()
            // Invite sent read the Sent in the app.
            runBlocking { sessionViewModel.inviteResults.first() }
        }.first
        assertKorean(screen(), *people)
    }

    @Test
    fun invitation_open_isInKorean() {
        TestAppContainer().also { it.auth.signInAs(account) }.install()
        scenario = launchPrepared<InvitationFragment>(
            R.id.invitationFragment,
            bundleOf(InvitationFragment.ARG_INVITATION_ID to "inv_1"),
        ) {}.first
        assertKorean(screen(), *people)
    }

    @Test
    fun invitation_noLongerAvailable_isInKorean() {
        TestAppContainer().also {
            it.auth.signInAs(account)
            it.invitations.opened = it.invitations.opened.copy(status = InvitationStatus.EXPIRED)
        }.install()
        scenario = launchPrepared<InvitationFragment>(
            R.id.invitationFragment,
            bundleOf(InvitationFragment.ARG_INVITATION_ID to "inv_1"),
        ) {}.first
        assertKorean(screen())
    }

    @Test
    fun joinWithACode_isInKorean() {
        TestAppContainer().install()
        scenario = launchFragmentInContainer<JoinCodeFragment>(
            fragmentArgs = bundleOf("keepCode" to false),
            themeResId = R.style.Theme_Pix,
        ).also { it.attachNav(R.id.joinCodeFragment) }
        assertKorean(screen())
    }

    @Test
    fun sessionNotFound_isInKorean() {
        TestAppContainer().install()
        scenario = launchFragmentInContainer<SessionNotFoundFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.sessionNotFoundFragment)
        }
        assertKorean(screen())
    }

    @Test
    @Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
    fun subjectView_connected_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 2"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(1f, 2f), -4..4, 0.5f, true)),
        )
        // The "Connecting…" check runs every 500 ms on the main looper; let one more round see the session.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertKorean(screen(), *people)
    }

    /** The photographer's set PICKING on the subject's phone, with two candidates. */
    private fun photographerPicking(modules: TestAppContainer) {
        modules.poses.startAsReceiver(receiving)
        idle()
        modules.session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.GENERATING))
        idle()
        for (poseId in listOf("pose-1", "pose-2")) {
            val copy = checkNotNull(FakePoseCandidateCodec().encode(poseId, bitmap()))
            modules.session.deliver(copy.begin("set1"))
            for (i in copy.chunks.indices) modules.session.deliver(copy.chunk("set1", i))
            modules.session.deliver(copy.end("set1"))
            idle()
        }
        modules.session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.PICKING, listOf("pose-1", "pose-2")))
        idle()
    }

    @Test
    @Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
    fun subjectView_whileThePhotographerPicksAPose_withAGuide_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.subjectFragment)
        }
        idle()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.mirror.setGuide(guide())
        photographerPicking(modules)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertKorean(screen(), *people)
    }

    /** The photographer's Reference confirm READY on the subject's phone, with its outline. */
    private fun photographerConfirming(modules: TestAppContainer) {
        modules.confirms.startAsReceiver(receiving)
        idle()
        modules.session.deliver(SessionMessage.GuideConfirm("c1", ConfirmStep.READY, guideId = "g1"))
        val image = FakeGuideImageCodec().encode(fakeGuide("g1"))
        modules.session.deliver(image.confirmBegin("c1"))
        for (i in image.chunks.indices) modules.session.deliver(image.confirmChunk("c1", i))
        modules.session.deliver(image.confirmEnd("c1"))
        idle()
    }

    @Test
    @Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
    fun subjectView_whileThePhotographerChecksTheGuide_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.subjectFragment)
        }
        idle()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        photographerConfirming(modules)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertKorean(screen(), *people)
    }

    /** The photographer's Generate poses on the subject's phone, the photographer typing. */
    private fun photographerDescribing(modules: TestAppContainer) {
        modules.descriptions.startAsReceiver(receiving)
        idle()
        modules.session.deliver(
            SessionMessage.PoseDescription("d1", DescriptionStep.OPEN, "벽에 기대서 웃기", Role.PHOTOGRAPHER),
        )
        val copy = checkNotNull(FakePoseCandidateCodec().encode("d1", bitmap()))
        modules.session.deliver(copy.sceneBegin("d1"))
        for (i in copy.chunks.indices) modules.session.deliver(copy.sceneChunk("d1", i))
        modules.session.deliver(copy.sceneEnd("d1"))
        idle()
    }

    @Test
    @Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
    fun subjectView_whileThePhotographerDescribesAPose_isInKorean() {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.subjectFragment)
        }
        idle()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        photographerDescribing(modules)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertKorean(screen(), *people)
    }

    @Test
    fun subjectsGeneratePoses_whileThePhotographerTypes_isInKorean() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        photographerDescribing(modules)
        scenario = launchFragmentInContainer<SubjectDescribeFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.subjectDescribeFragment)
        }
        assertKorean(screen(), *people)
    }

    @Test
    fun subjectsYourReference_isInKorean() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        photographerConfirming(modules)
        scenario = launchFragmentInContainer<SubjectConfirmFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.subjectConfirmFragment)
        }
        assertKorean(screen(), *people)
    }

    @Test
    fun subjectsPickAPose_isInKorean() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        photographerPicking(modules)
        scenario = launchFragmentInContainer<SubjectPickPoseFragment>(themeResId = R.style.Theme_Pix).also {
            it.attachNav(R.id.subjectPickPoseFragment)
        }
        assertKorean(screen(), *people)
    }

    @Test
    fun connectionLost_isInKorean() {
        TestAppContainer().install()
        scenario = launchFragmentInContainer<ConnectionLostFragment>(themeResId = R.style.Theme_Pix)
        assertKorean(screen())
    }

    // ---- Fonts ------------------------------------------------------------------------------

    /** How wide [text] is in [font], so two fonts can be told apart by what they draw. */
    private fun width(font: Int, text: String): Float {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return Paint().apply {
            typeface = ResourcesCompat.getFont(context, font)
            textSize = 48f
        }.measureText(text)
    }

    @Test
    fun inKorean_theFontAliasesArePretendard() {
        val pairs = listOf(R.font.pix_medium to R.font.pretendard_medium, R.font.pix_bold to R.font.pretendard_bold)
        for ((alias, pretendard) in pairs) {
            assertEquals(width(pretendard, "가이드 저장 Pix ID"), width(alias, "가이드 저장 Pix ID"), 0.01f)
        }
        assertNotEquals(width(R.font.manrope_bold, "Pix ID"), width(R.font.pix_bold, "Pix ID"), 0.01f)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h705dp-xxhdpi")
    fun inEnglish_theFontAliasesAreManrope() {
        assertEquals(width(R.font.manrope_bold, "Pix ID"), width(R.font.pix_bold, "Pix ID"), 0.01f)
        assertNotEquals(width(R.font.pretendard_bold, "Pix ID"), width(R.font.pix_bold, "Pix ID"), 0.01f)
    }

    // ---- The checks ----------------------------------------------------------------------

    private fun screen(): View {
        idle()
        var view: View? = null
        scenario!!.onFragment { view = it.requireView() }
        return view!!
    }

    /** Fails with every English word and every cut-off text on the shown views under [root], [data] left out. */
    private fun assertKorean(root: View, vararg data: String) {
        val problems = mutableListOf<String>()
        shown(root).forEach { view ->
            for (text in texts(view)) {
                val english = WORD.findAll((data.toList() + KEPT).fold(text) { left, kept -> left.replace(kept, "") })
                    .map { it.value }
                    .toList()
                if (english.isNotEmpty()) problems += "${name(view)}: English $english in \"$text\""
            }
            if (view is TextView) cutOff(view)?.let { problems += "${name(view)}: $it, \"${view.text}\"" }
            if (view is TextView) wordBroken(view)?.let { problems += "${name(view)}: a word broken at \"$it\"" }
        }
        assertEquals(emptyList<String>(), problems)
    }

    private fun shown(view: View): Sequence<View> = sequence {
        if (!view.isShown) return@sequence
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(shown(view.getChildAt(i)))
    }

    /** What [view] shows or reads out: its text, its hint while it has no text, and its content description. */
    private fun texts(view: View): List<String> = listOfNotNull(
        (view as? TextView)?.text?.toString()?.takeIf { it.isNotEmpty() },
        (view as? TextView)?.takeIf { it.text.isEmpty() }?.hint?.toString(),
        view.contentDescription?.toString(),
    )

    /**
     * Why the text of [view] does not fit, or null when it does: ellipsized, its last lines dropped, or taller than the
     * view. A view that ellipsizes on purpose holds a name, which may be cut in either language.
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

    /** Where [view] breaks a line inside a word, as the text around the break, or null when every break is between words. */
    private fun wordBroken(view: TextView): String? {
        val layout = view.layout ?: return null
        val text = view.text
        return (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
            .firstOrNull { it in 1 until text.length && text[it - 1].isLetterOrDigit() && text[it].isLetterOrDigit() }
            ?.let { "${text.subSequence(maxOf(0, it - 4), it)}|${text.subSequence(it, minOf(text.length, it + 4))}" }
    }

    /** The view's id, or its class when it has none; ids that views make for themselves have no name. */
    private fun name(view: View): String =
        runCatching { view.resources.getResourceEntryName(view.id) }.getOrElse { view.javaClass.simpleName }

    private companion object {
        val WORD = Regex("[A-Za-z]+")

        /** What Korean keeps as it is in English: names, and the letters a Pix ID may use. */
        val KEPT = listOf("Pix", "ID", "Google", "AI", "a–z")
    }
}
