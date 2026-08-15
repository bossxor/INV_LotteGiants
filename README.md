# 사직스코어 (롯데 자이언츠 실시간)

롯데 자이언츠 중심 KBO 실시간 스코어 앱. 홈 위젯, Now Bar(Live Update), 이벤트 알림을 지원합니다.

## 기능

- **라이브**: 점수, 이닝, S/B/O, 루상·주자 타순, 투수/타자, 라인업, 프리뷰·요약·중계·기록 탭
- **결과 / 일정**: 일별·월별 경기. 리스트는 좌우 스와이프로 날짜 이동. 캘린더는 달력 스와이프로 월 이동, 하단 결과 스와이프로 일 이동. DH·순위·취소 사유 표시
- **KBO 순위** · 타이틀 순위 · 등록/말소 공시
- **홈 위젯** / **Now Bar** / **이벤트 알림** (득점, 투수교체, 홈런, 라인업 발표, 취소 사유 등)
- **설정**: 즐겨찾기 선수, 테마, 자동 업데이트

## 데이터 출처

앱은 **KBO 공식 API를 1차 소스**로 쓰고, KBO에 없는 항목만 다른 출처로 보완합니다.

| 구분 | 출처 | 용도 |
|------|------|------|
| **1차 (KBO 공식)** | `koreabaseball.com` | 일정·점수·이닝·BSO·주자·순위·스코어보드·박스스코어·취소 사유·엠블럼 |
| **보완 (네이버 스포츠)** | `api-gw.sports.naver.com` | 실시간 문자중계, 투구 위치, 라인업 상세, 경기 프리뷰 |
| **보완 (KBO 모바일)** | `m.koreabaseball.com` | 등록/말소 공시 (`GetRoster`) |
| **보완 (Keubo)** | `keubo.kr` | 타이틀 순위, 팀 카드, 선수 시즌 스탯, 등록 말소 이력 |
| **보완 (루타)** | `ruta.co.kr` | 승률 곡선, 하이라이트 (연결 시) |
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

**요구:** JDK 17, Android SDK (`local.properties`에 `sdk.dir`)

```powershell
# Windows (권장)
.\scripts\build.ps1 -Type release

# 또는 직접
.\gradlew.bat :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

서명: 루트에 `keystore.properties` + `lotte-release.jks` (git 제외).  
CI 서명 시크릿 등록: `.\scripts\setup-ci-signing.ps1`

## 버전 관리

`app/build.gradle.kts` 한 곳에서만 관리합니다.

| 필드 | 설명 | 예시 |
|------|------|------|
| `versionName` | 사용자에게 보이는 버전 | `1.3.11` |
| `versionCode` | 업데이트 비교용 정수 (배포마다 +1) | `1018` |

기능 배포 시 `versionCode`만 올리고 `versionName`은 유지해도 됩니다.

## 자동 업데이트 (`latest` 채널)

`main` 브랜치 push 시 GitHub Actions가 release APK를 빌드해 고정 태그 **`latest`** 에 덮어씁니다.  
앱 시작 시 `update.json`을 읽어 `versionCode`가 더 크면 자동으로 다운로드 후 설치 화면을 띄웁니다.

### 수동 배포

```powershell
.\scripts\build.ps1 -Type release
.\scripts\publish-latest.ps1 -Notes "변경 내용 요약"
```

### `update.json` 예시

```json
{
  "versionCode": 1007,
  "versionName": "1.3.0",
  "apkFileName": "LotteGiants.apk",
  "notes": "변경 내용"
}
```

- 저장소가 **private**이면 `local.properties` 또는 환경변수에 `GITHUB_TOKEN` 설정 후 빌드
- Android는 사용자 확인 없이 조용히 설치할 수 없어, **설치 화면 1회 확인**은 필수입니다

## 프로젝트 구조 (요약)

```
app/src/main/java/com/bossxor/lottegiants/
  data/          KboOfficialApi, GiantsRepository, UpdateChecker …
  domain/        Models, 스냅샷·경기 상태
  ui/screens/    라이브, 결과, 순위, 설정 …
  live/          알림, Now Bar, 이벤트 감지
  widget/        홈 화면 위젯
scripts/         build, publish-latest, CI 서명 설정
.github/workflows/publish-latest.yml
```
