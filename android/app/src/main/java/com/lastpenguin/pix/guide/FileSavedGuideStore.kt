// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import java.io.IOException
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
 * (`filesDir/guides/<id>/`) with `cutout.webp`, `reference.webp`, and `meta.json`. Design 2.8 names a `mask.png`;
 * until only the mask travels to the subject (Design 2.6.1, step 6), a guide still holds its cutout, whose alpha is
 * that smoothed mask, so the cutout is kept, encoded as the subject receives it (WebP with alpha, at most 720 px
 * tall), and a loaded guide draws the outline the subject draws. A guide is written to a hidden folder and renamed
 * into place, so a half-written one is never listed. Nothing goes to MediaStore.
 * Owner: Guide controls (#58).
 */
class FileSavedGuideStore(
    private val directory: File,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val outlines: OutlineExtractor = OutlineExtractor(),
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedGuideStore {

    private val _saved = MutableStateFlow<List<SavedGuide>>(emptyList())
    override val saved: StateFlow<List<SavedGuide>> = _saved.asStateFlow()

    private val lock = Mutex()

    /** Newest first; null until the folder has been read. */
    private var entries: List<Entry>? = null

    init {
        // Read the folder now, so Add a pose guide lists the saved guides as soon as it opens.
        scope.launch { locked { } }
    }

    override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> = locked { entries ->
        entries.firstOrNull { it.saved.id == guide.id }?.let { return@locked Result.success(it.saved) }
        try {
            val entry = write(guide, state)
            val kept = listOf(entry) + entries
            kept.drop(SavedGuideStore.MAX_SAVED).forEach { remove(it.saved.id) }
            publish(kept.take(SavedGuideStore.MAX_SAVED))
            Result.success(entry.saved)
        } catch (error: IOException) {
            Log.w(TAG, "Could not save guide ${guide.id}", error)
            Result.failure(error)
        }
    }

    override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> = locked { entries ->
        val meta = entries.firstOrNull { it.saved.id == id }?.meta
            ?: return@locked Result.failure(NoSuchElementException("No saved guide $id"))
        val folder = File(directory, id)
        val cutout = decode(File(folder, CUTOUT))
            ?: return@locked Result.failure(IOException("The cutout of saved guide $id cannot be read"))
        val guide = ReferenceGuide(
            id = id,
            cutout = cutout,
            outline = outlines.extract(cutout, outlineStrokePx(cutout.height)),
            aspect = meta.aspect,
            source = meta.source,
            anchor = meta.anchor?.let { NormalizedRect(it.left, it.top, it.right, it.bottom) },
            reference = decode(File(folder, REFERENCE)),
        )
        Result.success(guide to meta.state)
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

    private fun write(guide: ReferenceGuide, state: GuideState): Entry {
        if (!ID.matches(guide.id)) throw IOException("Guide id ${guide.id} cannot name a folder")
        val folder = File(directory, guide.id)
        val unfinished = File(directory, ".${guide.id}")
        unfinished.deleteRecursively()
        if (!unfinished.mkdirs()) throw IOException("Could not create $unfinished")
        val meta = Meta(
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
        } catch (error: IOException) {
            unfinished.deleteRecursively()
            throw error
        }
        val thumbnail = shrink(guide.reference ?: guide.cutout, THUMBNAIL_LONG_SIDE)
        return Entry(SavedGuide(guide.id, meta.savedAt, thumbnail), meta)
    }

    /** Every finished folder, newest first. A folder left by a save that did not finish is deleted. */
    private fun readAll(): List<Entry> {
        val folders = directory.listFiles() ?: return emptyList()
        return folders
            .mapNotNull { folder ->
                when {
                    folder.name.startsWith(".") -> null.also { folder.deleteRecursively() }
                    ID.matches(folder.name) -> read(folder)
                    else -> null
                }
            }
            .sortedByDescending { it.saved.savedAt }
    }

    private fun read(folder: File): Entry? {
        val meta = try {
            json.decodeFromString(Meta.serializer(), File(folder, META).readText())
        } catch (error: IOException) {
            Log.w(TAG, "Skipped saved guide ${folder.name}", error)
            return null
        } catch (error: SerializationException) {
            Log.w(TAG, "Skipped saved guide ${folder.name}", error)
            return null
        }
        val reference = File(folder, REFERENCE)
        val thumbnail = decode(if (reference.isFile) reference else File(folder, CUTOUT), THUMBNAIL_LONG_SIDE)
            ?: return null
        return Entry(SavedGuide(folder.name, meta.savedAt, thumbnail), meta)
    }

    private fun writeWebp(file: File, bitmap: Bitmap) {
        file.outputStream().use { out ->
            if (!bitmap.compress(webp(), QUALITY, out)) throw IOException("Could not encode $file")
        }
    }

    /** [file] as ARGB_8888, its long side shrunk to at most [maxLongSide]; null when it is missing or unreadable. */
    private fun decode(file: File, maxLongSide: Int? = null): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        if (maxLongSide != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            options.inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight, maxLongSide)
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        return if (maxLongSide == null) bitmap else shrink(bitmap, maxLongSide)
    }

    private fun remove(id: String) {
        if (!File(directory, id).deleteRecursively()) Log.w(TAG, "Could not delete saved guide $id")
    }

    private class Entry(val saved: SavedGuide, val meta: Meta)

    /** `meta.json`: what places the guide again, and when it was saved (Design 2.8). */
    @Serializable
    private data class Meta(
        val savedAt: Long,
        val aspect: Float,
        val source: GuideSource,
        val anchor: Box? = null,
        val state: GuideState,
    )

    @Serializable
    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private companion object {
        const val TAG = "PixSavedGuides"
        const val CUTOUT = "cutout.webp"
        const val REFERENCE = "reference.webp"
        const val META = "meta.json"

        /** As the subject receives the cutout (`WebpGuideImageCodec`), so a loaded guide draws the subject's line. */
        const val MAX_CUTOUT_HEIGHT = 720
        const val QUALITY = 90

        /** The thumbnails on Add a pose guide; twenty of them stay under 4 MB in memory. */
        const val THUMBNAIL_LONG_SIDE = 256

        /** The UUIDs that `MlKitReferenceGuideMaker` gives, which also name the folders. */
        val ID = Regex("[A-Za-z0-9-]{1,64}")

        val json = Json { ignoreUnknownKeys = true }

        fun fitHeight(bitmap: Bitmap, maxHeight: Int): Bitmap {
            if (bitmap.height <= maxHeight) return bitmap
            val width = maxOf(1, (bitmap.width.toFloat() * maxHeight / bitmap.height).roundToInt())
            return bitmap.scale(width, maxHeight)
        }

        fun shrink(bitmap: Bitmap, maxLongSide: Int): Bitmap {
            val size = fitLongSide(bitmap.width, bitmap.height, maxLongSide)
            return if (size.width == bitmap.width && size.height == bitmap.height) {
                bitmap
            } else {
                bitmap.scale(size.width, size.height)
            }
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
