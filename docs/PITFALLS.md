# 踩坑手册

**只收「端侧工程事实」**：工具链咬合、构建陷阱、环境自检那一类。平台事实（`android.webkit` 的行为、
API 语义）不进这里，它们属三端共同的判据，落在 pro 的各里程碑「平台事实」块。

每条都实测过。**不要凭记忆改，改之前先复现。**

## 工具链咬合

- **AGP / Studio / compileSdk 三者互相咬。** AGP 9.0 支持的最大 API level 是 36.1，要 `compileSdk 37.0`
  得 AGP 9.2 起；Studio 与 AGP 是单向兼容（新 Studio 开旧 AGP 可以，反过来不行）。当前组合
  AGP 9.2.1 / Gradle 9.4.1 / 内置 Kotlin 2.2.10，改任一个都要重跑一次 `check.sh`
- **AGP 9 内置 Kotlin。** Android 模块 apply `org.jetbrains.kotlin.android` 会冲突；纯 JVM 模块反过来
  必须自己 apply `org.jetbrains.kotlin.jvm`
- **内置 Kotlin 不带 Compose 编译器插件。** `buildFeatures.compose = true` 必须配一个版本相等的
  `org.jetbrains.kotlin.plugin.compose`，否则 configuration 阶段就报缺插件
- **`kotlin-test` 不能用。** 那是多平台聚合坐标，靠 Gradle variant 属性选测试框架，而 AGP 内置 Kotlin
  不设这个属性，会解析到拿不到 `kotlin.test.Test` 的变体。要写 `kotlin-test-junit`
- **convention plugin 里不要用 `extensions.configure(Class) { }`。** kotlin-dsl 带进来的同名重载会让
  类型推不出来，实测报 `Function2<...> was expected Function1<...>`。改用
  `extensions.getByType(X::class.java)` 拿到再赋值
- **AGP 9 的 `CommonExtension` 没有泛型参数**，`defaultConfig` / `lint` / `compileOptions` /
  `testOptions` / `buildFeatures` 只有 getter、没有接 lambda 的重载。唯一是函数的是 `compileSdk(action)`：

  ```kotlin
  android.compileSdk {
      version = release(37) { minorApiLevel = 0 }
  }
  ```

- **`buildFeatures.buildConfig` 在 AGP 9 默认 false。** 要 `BuildConfig.DEBUG` 得显式打开

## 假绿的边界

- **`android.jar` 在单测里是 stub。** 开了 `testOptions.unitTests.isReturnDefaultValues` 之后 stub 方法
  静默返回默认值，于是「断言 `WebSettings.javaScriptEnabled == true`」这类测试**会绿，而绿是假的**。
  本仓刻意保持它关着（AGP 默认 false）：单测里碰框架对象当场抛「not mocked」，那是要的信号。
  代价是 `:core:webview` 一条测试也不能写，所有判定必须下沉到 `:core:container`
- **`.js` / `.html` 不在 Spotless 范围，也没有 JS lint。** 探针页与 Kotlin 侧的对应关系（slug 清单、
  `<!--CRAB-INJECT-->` 标记、`window.__CRAB__` 的形状）编译器管不到，只能靠扫文本的单测兜着

## 模块与依赖

- **公开类的超类型来自 `implementation` 依赖时，消费方报 `Cannot access '...' which is a supertype
  of '...'`。** 所以 `:core:webview` 对 `:core:container` 用 `api`
- **纯 JVM 模块要额外 apply `com.android.lint`。** 不加的话 `:app:lint` 会把它当外部依赖跳过（日志里
  明说 "Lint will treat :core:xxx as an external dependency"），等于判定逻辑那一整块没被检查
- **仓库只在 `settings.gradle.kts` 声明**，`FAIL_ON_PROJECT_REPOS` 已开，模块里再写 `repositories`
  直接构建失败
- **版本号只允许写在 `gradle/libs.versions.toml`**，SDK 档位与 `versionName` 也在里面（convention
  plugin 从版本目录读）
- 新增模块：`settings.gradle.kts` 注册 + 套一个 convention plugin，不在模块里重复写 Android 配置

## 资源与 lint

- **自适应图标必须留在 `mipmap-anydpi-v26/`。** lint 的 `ObsoleteSdkInt` 会建议合并进 `mipmap-anydpi/`，
  但实测那样 AAPT 直接找不到 `mipmap/ic_launcher`，构建失败。这条抑制按路径写在 `app/lint.xml`
- 抑制 lint 一律按路径写进模块的 `lint.xml`，不在 convention plugin 里全局 disable
- `AndroidGradlePluginVersion` / `GradleDependency` / `NewerVersionAvailable` 三条已关：它们的结果取决于
  「今天 Maven 上有什么」，会让同一份代码今天绿明天黄。升级依赖是显式决定

## 环境

- **`java` 不在 PATH。** macOS 的 `/usr/bin/java` 只是存根：`command -v java` 会成功而 `java -version`
  报错，所以探测要看退出码。用 Android Studio 自带 JBR 21
  （`/Applications/Android Studio.app/Contents/jbr/Contents/Home`），由 `scripts/check.sh` 注入
  `JAVA_HOME`。**不要写进 `gradle.properties`**——那是机器特定路径，换台机器就废
- **SDK 只装了 `android-36.1` 与 `android-37.0`**，没有 `android-37`，所以 `compileSdk` 必须带
  `minorApiLevel`
- **无 `cmdline-tools` / 无 system-image / 无 AVD / 无真机。** APK 装不上、`probe.sh` 跑不了。要打开这条
  路：SDK Manager 里装 `cmdline-tools`，再拉一个 **API 37** 的 system-image 建 AVD——minSdk 37 把验收
  门槛一起抬上去了，低档位镜像不算
- **`developer.android.com` 连不上。** 查 API 形状去读本地 jar：
  `~/.gradle/caches/modules-2/files-2.1/` 下的 `-sources.jar` / `.pom`，aar 里的 `classes.jar` 用
  `javap` 打签名。比搜索可靠，也不会编出不存在的方法
- 首次构建要联网下 wrapper 与依赖；`./scripts/env-probe.sh` 一次看完上面全部
