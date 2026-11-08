# Stage 9 — Implementation Readiness

**Target:** `docs/architecture.md` 1..81  
**DetailedRange:** N/A (no Detailed Implementation Plan)  
**Reviewers:** egm-implementation-readiness, egm-codebase-details, egm-style

## Implementation readiness (egm-implementation-readiness)

**Verdict: Not ready** as an executable plan.

1. **Blocker** — No Detailed Implementation Plan / phases / exit criteria.  
2. **Blocker** — No repository-relative file paths / allowlist.  
3. **Blocker** — Zero-Decision Rule fail: policies without signatures, keys, lock types, error contracts.  
4. **Major** — Ownership/TTL not wired to per-file change lists; cursor schema incomplete.  
5. **Major** — No implementable test plan (class paths, asserts).  
6. **Major** — No extension checklist / rollback / non-goals for safe change.  
7. **Nit** — Version/test counts drift without linked sources of truth.

## Codebase details (egm-codebase-details)

**App now:** 2.0.82 / 2082 · tests ~261 (README). Doc stamped 2.0.61 / 214.

1. **Blocker** — Doc reads as “current” but is a 2.0.61 snapshot.  
2. **Blocker** — Alert master switch (`masterEnabled`) missing from alert path.  
3. **Major** — Postseason focus / neutral alerts omitted.  
4. **Major** — Watch topology stale (AlertWatchService + WorkManager FAST_POLL not shown).  
5. **Major** — Relay “지난 이닝 최대 3개” ≠ code (`Semaphore(3)` concurrency, not max innings).  
6. **Major** — Schedule fail backoff 60s omitted.  
7. **Major** — Not an IR executable plan (type mismatch).  
8. **Major** — Missing: seasonOver roster silence, results default “전체”, reducer eighth/extra keys.  
9. **Nit** — WeatherSource not TimedSourceCache implementation.  
10. **Nit** — Calendar-day vs `kboToday()` midnight–05:00 confusion risk.

**Aligned:** single app module; FULL/LIVE split; most named classes; EntryDayResult/LatestRequest; pure Reducer; Detector mutexes; many TTL numbers.

## Style (egm-style) — implementer voice

1. **Major** — Changelog past tense in invariants.  
2. **Major** — 10/30/60 unmapped.  
3. **Major** — Fixture replay reads as ongoing guarantee.  
4. **Major** — Release counts as architecture contracts.  
5–10. **Nits** — “신선”, “새 형식”, “기존 API”, LOC table, duplicate field caveats, “지연 상세” ambiguity.
