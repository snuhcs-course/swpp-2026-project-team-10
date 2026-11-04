// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.content.Context
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.SavedGuide
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * A saved guide as My library and the library show it (FR-3.8, FR-3.11): its thumbnail, read when its cell shows, and
 * its kind, as an icon and in what TalkBack says.
 */

/** The icon of the guide's kind: a photo the user chose, or a pose made by AI. */
@get:DrawableRes
internal val SavedGuide.kindIcon: Int
    get() = when (source) {
        GuideSource.GALLERY -> R.drawable.ic_image
        GuideSource.GENERATED -> R.drawable.ic_sparkle
    }

/** What TalkBack says for the guide: its kind and when it was saved, as the thumbnail itself shows neither in words. */
internal fun Context.describe(guide: SavedGuide): String {
    val savedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(guide.savedAt))
    val kind = when (guide.source) {
        GuideSource.GALLERY -> R.string.saved_photo_guide_description
        GuideSource.GENERATED -> R.string.saved_pose_guide_description
    }
    return getString(kind, savedAt)
}

/**
 * Shows [guide]'s thumbnail in this view once [viewModel] has it, empty until then. A view given another guide before
 * that, as a recycled cell is, cancels the earlier read, so it never shows that guide's thumbnail.
 */
internal fun ImageView.showThumbnail(guide: SavedGuide, viewModel: ReferenceViewModel, scope: CoroutineScope) {
    (getTag(R.id.thumbnail_job) as? Job)?.cancel()
    setImageDrawable(null)
    setTag(R.id.thumbnail_job, scope.launch { setImageBitmap(viewModel.thumbnail(guide.id)) })
}
