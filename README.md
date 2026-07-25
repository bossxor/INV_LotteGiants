# 롯데 자이언츠 실시간 현황

롯데 전용 KBO 실시간 스코어 앱 + 홈 위젯 + Now Bar(Live Update) + 이벤트 알림.

## 기능

- **라이브**: 점수, 이닝, S/B/O, 루상 주자, 투수/타자/다음 타자, 선발 라인업
- **전체 경기**: 오늘 다른 팀 경기 (이닝·점수만)
- **KBO 순위**
- **홈 위젯**: Glance 위젯, 경기 중 약 12초 폴링
- **Now Bar**: 포그라운드 서비스 ongoing 알림을 Live Update로 승격
- **이벤트 알림 10종**: 득점, 투수교체, 홈런, 득점권, 역전/동점, 이닝교대, 시작/종료, 30분 전, 라인업, 취소

## APK

- `LotteGiantsLive-release.apk` — 서명된 릴리스 빌드 (설치용)
- `LotteGiantsLive-debug.apk` — 디버그 빌드

설치: 폰에서 "출처를 알 수 없는 앱" 허용 후 APK 실행, 또는

```bash
adb install -r LotteGiantsLive-release.apk
```

## 권장 설정 (One UI)

1. 앱 알림 허용
2. **설정 → 배터리 최적화 예외** (실시간 위젯/Now Bar 유지)
3. 홈 화면 길게 누르기 → 위젯 → **롯데 라이브** 추가

## 빌드

JDK 17 + Android SDK 필요. `local.properties`에 `sdk.dir` 설정 후:

```bash
gradlew.bat :app:assembleRelease
```

데이터는 네이버 스포츠 비공식 API (`api-gw.sports.naver.com`)를 사용합니다.
