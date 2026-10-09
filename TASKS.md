# TASKS · Android 侧任务清单

把 pro 五个里程碑「怎么算过」那几张表逐条翻译成本端任务。**判据不在这里**，在 pro；这里只写落在哪个
API、动哪个文件、哪条单测、现在是什么状态。

## 按的是 pro 的哪个版本

- commit `922ffd3`（「收 and 的三条待回流：Safe Browsing 进 M2、文件访问改原生导航观察、宿主单实例进 M4」），
  pro 工作区干净。README 与 `plan/` 五份重读过
- 上一版按的是 `bbe9be9`。`bbe9be9..922ffd3` 共六个提交，**动 Android 判据的只有 `922ffd3` 一个**，四处：
  - **M2 · Safe Browsing**：手段定成框架的 `setSafeBrowsingEnabled(false)`，不走 compat、不取 manifest；明写
    没有观察面，「取值有单测、写入只剩走查」，M5「不许糊过去」加一条。本端代码本来就是这个形状，落点见 M2
    那一节
  - **M3 · 差异表文件访问那一行**：观察法从「页面里 `fetch` 一个 `file://`」改成「原生侧把主帧导航到应用私有
    目录里一个已知的 `file://` 文件」，并要求文件放 `filesDir`、每次观察重新导航、这次导航绕开 M4 的闸门。
    「21 格」改成「20 格」（iOS 那格不适用）。落点见 M5 · 差异表
  - **M4 · 宿主单实例**：新增「宿主」要求、三端落点表（Android `singleTask` + `onNewIntent`）、「故意做坏事」
    第十二行、不进输出的判据从五条变六条、「要试出来的」最后一行（Android 用 `am start -n` 当别的入口）。
    落点见 M4 那一节
  - README 的 M4 那一行跟着改成「十二处故意触发」
- 另外五个（`b7b7647` / `2e27550` / `5ddc786` / `5a423d7` / `a3cecc0`）逐个读过，**不动 Android 的判据**：iOS
  承载 origin 拍成 `crab://ios.crab.invalid`（README 加一句「三端 origin 各不相同，页面比对 origin 要认三个值」）、
  鸿蒙档位维持 23 与模拟器能当验收面、M4「回读日志」那格记上 iOS 已成立 / 鸿蒙未成立，其余是 `进度.md` 快照
- 本仓送去的第 1、2、4、5 条待回流都进了 `922ffd3`，对照见 [`docs/待回流-pro.md`](docs/待回流-pro.md) 的
  「已回流」；第 3 条（`textZoom`）还占着位，另新开一条第 6 条（入口页后退不销毁容器）
- 再往前：`e857625..287c92d` 那一批四条 Android 平台事实也产自本端，同见「已回流」

## 运行记录 · 十六行第一遍（2026-09-21）

`scripts/probe.sh` 的输出，**退出码 0**：

```
origin PASS
storage PASS
subresource PASS
intercept PASS
escape PASS
escape-encoded PASS
inject-order PASS
inject-scope PASS
nav-same-origin PASS
nav-back PASS
nav-cross-origin PASS
nav-blank PASS
nav-system-scheme MANUAL
nav-unknown-scheme PASS
dialog PASS
permission PASS
```

跑的时候是什么条件：

| 什么 | 值 |
| --- | --- |
| 工程 | 工作机的本仓检出，HEAD `8e4f40b`，与 `origin/main` 同点 |
| 容器 | `emulator-5554`，`ro.build.version.sdk` = 37 |
| WebView 内核 | Chrome/145（从探针页打出来的 UA 读到） |
| 装之前 | `./scripts/check.sh` 绿 |
| 这一遍的跑法 | `--no-install`，人工八个按钮 / 三个对话框 / 一次系统返回键先做完再回车 |

`nav-system-scheme` 那条 `MANUAL` 按 pro 的规约另附一行人工观察结果：

> **`tel:` 跳出了模拟器里的拨号，`mailto:` 跳出了邮件。两次都交给了系统。** 观察时间 2026-09-21，
> 与上面那十六行是同一遍。

**这一遍跑在工作机上，不是跑在本机。** 两台机器的差别见
[`docs/PITFALLS.md`](docs/PITFALLS.md) 的「环境」一节：本机没有 `cmdline-tools` / system-image / AVD，
`probe.sh` 在本机起不来，所以本机的 AI 会话只能跑编译、单测、静态检查那三样。

**十六行不覆盖的还有七格**，逐格记在下面各节。`DOCUMENT_START_SCRIPT` 那一格 2026-09-23 读到了值，
切后台那一行 2026-09-24 看过一次（原始输出待补），其余还没跑：

| 还欠什么 | 记在哪一节 |
| --- | --- |
| `DOCUMENT_START_SCRIPT` 的取值（注入走原生还是兜底） | M3 · 注入走哪条路。**已读到 `true`**（2026-09-23）：走原生，兜底那条没跑过 |
| 切后台 / 最近任务划掉 / 入口页直接后退 | M4 · 三格。切后台已观察到停播、停表（2026-09-24，原始输出待补）；另两格未跑 |
| 再次启动回到原容器（pro 922ffd3 新加的第十二行） | M4 · 那一格。`singleTask` 已落地，未跑 |
| 渲染进程终止两次 | M4 · 那一格 |
| 入口文件挪走看错误态 | M4 · 那一格 |
| 加载后生效差异表 Android 那一列 | M5 · 差异表 |
| 文字跟不跟随系统字号 | M3 · 缩放那一行 |

**宿主改了 `launchMode` 与返回键之后，上面那十六行要在工作机上回归一遍**才还算数，顺序排在
[`docs/RUNBOOK.md`](docs/RUNBOOK.md) 的「零」。这几格往后不再手敲 `adb` 抄输出，改成人只操作、日志自动落盘，
规划见「后续 · 模拟器那一半的日志自动落盘」。

## 状态记号

| 记号 | 意思 |
| --- | --- |
| 已落地 | 代码在，`./scripts/check.sh` 绿（判定逻辑有单测） |
| 已核实 | 在 API 37 模拟器上观察过一次，出处是上面那十六行或另附的人工结果 |
| 待模拟器核实 | 代码在，但判据只能在模拟器上看，**十六行不覆盖它**。上面那张「还欠什么」表就是这些格 |
| 只剩走查 | pro 明写在容器阶段没有观察面，M5 里要照实写「未验证」 |

