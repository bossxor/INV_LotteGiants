# Stage 6 — Comprehension Test

**Target:** `docs/architecture.md` (FeatureRange 1..81)  
**DetailedRange:** N/A  
**GoldfishContext:** none

## What this proposes

이 문서는 버전 2.0.61 기준으로, Gradle `app` 단일 모듈을 유지한 채 조회·알림·화면 상태의 책임을 클래스별로 나눈 현재 구조를 설명한다. UI/서비스는 `GiantsRepository`를 통해 `SnapshotCoordinator`와 소스·스냅샷 저장소로 연결되고, 별도로 `EventDetector`→`GameEventReducer`→`AlertDispatcher`/`AlertBatch`로 알림을 낸다. 자료별 캐시·요청 공유 정책, 화면 컨트롤러의 요청/세대 소유, 동시성·취소·TTL·커서에 대한 단위 테스트와 한계, 그리고 전적 표시·공시(엔트리) 상태의 2.0.61 규칙을 문서화한다. 별도의 “앞으로 구현할 계획”이 아니라, 이미 적용된 구조와 검증 범위를 고정해 둔 설명서다.

## How the existing system works, per this document

조회는 `SnapshotCoordinator`가 FULL(전체)과 LIVE(경기)를 구분하고, 동일 조건의 진행 중 요청을 공유하며, 네트워크는 게시 잠금 밖에서 돌리고 디스크·메모리 게시만 직렬화한다. 늦은/과거 응답은 세대·팀·선택 경기·날짜·중계 seqno·DH 전환으로 걸러지고, 소스들(`Schedule`/`Relay`/`Player`/`Roster`/`Preview`/`Weather`)과 `SingleFlight`·`TimedSourceCache`가 TTL·공유·취소를 담당한다. 이벤트는 순수 `GameEventReducer`가 커서·알림·정정 계획을 계산하고, `EventDetector`가 잠금·저장·순서를, `AlertDispatcher`/`AlertBatch`가 배치 정책과 시스템 알림을 담당한다. 화면은 `PlayerDetail`/`PlayerRoster`/`Calendar`/`Entry` 컨트롤러와 `LatestRequest`가 선택 변경·취소를 소유하고, `MainViewModel`은 공개 API와 라이브 스냅샷 합치기만 남긴다. 검증은 동시성·가짜 지연 중심이며 실제 API·절전·LIVE 종단 지연은 현장 확인으로 남긴다.

## Findings (egm-comprehension)

1. **Major** — Keubo undefined beside KBO/Naver; only “don’t merge into official roster” stated.  
2. **Major** — “레이스” lock axis undefined in EventDetector.  
3. **Major** — “10/30/60초 정책” referenced as preserved, mapping not in doc.  
4. **Major** — “gated FULL” in comparison table never defined in body.  
5. **Major** — 공시/등말소 lack one-line domain definition.  
6. **Major** — “무음 보완” meaning unclear (no notify vs cursor fill vs correction).  
7. **Nit** — seqno / DH could use one-line gloss.  
8. **Nit** — 2.0.59 `parts` JSON assumed prior knowledge.  
9. **Nit** — GiantsRepository naming / “세대” lack explicit glossary lines.

**Blockers:** none (paraphrase of whole FeatureRange possible).
