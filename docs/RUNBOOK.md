# RUNBOOK · 人工步骤

`scripts/check.sh` 跑不到的那一半都在这里：pro 的 M4 要求「故意做坏事」各做一遍，加上 M3 留给模拟器的
几项。**这份文档只写怎么做、看什么**，判据在 pro，状态在 `TASKS.md`。

前置：一台 **API 37** 的模拟器（`minSdk = 37`）。

**「一」那六处已经在工作机上做过一遍**（2026-09-21，十六行齐，原文在 `TASKS.md` 的运行记录那节）。
**「五」也做完了**（2026-09-23，两行原文在 `TASKS.md` 的「M3 · 注入走哪条路」）。之后宿主改了
`launchMode` 与返回键，所以「一」要回归一遍，顺序在「零」。其余几处做到哪以
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
图标发的请求一致。宿主改成 `singleTask` 之后，只写 `-n` 照理不会再多开一个 `MainActivity`（原先会，见
[`PITFALLS.md`](PITFALLS.md) 的「模拟器与 `probe.sh`」），但「照理」要等「四 · 再次启动」那格跑出来才算数，
所以别处照旧带上。**只有那一格故意只写 `-n`。**

**二到七各有一条 `./scripts/capture.sh <场景>`**：logcat 从开跑持续落进 `runs/`，跑完把
`runs/<时间>-<场景>.tar.gz` 交出来，不用再贴终端输出。场景名见 `./scripts/capture.sh --help`。它**还没在
工作机上真跑过**（`TASKS.md` 的「后续 · 模拟器那一半的日志自动落盘」），所以下面各节的手敲命令先留着，
两条路任选。

## 零、工作机上的跑法（按这个顺序，每格一条命令）

前提：工作机的检出已经 `git pull` 到本处推上去的那个提交（工作机推不了远端，所以拿 bundle 拉，**只 pull、
不在那边推**；做法见 [`PITFALLS.md`](PITFALLS.md) 的「环境」）。开跑之前比一次 `git rev-parse HEAD`，与
本处的 `main` 不一致就先别跑——后加的 `launchMode` 与 `onNewIntent` 那行日志在旧 APK 上打不出来。

| # | 命令 | 对应哪一节 | 看完要回答什么 |
| --- | --- | --- | --- |
| 1 | `./scripts/capture.sh --install env` | 五 | `DOCUMENT_START_SCRIPT` 与整串 UA 两行还在、与 09-23 一致；`--install` 顺带把带 `singleTask` 的新包装上 |
| 2 | `./scripts/capture.sh background` | 四 · 切后台 | 声音停没停、tick 时间戳断口有多长（补上 09-24 那格的原始输出） |
| 3 | `./scripts/probe.sh` | 一 | **回归**：改了 `launchMode` 与返回键之后，十六行与 09-21 那一遍逐行相同、退出码 0 |
| 4 | `./scripts/capture.sh multi-instance` | 四 · 再次启动 | 两遍都只有一条 `Hist`、tick 不从 1 起、各多一行 `CRAB-ENV onNewIntent` |
| 5 | `./scripts/capture.sh destroy` | 四 · 划掉 | 不崩、没有 WebView 相关的异常栈 |
| 6 | `./scripts/capture.sh back-at-root` | 四 · 入口页后退 + 紧接那一格 | 入口页后退退到桌面；点图标回来是原页面；第二页上后退回到入口页 |
| 7 | `./scripts/capture.sh font-scale` | 六 | 字号 1.30 下页面文字变没变、双指撑开放没放大；结束后 `font_scale` 回到 1.00 |
| 8 | `./scripts/capture.sh render-gone` | 三 | `adb root` 拿不拿得到；拿到了就是两次杀进程各自的结果 |
| 9 | `./scripts/capture.sh missing-entry` | 二 | 错误界面上屏、一行 `CRAB-ERR load 404`；结束后入口页已放回、重装后探针页回来 |
| 10 | `git apply docs/实例B.patch` → `./scripts/capture.sh --install free`（另开终端照「七」操作）→ `git apply -R docs/实例B.patch` → `./gradlew --quiet :app:installDebug` | 七 | 差异表七格各落哪一档；跑完 `git status` 干净、设备上装回的是不带 B 的包 |

