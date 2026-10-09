# 等着回流 pro 的平台事实

**只收「平台事实」**：平台的声明、API 的形状、还成立的技术约束——它们是三端共同判据的输入，所以要回
pro。工具链咬合、环境自检、构建陷阱一律不回流，落 [`PITFALLS.md`](PITFALLS.md)。

**本仓不改 pro 的 `plan/` 文件。** 这份清单是给你看的：哪条进 pro、进哪一份，由你定。进了就把那条从
这里划掉。

判据一句都不复述在这里，只写「本端在 Android 上观察/读到了什么」。

## 待回流

### 3. `WebSettings.textZoom` 与系统字号的关系（还没读到，占位）

pro 的 M3 要求看一次「文字跟不跟随系统字号」。取法写在 [`RUNBOOK.md`](RUNBOOK.md) 的「六」，
**还没跑过**。跑出来无论哪个结果都是一条平台事实：

- 不跟随 → `textZoom = 100` 把系统字号那条路也按住了，pro 那笔「关缩放之后由页面侧提供字号调节」的债
  多一层理由
- 跟随 → 「关缩放」在 Android 上并没有把放大内容的路全堵死，那笔债的范围要重估

这一格读到之前不回流，留个位置免得忘。

### 6. Android 12 起，入口页上后退是把任务挪到后台，容器并不销毁

pro M4 那一格写的是「在入口页直接后退，交宿主关掉容器，不留在空白页」。本端到底时把返回键交回系统默认
行为，而 `sdk/sources/android-36.1/android/app/Activity.java` 里 `onBackPressed` 的 javadoc 原文：

> Starting with platform version S, for activities that are the root activity of the task and also
> declare an IntentFilter with ACTION_MAIN and CATEGORY_LAUNCHER in the manifest, the current activity
> and its task will be moved to the back of the activity stack instead of being finished.

`MainActivity` 正是带 `MAIN` + `LAUNCHER` 的任务根，所以用户看到的是退回桌面、不留空白页，但容器（连同
`WebView` 与页面里的 JS 状态）还活着；再点图标，`singleTask` 之下回来的是同一个容器、停在入口页，走
`onNewIntent`，没有 `onCreate`。

要 pro 看的只有一件：**「关掉容器」包不包括「挪到后台、实例保留」**。包括则本端不用动；不包括则要宿主
自己 `finish()`，那就与平台默认行为反着来，而且和「再次启动回到原容器、不重载」那条一起看才知道该怎么取。
本端不自决，现在按平台默认走。javadoc 是本机读的；设备上的表现排在工作机那一遍里（RUNBOOK「四」
`back-at-root`），**还没看过**。

本端顺带按出口 1 改了一处实现：原先到底时 `remove()` 掉返回键回调，实例活下来之后回调不会再挂上，第二页
上的返回键会直接退到桌面；现在只在分发那一下暂时关掉（见 `MainActivity.backCallback`）。

## 已回流（922ffd3 那一批，四条）

pro `922ffd3`（「收 and 的三条待回流：Safe Browsing 进 M2、文件访问改原生导航观察、宿主单实例进 M4」）收了
原先的第 1、2、4、5 条，编号照旧空着，免得会话记录里的引用对不上：

| 原编号 | 事实 | 进了 pro 的哪一份 |
| --- | --- | --- |
| 1 | 关 Safe Browsing 的现行手段是框架的 `setSafeBrowsingEnabled`，不走 compat、不取 manifest | M2 的要求与「平台事实」，「要试出来的」里那条划掉 |
| 2 | Safe Browsing 在 Android 上没有观察面 | M2 的要求（取值有单测、写入只剩走查），M5「不许糊过去」加一条 |
| 4 | 差异表文件访问那一行按 `fetch` 一个 `file://` 分辨不出档位；`setAllowFileAccess` 管不到 `android_asset` | M3：观察法改成原生侧主帧导航到私有目录里的已知文件（三处要求），javadoc 那句进「平台事实」，iOS 那格记不适用 |
| 5 | 从别的入口进来过一次，再点图标会多开一个容器 | M4：「宿主」要求、单实例落点表、「故意做坏事」第十二行、「要试出来的」最后一行，现象进「平台事实」 |

本端随之做的：第 5 条落成 `launchMode = singleTask` + `onNewIntent`（`HostSingleInstanceTest`）；第 4 条落成
RUNBOOK「七」的文件访问那一格；第 1、2 条代码早已是这个形状，`TASKS.md` 里那格改记「只剩走查」。

## 已回流（`e857625..287c92d` 那一批，四条）

留档，不要再报一遍：

| 事实 | 进了 pro 的哪一份 |
| --- | --- |
| UA 缩减冻住了系统版本与机型段，读不到真实档位 | M3 |
| `data:` URL 的 iframe 在 Android 上起得来（`inject-scope` 的载体不必换 `sandbox` + `srcdoc`） | M3 |
| 两个 file URL setter 已废弃 | M3 |
| 带 Google Play 的镜像一律拒绝 `adb root`，所以「主动触发渲染进程终止」在 Android 上有前提 | M4 |

## 不回流的（写在这里免得下次再议）

- **工具链与环境**：AGP 9 那一串咬合、JBR 路径、`adb` 不在 PATH、两处检出能力不同——落
  [`PITFALLS.md`](PITFALLS.md)。pro 的 README 明写环境不进规划
- **端内自定的命名**：`CRAB-ERR` 的三个场景词、探针页的 `tone.wav` 与 tick。pro 只定了日志的形状，
  这些是本端为了有观察面加的，记在 `TASKS.md` 的「端内自定的部分」
- **`CrabContainer` 里那个 `CRAB-ENV` 诊断 `init` 块**：一次性的，读到值就可以删，不是契约
- **`MainActivity.onNewIntent` 里那行 `CRAB-ENV onNewIntent`**：同上，是「再次启动」那格的诊断
