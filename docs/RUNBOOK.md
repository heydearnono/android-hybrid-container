# RUNBOOK · 人工步骤

`scripts/check.sh` 跑不到的那一半都在这里：pro 的 M4 要求「故意做坏事」各做一遍，加上 M3 留给模拟器的
几项。**这份文档只写怎么做、看什么**，判据在 pro，状态在 `TASKS.md`。

前置：一台 **API 37** 的模拟器（`minSdk = 37`）。

**「一」那六处已经在工作机上做过一遍**（2026-09-21，十六行齐，原文在 `TASKS.md` 的运行记录那节）。
**「五」也做完了**（2026-09-23，两行原文在 `TASKS.md` 的「M3 · 注入走哪条路」）。其余几处做到哪以
`TASKS.md` 为准，这里不记进度——`~/Desktop/github` 这处检出没有 `cmdline-tools`、没有 system-image、
没有 AVD，两处检出的差别见 [`PITFALLS.md`](PITFALLS.md) 的「环境」。别把 `TASKS.md` 里的「已落地」读成
「过了」。

`adb` 取 `$ANDROID_HOME/platform-tools/adb`（默认 `~/Library/Android/sdk/platform-tools/adb`）。
下面写 `adb` 的地方都指这一个。每开一个新终端窗口先注入环境：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

**命令行起应用一律带 `-a android.intent.action.MAIN -c android.intent.category.LAUNCHER`**，形状与桌面
图标发的请求一致。只写 `-n` 的话，之后按 Home 再点桌面图标会多开一个 `MainActivity`，页面重载、tick
从 0 起，切后台那一格就看不出断口了（见 [`PITFALLS.md`](PITFALLS.md) 的「模拟器与 `probe.sh`」）。

## 一、跟着 `scripts/probe.sh` 走的六处

`./scripts/probe.sh` 会装好、清日志、起应用，然后**停下来等你**。它不代按：按完再回车，它才去回读
logcat 并打那固定十六行。

| # | 做什么 | 看什么 |
| --- | --- | --- |
| 1 | 点「跨 origin」（`https://out.crab.invalid/`） | 页面**不动**，仍在入口页；logcat 一行 `CRAB-NAV nav-cross-origin`。跳出系统浏览器就是漏了 |
| 2 | 点「_blank」，再点「window.open」 | 都不开新窗口、都留在入口页；`CRAB-NAV nav-blank` **两行**。只有一行说明其中一条被静默拦掉了（脚本那条的前置是 `setJavaScriptCanOpenWindowsAutomatically`） |
| 3 | 点「tel:」，回来再点「mailto:」 | 跳出拨号盘 / 邮件；各一行 `CRAB-NAV nav-system-scheme`。**跳没跳出去只有人眼看得见**，所以这一格输出恒为 `MANUAL`，不是没测 |
| 4 | 点「未知 scheme」（`crabx://probe`） | 留在入口页、**应用没消失**；一行 `CRAB-NAV nav-unknown-scheme`。`probe.sh` 会顺手查 `pidof`——拦住了但把应用带走了也是没做到 |
| 5 | 点「对话框」一次 | 依次弹三个：alert 关掉、confirm 选「确定」、prompt 里输入 `CRAB`。三行 `CRAB-DLG alert/confirm/prompt`，页面上 `dialog` 变 PASS。**停在「…」说明 `JsResult` 没被回**——那时 `window.alert()` 永不返回，后两个压根不弹 |
| 6 | 点「定位」 | **不弹**系统授权框，页面在几秒内拿到失败回调（`denied:1`）；一行 `CRAB-PERM geolocation`。一直挂着算 FAIL——pro 要的是「给页面一个明确的失败」 |

顺带把 `nav-same-origin` / `nav-back` 也做掉：点「跳到第二页」，在第二页按**系统返回键**回来。页面上没有
回链，唯一的回法就是返回键，所以这两条一起判。

## 二、必须单独跑的一处：删掉入口文件

`ProbeContractAlignmentTest` 要求 `assets/probe/index.html` 在，而 `probe.sh` 装之前会跑
`./scripts/check.sh`——所以这一遍**不能跟着 `probe.sh` 走**，得手动装，跑完把文件放回去。

```bash
# 1. 先把入口页挪走（挪，不是删——放回去要靠它）
mv app/src/main/assets/probe/index.html /tmp/crab-index.html

# 2. 绕过 check.sh 直接装（此时那条对齐单测会红，是预期的）
./gradlew --quiet :app:installDebug

# 3. 起应用看错误界面
adb logcat -c
adb shell am force-stop net.xiaoluzhu.crab
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  -n net.xiaoluzhu.crab/net.xiaoluzhu.crab.MainActivity
adb logcat -d | grep CRAB-ERR

# 4. 放回去，重新装一遍，确认恢复
mv /tmp/crab-index.html app/src/main/assets/probe/index.html
./scripts/check.sh && ./gradlew --quiet :app:installDebug
```