顺序的理由：1、2 最便宜，先确认新包装上了、日志通道还在；3 是本轮改动的回归，红了后面都不用跑；8、9
要改设备状态或挪仓库文件，还原最容易漏，放后面；10 要先打临时补丁，天然最后。

做完把 `runs/*.tar.gz` 全部带回本处放进 `runs/`，判读与摘原文进 `TASKS.md` 由 AI 做。

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

脚本版：`./scripts/capture.sh missing-entry`（挪走、绕过 `check.sh` 装、退出时放回并重装）。

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

脚本版：`./scripts/capture.sh render-gone`（先 `adb root`，拒绝就落盘原文结束）。

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

`grep` 不到的话：渲染进程名也可能以 WebView 的包名开头（`…webview:sandboxed_process…`），那就在起应用
前后各 `adb shell ps -A | grep sandboxed_process` 一次，多出来的那个是本应用的。`capture.sh` 就是这么找的。

看什么：

- 第一次：一行 `CRAB-ERR render-gone 1`，页面**自己重新加载**回探针页（换了一个新 WebView）
- 等新页面加载完，再查一次 pid、再杀一次：又一行 `CRAB-ERR render-gone`，这次进「页面没能打开」
- 点「重试」：回探针页，而且额度给满——再杀一次应当又能悄悄恢复一回

`adb root` 拿不到 root 时（Google Play 镜像）这一处做不了，`TASKS.md` 里照实写「造不出来」，不要拿
「没崩过」当通过。

## 四、切后台、销毁、在入口页直接后退、再次启动

脚本版：`background` / `destroy` / `back-at-root`（含「紧接上一格」那行）/ `multi-instance`，一格一条。

| 做什么 | 看什么 |
| --- | --- |
| 探针页上点「播放声音」，按 Home 键等十秒再回来 | 切出去后声音**停**、页面上的 `tick` 数**不涨**；回来后继续。`adb logcat -d \| grep 'CRAB-ENV tick'` 的时间戳里应当有一段十秒左右的断口。声音一直响或 tick 一直涨 = `onPause()` / `pauseTimers()` 没生效 |
| 在最近任务里划掉应用，再重新起 | 不崩、logcat 里没有 WebView 相关的异常栈。容器销毁时是**先从视图树摘除再 `destroy()`**，顺序反了会崩在渲染层 |
| 在入口页（没去过第二页时）直接按系统返回键 | 应用退到桌面，**不是**卡在页面上什么都不发生。Android 12 起这一下是把任务挪到后台、`MainActivity` 不销毁（`Activity.onBackPressed` 的 javadoc），所以容器其实还活着 |
| 紧接上一格：点桌面图标回来，点「跳到第二页」，在第二页按系统返回键 | 回来的是**原来那个页面**（tick 接着走、logcat 多一行 `CRAB-ENV onNewIntent`），第二页上后退回到入口页，**不是**直接退到桌面 |

**`singleTask` 改了最后一格的什么。** 原先的写法在入口页后退时 `remove()` 掉了 `backCallback`，而上一格
之后实例还活着、回来不再走 `onCreate`，回调也就不会再挂上——第二页上的返回键直接退到桌面。`standard`
下踩不踩取决于怎么回来（按 09-24 多开那次的现象推：只写 `-n` 起过的话点图标会新建实例、重新 `onCreate`，
碰巧躲过去；带 `MAIN` + `LAUNCHER` 起的则是原任务提到前台，照样踩）；`singleTask` 之下回来的**一定**是原
实例，所以这一格从「可能踩」变成「必踩」。本端已按出口 1 改掉：到底时只把回调暂时关掉、分发完马上打开，不再
`remove()`。**预期因此是「回到入口页」**；还是退到桌面就是这处修法没生效，照实记。

