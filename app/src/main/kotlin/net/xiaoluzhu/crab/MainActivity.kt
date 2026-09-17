package net.xiaoluzhu.crab

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.xiaoluzhu.crab.container.ContainerState
import net.xiaoluzhu.crab.webview.CrabContainer

/**
 * 容器的宿主。**只做装配、生命周期与两个界面之间的切换**，判定逻辑一条都不在这里
 * （错误态怎么来的、能不能重试、要不要换 WebView 全在 `:core:container`）。
 */
class MainActivity : ComponentActivity() {
    /** Compose 读的两份可变状态。容器在 [onCreate] 里才造，所以它们必须先于容器存在。 */
    private var containerState by mutableStateOf<ContainerState>(ContainerState.Loading)
    private var hostedWebView by mutableStateOf<WebView?>(null)

    private lateinit var container: CrabContainer

    /**
     * 返回键默认直接结束 Activity——页面里的历史一步都回不了，`nav-back` 那条必红。
     */
    private val backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (container.canGoBack()) {
                    container.goBack()
                    return
                }
                // 到底了：先摘掉自己再重新分发，交回默认行为（结束 Activity）。
                // 不摘就会被自己再接一次，表现是返回键彻底失灵——按了没反应比退错地方更难查。
                remove()
                onBackPressedDispatcher.onBackPressed()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container =
            CrabContainer(
                context = this,
                isDebugBuild = BuildConfig.DEBUG,
                versionName = BuildConfig.VERSION_NAME,
                onStateChanged = { containerState = it },
                onWebViewReplaced = { hostedWebView = it },
            )
        hostedWebView = container.webView
        onBackPressedDispatcher.addCallback(this, backCallback)
        container.loadEntry()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = containerState
                    val webView = hostedWebView
                    if (state is ContainerState.Error) {
                        ErrorScreen(onRetry = { container.onRetry() })
                    } else if (webView != null) {
                        // key 挂在实例上：渲染进程终止后换的是另一个 WebView，
                        // 不换 key 的话 AndroidView 会继续挂着已经 destroy 的那个，表现是一块白板
                        key(webView) {
                            AndroidView(
                                factory = { webView },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
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

/**
 * 错误界面：加载失败、SSL 错误、渲染进程终止**共用这一个**，文案与按钮取 pro 的取值表。
 * 场景与错误码只进 logcat（`CRAB-ERR <场景> <错误码>`），不上屏——屏幕上是给人看的，
 * 错误码是给查的人看的。
 */
@Composable
private fun ErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.crab_error_message))
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = stringResource(R.string.crab_error_retry))
        }
    }
}
