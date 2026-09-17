#!/usr/bin/env bash
# Crab 容器 —— AI 能自己跑完的全部验证
# 用法: ./scripts/check.sh [--fix]
#   绿的定义：格式无 diff、APK 产出、单测全过、lint 零 error
set -euo pipefail

cd "$(dirname "$0")/.."

# java 不在 PATH（macOS /usr/bin/java 只是存根），用 Android Studio 自带的 JBR 21。
# 不要写进 gradle.properties——那是机器特定路径。
if [ -z "${JAVA_HOME:-}" ]; then
  JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [ -x "$JBR/bin/java" ]; then
    export JAVA_HOME="$JBR"
  else
    echo "找不到可用的 JDK：JAVA_HOME 未设置，且 $JBR 不存在" >&2
    exit 1
  fi
fi

if [ "${1:-}" = "--fix" ]; then
  ./gradlew spotlessApply
fi

./gradlew spotlessCheck assembleDebug test lint
