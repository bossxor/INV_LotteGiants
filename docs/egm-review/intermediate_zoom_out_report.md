# Stage 8 — Intermediate Zoom Out

**Target:** `docs/architecture.md` 1..81  
**Prior:** `zoom_out_report.md`  
**Target delta since Stage 5:** none (document unchanged)

## Verdict vs Zoom-Out Standard

| Criterion | Result |
|---|---|
| Context sufficiency | Pass |
| Root problem focus | Pass |
| Simplicity / YAGNI | Pass |
| Anti-weeds | Fail (Stage 5 Majors untouched) |
| Leverage existing | Pass |

**Overall:** does not pass Zoom-Out Standard until Stage 5 Majors addressed.

Critic Alternatives/Goals Blockers = feature-spec template mismatch, not Zoom-Out altitude defects. Do not expand this snapshot into a full RFC solely to clear Critic.

## Remaining Stage 5 issues

1. **Major** — TTL/limit constants in ownership section  
2. **Major** — SingleFlight/TimedSourceCache listed as domain owners  
3. **Major** — test counts / LOC / changelog in structure doc  
4. **Nit** — runBlocking / finally / JSON field weeds  
5. **Nit** — version stamp vs “current” reading

## New after Comprehension/Critic

6. **Major** — dangling “10/30/60초 정책” without mapping  
7. **Nit** — “gated FULL” undefined in comparison table  
8. **Nit** — Critic RFC gaps should not drive Zoom-Out fails  
9. **Nit** — glossary Majors (Keubo, 레이스, 공시, 무음 보완) optional one-liners
