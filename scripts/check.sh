#!/usr/bin/env bash
# 唯一的验证入口。改完任何代码都跑它，绿了才算完。
#
# 为什么是这四步：本机没有真机、没有 AVD、也没有 cmdline-tools 去下 system-image，
# 所以「编译 + JVM 单测 + 静态检查」是唯一能自动化的验证闭环。
# APK 装不上、UI 长什么样、instrumented 测试，都验证不到——那部分要人在 Android Studio 里看。
set -euo pipefail

cd "$(dirname "$0")/.."

# java 不在 PATH（macOS 的 /usr/bin/java 是个 stub），用 Android Studio 自带的 JBR。
# 不写进 gradle.properties：那是机器特定路径，提交进去会污染仓库。
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x "$JBR/bin/java" ]; then
    export JAVA_HOME="$JBR"
  else
    echo "找不到 JDK：$JBR 不存在，且 JAVA_HOME 未设置。" >&2
    exit 1
  fi
fi

FIX=0
for arg in "$@"; do
  case "$arg" in
    --fix) FIX=1 ;;
    *) echo "未知参数：$arg（可用：--fix，自动修格式问题）" >&2; exit 2 ;;
  esac
done

if [ "$FIX" -eq 1 ]; then
  echo "==> spotlessApply（自动修格式）"
  ./gradlew spotlessApply
fi

echo "==> spotlessCheck / assembleDebug / test / lint"
./gradlew spotlessCheck assembleDebug test lint

echo
echo "全绿。验证不到的部分：APK 是否能装能跑、UI 视觉、instrumented 测试。"