切后台那一格与十六条断言无关，是 pro 单独要求人工看一次的。声音（`tone.wav`）与每秒 tick 是端内给它
加的观察面——不给点声音、不给个在走的数，「停没停」根本看不出来。

### 再次启动：换一个入口起应用，再点图标

pro M4「故意做坏事」第十二行：从桌面图标之外的入口启动一次，回桌面，再点桌面图标，要回到原来那个容器、
页面不重载。Android 的落点是 `launchMode = singleTask`，再次启动走 `onNewIntent`（pro M4 的落点表）。
「别的入口」用 `am start -n`（pro「要试出来的」最后一行），**这是全篇唯一故意只写 `-n` 的地方**；第二遍
换成带 `MAIN` + `LAUNCHER` 的请求当对照：

```bash
# 第一遍：只写 -n
adb logcat -c
adb shell am force-stop net.xiaoluzhu.crab
adb shell am start -n net.xiaoluzhu.crab/net.xiaoluzhu.crab.MainActivity
# 按 Home，再点桌面图标，然后：
adb shell dumpsys activity activities | grep -E 'Hist #.*net.xiaoluzhu.crab'
adb logcat -d | grep 'CRAB-ENV onNewIntent'

# 第二遍：换成带 MAIN + LAUNCHER 的那条 am start，其余相同
```

看什么：**两遍都只有一条** `MainActivity` 的 `Hist`；点图标之后多一行 `CRAB-ENV onNewIntent Intent { … }`
（带 `FLAG_ACTIVITY_BROUGHT_TO_FRONT` 的话那段 flags 里能看见），页面上的 tick 接着走、不从 1 起。
改之前（`standard`）实测第一遍是两条 `Hist`、tick 从 0 起，所以第一遍是这格真正要看的那一遍。
`dumpsys` 的格式随版本变，grep 不到就去掉过滤、把 `net.xiaoluzhu.crab` 附近那段原样贴回来。

`CRAB-ENV onNewIntent` 那一行是诊断不是契约，前缀与 `CrabContainer` 那行 `CRAB-ENV` 同理写成字面量。新
请求带来的内容怎么处理 pro 留给 FE 接入时定，所以 `onNewIntent` 里除了这一行什么都不做。

## 五、开机读一行：`DOCUMENT_START_SCRIPT` 与整串 UA

**最便宜的一格，装上起一次就有。** 两条都在 `CRAB-ENV` 这个前缀下，一次 grep 全拿到（脚本版：`./scripts/capture.sh env`）：

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
脚本版：`./scripts/capture.sh font-scale`（退出时一律改回 1.00，含 Ctrl-C）。

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

### B 是一份现成的临时补丁，跑完反向打掉

B 写好了，在 [`实例B.patch`](实例B.patch)（本机编译、格式、lint 过过，**没在设备上跑过**）。它动三处，
一处都不进提交：

| 文件 | 加了什么 |
| --- | --- |
| `app/src/main/kotlin/…/InstanceB.kt`（新文件） | 冷启动带 `--es crab.b <项>` 时，那一项在首次加载**之前**设成相反值，其余照取值表；之后的动作经 `--es crab.b.cmd <动作>` 走 `onNewIntent` 进来。每一步打一行 `CRAB-ENV B …`，连同当时 `WebSettings` 的读回值 |
| `MainActivity.kt` | 两行：`loadEntry()` 之前调 `InstanceB.beforeFirstLoad`，`onNewIntent` 里调 `InstanceB.onCommand` |
| `AndroidManifest.xml` | `INTERNET` 权限与 `usesCleartextTraffic="true"`，混合内容那一项要真发一次 http 请求 |

