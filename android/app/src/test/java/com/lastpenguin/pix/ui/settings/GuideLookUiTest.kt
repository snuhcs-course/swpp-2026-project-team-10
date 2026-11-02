// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideOutlines
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.ui.ScreenTestSupport
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Reference confirm's outline in the settings' look (FR-3.10, FR-2.3), on the image both phones' confirm screens use.
 * Partitions: white (no tint, the dark card behind it), a light color (tinted, no backing), black (tinted, on a light
 * backing, since a dark line vanishes on the dark card), and back to white; no guide yet (nothing shown).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideLookUiTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val image = ImageView(ApplicationProvider.getApplicationContext())
    private val look = MutableStateFlow(GuideLook())
    private val guides = MutableStateFlow<ReferenceGuide?>(null)

    @After
    fun tearDown() = scope.cancel()

    private fun follow() {
        scope.launch { image.showOutline(look, guides, GuideOutlines(dispatcher = Dispatchers.Unconfined)) }
        idle()
    }

    @Test
    fun aBlackLine_getsALightBacking_andOtherColorsNone() {
        val guide = ScreenTestSupport.guide(withReference = false)
        guides.value = guide
        follow()
        assertNull(image.colorFilter)
        assertNull(image.background)
        assertSame(guide.outline, (image.drawable as BitmapDrawable).bitmap)

        look.value = GuideLook(GuideColor.MINT)
        idle()
        assertNotNull(image.colorFilter)
        assertNull(image.background)

        look.value = GuideLook(GuideColor.BLACK)
        idle()
        assertNotNull(image.colorFilter)
        assertNotNull(image.background)

        look.value = GuideLook()
        idle()
        assertNull(image.colorFilter)
        assertNull(image.background)
    }

    @Test
    fun noGuideYet_showsNothing() {
        look.value = GuideLook(GuideColor.BLACK)
        follow()
        assertNull(image.drawable)
    }
}
