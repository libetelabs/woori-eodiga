# Pleos 에뮬레이터 시험 도구 메모

> 2026-10-30 레인이 문서를 보고 정리한 원본에, 실제로 돌려 보고 알게 된 것을 맨 위에 덧붙였다.

## 실제로 확인한 것(10-30)

- `cmd car_service inject-vhal-event` 는 user 빌드라 **막힌다**. 기어는 Virtual Console HTTP `PUT /vehicle/gear?gear=Drive|Park` (콘솔 페이지가 열려 있어야 함, `tools/vc_hold.py`).
- Virtual Console 에는 HTTP API 가 있다: `/vehicle/state`, `/vehicle/gear`, `/vehicle/controls`, `/vehicle/body`, `/vehicle/hvac`, `/vehicle/etcs`, `/vehicle/catalog`.
- `wm size` / `wm density` 로 폼팩터를 흉내 내면 Pleos 홈 배치가 깨진다. FormFactorConsole + bridge 경로로 해야 한다(미시험).

---

## 원본(문서 기준, 미검증 표시 포함)


ADB=/Volumes/DevSSD/Android/sdk/platform-tools/adb

## Installed (VERIFIED)
- Go 1.27.3 darwin-arm64: /Volumes/DevSSD/Android/tools/go  (not via brew; internal disk untouched)
- host-display-bridge 1.0.0 source: /Volumes/DevSSD/Android/tools/host-display-bridge-dl/host-display-bridge
- Built binary (unit tests pass): /Volumes/DevSSD/Android/tools/connect-display-bridge
- Go caches: tools/gopath, tools/gocache
- Rebuild: export GOPATH=/Volumes/DevSSD/Android/tools/gopath GOCACHE=/Volumes/DevSSD/Android/tools/gocache GOTOOLCHAIN=local PATH=/Volumes/DevSSD/Android/tools/go/bin:$PATH; cd host-display-bridge-dl/host-display-bridge && go build -o ../../connect-display-bridge .

## FormFactorConsole
- Docs give NO download/APK/package/launch command; it is "an app on the emulator" (presumably preinstalled in the Pleos image).
  After boot find it: `$ADB shell pm list packages | grep -i -E "formfactor|console|pleos"`, then launch from the launcher or
  `$ADB shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1`. UNVERIFIED.
- Needs standalone (windowed) emulator, not headless/embedded; multi-display enabled; emulator >= 34.1.1.

## Form-factor flow (from docs)
1. Boot AVD (cold boot; quick boot disables VHAL). Emulator needs gRPC: discovery file token, or launch with `-grpc 8554 -grpc-use-token`.
2. `$ADB devices` ; `$ADB emu avd name` (e.g. Pleos_Connect_v2.0; doc passes "Pleos Connect v2.0")
3. `/Volumes/DevSSD/Android/tools/connect-display-bridge --avd "<name>"`  (HTTP 127.0.0.1:8096; fallback `--emulator-address 127.0.0.1:8554`). Keep running.
4. Open FormFactorConsole in the emulator (uses port 8095, forwarded via adb: `adb forward tcp:8095 tcp:8095` is implied; docs only show removal).
5. Done: `$ADB forward --remove tcp:8095`
- Bridge HTTP API (from its README, UNVERIFIED live):
  - `curl http://127.0.0.1:8096/health`
  - `curl http://127.0.0.1:8096/api/window-policy/display`
  - create secondary display (slot 2): `curl -X PUT http://127.0.0.1:8096/api/window-policy/display/create -H 'Content-Type: application/json' -d '{"dpi":160,"width":2560,"height":1440,"orientation":"portrait"}'`
  - remove: `curl -X PUT .../display/remove -H 'Content-Type: application/json' -d '{}'`
  So form-factor testing creates a DISPLAY2 (extra display), it does not resize the main one.

## (a) Gear via adb
- GEAR_SELECTION = 0x11400400 (INT32, global, SYSTEM). VehicleGear: NEUTRAL=1, REVERSE=2, PARKING=4, DRIVE=8 (AOSP; CURRENT_GEAR is 0x11400401).
- Syntax (verified in AOSP CarShellCommand): `inject-vhal-event <prop> [zone] <data> [-t delay]`; zone optional (defaults global).
  - `$ADB shell cmd car_service inject-vhal-event 0x11400400 4`   # P
  - `$ADB shell cmd car_service inject-vhal-event 0x11400400 8`   # D
  (tools/emu-test.sh already uses this.)
- UNVERIFIED: whether Pleos' VHAL accepts it / whether it overrides Virtual Console state. Fallback: Virtual Console Motion tab > Gear Selection (P/R/N/D).
- Read-back: `$ADB shell dumpsys car_service` (property dump) UNVERIFIED.

## Virtual Console
- `$ADB forward tcp:8088 tcp:8088` then http://127.0.0.1:8088/ (min emulator v2.1.0). Domains: Vehicle, Motion, ADAS, Convenience, Safety, Energy, Climate.
- Docs mention NO HTTP API, no curl interface. Probe after boot (UNVERIFIED): `curl -s http://127.0.0.1:8088/`, then check the page JS (network calls) in browser dev tools.

## (b) Navigation simulation
- NOT documented anywhere I fetched (virtual-console, use-vehicle-emulator, release note, form-factor). No known intent/adb method.
- App reads NaviHelper getDestinationInfo (perm pleos.car.permission.NAVI_ROUTE). Options: set a destination in the stock navi app on the AVD; or use the app's built-in demo button (emu-test.sh taps it); or probe console JS for a navi endpoint. All UNVERIFIED.

## (c) Display size
- Docs give NO wm size/density numbers (presets are only in screenshots; only "12.9 portrait" and "14.6 landscape" names known). The 17" 840x1184dp / 1700x1184dp values are from the user's context, not confirmed by me.
- dp -> px: px = dp * dpi/160. Example at 160 dpi, 840x1184dp = `wm size 840x1184; wm density 160`; at 240 dpi = `wm size 1260x1776; wm density 240`. Pick dpi from FormFactorConsole's displayed values.
- `$ADB shell wm size 840x1184` ; `$ADB shell wm density 160` ; reset: `$ADB shell wm size reset; $ADB shell wm density reset`. On multi-display use `-d <displayId>`. UNVERIFIED on AAOS main display; the bridge route above is the official one.
