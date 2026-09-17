# TASKS · Android 侧任务清单

把 pro 五个里程碑「怎么算过」那几张表逐条翻译成本端任务。**判据不在这里**，在 pro；这里只写落在哪个
API、动哪个文件、哪条单测、现在是什么状态。

## 按的是 pro 的哪个版本

- commit `e13f506dd2af01e71d98d432730a07197fa78f07`（「加一份开工文档:三端并行的顺序与动作」）
- **外加 pro 工作区里未提交的改动**：`README.md` 与 `plan/` 五份当时都是 modified 状态，本清单按的是
  那份工作区内容。pro 一提交就以提交为准，往下一个里程碑走之前重读那六份文件

## 状态记号

| 记号 | 意思 |
| --- | --- |
| 已落地 | 代码在，`./scripts/check.sh` 绿（判定逻辑有单测） |
| 待模拟器核实 | 代码在，但判据只能在模拟器上看。本机无 `cmdline-tools`、无 API 37 镜像、无 AVD，**一条也没核过** |
| 只剩走查 | pro 明写在容器阶段没有观察面，M5 里要照实写「未验证」 |

**不要把「已落地」读成「过了」。** 十六条断言一条都还没在真容器里跑过。

## M1 · 装到模拟器

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 构建通过 | `crab.android.application` + 三模块，`./scripts/check.sh` | 已落地 |
| 标识 `net.xiaoluzhu.crab` | `app/build.gradle.kts` 的 `namespace` 与 `applicationId` | 已落地 |
| 应用名 Crab / 螃蟹 | `values/strings.xml` + `values-zh/strings.xml` | 已落地 |
| `minSdk = 37` | `gradle/libs.versions.toml` | 已落地 |
| 装得上、屏幕上有一个原生页面 | `MainActivity`（M1 不碰 WebView） | 待模拟器核实 |

## M2 · 本地承载

十六条断言里 M2 占前六条（`origin` / `storage` / `subresource` / `intercept` / `escape` /
`escape-encoded`）。它们全部由探针页自己判，结果经 `console.log` → `onConsoleMessage` → logcat →
`scripts/probe.sh` 回读。**六条一条都还没在真容器里跑过。**

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 页面从 assets 起来，`location.origin` 是约定的承载 origin | `HostingOrigin` + `CrabContainer` 的 `WebViewAssetLoader`（`setDomain` / `setHttpAllowed(false)`） | 待模拟器核实 |
| 同目录子资源（`.js` / `.png`）加载成功 | `assets/probe/probe.js` · `probe.png`，走同一个 `PathHandler` | 待模拟器核实 |
| `localStorage` 可读可写 | `settings.domStorageEnabled = true`（M2 先借这一项） | 待模拟器核实 |
| 承载目录里不存在的路径 → 容器自己回 404 + `INTERCEPTED` | `CrabAssetPathHandler`（未命中不返回 null） | 待模拟器核实 |
| 明文穿越 `../` 读不到承载目录外的文件 | `AssetRouting.resolve` 判 `OutOfBounds`；`AssetRoutingTest` | 已落地 |
| 编码穿越 `%2e%2e%2f`（含双重编码）同样读不到 | 同上，先解码再判；`AssetRoutingTest` | 已落地 |
| 承载 origin 单点定义要有**一条可执行检查** | `HostingOriginSingleDefinitionTest` 扫源码树（定义处 + 探针页期望值，第三处即红） | 已落地 |
| 探针页 / `probe.sh` / slug 清单三者对齐 | `ProbeContractAlignmentTest`（`.html` / `.js` / `.sh` 没有别的兜底） | 已落地 |
| 越界素材真实存在（否则 `escape` 测不到东西） | `assets/outside/out-of-bounds.txt`，内容 `OUT_OF_BOUNDS` | 已落地 |

`scripts/probe.sh` 已就位并输出全部十六行，但 M2 只实现了前六条对应的页面侧判定，其余九条在 M3/M4
补上之前一律打 FAIL（`nav-system-scheme` 恒为 MANUAL）。**这不是「跑失败了」，是「还没实现」。**

## M3 · 配置、注入与 UA

