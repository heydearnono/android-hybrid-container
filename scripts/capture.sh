#!/usr/bin/env bash
# 模拟器那一半的日志自动落盘：人只管在模拟器上操作，logcat 从开跑到结束持续写进 runs/<时间>-<场景>/。
#
# 用法: ./scripts/capture.sh [--install] <场景>
#   env             起应用，等两行 CRAB-ENV 出来就结束（RUNBOOK 五）
#   background      切后台：播放声音 → Home → 十秒 → 从最近任务切回（RUNBOOK 四）
#   destroy         最近任务里划掉，再从图标起（RUNBOOK 四）
#   back-at-root    入口页直接后退，再回来进第二页后退（RUNBOOK 四）
#   multi-instance  只带 -n 与带 MAIN + LAUNCHER 各起一遍，再点图标（RUNBOOK 四）
#   font-scale      font_scale 设 1.30 再起；退出时一律改回 1.00（RUNBOOK 六）
#   render-gone     先 adb root，再杀渲染进程两次（RUNBOOK 三）
#   missing-entry   挪走入口页、绕过 check.sh 装；退出时放回并重装（RUNBOOK 二）
#   free            什么都不代做，只录（临时造实例 B 那一类）
#
# 跑的时候在终端里敲一行回车就是一条备注，同时以 CRAB-MARK 写进 logcat；敲 shot 回车截一张图；
# 敲 q 回车或 Ctrl-D 结束，Ctrl-C 也行，收尾照做。页面上的按钮仍由人按（ADR-0001），脚本只代做 adb
# 这一层的准备与收尾。runs/ 不提交，把 runs/<时间>-<场景>.tar.gz 交出来即可。
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="net.xiaoluzhu.crab"
ACTIVITY="$APP_ID/$APP_ID.MainActivity"
ENTRY="app/src/main/assets/probe/index.html"
SCENES="env background destroy back-at-root multi-instance font-scale render-gone missing-entry free"

INSTALL=0
SCENE=""
for arg in "$@"; do
  case "$arg" in
    --install) INSTALL=1 ;;
    -h | --help)
      sed -n '2,17p' "$0"
      exit 0
      ;;
    -*)
      echo "未知参数：$arg" >&2
      exit 2
      ;;
    *)
      if [[ -n $SCENE ]]; then
        echo "一次只跑一个场景" >&2
        exit 2
      fi
      SCENE="$arg"
      ;;
  esac
done
if [[ -z $SCENE || " $SCENES " != *" $SCENE "* ]]; then
  echo "场景只能是：$SCENES" >&2
  exit 2
fi

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if [[ ! -x $ADB ]]; then
  echo "找不到 adb：$ADB" >&2
  exit 1
fi
if [[ -z "$("$ADB" devices | sed -n '2p')" ]]; then
  echo "没有连上的设备/模拟器。minSdk 37，需要 API 37 的镜像。" >&2
  exit 1
fi

RUN_NAME="$(date +%Y%m%d-%H%M%S)-$SCENE"
RUN_DIR="runs/$RUN_NAME"
LOGCAT="$RUN_DIR/logcat.txt"
STEPS="$RUN_DIR/steps.txt"
ENV_TXT="$RUN_DIR/env.txt"
mkdir -p "$RUN_DIR"

LOGCAT_PID=""
FONT_CHANGED=0
ENTRY_MOVED=0

adb_out() { "$ADB" shell "$@" 2>/dev/null | tr -d '\r' || true; }

sh_quote() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }

# 人做了什么、脚本做了什么，与 tick、CRAB-ERR 落在同一条时间线上。
mark() {
  local who="$1" msg="$2"
  printf '%s %s: %s\n' "$(date +%H:%M:%S)" "$who" "$msg" >>"$STEPS"
  "$ADB" shell "log -t CRAB-MARK $(sh_quote "$who: $msg")" >/dev/null 2>&1 || true
}

checklist() { tee -a "$STEPS"; }

shot() {
  local file
  file="$RUN_DIR/shot-$(date +%H%M%S).png"
  if "$ADB" exec-out screencap -p >"$file"; then
    mark 脚本 "截图 ${file##*/}"
    echo "截好了：$file"
  fi
}

# 打一条提示，等人回车再往下走；敲的内容照样记成备注。q / Ctrl-D 返回 1。
step() {
  local line
  printf '\n%s\n' "$1" | checklist
  while true; do
    printf '> '
    IFS= read -r line || return 1
    case "$line" in
      q) return 1 ;;
      shot) shot ;;
      '') return 0 ;;
      *)
        mark 人 "$line"
        return 0
        ;;
    esac
  done
}

