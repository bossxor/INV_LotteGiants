# 사직스코어

**롯데 자이언츠 중심 KBO 실시간 스코어 앱**

홈 위젯 · Now Bar(Live Update) · 이벤트 알림 · Wear OS 동반 앱을 지원합니다.

| | |
|---|---|
| **버전** | `1.3.74` (`versionCode` **1082**) |
| **패키지** | `com.bossxor.lottegiants` |
| **원격** | [bossxor/INV_LotteGiants](https://github.com/bossxor/INV_LotteGiants.git) (private) |

> 집·회사에서 이어서 작업할 때는 **[작업일지.md](작업일지.md)** 를 본다. 최신이 위. 작업이 끝나면 그 파일을 고치고 push.

---

## 목차

- [화면 구성](#화면-구성)
- [알림 · Now Bar · Wear](#알림--now-bar--wear)
- [홈 위젯](#홈-위젯)
- [데이터 출처](#데이터-출처)
- [빌드 · 설치](#빌드--설치)
- [버전 · 자동 업데이트](#버전--자동-업데이트)
- [프로젝트 구조](#프로젝트-구조)

---

## 화면 구성

### 라이브

- 상단 **히어로**는 로고·점수·이닝을 한 덩어리로 표시
- **우천중단**은 `우천중단 (19:50 예정)`처럼 사유·재개 시각 표기 (취소와 구분, LIVE 유지)
- 경기 중: 다이아몬드 · BSO · 투수/타자
- 오늘 경기는 가로 카드, 상세는 **프리뷰 · 라인업 · 요약 · 중계 · 기록** 탭
- 다른 팀 경기도 상세 확인 가능 (메인·위젯·알림은 항상 롯데 기준)
- 하단 **라이브** 탭을 누르면 롯데 경기로 복귀

### 중계 · 요약

| 탭 | 내용 |
|----|------|
| **중계** | 이닝 칩 + 아웃카운트·타석 묶음 문자중계. 안타·득점·아웃 색 구분 |
| **요약** | 현재 타석, 주요 장면, 루타 하이라이트(영상 링크), **승률 곡선** |

승리확률은 네이버 중계 → 루타 → 추정 순으로 소스를 고른다. 초반에 API가 `1:99`처럼 극단값을 주면 점수·이닝 추정에서 너무 벗어나지 않게 보정한다 (1.3.71).

### 결과 · 일정

- 일별·월별 경기 목록. 팀을 고르면 그 팀 시즌만
- 리스트: 좌우 스와이프로 날짜 이동
- 캘린더: 달력 스와이프로 월 이동, 하단 결과 스와이프로 일 이동
- DH · 순위 · 취소 사유 표시
- 예정 경기: **홈=파랑, 원정=빨강**

### 순위 · 엔트리

- **KBO 순위** · 타이틀 순위 · **등록/말소 공시** (엔트리·타이틀은 보고 있는 팀 기준)
- 롯데 카드: 가을야구 컷 · 매직/트래직 · 자력 · 페이스 · 홈/원정 · 연전 · 다음 5경기
- 엔트리 화면: 일자별 **등록·말소** 모두 표시, 변동이 있던 날짜 하이라이트
- 카드 공유 가능

### 하단 탭

**라이브 · 결과 · 순위 · 설정** — 이름을 항상 표시. 선택된 탭은 볼드·롯데 레드.

### 설정

- 즐겨찾기 선수 (동명이인은 **팀·등번호**로 구분)
- 알림 종류별 토글 · 표시 모드 · Now Bar 시작 시각(30분 단위)
- 테마(라이트/다크/시스템) · 자동 업데이트 (앱 시작과 동시에 확인)

> 폴드 커버 · ICS 캘린더 구독 · TalkBack 전용 레이아웃은 범위에 넣지 않는다.

---

## 알림 · Now Bar · Wear

### 표시 모드 (3가지)

| 모드 | 설명 |
|------|------|
| **라이브 바** | Android Live Update(`ProgressStyle`) + One UI Ongoing Activity extras. 잠금화면·AOD·상태바 Now Bar 칩 |
| **상세 알림** | 펼치면 승률 바·투수/타자 큰 카드. **Now Bar 승격 불가** (커스텀 RemoteViews 금지) |
| **점수만** | 짧은 점수 텍스트 + Now Bar 칩 |

### Now Bar

네이버지도가 쓰는 **Ongoing Activity**는 삼성 One UI 7 파트너 API다. 공개 SDK가 없고 패키지 화이트리스트가 필요하다.

S26(One UI 8+, Android 16)은 그 대신 [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)가 Now Bar로 연결된다.

- 설정한 **경기 시작 N분 전**(30분~4시간, 30분 단위, 기본 2시간)부터 표시
- 경기 중에는 항상 표시. 종료 후에는 밀어 지울 수 있음
- 칩은 **라이브 바**·**점수만**. **상세 알림**은 스코어카드라 승격되지 않음
- 시스템에서 **사직스코어 라이브 알림(Now Bar)** 허용 필요
- 칩이 없으면 One UI **개발자 옵션 → 모든 앱의 라이브 알림**을 켜 본다

### 알림 종류 · 딥링크

알림을 누르면 해당 화면으로 바로 이동한다.

| 이벤트 | 이동 | 예시 문구 |
|--------|------|-----------|
| 득점 · 홈런 · 역전 | **중계** 탭 | `롯데 득점! 전준우 좌전 적시타 · 2타점 · 5:4` |
| 득점권 | **중계** 탭 | `전준우 2루타, 득점권` |
| 라인업 | **라인업** 탭 | — |
| 엔트리 변동 | **엔트리** 화면 | 등록·말소 실시간 알림 |
| 매직/트래직 | **순위** 탭 | `12 → 11 · 롯데, 두산 5:3 승` |

- **역전**: 지고 있던 팀이 앞설 때만 (`3:4 → 5:4`). 동점 선취(`0:0 → 0:1`)는 역전이 아님
- 홈런, 투수 교체, 라인업, 취소, 즐겨찾기 **타석·등판·등말소** 등

### 엔트리·라인업 실시간 감시 (1.3.63~)

앱을 열지 않아도 공시를 잡기 위해 **3중 백업**을 둔다.

| 계층 | 역할 | 주기 |
|------|------|------|
| **AlertWatchService** | 포그라운드 감시 (삼성 배터리 최적화 대비) | **20초** (07~24시) |
| **AlarmManager** | fast poll(라인업) + roster poll(엔트리) | 20~30초 |
| **WorkManager** | 전체 스냅샷·알람 재등록 | 15분 (보조) |

### 실시간 스코어 알림 (1.3.64~)

- **다시 표시**: 표시 시작 시간과 무관하게 알림을 고정. 경기 전에는 FGS 없이 알림만 둔다.
- **라이브 바**(1.3.73): Now Bar용 `ProgressStyle` Live Update. 커스텀 스코어카드는 상세 알림만.
- **펼친 알림** (1.3.71, 상세 알림): 로고–점수–다이아몬드–점수–로고 / 투수·BSO·타자 / 승률 바
- **상태표시줄 아이콘**: 야구공 실루엣. 런처는 SAJIK 배지
- 경기 전 FGS를 반복 켜지 않아 Now Bar가 깜빡이지 않게 함 (1.3.68)

### Wear OS

갤럭시 워치4(44mm, Wear OS 3) 포함 — 워치 앱 · 타일 · 시계 컴플리케이션에 같은 롯데 점수. 폰 APK 설치 시 워치 APK가 함께 포함된다.

---

## 홈 위젯

- 각 팀 로고 **위에 순위**, 선발은 `로드리게스 vs 보스` 한 줄
- 하단은 `잔여 n`만 (텍스트 잘림 방지)
- 경기 전 카운트다운
- 새로고침 버튼은 위젯 본문과 클릭이 겹치지 않게 위에 두고, 누르면 **강제 fetch** (1.3.71)
- **경기일 경계는 서울 오전 5시**. 그 시각이 지나면 어제 종료 결과를 버리고 오늘·다음 경기를 보여 준다 (1.3.74). 앱 내부 업데이트 뒤에도 위젯을 다시 그린다.

---

## 데이터 출처

**KBO 공식 API를 1차 소스**로 쓰고, 없는 항목만 다른 출처로 보완한다.

| 우선순위 | 출처 | 용도 |
|----------|------|------|
| **1차** | `koreabaseball.com` | 일정·점수·이닝·BSO·주자·순위·스코어보드·박스스코어·취소 사유·엠블럼 |
| 보완 | `api-gw.sports.naver.com` | 실시간 문자중계, 투구 위치, 라인업 상세, 경기 프리뷰, 선수 사진 |
| 보완 | `m.koreabaseball.com` | 등록/말소 공시 (`GetRoster`) |
| 보완 | `keubo.kr` | 타이틀 순위, 팀 카드, 선수 시즌 스탯, 등록 말소 이력 |
| 보완 | `ruta.co.kr` | 승률 곡선, 하이라이트 문구·링크 |
| 보완 | Open-Meteo | 구장 날씨 |

점수는 앱 · 백그라운드 서비스 · 위젯이 **8초 이내 스냅샷을 재사용**한다. 당겨서 새로고침만 강제 fetch.

### KBO 공식 API (주요)

- `GetKboGameList` — 당일·기간 일정, 실시간 점수·선발·취소
- `GetTeamRank` — KBO 순위
- `GetScoreBoardScroll` — 이닝별 득점, R/H/E, 관중, 상대전적
- `GetBoxScoreScroll` — 종료 경기 요약·주요 장면

### 네이버에 남겨 둔 것

- LIVE 경기 문자중계·투구 존 차트 (`getRelay`)
- 선발 라인업 상세·교체 타자 성적
- 경기 프리뷰 선수 시즌 성적 (`getPreview`)
- 선수 사진 (네이버 CDN)

> 네이버·Keubo·루타 API는 비공식 엔드포인트입니다. KBO 공식 사이트 이용약관을 준수하며, 상업적 재배포 목적이 아닌 **개인 팬 앱** 용도로만 사용합니다.

---

## 빌드 · 설치

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

**서명:** 루트에 `keystore.properties` + `lotte-release.jks` (git 제외).  
CI 서명 시크릿: `.\scripts\setup-ci-signing.ps1`  
debug/release **모두 동일 키**로 서명한다. 디버그 키로 깔린 기기는 서명이 달라 덮어쓰기가 실패하므로 한 번 지운 뒤 다시 설치한다.

릴리스 `mergeReleaseResources`는 NAS 잠금으로 실패하는 경우가 있다. 그때는 debug APK로 실기한다.

**커밋하지 않는 것:** APK · `.gradle` · `app/build` · `_keubo_apk_res` · 시크릿

---

## 버전 · 자동 업데이트

`app/build.gradle.kts` **한 곳**에서만 관리한다.

| 필드 | 설명 | 현재 |
|------|------|------|
| `versionName` | 사용자에게 보이는 버전 | `1.3.74` |
| `versionCode` | 업데이트 비교용 정수 (배포마다 +1) | `1082` |

기능 배포 시 `versionCode`만 올리고 `versionName`은 유지해도 된다.

### `latest` 채널

`main` push 시 GitHub Actions가 release APK를 빌드해 고정 태그 **`latest`** 에 덮어쓴다.

1. **프로세스 시작과 동시에** `latest` 릴리스 본문 `versionCode`를 본다. 이미 최신이면 `update.json`을 받지 않는다 (1.3.72).
2. 더 크면 APK를 GitHub API 에셋으로 받고 **앱 내부 PackageInstaller**로 갱신
3. 앱을 다시 켜면 45초 뒤부터 재확인. 「확인 중」 창은 없고, 받을 때만 진행률을 띄운다.
4. 이 앱이 설치 주체가 되면 확인 없이 끝날 수 있고, 아니면 시스템 확인 한 번

`main` 푸시 후 CI가 `latest`에 APK를 올리기까지는 수 분이 걸린다. 그 전에는 앱이 새 파일을 볼 수 없다.

이미 **다른 키(디버그 키 등)** 로 깔린 기기는 한 번 삭제한 뒤 릴리스 APK로 다시 설치해야 한다.

### 수동 배포

```powershell
.\scripts\build.ps1 -Type release
.\scripts\publish-latest.ps1 -Notes "변경 내용 요약"
```

### `update.json` 예시

```json
{
  "versionCode": 1082,
  "versionName": "1.3.74",
  "apkFileName": "LotteGiants.apk",
  "notes": "변경 내용"
}
```

- 저장소가 **private**이면 `local.properties` 또는 환경변수에 `GITHUB_TOKEN` 설정 후 빌드

---

## 프로젝트 구조

```
app/src/main/java/com/bossxor/lottegiants/
  data/          KboOfficialApi, GiantsRepository, SnapshotStore, UpdateChecker …
  domain/        Models, MagicNumber, WinProb, 중계 그룹핑, 득점·역전 알림 문구
  ui/screens/    라이브, 결과, 순위, 엔트리, 설정 …
  live/          LiveScoreService, AlertWatchService, NotificationHelper, EventDetector …
  widget/        LotteWidget, WidgetAssets
app/src/test/    중계 분류, 역전/득점, 매직 사유, 승률 파싱 단위 테스트
wear/            Wear OS 타일·컴플리케이션 (폰 앱과 동기화)
scripts/         env.ps1, build.ps1, publish-latest, CI 서명 설정
.github/workflows/publish-latest.yml
```
