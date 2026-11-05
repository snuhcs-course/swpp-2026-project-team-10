// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.account.OpenedInvitation
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.databinding.FragmentJoinCodeBinding
import com.lastpenguin.pix.databinding.ItemIncomingInvitationBinding
import com.lastpenguin.pix.push.PushNotifications
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * See their camera's render paths, Join with code until #188 (R&S 6.7, FR-8.2, FR-8.3, FR-8.5; Testing Documentation
 * 2.1.6), on the real Fragment. Partitions: where the code comes from, by the photographer's buttons' own labels; the
 * field empty, 5 digits, 6 digits; opened fresh or to correct a kept code; Connect pressed, then the session
 * connecting (to Subject view), not found or expired (to Session not found), refused for too many wrong codes from
 * this network (its message, Connect enabled again; NFR-15), or failing otherwise (the same-Wi-Fi message, Connect
 * enabled again); Back. Received invitations (FR-6.17): none (no section, the code's own heading, the keyboard up),
 * one and two (a compact row each, newest first, with the time left, the "Or" heading, the keyboard down when they are
 * known as the screen opens), one already expired (not listed); Accept (Subject view with the inviter as the partner,
 * its notification taken away), Accept while another session is open (nothing accepted) or while this phone waits in
 * a room alone after its subject left (the room ended, then accepted), Accept of one gone since (its
 * row gone, said), the session not taking the place (said, the screen kept), Decline (its row gone).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JoinCodeFragmentTest {
    private var scenario: FragmentScenario<JoinCodeFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(keepCode: Boolean = false): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<JoinCodeFragment>(
            fragmentArgs = bundleOf("keepCode" to keepCode),
            themeResId = R.style.Theme_Pix,
        )
        idle()
        return modules
    }

    /**
     * Signed in with [waiting] received. [known] reads them before the screen opens, as Who takes the photo? does
     * while it is shown; otherwise the screen's own first read brings them.
     */
    private fun launchInvited(vararg waiting: OpenedInvitation, known: Boolean = true): TestAppContainer {
        val modules = TestAppContainer()
        modules.auth.signInAs(modules.auth.account)
        modules.invitations.incoming = waiting.toList()
        modules.install()
        scenario = launchFragmentInContainer<JoinCodeFragment>(
            fragmentArgs = bundleOf("keepCode" to false),
            themeResId = R.style.Theme_Pix,
            initialState = if (known) Lifecycle.State.CREATED else Lifecycle.State.RESUMED,
        )
        if (known) {
            scenario!!.onFragment { fragment ->
                val activity = fragment.requireActivity()
                val incoming = ViewModelProvider(
                    activity,
                    PixViewModels.Factory,
                )[IncomingInvitationsViewModel::class.java]
                activity.lifecycleScope.launch { incoming.keepRefreshing() }
            }
            idle()
            scenario!!.moveToState(Lifecycle.State.RESUMED)
        }
        idle()
        return modules
    }

    private fun rows(): List<ItemIncomingInvitationBinding> = onScreen { binding, _ ->
        (0 until binding.invitationsList.childCount).map {
            ItemIncomingInvitationBinding.bind(binding.invitationsList.getChildAt(it))
        }
    }

    private fun <T> onScreen(block: (FragmentJoinCodeBinding, JoinCodeFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result =
                block(FragmentJoinCodeBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    private fun sessionViewModel(fragment: JoinCodeFragment): SessionViewModel =
        ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[SessionViewModel::class.java]

    private fun type(code: String) = onScreen { binding, _ -> binding.codeInput.setText(code) }

    @Test
    fun saysWhereTheCodeComesFrom_onThePhotographersPhone() {
        launch()

        onScreen { binding, _ ->
            val hint = binding.joinHint.text
            assertEquals("It shows when they tap I'll take the photo › Show a code.", hint.toString())
            // The photographer's buttons stand out.
            val span = (hint as Spanned).getSpans(0, hint.length, ForegroundColorSpan::class.java).single()
            val colored = hint.subSequence(hint.getSpanStart(span), hint.getSpanEnd(span)).toString()
            assertEquals("I'll take the photo › Show a code", colored)
            assertEquals(string(R.string.join), binding.joinButton.text.toString())
        }
    }

    @Test
    fun startsEmpty_withJoinDisabled() {
        launch()

        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.invitationsSection.visibility)
            assertEquals(string(R.string.join_body), binding.codeHeading.text.toString())
            assertTrue(binding.codeInput.hasFocus())
            assertEquals("", binding.codeInput.text.toString())
            assertFalse(binding.joinButton.isEnabled)
            assertEquals(View.GONE, binding.statusText.visibility)
            assertEquals("", binding.digit1.text.toString())
        }
    }

    @Test
    fun sixDigits_enableJoinAndFillTheBoxes_fiveDoNot() {
        launch()

        type("48291")
        onScreen { binding, _ ->
            assertFalse(binding.joinButton.isEnabled)
            assertEquals("1", binding.digit5.text.toString())
            assertEquals("", binding.digit6.text.toString())
        }

        type("482915")
        onScreen { binding, _ ->
            assertTrue(binding.joinButton.isEnabled)
            assertEquals(
                listOf("4", "8", "2", "9", "1", "5"),
                listOf(
                    binding.digit1,
                    binding.digit2,
                    binding.digit3,
                    binding.digit4,
                    binding.digit5,
                    binding.digit6,
                ).map { it.text.toString() },
            )
        }
    }

    @Test
    fun openedToCorrectACode_offersTheKeptCode() {
        launch(keepCode = true)
        // Session not found keeps the code of the join that failed (FR-8.3); its Try again opens this screen as a
        // new instance, which recreate() stands for. The field's own saved state is turned off, because a new
        // instance has none, while a rotation would restore what was typed.
        onScreen { binding, fragment ->
            sessionViewModel(fragment).joinRoom("482915")
            binding.codeInput.isSaveEnabled = false
        }
        idle()

        scenario!!.recreate()
        idle()

        onScreen { binding, _ ->
            assertEquals("482915", binding.codeInput.text.toString())
            assertTrue(binding.joinButton.isEnabled)
        }
    }

    @Test
    fun join_showsConnecting_andAConnectingSessionOpensSubjectView() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)

        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.statusText.visibility)
            assertEquals(string(R.string.connecting), binding.statusText.text.toString())
            assertFalse(binding.joinButton.isEnabled)
        }
        assertEquals(SessionEntry.RoomCode("482915") to Role.SUBJECT, modules.session.starts.last())

        modules.session.setState(SessionState.Connecting("s1"))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun join_unknownCode_opensSessionNotFound() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)
        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        idle()

        assertEquals(R.id.sessionNotFoundFragment, nav.currentDestination?.id)
    }

    @Test
    fun join_otherFailure_showsTheWifiMessageAndEnablesJoinAgain() {
        val modules = launch()
        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.ERROR))
        idle()

        onScreen { binding, _ ->
            assertEquals(string(R.string.couldnt_connect), binding.statusText.text.toString())
            assertEquals(View.VISIBLE, binding.statusText.visibility)
            assertTrue(binding.joinButton.isEnabled)
        }
    }

    @Test
    fun join_tooManyWrongCodesFromThisNetwork_saysSoAndEnablesJoinAgain() {
        val modules = launch()
        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.RATE_LIMITED))
        idle()

        onScreen { binding, _ ->
            assertEquals(string(R.string.join_rate_limited), binding.statusText.text.toString())
            assertEquals(View.VISIBLE, binding.statusText.visibility)
            assertTrue(binding.joinButton.isEnabled)
        }
    }

    @Test
    fun invitations_eachACompactRow_newestFirst_withTheTimeLeft_andTheKeyboardDown() {
        launchInvited(invitation("inv_2", JUNHYEONG, 48_000), invitation("inv_1", DONGJE, 21_000))

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.invitationsSection.visibility)
            assertEquals(string(R.string.join_body_or), binding.codeHeading.text.toString())
            assertFalse(binding.codeInput.hasFocus())
        }
        val rows = rows()
        assertEquals(listOf("Junhyeong", "Dongje"), rows.map { it.nameText.text.toString() })
        assertEquals(listOf("J", "D"), rows.map { it.avatarText.text.toString() })
        assertEquals(listOf("0:48", "0:21"), rows.map { it.timeLeftText.text.toString() })
        assertEquals(string(R.string.accept_invitation_from, "Dongje"), rows[1].acceptButton.contentDescription)
        assertEquals(string(R.string.decline_invitation_from, "Dongje"), rows[1].declineButton.contentDescription)
    }

    @Test
    fun oneInvitation_isACompactRowToo_andOneThatExpiredIsNotListed() {
        launchInvited(invitation("inv_1", DONGJE, 48_000), invitation("inv_0", JUNHYEONG, -1_000), known = false)

        assertEquals(listOf("Dongje"), rows().map { it.nameText.text.toString() })
        onScreen { binding, _ -> assertEquals(View.VISIBLE, binding.invitationsSection.visibility) }
    }

    @Test
    fun accept_opensSubjectView_withTheInviter_andTakesTheNotificationAway() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val expiresAt = System.currentTimeMillis() + 48_000
        val push = mapOf(
            "type" to "invitation",
            "invitationId" to "inv_1",
            "fromName" to "Dongje",
            "expiresAt" to expiresAt.toString(),
        )
        assertTrue(PushNotifications.show(context, push))
        val modules = launchInvited(invitation("inv_1", DONGJE, 48_000))
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)

        rows().single().acceptButton.performClick()
        idle()

        assertEquals("accept:inv_1", modules.invitations.calls.last { it != "incoming" })
        assertEquals(
            SessionEntry.Invitation("inv_1", modules.invitations.place, SessionPartner("u_1", "Dongje")) to
                Role.SUBJECT,
            modules.session.starts.single(),
        )
        val notifications = context.getSystemService(NotificationManager::class.java)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        modules.session.setState(SessionState.Connecting("s_1"))
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun accept_whileAnotherSessionIsOpen_saysSo_andAcceptsNothing() {
        val modules = launchInvited(invitation("inv_1", DONGJE, 48_000))
        modules.session.setState(SessionState.Connected("s_0", PeerInfo("Junhyeong"), Role.SUBJECT))
        idle()

        rows().single().acceptButton.performClick()
        idle()

        assertEquals(string(R.string.invitation_in_session), ShadowToast.getTextOfLatestToast())
        assertFalse(modules.invitations.calls.any { it.startsWith("accept") })
        assertEquals(1, rows().size)
    }

    @Test
    fun accept_whileThisPhoneWaitsInARoomAlone_endsTheRoom_thenAccepts() {
        val modules = launchInvited(invitation("inv_1", DONGJE, 48_000))
        modules.session.endsOnLeave = true
        // The subject of this phone's room-code session left, and the room waits for a Reconnect (Design 2.4).
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        rows().single().acceptButton.performClick()
        idle()

        assertEquals(listOf(EndReason.CANCELLED), modules.session.leaves)
        assertEquals("accept:inv_1", modules.invitations.calls.last { it != "incoming" })
        assertEquals(
            SessionEntry.Invitation("inv_1", modules.invitations.place, SessionPartner("u_1", "Dongje")) to
                Role.SUBJECT,
            modules.session.starts.single(),
        )
    }

    @Test
    fun accept_ofOneGoneSince_removesItsRow_andSaysSo() {
        val modules = launchInvited(invitation("inv_1", DONGJE, 48_000))
        modules.invitations.failure = AccountError.INVITATION_GONE

        rows().single().acceptButton.performClick()
        idle()

        assertEquals(string(R.string.invitation_gone_title), ShadowToast.getTextOfLatestToast())
        assertTrue(modules.session.starts.isEmpty())
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.invitationsSection.visibility)
            assertEquals(string(R.string.join_body), binding.codeHeading.text.toString())
        }
    }

    @Test
    fun accept_whenThePhotographerLeftMeanwhile_saysTheInvitationIsGone_andStays() {
        val modules = launchInvited(invitation("inv_1", DONGJE, 48_000))
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)
        rows().single().acceptButton.performClick()
        idle()

        modules.session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        idle()

        assertEquals(string(R.string.invitation_gone_title), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.joinCodeFragment, nav.currentDestination?.id)
        // Answered, so its row went with the accept.
        onScreen { binding, _ -> assertEquals(View.GONE, binding.invitationsSection.visibility) }
    }

    @Test
    fun decline_removesTheRow() {
        val modules = launchInvited(invitation("inv_2", JUNHYEONG, 48_000), invitation("inv_1", DONGJE, 21_000))

        rows()[1].declineButton.performClick()
        idle()

        assertEquals("decline:inv_1", modules.invitations.calls.last { it != "incoming" })
        assertEquals(listOf("Junhyeong"), rows().map { it.nameText.text.toString() })
        assertTrue(modules.session.starts.isEmpty())
    }

    @Test
    fun back_returnsToTheCamera() {
        launch()
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)

        onScreen { binding, _ -> binding.backButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    private companion object {
        val DONGJE = PixUser("u_1", "dongje.p", "Dongje")
        val JUNHYEONG = PixUser("u_3", "junhyeong.h", "Junhyeong")

        /** From [from], with [left] ms to go by this phone's clock. */
        fun invitation(id: String, from: PixUser, left: Long) =
            OpenedInvitation(id, from, InvitationStatus.PENDING, System.currentTimeMillis() + left)
    }
}
