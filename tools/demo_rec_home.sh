#!/bin/bash
# 장면 A(release): 첫 화면 → 유튜브 보기 → 탈것 고르기 → 바로 출발(롯데월드) → 여행 화면 → 얼마나 남았어? → 기어 D/P
export ANDROID_ADB_SERVER_PORT=5038
A=/Volumes/DevSSD/Android/sdk/platform-tools/adb
R=/Volumes/DevSSD/Android/rec
LOG=$R/segA.log; : > $LOG
now() { python3 -c "import time;print(time.time())"; }
ev() { echo "$(python3 -c "print(round($(now)-$T0,1))") $1" >> $LOG; }
tap() { $A shell input tap $1 $2 </dev/null; }
gear() { curl -s -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=$1" >/dev/null; }
$A emu screenrecord start --size 1920x1080 --time-limit 178 --bit-rate 10000000 $R/segA.webm </dev/null
T0=$(now)
ev start; sleep 4
ev tap-youtube; tap 1694 734; sleep 4
ev tap-songs; tap 2227 243; sleep 4
ev tap-close; tap 1696 927; sleep 3
ev tap-ride; tap 1694 623; sleep 4
ev tap-train; tap 1050 608; sleep 5
ev tap-lotteworld; tap 1694 926; sleep 12
ev trip
ev tap-ask; tap 2160 535; sleep 6
ev gear-D; gear Drive; sleep 8
ev gear-P; gear Park; sleep 6
ev stop
$A emu screenrecord stop </dev/null
