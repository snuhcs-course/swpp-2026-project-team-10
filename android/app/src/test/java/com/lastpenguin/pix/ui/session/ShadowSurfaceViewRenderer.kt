// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSurfaceView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * WebRTC's renderer without its EGL thread, for screen tests under Robolectric: `init` would create an EGL context
 * from the fake session's context and fail, and there is no GPU to draw on. Layout, scaling, and the frame fit are
 * untouched (SubjectVideoFitTest covers them); a shadow of a View must extend the View's shadow. Use with
 * `@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])`.
 */
@Implements(SurfaceViewRenderer::class)
class ShadowSurfaceViewRenderer : ShadowSurfaceView() {
    @Implementation
    fun init(sharedContext: EglBase.Context?, rendererEvents: RendererCommon.RendererEvents?) = Unit

    @Implementation
    fun release() = Unit
}
