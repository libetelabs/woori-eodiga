# 우리 어디가? (Pleos Connect 앱)

현대차그룹 차량용 앱 공모전 출품작. 뒷자리 아이를 위한 "여행 진행판" 앱이다.
순정 내비에 찍힌 목적지를 읽어, 남은 길을 장난감 세상 그림과 "만화 몇 편" 으로 보여 준다.

## 시연 영상

[`demo/우리어디가_시연영상.mp4`](demo/) (1분 43초, 1920×1080) — Pleos Connect 에뮬레이터 실제 화면 녹화.
첫 화면·유튜브 보기·탈것 고르기 → 순정 내비 목적지를 앱이 읽음 → 데모 여행(2배속) → 도착 축하.
앱이 말하는 문장은 자막(「음성」)으로 넣었다(에뮬레이터 소리는 녹음되지 않음). 다시 만들기: `tools/make_demo_video.py`, 녹화 순서는 `tools/demo_rec_*.sh`.

## 바로 설치해 보기

- 제출용 APK: [`apk/libetelabs_woorieodiga_release.apk`](apk/) (release, 서명됨, 약 24MB)
- 시험 환경: Pleos Connect 에뮬레이터 v2.1(Android 14, `crp_car_arm64_emulator`), 장치 "Pleos Connect v2.1.0" 2560×1440, macOS(Apple Silicon)
- 설치: 에뮬레이터를 켠 뒤 `adb install apk/libetelabs_woorieodiga_release.apk` → 홈 아래 앱 막대의 「우리 어디가?」 아이콘
- 내비에서 목적지를 찍으면 여행이 시작된다. 데모 여행 버튼은 디버그 빌드에만 있다(아래 「빌드」).

## 빌드

- Android Studio(최신)로 이 폴더를 열면 된다. Gradle 8.11.1, AGP 8.7.2, Kotlin 2.0.21.
- 명령줄: `./gradlew assembleDebug` (wrapper jar 가 없으면 Android Studio 가 만든다)
- 결과: `app/build/outputs/apk/debug/app-debug.apk`
- release: `assembleRelease`. 서명 키는 `local.properties` 의 `release.storeFile` 등에서 읽는다(키 파일·비밀번호는 레포 밖). 없으면 서명 없이 만든다.
- Pleos SDK 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (settings.gradle.kts)
- `local.properties` 의 `sdk.dir` 은 이 맥의 Unity 에 딸린 SDK 를 가리킨다. Android Studio 로 열면 자기 SDK 로 바뀐다.

## 쓰는 API

| 하는 일 | API | 권한 |
| --- | --- | --- |
| 목적지 이름·남은 거리·남은 시간 | NaviHelper `getDestinationInfo` → `onDestinationInfo` (20초 간격, 화면 보일 때만) | `pleos.car.permission.NAVI_ROUTE` |
| 남은 시간(자주) | `onDrivingInfo.destination.duration` | 〃 |
| 출발 시 전체 거리 | `onRouteStarted(RouteStartInfo.distance)` | 〃 |
| 재탐색 / 취소 / 도착 | `onReRouteEnded` / `onRouteCancelled` / `onDestinationArrived` | 〃 |
| 즐겨찾기·최근 목적지 | `getBookmarkInfo` / `getRecentDestinationInfo` | 〃 |
| 바로 출발(정차 중) | `requestRoute(RouteInfo)` | `pleos.car.permission.NAVI_ROUTE_SEARCH` |
| 기어 P/D | AAOS `CarPropertyManager` `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` |
| 유튜브 보기(정차 중) | 차의 유튜브 앱을 `ACTION_VIEW` 플레이리스트 주소로 연다. 목록: [docs/youtube-kids-list.md](docs/youtube-kids-list.md) | 없음 |
| 음성 | Pleos `TextToSpeech` 3.3.1, 기기 안 합성(ON_DEVICE, ko_KR). Pleos 이미지엔 안드로이드 기본 TTS 엔진이 없다. 서버 합성은 Pleos 쪽 "준비 중"이라 꺼 둠(`Voice.SERVER_TTS_OPEN`) | `pleos.car.permission.TTS_SERVICE` |

