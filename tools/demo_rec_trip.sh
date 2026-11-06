#!/bin/bash
# 장면 B(debug): 데모 여행 90초(동네→도시→재탐색→들판→산길→도착) + 얼마나 남았어? 두 번 + 도착 축하
export ANDROID_ADB_SERVER_PORT=5038
A=/Volumes/DevSSD/Android/sdk/platform-tools/adb
R=/Volumes/DevSSD/Android/rec
LOG=$R/segB.log; : > $LOG
now() { python3 -c "import time;print(time.time())"; }
ev() { echo "$(python3 -c "print(round($(now)-$T0,1))") $1" >> $LOG; }
tap() { $A shell input tap $1 $2 </dev/null; }
$A shell am start -n com.woorieodiga.app/.MainActivity </dev/null >/dev/null; sleep 6
$A emu screenrecord start --size 1920x1080 --time-limit 120 --bit-rate 10000000 $R/segB.webm </dev/null
T0=$(now)
ev start; sleep 2
ev demo; $A shell am start --activity-single-top -n com.woorieodiga.app/.MainActivity --ez demo true </dev/null >/dev/null
sleep 18; ev tap-ask1; tap 2160 535
sleep 50; ev tap-ask2; tap 2160 535
sleep 35; ev end
$A emu screenrecord stop </dev/null
