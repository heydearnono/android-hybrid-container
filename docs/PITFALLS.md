# 踩坑手册

上手和改动这个基座时**真实撞过的坑**，按你会撞上的顺序排。每条只写三样：症状、为什么、怎么做。

日常协作约定见 [CLAUDE.md](../CLAUDE.md)，决策理由见 [docs/adr/](adr/)。这份文档不重复它们，只记「照着做也会踩的地方」。

## 先记住一件事

验证只有一个入口：

```bash
./scripts/check.sh          # spotlessCheck → assembleDebug → test → lint
./scripts/check.sh --fix    # 先自动修格式，再跑上面这串
```

绿的定义是四条全绿：**格式无 diff、APK 产出、单测全过、lint 零 error**。单独跑一个 `./gradlew assembleDebug` 通过不等于改完了——lint 和格式都能让 CI 红。

## 1. 打开工程

### Studio 报 "incompatible version (AGP 9.2.1) ... Latest supported version is AGP 9.0.0"

**症状**：Studio sync 直接失败，但命令行 `./scripts/check.sh` 全绿。

**为什么**：Studio ↔ AGP 是单向兼容——Studio 可以比 AGP 新，反过来不行。项目用 AGP 9.2.1（2026-04 发布），而报这个错的 Studio 上限是 AGP 9.0.0。Gradle CLI 不做这个检查，所以只有 IDE 会拦。

**怎么做**：升 Studio。`Help → Check for Updates`，升到 2026.1.4（Quail 4）或更新——它配套的 AGP stable 是 9.4.0，覆盖 9.2.1。项目一行都不用改。

**不要反过来降 AGP 到 9.0.x**：AGP 9.0 支持的最大 API level 是 36.1，而本项目 `compileSdk = 37.0`（AGP 9.2 的上限正好是 37.0）。降 AGP 就得连 compileSdk 一起退，纯自伤。

顺带两条容易被忽略的咬合关系：

- wrapper 里的 Gradle 9.4.1 **就是 AGP 9.2 要求的 min/default 版本**，不是随手选的。
- 想跟到 AGP 9.4.0，必须同时把 wrapper 升到 Gradle 9.6.0——这是独立的一次升级决定，不要顺手做。
- AGP 9.0.0 / 9.2.1 / 9.4.0 的 pom 里 `kotlin-gradle-plugin` **都是 2.2.10**，所以在 9.x 内部挪动 AGP 不会牵动 Kotlin 和 Compose 编译器插件版本。真正咬死的是 compileSdk 上限和 Gradle 配对这两处。

### `./gradlew` 起不来：找不到 java / JAVA_HOME 没设

**为什么**：`java` 不在 PATH。而且 macOS 的 `/usr/bin/java` 只是个存根（stub）——**用 `command -v java` 探测会误报成功**，必须看退出码（`java -version >/dev/null 2>&1`）。

**怎么做**：跑 `./scripts/check.sh`，它自己会注入 Android Studio 自带的 JBR。要手工敲 gradle 命令就自己导一下：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

**不要写进 `gradle.properties`**：那是机器特定路径，提交进去会污染别人的环境。

### SDK platform 对不上

本机只装了 `android-36.1` 和 `android-37.0`，**没有 `android-36` / `android-37`**。所以 `compileSdk` 必须带 `minorApiLevel`（见 `build-logic/src/main/kotlin/com/heydearnono/hybrid/buildlogic/AndroidConfig.kt`）：

```kotlin
extension.compileSdk {
    version = release(37) { minorApiLevel = 0 }
}
```

先跑 `./scripts/env-probe.sh` 体检 JDK / SDK / build-tools / 设备。**缺组件时由人决定装什么，不要让 AI 擅自安装。**

### 首次构建要联网

wrapper 发行版和全部依赖都得下载。仓库只在 `settings.gradle.kts` 声明（`google()` + `mavenCentral()`），别在模块里加（见第 4 节）。

`gradle/gradle-daemon-jvm.properties` 如果出现在工作区，它是 `updateDaemonJvm` 生成的本地产物，带机器无关的 foojay 下载地址——是否入库是一次独立决定，别顺手 commit。

## 2. 验证的边界：最容易被误导的一节

### 单测绿 ≠ 功能对

convention plugin 里开了 `testOptions.unitTests.isReturnDefaultValues = true`（不开的话 `android.jar` 的 stub 方法会抛 "not mocked"，单测根本跑不起来）。代价是：

**`android.webkit` / `androidx.webkit` 在单测里是 stub，会静静返回默认值而不是报错。** 所以给 WebView 容器写的单测**会绿，但那个绿是假的**——比没有测试更坏，因为它会让你以为验证过了。

`:core:webview` 因此**刻意不带单测**。这不是遗漏，是决定（[ADR-0001](adr/0001-verification-loop.md)）。

