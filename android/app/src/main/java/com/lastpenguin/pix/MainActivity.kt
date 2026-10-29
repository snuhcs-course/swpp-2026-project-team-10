// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.lastpenguin.pix.databinding.ActivityMainBinding

/** The only activity. Every screen is a destination in `res/navigation/nav_graph.xml` (Design 2.8). */
class MainActivity : AppCompatActivity() {
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
    }

    /**
     * A notification or an invite link while Pix runs (single-top): its screen opens on top of what is shown. At a start
     * the navigation graph opens the deep link itself, over Camera (Design 2.6.6).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = intent.data ?: return
        val navController = (supportFragmentManager.findFragmentById(R.id.navHost) as? NavHostFragment)?.navController
        if (navController?.graph?.hasDeepLink(link) == true) navController.navigate(link)
    }
}