M3 添的是第七、八条断言（`inject-order` / `inject-scope`）。**六项配置的「写没写进去」这里一条单测都
给不出**：JVM 单测里 `WebSettings` 是 `android.jar` 的 stub，断言它的取值只会得到与代码无关的绿。所以
取值（`WebSettingsSpec`，有单测）与写入（`CrabWebSettings.applyCrabSpec`，只能观察）刻意分成两处。

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 六项配置按取值表落地 | `WebSettingsSpec`（取值，`WebSettingsSpecTest`）→ `applyCrabSpec`（写入） | 取值已落地 / 写入待模拟器核实 |
| 混合内容 `NEVER_ALLOW` | `MixedContentPolicy` → `WebSettings.MIXED_CONTENT_NEVER_ALLOW`（常量值 0/2/1 由 javap 核过） | 取值已落地 / 写入待模拟器核实 |
| 文件与内容访问四项全关 | 同上四个字段 | 取值已落地 / 写入待模拟器核实 |
| 缩放三项关 + `textZoom = 100` | 同上；文字大小跟不跟随系统字号要在模拟器上看 | 取值已落地 / 写入待模拟器核实 |
| 多窗口 / 脚本开窗 / 定位**显式开** | 三项 true，为的是 M4 的回调能被调用后当场拒绝并留日志 | 取值已落地 |
| UA 追加 `Crab/0.1.0`，不替换整串 | `UserAgent.decorate`（`UserAgentTest`）；版本号由 `:app` 的 `BuildConfig.VERSION_NAME` 传入 | 已落地 / 生效值待模拟器核实 |
| 页面开口之前注入，`inject-order` 成立 | `DocumentStartScript.source` + `WebViewCompat.addDocumentStartJavaScript`；入口页首行 `<script>` 记快照 | 待模拟器核实 |
| 不支持时走兜底，且**两条路不叠加** | `CrabContainer.documentStartScriptSupported` 一个布尔决定；兜底是 `CrabAssetPathHandler(inlineDocumentStartScript = true)` 改写 HTML | 待模拟器核实 |
| 注入范围不漏到别的 origin（`inject-scope`） | `HostingOrigin.allowedOriginRules`；页面用 `data:` iframe 经 `postMessage` 自报 | 待模拟器核实 |
| 脚本重复注入看得出来 | 脚本用 `window.__CRAB__ ||` 初始化、`injected` 只增不减；`DocumentStartScriptTest` 盯着源码里这两条 | 已落地 |
| 兜底只改 HTML、不碰素材 | `DocumentStartScript.appliesTo`（`DocumentStartScriptTest`） | 已落地 |
| 加载后生效差异表（七行） | 探针页 `CRAB-ENV` 一行打 UA 与 `typeof localStorage` | 待模拟器核实 |

**M3 留给模拟器的三个未知**，结果会反过来影响判据：

1. `DOCUMENT_START_SCRIPT` 在目标镜像的内核上支不支持——决定走注入还是兜底，两条路的表现应当一致
2. `data:` URL 的 iframe 加不加载得起来。加载不起来时 `inject-scope` 会因超时打 FAIL，细节里写着
   「可能需要换 sandbox+srcdoc 退路」。**换退路要三端一起换，得回 pro 议，端内不自决**
3. 文字大小跟不跟随系统字号（`textZoom = 100` 是否真的挡住了系统字号）

## M4 · 导航与降级

M4 补齐剩下八条断言（`nav-same-origin` / `nav-back` / `nav-cross-origin` / `nav-blank` /
`nav-system-scheme` / `nav-unknown-scheme` / `dialog` / `permission`）。**其中四条页面判不了**——被拦下的
那一跳页面什么都收不到，证据只在容器打的 `CRAB-NAV` 里，由 `scripts/probe.sh` 从 logcat 回读。

