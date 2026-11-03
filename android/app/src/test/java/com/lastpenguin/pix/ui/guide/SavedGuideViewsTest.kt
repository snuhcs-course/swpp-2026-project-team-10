// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * A saved guide's thumbnail in a cell (FR-3.11, `showThumbnail`). Partitions: a thumbnail that arrives (shown), a cell
 * given another guide before the first thumbnail arrives, as a recycled cell is (the later guide's shows, never the
 * earlier one's, in whatever order they arrive), and one the store cannot read (the cell stays empty).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedGuideViewsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val image = ImageView(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = scope.cancel()

    /** A store whose thumbnails come only when the test completes them, one gate per guide. */
    private class GatedThumbnails : SavedGuideStore by SavedGuideStore.InMemory() {
        val gates = mutableMapOf<String, CompletableDeferred<Bitmap?>>()

        override suspend fun thumbnail(id: String): Bitmap? = gates.getOrPut(id) { CompletableDeferred() }.await()
    }

    private val store = GatedThumbnails()
    private val viewModel = ReferenceViewModel(TestAppContainer.ScriptedGuideMaker(), InMemoryGuideRepository(), store)

    private fun saved(id: String) = SavedGuide(id, savedAt = 0L, source = GuideSource.GALLERY)

    private fun shown(): Bitmap? = (image.drawable as? BitmapDrawable)?.bitmap

    @Test
    fun aThumbnailThatArrives_isShown() {
        val thumbnail = bitmap()
        image.showThumbnail(saved("g1"), viewModel, scope)
        idle()
        assertNull(shown())

        store.gates.getValue("g1").complete(thumbnail)
        idle()

        assertSame(thumbnail, shown())
    }

    @Test
    fun aCellGivenAnotherGuide_showsOnlyTheLaterGuidesThumbnail() {
        val first = bitmap()
        val second = bitmap()
        image.showThumbnail(saved("g1"), viewModel, scope)
        idle()
        image.showThumbnail(saved("g2"), viewModel, scope)
        idle()

        store.gates.getValue("g2").complete(second)
        idle()
        store.gates.getValue("g1").complete(first)
        idle()

        assertSame(second, shown())
    }

    @Test
    fun aThumbnailTheStoreCannotRead_leavesTheCellEmpty() {
        image.showThumbnail(saved("g1"), viewModel, scope)
        idle()

        store.gates.getValue("g1").complete(null)
        idle()

        assertNull(shown())
    }
}
