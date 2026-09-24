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
- **AGP 升级助手改的六个文件不是工作产物。** 它会动 `gradle.properties`、`gradle/libs.versions.toml`、
  `gradle/wrapper/gradle-wrapper.jar`、`gradle-wrapper.properties`、`gradlew`、`gradlew.bat`，
  `git stash -u` 停掉即可。**绿的组合是 AGP 9.2.1 + Gradle 9.4.1**，不要恢复那个 stash
- **停掉之后 Studio 会报「built with AGP 9.2.1 but it is synced with 9.3.3」。** 那是它的同步模型旧了，
  不是工程坏了：File → Sync Project with Gradle Files，升级横幅点掉
- **`gradle/gradle-daemon-jvm.properties` 不要提交。** `./gradlew updateDaemonJvm` 生成的，内容是
  `toolchainVersion=21` 加十条 foojay 自动下载 URL。提交之后 daemon JVM 的判据就从「路径」变成「版本」，
  而 JBR 版本随机器变（见下面「环境」）：工作机是 25，找不到 21 就会照那些 URL 下一个 JDK 回来跑，
  用的已经不是产出绿构建的那个 JVM 了，还给 `./gradlew` 加了一条网络依赖。删掉即可，随时能再生。
  **也不要加进 `.gitignore`**——留着它在 `git status` 里现形，比被忽略后悄悄影响本地构建好
- **ktlint 不许 KDoc 挂在 `init` 块上。** `standard:kdoc` 报
  `A KDoc is not allowed inside 'class_initializer'`，而 `spotlessCheck` 是 `check.sh` 的第一步，直接红。
  `init` 块上要写为什么就用 `//`；改成一个只为副作用而存在的 `private val ... : Unit` 属性是更坏的写法

## 假绿的边界

- **`android.jar` 在单测里是 stub。** 开了 `testOptions.unitTests.isReturnDefaultValues` 之后 stub 方法
  静默返回默认值，于是「断言 `WebSettings.javaScriptEnabled == true`」这类测试**会绿，而绿是假的**。
  本仓刻意保持它关着（AGP 默认 false）：单测里碰框架对象当场抛「not mocked」，那是要的信号。
  代价是 `:core:webview` 一条测试也不能写，所有判定必须下沉到 `:core:container`
- **`.js` / `.html` 不在 Spotless 范围，也没有 JS lint。** 探针页与 Kotlin 侧的对应关系（slug 清单、
  `<!--CRAB-INJECT-->` 标记、`window.__CRAB__` 的形状）编译器管不到，只能靠扫文本的单测兜着
- **只改了 `.sh` / `.js` / `.html`，`check.sh` 的绿可能是没跑出来的绿。** `:app` 那三条扫文本的测试
  自己去读仓库里的文件，这些文件没登记成 Gradle 的任务输入，所以 Gradle 认为测试「没变」、直接
  `UP-TO-DATE` 跳过。实测（2026-09-24）：改了 `scripts/probe.sh` 再跑 `check.sh`，日志里是
  `:app:testDebugUnitTest UP-TO-DATE`。只动了这类文件时，另跑一次
  `./gradlew :app:testDebugUnitTest --rerun`，看到任务真的执行了才算数

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

## 模拟器与 `probe.sh`

- **不要用 Studio 的 Run 按钮装应用。** 它和 `probe.sh` 里的 `:app:installDebug` 会撞
- **`probe.sh` 开头 `logcat -c` 清日志，所以每次重跑，八个按钮、三个对话框、那一次系统返回键都得从头
  做一遍。** 不做就回车，红的正好是所有靠交互的 slug——第一次跑就是这样：8 PASS + 7 FAIL。
  **那不是实现坏了**
- **`dialog` 单独红那一次是三个对话框没答完**（alert 关掉、confirm 点确定、prompt 里输入 `CRAB`，
  缺一不可）。先 `adb logcat -d | grep -E 'CRAB-DLG|CRAB-PROBE dialog'` 看清再说：日志缓冲此时还没被清掉，
  不必重跑一整遍