判定全在 `:core:container`（`NavigationGate` / `CrabLog` / `ContainerStateMachine` /
`RenderProcessRecovery` / `ContainerCoordinator`，都有单测）；`:core:webview` 只把回调原样转进来、把返回
值原样转回去。

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 同 origin 跳转放行 | `NavigationGate.decide` → `Allow`；`NavigationGateTest` | 已落地 |
| 返回键回上一页，到底交回宿主 | `MainActivity.backCallback`（`canGoBack()` → `goBack()`，否则 `remove()` 后重新分发） | 待模拟器核实 |
| 跨 origin 拦下 + 一行 `CRAB-NAV nav-cross-origin`，**不交系统浏览器** | `NavigationGate` → `Deny`；`ContainerCoordinatorTest` 断言 `openInSystem` 没被调 | 已落地 |
| 看起来像子域的 `…invalid.evil.com` 判为跨 origin | `HostingOrigin.isHostingOrigin` 比 host 全等，不做前缀匹配；`NavigationGateTest` | 已落地 |
| `_blank` 与 `window.open` 各留一行 `nav-blank` | `onCreateWindow` → `onWindowOpenRequest`（返回 false = 不开窗）；`probe.sh` 要求这行 **≥2 条** | 已落地 / 行数待模拟器核实 |
| `tel:` / `mailto:` 交系统 + 一行 `nav-system-scheme` | `NavigationGate` → `HandOffToSystem`；`AndroidActions.openInSystem` 起 `ACTION_VIEW` | 已落地 / 拨号盘邮件是否真起来待核实 |
| 未知 scheme 拦下 + 一行，**且进程还活着** | 同 `Deny` 一条路；`probe.sh` 额外查 `pidof net.xiaoluzhu.crab` | 已落地 / 进程存活待模拟器核实 |
| 三种对话框各一行 `CRAB-DLG`，`JsResult` **必须回一次** | `CrabWebChromeClient` 三个 `onJs*`，回值在 `setOnDismissListener` 里统一给（关按钮、点外面也算） | 已落地 / 弹窗与恢复执行待模拟器核实 |
| 权限一律拒绝 + 每项一行 `CRAB-PERM` | `onPermissionRequest`（`deny()`）与 `onGeolocationPermissionsShowPrompt`（`invoke(origin, false, false)`）两处都接 | 已落地 / 定位那条待模拟器核实 |
| 主文档加载失败进错误态 + `CRAB-ERR load <码>` | `ContainerStateMachine` 过滤子帧；`onReceivedError` 与 `onReceivedHttpError` 都转 | 已落地 / 删入口文件那一遍待模拟器核实 |
| 错误界面文案「页面没能打开」+「重试」按钮 | `MainActivity.ErrorScreen` + `values/strings.xml`（场景与错误码只进 logcat，不上屏） | 已落地 / 上屏待模拟器核实 |
| 渲染进程终止换一次 WebView，第二次进错误态 | `RenderProcessRecovery`（上限一次、同一次终止的重复回调幂等）；`onRenderProcessGone` **必须返回 true** | 已落地 / 造得出来才算核实 |
| 重试回 Loading、重新加载、额度给满 | `ContainerCoordinator.onRetry`；`ContainerCoordinatorTest` | 已落地 |
| SSL 错误只 `cancel()`，一次也不许 `proceed()` | `CrabWebViewClient.onReceivedSslError` | 已落地 / **这条路没有观察面**（见下） |
| 切后台媒体停播、计时器停 | `MainActivity.onPause` → `webView.onPause()` + `pauseTimers()`；观察面是探针页的循环音与每秒 tick | 待模拟器核实 |
| 销毁容器不崩、不泄漏 | `CrabContainer.destroy()`：**先从视图树摘除再 `destroy()`** | 待模拟器核实 |

### M4 留下的四处不确定，照实记

1. **SSL 主文档判定是近似。** `onReceivedSslError` 不带 `WebResourceRequest`，拿不到主帧位，只能拿
   `SslError.getUrl()` 与 `WebView.getUrl()` 比。判错的后果是错误态多进或少进一次。而承载 origin 落在
   `.invalid` 下、解析就会失败，轮不到证书校验，**所以这条路本身造不出来**：真收到一次说明有请求漏到了
   网上，那是 M2「承载 origin 上不发真实网络请求」被破的证据
2. **渲染进程终止造不造得出来未知。** 取法写在 `docs/RUNBOOK.md`（`adb root` + `kill -9` 渲染进程）。
   `adb root` 在 Google Play 镜像上不可用，那种镜像上这一格只能空着
3. **相机与麦克风只剩走查。** pro 明写探针页只按定位这一条。代码上 `onPermissionRequest` 对
   `request.resources` 里的每一项都打一行再整体 `deny()`，与定位走的是同一条路，但**没有观察面**，
   M5 里按未验证写