**「已落地」仍然不等于「过了」，「已核实」才是。** 十六条已经在真容器里跑过一遍，但它们只覆盖十六条；
上面那七格大多还没跑，别拿退出码 0 当整个 M2–M4 都过了。

## M1 · 装到模拟器

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 构建通过 | `crab.android.application` + 三模块，`./scripts/check.sh` | 已落地 |
| 标识 `net.xiaoluzhu.crab` | `app/build.gradle.kts` 的 `namespace` 与 `applicationId` | 已落地 |
| 应用名 Crab / 螃蟹 | `values/strings.xml` + `values-zh/strings.xml` | 已落地 |
| `minSdk = 37` | `gradle/libs.versions.toml` | 已落地 |
| 装得上、屏幕上有一个原生页面 | `MainActivity`（M1 不碰 WebView） | 已核实 |

「装得上、起得来」是十六行那一遍顺带核掉的：`:app:installDebug` 成功、应用起来、探针页十六条都判了出来。
**M1 当时那个「纯原生页面」形态已经不存在了**——M2 起 `MainActivity` 就挂上了容器，核到的是现在这个形态。

M1 还有三件一次性动作（pro 的「动工之前」，顺序不能颠倒），都已经做完：

| 动作 | 落点 |
| --- | --- |
| 在旧 `main` 的 HEAD 上打 tag `pre-rebuild` 并推远端（唯一的回退路径） | tag 指向 `000854a`（「chore: 保留重建前的未提交改动」——先把脏改动提进去，免得它只活在工作区），`git ls-remote --tags origin` 里有 |
| `main` 上清空，不留 `archive/` 一类目录 | `da4d343` 起全部重写；旧的 10 模块基座、JSBridge、网络层、6 篇旧 ADR 一并没了，只留 `gradle/wrapper`、`gradlew`、`.gitignore`、`.editorconfig`、`.claude/`、`gradle.properties` |
| `docs/` 分两处安置 | 端侧工程事实（AGP 9 那一串咬合、环境自检、`kotlin-test-junit`）进了本仓 `docs/PITFALLS.md` 与 `CLAUDE.md`；剩下三条**平台事实**没有自行搬进 pro（pro 仓一个字不改是本次的边界），已在会话里报给你，由你决定进不进 pro |

`and` 里那份 JSBridge 契约随之作废，重建后不存在。

## M2 · 本地承载

十六条断言里 M2 占前六条（`origin` / `storage` / `subresource` / `intercept` / `escape` /
`escape-encoded`）。它们全部由探针页自己判，结果经 `console.log` → `onConsoleMessage` → logcat →
`scripts/probe.sh` 回读。**六条都在真容器里跑过一遍了，全 PASS**（出处见上面那节运行记录）。

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 页面从 assets 起来，`location.origin` 是约定的承载 origin | `HostingOrigin` + `CrabContainer` 的 `WebViewAssetLoader`（`setDomain` / `setHttpAllowed(false)`） | 已核实（`origin` PASS） |
| 同目录子资源（`.js` / `.png`）加载成功 | `assets/probe/probe.js` · `probe.png`，走同一个 `PathHandler` | 已核实（`subresource` PASS） |
| `localStorage` 可读可写 | `settings.domStorageEnabled = true`（M2 先借这一项） | 已核实（`storage` PASS） |
| 承载目录里不存在的路径 → 容器自己回 404 + `INTERCEPTED` | `CrabAssetPathHandler`（未命中不返回 null） | 已核实（`intercept` PASS） |
| 明文穿越 `../` 读不到承载目录外的文件 | `AssetRouting.resolve` 判 `OutOfBounds`；`AssetRoutingTest` | 已落地 + 已核实（`escape` PASS） |
| 编码穿越 `%2e%2e%2f`（含双重编码）同样读不到 | 同上，先解码再判；`AssetRoutingTest` | 已落地 + 已核实（`escape-encoded` PASS） |
| 承载 origin 单点定义要有**一条可执行检查** | `HostingOriginSingleDefinitionTest` 扫源码树（定义处 + 探针页期望值，第三处即红） | 已落地 |
| 探针页 / `probe.sh` / slug 清单三者对齐 | `ProbeContractAlignmentTest`（`.html` / `.js` / `.sh` 没有别的兜底） | 已落地 |
| 越界素材真实存在（否则 `escape` 测不到东西） | `assets/outside/out-of-bounds.txt`，内容 `OUT_OF_BOUNDS` | 已落地 |
| Safe Browsing 关掉（Android 独有，pro M2 定了手段：框架的 `setSafeBrowsingEnabled(false)`，不走 compat、不取 manifest） | `WebSettingsSpec.SAFE_BROWSING_ENABLED = false`（`WebSettingsSpecTest`）→ `applyCrabSpec` 里 `safeBrowsingEnabled =` | **只剩走查**（取值已落地；写入没有观察面，与 pro M5「Android 的 Safe Browsing 只剩走查」那条对上，M5 里 Android 那一列明写未验证） |

**这一格 2026-10-09 对着 pro `922ffd3` 核过一次，代码不用动**：

- 写入走的是框架那个 setter：`applyCrabSpec` 里的 `safeBrowsingEnabled = …` 编成的字节码是
  `invokevirtual android/webkit/WebSettings.setSafeBrowsingEnabled:(Z)V`（`javap -c` 读
  `CrabWebSettingsKt.class`）
- 全仓没有 `WebSettingsCompat`，manifest 里也没有 `android.webkit.WebView.EnableSafeBrowsing` meta-data
- 取值有单测：`WebSettingsSpecTest` 的「Safe Browsing 关」断言 `SAFE_BROWSING_ENABLED` 为 false

手段为什么是这个（框架 setter 没弃用、compat 在 `minSdk = 37` 下恒转给框架、manifest 那条是应用级且没有
可测取值）已经进了 pro M2 的「平台事实」，这里不再复述。**没有观察面**也是 pro 写定的：要看见它得有一次被
拦下来，而承载 origin 落在 `.invalid` 下、容器一条真实网络请求都不发。所以「写入」那半只剩走查，与 SSL 那条
同类。

