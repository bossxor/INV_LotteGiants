# 사직스코어 (롯데 자이언츠 실시간)

롯데 전용 KBO 실시간 스코어 앱 + 홈 위젯 + Now Bar(Live Update) + 이벤트 알림.

## 기능

- **라이브**: 점수, 이닝, S/B/O, 루상 주자, 투수/타자/다음 타자, 선발 라인업, 5탭 상세
- **결과 / 일정**: 일별·월별 경기
- **KBO 순위** · 타이틀 순위
- **홈 위젯** / **Now Bar** / **이벤트 알림** (득점, 불펜 투수교체, 홈런, 취소 사유 등)
- **설정**: 즐겨찾기(사진), 테마, GitHub 업데이트 확인

## 빌드

JDK 17 + Android SDK. `local.properties`에 `sdk.dir` 설정 후:

```bash
gradlew.bat :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

서명: 루트에 `keystore.properties` + `lotte-release.jks` (git 제외).

## GitHub 릴리스 (인앱 업데이트)

앱은 `GET https://api.github.com/repos/bossxor/INV_LotteGiants/releases/latest` 로
최신 릴리스를 확인합니다.

1. `app/build.gradle.kts`의 `versionCode` / `versionName` 올리기
2. `assembleRelease` 후 APK를 Release asset으로 업로드 (`*.apk`)
3. **Release body에 반드시** 아래 한 줄 포함 (없으면 업데이트 검사 스킵):

```
versionCode: 2
```

4. `versionCode`가 기기에 설치된 값보다 크면 앱 시작 시 / 설정「업데이트 확인」에서 설치 제안

데이터: 네이버 스포츠 비공식 API (`api-gw.sports.naver.com`).