看什么：屏幕上是「页面没能打开」+「重试」按钮（不是白屏、不是 WebView 自带的错误页）；一行
`CRAB-ERR load <码>`（`CrabAssetPathHandler` 兜的是 404，所以码是 404，不是 `-2`）。点「重试」——文件还没
放回去时应当还是错误界面，放回去之后重装再点应当回到探针页。

## 三、渲染进程终止（两次）

这条要的是「第一次换 WebView 悄悄恢复、第二次进错误界面」，所以**同一次运行里要杀两次**。

**顺序是先 `adb root`**：拿不到就别往下走了——带 Google Play 的镜像一律拒绝它，那种镜像上这一格在
Android 上整条不成立（已回流 pro 的 M4），退代码走查并在 `TASKS.md` / M5 里明写未验证。

```bash
adb root                      # 先这一步。Google Play 镜像上拒绝，拒绝了这一格就到此为止

# 渲染进程的名字是 <包名>:sandboxed_process… 或 …:webview_service，取决于内核实现，所以按包名过滤
adb shell ps -A | grep net.xiaoluzhu.crab

# 上面那行里不是主进程的那个 pid 就是渲染进程
adb shell kill -9 <renderer-pid>
```

看什么：

- 第一次：一行 `CRAB-ERR render-gone 1`，页面**自己重新加载**回探针页（换了一个新 WebView）
- 等新页面加载完，再查一次 pid、再杀一次：又一行 `CRAB-ERR render-gone`，这次进「页面没能打开」
- 点「重试」：回探针页，而且额度给满——再杀一次应当又能悄悄恢复一回

`adb root` 拿不到 root 时（Google Play 镜像）这一处做不了，`TASKS.md` 里照实写「造不出来」，不要拿
「没崩过」当通过。

## 四、切后台、销毁、在入口页直接后退

| 做什么 | 看什么 |
| --- | --- |
| 探针页上点「播放声音」，按 Home 键等十秒再回来 | 切出去后声音**停**、页面上的 `tick` 数**不涨**；回来后继续。`adb logcat -d \| grep 'CRAB-ENV tick'` 的时间戳里应当有一段十秒左右的断口。声音一直响或 tick 一直涨 = `onPause()` / `pauseTimers()` 没生效 |
| 在最近任务里划掉应用，再重新起 | 不崩、logcat 里没有 WebView 相关的异常栈。容器销毁时是**先从视图树摘除再 `destroy()`**，顺序反了会崩在渲染层 |
| 在入口页（没去过第二页时）直接按系统返回键 | 应用退到桌面 / 结束，**不是**卡在页面上什么都不发生。返回键到底之后交回宿主的默认行为 |

切后台那一格与十六条断言无关，是 pro 单独要求人工看一次的。声音（`tone.wav`）与每秒 tick 是端内给它
加的观察面——不给点声音、不给个在走的数，「停没停」根本看不出来。

## 五、开机读一行：`DOCUMENT_START_SCRIPT` 与整串 UA

**最便宜的一格，装上起一次就有。** 两条都在 `CRAB-ENV` 这个前缀下，一次 grep 全拿到：

```bash
adb shell am force-stop net.xiaoluzhu.crab
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  -n net.xiaoluzhu.crab/net.xiaoluzhu.crab.MainActivity
adb logcat -d | grep CRAB-ENV
```

两行各自要什么：

| 哪一行 | 长什么样 | 拿它干什么 |
| --- | --- | --- |
| `CrabContainer` 的 `init` 打的 | `CRAB-ENV DOCUMENT_START_SCRIPT=<true\|false> webview=<包名>/<完整版本号>` | 填 `TASKS.md` 的「M3 · 注入走哪条路」。`inject-order` 绿**不是**这一格的答案：两条路都要求 `injected == 1` |
| 探针页打的 | `CRAB-ENV UA: … · typeof localStorage: …`（还有每秒一行的 `CRAB-ENV tick …`） | 整串 UA 抄下来：尾巴是不是 `Crab/0.1.0`、系统 UA 有没有被替换，以及差异表的 UA 那一行 |

`DOCUMENT_START_SCRIPT` 那一行**读到值不等于这一格做完了**：

- `true` → 这台机器一直走原生注入，**兜底那条一次没跑过**，得另找一个旧内核的镜像，或按未验证记
- `false` → 反过来，原生那条没有观察面；且要确认 `inject-order` 的 PASS 是兜底挣来的

版本号必须连内核包名一起抄（`com.google.android.webview` 与 `com.android.webview` 是两种镜像），换个
镜像这个结果就不一定还算。查内核的另一条路是 `adb shell cmd webviewupdate query`——**不是**
`dumpsys webview`，没有那个服务。

那个 `init` 块是一次性诊断，前缀刻意写成字面量、不从 `CrabLog` / `ProbeContract` 派生。**读到结果之后
可以删掉它**，删了不牵动任何契约与单测。