`scripts/probe.sh` 输出的十六行里，前六行就是这一节。**别把「六行 PASS」读成「M2 过了」**：pro 的 M2
还要求承载 origin 上不发真实网络请求，那一条在本端的形状是「拦截点未命中也自己回 404」，已经由
`intercept` 那条覆盖；而 Safe Browsing 与 SSL 两条属走查。

## M3 · 配置、注入与 UA

M3 添的是第七、八条断言（`inject-order` / `inject-scope`）。**六项配置的「写没写进去」这里一条单测都
给不出**：JVM 单测里 `WebSettings` 是 `android.jar` 的 stub，断言它的取值只会得到与代码无关的绿。所以
取值（`WebSettingsSpec`，有单测）与写入（`CrabWebSettings.applyCrabSpec`，只能观察）刻意分成两处。

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 六项配置按取值表落地 | `WebSettingsSpec`（取值，`WebSettingsSpecTest`）→ `applyCrabSpec`（写入） | 取值已落地 / 写入逐项见下 |
| JavaScript 与 DOM storage 开 | 同上两个字段 | 已核实（探针页本身跑起来了 + `storage` PASS） |
| 混合内容 `NEVER_ALLOW` | `MixedContentPolicy` → `WebSettings.MIXED_CONTENT_NEVER_ALLOW`（常量值 0/2/1 由 javap 核过） | 取值已落地 / 写入**十六行不覆盖**（探针页没有 http 子资源，见差异表那节） |
| 文件与内容访问四项全关 | 同上四个字段 | 取值已落地 / 写入**十六行不覆盖**（探针页没有 `file://` 请求） |
| 缩放三项关 + `textZoom = 100` | 同上；文字大小跟不跟随系统字号要在模拟器上看 | 取值已落地 / 写入待模拟器核实（七格之一） |
| 多窗口 / 脚本开窗 / 定位**显式开** | 三项 true，为的是 M4 的回调能被调用后当场拒绝并留日志 | 已核实（`nav-blank` 与 `permission` 都 PASS——回调被调用了才有那几行日志） |
| UA 追加 `Crab/0.1.0`，不替换整串 | `UserAgent.decorate`（`UserAgentTest`）；版本号由 `:app` 的 `BuildConfig.VERSION_NAME` 传入 | 已落地 + 已核实（2026-09-23 整串抄下：结尾 `Crab/0.1.0`，系统 UA 完整保留，见下） |
| 页面开口之前注入，`inject-order` 成立 | `DocumentStartScript.source` + `WebViewCompat.addDocumentStartJavaScript`；入口页首行 `<script>` 记快照 | 已核实（`inject-order` PASS） |
| 不支持时走兜底，且**两条路不叠加** | `CrabContainer.documentStartScriptSupported` 一个布尔决定；兜底是 `CrabAssetPathHandler(inlineDocumentStartScript = true)` 改写 HTML | 不叠加已核实（`injected == 1`）/ **本机走原生，兜底没跑过**（`DOCUMENT_START_SCRIPT=true`，见下一节） |
| 注入范围不漏到别的 origin（`inject-scope`） | `HostingOrigin.allowedOriginRules`；页面用 `data:` iframe 经 `postMessage` 自报 | 已核实（`inject-scope` PASS，**只覆盖帧那一面**）|
| 脚本重复注入看得出来 | 脚本用 `window.__CRAB__ ||` 初始化、`injected` 只增不减；`DocumentStartScriptTest` 盯着源码里这两条 | 已落地 |
| 兜底只改 HTML、不碰素材 | `DocumentStartScript.appliesTo`（`DocumentStartScriptTest`） | 已落地 |
| 承载 origin 的 Cookie 不与原生共享 | 落点是**什么都不做**：容器没有网络层（Retrofit / OkHttp 已砍），`CookieManager` 一次也没碰，没有可共享的对方 | 只剩走查 |
| storage 跨启动存活，容器不主动清空 | 同样是**什么都不做**：全仓没有 `clearCache` / `WebStorage` / `removeAllCookies` 的调用 | 只剩走查 / 跨启动存活待模拟器核实 |
| 加载后生效差异表（七行） | 探针页 `CRAB-ENV` 一行打 UA 与 `typeof localStorage` | 待模拟器核实（七格全空，见 M5 那节） |

**UA 那一格已核实**（2026-09-23，工作机 API 37 AVD，`adb logcat -d | grep 'CRAB-ENV'`）。原文：

```
CRAB-ENV UA: Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/145.0.0.0 Mobile Safari/537.36 Crab/0.1.0 · typeof localStorage: object
```

- 结尾是 `Crab/0.1.0`，前面的系统 WebView UA 完整保留（带 `wv`），没有被整串替换。拼接形状由
  `UserAgentTest` 盯着，这一次观察补上的是「生效值确实长这样」
- `Android 10; K` 与 `Chrome/145.0.0.0` 是 UA 缩减冻住的段，已回流 pro 的 M3，不再报
- 这串是**实例 A 的基线**。差异表 UA 那一格要的是实例 B 的前后对照，仍是「未填」；M5 三端并排比 UA
  用的就是这一串

### M3 · 注入走哪条路（七格之一，已读到 `true`）

pro 的 M3「要试出来的」明写这一格**不能拿 `inject-order` 绿当答案**：原生注入与兜底都要求
`injected == 1`，那是设计意图（两条叠加就是 bug），所以绿了认不出走的是哪条。支持与否只能另打一次。

落点是 `CrabContainer` 里那个 `init` 块，开机就打一行：

```
CRAB-ENV DOCUMENT_START_SCRIPT=<true|false> webview=<包名>/<完整版本号>
```

前缀刻意写成字面量、不从 `CrabLog` 或 `ProbeContract` 派生——那两处是契约、有单测盯着、`probe.sh` 靠它们
回读；这一行只是一次性诊断，读到之后删掉它不该牵动契约。

回读：`adb logcat -d | grep 'CRAB-ENV DOCUMENT_START_SCRIPT'`。**两种结果各有一笔后账，别当读到值就完了：**