음성 키: `local.properties` 에 `pleos.clientId=` / `pleos.clientSecret=` (Pleos Playground My Project 발급). 레포에 올리지 않는다.

## 시험 방법

에뮬레이터 설치·결과 기록: [docs/test-log-2026-10-30.md](docs/test-log-2026-10-30.md), [docs/test-log-2026-11-01.md](docs/test-log-2026-11-01.md), 절차 메모: [docs/emulator-runbook.md](docs/emulator-runbook.md)

1. 에뮬레이터 켜기(외장 SSD 에 설치됨)
   ```
   export ANDROID_SDK_ROOT=/Volumes/DevSSD/Android/sdk ANDROID_AVD_HOME=/Volumes/DevSSD/Android/avd
   $ANDROID_SDK_ROOT/emulator/emulator -avd Pleos_Connect_v2_1 -no-snapshot-save
   ```
   부팅 3~4분. 부팅 직후엔 홈이 기본 내비를 앞으로 띄우니 1~2분 더 기다린다.
2. **자동 시험**: `bash tools/emu-test.sh` → 설치·기어 P/D·데모 여행·주야간·창 크기 캡처, 결과는 `tools/out/<시각>/`.
   - 기어는 Virtual Console HTTP(`PUT /vehicle/gear?gear=Drive|Park`)로 바꾼다. 콘솔 페이지가 열려 있어야 먹어서
     `tools/vc_hold.py`(Playwright)가 헤드리스로 열어 둔다. `cmd car_service inject-vhal-event` 는 user 빌드라 막혀 있다.
   - 데모 여행은 `adb shell am start --activity-single-top -n com.woorieodiga.app/.MainActivity --ez demo true` (디버그 빌드만).
3. **데모 여행**(손으로): 앱 첫 화면 아래 "데모 여행 (시험용)". 12km·25분 길을 90초에 달린다.
   동네 → 도시 → 들판 → 산길 → 할머니 집, 중간에 "길이 바뀌었어", 도착 3초 뒤 P 로 바뀌며 축하 화면.
4. **진짜 내비**: 에뮬레이터 내비에서 목적지를 찍고 모의 주행을 켠다. 앱이 목적지와 남은 시간을 읽는지 본다. (11-01 통과: 롯데월드 → 놀이공원, 약 38분)
5. **기어**(손으로): `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ 의 P/R/N/D.
   D 에서는 탈것 고르기·바로 출발·도착 그림 바꾸기가 숨는다.
6. **창 크기**: 맥에서 `connect-display-bridge --avd Pleos_Connect_v2_1` 을 띄운 뒤 `bash tools/formfactor.sh` → 프리셋 6종 × 가로·세로 12가지 캡처(`tools/out/ff/`). `wm size` 흉내는 Pleos 홈 배치가 깨져 부정확하다.
7. **주야간**: `adb shell cmd uimode night yes|no`

## 화면 규칙

- 주행 중: 그림은 움직이지 않는다. 바뀌는 건 구간이 넘어갈 때뿐이다. 누를 수 있는 건 "얼마나 남았어?" 하나다.
- 정차(P) 중에만: 탈것 고르기, 즐겨찾기로 바로 출발, 목적지 종류 바꾸기, 유튜브 보기, 도착 축하.
- 유튜브 보기: 만화·애니·동요 공식 플레이리스트 각 20개. 고르면 차의 유튜브 앱이 열리고, 앱 막대나 뒤로 가기로 돌아오면 여행이 이어진다.
- 목적지 주소는 이 앱에서 받지 않는다. 운전자가 순정 내비에 찍은 목적지를 그대로 쓴다.
