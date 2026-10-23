// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * [SavedGuideStore] in the app's private files (Design 2.8, *Guide store*): one folder per guide under [directory]
 * (`filesDir/guides/<id>/`) with `cutout.webp`, `reference.webp`, and `meta.json`. Until only the mask travels to the
 * subject (Design 2.6.1, step 6), a guide still holds its cutout, whose alpha is that smoothed mask, so the cutout is
 * kept, encoded as the subject receives it (WebP with alpha, at most 720 px tall), and a loaded guide draws the
 * outline the subject draws. The encoding is the store's own, so a later change to what travels does not change the
 * guides already saved. A guide is written to a hidden folder and renamed into place, so a half-written one is never
 * listed; a folder that cannot be read back is deleted. The list is ordered by an increasing number kept in
 * `meta.json`, not by the clock. Nothing goes to MediaStore.
 * Owner: Guide controls (#58).
 */
class FileSavedGuideStore(
    private val directory: File,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val outlines: OutlineExtractor = OutlineExtractor(),
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedGuideStore {

    private val _saved = MutableStateFlow<List<SavedGuide>>(emptyList())
    override val saved: StateFlow<List<SavedGuide>> = _saved.asStateFlow()

    private val lock = Mutex()

    /** Newest first; null until the folder has been read. */
    private var entries: List<Entry>? = null

    init {
        // Read the folder now: the Camera screen shows at once whether its guide is saved, and Add a pose guide
        // lists the saved guides as soon as it opens.
        scope.launch { locked { } }
    }

    override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> = locked { entries ->
        entries.firstOrNull { it.saved.id == guide.id }?.let { return@locked Result.success(it.saved) }
        try {
            val entry = write(guide, state, order = (entries.maxOfOrNull { it.meta.order } ?: 0L) + 1)
            val kept = listOf(entry) + entries
            kept.drop(SavedGuideStore.MAX_SAVED).forEach { remove(it.saved.id) }
            publish(kept.take(SavedGuideStore.MAX_SAVED))
            Result.success(entry.saved)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Any failure, not only a full disk: a bitmap that cannot be encoded must not end the app.
            Log.w(TAG, "Could not save guide ${guide.id}", error)
            Result.failure(error)
        }
    }

    override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> {
        val read = locked { entries ->
            val meta = entries.firstOrNull { it.saved.id == id }?.meta ?: return@locked null
            val folder = File(directory, id)
            Triple(meta, decode(File(folder, CUTOUT)), decode(File(folder, REFERENCE)))
        } ?: return Result.failure(NoSuchElementException("No saved guide $id"))
        val (meta, cutout, reference) = read
        if (cutout == null) return Result.failure(IOException("The cutout of saved guide $id cannot be read"))
        // Tracing is work for the CPU, done outside the lock so that saves and deletes do not wait for it.
        val outline = withContext(compute) { outlines.extract(cutout, outlineStrokePx(cutout.height)) }
        val guide = ReferenceGuide(
            id = id,
            cutout = cutout,
            outline = outline,
            aspect = meta.aspect,
            source = meta.source,
            anchor = meta.anchor?.let { NormalizedRect(it.left, it.top, it.right, it.bottom) },
            reference = reference,
        )
        return Result.success(guide to meta.state)
    }

    override suspend fun delete(id: String) {
        locked { entries ->
            if (entries.none { it.saved.id == id }) return@locked
            remove(id)
            publish(entries.filterNot { it.saved.id == id })
        }
    }

    /** Runs [block] on [io], one at a time, with the entries, reading the folder first if it has not been read. */
    private suspend fun <T> locked(block: suspend (List<Entry>) -> T): T = withContext(io) {
        lock.withLock { block(entries ?: readAll().also(::publish)) }
    }

    private fun publish(next: List<Entry>) {
        entries = next
        _saved.value = next.map { it.saved }
    }

    private fun write(guide: ReferenceGuide, state: GuideState, order: Long): Entry {
        if (!ID.matches(guide.id)) throw IOException("Guide id ${guide.id} cannot name a folder")
        val folder = File(directory, guide.id)
        val unfinished = File(directory, ".${guide.id}")
        unfinished.deleteRecursively()
        if (!unfinished.mkdirs()) throw IOException("Could not create $unfinished")
        val meta = Meta(
            order = order,
            savedAt = clock(),
            aspect = guide.aspect,
            source = guide.source,
            anchor = guide.anchor?.let { Box(it.left, it.top, it.right, it.bottom) },
            state = state,
        )
        try {
            writeWebp(File(unfinished, CUTOUT), fitHeight(guide.cutout, MAX_CUTOUT_HEIGHT))
            guide.reference?.let { writeWebp(File(unfinished, REFERENCE), it) }
            File(unfinished, META).writeText(json.encodeToString(Meta.serializer(), meta))
            folder.deleteRecursively()
            if (!unfinished.renameTo(folder)) throw IOException("Could not move $unfinished to $folder")
        } catch (error: Exception) {
            unfinished.deleteRecursively()
            throw error
        }
        return Entry(SavedGuide(guide.id, meta.savedAt, thumbnail(guide.reference ?: guide.cutout)), meta)
    }

    /**
     * Every finished folder, newest first, and at most [SavedGuideStore.MAX_SAVED] of them. A folder left by a save
     * that did not finish, one that cannot be read back, and the oldest beyond the limit are deleted, so the folder
     * and the list stay the same after a delete that stopped halfway.
     */
    private fun readAll(): List<Entry> {
        val folders = directory.listFiles() ?: return emptyList()
        val read = folders
            .mapNotNull { folder ->
                when {
                    folder.name.startsWith(".") -> null.also { folder.deleteRecursively() }
                    !ID.matches(folder.name) -> null
                    else -> read(folder) ?: null.also { folder.deleteRecursively() }
                }
            }
            .sortedWith(compareByDescending<Entry> { it.meta.order }.thenByDescending { it.meta.savedAt })
        read.drop(SavedGuideStore.MAX_SAVED).forEach { remove(it.saved.id) }
        return read.take(SavedGuideStore.MAX_SAVED)
    }

    /** The folder's guide, or null when its `meta.json` or images cannot be used. */
    private fun read(folder: File): Entry? {
        val meta = try {
            json.decodeFromString(Meta.serializer(), File(folder, META).readText())
        } catch (error: IOException) {
            Log.w(TAG, "Saved guide ${folder.name} has no meta.json", error)
            return null
        } catch (error: SerializationException) {
            Log.w(TAG, "Saved guide ${folder.name} has a damaged meta.json", error)
            return null
        }
        if (!meta.isUsable()) {
            Log.w(TAG, "Saved guide ${folder.name} has values that cannot place a guide")
            return null
        }
        val reference = File(folder, REFERENCE)
        val source = decode(if (reference.isFile) reference else File(folder, CUTOUT)) ?: return null
        return Entry(SavedGuide(folder.name, meta.savedAt, thumbnail(source)), meta)
    }

    private fun writeWebp(file: File, bitmap: Bitmap) {
        file.outputStream().use { out ->
            if (!bitmap.compress(webp(), QUALITY, out)) throw IOException("Could not encode $file")
        }
    }

    /** [file] as ARGB_8888; null when it is missing or unreadable. */
    private fun decode(file: File): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun remove(id: String) {
        if (!File(directory, id).deleteRecursively()) Log.w(TAG, "Could not delete saved guide $id")
    }

    private class Entry(val saved: SavedGuide, val meta: Meta)

    /** `meta.json`: what places the guide again, its place in the list, and when it was saved (Design 2.8). */
    @Serializable
    private data class Meta(
        /** Increases with every save; orders the list, so a clock set back cannot reorder it. */
        val order: Long = 0,
        val savedAt: Long,
        val aspect: Float,
        val source: GuideSource,
        val anchor: Box? = null,
        val state: GuideState,
    ) {
        /** What `GuideGeometry` needs to place the guide: a positive aspect and a real box. */
        fun isUsable(): Boolean = aspect.isFinite() && aspect > 0f && (anchor == null || anchor.isUsable())
    }

    @Serializable
    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun isUsable(): Boolean = listOf(left, top, right, bottom).all { it.isFinite() } && left < right && top < bottom
    }

    private companion object {
        const val TAG = "PixSavedGuides"
        const val CUTOUT = "cutout.webp"
        const val REFERENCE = "reference.webp"
        const val META = "meta.json"

        /** As the subject receives the cutout (`WebpGuideImageCodec`), so a loaded guide draws the subject's line. */
        const val MAX_CUTOUT_HEIGHT = 720
        const val QUALITY = 90

        /**
         * The thumbnails on Add a pose guide: the item's 3:4 at its size on a 3x screen (84 × 112 dp), so a photo of
         * any shape stays sharp; twenty of them take under 7 MB in memory.
         */
        const val THUMBNAIL_WIDTH = 252
        const val THUMBNAIL_HEIGHT = 336

        /** The UUIDs that `MlKitReferenceGuideMaker` gives, which also name the folders. */
        val ID = Regex("[A-Za-z0-9-]{1,64}")

        val json = Json { ignoreUnknownKeys = true }

        fun fitHeight(bitmap: Bitmap, maxHeight: Int): Bitmap {
            if (bitmap.height <= maxHeight) return bitmap
            val width = maxOf(1, (bitmap.width.toFloat() * maxHeight / bitmap.height).roundToInt())
            return bitmap.scale(width, maxHeight)
        }

        /** The middle of [source] cut to 3:4, at most [THUMBNAIL_WIDTH] × [THUMBNAIL_HEIGHT]. */
        fun thumbnail(source: Bitmap): Bitmap {
            val (width, height) = if (source.width * 4 > source.height * 3) {
                maxOf(1, source.height * 3 / 4) to source.height
            } else {
                source.width to maxOf(1, source.width * 4 / 3)
            }
            val cropped = Bitmap.createBitmap(
                source,
                (source.width - width) / 2,
                (source.height - height) / 2,
                width,
                height,
            )
            return if (height <= THUMBNAIL_HEIGHT) cropped else cropped.scale(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        }

        @Suppress("DEPRECATION")
        fun webp(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R
            ) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
    }
}