| 读到 | 意味着 | 那么欠什么 |
| --- | --- | --- |
| `true` | 这台机器一直走 `addDocumentStartJavaScript`，**兜底那条一次没跑过** | `CrabAssetPathHandler(inlineDocumentStartScript = true)` 改写 HTML 那条路没有观察面。要么找一个旧内核的镜像，要么在 M5 里按未验证写 |
| `false` | 这台机器一直走兜底，**原生那条没有观察面** | 反过来同理；且要确认 `inject-order` 的 PASS 是兜底挣来的 |

**读到的是 `true`**（2026-09-23，工作机 API 37 AVD，`adb logcat -d | grep 'CRAB-ENV'`）。原文：

```
CRAB-ENV DOCUMENT_START_SCRIPT=true webview=com.google.android.webview/145.0.7632.218
```

- 注入一直走原生的 `addDocumentStartJavaScript`，`inject-order` 的 PASS 是原生那条跑出来的
- 兜底那条（`CrabAssetPathHandler` 改写 HTML）**一次都没跑过**。要观察它得另找一个内核不支持
  `DOCUMENT_START_SCRIPT` 的镜像；找到之前，M5 里按「未验证」写
- 结论只对 `com.google.android.webview/145.0.7632.218` 这个内核成立，换镜像要重读

打这一行的 `init` 块留到收口时删。

**M3 留给模拟器的两个未知**（原先三个，`data:` iframe 那条已经答掉）：

1. 文字大小跟不跟随系统字号（`textZoom = 100` 是否真的挡住了系统字号）
2. 混合内容、`file://`、`<video autoplay>` 三项配置的生效情况——探针页里根本没有这三样，属差异表那一摊

`data:` URL 的 iframe **在 Android 上起得来**（`inject-scope` PASS 就是它挣来的），所以「换
`sandbox` + `srcdoc` 退路」这件事在 Android 侧不必发生。这条已作为平台事实回流 pro。

**关掉缩放欠下的那笔债，本端认领不了，但记在这里。** pro 要求关缩放的同时由**页面侧提供字号调节**
（双指缩放是低视力用户放大内容的唯一手段，`textZoom = 100` 又把系统字号那条路一并按住了）。容器这一层
不实现它，探针页也不是产品页面、不承载这个功能。**接 FE 时这条要一起带过去**——写在这里是为了它有人
认领，不是为了在本仓解决。

## M4 · 导航与降级

M4 补齐剩下八条断言（`nav-same-origin` / `nav-back` / `nav-cross-origin` / `nav-blank` /
`nav-system-scheme` / `nav-unknown-scheme` / `dialog` / `permission`）。**其中四条页面判不了**——被拦下的
那一跳页面什么都收不到，证据只在容器打的 `CRAB-NAV` 里，由 `scripts/probe.sh` 从 logcat 回读。

判定全在 `:core:container`（`NavigationGate` / `CrabLog` / `ContainerStateMachine` /
`RenderProcessRecovery` / `ContainerCoordinator`，都有单测）；`:core:webview` 只把回调原样转进来、把返回
值原样转回去。

| 判据 | 本端落点 | 状态 |
| --- | --- | --- |
| 同 origin 跳转放行 | `NavigationGate.decide` → `Allow`；`NavigationGateTest` | 已落地 + 已核实（`nav-same-origin` PASS） |
| 返回键回上一页，到底交回宿主 | `MainActivity.backCallback`（`canGoBack()` → `goBack()`，否则暂时关掉自己、重新分发、再打开） | 回上一页已核实（`nav-back` PASS，那时还是 `remove()` 的写法）/ **到底交回宿主待核实** |
| 跨 origin 拦下 + 一行 `CRAB-NAV nav-cross-origin`，**不交系统浏览器** | `NavigationGate` → `Deny`；`ContainerCoordinatorTest` 断言 `openInSystem` 没被调 | 已落地 + 已核实（`nav-cross-origin` PASS） |
| 看起来像子域的 `…invalid.evil.com` 判为跨 origin | `HostingOrigin.isHostingOrigin` 比 host 全等，不做前缀匹配；`NavigationGateTest` | 已落地 |
| `_blank` 与 `window.open` 各留一行 `nav-blank` | `onCreateWindow` → `onWindowOpenRequest`（返回 false = 不开窗）；`probe.sh` 要求这行 **≥2 条** | 已落地 + 已核实（`nav-blank` PASS，即 ≥2 行确实出来了） |
| `tel:` / `mailto:` 交系统 + 一行 `nav-system-scheme` | `NavigationGate` → `HandOffToSystem`；`AndroidActions.openInSystem` 起 `ACTION_VIEW` | 已落地 + 已核实（`MANUAL` + 人工观察：拨号盘与邮件都跳出来了，见运行记录那节） |
| 未知 scheme 拦下 + 一行，**且进程还活着** | 同 `Deny` 一条路；`probe.sh` 额外查 `pidof net.xiaoluzhu.crab` | 已落地 + 已核实（`nav-unknown-scheme` PASS，`pidof` 是脚本自己查的） |
| 三种对话框各一行 `CRAB-DLG`，`JsResult` **必须回一次** | `CrabWebChromeClient` 三个 `onJs*`，回值在 `setOnDismissListener` 里统一给（关按钮、点外面也算） | 已落地 + 已核实（`dialog` PASS：三个原生框都弹了出来、页面三个答案都收到了） |
| 权限一律拒绝 + 每项一行 `CRAB-PERM` | `onPermissionRequest`（`deny()`）与 `onGeolocationPermissionsShowPrompt`（`invoke(origin, false, false)`）两处都接 | 已落地 / 定位那条已核实（`permission` PASS）/ 相机麦克风只剩走查 |
| 主文档加载失败进错误态 + `CRAB-ERR load <码>` | `ContainerStateMachine` 过滤子帧；`onReceivedError` 与 `onReceivedHttpError` 都转 | 已落地 / **删入口文件那一遍待核实**（七格之一） |
| 错误界面文案「页面没能打开」+「重试」按钮 | `MainActivity.ErrorScreen` + `values/strings.xml`（场景与错误码只进 logcat，不上屏） | 已落地 / **上屏待核实**（与上一格同一遍） |
| 渲染进程终止换一次 WebView，第二次进错误态 | `RenderProcessRecovery`（上限一次、同一次终止的重复回调幂等）；`onRenderProcessGone` **必须返回 true** | 已落地 / **待核实，且可能整条不成立**（七格之一，见下） |
| 重试回 Loading、重新加载、额度给满 | `ContainerCoordinator.onRetry`；`ContainerCoordinatorTest` | 已落地 |
| SSL 错误只 `cancel()`，一次也不许 `proceed()` | `CrabWebViewClient.onReceivedSslError` | 已落地 / **这条路没有观察面**（见下） |
| 切后台媒体停播、计时器停 | `MainActivity.onPause` → `webView.onPause()` + `pauseTimers()`；观察面是探针页的循环音与每秒 tick | **已观察到，原始输出待补**（2026-09-24 工作机 API 37 AVD：点「播放声音」后按 Home，声音停，`onPause()` 生效；从最近任务切回，tick 接着之前的编号走，`pauseTimers()` 生效。tick 日志的时间戳断口还没留下，重跑后补原文） |
| 销毁容器不崩、不泄漏 | `CrabContainer.destroy()`：**先从视图树摘除再 `destroy()`** | **待核实**（最近任务划掉那一遍） |
| 宿主单实例：再次启动回到原容器、不重载（pro 922ffd3） | manifest 里 `MainActivity` 显式 `android:launchMode="singleTask"`；`onNewIntent` 只打一行 `CRAB-ENV onNewIntent <Intent>`，新请求的内容 pro 留给 FE 接入时定 | 取值已落地（`HostSingleInstanceTest`）/ **待核实**（RUNBOOK「四 · 再次启动」） |

