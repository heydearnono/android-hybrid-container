# CLAUDE.md

Crab 容器的 Android 一端，**全部由 AI 开发**。标识 `net.xiaoluzhu.crab`，工程名
`android-hybrid-container`。

上游规划在 `~/Desktop/github/Prospect`（下称 `pro`）：`README.md` + `plan/` 五份 + `开工.md`。
**判据只在那边，本仓不复述、也不改它。** 本仓的第一份产出是 [`TASKS.md`](TASKS.md)——把每个里程碑
「怎么算过」逐条翻译成 Android 任务，并记着按的是 pro 哪个版本。

## 三条纪律，端侧不许自己动

1. **取值一律取 pro 的取值表。** 承载 origin 一类在端内只定义一处（`HostingOrigin.kt`），别处从它
   派生，不另写字面量
2. **slug、输出形状与探针页那五条规约三端一字不差。** 要改，三端一起改
3. **做不到时只走第 1 个出口（改本端实现）。** 放宽要求（出口 2）、允许本端偏离（出口 3）改的是三端
   共同的判据，**回 pro 议，不在这里自决**——遇到就停下来报给用户

## 验证闭环（最重要的一节）

**本机没有真机、没有 AVD、没有 system-image、没有 cmdline-tools**，所以 AI 能自己跑完的验证只有三样：
编译、JVM 单测、静态检查。

```bash
./scripts/check.sh          # spotlessCheck → assembleDebug → test → lint
./scripts/check.sh --fix    # 先 spotlessApply
```

绿了才算完。绿的定义：格式无 diff、APK 产出、单测全过、lint 零 error。

**做不到的事**：装 APK、运行 App、看 UI、跑 instrumented 测试、跑 `scripts/probe.sh`。这些一律标
「待模拟器核实」写进 `TASKS.md`，**不要假装验证过**。要打开这条路，需要用户在 SDK Manager 里装
`cmdline-tools`，再拉一个 **API 37 的 system-image** 建 AVD（minSdk 37 抬高了验收门槛）。

**WebView 是这条边界上最危险的地方。** `android.webkit` / `androidx.webkit` 在单测里是 `android.jar`
的 stub。`testOptions.unitTests.isReturnDefaultValues` **刻意没开**：开了以后 stub 会静默返回默认值，
给容器写的测试会绿、而那个绿是假的，比没有测试更坏（pro 的 M3 明写这一条）。所以：

- `:core:webview` 不带单测，里面只允许直线代码——**不许有分支、不许有状态机**
- 任何「判定」都要先下沉到 `:core:container` 再被 webview 调用，因为那是唯一能真测的地方

## 模块结构

按「能否在 JVM 上测」切分：

| 模块 | 插件 | 内容 |
| --- | --- | --- |
| `:app` | `crab.android.application` + `crab.android.compose` | `CrabApplication`（调试开关）、`MainActivity`（Compose 壳 + `AndroidView` 挂容器 + 错误态 UI）、返回键 / 生命周期 / 销毁、探针页素材（`src/main/assets/`） |
| `:core:container` | `crab.jvm.library` | 纯 JVM 判定逻辑：`HostingOrigin`、`AssetRouting`、`WebSettingsSpec`、`DocumentStartScript`、`UserAgent`、`NavigationGate`、`CrabLog`、`RenderProcessRecovery`、`ContainerState`、`RemoteDebugging` |
| `:core:webview` | `crab.android.library` | `WebSettings` 写入、`WebViewAssetLoader` + `PathHandler`、`WebViewClient` / `WebChromeClient` 回调转发、`addDocumentStartJavaScript` 安装 |

- 依赖方向：`:app` → `:core:webview` → `:core:container`。`:core:container` 零依赖
- `:core:webview` 对 `:core:container` 用 **`api`**：公开类的超类型来自 `implementation` 依赖时，
  `:app` 会报 `Cannot access '...' which is a supertype of '...'`
- 新增模块：`settings.gradle.kts` 注册 + 套一个 convention plugin，**不在模块里重复写 Android 配置**

## 容器的规矩

