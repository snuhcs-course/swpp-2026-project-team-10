// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.databinding.FragmentSentRequestsBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * Partitions for Sent requests (R&S 6.4; FR-5.16), on the real layout: none (the empty card), several (each with its
 * name, @ID, and Withdraw, newest first as given), one being withdrawn (its button waits), the last refresh failed.
 * Taps: Withdraw.
 */
@RunWith(AndroidJUnit4::class)
class SentRequestsScreenTest {
    private val withdrawn = mutableListOf<String>()
    private val account = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)
    private val sent = listOf(
        SentRequest("fr_77", PixUser("u_4", "test4", "Test Account 4"), 900),
        SentRequest("fr_76", PixUser("u_5", "test5", "Test Account 5"), 800),
    )

    private fun screen(): Pair<FragmentSentRequestsBinding, SentRequestsScreen> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = FragmentSentRequestsBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        return binding to SentRequestsScreen(binding) { withdrawn += it.id }
    }

    private val View.shown get() = visibility == View.VISIBLE

    private fun View.findText(id: Int): String = findViewById<TextView>(id).text.toString()

    @Test
    fun none_showsTheEmptyCard() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account)))

        assertTrue(binding.emptyCard.shown)
        assertEquals(0, binding.sentList.childCount)
        assertFalse(binding.offlineText.shown)
    }

    @Test
    fun each_showsItsUserAndWithdraws() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account), sent = sent))

        assertFalse(binding.emptyCard.shown)
        val rows = (0 until binding.sentList.childCount).map { binding.sentList.getChildAt(it) }
        assertEquals(listOf("Test Account 4", "Test Account 5"), rows.map { it.findText(R.id.nameText) })
        assertEquals(listOf("@test4", "@test5"), rows.map { it.findText(R.id.pixIdText) })
        val withdraw = rows[1].findViewById<Button>(R.id.withdrawButton)
        assertEquals("Withdraw the request to Test Account 5", withdraw.contentDescription)
        withdraw.performClick()
        assertEquals(listOf("fr_76"), withdrawn)
    }

    @Test
    fun oneBeingWithdrawn_waits() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account), sent = sent, answering = setOf("fr_77")))

        val buttons = (0 until 2).map { binding.sentList.getChildAt(it).findViewById<Button>(R.id.withdrawButton) }
        assertEquals(listOf(false, true), buttons.map { it.isEnabled })
    }

    @Test
    fun aFailedRefresh_isShownAboveTheList() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account), sent = sent, offline = true))

        assertTrue(binding.offlineText.shown)
        assertEquals(2, binding.sentList.childCount)
    }
}
