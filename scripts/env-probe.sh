#!/usr/bin/env bash
# 生产级 Android 基座 —— 本机环境探测
# 用法: ./scripts/env-probe.sh
set -uo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

hr() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
no() { printf '  \033[31m✗\033[0m %s\n' "$1"; }

hr "JDK"
# macOS 自带 /usr/bin/java 存根（stub），存在但无 runtime 时会报错，所以要看退出码而非 command -v
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
                             || no "cmdline-tools 缺失 → 无法用 sdkmanager / avdmanager"
  if [ -d "$SDK/ndk" ]; then
    ok "NDK: $(ls "$SDK/ndk" | tr '\n' ' ')"
  else
    no "NDK 缺失 → 原生（C/C++）模块编译不了"
  fi
else
  no "未找到 Android SDK（试过 $SDK）"
fi

hr "Gradle"
command -v gradle >/dev/null 2>&1 && ok "gradle CLI: $(gradle --version 2>/dev/null | awk '/^Gradle/{print $2}')" \
                                  || no "gradle CLI 未安装（用 wrapper 即可）"
DISTS=$(ls "$HOME/.gradle/wrapper/dists" 2>/dev/null | grep -v CACHEDIR | tr '\n' ' ')
[ -n "$DISTS" ] && ok "已缓存 wrapper 发行版: $DISTS" || no "无已缓存的 wrapper 发行版（首次构建需联网下载）"

hr "设备 / 模拟器"
if command -v adb >/dev/null 2>&1; then
  ok "adb: $(adb --version 2>/dev/null | awk '/version/{print $NF; exit}')"
  DEV=$(adb devices | awk 'NR>1 && NF {print $1"("$2")"}' | tr '\n' ' ')
  if [ -n "$DEV" ]; then
    ok "已连接: $DEV"
    for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
      printf '     %s → %s / %s / Android %s / SDK %s\n' "$s" \
        "$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')" \
        "$(adb -s "$s" shell getprop ro.board.platform 2>/dev/null | tr -d '\r')" \
        "$(adb -s "$s" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')" \
        "$(adb -s "$s" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    done
  else
    no "无已连接设备 → APK 装不上、跑不了 instrumented 测试，UI 只能靠人工在 IDE 里看"
  fi
else
  no "adb 不可用"
fi
AVDS=$(ls "$HOME/.android/avd" 2>/dev/null | grep '\.avd$' | tr '\n' ' ')
[ -n "$AVDS" ] && ok "AVD: $AVDS" || no "无 AVD"

hr "其他运行时"
for c in python3 node uv git; do
  command -v "$c" >/dev/null 2>&1 && ok "$c: $($c --version 2>&1 | head -1)" || no "$c 未安装"
done

echo
