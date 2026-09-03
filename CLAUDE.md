# CLAUDE.md

生产级 Android 混合容器基座。多模块 Gradle 工程，**全部由 AI 开发**。

包名是 `com.heydearnono.hybrid`，工程名 `android-hybrid-container`。

## 验证闭环（最重要的一节）

当前**没有真机、没有 AVD、没有 cmdline-tools**，所以 AI 能自己跑的验证只有三样：**编译、JVM 单测、静态检查**。

改完代码跑：

```bash
./scripts/check.sh          # spotlessCheck → assembleDebug → test → lint
./scripts/check.sh --fix    # 先 spotlessApply 再跑上面这串
```

绿了才算完。绿的定义：格式无 diff、APK 产出、单测全过、lint 零 error。

**做不到的事**：装 APK、运行 App、看 UI、任何 instrumented 测试。这些只能人工在 Android Studio 里做，不要假装验证过。要打开这条路，需要用户在 SDK Manager 里装 `cmdline-tools`，之后才能用 `sdkmanager` 拉 system-image、起 AVD。

**WebView 是这条边界上最危险的地方。** `android.webkit` / `androidx.webkit` 在单测里是 stub，而 `unitTests.isReturnDefaultValues = true` 会让它们静静返回默认值而不是报错——所以给容器写的测试**会绿，但那个绿是假的**，比没有测试更坏。`:core:webview` 因此刻意不带单测；要验证容器行为只能人工跑。对应地，容器里不许写业务分支（见「JSBridge 与容器」一节）。

这条约束直接决定架构（见下一节）：业务逻辑尽量压进纯 JVM 模块，因为那是能自测的部分。

## 模块结构

按「能否在 JVM 上测」切分，不是按惯例切：

| 模块 | 插件 | 内容 |
|---|---|---|
| `:app` | `base.android.application` + `base.android.compose` | 薄壳：`BaseApplication`(startKoin)、`MainActivity`、NavHost、DI 装配 |
| `:core:common` | `base.jvm.library` | `Outcome` / `AppError` / `DispatcherProvider` |
| `:core:domain` | `base.jvm.library` | 实体、use case、repository **接口** |
| `:core:network` | `base.jvm.library` | Retrofit + OkHttp + kotlinx.serialization、DTO |
| `:core:data` | `base.jvm.library` | repository 实现、DTO→domain 映射 |
| `:core:bridge` | `base.jvm.library` | JSBridge：报文 DTO/codec、dispatcher、registry、origin 策略、全部 handler、port 接口、`FileKeyValueStore` |
| `:core:designsystem` | `base.android.library` + `base.android.compose` | `BaseTheme`、通用组件 |
| `:core:webview` | `base.android.library` + `base.android.compose` | WebView 容器、`WebViewAssetLoader`、bridge 注入、port 的 Android 实现。**不可自测的部分全在这里** |
| `:feature:articles` | `base.android.library` + `base.android.compose` | 样例：联网列表页 |
| `:feature:web` | `base.android.library` + `base.android.compose` | 全屏 web 页：三态 ViewModel + `PageHost` 实现 |

五个 `core` 是纯 JVM 的——Retrofit / OkHttp / kotlinx.serialization 都是普通 JVM 库。**大部分逻辑因此可自测。**

分层规则：

- 依赖方向单向向下：`:app` → `:feature` → `:core:data` → `:core:domain` → `:core:common`；容器一侧是 `:app` → `:feature:web` → `:core:webview` → `:core:bridge` → `:core:common`。`:core:domain` 零依赖（除 `:core:common`），且**不认识 DI 框架**——use case 在 `:core:data` 的 Koin module 里声明。
- `IOException` / `HttpException` / `SerializationException` 只允许出现在 `:core:network` 和 `:core:data` 内部。跨出 repository 前必须翻译成 `AppError`。
- `:feature` 之间不互相依赖。要共享就下沉到 `:core`。
- 只有 `:app` 知道「接口的实现是谁」。
- 新增模块：`settings.gradle.kts` 注册 + 套一个 convention plugin，**不在模块里重复写 Android 配置**。
- 公开类的**超类型**来自 `implementation` 依赖时，消费方会报 `Cannot access '...' which is a supertype of '...'`。所以 `:core:webview` 对 `:core:bridge`、`:feature:web` 对 `:core:webview` 用的是 `api`。

## JSBridge 与容器

通道是 `WebViewCompat.addWebMessageListener`（ADR-0005），能力拆成「纯 JVM handler + 极薄 port」（ADR-0006）。改这块之前先读这两篇。