**这十六行覆盖到的与没覆盖到的，界线就在「有没有按钮」。** 十六条断言全是探针页上的按钮 + logcat 回读，
所以导航、对话框、权限那九格一遍跑完；而「切后台」「最近任务划掉」「在入口页直接后退」「再次启动」
「删掉入口文件」「杀渲染进程」这六件事探针页上没有按钮，得按 [`docs/RUNBOOK.md`](docs/RUNBOOK.md) 另外走。
除了切后台看过一次，其余五件还没走。

### 宿主单实例怎么落的

pro M4 的落点表写的是「宿主 Activity 的 `launchMode` 取 `singleTask`，再次启动走 `onNewIntent`」，并要求取值
照 M3 的规矩显式写、能进单测或构建期检查的进。本端：

- **取值**：`app/src/main/AndroidManifest.xml` 里显式写 `singleTask`（不写就是默认的 `standard`，恰好是多开的
  那一种）。`HostSingleInstanceTest` 解析这份 manifest，断言三件事：宿主 `launchMode` 是 `singleTask`、它就是
  带 `MAIN` + `LAUNCHER` 的那个入口、`MainActivity` 接了 `onNewIntent`。`launchMode` 由系统服务端读，JVM 里
  没有别的东西会因为它取错而失败，所以只能扫文件
- **为什么不是 `singleTop`**：它只在实例已经在栈顶时复用，从别的任务进来照样新建；pro 定的就是 `singleTask`
- **`onNewIntent` 里只有一行诊断**：pro 要核「新请求有没有走到这个回调」，而它在屏幕上什么都不改，所以打
  一行 `CRAB-ENV onNewIntent <Intent>`。前缀写成字面量、不进 `CrabLog`，与 `CrabContainer` 那行 `CRAB-ENV`
  同理，读到结论可以删
- **连带改的返回键**（出口 1，本端实现）：原先到底时 `remove()` 掉 `backCallback`。Android 12 起，带
  `MAIN` + `LAUNCHER` 的任务根在这一下只是挪到后台、实例不销毁（`Activity.onBackPressed` 的 javadoc），再点图标
  回来走 `onNewIntent`、不再 `onCreate`，回调就再也挂不上——第二页上的返回键直接退到桌面。`singleTask` 让
  「回来的是原实例」从「看怎么回来」变成「一定」，所以这一格必踩。现在改成分发那一下暂时 `isEnabled = false`，
  分发完马上打开。设备上的表现排在 `back-at-root` 那一遍
- **「入口页后退之后容器其实还活着」算不算 pro 说的「交宿主关掉容器」**：这是判据怎么读的问题，本端不自决，
  记在 [`docs/待回流-pro.md`](docs/待回流-pro.md) 第 6 条，现在按平台默认走

### M4 留下的四处不确定，照实记

1. **SSL 主文档判定是近似。** `onReceivedSslError` 不带 `WebResourceRequest`，拿不到主帧位，只能拿
   `SslError.getUrl()` 与 `WebView.getUrl()` 比。判错的后果是错误态多进或少进一次。而承载 origin 落在
   `.invalid` 下、解析就会失败，轮不到证书校验，**所以这条路本身造不出来**：真收到一次说明有请求漏到了
   网上，那是 M2「承载 origin 上不发真实网络请求」被破的证据
2. **渲染进程终止造不造得出来未知。** 取法写在 `docs/RUNBOOK.md`（`adb root` + `kill -9` 渲染进程）。
   顺序是先 `adb root`：拿不到就整条不成立（带 Google Play 的镜像一律拒绝，这条已回流 pro 的 M4），
   退代码走查、在 M5 里明写「该端该条未验证」。**「跑了一整天没崩过」不是通过**——没触发过的恢复路径
   与写错了的恢复路径在日志上长得一模一样
3. **相机与麦克风只剩走查。** pro 明写探针页只按定位这一条。代码上 `onPermissionRequest` 对
   `request.resources` 里的每一项都打一行再整体 `deny()`，与定位走的是同一条路，但**没有观察面**，
   M5 里按未验证写
4. **`nav-blank` 的两行分不出是谁打的。** `onCreateWindow` 拿不到目标地址（在 `resultMsg` 的 transport
   里，只有真的建了窗口才取得到），所以两行的 URL 位置都是发起页，靠**条数**判而不是靠内容判

### 手势位（`hasGesture()`）本端没有消费方

pro 的 M4 把「闸门能不能区分用户点击与脚本发起」列进「要试出来的」，Android 的手段是
`WebResourceRequest.hasGesture()`。本端 `NavigationGate.decide` 只吃 `url` 与 `isMainFrame`，**刻意不看
手势位**：跨 origin 与 `_blank` 都是一律拒绝，判定不因发起者而变，多接一个入参只会多一条测不到的分支。
pro 自己也写了这条「现在没有消费方」，所以它不是缺口。真要那份对照数据（同一地址真手点一次、
`setTimeout` 改 `location` 一次，把手势位都打出来比），得在 `CrabWebViewClient` 里临时加一行日志再删掉
——与差异表实例 B 一样，不是跑 `probe.sh` 能得到的。

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

