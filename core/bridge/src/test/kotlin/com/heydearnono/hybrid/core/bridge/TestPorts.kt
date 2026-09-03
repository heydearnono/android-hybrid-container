package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.bridge.port.DeviceInfo
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.bridge.port.PageHost
import com.heydearnono.hybrid.core.bridge.port.Toaster
import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.DispatcherProvider
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonElement

/**
 * port 的手写 fake（ADR-0004）。每个都只做两件事：记下被怎么调的、按需给个答案。
 *
 * 这些 fake 顺带证明了一件事：port 接口窄到用十几行就能实现完，
 * 那么它在 Android 那边的真实现也不会有藏得住 bug 的空间。
 */
internal fun sampleDeviceInfo() =
    DeviceInfo(
        osVersion = "16",
        sdkInt = 37,
        manufacturer = "Google",
        model = "Pixel 9",
        appVersionName = "1.0.0",
        appVersionCode = 1L,
        locale = "zh-CN",
    )

internal class FakeDeviceInfoProvider(
    private val info: DeviceInfo = sampleDeviceInfo(),
) : DeviceInfoProvider {
    override fun snapshot(): DeviceInfo = info
}

internal class FakeToaster : Toaster {
    val shown: MutableList<Pair<String, Boolean>> = mutableListOf()

    override suspend fun show(
        text: String,
        long: Boolean,
    ) {
        shown += text to long
    }
}

internal class FakePageHost : PageHost {
    var closed: Boolean = false
        private set

    val titles: MutableList<String> = mutableListOf()

    override fun close() {
        closed = true
    }

    override fun setTitle(title: String) {
        titles += title
    }
}

internal class FakeNativeRouter(
    private val known: Set<String> = setOf("articles"),
) : NativeRouter {
    val opened: MutableList<String> = mutableListOf()

    override suspend fun open(route: String): Boolean {
        opened += route
        return route in known
    }
}

internal class InMemoryKeyValueStore : KeyValueStore {
    private val entries: MutableMap<String, String> = mutableMapOf()

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun set(
        key: String,
        value: String,
    ) {
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }
}

internal class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

/** 回包里的 JSON。JsonElement.toString() 出来的就是紧凑格式，正好是发给 JS 的那份。 */
internal fun Outcome<JsonElement>.json(): String = (this as Outcome.Success).value.toString()

internal fun Outcome<*>.rejectedCode(): String = ((this as Outcome.Failure).error as AppError.Rejected).code