- **查 WebView 内核别用 `dumpsys webview`，没有这个服务**，服务名是 `webviewupdate`
  （`adb shell cmd webviewupdate query`）。内核大版本也可以直接从探针页打出来的 UA 里读——`Chrome/145`
  就是这么拿到的（UA 里的系统版本段是常量，读不到真实档位，见 pro 的 M3 平台事实）

## 环境

**本仓有两处检出，能力不一样，别把一处的事实当成两处的：**

| 检出 | 模拟器 | 推远端 |
| --- | --- | --- |
| 工作机 | **有** API 37 的 AVD（`emulator-5554`），十六行就是在这里跑的 | ✗ remote 是 HTTPS，GitHub 在那台上被 reset（`Recv failure: Connection reset by peer`，退出码 128） |
| `~/Desktop/github` 下这处 | 无 `cmdline-tools` / 无 system-image / 无 AVD / 无真机 | ✓ remote 是 SSH，正常 |

所以运行记录产在工作机、提交与推送落在这处；**这处的 AI 会话只跑得动编译、单测、静态检查那三样**。

- **两处之间搬提交，基点写两边共有的那个提交，不写 `origin/main`。** 工作机的 `origin/main` 拉不动，
  停在上一次能 fetch 的地方；`git format-patch origin/main` 会把已经在远端的提交再导一遍，回到这处
  `git am` 就冲突。反方向（这处 → 工作机）用 `git bundle create <文件> <工作机的 HEAD>..main`，工作机上
  `git pull <文件> main`。开跑之前先比一次两边的 HEAD：工作机落后时，后来才加的日志根本打不出来

- **`java` 不在 PATH。** macOS 的 `/usr/bin/java` 只是存根：`command -v java` 会成功而 `java -version`
  报错，所以探测要看退出码（注意别接管道——`java -version | head` 拿到的是 `head` 的退出码）。终端里
  直接 `./gradlew` 报 `Unable to locate a Java Runtime`，而 Studio 的 Run 按钮照常能用，因为它走自带
  JBR。要 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`，
  `scripts/check.sh` 已经替你注入。**不要写进 `gradle.properties`**——那是机器特定路径，换台机器就废
- **JBR 的版本号随机器变，别拿它当事实。** 这处实测 OpenJDK 21.0.10，工作机实测 OpenJDK 25.0.3。
  钉的是那个路径，不是版本
- **`adb` / `emulator` 也不在 PATH。** SDK 根取 `local.properties` 里的 `sdk.dir`，`export ANDROID_HOME`，
  再把 `$ANDROID_HOME/platform-tools` 与 `$ANDROID_HOME/emulator` 加进 PATH
- **上面两条 `export` 只在开它的那个终端窗口里有效，换窗口重做一次。** 工作机上这两条各踩过一次
- **SDK 只装了 `android-36.1` 与 `android-37.0`**，没有 `android-37`，所以 `compileSdk` 必须带
  `minorApiLevel`
- **这处没有 `cmdline-tools` / system-image / AVD / 真机。** APK 装不上、`probe.sh` 跑不了。要打开这条
  路：SDK Manager 里装 `cmdline-tools`，再拉一个 **API 37** 的 system-image 建 AVD——minSdk 37 把验收
  门槛一起抬上去了，低档位镜像不算
- **`developer.android.com` 连不上。** 查 API 形状去读本地 jar：
  `~/.gradle/caches/modules-2/files-2.1/` 下的 `-sources.jar` / `.pom`，aar 里的 `classes.jar` 用
  `javap` 打签名。比搜索可靠，也不会编出不存在的方法。**要看 javadoc 读 `sdk/sources/android-36.1/`**：
  那里是带注释的框架源码（`platforms/` 下的 stub 源码 jar 不带 javadoc），`setAllowFileAccess` 管不到
  `android_asset` 就是从这里读到的
- 首次构建要联网下 wrapper 与依赖；`./scripts/env-probe.sh` 一次看完上面全部
