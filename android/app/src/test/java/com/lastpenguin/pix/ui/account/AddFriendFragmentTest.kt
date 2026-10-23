// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.account

import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.Relation
import com.lastpenguin.pix.account.RequestOutcome
import com.lastpenguin.pix.account.UserLookup
import com.lastpenguin.pix.databinding.FragmentAddFriendBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * Add a friend on the real Fragment (R&S 6.4; FR-5.5, FR-5.6, FR-5.8, US-7), with the friends fake behind the
 * ViewModel; `AccountFormsTest` covers the form's filter and labels. Partitions here: a search that finds a user
 * (Add sends the request and the result reads Requested, Withdraw takes it back), one who already asked (Accept),
 * no user (No user found, then Search again with the text kept), and a failed search; a failed Add as a message;
 * Back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddFriendFragmentTest {
    private var scenario: FragmentScenario<AddFriendFragment>? = null
    private lateinit var nav: TestNavHostController

    private val user = PixUser("u_2", "test2", "Test Account 2")

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        scenario = launchFragmentInContainer<AddFriendFragment>(themeResId = R.style.Theme_Pix)
        nav = scenario!!.attachNav(R.id.addFriendFragment)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentAddFriendBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentAddFriendBinding.bind(it.requireView())) }
        return result!!
    }

    private fun search(text: String) {
        onScreen { binding ->
            binding.pixIdInput.setText(text)
            binding.searchButton.performClick()
        }
        idle()
    }

    @Test
    fun aFoundUser_isAdded_andTheRequestWithdrawn() {
        val modules = TestAppContainer()
        modules.friends.lookup = UserLookup.Found(user, Relation.NONE, null)
        launch(modules)

        search("Test2")
        assertTrue(modules.friends.calls.contains("search:test2"))
        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.resultSection.visibility)
            assertEquals(string(R.string.add), binding.resultButton.text.toString())
            binding.resultButton.performClick()
        }
        idle()

        assertTrue(modules.friends.calls.contains("request:u_2"))
        onScreen { binding ->
            assertEquals(string(R.string.requested), binding.relationText.text.toString())
            assertEquals(View.VISIBLE, binding.withdrawButton.visibility)
            binding.withdrawButton.performClick()
        }
        idle()
        assertTrue(modules.friends.calls.contains("cancel:fr_1"))
        onScreen { assertEquals(View.GONE, it.withdrawButton.visibility) }
    }

    @Test
    fun aUserWhoAlreadyAsked_isAccepted() {
        val modules = TestAppContainer()
        modules.friends.lookup = UserLookup.Found(user, Relation.INCOMING, "fr_80")
        launch(modules)

        search("test2")
        onScreen { binding ->
            assertEquals(string(R.string.accept), binding.resultButton.text.toString())
            binding.resultButton.performClick()
        }
        idle()

        assertTrue(modules.friends.calls.contains("accept:fr_80"))
        onScreen { assertEquals(string(R.string.already_friends), it.relationText.text.toString()) }
    }

    @Test
    fun noUser_thenSearchAgainKeepsTheText() {
        launch()

        search("nobody")
        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.notFoundSection.visibility)
            assertEquals(string(R.string.search_again), binding.searchButton.text.toString())
            binding.searchButton.performClick()
        }
        idle()

        onScreen { binding ->
            assertEquals(View.GONE, binding.notFoundSection.visibility)
            assertEquals("nobody", binding.pixIdInput.text.toString())
            assertEquals(string(R.string.search), binding.searchButton.text.toString())
        }
    }

    @Test
    fun aFailedSearch_showsWhy_andAFailedAdd_isAMessage() {
        val modules = TestAppContainer()
        modules.friends.failure = AccountException(AccountError.OFFLINE)
        launch(modules)

        search("test2")
        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.statusText.visibility)
            assertEquals(string(R.string.offline_error), binding.statusText.text.toString())
        }

        modules.friends.failure = null
        modules.friends.lookup = UserLookup.Found(user, Relation.NONE, null)
        modules.friends.outcome = RequestOutcome.Sent("fr_2")
        search("test2")
        modules.friends.failure = AccountException(AccountError.UNAVAILABLE)
        onScreen { it.resultButton.performClick() }
        idle()

        assertEquals(string(R.string.unavailable_error), ShadowToast.getTextOfLatestToast())
        onScreen { assertEquals(string(R.string.add), it.resultButton.text.toString()) }
    }

    @Test
    fun back_leavesTheScreen() {
        launch()

        onScreen { it.backButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
