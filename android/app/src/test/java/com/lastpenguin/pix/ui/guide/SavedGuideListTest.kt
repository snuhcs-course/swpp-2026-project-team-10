// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.databinding.ItemSavedGuideBinding
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.SavedGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for the saved guides on Add a pose guide (FR-3.8), on the real layout.
 * No saved guide (the section is hidden), several (one tile each, in the given order, each named by when it was
 * saved), a tap on a tile (uses that guide), a tap on a tile's ✕ (asks to delete that guide), and the list shrinking
 * after a delete (the removed tile goes).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedGuideListTest {
    private val used = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    private fun sheet(): Pair<SheetAddGuideBinding, SavedGuideList> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = SheetAddGuideBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        return binding to SavedGuideList(binding, onUse = { used += it.id }, onDelete = { deleted += it.id })
    }

    private fun saved(id: String, savedAt: Long = 1_761_190_000_000L) =
        SavedGuide(id, savedAt, Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888))

    private fun SheetAddGuideBinding.tile(index: Int): ItemSavedGuideBinding =
        ItemSavedGuideBinding.bind(savedGuidesList.getChildAt(index))

    @Test
    fun render_noSavedGuide_hidesTheSection() {
        val (binding, list) = sheet()

        list.render(emptyList())

        assertEquals(View.GONE, binding.savedGuidesTitle.visibility)
        assertEquals(View.GONE, binding.savedGuidesScroll.visibility)
    }

    @Test
    fun render_severalSavedGuides_showsOneTileEachInOrder() {
        val (binding, list) = sheet()

        list.render(listOf(saved("g2"), saved("g1")))

        assertEquals(View.VISIBLE, binding.savedGuidesTitle.visibility)
        assertEquals(View.VISIBLE, binding.savedGuidesScroll.visibility)
        assertEquals(2, binding.savedGuidesList.childCount)
        val description = binding.tile(0).thumbnail.contentDescription.toString()
        assertTrue(description, description.startsWith("Saved guide from "))
        assertTrue(binding.tile(0).deleteButton.contentDescription.startsWith("Delete the saved guide from "))
    }

    @Test
    fun tapOnATile_usesThatGuide() {
        val (binding, list) = sheet()
        list.render(listOf(saved("g2"), saved("g1")))

        binding.tile(1).thumbnail.performClick()

        assertEquals(listOf("g1"), used)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun tapOnATilesDelete_asksToDeleteThatGuide() {
        val (binding, list) = sheet()
        list.render(listOf(saved("g2"), saved("g1")))

        binding.tile(0).deleteButton.performClick()

        assertEquals(listOf("g2"), deleted)
        assertTrue(used.isEmpty())
    }

    @Test
    fun render_afterADelete_dropsTheRemovedTile() {
        val (binding, list) = sheet()
        list.render(listOf(saved("g2"), saved("g1")))

        list.render(listOf(saved("g1")))
        binding.tile(0).thumbnail.performClick()

        assertEquals(1, binding.savedGuidesList.childCount)
        assertEquals(listOf("g1"), used)
    }
}
