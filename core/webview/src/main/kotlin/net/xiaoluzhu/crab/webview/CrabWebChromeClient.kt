package net.xiaoluzhu.crab.webview

import android.app.AlertDialog
import android.content.Context
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import net.xiaoluzhu.crab.container.ContainerCoordinator
import net.xiaoluzhu.crab.container.CrabLog
import net.xiaoluzhu.crab.container.DialogType

/**
 * 页面提出的四类要求：console、开新窗口、JS 对话框、权限。**判定与日志都在
 * [ContainerCoordinator]**，这里只负责弹框、拒绝、把回调答复掉。
 *
 * 两条不许错的讲究：
 * 1. **每个 `JsResult` 必须被回一次。** 不回等于页面里那句 `alert()` 永远不返回，脚本停在那儿——
 *    表现是探针页少几行结果，看起来像断言没跑。所以三种对话框都把答复挂在 `OnDismissListener` 上：
 *    按按钮、按返回键、点外面，走的都是它
 * 2. **权限一律拒绝，但要先让回调被调用。** 关掉 `setGeolocationEnabled` 之类的开关同样「拿不到定位」，
 *    但那是静默的，与「页面写错了」分不开
 *
 * 用的是框架的 [AlertDialog] 而不是 appcompat 那个：`:app` 的主题是
 * `@android:style/Theme.Material.Light.NoActionBar`，appcompat 的对话框在非 `Theme.AppCompat` 主题下
 * 直接抛异常。按钮文案取 `android.R.string.ok` / `cancel`，系统已经翻译好，这个模块因此不需要 `res/`。
 */
internal class CrabWebChromeClient(
    private val context: Context,
    private val coordinator: ContainerCoordinator,
    private val forwardConsoleToLogcat: Boolean,
) : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        if (forwardConsoleToLogcat) {
            Log.i(CrabLog.TAG, consoleMessage.message())
        }
        // 返回 true = 自己处理了，WebView 不再打默认那行（默认那行也会带上页面内容）。
        return true
    }

    /** 返回 false = 没建窗口，这次请求被忽略。 */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        coordinator.onWindowOpenRequest(view.url ?: "")
        return false
    }

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        coordinator.onDialog(DialogType.ALERT)
        AlertDialog
            .Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { result.confirm() }
            .show()
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        coordinator.onDialog(DialogType.CONFIRM)
        var confirmed = false
        AlertDialog
            .Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                confirmed = true
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { if (confirmed) result.confirm() else result.cancel() }
            .show()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean {
        coordinator.onDialog(DialogType.PROMPT)
        val input = EditText(context).apply { setText(defaultValue ?: "") }
        var confirmed = false
        AlertDialog
            .Builder(context)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                confirmed = true
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener {
                if (confirmed) result.confirm(input.text.toString()) else result.cancel()
            }.show()
        return true
    }

    /** 摄像头、麦克风等：先记下请求了什么，再整个拒绝。 */
    override fun onPermissionRequest(request: PermissionRequest) {
        coordinator.onPermissionRequest(request.resources.toList())
        request.deny()
    }

    /**
     * 定位单独一条回调，与 [onPermissionRequest] 两处都得接：地理位置不走 `PermissionRequest`。
     * `invoke(origin, false, false)` 的第三个参数是「要不要记住这个决定」——不记，下次还要留一行日志。
     */
    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        coordinator.onPermissionRequest(listOf(PERMISSION_GEOLOCATION))
        callback.invoke(origin, false, false)
    }

    private companion object {
        /** 定位没有对应的 `PermissionRequest.RESOURCE_*` 常量，只能自己起一个词。 */
        const val PERMISSION_GEOLOCATION: String = "geolocation"
    }
}
