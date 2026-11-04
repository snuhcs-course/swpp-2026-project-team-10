// AI-generated with Claude Code, 2026-11-04, reviewed by Joonhyung Han and Sungmin Jo
package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The guide maker's steps after the model (Design 2.6.1, steps 3–6) with a segmenter that answers with a given
 * confidence field: a person as a rectangle (the mask's size and values, the outline, the aspect, the anchor,
 * the thumbnail); a field with nothing in it (no person); the model not there (the failure passed on); a photo
 * that is not ARGB_8888 (converted); a large photo (shrunk to 1280 px, the thumbnail to 512 px).
 * A picked image (FR-2.10, step 1), read from a file: a screenshot of a feed post (cut, so the segmenter, the anchor,
 * and the thumbnail have the photo alone, and the guide keeps the screenshot and where the photo was), the same with
 * the whole image asked for (not cut), and a camera photo (not cut); a generated pose shaped like a screenshot is never
 * cut.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MlKitReferenceGuideMakerTest {
    /** The model replaced by a field: 1.0 inside [person], 0.0 elsewhere, or a failure. */
    private class FieldSegmenter(
        private val person: (x: Int, y: Int, width: Int, height: Int) -> Boolean,
        private val failure: Exception? = null,
    ) : SubjectSegmenter(ApplicationProvider.getApplicationContext()) {
        var calls = 0
        var lastConfig: Bitmap.Config? = null
        var lastSize: Pair<Int, Int>? = null

        override suspend fun segment(bitmap: Bitmap): Result<Segmentation> {
            calls++
            lastConfig = bitmap.config
            lastSize = bitmap.width to bitmap.height
            failure?.let { return Result.failure(it) }
            val width = bitmap.width
            val height = bitmap.height
            val confidence = FloatArray(width * height) { index ->
                if (person(index % width, index / width, width, height)) 1f else 0f
            }
            return Result.success(Segmentation(confidence, width, height))
        }
    }

    private fun maker(segmenter: SubjectSegmenter) =
        MlKitReferenceGuideMaker(ApplicationProvider.getApplicationContext(), segmenter, OutlineExtractor())

    private fun photo(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap =
        Bitmap.createBitmap(width, height, config).apply { eraseColor(Color.rgb(200, 150, 100)) }

    /** The person stands in the middle third of the width and the lower 70 % of the height. */
    private val standing = { x: Int, y: Int, width: Int, height: Int ->
        x in width / 3 until width * 2 / 3 && y in (height * 0.3f).toInt() until height
    }

    @Test
    fun aPerson_givesAMaskTheOutlineTheAspectTheAnchorAndTheThumbnail() = runBlocking {
        val guide = maker(FieldSegmenter(standing)).make(photo(300, 400), GuideSource.GENERATED).getOrThrow()

        // The crop is the person's box plus a 2 % margin: about 100×280 in a 300×400 photo.
        assertTrue("mask ${guide.mask.width}x${guide.mask.height}", guide.mask.width in 100..120)
        assertTrue("mask ${guide.mask.width}x${guide.mask.height}", guide.mask.height in 280..300)
        assertEquals(guide.mask.width, guide.outline.width)
        assertEquals(guide.mask.height, guide.outline.height)
        assertEquals(guide.mask.width.toFloat() / guide.mask.height, guide.aspect, 1e-6f)
        assertEquals(GuideSource.GENERATED, guide.source)
        // The mask alone, no photo pixels (AD-15): the person opaque in the middle, the background clear at the corner.
        assertEquals(Bitmap.Config.ALPHA_8, guide.mask.config)
        assertEquals(255, Color.alpha(guide.mask.getPixel(guide.mask.width / 2, guide.mask.height / 2)))
        assertEquals(0, Color.alpha(guide.mask.getPixel(0, 0)))
        // The anchor is where the crop lies in the photo (FR-3.1): centered horizontally, in the lower part.
        val anchor = checkNotNull(guide.anchor)
        assertTrue("anchor $anchor", abs(anchor.centerX - 0.5f) < 0.03f)
        assertTrue("anchor $anchor", abs(anchor.centerY - 0.65f) < 0.05f)
        // The thumbnail is the photo itself, already small enough here (FR-3.9).
        val reference = assertNotNull(guide.reference).let { guide.reference!! }
        assertEquals(300 to 400, reference.width to reference.height)
    }

    @Test
    fun nothingInTheField_isNoPersonFound() = runBlocking {
        val result = maker(FieldSegmenter({ _, _, _, _ -> false })).make(photo(100, 100), GuideSource.GALLERY)

        assertTrue(result.exceptionOrNull() is NoPersonFoundException)
    }

    @Test
    fun theModelNotThere_isPassedOnAsItsOwnFailure() = runBlocking {
        val failure = SegmentationModelUnavailableException()
        val result = maker(FieldSegmenter(standing, failure)).make(photo(100, 100), GuideSource.GALLERY)

        assertTrue(result.exceptionOrNull() is SegmentationModelUnavailableException)
    }

    @Test
    fun aPhotoInAnotherConfig_isConvertedBeforeSegmenting() = runBlocking {
        val segmenter = FieldSegmenter(standing)

        val guide = maker(segmenter).make(photo(120, 160, Bitmap.Config.RGB_565), GuideSource.GALLERY).getOrThrow()

        assertEquals(1, segmenter.calls)
        assertEquals(Bitmap.Config.ARGB_8888, segmenter.lastConfig)
    }

    @Test
    fun aLargePhoto_isShrunkTo1280_andItsThumbnailTo512() = runBlocking {
        val guide = maker(FieldSegmenter(standing)).make(photo(1600, 2000), GuideSource.GALLERY).getOrThrow()

        // Segmented at 1024×1280, so the crop is a third of 1024 wide; the thumbnail's long side is 512.
        assertTrue("mask ${guide.mask.width}", guide.mask.width in 340..400)
        val reference = guide.reference!!
        assertEquals(512, maxOf(reference.width, reference.height))
        assertEquals(410, minOf(reference.width, reference.height))
    }

    @Test
    fun aFeedScreenshot_isCutToItsPhoto_beforeThePersonIsFound() = runBlocking {
        val segmenter = FieldSegmenter(standing)

        val guide = maker(segmenter).make(file(feedPost())).getOrThrow()

        // The post's 3:4 photo, rows 170–890 of the 540 × 1170 screenshot, alone.
        assertEquals(540 to 720, segmenter.lastSize)
        val crop = checkNotNull(guide.crop)
        assertEquals(NormalizedRect(0f, 170f / 1170, 1f, 890f / 1170), crop.photo)
        assertEquals(473 to 1024, crop.original.width to crop.original.height)
        // Where the person starts and the thumbnail are the photo's, not the screenshot's (FR-3.1, FR-3.9).
        val anchor = checkNotNull(guide.anchor)
        assertTrue("anchor $anchor", abs(anchor.centerY - 0.65f) < 0.05f)
        assertEquals(384 to 512, guide.reference!!.width to guide.reference!!.height)
    }

    @Test
    fun aFeedScreenshot_withTheWholeImageAskedFor_isNotCut() = runBlocking {
        val segmenter = FieldSegmenter(standing)

        val guide = maker(segmenter).make(file(feedPost()), wholeImage = true).getOrThrow()

        assertEquals(540 to 1170, segmenter.lastSize)
        assertNull(guide.crop)
    }

    @Test
    fun aCameraPhoto_isNotCut() = runBlocking {
        val segmenter = FieldSegmenter(standing)

        val guide = maker(segmenter).make(file(photo(300, 400))).getOrThrow()

        assertEquals(300 to 400, segmenter.lastSize)
        assertNull(guide.crop)
    }

    @Test
    fun aGeneratedPoseShapedLikeAScreenshot_isNeverCut() = runBlocking {
        val segmenter = FieldSegmenter(standing)

        val guide = maker(segmenter).make(feedPost(), GuideSource.GENERATED).getOrThrow()

        assertEquals(540 to 1170, segmenter.lastSize)
        assertNull(guide.crop)
    }

    private fun feedPost(): Bitmap {
        val screen = FakeScreenshot().apply { post(720) }
        return Bitmap.createBitmap(screen.pixels, screen.width, screen.height, Bitmap.Config.ARGB_8888)
    }

    /** [bitmap] as a PNG file, as a screenshot is picked. */
    private fun file(bitmap: Bitmap): Uri {
        val file = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "picked.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }
}