对应的架构约束，改容器时必须守住：

- 容器（`HybridWebView` / `HybridWebViewClient`）只把 WebView 回调**原样**转给 `HybridWebViewListener`，状态迁移规则全部放在 `WebPageViewModel` 里——因为它能在 JVM 上测。
- port 实现只允许是**几行直线代码**，不许有分支、不许有状态机（[ADR-0006](adr/0006-capability-handler-port-split.md)）。参数校验、结果编码、错误映射全部写在 `:core:bridge` 的 handler 里，那部分有测试兜着。

### 自动化验证做不到的事

装 APK、运行 App、看 UI、任何 instrumented 测试——本机没有真机、没有 AVD、也没有 `cmdline-tools` 去下 system-image。这些只能人工在 Android Studio 里做，**不要假装验证过**。要打开这条路，先在 SDK Manager 里装 `cmdline-tools`。

第一次上手，请人工确认这三件事：demo 页能打开、每个按钮都有回包、`router.open('articles')` 能跳到原生列表页。

## 3. 改构建配置：AGP 9 的坑

这些全部实测过，**不要凭对 AGP 8 的记忆改**。版本三者互相咬死：AGP 9.2.1 / Gradle 9.4.1 / 内置 Kotlin 2.2.10（后者读自 `gradle-9.2.1.pom` 的 `kotlin-gradle-plugin` 依赖）。

| 坑 | 症状 | 怎么做 |
|---|---|---|
| Android 模块 apply 了 `org.jetbrains.kotlin.android` | 插件冲突 | **不要 apply**。AGP 9 内置 Kotlin 且默认开启 |
| 纯 JVM 模块没 apply KGP | Kotlin 源码不编译 | **必须** apply `org.jetbrains.kotlin.jvm`——内置 Kotlin 只对 AGP 模块生效 |
| 开了 `buildFeatures.compose` 但没加 compose 插件 | configuration 阶段报 "the Compose Compiler Gradle plugin is required" | 额外 apply `org.jetbrains.kotlin.plugin.compose`，**版本必须等于 AGP 内置的 Kotlin 版本**（2.2.10）。内置 Kotlin 不带 Compose 编译器插件 |
| 用旧的 `compileSdkVersion(...)` | 警告，AGP 10 会移除 | 用 block 写法 `compileSdk { version = release(37) { minorApiLevel = 0 } }` |
| 用了 `BuildConfig.DEBUG` 却没开 buildConfig | 编译不过 | AGP 9 起 `buildFeatures.buildConfig` 默认 `false`，要显式打开（`:app` 已开） |
| convention plugin 里写 `lint { ... }` block | 编译不过 | AGP 9 的 `CommonExtension` **没有泛型参数**，且 `defaultConfig` / `lint` / `compileOptions` / `testOptions` / `buildFeatures` 只有 `val`、没有接 lambda 的重载。只能属性赋值：`extension.lint.abortOnError = true`。唯一是函数的是 `compileSdk(action)` |
| 在模块里写 `kotlinOptions {}` | 已移除 | 移到顶层 `kotlin { compilerOptions {} }`。内置 Kotlin 注册的仍是 KGP 的 `KotlinAndroidProjectExtension`（实测），所以 convention plugin 能配 `jvmTarget` |
| 想用 `android.builtInKotlin=false` / `android.newDsl=false` 逃生 | 短期能过，AGP 10 失效 | **不用这两个开关** |

### 测试依赖必须是 `kotlin-test-junit`，不能是 `kotlin-test`

**症状**：编译报找不到 `kotlin.test.Test`。

**为什么**：`kotlin-test` 是多平台聚合坐标，靠 Gradle variant 属性选测试框架，而 AGP 内置 Kotlin 不设这个属性，会解析到拿不到 `kotlin.test.Test` 的变体。版本目录里的 alias 名叫 `kotlin-test`，但指向的 module 是 `kotlin-test-junit`——不要「顺手修正」它。

## 4. 加模块、加依赖

### 在模块里写 `repositories { }` → 构建直接失败

`FAIL_ON_PROJECT_REPOS` 已开。仓库**只在 `settings.gradle.kts` 声明**，这是为了让依赖来源不分叉。

### 版本号写在模块里 → review 会打回

**所有版本号只允许出现在 `gradle/libs.versions.toml`**，包括 SDK level——convention plugin 通过 `libs.intVersionOf("compileSdk")` 从版本目录读。

### 新增模块的正确姿势

1. `settings.gradle.kts` 里 `include(":xxx:yyy")`
2. 模块的 `build.gradle.kts` 里套一个 convention plugin：`base.jvm.library`（纯 JVM）/ `base.android.library` / `base.android.application`，要 Compose 再叠 `base.android.compose`
3. **不在模块里重复写 Android 配置**（compileSdk、minSdk、lint、测试依赖都由 convention plugin 给）

