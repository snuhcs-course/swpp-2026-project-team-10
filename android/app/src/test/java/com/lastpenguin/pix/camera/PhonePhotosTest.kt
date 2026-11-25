// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.camera

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * The phone's photos that Pix may read (FR-1.6, Design 2.6.5), through a small provider in place of MediaStore.
 * The query (state-based): every published photo the provider shows, with no album of Pix's asked for, kept oldest
 * first by when each was taken, or when it came to the phone for one that does not say, the newest last; two of one
 * time by their ids; none on an empty phone. Albums (state-based): each folder of them, the one with the newest photo
 * first, its photos oldest first; a folder with no name of its own named by its path; a photo in no folder in none.
 * Changes (state-based): a change anywhere in MediaStore reads it again, so a new photo joins and a deleted one
 * leaves, the newest too; a refresh reads the permission and the photos again and returns once they are there.
 * Failures (fault injection): a query that throws gives none, not a crash, and the next change reads again.
 * Edits (state-based): a photo read before whose change time moves, an edit saved over it under its URI, counts one
 * edit; a new photo, a deleted one, and a read with nothing changed count none.
 * Package visibility (equivalence over the query): on Android 14, where a query naming the owner leaves out the
 * photos of apps Pix cannot see, every app's photo still shows, and Pix's own still tells apart from another's.
 * Delete (state-based, fault injection; equivalence over the owner and Android's version): Pix's own photo goes from
 * MediaStore after Pix asks, which then tells of the change; a photo already gone counts as deleted; one MediaStore
 * refuses for no reason of ownership is not, with no crash. Another app's photo: from Android 11 Pix does not ask, and
 * Android's question deletes it, MediaStore untouched; on Android 10 Pix asks, and MediaStore's own question lets it
 * delete it. Pix's photo from before a reinstall, which MediaStore refuses as another's: Android's question.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PhonePhotosTest {

    /**
     * One photo's row: its id, when it was taken (ms) or null, when it came (s), which app saved it, its folder, and when
     * it was last changed (s).
     */
    data class Row(
        val id: Long,
        val taken: Long?,
        val added: Long,
        val owner: String? = PIX,
        val bucket: String? = "pix",
        val bucketName: String? = "Pix",
        val path: String? = "Pictures/Pix/",
        val modified: Long = added,
    )

    /**
     * Answers each query with [rows], or, asked by owner, with that owner's rows, and records what was asked and
     * deleted. With [hidesUnseenOwners], as Android 14's MediaStore does, a query that names the owner leaves out the
     * rows of every app but Pix, which Pix cannot see.
     */
    class FakeMediaStore : ContentProvider() {
        class Query(val uri: Uri, val projection: List<String>, val selection: String?, val args: List<String>)

        var rows: List<Row> = emptyList()
        var fails = false
        var hidesUnseenOwners = false

        /** Refuses every delete for a reason that is not the photo's owner, as a provider gone wrong does. */
        var refusesDelete = false

        /** Refuses to delete a photo of another app, as Android 10's MediaStore does, with its own question. */
        var asksForOthers = false

        /** Refuses Pix's own photos as another's, as after a reinstall. */
        var forgotPix = false
        val queries = mutableListOf<Query>()
        val deleted = mutableListOf<Uri>()

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (fails) throw IllegalStateException("MediaStore is gone")
            val columns = projection.orEmpty().toList()
            queries += Query(uri, columns, selection, args.orEmpty().toList())
            val owner = MediaStore.MediaColumns.OWNER_PACKAGE_NAME
            val byOwner = selection.orEmpty().contains("$owner = ?")
            val namesOwner = byOwner || owner in columns
            val answered = rows
                .filter { !byOwner || it.owner == args!!.first() }
                .filter { !(namesOwner && hidesUnseenOwners) || it.owner == PIX }
            return MatrixCursor(columns.toTypedArray()).apply {
                answered.forEach { row ->
                    addRow(
                        columns.map { column ->
                            when (column) {
                                MediaStore.MediaColumns._ID -> row.id
                                MediaStore.MediaColumns.DATE_TAKEN -> row.taken
                                MediaStore.MediaColumns.DATE_ADDED -> row.added
                                MediaStore.MediaColumns.DATE_MODIFIED -> row.modified
                                owner -> row.owner
                                MediaStore.MediaColumns.BUCKET_ID -> row.bucket
                                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME -> row.bucketName
                                MediaStore.MediaColumns.RELATIVE_PATH -> row.path
                                else -> null
                            }
                        }.toTypedArray(),
                    )
                }
            }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            if (refusesDelete) throw IllegalStateException("The disk is read-only")
            val id = uri.lastPathSegment!!.toLong()
            val row = rows.firstOrNull { it.id == id } ?: return 0
            if (forgotPix) throw SecurityException("Not this app's photo any more")
            if (row.owner != PIX) {
                if (!asksForOthers) throw SecurityException("Another app's photo")
                val question = RemoteAction(Icon.createWithData(ByteArray(0), 0, 0), "Allow", "Allow", answer())
                throw RecoverableSecurityException(SecurityException("Another app's photo"), "Allow", question)
            }
            deleted += uri
            rows = rows - row
            return 1
        }

        override fun getType(uri: Uri): String? = null
    }

    private lateinit var store: FakeMediaStore
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resolver: ContentResolver get() = context.contentResolver

    /** The permission as a test sets it. */
    private var access = PhotoAccess.ALL

    /** The photos Android's delete question was made for. */
    private val questions = mutableListOf<Uri>()
    private val deleteQuestion = answer().intentSender

    @Before
    fun setup() {
        store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
    }

    private fun TestScope.photos(sdk: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE): PhonePhotos {
        val photos = PhonePhotos(
            resolver,
            backgroundScope,
            PIX,
            readAccess = { access },
            dispatcher = StandardTestDispatcher(testScheduler),
            deleteRequest = { uri ->
                questions += uri
                deleteQuestion
            },
            sdk = sdk,
        )
        runCurrent()
        return photos
    }

    private fun photo(id: Long): Uri = Uri.parse("content://media/external/images/media/$id")

    /** What MediaStore tells an app when one of its rows changes. */
    private fun TestScope.changed(id: Long) {
        resolver.notifyChange(photo(id), null)
        runCurrent()
    }

    @Test
    fun `the photos are every published one, oldest first by when each was taken, the newest last`() = runTest {
        store.rows = listOf(
            // Another app's photo taken long ago, and a screenshot that does not say when it was taken.
            Row(12, taken = 1_000, added = 900, owner = "com.example.gallery"),
            Row(10, taken = 3_000_000, added = 3_000),
            Row(11, taken = null, added = 2_000, owner = "com.android.systemui"),
            // Taken at the same moment as photo 10: by id.
            Row(9, taken = 3_000_000, added = 3_000),
        )

        val photos = photos()

        assertEquals(listOf(photo(12), photo(11), photo(9), photo(10)), photos.photos.value)
        val query = store.queries.last()
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, query.uri)
        // No album of Pix's: without the permission, MediaStore shows Pix only its own anyway.
        assertFalse(query.selection, query.selection!!.contains(MediaStore.MediaColumns.RELATIVE_PATH))
        // Only a published photo: one still pending has no bytes to show yet.
        assertTrue(query.selection, query.selection!!.contains("${MediaStore.MediaColumns.IS_PENDING} = 0"))
    }

    @Test
    fun `on Android 14 every app's photo shows, though a query naming the owner would hide them`() = runTest {
        store.hidesUnseenOwners = true
        store.rows = listOf(
            Row(10, 1_000, 1),
            Row(11, 2_000, 2, owner = "com.example.camera"),
            Row(12, 3_000, 3, owner = null),
        )

        val photos = photos()

        assertEquals(listOf(photo(10), photo(11), photo(12)), photos.photos.value)
        // The photos' query names no owner; Pix's own are asked for apart, by Pix's package.
        val read = store.queries.last()
        assertFalse(read.projection.toString(), MediaStore.MediaColumns.OWNER_PACKAGE_NAME in read.projection)
        assertFalse(read.selection, read.selection!!.contains(MediaStore.MediaColumns.OWNER_PACKAGE_NAME))
        assertTrue(store.queries.any { it.args == listOf(PIX) })
        // Pix asks before deleting its own, and leaves another's to Android's question.
        assertTrue(photos.asksFirst(photo(10)))
        assertFalse(photos.asksFirst(photo(11)))
        assertFalse(photos.asksFirst(photo(12)))
    }

    @Test
    fun `a photo edited in place counts an edit, and a new or deleted photo none`() = runTest {
        store.rows = listOf(Row(10, 1_000, 1), Row(11, 2_000, 2))
        val photos = photos()
        assertEquals(0, photos.edits.value)

        // A new photo, then nothing changed.
        store.rows = store.rows + Row(12, 3_000, 3)
        changed(12)
        changed(12)
        assertEquals(0, photos.edits.value)

        // An app saves an edit over photo 11: its URI stays, its change time moves.
        store.rows = store.rows.map { if (it.id == 11L) it.copy(modified = 50) else it }
        changed(11)
        assertEquals(listOf(photo(10), photo(11), photo(12)), photos.photos.value)
        assertEquals(1, photos.edits.value)

        // A deleted photo.
        store.rows = store.rows.filter { it.id != 10L }
        changed(10)
        assertEquals(1, photos.edits.value)
    }

    @Test
    fun `a phone with no photos has none`() = runTest {
        val photos = photos()
        assertEquals(emptyList<Uri>(), photos.photos.value)
        assertEquals(emptyList<PhoneAlbum>(), photos.albums.value)
    }

    @Test
    fun `the albums are the photos' folders, the one with the newest photo first, each oldest first`() = runTest {
        store.rows = listOf(
            Row(1, 1_000, 1, bucket = "cam", bucketName = "Camera", path = "DCIM/Camera/"),
            Row(2, 2_000, 2),
            Row(3, 3_000, 3, bucket = "cam", bucketName = "Camera", path = "DCIM/Camera/"),
            // A folder with no name of its own, the storage's top, and a photo in no folder.
            Row(4, 1_500, 1, bucket = "top", bucketName = null, path = "Pictures/"),
            Row(5, 500, 1, bucket = null, bucketName = null, path = null),
        )

        val albums = photos().albums.value

        assertEquals(
            listOf(
                PhoneAlbum("cam", "Camera", "DCIM/Camera/", listOf(photo(1), photo(3))),
                PhoneAlbum("pix", "Pix", "Pictures/Pix/", listOf(photo(2))),
                PhoneAlbum("top", "Pictures", "Pictures/", listOf(photo(4))),
            ),
            albums,
        )
    }

    @Test
    fun `a change reads the photos again, for a new photo and for one deleted`() = runTest {
        store.rows = listOf(Row(10, 1_000, 1), Row(11, 2_000, 2))
        val photos = photos()

        store.rows = store.rows + Row(12, 3_000, 3, owner = null)
        changed(12)
        assertEquals(listOf(photo(10), photo(11), photo(12)), photos.photos.value)

        store.rows = store.rows.filter { it.id != 11L }
        changed(11)
        assertEquals(listOf(photo(10), photo(12)), photos.photos.value)

        store.rows = emptyList()
        changed(10)
        assertEquals(emptyList<Uri>(), photos.photos.value)
    }

    @Test
    fun `a refresh reads the permission and the photos again, and returns once they are there`() = runTest {
        access = PhotoAccess.OWN
        store.rows = listOf(Row(10, 1_000, 1))
        val photos = photos()
        assertEquals(PhotoAccess.OWN, photos.access.value)

        // Allowed: MediaStore shows another app's photo now, and tells of no change.
        access = PhotoAccess.ALL
        store.rows = store.rows + Row(11, 2_000, 2, owner = "com.example.camera")
        photos.refresh()

        assertEquals(PhotoAccess.ALL, photos.access.value)
        assertEquals(listOf(photo(10), photo(11)), photos.photos.value)
    }

    @Test
    fun `a query that fails gives none, and the next change reads again`() = runTest {
        store.fails = true
        val photos = photos()
        assertEquals(emptyList<Uri>(), photos.photos.value)

        store.fails = false
        store.rows = listOf(Row(11, 1_000, 1))
        changed(11)

        assertEquals(listOf(photo(11)), photos.photos.value)
    }

    @Test
    fun `Pix's own photo is asked about by Pix, goes from MediaStore, and leaves at its change`() = runTest {
        store.rows = listOf(Row(10, 1_000, 1), Row(11, 2_000, 2))
        val photos = photos()

        assertTrue(photos.asksFirst(photo(11)))
        assertEquals(PhotoDeletion.Deleted, photos.delete(photo(11)))
        assertEquals(listOf(photo(11)), store.deleted)
        changed(11)

        assertEquals(listOf(photo(10)), photos.photos.value)
        assertTrue(questions.isEmpty())
    }

    @Test
    fun `a photo already gone counts as deleted`() = runTest {
        store.rows = listOf(Row(10, 1_000, 1))
        val photos = photos()

        assertEquals(PhotoDeletion.Deleted, photos.delete(photo(11)))
    }

    @Test
    fun `a delete that MediaStore refuses for no reason of ownership is not done, and does not crash`() = runTest {
        store.rows = listOf(Row(10, 1_000, 1))
        val photos = photos()
        store.refusesDelete = true

        assertEquals(PhotoDeletion.Failed, photos.delete(photo(10)))
        assertEquals(listOf(photo(10)), photos.photos.value)
    }

    @Test
    fun `another app's photo, from Android 11, goes to Android's question alone, which deletes it`() = runTest {
        store.rows = listOf(Row(10, 1_000, 1, owner = "com.example.gallery"), Row(11, 2_000, 2, owner = null))
        val photos = photos(sdk = Build.VERSION_CODES.R)

        for (id in listOf(10L, 11L)) {
            assertFalse(photos.asksFirst(photo(id)))
            val result = photos.delete(photo(id))
            assertTrue("$result", result is PhotoDeletion.NeedsConsent && result.deletesItself)
            assertSame(deleteQuestion, (result as PhotoDeletion.NeedsConsent).request)
        }
        assertEquals(listOf(photo(10), photo(11)), questions)
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `another app's photo, on Android 10, is asked about by Pix, then MediaStore's question lets Pix delete it`() =
        runTest {
            store.rows = listOf(Row(10, 1_000, 1, owner = "com.example.gallery"))
            store.asksForOthers = true
            val photos = photos(sdk = Build.VERSION_CODES.Q)

            assertTrue(photos.asksFirst(photo(10)))
            val result = photos.delete(photo(10))

            assertTrue("$result", result is PhotoDeletion.NeedsConsent && !result.deletesItself)
            assertTrue(questions.isEmpty())
        }

    @Test
    fun `Pix's photo from before a reinstall, which MediaStore refuses as another's, goes to Android's question`() =
        runTest {
            store.rows = listOf(Row(10, 1_000, 1))
            store.forgotPix = true
            val photos = photos()

            val result = photos.delete(photo(10))

            assertTrue("$result", result is PhotoDeletion.NeedsConsent && result.deletesItself)
            assertEquals(listOf(photo(10)), questions)
        }

    private companion object {
        const val PIX = "com.lastpenguin.pix"

        /** A question's answer, which no test sends. */
        fun answer(): PendingIntent = PendingIntent.getActivity(
            ApplicationProvider.getApplicationContext(),
            0,
            Intent("com.lastpenguin.pix.test.QUESTION"),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
