#!/bin/bash
# FormFactorConsole 과 같은 창 크기(DISPLAY2)를 차례로 만들어 앱을 띄우고 캡처한다.
# 필요: 에뮬레이터 실행 중, connect-display-bridge 실행 중(127.0.0.1:8096). 결과는 tools/out/ff/<프리셋>-<방향>.png
# 창 크기(dp)는 FormFactorConsole 의 window-policy.js 값.
set -u
A="${ADB:-adb}"
OUT="$(cd "$(dirname "$0")" && pwd)/out/ff"
mkdir -p "$OUT"
PKG=com.woorieodiga.app
# 이름 dpi 가로w 가로h 세로w 세로h
PRESETS="s17 160 1700 1184 840 1184
s14.6 200 1360 948 672 948
s12.9 160 1274 855 628 855
ws24.6 160 1640 810 800 810
we24.6 160 1640 1400 800 1400
wf18.7 200 1256 800 648 800"
bridge() { curl -s -m 60 -X PUT "http://127.0.0.1:8096/api/window-policy/display/$1" -H 'Content-Type: application/json' -d "$2" >/dev/null; }
echo "$PRESETS" | while read -r name dpi lw lh pw ph; do
  for o in landscape portrait; do
    if [ $o = landscape ]; then w=$lw; h=$lh; else w=$pw; h=$ph; fi
    bridge remove '{}'; sleep 3
    bridge create "{\"dpi\":$dpi,\"width\":$((w*dpi/160)),\"height\":$((h*dpi/160)),\"orientation\":\"$o\"}"; sleep 4
    # 디스플레이를 다시 만들면 번호가 바뀐다(2, 3, 4…). adb shell 은 표준입력을 먹으니 </dev/null
    id=$($A shell dumpsys display </dev/null | sed -n 's/.*"Emulator 2D Display", displayId \([0-9]*\).*/\1/p' | tail -1)
    $A shell am force-stop --user 10 $PKG </dev/null
    $A shell am start --user 10 --display "$id" -n $PKG/.MainActivity </dev/null >/dev/null
    sleep "${WAIT:-18}"
    tmp=$(mktemp -d)
    $A emu screenrecord screenshot "$tmp" --display 2 </dev/null >/dev/null
    f=$(ls "$tmp"/*.png 2>/dev/null | head -1)
    [ -n "$f" ] && mv "$f" "$OUT/$name-$o.png" && echo "$name-$o ${w}x${h}dp" || echo "$name-$o 캡처 실패"
    rm -rf "$tmp"
  done
done
bridge remove '{}'
