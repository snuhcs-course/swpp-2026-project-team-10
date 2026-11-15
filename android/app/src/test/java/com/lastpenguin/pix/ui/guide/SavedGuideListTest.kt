// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.SavedGuide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for My library on Add a pose guide (FR-3.8, FR-3.11), on the real layout: no saved guide (no count), one,
 * several (All with the count), and the list shrinking after a delete in the library (the count follows).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedGuideListTest {
    private fun sheet(): Pair<SheetAddGuideBinding, SavedGuideList> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = SheetAddGuideBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        return binding to SavedGuideList(binding)
    }

    private fun saved(id: String) = SavedGuide(id, savedAt = 1_761_190_000_000L, source = GuideSource.GALLERY)

    @Test
    fun render_noSavedGuide_showsNoCount() {
        val (binding, list) = sheet()

        list.render(emptyList())

        assertEquals(View.VISIBLE, binding.libraryRow.visibility)
        assertEquals(View.GONE, binding.libraryCount.visibility)
    }

    @Test
    fun render_one_andSeveral_countEvery() {
        val (binding, list) = sheet()

        list.render(listOf(saved("g1")))
        assertEquals(View.VISIBLE, binding.libraryCount.visibility)
        assertEquals("All 1", binding.libraryCount.text.toString())

        list.render((12 downTo 1).map { saved("g$it") })
        assertEquals("All 12", binding.libraryCount.text.toString())
    }

    @Test
    fun render_afterADelete_countsWhatIsLeft() {
        val (binding, list) = sheet()
        list.render(listOf(saved("g2"), saved("g1")))

        list.render(listOf(saved("g1")))

        assertEquals("All 1", binding.libraryCount.text.toString())
    }
}
