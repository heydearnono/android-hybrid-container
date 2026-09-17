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

<!--TASKS-BODY-->
