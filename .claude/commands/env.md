---
description: 探测并诊断本机 Android 开发环境
allowed-tools: Bash(./scripts/env-probe.sh), Bash(adb *)
---

跑 `./scripts/env-probe.sh`，然后基于输出做诊断：

- 哪些缺失项会阻塞当前想做的事（cmdline-tools 缺 → 装不了 SDK 组件、建不了 AVD、跑不了 instrumented 测试；无真机且无 AVD → APK 装不上、UI 验证不了；NDK 缺 → 原生模块编不了）
- 给出对应的补齐命令，但**不要擅自安装任何东西**，列出来让我决定
- 如果 `CLAUDE.md`「环境事实」一节和实际不一致，更新它

输出简洁：先说「能做什么 / 不能做什么」，再列细节。
