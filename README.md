# 사직스코어 (롯데 자이언츠 실시간)

롯데 자이언츠 중심 KBO 실시간 스코어 앱. 홈 위젯, Now Bar(Live Update), 이벤트 알림, Wear 동반 앱을 지원합니다.

집·회사에서 이어서 한 작업은 **[작업일지.md](작업일지.md)** 에 적는다. 최신이 위. 끝나면 그 파일을 고치고 push.

현재 버전: **`1.3.55`** (`versionCode` **1063**). `app/build.gradle.kts`에서만 올립니다.

패키지: `com.bossxor.lottegiants`. 원격: `https://github.com/bossxor/INV_LotteGiants.git` (private).

## 기능

- **라이브**: 상단 히어로는 한 덩어리(로고·점수·이닝). 우천중단이면 `우천중단 (19:50 예정)`처럼 사유·재개 시각. 경기 중에는 다이아몬드·BSO·투수/타자. 오늘 경기는 가로 카드. 상세는 프리뷰·라인업·요약·중계·기록 탭. 다른 팀 경기도 상세 확인(메인·위젯·알림은 항상 롯데). 하단 **라이브** 탭은 롯데 경기로 돌아옴
- **하단 탭**: 라이브·결과·순위·설정 **이름을 항상 표시**. 선택된 탭은 볼드·롯데 레드
- **중계**: 이닝 칩 + **아웃카운트·타석**으로 묶인 문자중계. 안타·득점·아웃 색 구분. 알림을 누르면 해당 경기 **중계** 탭
- **요약**: 현재 타석, 주요 장면, 루타 하이라이트(있으면 영상 열기), 승률 곡선
- **결과 / 일정**: 일별·월별 경기. 팀을 고르면 그 팀 시즌만. 리스트는 좌우 스와이프로 날짜 이동. 캘린더는 달력 스와이프로 월 이동, 하단 결과 스와이프로 일 이동. DH·순위·취소 사유. 예정 경기는 **홈=파랑, 원정=빨강**
- **KBO 순위** · 타이틀 순위 · 등록/말소 공시 (엔트리·타이틀은 보고 있는 팀 기준). 롯데 카드에 **가을야구 컷·매직/트래직·자력·페이스·홈/원정·연전·다음 5경기**. 카드 공유 가능
- **홈 위젯**: 각 팀 로고 위에 순위, 선발은 `로드리게스 vs 보스` 한 줄, 하단은 `잔여 n`만. 경기 전 카운트다운. 새로고침 시 캐시가 없으면 한 번 받아 옴
- **알림 / Now Bar / Wear OS**
  - 표시 모드 3개: **라이브 바** · **상세 알림** · **점수만**
  - Now Bar 칩은 **라이브 바**·**점수만**이고 **경기 중**일 때만 승격. **상세 알림**은 커스텀 카드라 칩과 동시에 못 씀. 시스템에서 사직스코어 라이브 알림(Now Bar) 허용 필요
  - 펼친 상세 카드 하단에 양 팀 승률 게이지. **다시 표시**는 점수 카드(다음 경기 포함)
  - 매직/트래직 변동 알림에 당일·어제 경기 결과 사유 (`12 → 11 · 롯데, 두산 5:3 승`)
  - 득점 알림에 **누가 어떤 타구로 몇 타점** (`롯데 득점! 전준우 좌전 적시타 · 2타점 · 5:4`)
  - 득점권 알림에 **누가 어떻게** (`전준우 2루타, 득점권` · `2루 황성빈 · 타석 윤동희`)
  - **역전**은 지고 있던 팀이 앞설 때만 (`3:4 → 5:4`). 동점에서의 선취(`0:0 → 0:1`)는 역전이 아님
  - 홈런, 투수 교체, 라인업, 취소, 즐겨찾기 **타석·등판·등말소** 등
  - 갤럭시 워치4(44mm, Wear OS 3) 포함: 워치 앱·타일·시계 컴플리케이션에 같은 롯데 점수. 폰 설치 시 워치 APK가 포함됨
- **설정**: 즐겨찾기 선수, 알림 종류별 토글, 표시 모드, 테마, 자동 업데이트

폴드 커버·ICS 캘린더 구독·TalkBack 전용 레이아웃은 넣지 않는다.

## 데이터 출처

앱은 **KBO 공식 API를 1차 소스**로 쓰고, KBO에 없는 항목만 다른 출처로 보완합니다.

| 구분 | 출처 | 용도 |
|------|------|------|
| **1차 (KBO 공식)** | `koreabaseball.com` | 일정·점수·이닝·BSO·주자·순위·스코어보드·박스스코어·취소 사유·엠블럼 |
| **보완 (네이버 스포츠)** | `api-gw.sports.naver.com` | 실시간 문자중계, 투구 위치, 라인업 상세, 경기 프리뷰, 선수 사진 |
| **보완 (KBO 모바일)** | `m.koreabaseball.com` | 등록/말소 공시 (`GetRoster`) |
| **보완 (Keubo)** | `keubo.kr` | 타이틀 순위, 팀 카드, 선수 시즌 스탯, 등록 말소 이력 |
| **보완 (루타)** | `ruta.co.kr` | 승률 곡선, 하이라이트 문구·링크 (연결 시) |
| **날씨** | Open-Meteo | 구장 날씨 |

