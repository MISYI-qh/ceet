package id.ceet.browser

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import id.ceet.browser.core.Blocker
import id.ceet.browser.data.Store
import id.ceet.browser.ui.CeetApp
import id.ceet.browser.ui.CeetTheme
import id.ceet.browser.web.BrowserState
import id.ceet.browser.web.HOME_URL
import id.ceet.browser.web.TabState

class MainActivity : ComponentActivity() {
    private lateinit var state: BrowserState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        val blocker = Blocker(assets.open("blocklist.txt").bufferedReader().use { it.readText() })
        state = BrowserState(applicationContext, store, blocker)
        handleIntent(intent)

        setContent {
            CeetTheme(state.settings.theme) {
                BackHandler {
                    val t = state.active
                    val wv = t.webView
                    when {
                        !t.isHome && wv != null && wv.canGoBack() -> wv.goBack()
                        !t.isHome -> state.navigate(t, HOME_URL)
                        state.tabs.size > 1 -> state.closeTab(t.id)
                        else -> finish()
                    }
                }
                CeetApp(state) { tab -> WebViewHost(tab) }
            }
        }
    }

    @Composable
    private fun WebViewHost(tab: TabState) {
        AndroidView(
            modifier = androidx.compose.ui.Modifier,
            factory = { ctx -> FrameLayout(ctx).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
            update = { frame ->
                val wv = state.ensureWebView(frame.context, tab)
                if (wv.parent !== frame) {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    frame.removeAllViews()
                    frame.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { frame -> frame.removeAllViews() },
        )
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
