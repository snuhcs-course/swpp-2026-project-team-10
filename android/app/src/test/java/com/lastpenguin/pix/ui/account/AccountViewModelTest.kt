// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.account.FakeGoogleIdTokens
import com.lastpenguin.pix.account.GoogleSignIn
import com.lastpenguin.pix.account.GoogleSignInException
import com.lastpenguin.pix.account.GoogleSignInProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Partitions for Sign in, Sign up, Pix ID setup, and the Account sheet (FR-5.2, FR-5.3, FR-5.11–5.15, US-6). Log-in:
 * accepted (to Friends), refused (the error stays on the screen), an account without a Pix ID (to Pix ID setup).
 * Google: registered, NOT_REGISTERED (the question), the sheet closed (nothing shown), no Google account on the phone
 * (an error). The question: Yes (sign in, then Google is added; adding fails: the sign-in stands and says so), No (a new
 * account, then Pix ID setup), closed (the Google account is forgotten). Sign-up, Pix ID, name, Add Google, Add a
 * password, Sign out: each answered with its event. A second tap while a call runs does nothing. Check: asked (shown as
 * checking, beside a running sign-up), free, taken, failed (the reason kept), replaced by a newer check before it is
 * answered (only the newer one shows); a form that opens forgets the last check.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleIdTokens()
    private val activity = ContextWrapper(null)
    private lateinit var model: AccountViewModel
    private val events = mutableListOf<AccountEvent>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = AccountViewModel(auth, google)
        store.put("account", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.collectEvents() {
        backgroundScope.launch(dispatcher) { model.events.toList(events) }
        runCurrent()
    }

    @Test
    fun logIn_accepted_goesToFriends() = runTest {
        collectEvents()

        model.logIn("test1", "sunset2026")
        runCurrent()

        assertEquals(listOf(AccountEvent.SignedIn(needsPixId = false)), events)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun logIn_refused_keepsTheErrorOnTheScreen() = runTest {
        collectEvents()
        auth.failure = AccountException(AccountError.INVALID_CREDENTIALS)

        model.logIn("test1", "wrong1234")
        runCurrent()

        assertEquals(AccountError.INVALID_CREDENTIALS, (model.uiState.value.error as AccountException).error)
        assertTrue(events.isEmpty())

        model.clearError()
        assertNull(model.uiState.value.error)
    }

    @Test
    fun logIn_toAnAccountWithoutAPixId_goesToPixIdSetup() = runTest {
        collectEvents()
        auth.account = auth.account.copy(pixId = null)

        model.logIn("x", "y")
        runCurrent()

        assertEquals(listOf(AccountEvent.SignedIn(needsPixId = true)), events)
    }

    @Test
    fun google_registered_signsIn() = runTest {
        collectEvents()

        model.continueWithGoogle(activity)
        runCurrent()

        assertEquals(listOf("google:google-token"), auth.calls)
        assertEquals(listOf(AccountEvent.SignedIn(needsPixId = false)), events)
    }

    @Test
    fun google_closedByThePerson_showsNothing() = runTest {
        collectEvents()
        google.result = Result.failure(GoogleSignInException(GoogleSignInProblem.CANCELLED))

        model.continueWithGoogle(activity)
        runCurrent()

        assertNull(model.uiState.value.error)
        assertTrue(auth.calls.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun google_withoutAGoogleAccountOnThePhone_saysSo() = runTest {
        google.result = Result.failure(GoogleSignInException(GoogleSignInProblem.NO_ACCOUNT))

        model.continueWithGoogle(activity)
        runCurrent()

        assertEquals(GoogleSignInProblem.NO_ACCOUNT, (model.uiState.value.error as GoogleSignInException).problem)
    }

    @Test
    fun google_notRegistered_asksAboutAnExistingAccount() = runTest {
        collectEvents()
        auth.googleResult = GoogleSignIn.NotRegistered

        model.continueWithGoogle(activity)
        runCurrent()

        assertEquals(listOf(AccountEvent.AskExistingAccount), events)
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun existingAccount_yes_signsInAndAddsGoogle() = runTest {
        collectEvents()
        auth.googleResult = GoogleSignIn.NotRegistered
        model.continueWithGoogle(activity)
        runCurrent()

        model.answerExistingAccount(hasAccount = true)
        assertTrue(model.uiState.value.linkingGoogle)
        model.logIn("test1", "sunset2026")
        runCurrent()

        assertEquals(listOf("google:google-token", "logIn:test1", "addGoogle:google-token"), auth.calls)
        assertEquals(AccountEvent.SignedIn(needsPixId = false), events.last())
        assertFalse(model.uiState.value.linkingGoogle)
    }

    @Test
    fun existingAccount_yes_googleNotAdded_stillSignsIn() = runTest {
        collectEvents()
        auth.googleResult = GoogleSignIn.NotRegistered
        model.continueWithGoogle(activity)
        runCurrent()
        model.answerExistingAccount(hasAccount = true)
        val refused = AccountException(AccountError.GOOGLE_ALREADY_ADDED)
        auth.failures["addGoogle"] = refused

        model.logIn("test1", "sunset2026")
        runCurrent()

        assertEquals(
            listOf(AccountEvent.AskExistingAccount, AccountEvent.GoogleNotAdded(refused), AccountEvent.SignedIn(false)),
            events,
        )
        assertTrue(auth.state.value is AuthState.SignedIn)
    }

    @Test
    fun existingAccount_no_makesAnAccountThenAsksForAPixId() = runTest {
        collectEvents()
        auth.googleResult = GoogleSignIn.NotRegistered
        model.continueWithGoogle(activity)
        runCurrent()

        model.answerExistingAccount(hasAccount = false)
        runCurrent()

        assertEquals("googleSignUp:google-token", auth.calls.last())
        assertEquals(AccountEvent.SignedIn(needsPixId = true), events.last())
    }

    @Test
    fun existingAccount_closed_forgetsTheGoogleAccount() = runTest {
        auth.googleResult = GoogleSignIn.NotRegistered
        model.continueWithGoogle(activity)
        runCurrent()

        model.forgetGoogle()
        model.answerExistingAccount(hasAccount = false)
        runCurrent()

        assertEquals(listOf("google:google-token"), auth.calls)
    }

    @Test
    fun accountChanges_eachAnswerWithTheirEvent() = runTest {
        collectEvents()
        auth.signInAs(auth.account.copy(pixId = null, hasPassword = false, hasGoogle = true))

        model.signUp("test1", "Test Account 1", "sunset2026")
        runCurrent()
        model.savePixId("test2")
        runCurrent()
        model.saveDisplayName("Test Account 2")
        runCurrent()
        model.addPassword("sunset2026")
        runCurrent()
        model.addGoogle(activity)
        runCurrent()
        model.signOut()
        runCurrent()

        assertEquals(
            listOf(
                AccountEvent.SignedIn(needsPixId = false),
                AccountEvent.PixIdSaved,
                AccountEvent.NameSaved,
                AccountEvent.PasswordAdded,
                AccountEvent.GoogleAdded,
                AccountEvent.SignedOut,
            ),
            events,
        )
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun aSecondTap_whileACallRuns_doesNothing() = runTest {
        model.logIn("test1", "sunset2026")
        model.logIn("test1", "sunset2026")
        assertTrue(model.uiState.value.busy)
        runCurrent()

        assertEquals(1, auth.calls.size)
    }

    @Test
    fun checkPixId_showsCheckingThenTheAnswer() = runTest {
        auth.checkGate = CompletableDeferred()

        model.checkPixId("test5")
        runCurrent()

        assertEquals(PixIdCheck("test5"), model.uiState.value.pixIdCheck)
        assertTrue(model.uiState.value.pixIdCheck!!.checking)
        assertFalse(model.uiState.value.busy)

        auth.checkGate!!.complete(Unit)
        runCurrent()

        assertEquals(PixIdCheck("test5", available = true), model.uiState.value.pixIdCheck)
    }

    @Test
    fun checkPixId_taken_isNotAvailable() = runTest {
        model.checkPixId("test1")
        runCurrent()

        assertEquals(PixIdCheck("test1", available = false), model.uiState.value.pixIdCheck)
    }

    @Test
    fun checkPixId_failed_keepsTheReason() = runTest {
        auth.failures["check"] = AccountException(AccountError.OFFLINE)

        model.checkPixId("test5")
        runCurrent()

        val check = model.uiState.value.pixIdCheck!!
        assertEquals(AccountError.OFFLINE, check.error?.error)
        assertNull(check.available)
        assertFalse(check.checking)
    }

    @Test
    fun checkPixId_runsBesideASignUp() = runTest {
        auth.checkGate = CompletableDeferred()
        model.checkPixId("test5")
        model.signUp("test5", "Test Account 5", "testpassword5")
        runCurrent()

        assertTrue("signUp:test5" in auth.calls)
        assertTrue(model.uiState.value.pixIdCheck!!.checking)
    }

    @Test
    fun checkPixId_aNewerCheck_replacesOneStillWaiting() = runTest {
        auth.checkGate = CompletableDeferred()
        model.checkPixId("test5")
        runCurrent()

        model.checkPixId("test1")
        auth.checkGate!!.complete(Unit)
        runCurrent()

        assertEquals(PixIdCheck("test1", available = false), model.uiState.value.pixIdCheck)
    }

    @Test
    fun startForm_forgetsTheCheckAndTheError() = runTest {
        auth.failure = AccountException(AccountError.OFFLINE)
        model.logIn("test1", "sunset2026")
        model.checkPixId("test5")
        runCurrent()

        model.startForm()

        assertNull(model.uiState.value.pixIdCheck)
        assertNull(model.uiState.value.error)
    }
}