```bash
git apply docs/实例B.patch
./scripts/capture.sh --install free        # 这个终端只录；装的是带 B 的包

# 另开一个终端，先注入环境（见文首），再定义两个函数：
b_start() {  # 冷启动；带项名就是实例 B（该项取相反值），不带就是实例 A
  adb shell am force-stop net.xiaoluzhu.crab
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n net.xiaoluzhu.crab/.MainActivity ${1:+--es crab.b "$1"}
}
b() { adb shell am start -n net.xiaoluzhu.crab/.MainActivity --es crab.b.cmd "$1"; }

# 跑完：
git apply -R docs/实例B.patch && git status --short   # 应当是空的
./gradlew --quiet :app:installDebug                    # 设备上装回不带 B 的包
```

宿主是 `singleTask`，所以 `b <动作>` 不会多开实例，落到的就是正在跑的那个 B。动作有五个：

| 动作 | 做什么 |
| --- | --- |
| `probe` | 跑这一项的观察脚本（`evaluateJavascript`，不带用户手势） |
| `target` | 把这一项改成目标值，不重载 |
| `reload` | 重载当前页 |
| `file` | 原生侧把主帧 `loadUrl` 到 `filesDir/crab-b.html` |
| `entry` | 回探针页（走「重试」那条路：打不开的话容器在错误态，只 `loadUrl` 不会把界面切回来） |

### 每一项的跑法（对上 pro 的三步）

除文件访问外六项都是同一个序列：`b_start <项>` → 等页面停稳 → `b probe`（相反值下的表现，顺带确认观察面本身
受这个开关管）→ `b target` → `b probe`（变了记「立即生效」）→ 没变就 `b reload` → `b probe`（变了记「重载后
生效」，还没变记「完全不生效」）。`target` 刚一敲页面就自己重载了，记「改动本身触发重载」。

| 项 | `<项>` | `probe` 看什么 |
| --- | --- | --- |
| JavaScript | `js` | `#env` 那一行变成 `B js ran`，logcat 一行 `CRAB-ENV B js ran`。**相反值下第一次 `probe` 若也打出来了**，说明 `evaluateJavascript` 不受这个开关管，这一格的观察面不成立，照实记、不硬填 |
| DOM storage | `storage` | `CRAB-ENV B storage ok` 还是 `storage threw …` |
| 混合内容 | `mixed` | `CRAB-ENV B mixed loaded` 还是 `mixed blocked`。先在工作机上起 `python3 -m http.server 8000 --directory app/src/main/assets/probe`，模拟器上宿主机是 **`10.0.2.2`**。manifest 那两样补丁里已经加了：缺 `INTERNET` 请求发不出去，缺 `usesCleartextTraffic` WebView 照样拦明文（`NetworkSecurityPolicy.isCleartextTrafficPermitted` 的 javadoc），设成什么都会被误记成「完全不生效」 |
| 有声媒体自动播放 | `autoplay` | 新建一个有声的 `<video autoplay>`（`tone.wav`）：`CRAB-ENV B autoplay playing` 出没出来、两秒后 `paused=` 是什么，耳朵也听一下 |
| 缩放 | `zoom` | 相反值是可缩放 + `textZoom = 200`：文字大小人眼看，两指撑开看放不放大，`CRAB-ENV B zoom scale=` 是 `visualViewport.scale` |
| UA | `ua` | `CRAB-ENV B ua …` 的结尾有没有 `Crab/0.1.0`。相反值是系统 UA 原样 |

`CRAB-ENV B start` / `after target` 两行带着 `WebSettings` 的读回值：那是「写进去了」的证据，不是「生效了」的
证据，判档位只看 `probe` 那几行与屏幕。

### 文件访问那一行：原生侧导航，不在页面里看

pro 的 M3 改了这一行的观察法（922ffd3）：**原生侧把主帧导航到应用私有目录里一个已知的 `file://` 文件，
看打不打得开**。原先「页面里 `fetch` 一个 `file://`」在 Android 上开关两档都读不到，那条不再跑。补丁照
pro 那三处要求落：

- **已知文件放在 `filesDir`。** `InstanceB.beforeFirstLoad` 每次启动都把 `crab-b.html`（标题与正文都带
  `CRAB-B-FILE`，打开时自己打一行 `CRAB-ENV B file opened <URL>`）写进 `filesDir`。不放 `android_asset`：
  `setAllowFileAccess` 管不到那里，放那里两档都打得开
