package net.xiaoluzhu.crab

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import net.xiaoluzhu.crab.container.CrabLog
import net.xiaoluzhu.crab.webview.CrabContainer

/**
 * 容器的宿主。**只做装配、生命周期、再次启动与两个界面之间的切换**，判定逻辑一条都不在这里
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
                // 到底了：暂时关掉自己再重新分发，交回默认行为。不关就会被自己再接一次，表现是返回键
                // 彻底失灵——按了没反应比退错地方更难查。
                //
                // 只关一下、分发完马上打开，不能 remove()：Android 12 起，带 MAIN + LAUNCHER 的任务根
                // 在这里是把任务挪到后台，实例不销毁（`Activity.onBackPressed` 的 javadoc）。再点图标
                // 回来走的是 onNewIntent、没有 onCreate 再挂一次，remove() 掉之后第二页上的返回键就
                // 直接退到桌面了。singleTask 之下回来的一定是这个实例，这一格因此必踩。
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
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

    /**
     * 再次启动（`launchMode = singleTask`）：桌面图标、`am start -n`、将来的通知与深链接都落到这里，
     * 容器不换、页面不重载。新请求带来的内容怎么处理 pro 留给 FE 接入时定，所以这里只记一行。
     *
     * 那一行是给「再次启动」那格看的诊断，不是契约：pro 要核「新请求有没有走到这个回调」，而它在屏幕上
     * 什么都不改。前缀因此写成字面量，与 `CrabContainer` 里那行 `CRAB-ENV` 同理，不从 `CrabLog` 派生。
     * `Intent.toString()` 带上 action、categories、flags，`FLAG_ACTIVITY_BROUGHT_TO_FRONT` 也在里面。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(CrabLog.TAG, "CRAB-ENV onNewIntent $intent")
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
