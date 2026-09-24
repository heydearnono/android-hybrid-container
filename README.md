# Crab · Android 容器

三端 WebView 容器的 Android 一端。规划在上游 `pro`（`Prospect` 仓）：容器要做什么、怎么算过、
取值取哪个，一律以那边的 `README.md` + `plan/` 五份文件为准，本仓不复述判据。

**它是一个把页面装起来跑的原生壳子**，范围只有五件事：本地承载、六项配置、页面开口前的注入、UA、
导航与降级。不做 JSBridge、不做文件上传下载、不做灰度/埋点/预热/离线包、不打包上架。

第一次读这个仓，先看 [`docs/导读.md`](docs/导读.md)：名词表、模块地图、一次启动经过哪些文件、建议的
阅读顺序。

## 怎么验

两条命令，各管一半：

```bash
./scripts/check.sh          # 能在本机跑完的：格式、APK 产出、单测、lint
./scripts/check.sh --fix    # 先 spotlessApply 再跑上面这串
./scripts/probe.sh          # 探针页十六条断言，需要模拟器；人工那一遍要先跑完
```

`check.sh` 管判定逻辑，`probe.sh` 管「在真容器里到底成不成立」。两条都不能替代另一条：写进
`WebSettings` 的那几行 JVM 单测断不了，而路径归一化与导航闸门不该等到模拟器上才发现写错。

`probe.sh` **不代按**：八个按钮、三个对话框、系统 scheme 那一跳都要人手做完再回车让它回读 logcat。
人工步骤（含「删掉入口文件」「杀渲染进程两次」这些必须单独跑的）在 [`docs/RUNBOOK.md`](docs/RUNBOOK.md)。

当前进度与每一条要求的核实状态在 [`TASKS.md`](TASKS.md)。踩过的坑在
[`docs/PITFALLS.md`](docs/PITFALLS.md)，架构决策在 [`docs/adr/`](docs/adr/)。

## 模块

按「能否在 JVM 上测」切，不按惯例切：

| 模块 | 装什么 |
| --- | --- |
| `:core:container` | 纯 JVM，**全部判定逻辑**：承载 origin 常量、路径归一化、导航闸门、日志格式、UA 拼接、六项配置取值、注入脚本、重载计数、错误态状态机 |
| `:core:webview` | 只写直线代码：`WebSettings` 写入、`WebViewAssetLoader`、各回调转发。**刻意不带单测**——`android.webkit` 在单测里是 stub，写出来的绿是假的 |
| `:app` | Compose 壳、返回键与生命周期、错误态 UI、探针页素材 |

## 取值

一律取 pro 的取值表，端内不另定。承载 origin 那个字符串只允许出现在两处：定义处
`core/container/.../HostingOrigin.kt`，以及探针页的 `app/src/main/assets/probe/probe.js`（页面要拿它当
期望值比对）。`HostingOriginSingleDefinitionTest` 扫源码树盯着「没有第三处」，
`ProbeContractAlignmentTest` 盯着「探针页那份与定义处逐字相等」。文档里的引用不算，它们不参与运行。
