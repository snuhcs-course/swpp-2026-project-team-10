// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetRoomCodeBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Room code's render paths (R&S 6.7, FR-8.1, FR-8.4; Testing Documentation 2.1.6), on the real bottom sheet.
 * Partitions on the session state: Idle, Waiting (the formatted code), Connecting, Connected (the sheet closes),
 * Ended by expiry and by another failure; which phone enters the code and where, with no way to join instead (#188;
 * until then, Join with a code instead cancelled the code and opened Join with code); Cancel; a configuration change,
 * such as the language, while the code waits (the room stays). The expiry note on one line at the Galaxy S22's
 * 360 × 705 dp, at font scale 1 and 1.3.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomCodeSheetTest {
    private var scenario: FragmentScenario<RoomCodeSheet>? = null
    private lateinit var sheet: RoomCodeSheet

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragment<RoomCodeSheet>(themeResId = R.style.Theme_Pix)
        scenario!!.onFragment { sheet = it }
        idle()
        return modules
    }

    private fun <T> onSheet(block: (SheetRoomCodeBinding) -> T): T = block(
        SheetRoomCodeBinding.bind(sheet.requireView()),
    )

    @Test
    fun idle_showsThePlaceholderAndGettingACode() {
        launch()

        onSheet { binding ->
            assertEquals(string(R.string.room_code_placeholder), binding.codeText.text.toString())
            assertEquals(string(R.string.getting_code), binding.statusText.text.toString())
        }
    }

    /** The sheet over an AppCompat activity, as in the app; without one, the notes' start icons are not laid out. */
    private fun showOverAppCompat(): SheetRoomCodeBinding {
        TestAppContainer().install()
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Pix) }
            .setup()
            .get()
        val sheet = RoomCodeSheet()
        sheet.show(activity.supportFragmentManager, null)
        idle()
        return SheetRoomCodeBinding.bind(sheet.requireView())
    }

    @Test
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun expiryNote_fitsOnOneLine_onA360dpPhone() {
        for (scale in listOf(1f, 1.3f)) {
            RuntimeEnvironment.setFontScale(scale)
            val binding = showOverAppCompat()

            assertEquals("at font scale $scale", 1, binding.expiryNote.lineCount)
        }
    }

    @Test
    fun aConfigurationChange_whileTheCodeWaits_keepsTheRoomAndTheCode() {
        val modules = TestAppContainer().install()
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        RoomCodeSheet().show(controller.get().supportFragmentManager, "roomCode")
        idle()
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        controller.recreate()
        idle()

        assertEquals(emptyList<EndReason>(), modules.session.leaves)
        val sheet = controller.get().supportFragmentManager.findFragmentByTag("roomCode") as RoomCodeSheet
        assertEquals("482 915", SheetRoomCodeBinding.bind(sheet.requireView()).codeText.text.toString())
    }

    @Test
    fun waiting_showsTheCodeInTwoGroups_thenConnectingWhenTheFriendJoins() {
        val modules = launch()

        modules.session.setState(SessionState.Waiting("482915"))
        idle()
        onSheet { binding ->
            assertEquals("482 915", binding.codeText.text.toString())
            assertEquals(string(R.string.waiting_to_connect), binding.statusText.text.toString())
        }

        modules.session.setState(SessionState.Connecting("s1"))
        idle()
        onSheet { binding ->
            assertEquals(string(R.string.peer_joined_connecting), binding.statusText.text.toString())
        }
    }

    @Test
    fun connected_closesTheSheetWithoutCancellingTheRoom() {
        val modules = launch()
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        idle()

        assertFalse(sheet.isAdded)
        assertTrue(modules.session.leaves.isEmpty())
    }

    @Test
    fun expired_saysSo_andAnotherFailureSaysCouldntConnect() {
        val modules = launch()

        modules.session.setState(SessionState.Ended(EndReason.EXPIRED))
        idle()
        onSheet { binding ->
            assertEquals(string(R.string.room_code_placeholder), binding.codeText.text.toString())
            assertEquals(string(R.string.code_expired), binding.statusText.text.toString())
        }

        modules.session.setState(SessionState.Ended(EndReason.ERROR))
        idle()
        onSheet { binding -> assertEquals(string(R.string.couldnt_connect), binding.statusText.text.toString()) }
    }

    @Test
    fun saysThatThePersonInThePhotoEntersTheCode_andWhere_withNoWayToJoinInstead() {
        val modules = launch()
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        onSheet { binding ->
            assertEquals(string(R.string.room_code_body), binding.joinSteps.text.toString())
            val where = binding.whereText.text
            assertEquals("Shoot together › I'll be in the photo", where.toString())
            // The other phone's button stands out.
            val span = (where as Spanned).getSpans(0, where.length, ForegroundColorSpan::class.java).single()
            val colored = where.subSequence(where.getSpanStart(span), where.getSpanEnd(span)).toString()
            assertEquals("I'll be in the photo", colored)
            // This phone's code is for the other phone: Cancel is the sheet's only button.
            val buttons = (0 until binding.root.childCount).map {
                binding.root.getChildAt(it)
            }.filterIsInstance<Button>()
            assertEquals(listOf(binding.cancelButton), buttons)
        }
    }

    @Test
    fun cancel_cancelsTheRoomAndCloses() {
        val modules = launch()
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        onSheet { binding -> binding.cancelButton.performClick() }
        idle()

        assertTrue(EndReason.CANCELLED in modules.session.leaves)
        assertFalse(sheet.isAdded)
    }
}