- **能力 = handler + port。** 参数校验、结果编码、错误映射全部写在 `:core:bridge` 的 handler 里（可测）；碰 Android API 的部分抽成 port 接口，实现放 `:core:webview` / `:feature:web` / `:app`。**port 实现只允许是几行直线代码，不许有分支、不许有状态机**——那部分代码没有测试兜着。
- 同理，容器（`HybridWebView` / `HybridWebViewClient`）只把 WebView 回调原样转给 `HybridWebViewListener`，**状态迁移规则全部放在 `WebPageViewModel` 里**，因为它能在 JVM 上测。
- **默认拒绝。** 新增一个能力要同时在 `BridgeDispatcherFactory.handlers()` 注册、在 `app/.../bridge/BridgeSecurity.kt` 的白名单加一行。少任何一处，`BridgeWiringTest` 会红。
- **origin 授权是双闸门**，两处必须同源：`BridgeSecurityConfig.allowedOriginRules`（喂 `addWebMessageListener`）和 `BridgePolicy`（喂 dispatcher）。`allowAnyOrigin` 在任何构建类型下都不许打开。iframe（`isMainFrame == false`）不给 bridge。
- **不静默降级。** `WEB_MESSAGE_LISTENER` 不支持时页面进 `BRIDGE_UNAVAILABLE` 错误态，绝不回退 `addJavascriptInterface`。
- **`app/src/main/assets/demo/bridge.js` 与 Kotlin 侧的对应关系编译器管不到**：注入对象名（`BRIDGE_JS_OBJECT_NAME` = `__hybridNative`）和报文格式（`BridgeMessage.kt`）。改一边必须改另一边。`.js` / `.html` 不在 Spotless 范围内，也没有 JS lint。
- assets 页面必须经 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/assets/...` 加载。`file://` 页面的 `sourceOrigin` 是字符串 `"null"`，写不进白名单，bridge 一条都不通。
- 查 `androidx.webkit` 的 API 形状读 sources jar，别凭记忆：`~/.gradle/caches/modules-2/files-2.1/androidx.webkit/`。

## 构建配置：AGP 9 的坑（全部实测过，不要凭记忆改）

版本三者互相咬死：**AGP 9.2.1 / Gradle 9.4.1 / 内置 Kotlin 2.2.10**（后者读自 `gradle-9.2.1.pom` 的 `kotlin-gradle-plugin` 依赖）。

1. **Android 模块不要 apply `org.jetbrains.kotlin.android`。** AGP 9 内置 Kotlin 且默认开启，再 apply KGP 会冲突。
2. **纯 JVM 模块反过来必须 apply `org.jetbrains.kotlin.jvm`**——内置 Kotlin 只对 AGP 模块生效。
3. **内置 Kotlin 不带 Compose 编译器插件。** 只要 `buildFeatures.compose = true`，就必须额外 apply `org.jetbrains.kotlin.plugin.compose`，且**版本必须等于 AGP 内置的 Kotlin 版本**（2.2.10）。不加会在 configuration 阶段报 "the Compose Compiler Gradle plugin is required"。
4. **`compileSdk` 用 block 写法**，旧的 `compileSdkVersion(...)` 已标记 AGP 10 移除：

   ```kotlin
   android { compileSdk { version = release(37) { minorApiLevel = 0 } } }
   ```

5. **`buildFeatures.buildConfig` 在 AGP 9 默认 `false`。** 要用 `BuildConfig.DEBUG` 得显式打开（`:app` 已开）。
6. **AGP 9 的 `CommonExtension` 没有泛型参数**，且 `defaultConfig` / `lint` / `compileOptions` / `testOptions` / `buildFeatures` 只有 `val`、没有接 lambda 的重载。所以 convention plugin 里只能用属性赋值（`extension.lint.abortOnError = true`），不能写 block。唯一是函数的是 `compileSdk(action)`。
7. `kotlinOptions {}` 已移到顶层 `kotlin { compilerOptions {} }`。内置 Kotlin 注册的仍是 KGP 的 `KotlinAndroidProjectExtension`（实测），所以 convention plugin 能配 `jvmTarget`。
8. **不使用 `android.builtInKotlin=false` / `android.newDsl=false`** 这两个逃生开关，AGP 10 会失效。

依赖坐标上踩过一个坑：**测试依赖用 `kotlin-test-junit`，不能用 `kotlin-test`**。后者是多平台聚合坐标，靠 Gradle variant 属性选测试框架，而 AGP 内置 Kotlin 不设这个属性，会解析到拿不到 `kotlin.test.Test` 的变体。

## 版本与依赖

- **所有版本号只允许写在 `gradle/libs.versions.toml`**，包括 SDK level（convention plugin 从版本目录读）。
- **仓库只在 `settings.gradle.kts` 声明**，`FAIL_ON_PROJECT_REPOS` 已开，模块里再写 `repositories` 直接构建失败。
- 根 `build.gradle.kts` 里的插件全部 `apply false`，只为把 AGP/KGP 放进构建 classpath——`build-logic` 是 `compileOnly` 编译的，运行期需要它们在。
- `build-logic` 通过 `pluginManagement { includeBuild(...) }` 组合进来。convention plugin 写成 `Plugin<Project>` 类 + `pluginManager.apply(id)`，不用预编译脚本插件。
- **升级依赖是显式决定**：改版本目录再跑 `./scripts/check.sh`。lint 的 `AndroidGradlePluginVersion` / `GradleDependency` / `NewerVersionAvailable` 已关掉——它们的结果取决于「今天 Maven 上有什么」，会让同一份代码今天绿明天黄。

