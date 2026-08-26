# 사직스코어 (롯데 자이언츠 실시간)

롯데 자이언츠 중심 KBO 실시간 스코어 앱. 홈 위젯, Now Bar(Live Update), 이벤트 알림, Wear 동반 앱을 지원합니다.

현재 버전: **`1.3.30`** (`versionCode` **1037**). `app/build.gradle.kts`에서만 올립니다.

## 기능

- **라이브**: 점수판이 화면 상단(큰 점수). 상세는 세그먼트 탭. 다른 팀 경기도 상세 확인(메인·위젯·알림은 항상 롯데). 하단 **라이브** 탭은 롯데 경기로 돌아옴
- **중계**: 이닝 칩 + **아웃카운트·타석**으로 묶인 문자중계. 안타·득점·아웃 색 구분. 알림을 누르면 해당 경기 **중계** 탭
- **요약**: 현재 타석, 주요 장면, 루타 하이라이트(있으면 영상 열기), 승률 곡선
- **결과 / 일정**: 일별·월별 경기. 팀을 고르면 그 팀 시즌만. 리스트는 좌우 스와이프로 날짜 이동. 캘린더는 달력 스와이프로 월 이동, 하단 결과 스와이프로 일 이동. DH·순위·취소 사유 표시
- **KBO 순위** · 타이틀 순위 · 등록/말소 공시 (엔트리·타이틀은 보고 있는 팀 기준)
- **홈 위젯** / **Now Bar** / **이벤트 알림** / **Wear OS**
  - Now Bar 지원 폰에서는 표시 모드(라이브 바·상세·점수만)와 관계없이 점수 칩이 잠금화면·상태바에 표시됨. 시스템에서 Now Bar 앱 허용 필요
  - 갤럭시 워치4(44mm, Wear OS 3) 포함: 워치 앱·타일·시계 컴플리케이션에 같은 롯데 점수. 폰 설치 시 워치 APK가 포함됨
  - 득점 알림에 **누가 몇 점** (`롯데 득점! 전준우 2점 · 5:4`)
  - **역전**은 지고 있던 팀이 앞설 때만 (`3:4 → 5:4`). 동점에서의 선취(`0:0 → 0:1`)는 역전이 아님
  - 홈런, 투수 교체, 라인업, 취소, 즐겨찾기 **타석·등판·등말소** 등
- **설정**: 즐겨찾기 선수, 알림 종류별 토글, 테마, 자동 업데이트

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

**요구:** JDK 17, Android SDK (`local.properties`에 `sdk.dir`), minSdk 31

```powershell
# Windows (권장)
.\scripts\build.ps1 -Type release

# 또는 직접
.\gradlew.bat :app:assembleRelease

# 단위 테스트
.\gradlew.bat :app:testReleaseUnitTest
```

APK: `app/build/outputs/apk/release/app-release.apk`

서명: 루트에 `keystore.properties` + `lotte-release.jks` (git 제외).  
CI 서명 시크릿 등록: `.\scripts\setup-ci-signing.ps1`  
debug/release 모두 이 키로 서명합니다. 디버그 키로 설치하면 GitHub 업데이트가 서명이 달라 실패합니다.

## 버전 관리

`app/build.gradle.kts` 한 곳에서만 관리합니다.

| 필드 | 설명 | 현재 |
|------|------|------|
| `versionName` | 사용자에게 보이는 버전 | `1.3.30` |
| `versionCode` | 업데이트 비교용 정수 (배포마다 +1) | `1037` |

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
  "versionCode": 1037,
  "versionName": "1.3.30",
  "apkFileName": "LotteGiants.apk",
  "notes": "변경 내용"
}
```

- 저장소가 **private**이면 `local.properties` 또는 환경변수에 `GITHUB_TOKEN` 설정 후 빌드
- Android는 Play 스토어 밖에서 완전히 무확인 설치를 막습니다. 앱 내부 설치 세션을 쓰며, 필요할 때만 시스템 확인이 뜹니다.

## 프로젝트 구조 (요약)

```
app/src/main/java/com/bossxor/lottegiants/
  data/          KboOfficialApi, GiantsRepository, UpdateChecker …
  domain/        Models, 중계 그룹핑, 득점·역전 알림 문구
  ui/screens/    라이브, 결과, 순위, 설정 …
  live/          알림, Now Bar, 이벤트 감지
  widget/        홈 화면 위젯
app/src/test/    중계 분류·역전/득점 문구 단위 테스트
wear/            Wear OS 타일·컴플리케이션 (폰 앱과 동기화)
scripts/         build, publish-latest, CI 서명 설정
.github/workflows/publish-latest.yml
```
