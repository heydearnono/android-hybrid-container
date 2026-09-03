package com.heydearnono.hybrid.di

import androidx.core.content.pm.PackageInfoCompat
import com.heydearnono.hybrid.bridge.AppNativeRouter
import com.heydearnono.hybrid.bridge.appBridgeSecurityConfig
import com.heydearnono.hybrid.core.bridge.di.bridgeModule
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.data.di.dataModule
import com.heydearnono.hybrid.core.network.di.networkModule
import com.heydearnono.hybrid.core.webview.di.webViewModule
import com.heydearnono.hybrid.core.webview.port.AndroidDeviceInfoProvider
import com.heydearnono.hybrid.feature.articles.di.articlesModule
import com.heydearnono.hybrid.feature.web.di.webModule
import com.heydearnono.hybrid.navigation.NATIVE_ROUTE_TARGETS
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 整个 App 的依赖图入口。新增 feature 就在这里加一行。
 *
 * `loggingEnabled` 由调用方按 buildType 传，库模块不自己判断环境。
 */
fun appModules(loggingEnabled: Boolean): List<Module> =
    listOf(
        networkModule(loggingEnabled = loggingEnabled),
        dataModule(),
        bridgeModule(appBridgeSecurityConfig()),
        webViewModule(),
        bridgeHostModule(),
        articlesModule(),
        webModule(),
    )

/**
 * bridge 的两个 port 只有 `:app` 能提供：`DeviceInfoProvider` 要应用版本号，
 * `NativeRouter` 要导航图。
 */
private fun bridgeHostModule() =
    module {
        single<DeviceInfoProvider> {
            val context = androidContext()
            // 在这里读而不是在 :core:webview 里读：versionCode 的 long 形式要 API 28，
            // 在库里读就得加一个 SDK_INT 判断，而那是不可自测的代码。
            // PackageInfoCompat 把这个判断收进 androidx，这边只剩一行直线调用。
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            AndroidDeviceInfoProvider(
                appVersionName = info.versionName.orEmpty(),
                appVersionCode = PackageInfoCompat.getLongVersionCode(info),
            )
        }

        // 两个绑定指向同一个实例：BaseNavHost 要按具体类型拿到它去装 NavController，
        // bridge 只认接口。
        single { AppNativeRouter(NATIVE_ROUTE_TARGETS, get()) }
        single<NativeRouter> { get<AppNativeRouter>() }
    }
