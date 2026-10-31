// AI-generated with Claude Code, 2026-10-31, reviewed by Dongje Park and Joonhyung Han
package com.lastpenguin.pix

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lastpenguin.pix.databinding.ActivityMainBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.PhotoNotice
import com.lastpenguin.pix.ui.session.SessionViewModel
import kotlinx.coroutines.launch

/** The only activity. Every screen is a destination in `res/navigation/nav_graph.xml` (Design 2.8). */
class MainActivity : AppCompatActivity() {
    private val sessionViewModel: SessionViewModel by viewModels { PixViewModels.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 15+ draws behind the system bars; keep the screens inside them.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Here, because a photo arrives on any screen of the session, not only on Subject view (FR-6.14).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.photoNotices.collect { notice ->
                    val text = when (notice) {
                        PhotoNotice.Saved -> getString(R.string.session_photo_saved)
                        is PhotoNotice.SaveFailed -> getString(R.string.session_photo_save_failed, notice.name)
                    }
                    Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
