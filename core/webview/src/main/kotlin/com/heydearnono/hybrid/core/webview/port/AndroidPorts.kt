package com.heydearnono.hybrid.core.webview.port

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.heydearnono.hybrid.core.bridge.port.DeviceInfo
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.Toaster
import com.heydearnono.hybrid.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * port 的 Android 实现。
 *
 * 这些类不可自测，所以每个方法都必须是几行直线代码：没有分支、没有状态。
 * 参数校验和错误映射全在 `:core:bridge` 的 handler 里，那边跑在 JVM 上。
 */

/**
 * @param appVersionName 应用版本，由 `:app` 传入——只有它能拿到自己的 PackageInfo，
 *   而在这里读会带出 API 28 的 `longVersionCode` 版本判断，就是一个分支。
 */
class AndroidDeviceInfoProvider(
    private val appVersionName: String,
    private val appVersionCode: Long,
) : DeviceInfoProvider {
    override fun snapshot(): DeviceInfo =
        DeviceInfo(
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            locale = Locale.getDefault().toLanguageTag(),
        )
}

/**
 * @param context 必须是 application context。传 Activity 的话，页面销毁后还没弹完的 toast
 *   会把它一起拖住。
 */
class AndroidToaster(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : Toaster {
    override suspend fun show(
        text: String,
        long: Boolean,
    ) = withContext(dispatchers.main) {
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
