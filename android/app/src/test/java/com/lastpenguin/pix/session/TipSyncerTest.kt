// AI-generated with Claude Code, 2026-11-17, reviewed by Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The tip of the guide on the photographer's camera on the subject's phone (FR-4.10, #213; Design 2.5.1), with the
 * fake session of ModuleFakes.kt.
 * Photographer, the tip (equivalence partitioning of what the guide has): a tip that is there goes with its guide and
 * its text; none, one being asked for, and one that failed go as no tip.
 * Photographer, changes (state-based): the same tip goes once; another guide's tip replaces it; a tip that goes away
 * is cleared once; the words asked again in another language go as the new tip.
 * Photographer, the connection: a sender started later (a reconnect) says what the guide has now, a tip or none, so
 * the subject never keeps a tip that went during the drop.
 * Subject, the copy (state-based): a tip is kept with its guide; a newer one replaces it; a clear drops it; it stays
 * while the receiver runs, as through a reconnect, and goes with the session.
 * Subject, what a tip holds (equivalence partitioning): its text, without the space around it; a tip without words is
 * no tip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TipSyncerTest {

    private val session = FakeSessionManager()
    private val onCamera = MutableStateFlow<GuideTip?>(null)

    private fun syncer() = TipSyncer(session, onCamera)

    private val railing = PoseTip("Move a little closer.\nHold the phone at your waist, tilted up a little.")
    private val steps = PoseTip("Take it from where you are, as it is.")

    private fun ready(guideId: String, tip: PoseTip) = GuideTip(guideId, TipState.Ready(tip))

    private fun message(guideId: String, tip: PoseTip) = SessionMessage.GuideTip(guideId, tip.text)

    /** A scope of its own under the test's, which a test ends as a reconnect or the session's end does. */
    private fun TestScope.part(): CoroutineScope = backgroundScope + Job(backgroundScope.coroutineContext[Job])

    private fun TestScope.sending(): TipSyncer {
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        return syncer
    }

    private fun TestScope.receiving(): TipSyncer {
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        return syncer
    }

    // ---- photographer: the tip -----------------------------------------------------------------------------------

    @Test
    fun `a tip goes with its guide and its text`() = runTest {
        onCamera.value = ready("g1", railing)
        sending()

        assertEquals(listOf<SessionMessage>(SessionMessage.GuideTip("g1", railing.text)), session.sent)
    }

    @Test
    fun `no tip, a tip being asked for, and a failed one go as no tip`() = runTest {
        for (none in listOf(null, GuideTip("g1", TipState.Preparing), GuideTip("g1", TipState.Failed))) {
            session.sent.clear()
            onCamera.value = none
            val sender = part()
            syncer().startAsSender(sender)
            runCurrent()
            assertEquals("$none", listOf<SessionMessage>(SessionMessage.GuideTipClear), session.sent)
            sender.cancel()
        }
    }

    // ---- photographer: changes -----------------------------------------------------------------------------------

    @Test
    fun `a tip that arrives after its guide is sent once`() = runTest {
        onCamera.value = GuideTip("g1", TipState.Preparing)
        sending()
        onCamera.value = ready("g1", railing)
        runCurrent()
        // The same tip published again, as the tips do when another guide's request ends.
        onCamera.value = GuideTip("g1", TipState.Ready(railing.copy()))
        runCurrent()

        assertEquals(listOf(SessionMessage.GuideTipClear, message("g1", railing)), session.sent)
    }

    @Test
    fun `another guide's tip replaces the one sent`() = runTest {
        onCamera.value = ready("g1", railing)
        sending()
        onCamera.value = ready("g2", steps)
        runCurrent()

        assertEquals(listOf(message("g1", railing), message("g2", steps)), session.sent)
    }

    @Test
    fun `a tip that goes away is cleared once`() = runTest {
        onCamera.value = ready("g1", railing)
        sending()
        // A saved copy of the guide takes its place: the same id, and no tip.
        onCamera.value = null
        runCurrent()
        onCamera.value = GuideTip("g2", TipState.Preparing)
        runCurrent()
        onCamera.value = GuideTip("g2", TipState.Failed)
        runCurrent()

        assertEquals(listOf(message("g1", railing), SessionMessage.GuideTipClear), session.sent)
    }

    @Test
    fun `a tip asked again in another language goes as the new words`() = runTest {
        onCamera.value = ready("g1", railing)
        sending()
        val korean = PoseTip("조금 다가가세요.")
        onCamera.value = GuideTip("g1", TipState.Preparing)
        runCurrent()
        onCamera.value = ready("g1", korean)
        runCurrent()

        assertEquals(
            listOf(message("g1", railing), SessionMessage.GuideTipClear, message("g1", korean)),
            session.sent,
        )
    }

    // ---- photographer: the connection ----------------------------------------------------------------------------

    @Test
    fun `a sender started later says what the guide has now`() = runTest {
        onCamera.value = ready("g1", railing)
        val syncer = syncer()
        val first = part()
        syncer.startAsSender(first)
        runCurrent()
        first.cancel()
        runCurrent()

        // The link dropped and came back with the same tip: the subject may have missed it.
        val second = part()
        syncer.startAsSender(second)
        runCurrent()
        assertEquals(listOf(message("g1", railing), message("g1", railing)), session.sent)
        second.cancel()
        runCurrent()

        // The tip went during the next drop: the subject is told, or it would keep the old one.
        onCamera.value = null
        syncer.startAsSender(backgroundScope)
        runCurrent()
        assertEquals(SessionMessage.GuideTipClear, session.sent.last())
        assertEquals(3, session.sent.size)
    }

    // ---- subject: the copy ---------------------------------------------------------------------------------------

    @Test
    fun `a tip is kept with its guide, and a newer one replaces it`() = runTest {
        val syncer = receiving()
        assertNull(syncer.mirror.value)

        session.deliver(message("g1", railing))
        runCurrent()
        assertEquals(SharedTip("g1", railing), syncer.mirror.value)

        session.deliver(message("g2", steps))
        runCurrent()
        assertEquals(SharedTip("g2", steps), syncer.mirror.value)
    }

    @Test
    fun `a clear drops the tip`() = runTest {
        val syncer = receiving()
        session.deliver(message("g1", railing))
        session.deliver(SessionMessage.GuideTipClear)
        runCurrent()

        assertNull(syncer.mirror.value)
    }

    @Test
    fun `the tip stays while the receiver runs and goes with the session`() = runTest {
        val syncer = syncer()
        val receiver = part()
        syncer.startAsReceiver(receiver)
        runCurrent()
        session.deliver(message("g1", railing))
        runCurrent()

        // A drop of the link changes the session's state and nothing here: the receiver runs through it.
        session.setState(SessionState.Reconnecting("s_1", since = 0L))
        runCurrent()
        assertEquals(SharedTip("g1", railing), syncer.mirror.value)

        receiver.cancel()
        runCurrent()
        assertNull(syncer.mirror.value)
    }

    @Test
    fun `an older receiver's end does not drop the tip of the session after it`() = runTest {
        val syncer = syncer()
        val old = part()
        syncer.startAsReceiver(old)
        runCurrent()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        session.deliver(message("g1", railing))
        runCurrent()

        old.cancel()
        runCurrent()
        assertEquals(SharedTip("g1", railing), syncer.mirror.value)
    }

    // ---- subject: what a tip holds -------------------------------------------------------------------------------

    @Test
    fun `a tip is kept as its text, and one without words is no tip`() = runTest {
        val syncer = receiving()
        session.deliver(SessionMessage.GuideTip("g1", "  Move a little closer.\nStep to the side.  "))
        runCurrent()
        assertEquals(SharedTip("g1", PoseTip("Move a little closer.\nStep to the side.")), syncer.mirror.value)

        session.deliver(SessionMessage.GuideTip("g1", " \n "))
        runCurrent()
        assertNull(syncer.mirror.value)
    }
}
