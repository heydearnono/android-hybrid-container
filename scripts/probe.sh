#!/usr/bin/env bash
# 探针跑法（三仓同名同形状：装 → 清日志 → 起 → 人工按 → 回读 → 十六行）。
#
# 这个命令**不代按**。八个按钮、三个对话框、系统 scheme 那一跳都必须人手做完，做完了再回车让它回读
# logcat。人工步骤见 docs/RUNBOOK.md。
#
# 输出固定十六行 `<slug> PASS|FAIL|MANUAL`，顺序与 pro 的清单一致（Kotlin 侧的定义处是
# ProbeContract.SLUGS，ProbeContractAlignmentTest 盯着这个文件里的顺序与它一致）。
# 没有对应记录的一律 FAIL——「没跑到」和「跑坏了」都是断言没成立。
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="net.xiaoluzhu.crab"
ACTIVITY="$APP_ID/$APP_ID.MainActivity"
INSTALL=1

for arg in "$@"; do
  case "$arg" in
    --no-install) INSTALL=0 ;;
    -h | --help)
      sed -n '2,10p' "$0"
      exit 0
      ;;
    *)
      echo "未知参数：$arg" >&2
      exit 2
      ;;
  esac
done

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if [[ ! -x $ADB ]]; then
  echo "找不到 adb：$ADB" >&2
  exit 1
fi

if [[ -z "$("$ADB" devices | sed -n '2p')" ]]; then
  echo "没有连上的设备/模拟器。minSdk 37，需要 API 37 的镜像。" >&2
  exit 1
fi

if [[ $INSTALL == 1 ]]; then
  echo "== 安装 =="
  ./scripts/check.sh >/dev/null # 装之前先保证本机这套判据是绿的
  ./gradlew --quiet :app:installDebug
fi

echo "== 清日志并启动 =="
"$ADB" logcat -c
"$ADB" shell am force-stop "$APP_ID" >/dev/null
"$ADB" shell am start -n "$ACTIVITY" >/dev/null

cat <<'CHECKLIST'

== 人工步骤（做完再回车；细节见 docs/RUNBOOK.md）==
  1. 等页面上的自动断言都出结果（不再有「…」）
  2. 点「跳到第二页」，再按系统返回键回到入口页
  3. 点「跨 origin」「_blank」「window.open」「未知 scheme」四个按钮，每次都该留在入口页
  4. 点「tel:」「mailto:」两个按钮，看是否跳出拨号盘/邮件（这条只能人工看，输出记 MANUAL）
  5. 点「对话框」：alert 关掉、confirm 选「确定」、prompt 里输入 CRAB
  6. 点「定位」，确认弹不出系统授权框、页面拿到失败回调（不是一直挂着）

按回车开始回读 logcat …
CHECKLIST
read -r _

LOG="$(mktemp -t crab-probe)"
trap 'rm -f "$LOG"' EXIT
"$ADB" logcat -d > "$LOG"

# 页面侧结果：取该 slug 最后一行（重跑时以最后一次为准）。
page_verdict() {
  local slug="$1" line
  line="$(grep -E "CRAB-PROBE ${slug} (PASS|FAIL)" "$LOG" | tail -n 1 || true)"
  case "$line" in
    *"CRAB-PROBE ${slug} PASS"*) echo PASS ;;
    *) echo FAIL ;;
  esac
}

# 原生日志侧结果：容器打了对应的那行就算成立。
log_verdict() {
  if grep -qF "$1" "$LOG"; then echo PASS; else echo FAIL; fi
}

count_lines() { grep -cF "$1" "$LOG" || true; }

# _blank 与 window.open 是两条路（用户点的 / 脚本发的），各要一行——只有一行说明其中一条被静默拦掉了。
blank_verdict() {
  local lines
  lines="$(count_lines 'CRAB-NAV nav-blank ')"
  if [[ ${lines:-0} -ge 2 ]]; then echo PASS; else echo FAIL; fi
}

# 未知 scheme 有两面：拒绝（那行日志）+ 不崩溃（回读时进程还在）。
# 少了后半句就分不出「拦住了」和「拦的时候把应用带走了」。
unknown_scheme_verdict() {
  if [[ "$(log_verdict 'CRAB-NAV nav-unknown-scheme ')" == PASS ]] &&
    [[ -n "$("$ADB" shell pidof "$APP_ID" | tr -d '\r')" ]]; then
    echo PASS
  else
    echo FAIL
  fi
}

# 页面读到答案 + 原生确实弹了三次，两边都要。
dialog_verdict() {
  local dialogs
  dialogs="$(count_lines 'CRAB-DLG ')"
  if [[ "$(page_verdict dialog)" == PASS && ${dialogs:-0} -ge 3 ]]; then echo PASS; else echo FAIL; fi
}

# 页面被拒 + 原生记了这次请求。
permission_verdict() {
  if [[ "$(page_verdict permission)" == PASS ]] && grep -qF 'CRAB-PERM ' "$LOG"; then
    echo PASS
  else
    echo FAIL
  fi
}

echo "== 结果 =="
FAILED=0
emit() {
  printf '%s %s\n' "$1" "$2"
  [[ $2 == FAIL ]] && FAILED=1
  return 0
}

emit origin "$(page_verdict origin)"
emit storage "$(page_verdict storage)"
emit subresource "$(page_verdict subresource)"
emit intercept "$(page_verdict intercept)"
emit escape "$(page_verdict escape)"
emit escape-encoded "$(page_verdict escape-encoded)"
emit inject-order "$(page_verdict inject-order)"
emit inject-scope "$(page_verdict inject-scope)"
emit nav-same-origin "$(page_verdict nav-same-origin)"
emit nav-back "$(page_verdict nav-back)"
emit nav-cross-origin "$(log_verdict 'CRAB-NAV nav-cross-origin ')"
emit nav-blank "$(blank_verdict)"
# 跳没跳出拨号盘/邮件只有人眼能看见，容器这边只知道自己交了出去。
emit nav-system-scheme MANUAL
emit nav-unknown-scheme "$(unknown_scheme_verdict)"
emit dialog "$(dialog_verdict)"
emit permission "$(permission_verdict)"

exit "$FAILED"