## 六、文字跟不跟随系统字号

`WebSettings.textZoom = 100` 是取值表里的一项，要看的是它有没有把系统字号那条路一并按住。

```bash
# 也可以走 设置 → 显示 → 字体大小，拖到最大
adb shell settings put system font_scale 1.30
adb shell am force-stop net.xiaoluzhu.crab
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  -n net.xiaoluzhu.crab/net.xiaoluzhu.crab.MainActivity
# 看完改回去
adb shell settings put system font_scale 1.00
```

看什么：探针页里的文字**大小不变**（原生错误界面上的文字会跟着变，那是 Compose 的 `sp`，不是 WebView
里的）。要是页面文字跟着变大了，说明 `textZoom` 没挡住系统字号——那不是 bug，是一条要记下来的平台事实，
并且会让 pro 那笔「关缩放之后由页面侧提供字号调节」的债多一层理由。

**顺手把双指缩放也试一遍**（差异表的「缩放」那一行要它）：两指在页面上撑开，页面不应放大。

## 七、加载后生效差异表 · 手工造实例 B

**最重的一格，放最后。** 七行要的观察面比十六条断言多，现在七格全「未填」。

pro 的做法是两个实例：A 加载前就设成目标值（当基线），B 加载前设成**相反**值、加载完再改回目标值，看
改动是立即生效 / 重载后生效 / 完全不生效 / 还是改动本身触发了一次重载。**A 就是现在的容器**
（`applyCrabSpec` 在 `createWebView()` 里一次写完），所以要临时造的只有 B。

B 怎么造（**跑完删掉，一行都不留**）：

1. 在 `MainActivity` 里加第二个 `WebView`（或加一个按钮换掉现有那个），加载前逐项设成相反值：
   `javaScriptEnabled = false`、`domStorageEnabled = false`、`mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW`、
   `allowFileAccess = true`、`mediaPlaybackRequiresUserGesture = false`、`setSupportZoom(true)`、
   UA 不装饰
2. 加载入口页，等它跑完
3. 逐项改成目标值，每改一项看一次，四档里选一档记进 `TASKS.md` 那张表

三项探针页里根本没有的观察面也得临时加（**一样跑完删掉**）：

| 要看 | 临时加什么 |
| --- | --- |
| 混合内容 | 一个 `http://` 子资源。模拟器上宿主机是 **`10.0.2.2`**，不是 `127.0.0.1`；随手起 `python3 -m http.server` 即可。**manifest 要临时加两样，跑完删掉**：`<uses-permission android:name="android.permission.INTERNET" />`，以及 `<application>` 上的 `android:usesCleartextTraffic="true"`。容器本来一条网络请求都不发，两样都没有；缺前一样请求发不出去，缺后一样 WebView 照样拦明文（`NetworkSecurityPolicy.isCleartextTrafficPermitted` 的 javadoc：WebView 对 targetSdk 26 起的应用遵守这个开关）。不加的话 `mixedContentMode` 设成什么都加载不到，会被误记成「完全不生效」 |
| 文件访问 | **按 pro 的观察法分辨不出，待 pro 议**。`setAllowFileAccess` 管不到 `file:///android_asset`，而页面在 `https://` 上、`fetch` 本来就拿不到 `file://`，所以开与关都是「读不到」。仍照原样 `fetch('file:///android_asset/probe/probe.png')` 一次、把报错原文抄下来：那能坐实后一半，它现在还是按 Chromium 的行为推的。细节见 [`待回流-pro.md`](待回流-pro.md) 第 4 条 |
| 有声媒体自动播放 | 一个有声的 `<video autoplay>`，看它自己播不播 |

这一格与其余六格的区别：**其余六格是「跑一遍就有」，这一格要先改代码。** 所以它天然最后做，而且做完
必须确认工作区干净（`git status` 里没有 `MainActivity`、`:core:webview`、`AndroidManifest.xml` 与探针页的
残留），否则临时代码会跟着提交进去。B 要挂容器那个私有的 `assetLoader` 才加载得了承载 origin，所以临时
代码多半会落到 `:core:webview`，不只是 `MainActivity`。

## 八、跑完之后

`./scripts/probe.sh` 的退出码：有任何一行 FAIL 就非零。把那十六行连同当次的判断贴进 `TASKS.md`
对应格子（把「待模拟器核实」改成结论），**不要只改状态不留输出**。

十六条之外的观察（「二」到「七」）不进那十六行，结论也写进 `TASKS.md`，写清是哪一格、哪一遍看到的。
**造不出来的照实写「造不出来」**：没触发过的恢复路径与写错了的恢复路径在日志上长得一模一样，
「一直没崩过」不是通过。

平台声明与还成立的技术约束攒进 [`待回流-pro.md`](待回流-pro.md) 等着回流 pro；工具链与环境的坑落
[`PITFALLS.md`](PITFALLS.md)，**不回流**。
