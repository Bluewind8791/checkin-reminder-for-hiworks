# 출퇴근 알림 (Check-in Reminder for Hiworks)

하이웍스 근태관리에서 출퇴근 체크를 잊지 않도록, 아직 출근이나 퇴근을 찍지 않았으면 알림을 보내는 Android 앱입니다.

> **비공식 도구입니다.** 하이웍스나 가비아와 관계가 없고, 공식 API가 아닌 웹에서 쓰는 API를 호출합니다. 하이웍스 쪽 변경으로 언제든 동작하지 않을 수 있습니다. 「하이웍스」는 해당 회사의 상표입니다.

## 화면

| 출근 | 퇴근 | 설정 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/main.png" width="240" alt="출근 탭. 오늘 출근 기록 08:32, 출근 시각, 알림 시각이 보이고 아래에 출근·퇴근·설정 탭과 지금 확인 버튼이 있는 독"> | <img src="docs/screenshots/end.png" width="240" alt="퇴근 탭. 퇴근 알림 ON 버튼, 퇴근 기록 18:00, 퇴근 시각, 퇴근 알림 시각 T+0·T+30"> | <img src="docs/screenshots/settings.png" width="240" alt="설정 탭. 알림 권한과 배터리 최적화 예외 상태, 버전과 새 버전 확인, 로그아웃"> |

| 퇴근 알림 시각 | 출근 시각 |
| :---: | :---: |
| <img src="docs/screenshots/alerts.png" width="240" alt="퇴근 알림 목록 T+0·T+30과 퇴근 10분 전·뒤를 골라 추가하는 입력란"> | <img src="docs/screenshots/start-time.png" width="240" alt="출근 시각을 직접 고르는 시계 다이얼로그"> |

출근 전까지 출근 체크를 하지 않았으면 이런 알림이 옵니다. 누르면 하이웍스 앱이 열립니다.

<img src="docs/screenshots/notification.png" width="420" alt="출근 체크를 아직 안 했습니다 · 09:00 출근, 10분 전입니다">

## 설치

휴대폰에서 [설치 페이지](https://checkin.bluewind91.com/)를 열어 APK를 받거나, [Releases](https://github.com/Bluewind8791/checkin-reminder-for-hiworks/releases/latest)에서 `app-release.apk`를 받아 설치합니다. Play 스토어 밖의 앱이라 「출처를 알 수 없는 앱 설치」를 허용해야 합니다.

처음 열면 앱 안에서 하이웍스에 로그인하고, 알림 권한을 허용하세요.

## 하는 일

- 근무 일정에서 오늘의 출근·퇴근 시각을 읽고, 휴일·휴가인 날은 건너뜁니다.
- 출근 시각 전에 출근 여부를 확인하고, 아직 출근 전이면 알림을 보냅니다. 알림 시각은 출근 1~60분 전 사이에서 1~5개를 정할 수 있습니다(기본 10·5·1분 전). 출근이 확인되면 남은 알림은 취소합니다.
- 퇴근 알림을 켜면(기본은 꺼짐) 퇴근 시각 전후에 퇴근 여부를 확인하고, 아직 퇴근 전이면 알림을 보냅니다. 알림 시각은 퇴근 60분 전부터 120분 뒤 사이에서 1~5개를 정할 수 있습니다(기본은 퇴근 시각). 출근을 찍지 않은 날이나 회사가 퇴근 체크를 쓰지 않는 경우에는 보내지 않습니다.
- 로그인 세션이 끊기지 않도록 3시간마다 조회 요청을 한 번 보냅니다.
- 앱을 열 때 [설치 페이지](https://checkin.bluewind91.com/)에 새 버전이 있는지 확인합니다. 있으면 바뀐 점을 보여 주고, 「업데이트」를 누르면 앱 안에서 내려받아 설치 화면을 띄웁니다. 처음 한 번은 이 앱에 「출처를 알 수 없는 앱 설치」를 허용해야 합니다.

## 하지 않는 일

- **출퇴근을 대신 찍지 않습니다.** 조회(GET)만 하고 출퇴근 등록 요청은 보내지 않습니다.
- 비밀번호를 저장하지 않습니다. 앱 안의 WebView로 직접 로그인하면 세션 쿠키(`PHPSESSID`)만 암호화해 기기에 저장합니다.
- `*.office.hiworks.com`(하이웍스)과 `checkin.bluewind91.com`(새 버전 확인·내려받기) 외의 서버와 통신하지 않습니다. 설치 페이지에는 아무것도 보내지 않고 받기만 하며, 하이웍스 세션 쿠키는 하이웍스에만 보냅니다.

## 빌드

- JDK 17, Android SDK 35
- `./gradlew assembleDebug`
- 최소 Android 8.0 (API 26)
- release 빌드는 Gradle 사용자 홈의 `gradle.properties`에 `checkinReminder.storeFile`, `checkinReminder.storePassword`, `checkinReminder.keyAlias`, `checkinReminder.keyPassword`가 있을 때만 서명됩니다. 없으면 서명되지 않은 APK가 나옵니다.

정확한 시각에 알림을 보내려고 `USE_EXACT_ALARM` 권한을 씁니다. 배터리 최적화 예외는 필수가 아니지만, 알림이 늦게 오면 설정해 주세요.

## 라이선스

[MIT](LICENSE). 포함된 폰트(JetBrains Mono, 나눔고딕코딩)는 SIL Open Font License를 따르며, 라이선스 원문은 `app/src/main/assets/licenses/`에 있습니다.
