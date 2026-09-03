package com.heydearnono.hybrid.core.bridge.port

/*
 * port：能力里真正碰 Android 的那一小半。
 *
 * 这些接口的实现放在 `:core:webview` / `:feature:web` / `:app`，在当前环境里
 * 验证不了（没有设备、没有 AVD）。所以对实现有一条硬要求：只允许是几行直线代码，
 * 不许有分支、不许有状态机。分支要放到 handler 里，那边能在 JVM 上测。
 */

/**
 * 发给 JS 的设备与应用信息。
 *
 * 字段刻意只放「不能用来追踪用户」的信息：没有 Android ID、IMEI、MAC、广告 ID。
 * 加字段之前先回答一个问题——装进这个容器的任意页面拿到它会怎样。
 */
data class DeviceInfo(
    val osVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val locale: String,
)

/** 读设备与应用信息。实现就是读几个 `Build.*` 字段加 PackageInfo。 */
fun interface DeviceInfoProvider {
    fun snapshot(): DeviceInfo
}

/** 弹一个 toast。suspend 是因为实现必须切到主线程。 */
fun interface Toaster {
    suspend fun show(
        text: String,
        long: Boolean,
    )
}

/**
 * 当前这个 web 页自己。
 *
 * 实现是页面的 ViewModel，所以它是**每个页面一个**，不是单例——
 * 见 [com.heydearnono.hybrid.core.bridge.di.BridgeDispatcherFactory]。
 */
interface PageHost {
    fun close()

    fun setTitle(title: String)
}

/** 跳到原生页面。suspend 是因为实现要碰 NavController，那是主线程的东西。 */
fun interface NativeRouter {
    /** @return false 表示这个路由名不在白名单里。 */
    suspend fun open(route: String): Boolean
}

/**
 * 给 JS 用的键值存储。
 *
 * suspend 是刻意的：唯一的实现要落盘，把「这个调用可能慢」写进签名里，
 * 而不是让调用方去猜。
 */
interface KeyValueStore {
    suspend fun get(key: String): String?

    suspend fun set(
        key: String,
        value: String,
    )

    suspend fun remove(key: String)
}