notes_until_quit() {
  local line
  printf '\n备注：敲一行回车记一条，shot 截图，q 回车或 Ctrl-D 结束\n'
  while printf '> ' && IFS= read -r line; do
    case "$line" in
      q) break ;;
      shot) shot ;;
      '') ;;
      *) mark 人 "$line" ;;
    esac
  done
}

snapshot_env() {
  {
    echo "== $1 · $(date '+%F %T %z') =="
    echo "场景: $SCENE"
    echo "HEAD: $(git rev-parse HEAD 2>/dev/null || echo '?')"
    echo "未提交:"
    git status --short 2>/dev/null || true
    echo "设备: $("$ADB" get-serialno 2>/dev/null | tr -d '\r')"
    echo "ro.build.version.sdk: $(adb_out getprop ro.build.version.sdk)"
    echo "ro.build.fingerprint: $(adb_out getprop ro.build.fingerprint)"
    echo "WebView: $(adb_out dumpsys webviewupdate | grep -i 'current webview package' || echo '（dumpsys webviewupdate 里没读到）')"
    echo "font_scale: $(adb_out settings get system font_scale)"
    echo "pidof: $(adb_out pidof "$APP_ID")"
    echo "ps:"
    adb_out ps -A | grep -F "$APP_ID" || true
    echo
  } >>"$ENV_TXT" 2>&1
}

start_capture() {
  "$ADB" logcat -c
  "$ADB" logcat -v threadtime -b main,system,crash >"$LOGCAT" 2>&1 &
  LOGCAT_PID=$!
  sleep 1
  mark 脚本 "开录 $RUN_NAME"
}

count_log() { grep -cE "$1" "$LOGCAT" 2>/dev/null || true; }

