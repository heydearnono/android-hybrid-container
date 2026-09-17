# RUNBOOK · 人工步骤

pro 的 M4 要求「故意做坏事」十一处各做一遍。**这份文档只写怎么做、看什么**，判据在 pro，状态在
`TASKS.md`。

前置：一台 **API 37** 的模拟器（`minSdk = 37`）。本机没有 `cmdline-tools`、没有 system-image、没有
AVD，所以这十一处**一处都还没做过**，别把 `TASKS.md` 里的「已落地」读成「过了」。

`adb` 取 `$ANDROID_HOME/platform-tools/adb`（默认 `~/Library/Android/sdk/platform-tools/adb`）。
下面写 `adb` 的地方都指这一个。

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
adb shell am start -n net.xiaoluzhu.crab/net.xiaoluzhu.crab.MainActivity
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

```bash
# 渲染进程的名字是 <包名>:sandboxed_process… 或 …:webview_service，取决于内核实现，所以按包名过滤
adb shell ps -A | grep net.xiaoluzhu.crab

# 上面那行里不是主进程的那个 pid 就是渲染进程
adb root                      # Google Play 镜像上不可用，那种镜像上这一格只能空着
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

## 五、跑完之后

`./scripts/probe.sh` 的退出码：有任何一行 FAIL 就非零。把那十六行连同当次的判断贴进 `TASKS.md`
对应格子（把「待模拟器核实」改成结论），**不要只改状态不留输出**。

十六条之外的观察（切后台、销毁、渲染进程）不进那十六行，结论也写进 `TASKS.md`，写清是哪一格。
