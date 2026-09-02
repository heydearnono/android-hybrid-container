# ADR-0001：验证闭环限定为编译 + JVM 单测 + 静态检查

| | |
|---|---|
| 编号 | ADR-0001 |
| 日期 | 2026-09-02 |
| 状态 | 已采纳 |
| 影响范围 | 测试 / 架构分层 |

## 决策

自动化验证只依赖三件事：**编译通过、JVM 单测全过、静态检查零 error**。全部收敛到 `./scripts/check.sh` 一个命令。不引入 instrumented 测试和 Robolectric。

## 背景

这个仓库全部由 AI 开发，前提是 AI 必须能自己判断改动对不对。当前环境实测结论：

- 无真机、无 AVD、无 system-image
- 无 `cmdline-tools`，`sdkmanager` / `avdmanager` 不可用，所以连「下载 system-image 自己建 AVD」这条路也是断的

也就是说 APK 装不上、跑不起来、UI 看不到，任何需要 Android 运行时的验证都做不了。

## 候选方案

| 方案 | 优势 | 代价 | 关键风险 |
|---|---|---|---|
| A（选中）编译 + JVM 单测 + lint | 全部可离线、可重复、几分钟内出结果 | 覆盖不到 UI 与真机行为 | 逻辑对但装上去崩，AI 不会知道 |
| B 加 Robolectric | 能测部分 Android API | 多一层沙箱语义差异，失败原因更难归因；仍不是真机 | 通过≠真机通过，反而给假信心 |
| C 等有设备了再建工程 | 验证最真实 | 阻塞整个基座的搭建 | 无限期等待 |

## 理由

选 A 不是因为它够好，而是因为 B 的增量收益在「AI 自主判断」这个前提下是负的：Robolectric 绿了仍然不能说明真机可用，却会让人以为可以。与其增加一个语义模糊的信号，不如把边界划清——**能验证的部分做到硬，不能验证的部分明确标出来交给人**。

这个决策反过来约束架构：既然 JVM 单测是唯一自动化手段，业务逻辑就必须尽量待在纯 JVM 模块里（见 [ADR-0002](0002-core-modules-pure-jvm.md)）。

## 后果

- 接受了什么代价：UI 视觉、APK 可安装性、真机性能与兼容性完全不在自动化覆盖内，每次改动都需要人工在 Android Studio 里确认一次视觉效果。
- 引入了什么依赖 / 锁定：`unitTests.isReturnDefaultValues = true`（否则 `android.jar` 的 stub 会抛 "not mocked"）。
- 什么条件下重新评估：用户在 SDK Manager 里装上 `cmdline-tools` 之后。届时可以拉 system-image、起 AVD，`connectedDebugAndroidTest` 变成可跑项，应新写 ADR 把 instrumented 测试纳入 `check.sh`。

## 参考

- `scripts/check.sh`、`scripts/env-probe.sh`
- `CLAUDE.md` 的「验证闭环」与「环境事实」两节
