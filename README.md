# Crab · Android 容器

三端 WebView 容器的 Android 一端。规划在上游 `pro`（`Prospect` 仓）：容器要做什么、怎么算过、
取值取哪个，一律以那边的 `README.md` + `plan/` 五份文件为准，本仓不复述判据。

**它是一个把页面装起来跑的原生壳子**，范围只有五件事：本地承载、六项配置、页面开口前的注入、UA、
导航与降级。不做 JSBridge、不做文件上传下载、不做灰度/埋点/预热/离线包、不打包上架。

## 怎么验

两条命令，各管一半：

```bash
./scripts/check.sh          # 能在本机跑完的：格式、APK 产出、单测、lint
./scripts/check.sh --fix    # 先 spotlessApply 再跑上面这串
./scripts/probe.sh          # 探针页十六条断言，需要模拟器；人工那一遍要先跑完
```

`check.sh` 管判定逻辑，`probe.sh` 管「在真容器里到底成不成立」。两条都不能替代另一条：写进
`WebSettings` 的那几行 JVM 单测断不了，而路径归一化与导航闸门不该等到模拟器上才发现写错。

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

一律取 pro 的取值表，端内不另定；`https://and.crab.invalid` 这个字符串全仓只允许出现在
`HostingOrigin.kt` 一处，有一条单测盯着。