점수는 앱·백그라운드 서비스·위젯이 **8초 이내 스냅샷을 재사용**한다. 당겨서 새로고침만 강제 fetch.

### KBO 공식 API (주요 엔드포인트)

- `GetKboGameList` — 당일·기간 일정, 실시간 점수·선발·취소
- `GetTeamRank` — KBO 순위
- `GetScoreBoardScroll` — 이닝별 득점, R/H/E, 관중, 상대전적
- `GetBoxScoreScroll` — 종료 경기 요약·주요 장면

### 네이버에 남겨 둔 것

- LIVE 경기 문자중계·투구 존 차트 (`getRelay`)
- 선발 라인업 상세·교체 타자 성적
- 경기 프리뷰 선수 시즌 성적 (`getPreview`)
- 선수 사진 (네이버 CDN)

> 네이버·Keubo·루타 API는 비공식 엔드포인트입니다. KBO 공식 사이트 이용약관을 준수하며, 상업적 재배포 목적이 아닌 개인 팬 앱 용도로만 사용합니다.

## 빌드

**요구:** JDK 17, Android SDK (`local.properties`에 `sdk.dir`), minSdk 31 / targetSdk 36

네트워크 공유(UNC·한글 경로)에서는 Gradle이 자주 깨진다. 먼저 환경을 올린다.

```powershell
. .\scripts\env.ps1          # JAVA_HOME 탐색, 필요 시 subst L:
.\scripts\build.ps1 -Type debug    # 또는 release
```

`env.ps1`이 `L:\`를 저장소에 붙이면 이후는 `L:\`에서 돌린다.

```powershell
Set-Location L:\
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
adb install -r "L:\app\build\outputs\apk\debug\app-debug.apk"
```

| 산출물 | 경로 |
|--------|------|
| debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| release APK | `app/build/outputs/apk/release/app-release.apk` |

서명: 루트에 `keystore.properties` + `lotte-release.jks` (git 제외).  
CI 서명 시크릿 등록: `.\scripts\setup-ci-signing.ps1`  
**debug/release 모두 이 키로 서명**한다. 디버그 키로 깔린 기기는 서명이 달라 덮어쓰기가 실패하므로 한 번 지운 뒤 다시 설치한다.

릴리스 `mergeReleaseResources`는 NAS 잠금으로 실패하는 경우가 있다. 그때는 debug APK로 실기한다.

APK·`.gradle`·`app/build`·시크릿은 커밋하지 않는다.

## 버전 관리

`app/build.gradle.kts` 한 곳에서만 관리합니다.

| 필드 | 설명 | 현재 |
|------|------|------|
| `versionName` | 사용자에게 보이는 버전 | `1.3.54` |
| `versionCode` | 업데이트 비교용 정수 (배포마다 +1) | `1062` |

기능 배포 시 `versionCode`만 올리고 `versionName`은 유지해도 됩니다.

## 자동 업데이트 (`latest` 채널)

`main` 브랜치 push 시 GitHub Actions가 release APK를 빌드해 고정 태그 **`latest`** 에 덮어씁니다.  
앱 시작 시 `update.json`을 읽어 `versionCode`가 더 크면 APK를 받은 뒤 **앱 내부 PackageInstaller**로 자기 자신을 갱신합니다.  
이 앱이 설치 주체(installer of record)가 된 뒤에는 확인 없이 끝날 수 있고, 아니면 시스템 확인만 한 번 뜹니다.  
외부 파일 앱으로 APK를 열어 설치하는 방식은 쓰지 않습니다. 다만 Android는 Play 스토어 없이 완전히 숨은 설치를 허용하지 않습니다.

이미 **다른 키(디버그 키 등)** 로 깔린 기기는 한 번 삭제한 뒤 릴리스 APK로 다시 설치해야 합니다. 그 다음부터는 앱 안 업데이트로 이어집니다.

### 수동 배포

```powershell
.\scripts\build.ps1 -Type release
.\scripts\publish-latest.ps1 -Notes "변경 내용 요약"
```

### `update.json` 예시

```json
{
  "versionCode": 1062,
  "versionName": "1.3.54",
  "apkFileName": "LotteGiants.apk",
  "notes": "변경 내용"
}
```

- 저장소가 **private**이면 `local.properties` 또는 환경변수에 `GITHUB_TOKEN` 설정 후 빌드
- Android는 Play 스토어 밖에서 완전히 무확인 설치를 막습니다. 앱 내부 설치 세션을 쓰며, 필요할 때만 시스템 확인이 뜹니다.

## 프로젝트 구조 (요약)

```
app/src/main/java/com/bossxor/lottegiants/
  data/          KboOfficialApi, GiantsRepository, SnapshotStore, UpdateChecker …
  domain/        Models, MagicNumber, 중계 그룹핑, 득점·역전 알림 문구
  ui/screens/    라이브, 결과, 순위, 설정 …
  live/          LiveScoreService, NotificationHelper, EventDetector, WearBridge
  widget/        LotteWidget, WidgetAssets
app/src/test/    중계 분류·역전/득점·매직 사유 단위 테스트
wear/            Wear OS 타일·컴플리케이션 (폰 앱과 동기화)
scripts/         env.ps1, build.ps1, publish-latest, CI 서명 설정
.github/workflows/publish-latest.yml
```
