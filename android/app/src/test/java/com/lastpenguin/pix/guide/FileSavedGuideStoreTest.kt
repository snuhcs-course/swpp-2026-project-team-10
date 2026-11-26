// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han and Sungmin Jo
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.generation.Situation
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
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
 * Partitions for saved guides on the phone (FR-3.8, FR-3.11, Design 2.8), with real files and real image encoding.
 * save: a new guide (listed first with its kind, its own folder of three files), the same guide again (one copy), an
 * id that cannot name a folder, storage that cannot be written, a bitmap that cannot be encoded (a failure, not a
 * crash), and a clock set back between two saves (the last one saved stays first).
 * thumbnail: a landscape reference (its middle cut to 3:4, not enlarged), a reference taller than the limit (cut and
 * scaled to 384 × 512), a guide without a reference (the outline, cut to 3:4), an unreadable reference (the outline),
 * an unreadable mask without a reference (null), an unknown id (null).
 * No limit (#297, boundary of the old 100): a 101st guide, and 101 folders found at a start, are all kept.
 * Favorites (#297): a favorite and history, each listed so and kept after a restart; a guide kept as history saved again
 * (still history); setFavorite marking and unmarking (kept after a restart, with its memo and situation), an unknown id
 * (a failure); a folder saved before the history (a favorite). delete: several at once, with an unknown id.
 * load: a saved guide (mask at most 720 px tall, outline rebuilt, anchor, aspect, source, state, and reference as
 * saved), an unknown id, an unreadable mask.
 * Migration from before the mask-only transfer (#113): a folder with `cutout.webp` and no `mask.png` is listed,
 * gets `mask.png` from the cutout's alpha, loses the cutout, and loads; one whose cutout cannot be read is deleted;
 * one left with both, by a move that stopped before the delete, loses the cutout and keeps its mask.
 * Restart (a new store on the same folder): the saved guides, newest first with their kinds, and they load; unfinished,
 * broken, and unusable folders (an aspect of 0) are not listed and are deleted; a folder whose mask cannot be decoded is
 * listed, since a start reads no image, and fails when used.
 * delete: a saved guide (gone from the list, the storage, and the next start), an unknown id.
 * Situation and memo (#270): a generated pose's situation, listed and loaded, and still there after a restart; a photo
 * guide's none; a folder saved before them, which has neither; a situation name this phone does not know (none). setMemo:
 * a memo (listed, kept after a restart, with the guide still loading), an empty one (removed), one longer than
 * MAX_MEMO (cut), one with line breaks and spaces around it (one line), an unknown id (a failure, nothing written), a
 * folder that cannot be written (a failure, the memo as it was).
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
    fun save_newGuides_listsTheNewestFirstWithTheirKinds() = runTest {
        val store = store()

        store.save(guide("g1"), GuideState()).getOrThrow()
        val saved = store.save(guide("g2", source = GuideSource.GENERATED), GuideState()).getOrThrow()

        assertEquals(listOf("g2", "g1"), store.saved.value.map { it.id })
        assertEquals(saved, store.saved.value.first())
        assertEquals(listOf(GuideSource.GENERATED, GuideSource.GALLERY), store.saved.value.map { it.source })
    }

    @Test
    fun thumbnail_landscapeReference_isItsMiddleCutTo3x4() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()

        val thumbnail = store.thumbnail("g1")

        // The 512 × 384 reference, cut to 3:4 at its own height: smaller than the limit, and not enlarged.
        assertEquals(288 to 384, thumbnail?.width to thumbnail?.height)
    }

    @Test
    fun thumbnail_referenceTallerThanTheLimit_isCutAndScaledDown() = runTest {
        val store = store()
        store.save(guide("g1", reference = photo(800, 1200)), GuideState()).getOrThrow()

        val thumbnail = store.thumbnail("g1")

        assertEquals(
            SavedGuideStore.THUMBNAIL_WIDTH to SavedGuideStore.THUMBNAIL_HEIGHT,
            thumbnail?.width to thumbnail?.height,
        )
    }

    @Test
    fun thumbnail_unreadableReference_isTheOutline() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1"), GuideState()).getOrThrow()
        File(directory, "g1/reference.webp").writeText("not an image")

        val thumbnail = store.thumbnail("g1")

        // The mask kept 720 px tall, 240 wide, so its outline cut to 3:4 is 240 × 320.
        assertEquals(240 to 320, thumbnail?.width to thumbnail?.height)
        assertTrue("the outline is drawn", hasVisiblePixel(checkNotNull(thumbnail)))
    }

    @Test
    fun thumbnail_unreadableMaskWithoutReference_isNull() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1", reference = null), GuideState()).getOrThrow()
        File(directory, "g1/mask.png").writeText("not an image")

        assertNull(store.thumbnail("g1"))
    }

    @Test
    fun thumbnail_unknownId_isNull() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()

        assertNull(store.thumbnail("g2"))
    }

    @Test
    fun save_newGuide_writesMaskReferenceAndMetaInItsOwnFolder() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)

        store.save(guide("g1"), GuideState()).getOrThrow()

        assertEquals(setOf("mask.png", "reference.webp", "meta.json"), File(directory, "g1").list()?.toSet())
        assertNotNull(MaskPng.decode(File(directory, "g1/mask.png").readBytes()))
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
    fun save_guideWithoutReference_makesTheThumbnailFromTheOutline() = runTest {
        val store = store()

        store.save(guide("g1", reference = null), GuideState()).getOrThrow()

        val thumbnail = checkNotNull(store.thumbnail("g1"))
        assertEquals(240 to 320, thumbnail.width to thumbnail.height)
        assertTrue("the outline is drawn", hasVisiblePixel(thumbnail))
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
        val broken = guide("g1").also { it.mask.recycle() }

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
    fun save_hundredAndFirstGuide_keepsEveryGuide() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)

        repeat(101) { store.save(smallGuide("g$it"), GuideState(), favorite = false).getOrThrow() }

        // No limit any longer (#297): nothing goes unless the user deletes it.
        assertEquals(101, store.saved.value.size)
        assertEquals("g0", store.saved.value.last().id)
        assertTrue(File(directory, "g0").exists())
    }

    @Test
    fun save_favoriteAndHistory_listsEachAsSavedAndKeepsItAfterARestart() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)

        store.save(guide("pose"), GuideState(), favorite = false).getOrThrow()
        store.save(guide("photo"), GuideState()).getOrThrow()
        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf(true, false), store.saved.value.map { it.favorite })
        assertEquals(listOf(true, false), after.saved.value.map { it.favorite })
    }

    @Test
    fun save_guideKeptAsHistory_staysHistoryWhenSavedAgain() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState(), favorite = false).getOrThrow()

        val again = store.save(guide("g1"), GuideState(), favorite = true).getOrThrow()

        assertFalse("a second save changes nothing; setFavorite does", again.favorite)
    }

    @Test
    fun setFavorite_marksAndUnmarks_andKeepsItAfterARestart() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1", situation = Situation.SELFIE), GuideState(), favorite = false).getOrThrow()
        store.setMemo("g1", "kept").getOrThrow()

        store.setFavorite("g1", true).getOrThrow()
        val marked = store(directory)
        advanceUntilIdle()
        store.setFavorite("g1", false).getOrThrow()
        val unmarked = store(directory)
        advanceUntilIdle()

        assertTrue(marked.saved.value.single().favorite)
        assertFalse(unmarked.saved.value.single().favorite)
        assertEquals("kept" to Situation.SELFIE, unmarked.saved.value.single().let { it.memo to it.situation })
        assertTrue(store.setFavorite("g2", true).isFailure)
    }

    @Test
    fun restart_folderSavedBeforeTheHistory_isAFavorite() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState(), favorite = true).getOrThrow()
        // A guide saved before #297 was saved by ♥ or *Save guide*: its meta.json says nothing of favorites.
        assertFalse("favorite" in File(directory, "g1/meta.json").readText())

        val after = store(directory)
        advanceUntilIdle()

        assertTrue(after.saved.value.single().favorite)
    }

    @Test
    fun delete_severalAtOnce_removesThemFromTheListAndTheStorage() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        listOf("g1", "g2", "g3").forEach { store.save(smallGuide(it), GuideState()).getOrThrow() }

        store.delete(listOf("g1", "g3", "unknown"))

        assertEquals(listOf("g2"), store.saved.value.map { it.id })
        assertFalse(File(directory, "g1").exists())
        assertFalse(File(directory, "g3").exists())
        assertTrue(File(directory, "g2").exists())
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
        assertEquals(Bitmap.Config.ALPHA_8, loaded.mask.config)
        assertEquals(720, loaded.mask.height)
        assertEquals(240, loaded.mask.width)
        assertEquals(0, Color.alpha(loaded.mask.getPixel(2, 2)))
        assertEquals(255, Color.alpha(loaded.mask.getPixel(120, 360)))
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
    fun load_unreadableMask_fails() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1"), GuideState()).getOrThrow()
        File(directory, "g1/mask.png").writeText("not an image")

        assertTrue(store.load("g1").isFailure)
    }

    @Test
    fun restart_newStoreOnTheSameFolder_listsTheSavedGuidesNewestFirstWithTheirKinds() = runTest {
        val directory = File(folder.root, "guides")
        val before = store(directory)
        before.save(guide("g1"), GuideState()).getOrThrow()
        before.save(guide("g2", source = GuideSource.GENERATED), GuideState()).getOrThrow()

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g2", "g1"), after.saved.value.map { it.id })
        assertEquals(listOf(GuideSource.GENERATED, GuideSource.GALLERY), after.saved.value.map { it.source })
        assertEquals("g1", after.load("g1").getOrThrow().first.id)
        assertNotNull(after.thumbnail("g1"))
    }

    @Test
    fun restart_folderWithAMaskThatCannotBeDecoded_isListedAndFailsWhenUsed() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        File(directory, "g1/mask.png").writeText("not an image")

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g1"), after.saved.value.map { it.id })
        assertTrue(after.load("g1").isFailure)
    }

    @Test
    fun restart_unfinishedOrBrokenFolders_areNotListed() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        // A save that stopped before its rename, a folder without meta.json, and one whose meta.json is damaged.
        File(directory, ".g2").mkdirs()
        File(directory, "g3").mkdirs()
        File(directory, "g4").mkdirs()
        File(directory, "g1/mask.png").copyTo(File(directory, "g4/mask.png"))
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
    fun restart_hundredAndOneFolders_keepsThemAll() = runTest {
        val directory = File(folder.root, "guides")
        val before = store(directory)
        repeat(101) { before.save(smallGuide("g$it"), GuideState(), favorite = false).getOrThrow() }

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(101, after.saved.value.size)
        assertTrue(File(directory, "g0").exists())
    }

    @Test
    fun restart_folderWithAnOldCutout_getsAMaskInItsPlaceAndLoads() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        oldCutout(File(directory, "g1"))

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g1"), after.saved.value.map { it.id })
        assertEquals(setOf("mask.png", "reference.webp", "meta.json"), File(directory, "g1").list()?.toSet())
        val loaded = after.load("g1").getOrThrow().first
        assertEquals(240 to 720, loaded.mask.width to loaded.mask.height)
        assertEquals(0, Color.alpha(loaded.mask.getPixel(2, 2)))
        assertEquals(255, Color.alpha(loaded.mask.getPixel(120, 360)))
        assertTrue("the outline is drawn", hasVisiblePixel(loaded.outline))
    }

    @Test
    fun restart_folderWithAMaskAndAnOldCutout_losesTheCutoutAndKeepsItsMask() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        val mask = File(directory, "g1/mask.png").readBytes()
        File(directory, "g1/cutout.webp").writeText("the reference's pixels")

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g1"), after.saved.value.map { it.id })
        assertEquals(setOf("mask.png", "reference.webp", "meta.json"), File(directory, "g1").list()?.toSet())
        assertArrayEquals(mask, File(directory, "g1/mask.png").readBytes())
        assertNotNull(after.load("g1").getOrNull())
    }

    @Test
    fun restart_folderWithAnUnreadableOldCutout_isLeftOutAndDeleted() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        File(directory, "g1/mask.png").delete()
        File(directory, "g1/cutout.webp").writeText("not an image")

        val after = store(directory)
        advanceUntilIdle()

        assertTrue(after.saved.value.isEmpty())
        assertFalse(File(directory, "g1").exists())
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

    @Test
    fun save_generatedPoseWithASituation_listsAndLoadsItAfterARestart() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(
            guide("g1", source = GuideSource.GENERATED, situation = Situation.LANDMARK),
            GuideState(),
        ).getOrThrow()
        store.save(guide("g2"), GuideState()).getOrThrow()

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf(null, Situation.LANDMARK), after.saved.value.map { it.situation })
        assertEquals(Situation.LANDMARK, after.load("g1").getOrThrow().first.situation)
        assertNull(after.load("g2").getOrThrow().first.situation)
    }

    @Test
    fun restart_folderSavedBeforeSituationsAndMemos_hasNeither() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1"), GuideState()).getOrThrow()
        val meta = File(directory, "g1/meta.json")
        assertFalse("an empty memo and no situation are not written", "memo" in meta.readText())
        assertFalse("situation" in meta.readText())

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(
            listOf(SavedGuide("g1", after.saved.value.single().savedAt, GuideSource.GALLERY)),
            after.saved.value,
        )
    }

    @Test
    fun restart_situationThisPhoneDoesNotKnow_readsAsNone() = runTest {
        val directory = File(folder.root, "guides")
        store(directory).save(guide("g1", situation = Situation.BACKGROUND), GuideState()).getOrThrow()
        val meta = File(directory, "g1/meta.json")
        meta.writeText(meta.readText().replace("\"background\"", "\"underwater\""))

        val after = store(directory)
        advanceUntilIdle()

        assertEquals(listOf("g1"), after.saved.value.map { it.id })
        assertNull(after.saved.value.single().situation)
    }

    @Test
    fun setMemo_savedGuide_isListedAndKeptAfterARestart() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1", situation = Situation.HALF_BODY), GuideState()).getOrThrow()

        assertTrue(store.setMemo("g1", "Mom at the beach").isSuccess)
        val after = store(directory)
        advanceUntilIdle()

        assertEquals("Mom at the beach", store.saved.value.single().memo)
        assertEquals("Mom at the beach", after.saved.value.single().memo)
        assertEquals(Situation.HALF_BODY, after.saved.value.single().situation)
        assertNotNull(after.load("g1").getOrNull())
        assertEquals(setOf("mask.png", "reference.webp", "meta.json"), File(directory, "g1").list()?.toSet())
    }

    @Test
    fun setMemo_empty_removesIt() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()
        store.setMemo("g1", "first").getOrThrow()

        store.setMemo("g1", "   ").getOrThrow()

        assertEquals("", store.saved.value.single().memo)
    }

    @Test
    fun setMemo_longerThanTheLimit_isCut() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()

        store.setMemo("g1", "a".repeat(SavedGuideStore.MAX_MEMO + 1)).getOrThrow()

        assertEquals("a".repeat(SavedGuideStore.MAX_MEMO), store.saved.value.single().memo)
    }

    @Test
    fun setMemo_lineBreaksAndSpacesAround_isOneLine() = runTest {
        val store = store()
        store.save(guide("g1"), GuideState()).getOrThrow()

        store.setMemo("g1", "  Seoul\n tower  ").getOrThrow()

        assertEquals("Seoul tower", store.saved.value.single().memo)
    }

    @Test
    fun setMemo_unknownId_failsAndWritesNothing() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1"), GuideState()).getOrThrow()

        assertTrue(store.setMemo("g2", "memo").isFailure)
        assertFalse(File(directory, "g2").exists())
        assertEquals("", store.saved.value.single().memo)
    }

    @Test
    fun setMemo_folderThatCannotBeWritten_failsAndKeepsTheMemo() = runTest {
        val directory = File(folder.root, "guides")
        val store = store(directory)
        store.save(guide("g1"), GuideState()).getOrThrow()
        store.setMemo("g1", "kept").getOrThrow()
        val guideFolder = File(directory, "g1")
        guideFolder.setWritable(false)

        val result = store.setMemo("g1", "lost")
        guideFolder.setWritable(true)

        assertTrue(result.isFailure)
        assertEquals("kept", store.saved.value.single().memo)
    }

    /** [guideFolder] as a guide saved before #113 left it: `cutout.webp`, a colored figure whose alpha is the mask. */
    private fun oldCutout(guideFolder: File) {
        File(guideFolder, "mask.png").delete()
        val cutout = Bitmap.createScaledBitmap(figure(300, 900), 240, 720, true)
        File(guideFolder, "cutout.webp").outputStream().use {
            cutout.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it)
        }
    }

    /** A mask 300×900 with an opaque figure on a clear background, and a 512×384 reference photo. */
    private fun guide(
        id: String,
        reference: Bitmap? = photo(512, 384),
        anchor: NormalizedRect? = NormalizedRect(0.1f, 0.1f, 0.5f, 0.9f),
        source: GuideSource = GuideSource.GALLERY,
        situation: Situation? = null,
    ): ReferenceGuide {
        val mask = figure(300, 900)
        return ReferenceGuide(id, mask, mask, 300f / 900f, source, anchor, reference, situation = situation)
    }

    /** A guide small enough that a hundred saves stay fast. */
    private fun smallGuide(id: String): ReferenceGuide {
        val mask = figure(30, 90)
        return ReferenceGuide(id, mask, mask, 30f / 90f, GuideSource.GALLERY, reference = null)
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
