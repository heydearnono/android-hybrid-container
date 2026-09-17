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

<!--TASKS-BODY-->