4. **`nav-blank` 的两行分不出是谁打的。** `onCreateWindow` 拿不到目标地址（在 `resultMsg` 的 transport
   里，只有真的建了窗口才取得到），所以两行的 URL 位置都是发起页，靠**条数**判而不是靠内容判

### 端内自定的部分（pro 没定，报给你知道）

- `CRAB-ERR <场景>` 那三个词 `load` / `ssl` / `render-gone` 是端内起的。pro 只定了这行的形状
- 探针页的循环音（`tone.wav`）与每秒 tick 是端内加的：pro 要求人工看一次「切后台媒体停播、计时器停」，
  而不给点声音、不给个在走的数就没什么可看。tick 同时打进 `CRAB-ENV`，断口在 logcat 里也能回读

## M5 · 调试开关与本端收口

pro 的 M5 是三端汇合，**汇合本身不在本仓**（贴到 pro 的 issue 上，运行记录留各端仓）。本仓在这个里程碑
只做两件事：收掉调试开关，把前四个里程碑的核实记录汇成能并排看的形状。

### 调试开关（pro 定的两条）

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| release 构建禁止开启远程调试 | `RemoteDebugging.enabledFor`；`RemoteDebuggingTest` 断言 `false` 那一格 | 已落地 |
| 取值由构建类型决定，不许写成常量或运行期可改 | `CrabApplication` 传 `BuildConfig.DEBUG`；`AppDebugSwitchTest` 扫源码盯住这一句，并禁掉写死成 `true` / `false` | 已落地 |

放在 `Application.onCreate`：`setWebContentsDebuggingEnabled` 是**进程级**静态开关，跟着某个 WebView 实例
走会漏掉「换过一次 WebView」之后的那个（M4 的渲染进程恢复正好会换）。pro 也明写 Android 与鸿蒙做不到
按容器区分，不要设计成那样。

### 十六条断言逐项状态

顺序即 `ProbeContract.SLUGS`，也是 `scripts/probe.sh` 的输出顺序。**「判定已落地」不等于「过了」**：
判定指页面侧或容器侧的判据已经写好且有自动化兜底，`PASS` / `FAIL` 一格都还没在真容器上产生过。

| # | slug | 判在哪 | 状态 |
| --- | --- | --- | --- |
| 1 | `origin` | 页面比 `location.origin` | 判定已落地 / 待模拟器核实 |
| 2 | `storage` | 页面读写 `localStorage` | 判定已落地 / 待模拟器核实 |
| 3 | `subresource` | 页面看 `.js` 在跑、`.png` 的 `naturalWidth` | 判定已落地 / 待模拟器核实 |
| 4 | `intercept` | 页面 `fetch` 不存在的路径，要 404 + `INTERCEPTED` | 判定已落地 / 待模拟器核实 |
| 5 | `escape` | 页面 `fetch ../outside/…`，读到 `OUT_OF_BOUNDS` 即 FAIL | 判定已落地（`AssetRoutingTest` 覆盖同一判据）/ 待模拟器核实 |
| 6 | `escape-encoded` | 同上，`%2e%2e%2f` 与双重编码 | 判定已落地（同上）/ 待模拟器核实 |
| 7 | `inject-order` | 入口页首行脚本记的快照 | 判定已落地 / 待模拟器核实 |
| 8 | `inject-scope` | `data:` iframe 经 `postMessage` 自报 | 判定已落地 / 待模拟器核实，**且只覆盖帧那一面**（见下） |
| 9 | `nav-same-origin` | 第二页留的 `sessionStorage` 标记 | 判定已落地 / 待人工走一遍 |
| 10 | `nav-back` | 同一个标记 + 当前在入口页 | 判定已落地 / 待人工走一遍 |
| 11 | `nav-cross-origin` | logcat 的 `CRAB-NAV nav-cross-origin` | 判定已落地 / 待人工走一遍 |
| 12 | `nav-blank` | 同上，**要 ≥2 行**（用户点的与脚本发的各一条） | 判定已落地 / 待人工走一遍 |
| 13 | `nav-system-scheme` | 跳没跳出拨号盘只有人眼看得见 | **恒为 MANUAL**（pro 已定，不是没测） |
| 14 | `nav-unknown-scheme` | 那行日志 + `pidof` 进程还在 | 判定已落地 / 待人工走一遍 |
| 15 | `dialog` | 页面读到三种答案 + logcat 三行 `CRAB-DLG` | 判定已落地 / 待人工走一遍 |
| 16 | `permission` | 页面拿到失败回调 + 一行 `CRAB-PERM` | 判定已落地 / 待人工走一遍 |

