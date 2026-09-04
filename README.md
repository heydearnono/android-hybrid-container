# android-hybrid-container

生产级 Android 混合容器基座。多模块 Gradle 工程，分层 + DI + 网络 + 导航 + 主题 + 测试 + 静态检查开箱可用，带一个 **WebView 容器 + JSBridge** 和一个端到端联网列表页样例证明链路通。

协作约定见 [CLAUDE.md](CLAUDE.md)，架构决策见 [docs/adr/](docs/adr/)，**上手前先扫一遍 [踩坑手册](docs/PITFALLS.md)**。

## 快速开始

```bash
./scripts/env-probe.sh      # 体检：JDK / SDK / 设备
./scripts/check.sh          # 唯一的验证入口：格式 → 编译 → 单测 → lint
./scripts/check.sh --fix    # 同上，但先自动修格式
```

`java` 不在 PATH，`check.sh` 会自动用 Android Studio 自带的 JBR。产物在 `app/build/outputs/apk/debug/`。

卡住了先查 [踩坑手册](docs/PITFALLS.md)——Studio sync 报 AGP 版本不兼容、`./gradlew` 找不到 java、单测「假绿」这些都在里面。

## 模块

```
:app                  薄壳：Application(startKoin)、MainActivity、NavHost、DI 装配
:core:common          纯 JVM   Outcome / AppError / DispatcherProvider
:core:domain          纯 JVM   实体、use case、repository 接口
:core:network         纯 JVM   Retrofit + OkHttp + kotlinx.serialization
:core:data            纯 JVM   repository 实现、DTO → domain 映射
:core:bridge          纯 JVM   JSBridge 协议、分发器、origin 策略、能力 handler 与 port 接口
:core:designsystem    Android  BaseTheme、通用组件
:core:webview         Android  WebView 容器、bridge 注入、port 的 Android 实现
:feature:articles     Android  样例：联网列表页（loading / empty / error / content）
:feature:web          Android  全屏 web 页：三态 + 标题 + page.* 能力的宿主
build-logic/                   convention plugins，模块构建配置只在这里改
```

依赖方向单向向下，`:core:domain` 不认识 DI 框架，异常在 `:core:data` 的边界翻译成 `AppError`。五个 `core` 里的纯 JVM 模块是刻意的——业务逻辑因此能在没有设备的环境里跑测试，理由见 [ADR-0002](docs/adr/0002-core-modules-pure-jvm.md)。

## 混合容器

起始页是装在 `app/src/main/assets/demo/` 的自检页，通过 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/assets/demo/index.html` 加载。页面上每个按钮对应一个原生能力，回包直接打在页面上。

首批能力：`device.info`、`ui.toast`、`page.close` / `page.setTitle`、`router.open`、`storage.get` / `set` / `remove`。

三条不打算让步的规则：

- **默认拒绝。** 能力白名单按 origin 逐个列出（`app/.../bridge/BridgeSecurity.kt`），新增能力不来这里加一行就谁也调不到。
- **双闸门。** 同一份配置同时喂给 `addWebMessageListener` 的 `allowedOriginRules` 和分发层的 `BridgePolicy`，`BridgeWiringTest` 锁住两者同源。iframe（`isMainFrame == false`）不给 bridge。
- **不静默降级。** 系统 WebView 不支持 `WEB_MESSAGE_LISTENER` 时页面进明确错误态，不回退到没有 origin 作用域的 `addJavascriptInterface`。

选型与分层理由见 [ADR-0005](docs/adr/0005-jsbridge-transport.md)、[ADR-0006](docs/adr/0006-capability-handler-port-split.md)。JS 侧 SDK 是手写的 `assets/demo/bridge.js`，不引 npm、不加构建步骤。

## 技术栈

Kotlin 2.2.10（AGP 9.2.1 内置）· Gradle 9.4.1 · Compose + Material 3 · Retrofit 3 + OkHttp 5 + kotlinx.serialization · Koin 4 · Navigation Compose · androidx.webkit 1.17.0 · JUnit 4 + kotlin.test + Turbine + MockWebServer · Spotless(ktlint) + Android Lint

版本号全部集中在 `gradle/libs.versions.toml`，不在别处硬编码。

## 架构决策记录

| | 决策 |
|---|---|
| [ADR-0001](docs/adr/0001-verification-loop.md) | 验证闭环限定为编译 + JVM 单测 + 静态检查 |
| [ADR-0002](docs/adr/0002-core-modules-pure-jvm.md) | core 模块做成纯 JVM，而不是 Android library |
| [ADR-0003](docs/adr/0003-di-koin.md) | DI 用 Koin，不用 Hilt |
| [ADR-0004](docs/adr/0004-hand-written-fakes.md) | 用手写 fake，不引 mock 框架 |
| [ADR-0005](docs/adr/0005-jsbridge-transport.md) | JSBridge 走 `addWebMessageListener`，按 origin 授权 |
| [ADR-0006](docs/adr/0006-capability-handler-port-split.md) | 能力拆成纯 JVM handler + 极薄 port，压缩不可验证面积 |

## 当前限制

自动化验证只覆盖编译、JVM 单测、静态检查（当前 98 个测试）。**APK 能否安装运行、UI 长什么样、instrumented 测试，全部需要人工在 Android Studio 里确认**——本环境没有真机、没有 AVD，也没有 `cmdline-tools` 去下载 system-image。要打开这条路，在 SDK Manager 里装 `cmdline-tools`。

容器层这条界线尤其硬：`android.webkit` / `androidx.webkit` 在单测里是 stub，且 `unitTests.isReturnDefaultValues = true` 会让它们静静返回默认值——**给 WebView 写的测试会绿，但那个绿是假的**。所以 `:core:webview` 刻意不带单测，能力的分支全在 `:core:bridge` 和 `WebPageViewModel` 里测。第一次上手请人工确认：demo 页能打开、每个按钮都有回包、`router.open('articles')` 能跳到原生列表页。

`bridge.js` 与 Kotlin 侧的注入对象名、报文格式靠人保证一致，`.js` / `.html` 不在 Spotless 覆盖范围内，也没有 JS lint。

未引入持久化（Room）：DAO 的验证绕不开设备，接口先在 `:core:domain` 留位置。JSBridge 首批不含离线包和 native / web 统一路由，`router.open` 只做白名单路由名的直接映射。