顺序即 `ProbeContract.SLUGS`，也是 `scripts/probe.sh` 的输出顺序。这一列是 2026-09-21 那一遍的结果，
**结果的出处只有一个**：上面那节运行记录。重跑一遍就整列覆盖，不在这里攒历史。

| # | slug | 判在哪 | 2026-09-21 |
| --- | --- | --- | --- |
| 1 | `origin` | 页面比 `location.origin` | PASS |
| 2 | `storage` | 页面读写 `localStorage` | PASS |
| 3 | `subresource` | 页面看 `.js` 在跑、`.png` 的 `naturalWidth` | PASS |
| 4 | `intercept` | 页面 `fetch` 不存在的路径，要 404 + `INTERCEPTED` | PASS |
| 5 | `escape` | 页面 `fetch ../outside/…`，读到 `OUT_OF_BOUNDS` 即 FAIL | PASS（`AssetRoutingTest` 覆盖同一判据） |
| 6 | `escape-encoded` | 同上，`%2e%2e%2f` 与双重编码 | PASS（同上） |
| 7 | `inject-order` | 入口页首行脚本记的快照 | PASS，**挣来 PASS 的是原生那条**（`DOCUMENT_START_SCRIPT=true`）；兜底那条没跑过，按未验证记（见 M3 那一节） |
| 8 | `inject-scope` | `data:` iframe 经 `postMessage` 自报 | PASS，**只覆盖帧那一面**（见下） |
| 9 | `nav-same-origin` | 第二页留的 `sessionStorage` 标记 | PASS |
| 10 | `nav-back` | 同一个标记 + 当前在入口页 | PASS（**入口页上再退一次**那半没覆盖） |
| 11 | `nav-cross-origin` | logcat 的 `CRAB-NAV nav-cross-origin` | PASS |
| 12 | `nav-blank` | 同上，**要 ≥2 行**（用户点的与脚本发的各一条） | PASS |
| 13 | `nav-system-scheme` | 跳没跳出拨号盘只有人眼看得见 | **MANUAL**（pro 已定，不是没测）+ 人工结果：拨号与邮件都跳出来了 |
| 14 | `nav-unknown-scheme` | 那行日志 + `pidof` 进程还在 | PASS |
| 15 | `dialog` | 页面读到三种答案 + logcat 三行 `CRAB-DLG` | PASS |
| 16 | `permission` | 页面拿到失败回调 + 一行 `CRAB-PERM` | PASS |

退出码 0。**十六格齐了不等于 M2–M4 过了**，差的七格在本文件开头那张「还欠什么」表里；改了 `launchMode` 之后这一列也要回归一遍才还算数。

### 加载后生效差异表 · Android 那一列

pro 的做法是两个实例：A 加载前设成目标值当基线，B 加载前设成相反值、加载完再改，看变不变（四档：
立即生效 / 重载后生效 / 完全不生效 / 改动本身触发重载）。

**本仓没有留这条路**：`CrabWebSettings.applyCrabSpec` 在 `createWebView()` 里一次写完，容器不提供
「加载后再改一项」的入口——那正是 pro「六项全部在首次加载之前落定」要求的形状。所以这七格要填，得临时
造一个实例 B，不是跑一遍 `probe.sh` 就能得到的。B 已经写成一份补丁 [`docs/实例B.patch`](docs/实例B.patch)
（`InstanceB.kt` 新文件 + `MainActivity` 两行 + manifest 两样），**不进提交**：工作机上 `git apply` 上去跑、
`git apply -R` 撤掉。本机打上去编译、格式、lint 都过过一次，**没在设备上跑过**。步骤写在
[`docs/RUNBOOK.md`](docs/RUNBOOK.md) 的「七」。

| 项 | Android | 怎么观察（pro 定的） |
| --- | --- | --- |
| JavaScript | 未填 | 一段脚本能否改掉页面上的一个显示 |
| DOM storage | 未填 | `localStorage.setItem` 是否抛 |
| 混合内容 | 未填 | 引一个 http 子资源。模拟器上宿主机是 `10.0.2.2`，不是 `127.0.0.1` |
| 文件访问 | 未填 | 原生侧把主帧导航到应用私有目录里一个已知的 `file://` 文件，看打不打得开（pro `922ffd3` 改的） |
| 有声媒体自动播放 | 未填 | 一个有声 `<video autoplay>` 会不会自己播 |
| 缩放 | 未填 | 双指手势人工看；顺带把系统字号调大一档，看文字变不变 |
| UA | 未填 | `navigator.userAgent` 打印出来（探针页的 `CRAB-ENV` 那行已经在打） |

**七格全空，写「未填」而不是「不生效」。** 十六行那一遍跑的是实例 A 的基线形态，一格也没喂进这张表：
探针页只有 UA 与 `typeof localStorage` 两项在打（实例 A 的整串已抄下，但那是基线、不是对照），混合内容、`file://`、
`<video autoplay>` 三样探针页里根本没有。**差异表要的东西比十六条断言多，这一点别混。**

**文件访问那一格照 pro 的新观察法落**，三处要求各落在补丁的一个地方：

| pro 的要求 | 本端怎么落 |
| --- | --- |
| 目标文件放在应用私有目录（不放 `android_asset`，`setAllowFileAccess` 管不到那里） | `InstanceB.beforeFirstLoad` 每次启动把 `crab-b.html` 写进 `filesDir`，打开时自己打一行 `CRAB-ENV B file opened` |
| 这次导航不得被 M4 的闸门拦下，怎么绕属端内自定 | 原生 `webView.loadUrl(file://…)`。`shouldOverrideUrlLoading` 对应用自己 `loadUrl` 的导航不回调（`WebViewClient` 的 javadoc），闸门根本没被问到；证据是这一跳没有 `CRAB-NAV` 行。**不在 `NavigationGate` 里开口子**——那是常驻代码，B 跑完要一行不留 |
| 每次观察都是一次新的导航，看完回到探针页 | `b file` 一次导航一次，`b entry` 走「重试」回探针页（打不开时容器在错误态，只 `loadUrl` 切不回界面） |

