// AI-generated with Claude Code, 2026-11-06, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.session

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.account.OpenedInvitation
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Who takes the photo? (R&S 6.7, FR-5.2; #188) on the real bottom sheet over Camera. Partitions: the choice (I'll take
 * the photo → Share my camera; I'll be in the photo → See their camera), each in the sheet's place so Back from it is
 * Camera; what each choice says it does; nothing starts before a choice (no room, no join); I'll be in the photo with
 * a room this phone waits in alone, after its subject left (ended), or a session with someone in it (kept). Received invitations
 * (FR-6.17): none (no badge), one and two (counted on I'll be in the photo, which is outlined, the badge within the tile
 * at the Galaxy S22's width), signed out (none asked).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShootTogetherSheetTest {
    private var scenario: FragmentScenario<ShootTogetherSheet>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** The sheet over Camera, as *Shoot together* opens it, with [waiting] received while signed in. */
    private fun launch(vararg waiting: OpenedInvitation, signedIn: Boolean = waiting.isNotEmpty()): TestAppContainer {
        val modules = TestAppContainer()
        if (signedIn) modules.auth.signInAs(modules.auth.account)
        modules.invitations.incoming = waiting.toList()
        modules.install()
        scenario = launchFragment<ShootTogetherSheet>(themeResId = R.style.Theme_Pix)
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        scenario!!.onFragment { sheet ->
            nav.setGraph(R.navigation.nav_graph)
            nav.navigate(R.id.action_camera_to_shootTogether)
            Navigation.setViewNavController(sheet.requireView(), nav)
        }
        idle()
        return modules
    }

    private fun tap(@IdRes choice: Int) {
        scenario!!.onFragment { it.requireView().findViewById<View>(choice).performClick() }
        idle()
    }

    private fun screens() = nav.currentBackStack.value.map { it.destination.id }.filter { it != R.id.nav_graph }

    /** The texts shown, without the badge while it is hidden. */
    private fun texts(view: View): List<String> = when {
        view.visibility != View.VISIBLE -> emptyList()
        view is TextView -> listOf(view.text.toString())
        view is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun takingThePhoto_opensShareMyCamera_inTheSheetsPlace() {
        val modules = launch()

        tap(R.id.photographerButton)

        assertEquals(listOf(R.id.cameraFragment, R.id.friendsFragment), screens())
        assertTrue(modules.session.starts.isEmpty())
    }

    @Test
    fun beingInThePhoto_opensSeeTheirCamera_inTheSheetsPlace() {
        val modules = launch()

        tap(R.id.subjectButton)

        assertEquals(listOf(R.id.cameraFragment, R.id.joinCodeFragment), screens())
        assertTrue(modules.session.starts.isEmpty())
    }

    @Test
    fun beingInThePhoto_endsARoomThisPhoneWaitsInAlone_butNotASessionWithSomeoneIn() {
        val modules = launch()
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        tap(R.id.subjectButton)

        assertEquals(listOf(EndReason.CANCELLED), modules.session.leaves)

        scenario!!.close()
        val connected = launch()
        connected.session.setState(SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        tap(R.id.subjectButton)
        assertTrue(connected.session.leaves.isEmpty())
    }

    @Test
    fun eachChoice_saysWhatItDoes() {
        launch()

        scenario!!.onFragment { sheet ->
            val view = sheet.requireView()
            assertTrue(string(R.string.who_takes_the_photo) in texts(view))
            assertEquals(
                listOf(string(R.string.role_photographer), string(R.string.role_photographer_body)),
                texts(view.findViewById(R.id.photographerButton)),
            )
            assertEquals(
                listOf(string(R.string.role_subject), string(R.string.role_subject_body)),
                texts(view.findViewById(R.id.subjectButton)),
            )
        }
    }

    private fun badge(): TextView? {
        var shown: TextView? = null
        scenario!!.onFragment { sheet ->
            shown = sheet.requireView().findViewById<TextView>(R.id.invitationsBadge).takeIf { it.isShown }
        }
        return shown
    }

    @Test
    fun noInvitations_noBadge_andSignedOut_noneAsked() {
        val modules = launch(signedIn = false)

        assertEquals(null, badge())
        assertTrue(modules.invitations.calls.isEmpty())
    }

    @Test
    @Config(qualifiers = "w360dp-h705dp")
    fun waitingInvitations_areCountedOnBeingInThePhoto_withinItsTile() {
        launch(invitation("inv_1"))
        assertEquals("1 invite", badge()?.text.toString())

        scenario!!.close()
        launch(invitation("inv_2"), invitation("inv_1"))
        val badge = badge()!!
        assertEquals("2 invites", badge.text.toString())
        scenario!!.onFragment { sheet ->
            val tile = sheet.requireView().findViewById<View>(R.id.subjectButton)
            val inTile = IntArray(2).also(badge::getLocationInWindow)[0] + badge.width
            val tileEnd = IntArray(2).also(tile::getLocationInWindow)[0] + tile.width - tile.paddingEnd
            assertTrue("badge ends at $inTile, the tile's content at $tileEnd", inTile <= tileEnd)
            assertEquals(1, badge.lineCount)
        }
    }

    private companion object {
        fun invitation(id: String) = OpenedInvitation(
            id,
            PixUser("u_1", "dongje.p", "Dongje"),
            InvitationStatus.PENDING,
            System.currentTimeMillis() + 48_000,
        )
    }
}