# 等 logcat 里匹配的行攒够 want 条，最多等 timeout 秒。
wait_log() {
  local pattern="$1" want="$2" timeout="$3" i=0 n
  while ((i < timeout)); do
    n="$(count_log "$pattern")"
    if ((${n:-0} >= want)); then return 0; fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

launch() {
  "$ADB" shell am force-stop "$APP_ID" >/dev/null
  "$ADB" shell am start "$@" >/dev/null
  mark 脚本 "am start $*"
}

# 与桌面图标发的请求同形（见 docs/PITFALLS.md「模拟器与 probe.sh」）
launch_like_icon() { launch -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$ACTIVITY"; }

use_jbr() {
  if [[ -z ${JAVA_HOME:-} ]]; then
    local jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    if [[ ! -x $jbr/bin/java ]]; then
      echo "找不到可用的 JDK：JAVA_HOME 未设置，且 $jbr 不存在" >&2
      exit 1
    fi
    export JAVA_HOME="$jbr"
  fi
}

restore_entry() {
  if [[ -f $ENTRY ]]; then
    echo "!!! $ENTRY 已经在了，没动 $RUN_DIR/index.html，自己比对一下" >&2
    return
  fi
  if ! mv "$RUN_DIR/index.html" "$ENTRY"; then
    echo "!!! 入口页没放回去！它在 $RUN_DIR/index.html，手动执行：mv $RUN_DIR/index.html $ENTRY" >&2
    return
  fi
  ENTRY_MOVED=0
  mark 脚本 "入口页已放回"
  echo "== 放回入口页，重装 =="
  if ! ./gradlew --quiet :app:installDebug; then
    echo "!!! 重装失败。入口页已放回，手动执行：./gradlew :app:installDebug" >&2
    return
  fi
  launch_like_icon
  if wait_log 'CRAB-ENV UA:' 1 30; then
    mark 脚本 "重装后探针页回来了"
  else
    echo "!!! 重装后 30 秒内没等到探针页的 CRAB-ENV UA 行" >&2
  fi
}

finish() {
  local status=$?
  trap '' INT TERM
  set +e
  if [[ $FONT_CHANGED == 1 ]]; then
    "$ADB" shell settings put system font_scale 1.00
    local now
    now="$(adb_out settings get system font_scale)"
    if [[ "$(awk -v v="$now" 'BEGIN { print (v + 0 == 1) }')" == 1 ]]; then
      mark 脚本 "font_scale 已改回 $now"
    else
      echo "!!! font_scale 没改回来，现在是 ${now:-?}。手动执行：adb shell settings put system font_scale 1.00" >&2
    fi
  fi
  if [[ $ENTRY_MOVED == 1 ]]; then restore_entry; fi
  if [[ -n $LOGCAT_PID ]]; then
    mark 脚本 "停录"
    sleep 1
    kill "$LOGCAT_PID" 2>/dev/null
    wait "$LOGCAT_PID" 2>/dev/null
    grep -E 'CRAB-' "$LOGCAT" >"$RUN_DIR/crab.txt"
  fi
  snapshot_env 结束
  tar -czf "runs/$RUN_NAME.tar.gz" -C runs "$RUN_NAME"
  echo
  echo "== 落盘：$RUN_DIR/（打包：runs/$RUN_NAME.tar.gz）=="
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT TERM

scene_env() {
  start_capture
  launch_like_icon
  if ! wait_log 'CRAB-ENV DOCUMENT_START_SCRIPT=' 1 30; then
    echo "30 秒内没等到 DOCUMENT_START_SCRIPT 那行（CrabContainer 的诊断 init 块删了的话就不会有）"
  fi
  if ! wait_log 'CRAB-ENV UA:' 1 30; then
    echo "30 秒内没等到探针页的 UA 行"
  fi
  grep -E 'CRAB-ENV (DOCUMENT_START_SCRIPT|UA:)' "$LOGCAT" || true
}

scene_background() {
  start_capture
  launch_like_icon
  checklist <<'EOF'

== 切后台（RUNBOOK 四）==
  1. 等探针页出来、tick 在走
  2. 点「播放声音」，确认听得到
  3. 按 Home，等十秒
  4. 从最近任务切回（不要点桌面图标）
  5. 看声音停没停过、tick 接没接上
EOF
  notes_until_quit
}

scene_destroy() {
  start_capture
  launch_like_icon
  checklist <<'EOF'

== 划掉再起（RUNBOOK 四）==
  1. 等探针页出来
  2. 在最近任务里把应用划掉
  3. 点桌面图标重新起，等探针页出来
  4. 应用崩没崩、有没有弹「已停止运行」，敲一句备注
EOF
  notes_until_quit
}

scene_back_at_root() {
  start_capture
  launch_like_icon
  checklist <<'EOF'

== 入口页后退（RUNBOOK 四）==
  1. 等探针页出来，不要去第二页
  2. 按系统返回键：应当退到桌面
  3. 点桌面图标回来
  4. 点「跳到第二页」，按系统返回键：应当回到入口页。直接退到桌面也照实敲一句备注
EOF
  notes_until_quit
  adb_out dumpsys activity activities >"$RUN_DIR/activities.txt"
}

multi_instance_pass() {
  local label="$1"
  shift
  launch "$@"
  step "[$label] 等页面出来后按 Home，再点桌面图标；页面停稳后回车" || return 1
  adb_out dumpsys activity activities >"$RUN_DIR/activities-$label.txt"
  echo "[$label] 本应用的 Hist 行：" | checklist
  grep -E 'Hist #' "$RUN_DIR/activities-$label.txt" | grep -F "$APP_ID" | checklist || true
}

scene_multi_instance() {
  start_capture
  checklist <<'EOF'

== 多实例（RUNBOOK 四）==
  第一遍只写 -n，第二遍带 MAIN + LAUNCHER，其余相同。每遍脚本起应用，人按 Home 再点图标
EOF
  multi_instance_pass 1-only-n -n "$ACTIVITY" || return 0
  multi_instance_pass 2-like-icon -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n "$ACTIVITY" || return 0
  notes_until_quit
}

scene_font_scale() {
  start_capture
  mark 脚本 "font_scale 原值 $(adb_out settings get system font_scale)"
  FONT_CHANGED=1
  "$ADB" shell settings put system font_scale 1.30
  mark 脚本 "font_scale 设 1.30"
  launch_like_icon
  checklist <<'EOF'

== 系统字号与双指缩放（RUNBOOK 六）==
  1. 看探针页里的文字变没变大（原生错误界面上的文字会跟着变，那是 Compose 的 sp，不算）
  2. 敲 shot 截一张
  3. 两指在页面上撑开：页面不应放大
  4. 两件事各敲一句备注。退出时脚本把 font_scale 改回 1.00
EOF
  notes_until_quit
}

# 起应用之前就在的那些不是本应用的。渲染进程名随内核实现不同，按「多出来的 sandboxed_process」找。
renderer_pids() {
  adb_out ps -A | awk -v app="$APP_ID" '$NF ~ /sandboxed_process/ || index($NF, app ":") == 1 { print $2 }' | sort
}

kill_renderer() {
  local n="$1" base="$2" ours count pid before
  adb_out ps -A >"$RUN_DIR/ps-kill-$n.txt"
  ours="$(comm -13 <(printf '%s\n' "$base") <(renderer_pids) | grep . || true)"
  count="$(printf '%s' "$ours" | grep -c . || true)"
  if [[ $count == 1 ]]; then
    pid="$ours"
  else
    echo "起应用之后多出来的渲染进程有 ${count:-0} 个（$(printf '%s ' $ours)），挑不出唯一一个："
    grep -E "sandboxed_process|$APP_ID:" "$RUN_DIR/ps-kill-$n.txt" || true
    printf '要杀的 pid（q 结束）> '
    IFS= read -r pid || return 1
    if [[ -z $pid || $pid == q ]]; then return 1; fi
  fi
  before="$(count_log 'CRAB-ERR render-gone')"
  "$ADB" shell kill -9 "$pid"
  mark 脚本 "第 $n 次 kill -9 $pid"
  if wait_log 'CRAB-ERR render-gone' $((${before:-0} + 1)) 15; then
    grep -E 'CRAB-ERR render-gone' "$LOGCAT" | tail -n 1
  else
    echo "15 秒内没等到 CRAB-ERR render-gone"
  fi
}

scene_render_gone() {
  echo "== adb root =="
  "$ADB" root 2>&1 | tee "$RUN_DIR/root.txt" || true
  sleep 2
  "$ADB" wait-for-device
  local uid base
  uid="$(adb_out id -u)"
  echo "id -u: $uid" >>"$RUN_DIR/root.txt"
  if [[ $uid != 0 ]]; then
    echo "拿不到 root（带 Google Play 的镜像一律拒绝）。这一格在这台镜像上造不出来，原文在 $RUN_DIR/root.txt"
    return 0
  fi
  start_capture
  "$ADB" shell am force-stop "$APP_ID" >/dev/null
  sleep 1
  base="$(renderer_pids)"
  adb_out ps -A >"$RUN_DIR/ps-before.txt"
  launch_like_icon
  wait_log 'CRAB-ENV UA:' 1 30 || echo "30 秒内没等到探针页"
  kill_renderer 1 "$base" || return 0
  step "第一次杀完：页面应当自己重载回探针页。看清之后回车杀第二次（q 结束）" || return 0
  kill_renderer 2 "$base" || return 0
  step "第二次杀完：应当进「页面没能打开」。点「重试」回到探针页后回车杀第三次，看额度是否给满（q 跳过）" ||
    return 0
  kill_renderer 3 "$base" || return 0
  echo "第三次杀完：应当又悄悄恢复一回"
  notes_until_quit
}

scene_missing_entry() {
  if [[ ! -f $ENTRY ]]; then
    echo "!!! $ENTRY 不在。上一次 missing-entry 可能没放回：找 runs/*-missing-entry/index.html" >&2
    exit 1
  fi
  use_jbr
  start_capture
  mv "$ENTRY" "$RUN_DIR/index.html"
  ENTRY_MOVED=1
  mark 脚本 "入口页挪到 $RUN_DIR/index.html"
  echo "== 绕过 check.sh 直接装（对齐单测此时会红，是预期的）=="
  ./gradlew --quiet :app:installDebug
  launch_like_icon
  if wait_log 'CRAB-ERR load' 1 30; then
    grep -E 'CRAB-ERR load' "$LOGCAT" | tail -n 1
  else
    echo "30 秒内没等到 CRAB-ERR load"
  fi
  checklist <<'EOF'

== 入口文件挪走（RUNBOOK 二）==
  1. 屏幕上应当是「页面没能打开」+「重试」，不是白屏、不是 WebView 自带的错误页。敲 shot 截一张
  2. 点「重试」：文件还没放回，应当还是错误界面
  3. q 结束：脚本放回入口页、重装、再起一次，等探针页回来
EOF
  notes_until_quit
}

scene_free() {
  start_capture
  checklist <<'EOF'

== 只录（free）==
  脚本什么都不代做。应用要自己起：点桌面图标
EOF
  notes_until_quit
}

snapshot_env 开始
if [[ $INSTALL == 1 && $SCENE != missing-entry ]]; then
  use_jbr
  echo "== 安装 =="
  ./scripts/check.sh >/dev/null
  ./gradlew --quiet :app:installDebug
fi

case "$SCENE" in
  env) scene_env ;;
  background) scene_background ;;
  destroy) scene_destroy ;;
  back-at-root) scene_back_at_root ;;
  multi-instance) scene_multi_instance ;;
  font-scale) scene_font_scale ;;
  render-gone) scene_render_gone ;;
  missing-entry) scene_missing_entry ;;
  free) scene_free ;;
esac