只翻 `allowFileAccess`：取值表里另外三个文件与内容开关同样是关，但主帧导航到 `file://` 只受这一项管。
目标值是关，所以 A 的基线预期是「打不开」，进错误态、一行 `CRAB-ERR load <码>`。

**混合内容那一格有个前提**：manifest 里没有 `INTERNET` 权限，也没开明文，补丁里两样都临时加了，否则
设成什么都加载不到。

### 六处不许糊过去 · 本端结论

pro 的 M5 点名了六处（`922ffd3` 加了 Safe Browsing 那一处），其中五处与 Android 有关，逐条给结论
（**不因为只是一半就省掉**）：

1. **渲染进程终止：未验证。** 取法在 `docs/RUNBOOK.md`（`adb root` + `kill -9`）。先看 `adb root` 拿不拿
   得到：拿不到（带 Google Play 的镜像一律拒绝）这条在 Android 上整条不成立，写「该端该条未验证」，
   不当成通过的例外。**不许拿「一直没崩过」顶上去**
2. **注入范围的主帧那一面：只剩走查。** `inject-scope` 判的是帧这一面（`data:` iframe 里没有
   `__CRAB__`，2026-09-21 PASS）。「跨 origin 的主文档里注入没漏」这一支在容器阶段**没有观察面**——跨
   origin 的主文档一律被闸门拦下、根本不在这个 WebView 里打开。Android 侧的 origin 限定落在
   `HostingOrigin.allowedOriginRules`（`addDocumentStartJavaScript` 的第三个参数），只有走查
3. **`WebSettings` 取值只剩观察一种手段：已按 pro 要的形状拆开。** 取值在 `WebSettingsSpec`（纯 JVM、
   有 `WebSettingsSpecTest`），写入在 `CrabWebSettings.applyCrabSpec`（只有直线赋值、无分支）。
   「拆没拆、拆到什么程度」这次确认过：拆了，写入那几行仍然只能靠观察，且六项里只观察到了两项
   （JavaScript 与 DOM storage），另四项要靠差异表那一摊
4. **相机与麦克风的拒绝：只剩走查。** 探针页只按定位那一条（pro 已定，2026-09-21 PASS）。代码上
   `onPermissionRequest` 对 `request.resources` 每一项打一行再整体 `deny()`，与定位走同一条路，
   但没有观察面，按未验证记
5. **Safe Browsing：只剩走查。** 取值有单测（`WebSettingsSpecTest`），写入走框架的
   `setSafeBrowsingEnabled`（`javap` 核过），没有观察面，Android 那一列明写未验证。见 M2 那一节
6. 第六处是鸿蒙的，与本端无关

### 汇合时要交出去的三样

| 交什么 | 从哪来 | 现在的形状 |
| --- | --- | --- |
| 十六条断言逐项结果 | `./scripts/probe.sh` 在 API 37 模拟器上的输出 | **有了**：2026-09-21 那一遍，15 PASS + 1 MANUAL，退出码 0。原文在上面那节运行记录 |
| 加载后生效差异表 Android 那一列 | 手工造实例 B | 七格全「未填」。要跑 `docs/RUNBOOK.md` 的「七」（实例 B 是 `docs/实例B.patch`），本机跑不了 |
| 每条要求的核实记录 | 本文件 M1–M5 各节 | 已成形，状态记号见开头。还欠七格 |

不另设汇总表——本文件就是那份记录，pro 那边只收结果与平台事实。等着回流 pro 的平台事实攒在
[`docs/待回流-pro.md`](docs/待回流-pro.md)。

## 后续 · 模拟器那一半的日志自动落盘（脚本已写，未在工作机上真跑）

**现在的收法太费人**：RUNBOOK 二到六每一处都要人自己敲 `adb logcat -c` / `am start` / `logcat -d | grep`，
再把输出抄回来。切后台那一格就是这样卡在「原始输出待补」的：看到了，但 tick 的时间戳没留下；而且
`logcat -d` 是事后读环形缓冲，每秒一行 tick 很快就把前面的挤掉。

**目标**：人只管在模拟器上操作，日志从开始到结束自动写进固定目录；做完把那一个目录（或打好的包）交出来，
判断、摘原文进本文件都由 AI 做。

### 形状

`scripts/capture.sh [--install] <场景>`，场景对 RUNBOOK 的各节：

| 场景 | RUNBOOK | 脚本代做的准备动作（都在 adb 这一层，不点页面） |
| --- | --- | --- |
| `env` | 五 | 起应用，等两行 `CRAB-ENV` 出来就结束 |
| `background` | 四·切后台 | 起应用；人点「播放声音」→ Home → 等十秒 → 从最近任务切回 |
| `destroy` | 四·划掉 | 起应用；人在最近任务里划掉再从图标起 |
| `back-at-root` | 四·入口页后退 | 起应用；人在入口页直接按返回键，再从图标回来进第二页后退（后一半是 RUNBOOK 四「紧接上一格」那行）；结束时存一份 `dumpsys activity activities` |
| `multi-instance` | 四·再次启动 | 先只带 `-n` 起，人按 Home 再点图标，存一份 `dumpsys activity activities` 并数新增的 `CRAB-ENV onNewIntent`；再带 `MAIN` + `LAUNCHER` 重复一遍。单实例下两遍都应当只有一条 `Hist` |
| `font-scale` | 六 | `font_scale` 设 1.30 再起应用，**退出时（含 Ctrl-C）一律改回 1.00** |
| `render-gone` | 三 | 先 `adb root`，拒绝就把拒绝原文落盘并结束；拿到了就找渲染进程（按起应用前后多出来的 `sandboxed_process` 找）、`kill -9`，人确认页面恢复后回车再杀第二次 |
| `missing-entry` | 二 | 挪走入口页、绕过 `check.sh` 直接装；**退出时放回并重装**，放回失败要大声报 |
| `free` | 七 | 什么都不做，只录。实例 B（`docs/实例B.patch`）用它，B 的动作在另一个终端里敲 |

每次跑产出一个目录 `runs/<YYYYMMDD-HHMMSS>-<场景>/`，结束时再打一个同名 `.tar.gz`：