- **承载 origin 只定义一处。** `HostingOrigin.kt` 里的那个字符串喂给三处：`WebViewAssetLoader` 的
  域名、`addDocumentStartJavaScript` 的 `allowedOriginRules`、`shouldOverrideUrlLoading` 的放行判定。
  有一条单测扫源码树，断言它除定义处外零命中——这是 M2 要求的「一条可执行检查」，走查不算
- **拦截点未命中必须自己回 404 + `INTERCEPTED`。** `shouldInterceptRequest` 返回 null 的语义是交回
  WebView 默认处理，请求会**真发到网上**
- **注入与兜底不许叠加。** `DOCUMENT_START_SCRIPT` 支持则走 `addDocumentStartJavaScript`，不支持则**只**
  走拦截点改写入口 HTML。两条都跑 `injected` 会变 2，`inject-order` 当场红
- **默认拒绝。** 跨 origin、`_blank`、`window.open`、未知 scheme、定位 / 相机 / 麦克风一律拒绝，
  **但回调照接**——靠不接回调达成的「不打开」没有日志，与「链接写错了」分不开
- **日志四个前缀一字不差**：`CRAB-NAV <slug> <URL>` / `CRAB-DLG <类型>` / `CRAB-PERM <权限>` /
  `CRAB-ERR <场景> <错误码>`。它们是 `probe.sh` 的判据，不是调试打印
- **SSL 一律 `cancel()`，一次也不许 `proceed()`。** 承载域落在 `.invalid` 下，收到 SSL 错误说明有请求
  漏到了网上
- **`onRenderProcessGone` 必须返回 true**，返回 false 系统会杀掉整个应用进程
- **`.js` / `.html` 没有任何自动化兜底**（不在 Spotless 范围、没有 JS lint）。探针页与 Kotlin 侧的对应
  关系（slug 清单、`<!--CRAB-INJECT-->` 标记、`window.__CRAB__` 的形状）只能靠单测扫文本兜着，改一边
  必须改另一边

## 构建配置：AGP 9 的坑（全部实测过，不要凭记忆改）

版本三者互相咬死：**AGP 9.2.1 / Gradle 9.4.1 / 内置 Kotlin 2.2.10**（后者读自 `gradle-9.2.1.pom` 的
`kotlin-gradle-plugin` 依赖）。

1. **Android 模块不要 apply `org.jetbrains.kotlin.android`。** AGP 9 内置 Kotlin 且默认开启，再 apply
   KGP 会冲突
2. **纯 JVM 模块反过来必须 apply `org.jetbrains.kotlin.jvm`**——内置 Kotlin 只对 AGP 模块生效
3. **内置 Kotlin 不带 Compose 编译器插件。** `buildFeatures.compose = true` 就必须额外 apply
   `org.jetbrains.kotlin.plugin.compose`，且**版本必须等于 AGP 内置的 Kotlin 版本**
4. **`compileSdk` 用 block 写法**，旧的 `compileSdkVersion(...)` 已标记 AGP 10 移除：
   `compileSdk { version = release(37) { minorApiLevel = 0 } }`
5. **`buildFeatures.buildConfig` 在 AGP 9 默认 `false`**，要 `BuildConfig.DEBUG` 得显式打开（`:app` 已开）
6. **AGP 9 的 `CommonExtension` 没有泛型参数**，且 `defaultConfig` / `lint` / `compileOptions` /
   `testOptions` / `buildFeatures` 只有 `val`、没有接 lambda 的重载。convention plugin 里只能属性赋值，
   唯一是函数的是 `compileSdk(action)`
7. **convention plugin 里不要用 `extensions.configure(Class) { }`**：kotlin-dsl 那几个同名重载会让类型
   推不出来（实测报 `Function2 ... was expected Function1`）。用 `extensions.getByType(X::class.java)`
   拿到再赋值
8. `kotlinOptions {}` 已移到顶层 `kotlin { compilerOptions {} }`。内置 Kotlin 注册的仍是 KGP 的
   `KotlinAndroidProjectExtension`（实测）
9. **不使用 `android.builtInKotlin=false` / `android.newDsl=false`** 这两个逃生开关，AGP 10 会失效