## 测试约定

- JUnit 4 + `kotlin.test`，全仓统一（`base.jvm.library` / `base.android.*` 都自动带上测试依赖，模块里不用重复声明）。
- **用手写 fake，不引 MockK 一类 mock 框架。** 理由和「全 AI 开发」直接相关：接口变了 fake 会编译不过、错误显式且指向准确；mock 只在运行期炸，更容易误判。
- 网络层用 `mockwebserver3`（okhttp 5，纯 JVM），能真实覆盖序列化和状态码分支。`MockResponse` 是 builder：`MockResponse.Builder().code(200).body("...").build()`。
- ViewModel 测试在 JVM 上跑（`testDebugUnitTest`）。`viewModelScope` 用 `Dispatchers.Main`，必须先 `Dispatchers.setMain(UnconfinedTestDispatcher())`，`@AfterTest` 里 `resetMain()`。
- Koin 依赖图用 `koinApplication { modules(...) }` 后**逐个 `get<T>()`** 来验证。只 `modules(...)` 不构造对象，少注册一个 `single` 也不会报。`:app` 的 `AppModulesTest` 只验证「能加载、无重复定义」，因为 ViewModel 的解析需要 `ViewModelStoreOwner`（设备上的事）。
- 测试方法名用中文反引号，描述行为不描述实现。ktlint 的 `function-naming` 已为此关掉。
- 单测里 `android.jar` 的 stub 方法返回默认值（`unitTests.isReturnDefaultValues = true`），否则会抛 "not mocked"。

当前 98 个测试，覆盖：use case 排序/过滤/错误透传、Retrofit 序列化与状态码、repository 的三种异常翻译、DI 图与 bridge 白名单接线、ViewModel 状态迁移、bridge 的报文编解码 / origin 匹配 / 分发与错误映射 / 8 个 handler 的参数校验 / 文件存储。

## 格式与静态检查

- Spotless + ktlint 在**根项目**统一配置，覆盖所有模块和 `build-logic`。规则细节在 `.editorconfig`，IDE 和 ktlint 读同一份。
- 有两条 ktlint 规则是刻意关掉的：`function-naming`（中文测试名）、`filename`（一个文件多个顶层声明是有意的，比如 `Outcome.kt` 里带扩展函数）。
- Android Lint 覆盖全部 10 个模块——纯 JVM 模块额外 apply 了 `com.android.lint`，不加的话 `:app:lint` 会把它们当外部依赖跳过（日志里会明说 "Lint will treat :core:xxx as an external dependency"），等于大部分业务代码没被检查。
- 抑制 lint 优先按路径写进模块的 `lint.xml`（例子：`app/lint.xml` 只为 adaptive icon 关掉 `ObsoleteSdkInt`），不要在 convention plugin 里全局 disable。

## 环境事实

- `java` 不在 PATH（macOS `/usr/bin/java` 只是存根，用 `command -v` 探测会误报成功，要看退出码）。用 Android Studio 自带 JBR 21：
  `/Applications/Android Studio.app/Contents/jbr/Contents/Home`。`scripts/check.sh` 会注入这个 `JAVA_HOME`；**不要写进 `gradle.properties`**，那是机器特定路径。
- SDK 在 `~/Library/Android/sdk`。**只装了 `android-36.1` 和 `android-37.0`，没有 `android-36` / `android-37`**，所以 `compileSdk` 必须带 `minorApiLevel`。
- build-tools：36.0.0 / 36.1.0 / 37.0.0。
- 无 `cmdline-tools`（`sdkmanager` / `avdmanager` 不可用）、无 NDK（原生模块编不了）、无 system-image、无 AVD、无真机。**需要装任何 SDK 组件时先提示用户，不要擅自安装。**
- Gradle 用 wrapper，本地已缓存 `gradle-9.4.1-bin`。
- 环境体检：`./scripts/env-probe.sh`。
- `developer.android.com` 在本环境连不上。查 AGP/Gradle 的 API 形状时，直接读 `~/.gradle/caches/modules-2/` 里的 `-sources.jar` 和 `.pom`，比搜索可靠。

## 写作与语言

- 回复、注释、文档用中文。代码标识符、API 名、命令保持英文原文。
- 注释写「为什么」，不写「做了什么」。尤其是绕过某个坑的地方，要写清坑是什么。
- 结论先行。不写与正文重复的总结段。
- 不编造版本号和 API 名。拿不准的去读本地 jar 或搜官方文档。

## 边界

- 不提交 API Key、签名密钥。`.gitignore` 已拦 `*.jks` / `*.keystore` / `keystore.properties` / `local.properties`。密钥走 `local.properties` 或环境变量，生产环境不落客户端。
- 不擅自 `git commit`。要提交时由用户明确要求。
- 架构决策写进 `docs/adr/`，模板见 `docs/adr/TEMPLATE.md`。**已定过的决策不要重新争论，先读 ADR。**
