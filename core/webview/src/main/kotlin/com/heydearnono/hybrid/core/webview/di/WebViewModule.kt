package com.heydearnono.hybrid.core.webview.di

import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.port.Toaster
import com.heydearnono.hybrid.core.bridge.storage.FileKeyValueStore
import com.heydearnono.hybrid.core.webview.port.AndroidToaster
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

/** JS 侧 `storage.*` 的落盘位置，在 app 私有目录下。 */
private const val BRIDGE_KV_DIR = "bridge-kv"

/**
 * 需要 Context 的那几个 port 绑定。
 *
 * 不在这里绑 `DeviceInfoProvider` / `NativeRouter`：前者要应用版本号、后者要导航图，
 * 两样都只有 `:app` 有。`DispatcherProvider` 由 `dataModule()` 提供，两个 module 必须一起装。
 */
fun webViewModule() =
    module {
        single<Toaster> { AndroidToaster(androidContext(), get()) }
        single<KeyValueStore> { FileKeyValueStore(File(androidContext().filesDir, BRIDGE_KV_DIR), get()) }
    }
