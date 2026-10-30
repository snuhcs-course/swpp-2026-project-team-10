// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo
package com.lastpenguin.pix

import android.content.Intent
import android.net.Uri
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
        // A link that starts Pix opens below, over Camera, not through the navigation graph, which opens it alone when
        // another app, such as a browser, starts Pix in its own task: *Back to camera* would then find no Camera.
        val link = intent.data
        if (link != null) intent = Intent(intent).setData(null)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 15+ draws behind the system bars; keep the screens inside them.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // A recreated activity has its screens back already, the link's among them.
        if (savedInstanceState == null && link != null) open(link)
    }

    /** A notification or an invite link while Pix runs (single-top). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(::open)
    }

    /** Opens [link]'s screen on top of what is shown, Camera at a start (Design 2.6.6). */
    private fun open(link: Uri) {
        val navController = (supportFragmentManager.findFragmentById(R.id.navHost) as? NavHostFragment)?.navController
        if (navController?.graph?.hasDeepLink(link) == true) navController.navigate(link)
    }
}
