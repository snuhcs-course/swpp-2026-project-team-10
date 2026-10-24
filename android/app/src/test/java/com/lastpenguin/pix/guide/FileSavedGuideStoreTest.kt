// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for saved guides on the phone (FR-3.8, Design 2.8), with real files and real WebP encoding.
 * save: a new guide (listed first with a 3:4 thumbnail, its own folder of three files), the same guide again (one
 * copy), a guide without a reference photo (the cutout makes the thumbnail), an id that cannot name a folder, storage
 * that cannot be written, a bitmap that cannot be encoded (a failure, not a crash), and a clock set back between two
 * saves (the last one saved stays first).
 * Boundary of MAX_SAVED: the 20th is kept with the others; the 21st deletes the oldest from the list and the storage;
 * 21 folders found at a start keep the newest 20.
 * load: a saved guide (cutout at most 720 px tall, outline rebuilt, anchor, aspect, source, state, and reference as
 * saved), an unknown id, an unreadable cutout.
 * Restart (a new store on the same folder): the saved guides, newest first, and they load; unfinished, broken, and
 * unusable folders (an aspect of 0) are not listed and are deleted.
 * delete: a saved guide (gone from the list, the storage, and the next start), an unknown id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileSavedGuideStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000L

    private fun TestScope.store(directory: File = File(folder.root, "guides")): FileSavedGuideStore =
        FileSavedGuideStore(
            directory,
            this,
            io = StandardTestDispatcher(testScheduler),
            compute = StandardTestDispatcher(testScheduler),
            clock = { now++ },
        )

    @Test
    fun save_newGuides_listsTheNewestFirstWithSmallThumbnails() = runTest {
        val store = store()

        store.save(guide("g1"), GuideState()).getOrThrow()
        val saved = store.save(guide("g2"), GuideState()).getOrThrow()

        assertEquals(listOf("g2", "g1"), store.saved.value.map { it.id })
        assertEquals(saved, store.saved.value.first())
        // The 512 × 384 reference, cut to the list item's 3:4.
        val thumbnail = store.saved.value.first().thumbnail
        assertEquals(252, thumbnail.width)
        assertEquals(336, thumbnail.height)
    }

    @Test
    fun save_newGuide_writesCutoutReferenceAndMetaInItsOwnFolder() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)

        store.save(guide("g1"), GuideState()).getOrThrow()

        assertEquals(setOf("cutout.webp", "reference.webp", "meta.json"), File(directory, "g1").list()?.toSet())
        assertEquals(listOf("g1"), directory.list()?.toList())
    }

    @Test
    fun save_sameGuideAgain_keepsOneCopy() = runTest {
        val store = store()
        val first = store.save(guide("g1"), GuideState()).getOrThrow()

        val again = store.save(guide("g1"), GuideState(cx = 0.3f)).getOrThrow()

        assertEquals(first, again)
        assertEquals(listOf("g1"), store.saved.value.map { it.id })
    }

    @Test
    fun save_guideWithoutReference_makesTheThumbnailFromTheCutout() = runTest {
        val store = store()

        store.save(guide("g1", reference = null), GuideState()).getOrThrow()

        assertEquals(336, store.saved.value.single().thumbnail.height)
        assertNull(store.load("g1").getOrThrow().first.reference)
    }

    @Test
    fun save_idThatCannotNameAFolder_failsAndWritesNothing() = runTest {
        val store = store()

        val result = store.save(guide("../g1"), GuideState())

        assertTrue(result.isFailure)
        assertTrue(store.saved.value.isEmpty())
        assertFalse(File(folder.root, "g1").exists())
    }

    @Test
    fun save_storageCannotBeWritten_failsAndListsNothing() = runTest {
        // A file where the folder should be, so no guide folder can be made under it.
        val blocked = folder.newFile("guides")
        val store = store(blocked)

        val result = store.save(guide("g1"), GuideState())

        assertTrue(result.isFailure)
        assertTrue(store.saved.value.isEmpty())
    }

    @Test
    fun save_bitmapThatCannotBeEncoded_failsAndListsNothing() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        val broken = guide("g1").also { it.cutout.recycle() }

        val result = store.save(broken, GuideState())

        assertTrue(result.isFailure)
        assertTrue(store.saved.value.isEmpty())
        assertTrue(directory.list().isNullOrEmpty())
    }

    @Test
    fun save_clockSetBackBetweenTwoSaves_keepsTheLastSavedFirst() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        now = 5_000L
        store.save(guide("g1"), GuideState()).getOrThrow()
        now = 1_000L

        store.save(guide("g2"), GuideState()).getOrThrow()

        assertEquals(listOf("g2", "g1"), store.saved.value.map { it.id })
        val after = store(directory)
        advanceUntilIdle()
        assertEquals(listOf("g2", "g1"), after.saved.value.map { it.id })
    }

    @Test
    fun save_twentiethGuide_keepsEveryGuide() = runTest {
        val store = store()

        repeat(SavedGuideStore.MAX_SAVED) { store.save(smallGuide("g$it"), GuideState()).getOrThrow() }

        assertEquals(20, store.saved.value.size)
        assertEquals("g0", store.saved.value.last().id)
    }

    @Test
    fun save_twentyFirstGuide_deletesTheOldestFromListAndStorage() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        repeat(SavedGuideStore.MAX_SAVED) { store.save(smallGuide("g$it"), GuideState()).getOrThrow() }

        store.save(smallGuide("g20"), GuideState()).getOrThrow()

        assertEquals(20, store.saved.value.size)
        assertEquals("g20", store.saved.value.first().id)
        assertEquals("g1", store.saved.value.last().id)
        assertFalse(File(directory, "g0").exists())
    }

    @Test
    fun load_savedGuide_comesBackAsSavedWithItsOutlineRebuilt() = runTest {
        val store = store()
        val anchor = NormalizedRect(0.2f, 0.1f, 0.6f, 0.9f)
        val state = GuideState(guideId = "g1", cx = 0.4f, cy = 0.6f, height = 0.9f, opacity = 0.3f)
        store.save(guide("g1", anchor = anchor, source = GuideSource.GENERATED), state).getOrThrow()

        val (loaded, loadedState) = store.load("g1").getOrThrow()

        assertEquals("g1", loaded.id)
        // At most 720 px tall, as the subject receives it, with the figure still opaque on a clear background.
        assertEquals(720, loaded.cutout.height)
        assertEquals(240, loaded.cutout.width)
        assertEquals(0, Color.alpha(loaded.cutout.getPixel(2, 2)))
        assertEquals(255, Color.alpha(loaded.cutout.getPixel(120, 360)))
        assertEquals(240, loaded.outline.width)
        assertTrue("the outline is drawn", hasVisiblePixel(loaded.outline))
        assertEquals(300f / 900f, loaded.aspect, 0f)
        assertEquals(anchor, loaded.anchor)
        assertEquals(GuideSource.GENERATED, loaded.source)
        assertEquals(512, loaded.reference?.width)
        assertEquals(state, loadedState)
    }

    @Test
    fun load_unknownId_fails() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()

        assertTrue(store.load("g2").isFailure)
    }

    @Test
    fun load_unreadableCutout_fails() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1"), GuideState()).getOrThrow()
        File(directory, "g1/cutout.webp").writeText("not an image")

        assertTrue(store.load("g1").isFailure)
    }

    @Test
    fun restart_newStoreOnTheSameFolder_listsTheSavedGuidesNewestFirst() = runTest {
        val directory = File(folder.root, "guides")
        val before = store(directory)
        before.save(guide("g1"), GuideState()).getOrThrow()
        before.save(guide("g2"), GuideState()).getOrThrow()

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g2", "g1"), after.saved.value.map { it.id })
        assertEquals("g1", after.load("g1").getOrThrow().first.id)
    }

    @Test
    fun restart_unfinishedOrBrokenFolders_areNotListed() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        // A save that stopped before its rename, a folder without meta.json, and one whose meta.json is damaged.
        File(directory, ".g2").mkdirs()
        File(directory, "g3").mkdirs()
        File(directory, "g4").mkdirs()
        File(directory, "g1/cutout.webp").copyTo(File(directory, "g4/cutout.webp"))
        File(directory, "g4/meta.json").writeText("{\"savedAt\":")

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g1"), after.saved.value.map { it.id })
        assertFalse(File(directory, ".g2").exists())
        assertFalse(File(directory, "g3").exists())
        assertFalse(File(directory, "g4").exists())
    }

    @Test
    fun restart_metaWithAnAspectThatCannotPlaceAGuide_isLeftOutAndDeleted() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        val meta = File(directory, "g1/meta.json")
        meta.writeText(meta.readText().replace(Regex("\"aspect\":[^,]+"), "\"aspect\":0.0"))

        val after = store(directory)
        advanceUntilIdle()

        assertTrue(after.saved.value.isEmpty())
        assertFalse(File(directory, "g1").exists())
    }

    @Test
    fun restart_twentyOneFolders_keepsTheNewestTwentyAndDeletesTheOldest() = runTest {
        val directory = File(folder.root, "guides")
        val before = store(directory)
        repeat(SavedGuideStore.MAX_SAVED) { before.save(smallGuide("g$it"), GuideState()).getOrThrow() }
        // A 21st folder from before, as when an earlier delete stopped halfway: older than every other.
        File(directory, "g0").copyRecursively(File(directory, "old"))
        val oldMeta = File(directory, "old/meta.json")
        oldMeta.writeText(oldMeta.readText().replace(Regex("\"order\":\\d+"), "\"order\":0"))

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(SavedGuideStore.MAX_SAVED, after.saved.value.size)
        assertFalse("old" in after.saved.value.map { it.id })
        assertFalse(File(directory, "old").exists())
    }

    @Test
    fun delete_savedGuide_removesItFromTheListTheStorageAndTheNextStart() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1"), GuideState()).getOrThrow()
        store.save(guide("g2"), GuideState()).getOrThrow()

        store.delete("g1")

        assertEquals(listOf("g2"), store.saved.value.map { it.id })
        assertFalse(File(directory, "g1").exists())
        val after = store(directory)
        advanceUntilIdle()
        assertEquals(listOf("g2"), after.saved.value.map { it.id })
    }

    @Test
    fun delete_unknownId_changesNothing() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()

        store.delete("g2")

        assertEquals(listOf("g1"), store.saved.value.map { it.id })
        assertNotNull(store.load("g1").getOrNull())
    }

    /** A cutout 300×900 with an opaque figure on a clear background, and a 512×384 reference photo. */
    private fun guide(
        id: String,
        reference: Bitmap? = photo(512, 384),
        anchor: NormalizedRect? = NormalizedRect(0.1f, 0.1f, 0.5f, 0.9f),
        source: GuideSource = GuideSource.GALLERY,
    ): ReferenceGuide {
        val cutout = figure(300, 900)
        return ReferenceGuide(id, cutout, cutout, 300f / 900f, source, anchor, reference)
    }

    /** A guide small enough that twenty saves stay fast. */
    private fun smallGuide(id: String): ReferenceGuide {
        val cutout = figure(30, 90)
        return ReferenceGuide(id, cutout, cutout, 30f / 90f, GuideSource.GALLERY, reference = null)
    }

    private fun figure(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawOval(
            width * 0.2f,
            height * 0.1f,
            width * 0.8f,
            height * 0.9f,
            Paint().apply { color = Color.rgb(200, 120, 80) },
        )
        return bitmap
    }

    private fun photo(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 140, 200)) }

    private fun hasVisiblePixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 0 }
    }
}
