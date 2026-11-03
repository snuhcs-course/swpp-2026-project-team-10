// AI-generated with Claude Code, 2026-11-02, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentTutorialBinding
import com.lastpenguin.pix.ui.PixViewModels

/** One slide: an image drawn in `wiki/design/tutorial/`, in the language of the screens, and what it says. */
internal data class TutorialSlide(@param:DrawableRes val image: Int, val captions: List<Int>)

internal val TUTORIAL_SLIDES = listOf(
    TutorialSlide(
        R.drawable.tutorial_camera,
        listOf(
            R.string.tutorial_settings,
            R.string.tutorial_together,
            R.string.tutorial_guide,
            R.string.tutorial_shutter,
            R.string.tutorial_switch,
        ),
    ),
    TutorialSlide(
        R.drawable.tutorial_guides,
        listOf(R.string.tutorial_upload, R.string.tutorial_generate, R.string.tutorial_saved),
    ),
    TutorialSlide(
        R.drawable.tutorial_guide_moves,
        listOf(R.string.tutorial_move, R.string.tutorial_edit, R.string.tutorial_fade, R.string.tutorial_save),
    ),
    TutorialSlide(
        R.drawable.tutorial_poses,
        listOf(R.string.tutorial_scene, R.string.tutorial_describe, R.string.tutorial_suggest),
    ),
    TutorialSlide(
        R.drawable.tutorial_friend,
        listOf(
            R.string.tutorial_friend_title,
            R.string.tutorial_friend_zoom,
            R.string.tutorial_friend_brightness,
            R.string.tutorial_friend_move,
            R.string.tutorial_friend_guide,
        ),
    ),
)

/**
 * The first-use tutorial (FR-10.4, Design 2.6.8): slides of the app's screens with what each control does, so it runs
 * no camera, server, or session. Swipe or *Next* go on; the last slide's *Start*, *Skip*, and Back all close it.
 * The slides are images, so TalkBack reads what they say from the row of dots, which is a live region.
 * Owner: Settings (#123).
 */
class TutorialFragment : Fragment(R.layout.fragment_tutorial) {

    private val settings: SettingsViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentTutorialBinding.bind(view)
        val lastSlide = TUTORIAL_SLIDES.lastIndex
        binding.slides.adapter = SlideAdapter()
        repeat(TUTORIAL_SLIDES.size) { layoutInflater.inflate(R.layout.view_tutorial_dot, binding.dots) }
        binding.slides.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = render(binding, position)
            },
        )
        render(binding, binding.slides.currentItem)
        binding.skipButton.setOnClickListener { close() }
        binding.nextButton.setOnClickListener {
            if (binding.slides.currentItem == lastSlide) close() else binding.slides.currentItem += 1
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { close() }
    }

    private fun render(binding: FragmentTutorialBinding, position: Int) {
        val last = position == TUTORIAL_SLIDES.lastIndex
        binding.skipButton.isInvisible = last
        binding.nextButton.setText(if (last) R.string.tutorial_start else R.string.tutorial_next)
        for (index in 0 until binding.dots.childCount) {
            binding.dots.getChildAt(index).isSelected = index == position
        }
        val page = getString(R.string.tutorial_page, position + 1, TUTORIAL_SLIDES.size)
        val captions = TUTORIAL_SLIDES[position].captions.map(::getString)
        binding.dots.contentDescription = (listOf(page) + captions).joinToString(". ")
    }

    private fun close() {
        settings.tutorialFinished()
        findNavController().popBackStack()
    }

    private class SlideAdapter : RecyclerView.Adapter<SlideAdapter.Slide>() {
        class Slide(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Slide(
            LayoutInflater.from(parent.context).inflate(R.layout.item_tutorial_slide, parent, false) as ImageView,
        )

        override fun onBindViewHolder(holder: Slide, position: Int) {
            holder.image.setImageResource(TUTORIAL_SLIDES[position].image)
        }

        override fun getItemCount() = TUTORIAL_SLIDES.size
    }
}
