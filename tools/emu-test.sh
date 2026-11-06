#!/usr/bin/env bash
# 우리 어디가? 에뮬레이터 스모크 시험 (Pleos Connect, API 34)
# 사용: bash tools/emu-test.sh   (에뮬레이터는 미리 켜 둘 것. 이 스크립트는 켜지 않는다)
set -u

# ===== 조정하는 곳 ==========================================================
ADB="${ADB:-/Volumes/DevSSD/Android/sdk/platform-tools/adb}"
PKG="com.woorieodiga.app"
ACT="$PKG/.MainActivity"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"

# 창 크기 목록: "이름 가로x세로 밀도(dpi)": 값은 짐작이니 실제 FormFactor 값으로 고칠 것
FORM_FACTORS=(
  "portrait   840x1185  160"
  "landscape  1920x720   160"
  "landscape-wide 2560x1080 160"
)
# 기어: user 빌드라 car_service inject 는 막혀 있다. Virtual Console HTTP(PUT /vehicle/gear)로 바꾼다
VC="http://127.0.0.1:8088"
# 데모 시각표(시작 이후 초): 0s 25s 재탐색(45초 틱, 배지는 8초 유지) 70s 도착(~90s) P 전환(~93s)
DEMO_SHOTS="0 25 47 70 92 97"
# ============================================================================

OUT="$ROOT/tools/out/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
N=0
FAILS=()

log()  { echo "[emu-test] $*"; }
warn() { echo "[emu-test] 경고: $*" >&2; FAILS+=("$*"); }

# 번호 붙은 PNG 저장: shot <이름>
shot() {
  N=$((N + 1))
  local f; f="$(printf '%s/%02d-%s.png' "$OUT" "$N" "$1")"
  "$ADB" exec-out screencap -p > "$f" 2>/dev/null
  if [ -s "$f" ]; then log "스크린샷 $(basename "$f")"; else warn "스크린샷 실패 $1"; fi
}

# 끝나면(중간에 죽어도) 화면 설정을 되돌린다
cleanup() {
  "$ADB" shell wm size reset >/dev/null 2>&1
  "$ADB" shell wm density reset >/dev/null 2>&1
  "$ADB" shell cmd uimode night auto >/dev/null 2>&1
  [ -n "${VC_PID:-}" ] && kill "$VC_PID" 2>/dev/null
}
trap cleanup EXIT

# 시작 시각 기준으로 N초 지점까지 기다린 뒤 촬영: shot_at <T0> <초> <이름>
shot_at() {
  local now; now="$(date +%s)"
  local wait=$(($1 + $2 - now))
  [ "$wait" -gt 0 ] && sleep "$wait"
  shot "$3"
}

# ---------- 1. 기기 대기, 설치, 실행 ----------
[ -x "$ADB" ] || { echo "adb 없음: $ADB" >&2; exit 2; }
[ -f "$APK" ] || { echo "APK 없음: $APK" >&2; exit 2; }
log "기기 기다리는 중..."
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
log "부팅 완료: $("$ADB" shell getprop ro.product.model | tr -d '\r') / API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
sleep 5   # 부팅 직후 시스템 서비스가 자리잡을 시간

"$ADB" logcat -c
"$ADB" install -r -g "$APK" 2>&1 | tee "$OUT/install.txt" | tail -2
grep -q "Success" "$OUT/install.txt" || { echo "설치 실패, 중단" >&2; exit 3; }

"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -W -n "$ACT" > "$OUT/start.txt" 2>&1
sleep 4
shot "home"

front() { "$ADB" shell am start --activity-single-top -n "$ACT" > /dev/null 2>&1; sleep 2; }

# ---------- 2. 기어 (Virtual Console 을 열어 두고 HTTP 로) ----------
"$ADB" forward tcp:8088 tcp:8088 >/dev/null
python3 "$ROOT/tools/vc_hold.py" > "$OUT/vc.txt" 2>&1 &
VC_PID=$!
for _ in $(seq 1 40); do grep -q "VC_" "$OUT/vc.txt" && break; sleep 1; done
if grep -q "VC_READY" "$OUT/vc.txt"; then
  for g in Drive Park; do
    for _ in 1 2 3; do  # 콘솔이 막 열렸을 땐 emulator_inactive 가 한두 번 난다
      curl -s -m 10 -X PUT "$VC/vehicle/gear?gear=$g" > "$OUT/gear-$g.txt"
      grep -q '"error"' "$OUT/gear-$g.txt" || break
      sleep 2
    done
    grep -q '"error"' "$OUT/gear-$g.txt" && warn "기어 $g 실패: $(cat "$OUT/gear-$g.txt")"
    sleep 3
    shot "gear-$g"
  done
else
  warn "Virtual Console 준비 안 됨($OUT/vc.txt) - 기어 시험 건너뜀"
fi

# ---------- 3. 데모 여행(디버그 빌드: --ez demo true 로 시작) ----------
# --activity-single-top: 이미 떠 있는 화면에도 onNewIntent 로 전달
"$ADB" shell am start --activity-single-top -n "$ACT" --ez demo true > /dev/null 2>&1
T0="$(date +%s)"
log "데모 여행 시작"
for s in $DEMO_SHOTS; do
  shot_at "$T0" "$s" "demo-${s}s"
done

# 부팅 직후엔 홈이 기본 내비를 앞으로 띄우므로 단계마다 앱을 다시 앞으로

# ---------- 4. 주야간 ----------
front
for m in yes no; do
  "$ADB" shell cmd uimode night "$m" >/dev/null 2>&1 || warn "uimode night $m 실패"
  sleep 2
  shot "night-$m"
done

# ---------- 5. 창 크기 ----------
front
for ff in "${FORM_FACTORS[@]}"; do
  set -- $ff
  "$ADB" shell wm size "$2" >/dev/null 2>&1 || warn "wm size $2 실패"
  "$ADB" shell wm density "$3" >/dev/null 2>&1 || warn "wm density $3 실패"
  sleep 3
  shot "size-$1-$2"
done
"$ADB" shell wm size reset >/dev/null 2>&1
"$ADB" shell wm density reset >/dev/null 2>&1
sleep 2
shot "size-reset"

# ---------- 6. 로그 ----------
PID="$("$ADB" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
"$ADB" logcat -d -v threadtime > "$OUT/logcat-full.txt" 2>&1
{
  [ -n "$PID" ] && "$ADB" logcat -d -v threadtime --pid="$PID"
  "$ADB" logcat -d -v threadtime -s WooriVoice:V WooriNavi:V WooriGear:V NaviHelper:V AndroidRuntime:V
} > "$OUT/logcat.txt" 2>&1
[ -z "$PID" ] && warn "앱 프로세스가 없음(죽었을 수 있음)"
FATAL="$(grep -c 'FATAL EXCEPTION' "$OUT/logcat-full.txt")"
if [ "$FATAL" -gt 0 ]; then
  warn "FATAL EXCEPTION $FATAL 건 (grep 'FATAL EXCEPTION' -A15 $OUT/logcat-full.txt)"
fi

# ---------- 7. 요약 ----------
echo
echo "=============== 요약 ==============="
echo "출력: $OUT"
echo "스크린샷: $N 장, 앱 pid: ${PID:-없음}, FATAL: $FATAL"
if [ "${#FAILS[@]}" -eq 0 ]; then echo "문제 없음"; else
  echo "확인할 것 ${#FAILS[@]}건:"; for f in "${FAILS[@]}"; do echo "  - $f"; done
fi
[ "$FATAL" -eq 0 ]
