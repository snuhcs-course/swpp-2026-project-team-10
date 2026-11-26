// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.camera

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How much of the phone's photos Pix may read (FR-1.6), which the phone's photo permission decides. */
enum class PhotoAccess {
    /** Every photo on the phone. */
    ALL,

    /** The photos the user chose to allow, and Pix's own (Android 14 and later). */
    CHOSEN,

    /** Only the photos Pix saved itself, as without the permission. */
    OWN,
}

/** What became of a photo asked to be deleted from the viewer (FR-1.16). */
sealed interface PhotoDeletion {
    /** It is gone from the phone, also when it already was. */
    data object Deleted : PhotoDeletion

    /** It could not be deleted, and stays. */
    data object Failed : PhotoDeletion

    /**
     * Another app's photo, which only Android's own question may delete: [request] shows it. With [deletesItself], the
     * question's yes deletes it; otherwise the yes lets Pix delete it, and the delete is asked again (Android 10).
     */
    data class NeedsConsent(val request: IntentSender, val deletesItself: Boolean) : PhotoDeletion
}

/** Deletes a photo on this phone from the photo viewer (FR-1.16). */
fun interface PhotoDeleter {
    suspend fun delete(uri: Uri): PhotoDeletion

    /**
     * Whether Pix asks before deleting [uri] itself: for its own photos, which it deletes at once, and from Android 12
     * for every photo, since Android's own question for another app's ([PhotoDeletion.NeedsConsent]) can be turned off
     * there ([needsAccess]). On Android 11 that question cannot, so Pix does not ask a second time.
     */
    fun asksFirst(uri: Uri): Boolean = true

    /**
     * Whether Android would ask before deleting [uri] every time, as for another app's photo, until Pix may manage the
     * phone's media, which the user allows once (Android 12 and later; FR-1.16). Never while Pix may read only some of
     * the phone's photos, since Android then asks all the same.
     */
    fun needsAccess(uri: Uri): Boolean = false
}

/** One of the phone's albums, a folder of its photos, as a gallery app lists them (FR-1.6). */
data class PhoneAlbum(
    /** MediaStore's bucket id, which names the folder. */
    val id: String,
    /** The folder's own name. */
    val name: String,
    /** Where the folder is, such as `DCIM/Camera/`, by which the screen names the phone's own albums in its language. */
    val path: String?,
    /** Its photos, oldest first, as [PhonePhotoSource.photos] has them. */
    val photos: List<Uri>,
)

/** The photos on this phone that Camera's thumbnail, the newest, and its viewer show outside a session (FR-1.6). */
interface PhonePhotoSource {
    /** Oldest first by when each was taken, the newest last; empty until first read. */
    val photos: StateFlow<List<Uri>>

    /** The albums of [photos], the one with the newest photo first; read with them. */
    val albums: StateFlow<List<PhoneAlbum>>

    /** How much of the phone's photos [photos] has. */
    val access: StateFlow<PhotoAccess>

    /**
     * How many times a photo already in [photos] was changed in place, as an app's edit saved over it, which keeps its
     * URI: a picture kept by URI is stale once this moves, and is read again.
     */
    val edits: StateFlow<Int>

    /** Reads the permission and the photos again, after the permission was asked; returns once [photos] has them. */
    suspend fun refresh()
}

/** What to ask for to read every photo, or the photos the user chooses: the photo permissions of each Android. */
fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)

    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * Whether the user let Pix manage the phone's media, Android 12's special access with which Android deletes another
 * app's photo without its question (FR-1.16); never before Android 12.
 */
fun Context.canManageMedia(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(this)

/** Android's screen that lets Pix manage the phone's media, or null before Android 12, which has none. */
fun Context.manageMediaSettings(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", packageName, null))
} else {
    null
}

/** How much of the phone's photos this app may read now. */
fun Context.photoAccess(): PhotoAccess {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        granted(photoPermissions().first()) -> PhotoAccess.ALL

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.CHOSEN

        else -> PhotoAccess.OWN
    }
}