依赖坐标上踩过一个坑：**测试依赖用 `kotlin-test-junit`，不能用 `kotlin-test`**。后者是多平台聚合坐标，
靠 Gradle variant 属性选测试框架，而 AGP 内置 Kotlin 不设这个属性，会解析到拿不到 `kotlin.test.Test`
的变体。

## 版本与依赖

- **所有版本号只允许写在 `gradle/libs.versions.toml`**，包括 SDK 档位与 `versionName`
- **仓库只在 `settings.gradle.kts` 声明**，`FAIL_ON_PROJECT_REPOS` 已开
- 根 `build.gradle.kts` 里的插件全部 `apply false`，只为把 AGP/KGP 放进构建 classpath——`build-logic`
  是 `compileOnly` 编译的，运行期需要它们在
- `build-logic` 通过 `pluginManagement { includeBuild(...) }` 组合进来，写成 `Plugin<Project>` 类
- **升级依赖是显式决定**：改版本目录再跑 `check.sh`。lint 的 `AndroidGradlePluginVersion` /
  `GradleDependency` / `NewerVersionAvailable` 已关掉——它们的结果取决于「今天 Maven 上有什么」

## 测试约定

- JUnit 4 + `kotlin.test`（convention plugin 自动带上，模块里不重复声明）
- **用手写 fake，不引 MockK 一类 mock 框架。** 接口变了 fake 会编译不过、错误指向准确；mock 只在运行期炸
- 测试方法名用中文反引号，描述行为不描述实现（ktlint 的 `function-naming` 已为此关掉）
- **不许为了让测试跑起来去开 `isReturnDefaultValues`**，见上面「验证闭环」

## 格式与静态检查

- Spotless + ktlint 在**根项目**统一配置，覆盖所有模块和 `build-logic`。规则细节在 `.editorconfig`
- 两条 ktlint 规则刻意关掉：`function-naming`（中文测试名）、`filename`（一个文件多个顶层声明是有意的）
- Android Lint 覆盖全部模块——纯 JVM 模块额外 apply 了 `com.android.lint`，不加的话 `:app:lint` 会把它
  当外部依赖跳过，等于判定逻辑那一整块没被检查
- 抑制 lint 按路径写进模块的 `lint.xml`（例子：`app/lint.xml` 只为自适应图标关掉 `ObsoleteSdkInt`）

## 环境事实

- `java` 不在 PATH（macOS `/usr/bin/java` 只是存根，用 `command -v` 探测会误报成功，要看退出码）。用
  Android Studio 自带 JBR 21：`/Applications/Android Studio.app/Contents/jbr/Contents/Home`。
  `scripts/check.sh` 会注入这个 `JAVA_HOME`；**不要写进 `gradle.properties`**，那是机器特定路径
- SDK 在 `~/Library/Android/sdk`。**只装了 `android-36.1` 和 `android-37.0`**，所以 `compileSdk` 必须带
  `minorApiLevel`
- 无 `cmdline-tools`、无 NDK、无 system-image、无 AVD、无真机。**需要装任何 SDK 组件时先提示用户，
  不要擅自安装**
- 环境体检：`./scripts/env-probe.sh`
- `developer.android.com` 在本环境连不上。查 API 形状时读 `~/.gradle/caches/modules-2/` 里的
  `-sources.jar` / `.pom`，或对 aar 里的 `classes.jar` 跑 `javap`，比搜索可靠

## 写作与语言

- 回复、注释、文档用中文。代码标识符、API 名、命令保持英文原文
- 注释写「为什么」，不写「做了什么」。绕过某个坑的地方要写清坑是什么
- 结论先行。不写与正文重复的总结段
- 不编造版本号和 API 名。拿不准的去读本地 jar 或查官方文档

## 边界

- 不提交 API Key、签名密钥。`.gitignore` 已拦 `*.jks` / `*.keystore` / `keystore.properties` /
  `local.properties`
- 不擅自 `git commit`、不擅自推远端。要提交时由用户明确要求
- 架构决策写进 `docs/adr/`，模板见 `docs/adr/TEMPLATE.md`。**已定过的决策不要重新争论，先读 ADR**
