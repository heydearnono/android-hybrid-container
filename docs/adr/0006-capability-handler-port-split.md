# ADR-0006：能力拆成「纯 JVM handler + 极薄 port」

| | |
|---|---|
| 编号 | ADR-0006 |
| 日期 | 2026-09-03 |
| 状态 | 已采纳 |
| 影响范围 | 架构分层 / 测试 |

## 决策

每个 bridge 能力拆成两半：

- **handler** 在 `:core:bridge`（纯 JVM）。负责参数校验、结果编码、错误映射——也就是全部分支。
- **port** 是一个窄接口，实现放 `:core:webview` / `:feature:web` / `:app`。只允许是几行直线代码，**不许有分支、不许有状态机**。

对 port 实现的这条「不许有分支」不是风格偏好，是可验证性的直接推论。同理，容器（`HybridWebView` / `HybridWebViewClient`）只把 WebView 回调原样转给 `HybridWebViewListener`，**所有状态迁移规则都放在 `WebPageViewModel` 里**，因为 ViewModel 能在 JVM 上测。

## 背景

ADR-0001 定下的验证闭环是编译 + JVM 单测 + 静态检查，没有设备、没有 AVD。容器直接撞上这条：`android.webkit` / `androidx.webkit` 在单测里是 stub，而 `unitTests.isReturnDefaultValues = true` 让它们静静返回默认值。后果是「为 WebView 写的测试会绿，但那个绿是假的」——比没有测试更坏，因为它给出虚假的信心。

所以本轮的设计主线不是「怎么写容器」，而是**怎么把可测的部分和不可测的部分切开，让不可测的面积尽可能小、尽可能薄、尽可能没有分支**。

## 候选方案

| 方案 | 优势 | 代价 | 关键风险 |
|---|---|---|---|
| A（选中）handler / port 对切 | 校验与错误映射全部可测；不可测的只剩几行 Android 调用；port 窄到手写 fake 十几行写完 | 一个能力两个文件、一次 DI 接线；`page.*` 的 port 是每页一个，逼出了 `BridgeDispatcherFactory` | port 的真实现和 fake 行为漂移（但实现只有几行，漂移空间小） |
| B 能力直接调 Android API | 少一层接口，代码短 | 整个能力都不可测，包括参数校验这种最容易写错的部分 | 「缺少 key 时该回什么」这类分支只能靠真机点一遍，AI 无法自验 |
| C 引 Robolectric 测容器 | 能在 JVM 上跑一部分 Android 代码 | 新增重依赖；Robolectric 的 WebView 影子实现不加载真实 WebView | 测的是影子的行为，不是 WebView 的行为——同样是假的绿，只是更贵 |

结果是可量化的：本轮新增 81 个测试（全仓从 17 涨到 98），覆盖协议编解码、origin 匹配、分发路由与错误映射、8 个 handler 的参数校验、文件存储读写、ViewModel 状态迁移、DI 图与白名单接线。不可测的代码是 `core/webview` 里的容器和 4 个 port 实现，加起来不到两百行，且没有一个 `if` 是业务判断。

## 理由

C 是最容易被选中的方案，也是最危险的：它把「不可验证」换成了「验证了别的东西」。Robolectric 的 WebView 是影子对象，`addWebMessageListener` 在它上面的行为跟真机没有关系，测通了不代表 bridge 通了。既然结论仍然要靠人在 Android Studio 里点一遍，就不该为它付一份重依赖，更不该让它产出绿色对号。

A 与 B 的分界线画在「有分支的代码」上。参数缺失、类型不对、key 为空、路由名不在白名单——这些都是最常写错、且错了以后表现最含糊的地方（JS 侧只看到一个 Promise reject）。把它们全留在 JVM 侧，就等于把「最容易错的部分」和「唯一能自动验证的部分」重合起来。

代价里最实的一条是 `PageHost`：它必须是每个页面一个实例（`page.close` 得关掉发起调用的那个页面），于是 dispatcher 不能是单例，只能是工厂。这比单例麻烦，但反过来说，单例方案需要一个「当前页面」的全局可变引用，那在多个 web 页共存时必然出错。

## 后果

- 接受了什么代价：一个能力两处改动；`AppError` 为此新增了一个分支 `Rejected(code, detail)`。

  `Rejected` 里的 `code` 是字符串，而 bridge 内部本来是强类型的 `enum class BridgeErrorCode`。让字符串跨出边界是刻意的：接收方是 JS，它只认字符串。`BridgeErrorCode → wireCode` 的穷举 `when` 放在 `:core:bridge`，`AppError` 将来再长分支时编译会在那里红。全仓对 `AppError` 做穷举 `when` 的地方只有 `ArticlesScreen.displayMessage()` 一处，同步成本可控。

- 引入了什么依赖 / 锁定：port 接口成了 `:core:bridge` 的公开契约，改签名会同时波及 `:core:webview`、`:feature:web`、`:app`（好在编译期就会全部报出来）。`BridgeDispatcherFactory` 的存在被 `PageHost` 的生命周期锁定。
- 什么条件下重新评估：装上 `cmdline-tools` 并跑起 AVD 之后。届时 instrumented 测试能真正验证容器，「压缩不可测面积」的收益会下降，但 handler 侧的测试仍然更快更稳，不建议合并回去。

## 未被本决策解决的部分

拆分只是把不可验证的面积压小，没有消掉它。以下事项在当前环境**一行都验证不了**，必须人工在 Android Studio 里确认：WebView 是否加载成功、bridge 对象是否真的注入、`WebViewAssetLoader` 是否拦到了请求、JS 能否执行、能否真的对话一次、Toast 是否弹出。

还有一个已知的单向缺口：`bridge.js` 与 Kotlin 侧的报文格式、注入对象名靠人保证一致，`.js` / `.html` 既不在 Spotless 覆盖范围内，也没有 JS lint。首批不补。

## 参考

- `core/bridge/.../port/Ports.kt`（对实现的约束写在文件头注释里）
- `core/bridge/.../handler/`、`core/webview/.../port/AndroidPorts.kt`
- `feature/web/.../WebPageViewModel.kt` 与 `feature/web/src/test/.../WebPageViewModelTest.kt`
- ADR-0001（验证闭环）、ADR-0004（手写 fake）
