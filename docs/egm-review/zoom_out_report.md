# Stage 5 — Initial Zoom Out Report

**Target:** `docs/architecture.md`  
**FeatureRange:** 1..81  
**DetailedRange:** N/A (no Detailed Implementation Plan section)  
**GoldfishContext:** none  
**Mode:** uninterrupted full flow

## Zoom-Out (egm-zoom-out)

**Context sufficiency:** Pass. Single `app` module with separated query/publish/alert/UI ownership; FULL/LIVE contention and late-response races are the root problem.

**Reuse:** Documented components exist in the codebase. No invented utilities.

### Findings

1. **Major** — `## 자료별 소유권` / `## 2.0.61`  
   TTL constants (30s/10min/5min/15min, max 6/3/12, watch 10/30/60s) sit at architecture altitude. Keep ownership + TTL *policy boundaries*; move concrete numbers out of the structure doc.

2. **Major** — ownership table `SingleFlight` / `TimedSourceCache`  
   These are shared mechanisms, not domain owners. Code already nests SingleFlight inside TimedSourceCache. Collapse to one principle line.

3. **Major** — `## 검증과 한계` / `## 2.0.61`  
   Test counts, LOC comparisons, and release changelog dilute root invariants. Keep behavioral comparison table only; move metrics/history elsewhere.

4. **Nit** — `## 이벤트와 정책` / `## 화면 요청`  
   `runBlocking`, `finally` loading, cursor JSON field lists are weeds. Keep contracts: pure Reducer / Detector locks / typed cursor with legacy read-only.

5. **Nit** — Title stamped `2.0.61` while repo is at `2.0.82`  
   Mark explicitly as “as of 2.0.61” snapshot so it is not read as current tree truth.

**Blockers:** none.

## Inventive Catalyst (egm-inventive-catalyst)

**Verdict: No false compromise detected.**

FULL/LIVE split, publish lock outside network, SingleFlight, generation checks, failure≠no-change, EntryDayResult already resolve the main contradictions. Doze/e2e latency called out as verification scope, not accepted design surrender.