选哪个插件的判据是「能不能在 JVM 上测」，不是惯例。业务逻辑尽量放纯 JVM 模块——那是唯一能自动验证的部分（[ADR-0002](adr/0002-core-modules-pure-jvm.md)）。

### `Cannot access '...' which is a supertype of '...'`

**症状**：消费方模块编译不过，报某个类的**超类型**不可见。

**为什么**：公开类的超类型来自 `implementation` 依赖时，它不会传递给消费方。

**怎么做**：这种依赖改成 `api`。仓库里现有四处是刻意的 `api`，别改回 `implementation`：

- `:core:webview` → `:core:bridge`（容器公开签名里有 `BridgeDispatcher` / `BridgeSecurityConfig`）
- `:feature:web` → `:core:webview`（`WebPageViewModel` 实现的是 `HybridWebViewListener`）
- `:core:domain` / `:core:bridge` → `:core:common`（签名里有 `Outcome` / `AppError`）
- `:core:bridge` / `:core:network` → `kotlinx-serialization-json`（`JsonObject` 出现在 handler 签名里）

### 分层规则（越界了 review 一定挡）

- 依赖方向单向向下：`:app` → `:feature` → `:core:data` → `:core:domain` → `:core:common`；容器一侧是 `:app` → `:feature:web` → `:core:webview` → `:core:bridge` → `:core:common`。
- `:core:domain` 零依赖（除 `:core:common`），且**不认识 DI 框架**——use case 在 `:core:data` 的 Koin module 里声明。
- `IOException` / `HttpException` / `SerializationException` 只允许出现在 `:core:network` 和 `:core:data` 内部，跨出 repository 前必须翻译成 `AppError`。
- `:feature` 之间不互相依赖，要共享就下沉到 `:core`。
- 只有 `:app` 知道「接口的实现是谁」。

## 5. 改 JSBridge

改这块之前先读 [ADR-0005](adr/0005-jsbridge-transport.md)（通道选型）和 [ADR-0006](adr/0006-capability-handler-port-split.md)（handler + port 拆分）。已定过的决策不要重新争论。

### 新增能力少加一处 → 页面调不到，但编译是过的

一个能力要同时改**两个地方**：

1. `core/bridge/src/main/kotlin/.../di/BridgeModule.kt` 里 `BridgeDispatcherFactory.handlers()` 注册 handler
2. `app/src/main/kotlin/.../bridge/BridgeSecurity.kt` 的 `DEMO_PAGE_METHODS` 白名单加一行

漏第 2 处 = 谁也调不到（**默认拒绝**，不是默认允许）。漏第 1 处 = 白名单里有个不存在的方法名。两种情况 `BridgeWiringTest` 都会红。

### origin 授权是双闸门，两处必须同源

同一份 `BridgeSecurityConfig` 同时喂：

- `allowedOriginRules` → `addWebMessageListener`（让 WebView 压根不给别的页面注入 bridge 对象）
- `BridgePolicy` → dispatcher（每次分发都校验）

不同源会出现「注入层放进来了、分发层却拒绝」这种极难查的不一致。另外：

- **`allowAnyOrigin` 在任何构建类型下都不许打开**，debug 也不行。打开它等于放弃 bridge 的全部访问控制。
- origin 匹配是**精确相等**（只做去尾斜杠 + 转小写），不支持 `*.example.com` 通配——子域匹配写错是经典漏洞（`https://a.example.com.evil.com` 被当成子域放过）。
- iframe（`isMainFrame == false`）不给 bridge。

### 页面用 `file://` 加载 → bridge 一条都不通

**为什么**：`file://` 页面的 `sourceOrigin` 是字符串 `"null"`，写不进白名单。