| 文件 | 内容 |
| --- | --- |
| `logcat.txt` | `adb logcat -v threadtime -b main,system,crash` **从开跑持续写到结束**，不是事后 `-d`。不按 pid 过滤：划掉应用那一格进程会换 |
| `crab.txt` | 从上面筛出的 `CRAB-` 行，AI 先读这份 |
| `env.txt` | 本仓 HEAD、设备序列号、`ro.build.version.sdk`、WebView 包名与版本（`dumpsys webviewupdate`）、`font_scale`、开始与结束时的 `pidof` / `ps -A \| grep 包名` |
| `steps.txt` | 脚本打给人的那份清单，加上人在终端里随手敲的备注 |
| 其余 | 场景各自的：`shot-*.png`（终端里敲 `shot` 截的）、`activities*.txt`、`root.txt`、`ps-*.txt` |

备注是**可选的**：跑的时候在终端里敲一行（比如「按了 Home」）回车，脚本同时用 `adb shell log -t CRAB-MARK`
把它写进 logcat，这样人做了什么与 tick 断口在同一条时间线上。什么都不敲也能判。

`probe.sh` 一并改：现在 `mktemp` + `trap rm` 那份 logcat 改为留进 `runs/<时间>-probe/`，连同那十六行，
十六行的原文不再靠手抄。已改：`logcat.txt`、`crab.txt`、`env.txt`、`result.txt`，stdout 仍只打那十六行。

### 和已定的规矩怎么对上

- **ADR-0005 不动**：十六行仍是唯一的运行记录形式。`runs/` 是证据原件，不是记录；本文件照样摘原文，
  并注明摘自哪个 `runs/` 目录
- **ADR-0001 不动**：「不代按」说的是页面上的按钮与对话框，那些仍由人按。脚本只代做 adb 层面的准备与收尾
- **`runs/` 不提交**，落地时加进 `.gitignore`：每秒一行 tick，体积大，还带设备信息。要留的是摘进本文件的那几行
- **两处检出**：在工作机上跑，把那个 `.tar.gz` 带回这处放进 `runs/` 就行，不用再贴终端输出

### 落地顺序与怎么算落地

1. `env` + `background`：最便宜，而且正好把切后台那格的「原始输出待补」补上
2. `probe.sh` 落盘
3. `destroy` / `back-at-root` / `font-scale`
4. `render-gone` / `missing-entry`：这两个脚本要改设备状态或挪仓库文件，退出时的还原最容易写漏，放最后

`.sh` 不在任何自动化检查的覆盖范围里（见 [`docs/PITFALLS.md`](docs/PITFALLS.md) 的「假绿的边界」），
`check.sh` 绿说明不了它能用。**每个场景都在工作机上真跑出一个 `runs/` 目录、AI 能从里面判出结论，
才算落地。** 落地之后 RUNBOOK 各节的手敲命令换成对应场景的一条命令。

**现状（2026-10-09）**：九个场景都写了，`probe.sh` 落盘也改了，只在一个假 `adb` 上空跑过流程（2026-09-24：
场景分发、备注转义、`shot`、Ctrl-C / TERM 之后改回 `font_scale`、`missing-entry` 安装失败时放回入口页）。
10-09 跟着单实例改了 `multi-instance` 与 `back-at-root` 的清单，`multi-instance` 多数一样 `onNewIntent` 的行数。
**一个都没在工作机上真跑，按上面的定义都不算落地**，RUNBOOK 的手敲命令先留着。

### 工作机上的跑法（每格一条命令，按这个顺序）

完整的表（每格看完要回答什么）在 [`docs/RUNBOOK.md`](docs/RUNBOOK.md) 的「零」，这里只列顺序：

1. `./scripts/capture.sh --install env`，再 `./scripts/capture.sh background`
2. `./scripts/probe.sh`——**回归**：确认 `launchMode` 与返回键改完之后十六行与 09-21 那一遍逐行相同
3. `./scripts/capture.sh multi-instance`
4. `./scripts/capture.sh destroy` / `back-at-root` / `font-scale`
5. `./scripts/capture.sh render-gone`
6. `./scripts/capture.sh missing-entry`
7. `git apply docs/实例B.patch` → `./scripts/capture.sh --install free`（差异表七格，另一个终端照 RUNBOOK「七」
   敲 `b_start` / `b`）→ `git apply -R docs/实例B.patch` → `./gradlew --quiet :app:installDebug`

跑完把 `runs/*.tar.gz` 带回这处放进 `runs/`，AI 逐个判、摘原文进本文件对应各格，并注明摘自哪个目录。

## 对账：欠着的几笔，照实记

pro 的 `开工.md` 要求每个里程碑收尾把「怎么算过」那几格的结果贴到 pro 的 issue 上，运行记录留本仓。
**M1 与十六行那一笔已经有了**（2026-09-21，在工作机上跑的），贴 issue 还欠着（`gh` 未登录）。
剩下这几笔欠的原因是同一个——**这处检出没有模拟器**：

| 欠什么 | 卡在哪 |
| --- | --- |
| 七格里的六格：切后台 / 最近任务划掉 / 入口页直接后退、再次启动、删入口文件看错误态、渲染进程终止两次、系统字号、差异表；外加改了 `launchMode` 之后十六行的回归 | 要 AVD。这处连 `cmdline-tools` 都没有；要打开这条路得在 SDK Manager 里装 `cmdline-tools` + 拉一个 API 37 的 system-image |
| 结果贴到 pro 的 issue | `gh` 未登录；且工作机的 remote 是 HTTPS、GitHub 在那台上被 reset，推不上去。提交与推送只能从这处走 |
| 平台事实回流 pro | 本次会话的边界仍是「pro 仓一个字不改」（唯一例外是搬完之后删掉 `交接-and.md`，那是 `开工.md` 定的规矩）。攒着的见 [`docs/待回流-pro.md`](docs/待回流-pro.md) |

**那条插队 spike 现在有答案了：`data:` iframe 在 Android 上起得来**（`inject-scope` PASS 就是它挣来的），
所以「换 `sandbox` + `srcdoc`」在 Android 侧不必发生。当初没等它就把 M2–M4 写完，事后看没有付出代价，
但**那不改变当时的判断错在哪**：赌对了不等于该赌。这条已经回流 pro。
