# ADR-0002：core 模块做成纯 JVM，而不是 Android library

| | |
|---|---|
| 编号 | ADR-0002 |
| 日期 | 2026-09-02 |
| 状态 | 已采纳 |
| 影响范围 | 架构分层 / 构建 / 测试 |

## 决策

`:core:common` / `:core:domain` / `:core:network` / `:core:data` 全部用 `java-library` + `org.jetbrains.kotlin.jvm`，不套 `com.android.library`。只有 `:app` / `:core:designsystem` / `:feature:*` 是 Android 模块。

## 背景

[ADR-0001](0001-verification-loop.md) 把自动化验证限定成 JVM 单测。那么「哪些代码能被自测」就等价于「哪些代码不需要 Android 运行时」。默认把所有模块建成 `com.android.library` 是 Android 工程的惯例，但它会把这条线画在错的地方。

Retrofit、OkHttp、kotlinx.serialization、coroutines 都是普通 JVM 库，不需要 Android 运行时。

## 候选方案

| 方案 | 优势 | 代价 | 关键风险 |
|---|---|---|---|
| A（选中）core 纯 JVM | 测试是普通 `test` task，秒级、无 `android.jar` 参与；用错 Android API 会当场编译不过 | 想用 `Context` / `SharedPreferences` 时必须先重新设计而不是随手 import | 后续要加 Room 之类必须依赖 Android 的东西时，得新开一个 Android 模块 |
| B core 全用 `com.android.library` | 惯例，未来加什么都能塞 | 测试走 `testDebugUnitTest`，慢且掺进 `android.jar` stub；「不小心依赖 Android」不会被发现 | 业务逻辑逐渐渗入 Android API，可自测的部分越来越少 |

实测支撑：`:core:*` 的 `test` task 在增量情况下秒级完成，`:app:testDebugUnitTest` 明显更慢。

## 理由

纯 JVM 模块在这里起的是**围栏**作用，不只是速度优化。一旦有人在 `:core:domain` 里 `import android.content.Context`，编译立刻失败——这个信号比 code review 可靠，尤其在没有人逐行看 AI 产出的情况下。方案 B 的灵活性正是这里不想要的东西。

## 后果

- 接受了什么代价：不引入 Room。DAO 的验证绕不开设备或 Robolectric，两者都不满足 ADR-0001。持久化接口先在 `:core:domain` 留位置，实现等有设备再做。
- 引入了什么依赖 / 锁定：这些模块必须自己 apply KGP——AGP 9 的内置 Kotlin 只对 Android 模块生效。同时为了让 `:app:lint` 不把它们当外部依赖跳过，额外 apply 了 `com.android.lint`。
- 什么条件下重新评估：需要在 core 层引入必须依赖 Android 运行时的能力（Room、DataStore、WorkManager）时。做法应是新增一个 Android 模块承接，而不是把现有 core 模块整体改成 Android library。

## 参考

- `build-logic/src/main/kotlin/JvmLibraryConventionPlugin.kt`
- `settings.gradle.kts` 里按「能否在 JVM 上测」分组的注释
