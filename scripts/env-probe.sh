#!/usr/bin/env bash
# Crab 容器 —— 本机环境探测
# 用法: ./scripts/env-probe.sh
set -uo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

hr() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
no() { printf '  \033[31m✗\033[0m %s\n' "$1"; }

hr "JDK"
# macOS 自带 /usr/bin/java 是存根：存在但无 runtime 时会报错，所以看退出码而非 command -v
if java -version >/dev/null 2>&1; then
  ok "PATH 中的 java: $(java -version 2>&1 | head -1)"
else
  no "PATH 中无可用的 java（macOS 的 /usr/bin/java 只是存根）"
fi
if [ -x "$JBR/bin/java" ]; then
  ok "Android Studio JBR: $("$JBR/bin/java" -version 2>&1 | head -1)"
  echo "     JAVA_HOME=\"$JBR\""
else
  no "未找到 Android Studio 自带 JBR（$JBR）"
fi

hr "Android SDK"
if [ -d "$SDK" ]; then
  ok "SDK 根目录: $SDK"
  echo "     platforms:    $(ls "$SDK/platforms" 2>/dev/null | tr '\n' ' ')"
  echo "     build-tools:  $(ls "$SDK/build-tools" 2>/dev/null | tr '\n' ' ')"
  [ -d "$SDK/cmdline-tools" ] && ok "cmdline-tools 已安装（sdkmanager/avdmanager 可用）" \
                             || no "cmdline-tools 缺失 → 无法用 sdkmanager / avdmanager 拉镜像建 AVD"
  IMAGES=$(ls "$SDK/system-images" 2>/dev/null | tr '\n' ' ')
  [ -n "$IMAGES" ] && ok "system-images: $IMAGES" || no "无 system-image → 起不了模拟器（minSdk 37 需要 API 37 的镜像）"
else
  no "未找到 Android SDK（试过 $SDK）"
fi

hr "Gradle"
DISTS=$(ls "$HOME/.gradle/wrapper/dists" 2>/dev/null | grep -v CACHEDIR | tr '\n' ' ')
[ -n "$DISTS" ] && ok "已缓存 wrapper 发行版: $DISTS" || no "无已缓存的 wrapper 发行版（首次构建需联网下载）"

hr "设备 / 模拟器"
if command -v adb >/dev/null 2>&1; then
  ok "adb: $(adb --version 2>/dev/null | awk '/version/{print $NF; exit}')"
  DEV=$(adb devices | awk 'NR>1 && NF {print $1"("$2")"}' | tr '\n' ' ')
  if [ -n "$DEV" ]; then
    ok "已连接: $DEV"
    for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
      printf '     %s → %s / Android %s / SDK %s\n' "$s" \
        "$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')" \
        "$(adb -s "$s" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')" \
        "$(adb -s "$s" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    done
  else
    no "无已连接设备 → scripts/probe.sh 跑不了，十六条断言一条也核不了"
  fi
else
  no "adb 不可用"
fi
AVDS=$(ls "$HOME/.android/avd" 2>/dev/null | grep '\.avd$' | tr '\n' ' ')
[ -n "$AVDS" ] && ok "AVD: $AVDS" || no "无 AVD"

echo