/**
 * The photos on this phone that Pix may read, taken here, received in a session, or from any other app (FR-1.6;
 * Design 2.6.5): every photo with the photo permission, the chosen ones with partial access, and without it only those
 * Pix saved, since Android shows an app no others then; so after a reinstall Pix's own older photos are not its own
 * any more. They are read from MediaStore when the app starts, at every MediaStore change, so a photo deleted in the
 * gallery leaves them, and at [refresh]. Ordered by when each was taken, as a gallery app orders them, or when it
 * came to the phone for a photo that does not say. [delete] deletes Pix's own photos at once; another app's only
 * Android's own question may delete ([PhotoDeletion.NeedsConsent]), which from Android 12 deletes it without asking once
 * Pix may manage the phone's media ([canManageMedia]).
 * Owner: Camera/Overlay (#3).
 */
class PhonePhotos(
    private val resolver: ContentResolver,
    scope: CoroutineScope,
    /** This app's package, which MediaStore names as the owner of each photo Pix saved. */
    private val ownPackage: String,
    /** The permission as it is now (`photoAccess`). */
    private val readAccess: () -> PhotoAccess,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Android's question that deletes [Uri] once the user says yes (Android 11 and later). */
    private val deleteRequest: (Uri) -> IntentSender = { uri ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
        } else {
            // Never asked for there: Android 10's MediaStore asks with its own refusal (RecoverableSecurityException).
            error("Android 10 has no delete question")
        }
    },
    private val sdk: Int = Build.VERSION.SDK_INT,
    /** Whether the user let Pix manage the phone's media (`MediaStore.canManageMedia`, Android 12 and later). */
    private val canManageMedia: () -> Boolean = { false },
) : PhonePhotoSource,
    PhotoDeleter {
    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    override val photos: StateFlow<List<Uri>> = _photos

    private val _albums = MutableStateFlow<List<PhoneAlbum>>(emptyList())
    override val albums: StateFlow<List<PhoneAlbum>> = _albums

    private val _access = MutableStateFlow(PhotoAccess.OWN)
    override val access: StateFlow<PhotoAccess> = _access

    private val _edits = MutableStateFlow(0)
    override val edits: StateFlow<Int> = _edits

    /** When each photo read was last changed, by its id, to tell a photo edited in place from one unchanged. */
    private var modified: Map<Long, Long> = emptyMap()

    /** The photos read that another app saved, which Pix may not delete itself. */
    @Volatile
    private var others: Set<Uri> = emptySet()

    /** One read at a time, so an older one never lands after a newer. */
    private val reading = Mutex()

    init {
        // A burst of changes, such as a photo's row and then its bytes, is read once.
        val changes = Channel<Unit>(Channel.CONFLATED)
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    changes.trySend(Unit)
                }
            },
        )
        changes.trySend(Unit)
        scope.launch(dispatcher) { for (change in changes) readNow() }
    }

    override suspend fun refresh() = withContext(dispatcher) { readNow() }

    private suspend fun readNow() = reading.withLock {
        _access.value = readAccess()
        val read = read()
        others = read.others
        // A photo that was there before and changed since: an edit saved over it, under the same URI.
        val before = modified
        modified = read.modified
        if (read.modified.any { (id, at) -> before[id]?.let { it != at } == true }) _edits.value++
        _photos.value = read.photos
        _albums.value = read.albums
    }

    override fun asksFirst(uri: Uri): Boolean =
        sdk < Build.VERSION_CODES.R || sdk >= Build.VERSION_CODES.S || uri !in others

    // Only with every photo readable: with chosen photos alone, Android asks before a delete even once Pix may
    // manage media (MediaProvider's PermissionActivity checks the read permission too), so the access would promise
    // what it does not give.
    override fun needsAccess(uri: Uri): Boolean =
        sdk >= Build.VERSION_CODES.S && uri in others && !canManageMedia() && _access.value == PhotoAccess.ALL

    /**
     * Deletes [uri] from MediaStore; [photos] lose it once MediaStore tells of the change. Another app's photo goes to
     * Android's question: at once from Android 11, whose question deletes it; on Android 10 once MediaStore refuses,
     * and its question lets Pix delete it.
     */
    override suspend fun delete(uri: Uri): PhotoDeletion = withContext(dispatcher) {
        val newer = sdk >= Build.VERSION_CODES.R
        try {
            if (newer && uri in others) return@withContext PhotoDeletion.NeedsConsent(deleteRequest(uri), true)
            // 0 rows: the photo was already gone, deleted in the gallery since.
            resolver.delete(uri, null, null)
            PhotoDeletion.Deleted
        } catch (error: SecurityException) {
            // A photo Pix saved before a reinstall, or another app's that MediaStore did not name as such.
            when {
                newer -> PhotoDeletion.NeedsConsent(deleteRequest(uri), true)

                error is RecoverableSecurityException ->
                    PhotoDeletion.NeedsConsent(error.userAction.actionIntent.intentSender, false)

                else -> failed(error)
            }
        } catch (error: Exception) {
            failed(error)
        }
    }

    private fun failed(error: Exception): PhotoDeletion {
        // A provider that refuses: the photo stays, and the viewer says so.
        Log.w(TAG, "The photo could not be deleted", error)
        return PhotoDeletion.Failed
    }

    /**
     * What one read finds: every photo Pix may read, oldest first, their albums, those another app saved, and when each
     * was last changed, by its id.
     */
    private class Found(
        val photos: List<Uri>,
        val albums: List<PhoneAlbum>,
        val others: Set<Uri>,
        val modified: Map<Long, Long> = emptyMap(),
    )

    private fun read(): Found = try {
        val own = ownIds()
        // Read without the owner: from Android 14 a query that asks for it, in its columns or its selection, leaves
        // out every photo whose app Pix cannot see (package visibility), even with the photo permission.
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
            ),
            // A photo still pending has no bytes to show yet; MediaStore leaves it out by default, and this says so
            // for any provider that does not.
            "${MediaStore.MediaColumns.IS_PENDING} = 0",
            null,
            null,
        )?.use { rows ->
            val id = rows.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val taken = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val added = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val changed = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val bucket = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketName = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val path = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val read = ArrayList<Read>(rows.count)
            while (rows.moveToNext()) {
                // DATE_TAKEN is in milliseconds, DATE_ADDED in seconds.
                val at = if (rows.isNull(taken)) rows.getLong(added) * MS_PER_S else rows.getLong(taken)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows.getLong(id))
                val folder = rows.getString(bucket)?.let {
                    Folder(it, rows.getString(bucketName), rows.getString(path))
                }
                read += Read(uri, rows.getLong(id), at, rows.getLong(id) in own, folder, rows.getLong(changed))
            }
            read.sortWith(compareBy(Read::at, Read::id))
            Found(
                photos = read.map(Read::uri),
                albums = albumsOf(read),
                others = read.filterNot(Read::ours).mapTo(HashSet(), Read::uri),
                modified = read.associate { it.id to it.modified },
            )
        } ?: Found(emptyList(), emptyList(), emptySet())
    } catch (error: Exception) {
        // A provider that refuses or is gone leaves the placeholder, not a crash; the next change reads again.
        Log.w(TAG, "The photos could not be read", error)
        Found(emptyList(), emptyList(), emptySet())
    }

    /**
     * The ids of the photos Pix saved, which Pix deletes after its own question; another app's go to Android's. Asked
     * apart, by Pix's own package, which Pix always sees, so the photos' own query names no owner. None when the
     * provider refuses: every photo is then another's, and Android asks before any delete.
     */
    private fun ownIds(): Set<Long> = try {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
            arrayOf(ownPackage),
            null,
        )?.use { rows ->
            val id = rows.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            buildSet { while (rows.moveToNext()) add(rows.getLong(id)) }
        }.orEmpty()
    } catch (error: Exception) {
        Log.w(TAG, "Pix's own photos could not be told apart", error)
        emptySet()
    }

    /**
     * The folders of [read], oldest first as it is, each with its photos in that order, the one with the newest photo
     * first. A folder without a name of its own, the storage's top, is named by its path.
     */
    private fun albumsOf(read: List<Read>): List<PhoneAlbum> {
        // Where each folder's newest photo is among them all.
        val newest = HashMap<String, Int>()
        read.forEachIndexed { index, photo -> photo.folder?.let { newest[it.id] = index } }
        return read.filter { it.folder != null }
            .groupBy { it.folder!!.id }
            .map { (bucket, photos) ->
                val folder = photos.last().folder!!
                PhoneAlbum(
                    bucket,
                    folder.name ?: folder.path.orEmpty().trimEnd('/'),
                    folder.path,
                    photos.map(Read::uri),
                )
            }
            .sortedByDescending { newest.getValue(it.id) }
    }

    private class Folder(val id: String, val name: String?, val path: String?)

    private class Read(
        val uri: Uri,
        val id: Long,
        val at: Long,
        val ours: Boolean,
        val folder: Folder?,
        val modified: Long,
    )

    private companion object {
        const val TAG = "PhonePhotos"
        const val MS_PER_S = 1000L
    }
}
