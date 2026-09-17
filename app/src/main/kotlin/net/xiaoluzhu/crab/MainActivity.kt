package net.xiaoluzhu.crab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.xiaoluzhu.crab.webview.CrabContainer

/**
 * 容器的宿主。**只做装配与生命周期**，判定逻辑一条都不在这里。
 *
 * 错误态 UI、返回键接管在 M4 加上。
 */
class MainActivity : ComponentActivity() {
    private lateinit var container: CrabContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container =
            CrabContainer(
                context = this,
                isDebugBuild = BuildConfig.DEBUG,
                versionName = BuildConfig.VERSION_NAME,
            )
        container.loadEntry()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { container.webView },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        container.onPause()
    }

    override fun onResume() {
        super.onResume()
        container.onResume()
    }

    override fun onDestroy() {
        container.destroy()
        super.onDestroy()
    }
}
