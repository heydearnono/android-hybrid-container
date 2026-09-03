# android-hybrid-container

生产级 Android 应用基座。多模块 Gradle 工程，分层 + DI + 网络 + 导航 + 主题 + 测试 + 静态检查开箱可用，带一个端到端联网列表页样例证明链路通。

协作约定见 [CLAUDE.md](CLAUDE.md)，架构决策见 [docs/adr/](docs/adr/)。

## 快速开始

```bash
./scripts/env-probe.sh      # 体检：JDK / SDK / 设备
./scripts/check.sh          # 唯一的验证入口：格式 → 编译 → 单测 → lint
./scripts/check.sh --fix    # 同上，但先自动修格式
```

`java` 不在 PATH，`check.sh` 会自动用 Android Studio 自带的 JBR。产物在 `app/build/outputs/apk/debug/`。

## 模块

```
:app                  薄壳：Application(startKoin)、MainActivity、NavHost、DI 装配
:core:common          纯 JVM   Outcome / AppError / DispatcherProvider
:core:domain          纯 JVM   实体、use case、repository 接口
:core:network         纯 JVM   Retrofit + OkHttp + kotlinx.serialization
:core:data            纯 JVM   repository 实现、DTO → domain 映射
:core:designsystem    Android  BaseTheme、通用组件
:feature:articles     Android  样例：联网列表页（loading / empty / error / content）
build-logic/                   convention plugins，模块构建配置只在这里改
```

依赖方向单向向下，`:core:domain` 不认识 DI 框架，异常在 `:core:data` 的边界翻译成 `AppError`。四个 `core` 模块刻意做成纯 JVM——业务逻辑因此能在没有设备的环境里跑测试，理由见 [ADR-0002](docs/adr/0002-core-modules-pure-jvm.md)。

## 技术栈

Kotlin 2.2.10（AGP 9.2.1 内置）· Gradle 9.4.1 · Compose + Material 3 · Retrofit 3 + OkHttp 5 + kotlinx.serialization · Koin 4 · Navigation Compose · JUnit 4 + kotlin.test + Turbine + MockWebServer · Spotless(ktlint) + Android Lint

版本号全部集中在 `gradle/libs.versions.toml`，不在别处硬编码。

## 架构决策记录

| | 决策 |
|---|---|
| [ADR-0001](docs/adr/0001-verification-loop.md) | 验证闭环限定为编译 + JVM 单测 + 静态检查 |
| [ADR-0002](docs/adr/0002-core-modules-pure-jvm.md) | core 模块做成纯 JVM，而不是 Android library |
| [ADR-0003](docs/adr/0003-di-koin.md) | DI 用 Koin，不用 Hilt |
| [ADR-0004](docs/adr/0004-hand-written-fakes.md) | 用手写 fake，不引 mock 框架 |

## 当前限制

自动化验证只覆盖编译、JVM 单测、静态检查。**APK 能否安装运行、UI 长什么样、instrumented 测试，全部需要人工在 Android Studio 里确认**——本环境没有真机、没有 AVD，也没有 `cmdline-tools` 去下载 system-image。要打开这条路，在 SDK Manager 里装 `cmdline-tools`。

未引入持久化（Room）：DAO 的验证绕不开设备，接口先在 `:core:domain` 留位置。
