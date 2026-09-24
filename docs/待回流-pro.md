# 等着回流 pro 的平台事实

**只收「平台事实」**：平台的声明、API 的形状、还成立的技术约束——它们是三端共同判据的输入，所以要回
pro。工具链咬合、环境自检、构建陷阱一律不回流，落 [`PITFALLS.md`](PITFALLS.md)。

**本仓不改 pro 的 `plan/` 文件。** 这份清单是给你看的：哪条进 pro、进哪一份，由你定。进了就把那条从
这里划掉。

判据一句都不复述在这里，只写「本端在 Android 上观察/读到了什么」。

## 待回流

### 1. 关 Safe Browsing 的现行手段是框架那个 setter，不是 compat、也不是 manifest

pro 的 M2 把「关 Safe Browsing 的正确手段」列作待核。三条都是在本机读 SDK 与 aar 读出来的，**不需要
模拟器**：

- `WebSettings.setSafeBrowsingEnabled(boolean)` 在 `platforms/android-37.0/android-stubs-src.jar` 里
  **没有 `@Deprecated`**。同一个文件里 `setPluginState` / `setLightTouchEnabled` / `getForceDark` 都带着
  这个注解，所以「没带」是有意义的信号，不是 stub 把注解洗掉了
- `androidx.webkit.WebSettingsCompat.setSafeBrowsingEnabled`（1.17.0）也在、也没弃用，但 javap 读它的
  字节码：先问 `ApiFeature$O.isSupportedByFramework()`，成立就直接转给框架那个 setter。**`minSdk = 37`
  下这个分支恒真**，走 compat 只是多一次判断
- manifest 的 `android.webkit.WebView.EnableSafeBrowsing` meta-data 是**应用级**开关，且
  `android.jar` 里一次都不出现（由 WebView provider 读，不在 SDK stub 里），本机核不了。形状也与
  「显式设置 + 取值进单测」不同：它没有可进单测的取值

本端的处置：用框架的 setter，不引 compat，不取 manifest 那一路。

### 2. Safe Browsing 这一项在 Android 上没有观察面

要看见它得有一次被拦下来，而承载 origin 落在 `.invalid` 下、容器一条真实网络请求都不发，拦不到东西。
所以这一项与 SSL 那条同类：取值有单测，**写入只剩走查**。

回流的意思是让 pro 的 M2/M5 能照实记「该端该条未验证」，而不是留在「待核」。

### 3. `WebSettings.textZoom` 与系统字号的关系（还没读到，占位）

pro 的 M3 要求看一次「文字跟不跟随系统字号」。取法写在 [`RUNBOOK.md`](RUNBOOK.md) 的「六」，
**还没跑过**。跑出来无论哪个结果都是一条平台事实：

- 不跟随 → `textZoom = 100` 把系统字号那条路也按住了，pro 那笔「关缩放之后由页面侧提供字号调节」的债
  多一层理由
- 跟随 → 「关缩放」在 Android 上并没有把放大内容的路全堵死，那笔债的范围要重估

这一格读到之前不回流，留个位置免得忘。

### 4. 差异表「文件访问」那一行，按 pro 定的观察法在 Android 上分辨不出档位

pro 的 M5 差异表对这一行定的观察法是「`fetch` 一个 `file://` URL 能否读到」。本端照它写的临时观察面是
`fetch('file:///android_asset/probe/probe.png')`，两处都挡着它：

- **`setAllowFileAccess` 管不到 `android_asset`。** `sdk/sources/android-36.1` 里的 javadoc 原文：
  「this enables or disables file system access only. Assets and resources are still accessible using
  file:///android_asset and file:///android_res」。本机读到的，不需要模拟器
- **页面在 `https://` 承载 origin 上，`fetch` 本来就拿不到 `file://`。** Chromium 的 `fetch` 不认
  `file:` scheme。这一半**还没在模拟器上看过**，是按 Chromium 的行为推的

两样叠起来，B 实例里这一项开与关都是「读不到」，四档里哪一档都填不进去。换成别的观察法（比如原生侧直接
`loadUrl` 一个 `file:///data/…` 路径）改的是三端共同的观察法，按 `CLAUDE.md` 的第三条纪律**不在本仓
自决**。在 pro 议定之前，`TASKS.md` 差异表的这一格照实写「按 pro 的观察法分辨不出，待 pro 议」。

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