- **这次导航绕开闸门。** `file` 动作走原生 `loadUrl`，而 `shouldOverrideUrlLoading` 对应用自己 `loadUrl` 发起
  的导航不回调（`WebViewClient` 的 javadoc 原文「this is not called for navigations which the app initiated
  with `loadUrl()`」）。闸门没被问到，也就拦不了；证据是这一跳前后 `crab.txt` 里**没有** `CRAB-NAV` 行
- **每次观察都是一次新的导航。** 看完 `b entry` 回探针页，再走下一步

序列：

1. **A 的基线**：`b_start`（不带项名）→ `b file`。目标值是关，应当**打不开**：屏幕上是「页面没能打开」、
   一行 `CRAB-ERR load <码>`，码照抄。→ `b entry`
2. **B**：`b_start file`（开着起）→ `b file`：应当打开了，屏幕上 `CRAB-B-FILE 打开了`、一行
   `CRAB-ENV B file opened`。→ `b entry`
3. `b target`（改成关，不重载）→ `b file`。打不开了记「立即生效」；还打得开就 `b entry` → `b reload` →
   `b file`，打不开了记「重载后生效」，还打得开记「完全不生效」

只翻 `allowFileAccess` 一项：另外三个文件与内容开关在取值表里同样是关，但主帧导航到 `file://` 只受这一项管。

### 跑完

工作区必须干净：`git apply -R` 之后 `git status --short` 是空的，否则临时代码会跟着提交进去；设备上要重装
一次不带 B 的包，不然下一次 `probe.sh --no-install` 跑的是 B。

## 八、跑完之后

`./scripts/probe.sh` 的退出码：有任何一行 FAIL 就非零。把那十六行连同当次的判断贴进 `TASKS.md`
对应格子（把「待模拟器核实」改成结论），**不要只改状态不留输出**。

十六条之外的观察（「二」到「七」）不进那十六行，结论也写进 `TASKS.md`，写清是哪一格、哪一遍看到的。
**造不出来的照实写「造不出来」**：没触发过的恢复路径与写错了的恢复路径在日志上长得一模一样，
「一直没崩过」不是通过。

走手敲那条路时每格贴回什么（**原样贴，别只贴结论**；每格开始前先 `adb logcat -c`）。走 `capture.sh`
的话交那个 `.tar.gz` 就行，这些都在里面：

| 哪一格 | 贴回 |
| --- | --- |
| 四 · 切后台 | `adb logcat -d \| grep 'CRAB-ENV tick'` 全部，外加声音停没停一句 |
| 四 · 划掉 / 入口页后退 / 后退之后再进第二页 | 每格一句看到了什么；划掉那格再贴 `adb logcat -d \| grep -iE 'FATAL\|AndroidRuntime\|chromium'`，空的也照贴 |
| 四 · 再次启动 | 两遍各一段 `dumpsys` 的 grep 输出，加 `adb logcat -d \| grep 'CRAB-ENV onNewIntent'` |
| 六 | 字号 1.30 下探针页文字变没变、双指撑开放没放大，各一句；截图更好。看完确认 `font_scale` 已改回 1.00 |
| 三 | `adb root` 那一行的原文；拿到 root 的话再加 `ps -A \| grep` 与每次杀完的 `adb logcat -d \| grep CRAB-ERR` |
| 二 | `adb logcat -d \| grep CRAB-ERR`，外加屏幕上看到的是什么、点「重试」之后是什么 |
| 七 | 七项各一句（立即生效 / 重载后生效 / 完全不生效 / 改动触发重载），`adb logcat -d \| grep -E 'CRAB-ENV B\|CRAB-ERR\|CRAB-NAV'`，跑完之后的 `git status` |

平台声明与还成立的技术约束攒进 [`待回流-pro.md`](待回流-pro.md) 等着回流 pro；工具链与环境的坑落
[`PITFALLS.md`](PITFALLS.md)，**不回流**。
