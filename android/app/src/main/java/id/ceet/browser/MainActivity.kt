package id.ceet.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.ceet.browser.core.Blocker
import id.ceet.browser.data.Store
import id.ceet.browser.ui.CeetApp
import id.ceet.browser.ui.CeetTheme
import id.ceet.browser.web.BrowserState
import id.ceet.browser.web.HOME_URL

class MainActivity : ComponentActivity() {
    private lateinit var state: BrowserState
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        val blocker = Blocker(assets.open("blocklist.txt").bufferedReader().use { it.readText() })
        state = BrowserState(applicationContext, store, blocker)
        wireState()
        handleIntent(intent)

        setContent {
            CeetTheme(state.settings.theme) {
                BackHandler {
                    val t = state.active
                    val wv = t.webView
                    when {
                        customView != null -> exitFullscreen()
                        state.findOpen -> state.endFind()
                        !t.isHome && wv != null && wv.canGoBack() -> wv.goBack()
                        !t.isHome -> state.navigate(t, HOME_URL)
                        state.tabs.size > 1 -> state.closeTab(t.id)
                        else -> finish()
                    }
                }
                CeetApp(state)
            }
        }
    }

    /** Menghubungkan hal yang butuh Activity: berkas, layar penuh, dan aplikasi lain. */
    private fun wireState() {
        state.external = { url -> openExternal(url) }
        state.chooseFile = { callback, params ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            try { fileLauncher.launch(params.createIntent()); true } catch (e: Exception) { fileCallback = null; callback.onReceiveValue(null); false }
        }
        state.showCustom = { view, cb ->
            if (customView != null) cb.onCustomViewHidden() else {
                customView = view; customCallback = cb
                addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        state.hideCustom = { exitFullscreen() }
    }

    private fun exitFullscreen() {
        customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun openExternal(url: String) {
        try {
            val intent = if (url.startsWith("intent:")) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply { addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null }
            } else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (fallback != null && fallback.startsWith("http")) state.navigate(state.active, fallback)
                else Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Tautan tidak bisa dibuka", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && (data.scheme == "http" || data.scheme == "https")) {
            val t = state.newTab()
            state.navigate(t, data.toString())
        }
    }

    override fun onPause() {
        state.saveSession()
        state.active.webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        state.active.webView?.onResume()
    }

    override fun onDestroy() {
        state.saveSession()
        super.onDestroy()
    }
}