### 加载后生效差异表 · Android 那一列

pro 的做法是两个实例：A 加载前设成目标值当基线，B 加载前设成相反值、加载完再改，看变不变（四档：
立即生效 / 重载后生效 / 完全不生效 / 改动本身触发重载）。

**本仓没有留这条路**：`CrabWebSettings.applyCrabSpec` 在 `createWebView()` 里一次写完，容器不提供
「加载后再改一项」的入口——那正是 pro「六项全部在首次加载之前落定」要求的形状。所以这七格要填，得临时
造一个实例 B（在 `MainActivity` 里加一段临时代码，跑完删掉），不是跑一遍 `probe.sh` 就能得到的。

| 项 | Android | 怎么观察（pro 定的） |
| --- | --- | --- |
| JavaScript | 未填 | 一段脚本能否改掉页面上的一个显示 |
| DOM storage | 未填 | `localStorage.setItem` 是否抛 |
| 混合内容 | 未填 | 引一个 http 子资源。模拟器上宿主机是 `10.0.2.2`，不是 `127.0.0.1` |
| 文件访问 | 未填 | `fetch` 一个 `file://` URL 能否读到 |
| 有声媒体自动播放 | 未填 | 一个有声 `<video autoplay>` 会不会自己播 |
| 缩放 | 未填 | 双指手势人工看；顺带把系统字号调大一档，看文字变不变 |
| UA | 未填 | `navigator.userAgent` 打印出来（探针页的 `CRAB-ENV` 那行已经在打） |

**七格全空，写「未填」而不是「不生效」。** 探针页现在只有 UA 与 `typeof localStorage` 两项在打，混合
内容、`file://`、`<video autoplay>` 三样探针页里根本没有——差异表要的东西比十六条断言多，这一点别混。

### 五处不许糊过去 · 本端结论

pro 的 M5 点名了五处，其中四处与 Android 有关，逐条给结论（**不因为只是一半就省掉**）：

1. **渲染进程终止：待核实，可能只剩走查。** 取法在 `docs/RUNBOOK.md`（`adb root` + `kill -9`）。
   `adb root` 在 Google Play 镜像上不可用，那种镜像上这条只能写「该端该条未验证」，不当成通过的例外
2. **注入范围的主帧那一面：只剩走查。** `inject-scope` 判的是帧这一面（`data:` iframe 里没有
   `__CRAB__`）。「跨 origin 的主文档里注入没漏」这一支在容器阶段**没有观察面**——跨 origin 的主文档
   一律被闸门拦下、根本不在这个 WebView 里打开。Android 侧的 origin 限定落在
   `HostingOrigin.allowedOriginRules`（`addDocumentStartJavaScript` 的第三个参数），只有走查
3. **`WebSettings` 取值只剩观察一种手段：已按 pro 要的形状拆开。** 取值在 `WebSettingsSpec`（纯 JVM、
   有 `WebSettingsSpecTest`），写入在 `CrabWebSettings.applyCrabSpec`（只有直线赋值、无分支）。
   「拆没拆、拆到什么程度」这次确认过：拆了，写入那几行仍然只能靠观察
4. **相机与麦克风的拒绝：只剩走查。** 探针页只按定位那一条（pro 已定）。代码上
   `onPermissionRequest` 对 `request.resources` 每一项打一行再整体 `deny()`，与定位走同一条路，
   但没有观察面，按未验证记
5. 第五处是鸿蒙的，与本端无关

### 汇合时要交出去的三样

| 交什么 | 从哪来 | 现在的形状 |
| --- | --- | --- |
| 十六条断言逐项结果 | `./scripts/probe.sh` 在 API 37 模拟器上的输出 | **还没有**（本机无 `cmdline-tools`、无镜像、无 AVD） |
| 加载后生效差异表 Android 那一列 | 手工造实例 B | 七格全「未填」 |
| 每条要求的核实记录 | 本文件 M1–M5 各节 | 已成形，状态记号见开头 |

不另设汇总表——本文件就是那份记录，pro 那边只收结果与平台事实。

<!--TASKS-BODY-->
