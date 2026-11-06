// AI-generated with Claude Code, 2026-11-03, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemSavedGuideBinding
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.SavedGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for My library on Add a pose guide (FR-3.8, FR-3.11), on the real layout.
 * No saved guide (Add photo alone, no All), a few (one tile each after Add photo, in the given order, each named by
 * its kind and when it was saved, with its kind's icon and its thumbnail asked for; All with the count), more than the
 * row holds (the newest ROW_SIZE, All with every guide's count), a tap on a tile (uses that guide), and the list
 * shrinking after a delete in the library (the removed tile goes).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedGuideListTest {
    private val used = mutableListOf<String>()
    private val thumbnails = mutableListOf<String>()

    private fun sheet(): Pair<SheetAddGuideBinding, SavedGuideList> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = SheetAddGuideBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        val list = SavedGuideList(binding, showThumbnail = { _, guide -> thumbnails += guide.id }, onUse = {
            used +=
                it.id
        })
        return binding to list
    }

    private fun saved(id: String, source: GuideSource = GuideSource.GALLERY) =
        SavedGuide(id, savedAt = 1_761_190_000_000L, source = source)

    private fun SheetAddGuideBinding.tile(index: Int): ItemSavedGuideBinding =
        ItemSavedGuideBinding.bind(savedGuidesList.getChildAt(index))

    @Test
    fun render_noSavedGuide_showsAddPhotoAlone() {
        val (binding, list) = sheet()

        list.render(emptyList())

        assertEquals(View.VISIBLE, binding.addPhotoTile.visibility)
        assertEquals(0, binding.savedGuidesList.childCount)
        assertEquals(View.GONE, binding.libraryAllButton.visibility)
    }

    @Test
    fun render_aFewSavedGuides_showsOneTileEachInOrderWithTheirKinds() {
        val (binding, list) = sheet()

        list.render(listOf(saved("g2", GuideSource.GENERATED), saved("g1")))

        assertEquals(2, binding.savedGuidesList.childCount)
        assertEquals(listOf("g2", "g1"), thumbnails)
        val generated = binding.tile(0).thumbnail.contentDescription.toString()
        assertTrue(generated, generated.startsWith("AI pose guide saved "))
        val photo = binding.tile(1).thumbnail.contentDescription.toString()
        assertTrue(photo, photo.startsWith("Photo guide saved "))
        assertEquals(R.drawable.ic_sparkle, shadowOf(binding.tile(0).kindIcon.drawable).createdFromResId)
        assertEquals(R.drawable.ic_image, shadowOf(binding.tile(1).kindIcon.drawable).createdFromResId)
        assertEquals(View.VISIBLE, binding.libraryAllButton.visibility)
        assertEquals("All 2", binding.libraryAllButton.text.toString())
    }

    @Test
    fun render_moreThanTheRowHolds_showsTheNewestAndCountsEvery() {
        val (binding, list) = sheet()
        val saved = (12 downTo 1).map { saved("g$it") }

        list.render(saved)

        assertEquals(SavedGuideList.ROW_SIZE, binding.savedGuidesList.childCount)
        assertEquals((12 downTo 3).map { "g$it" }, thumbnails)
        assertEquals("All 12", binding.libraryAllButton.text.toString())
    }

    @Test
    fun tapOnATile_usesThatGuide() {
        val (binding, list) = sheet()
        list.render(listOf(saved("g2"), saved("g1")))

        binding.tile(1).thumbnail.performClick()

        assertEquals(listOf("g1"), used)
    }

    @Test
    fun render_afterADelete_dropsTheRemovedTile() {
        val (binding, list) = sheet()
        list.render(listOf(saved("g2"), saved("g1")))

        list.render(listOf(saved("g1")))
        binding.tile(0).thumbnail.performClick()

        assertEquals(1, binding.savedGuidesList.childCount)
        assertEquals("All 1", binding.libraryAllButton.text.toString())
        assertEquals(listOf("g1"), used)
    }
}