**怎么做**：assets 页面必须经 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/assets/...` 加载（常量 `APP_ASSETS_ORIGIN` / `appAssetsUrl()` 在 `core/webview/.../HybridAssets.kt`）。

### 不静默降级

`WEB_MESSAGE_LISTENER` 不被系统 WebView 支持时，页面进 `BRIDGE_UNAVAILABLE` 错误态，**绝不回退 `addJavascriptInterface`**——那个 API 没有 origin 作用域。

### `bridge.js` 与 Kotlin 侧的对齐，编译器管不到

`app/src/main/assets/demo/bridge.js` 有两处必须和 native 一致，改一边就要改另一边：

1. 注入对象名：JS 的 `NATIVE_OBJECT_NAME` == Kotlin 的 `BRIDGE_JS_OBJECT_NAME`（`__hybridNative`，在 `core/webview/.../BridgeInstaller.kt`）
2. 报文格式：和 `core/bridge/.../BridgeMessage.kt` 一致

`.js` / `.html` **不在 Spotless 范围内，也没有 JS lint**——这两个文件没有任何自动化兜底。

### 查 `androidx.webkit` 的 API 形状不要凭记忆

`developer.android.com` 在本环境连不上。直接读 sources jar：`~/.gradle/caches/modules-2/files-2.1/androidx.webkit/`。AGP / Gradle 的 API 同理，读本地 `-sources.jar` 和 `.pom` 比搜索可靠。

## 6. 写测试

当前 98 个测试、19 个测试文件，全部 JUnit 4 + `kotlin.test`。

### 不要引 MockK 一类 mock 框架，用手写 fake

理由和「全 AI 开发」直接相关：接口变了 fake 会**编译不过**，错误显式且指向准确；mock 只在运行期炸，更容易误判（[ADR-0004](adr/0004-hand-written-fakes.md)）。现成的 fake 在 `core/bridge/src/test/.../TestFakes.kt` 和 `TestPorts.kt`。

### Koin 依赖图：只 `modules(...)` 等于没测

**症状**：少注册一个 `single`，DI 测试却是绿的。

**为什么**：`modules(...)` 不构造对象。

**怎么做**：`koinApplication { modules(...) }` 之后**逐个 `get<T>()`**。参考 `core/data/.../DiGraphTest.kt`、`core/bridge/.../BridgeDiGraphTest.kt`。

`:app` 的 `AppModulesTest` 只验证「能加载、无重复定义」，因为 ViewModel 的解析需要 `ViewModelStoreOwner`——那是设备上的事。

### ViewModel 测试：不 setMain 就挂

`viewModelScope` 用 `Dispatchers.Main`，JVM 上没有。必须：

```kotlin
@BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
@AfterTest fun tearDown() { Dispatchers.resetMain() }
```

### MockWebServer 是 builder（okhttp 5）

```kotlin
MockResponse.Builder().code(200).body("...").build()
```

坐标是 `mockwebserver3`，纯 JVM，能真实覆盖序列化和状态码分支，不需要设备。

### 测试方法名用中文反引号

描述行为，不描述实现。ktlint 的 `function-naming` 已为此关掉——不要「顺手」改回 camelCase。

## 7. 格式与静态检查

- Spotless + ktlint 在**根项目**统一配置，覆盖所有模块和 `build-logic`。规则细节在 `.editorconfig`，IDE 和 ktlint 读同一份。
- 有两条 ktlint 规则是**刻意**关掉的：`function-naming`（中文测试名）、`filename`（一个文件放多个顶层声明是有意的，比如 `Outcome.kt` 带扩展函数）。
- Android Lint 覆盖全部 10 个模块——纯 JVM 模块额外 apply 了 `com.android.lint`。**不加的话 `:app:lint` 会把它们当外部依赖跳过**（日志里会明说 "Lint will treat :core:xxx as an external dependency"），等于大部分业务代码没被检查。
- 抑制 lint 优先按路径写进模块的 `lint.xml`（例子：`app/lint.xml` 只为 adaptive icon 关掉 `ObsoleteSdkInt`），**不要在 convention plugin 里全局 disable**。
- `AndroidGradlePluginVersion` / `GradleDependency` / `NewerVersionAvailable` 三条已关掉：它们的结论取决于「今天 Maven 上有什么」，会让同一份代码今天绿明天黄。升级依赖是显式决定——改版本目录再跑 `./scripts/check.sh`。

### 已知噪音：lint warning 不为零

`warningsAsErrors = false`，所以有 warning 是正常的，比如 `BridgeTransport.kt` 上的 `RequiresFeature`（postMessage 要求先检查 `WEB_MESSAGE_LISTENER`——实际检查在 `BridgeInstaller` 里做过了，lint 看不到跨文件的这层）。**判断绿不绿只看 error 数**。

## 8. 边界

- 不提交 API Key、签名密钥。`.gitignore` 已拦 `*.jks` / `*.keystore` / `keystore.properties` / `local.properties`。密钥走 `local.properties` 或环境变量，生产环境不落客户端。
- 不擅自 `git commit`，要提交时由人明确要求。
- 架构决策写进 `docs/adr/`（模板 `docs/adr/TEMPLATE.md`）。**已定过的决策先读 ADR，不要重新争论。**
- 未引入的东西是决定、不是遗漏：持久化（Room，DAO 验证绕不开设备）、离线包、native/web 统一路由。

## 9. 往这份文档里加坑

新踩到一个坑，按同样三段写进最贴近的小节：**症状（贴报错原文）→ 为什么 → 怎么做**。

两条收录标准：

- 能贴出**具体症状**。写不出「怎么发现的」就不是坑，是偏好，那属于 `CLAUDE.md`。
- 说明**为什么不能反过来做**。只写「要这样」的条目，下一个人会觉得是随手定的，然后改掉。

如果坑的根因是一次架构决策，正文里给个链接指向 ADR，不要在这里复述理由。






