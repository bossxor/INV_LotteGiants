# Consolidated EGM Review — `docs/architecture.md`

**Preset:** uninterrupted full flow (all rubrics)  
**Target:** `C:\Users\OJT\Downloads\App\Lotte widget\docs\architecture.md`  
**Semantic split:** no `## Detailed Implementation Plan` → FeatureRange `1..81`, DetailedRange `N/A`  
**GoldfishContext:** none (no in-doc links; user chose “알아서”)  
**EGM_ROOT:** `C:\Users\OJT\.claude\plugins\cache\egm\egm\0.1.1`  
**Stage reports:**  
- `zoom_out_report.md`  
- `comprehension_report.md`  
- `critic_report.md`  
- `intermediate_zoom_out_report.md`  
- `implementation_readiness_report.md`

---

## Executive verdict (critique first)

1. **This file is a 2.0.61 architecture snapshot, not a feature RFC and not an implementation plan.** Scoring it against feature-spec / implementation-readiness standards correctly produces Blockers that are largely **document-type mismatch**.  
2. **Against the live tree (2.0.82) it is stale.** Treating it as “current structure” is the real product risk: alert master switch, postseason focus, WorkManager FAST_POLL path, Schedule fail-backoff, Relay “3 innings” wording.  
3. **As high-altitude architecture prose it is mostly sound** (FULL/LIVE split, pure Reducer, ownership idea) but **Anti-Weeds fails**: TTL constants, mechanism rows, test/LOC changelog noise.

Do **not** rewrite this into a full Alternatives/Goals RFC unless you intend a greenfield feature spec. Prefer: retitle as as-of snapshot, strip weeds, add glossary one-liners, and either refresh to 2.0.82 or add a dated “current deltas” appendix.

---

## Priority action list

### 🔴 Blocker (do first if this doc is meant to describe *current* system)

| ID | Action | Sources |
|---|---|---|
| B1 | Stamp title/body as **as of 2.0.61** *or* refresh content to **2.0.82** so “현재” is true | codebase-details, zoom-out nit |
| B2 | Document **alerts master switch** (`masterEnabled`) on AlertDispatcher/Batch path | codebase-details |
| B3 | If used as implement plan: add Detailed Implementation Plan + file allowlist + contracts — **or** stop calling it an implementation plan | implementation-readiness |

### 🟡 Major

| ID | Action | Sources |
|---|---|---|
| M1 | Move TTL/limit numbers out of ownership altitude; keep policy boundaries only | zoom-out, intermediate |
| M2 | Collapse SingleFlight/TimedSourceCache to one mechanism principle | zoom-out |
| M3 | Strip test counts / LOC / release changelog from structure; keep behavioral comparison only | zoom-out, style |
| M4 | Define or drop: Keubo, 레이스, 공시/등말소, 무음 보완, gated FULL, 10/30/60 mapping | comprehension, intermediate |
| M5 | Add postseason focus + neutral alerts to LIVE/alert narrative | codebase-details |
| M6 | Update watch diagram: AlertWatchService + AlarmPollWorker/WorkManager | codebase-details |
| M7 | Fix Relay “지난 이닝 최대 3” → concurrent fetch Semaphore(3) | codebase-details |
| M8 | Document Schedule 60s fail backoff | codebase-details |
| M9 | Present-tense invariants; stop changelog voice | style, critic |
| M10 | (Only if reclassified as feature-spec) Problem / Goals / Non-Goals / Alternatives / Risks | design, structural |

### 🟢 Nit

| ID | Action | Sources |
|---|---|---|
| N1 | Gloss seqno / DH; rename “새 형식”; drop “삼켰”; dedupe field-verification lines | comprehension, style |
| N2 | WeatherSource ≠ TimedSourceCache implementation note | codebase-details |
| N3 | Calendar-day vs `kboToday()` 00:00–05:00 | codebase-details |
| N4 | Optional one-line extension checklist if maintainers use this as onboarding | implementation-readiness |

---

## Traceability matrix (raw → consolidated)

| Consolidated | Stage findings |
|---|---|
| B1 | codebase-details #1; zoom-out #5; intermediate #5 |
| B2 | codebase-details #2 |
| B3 | implementation-readiness #1–3, #6; codebase-details #7 |
| M1 | zoom-out #1; intermediate #1 |
| M2 | zoom-out #2; intermediate #2 |
| M3 | zoom-out #3; style/critic version noise |
| M4 | comprehension #1–6; intermediate #6–7 |
| M5–M8 | codebase-details #3–6 |
| M9 | style Stage7/9; critic style |
| M10 | design/structural Blockers (type-dependent) |
| Inventive | Stage5: **no false compromise** |

---

## Stage rollup

| Stage | Outcome |
|---|---|
| 5 Zoom-out + Inventive | No Blockers; Anti-Weeds Majors; inventive: no false compromise |
| 6 Comprehension | Paraphrase OK; Major glossary gaps |
| 7 Critic | Fails feature-spec template; strong descriptive architecture |
| 8 Intermediate zoom-out | Stage5 Majors still open; Critic RFC Blockers ≠ Zoom-Out fails |
| 9 Readiness | Not executable plan; **code drift vs 2.0.82 is the sharp finding** |

---

## Counter-argument (what “pass” would wrongly claim)

Claiming the architecture doc is “fine because the code works” ignores that new readers and later agents will treat 2.0.61 prose as current law. Claim that Critic Blockers require a full RFC rewrite overbuilds a descriptive note. Right response: **honest snapshot labeling + drift fixes + weed trim**, not a fake Alternatives section.
