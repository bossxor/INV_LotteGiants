package com.bossxor.lottegiants.domain

import kotlinx.serialization.Serializable

@Serializable
data class ScoreRecord(val id: Int, val play: ScoringPlay, val active: Boolean = true)

@Serializable
data class ScoreLedger(val baseline: Int = -1, val records: List<ScoreRecord> = emptyList())

data class ScoreUpdate(val record: ScoreRecord, val silent: Boolean, val correction: Boolean = false)
data class ScoreReconciliation(val ledger: ScoreLedger, val updates: List<ScoreUpdate>, val supersededIds: List<Int> = emptyList())

/** 순서가 뒤바뀐 중계와 여러 조회로 나뉜 득점을 같은 알림 ID로 보완한다. */
fun reconcileScores(ledger: ScoreLedger, texts: List<RelayText>, lastSeqno: Int,
    previous: Int, current: Int, isHome: Boolean, roster: List<String>): ScoreReconciliation {
    val baseline = if (ledger.baseline >= 0) ledger.baseline else previous.coerceAtLeast(0)
    val records = ledger.records.toMutableList()
    val updates = mutableListOf<ScoreUpdate>()
    val superseded = mutableListOf<Int>()
    if (current < previous) {
        for (i in records.indices) {
            val r = records[i]
            if (r.active && r.play.endScore > current) {
                val corrected = r.copy(active = false)
                records[i] = corrected
                updates += ScoreUpdate(corrected, silent = true, correction = true)
            }
        }
    }
    if (current > baseline) {
        val plays = scoringPlays(texts, lastSeqno, baseline, current, isHome, roster)
        for (play in plays) {
            // 이미 확인한 인과관계가 없는 점수 선도착 알림은 새 상세가 올 때까지 유지한다.
            if (current == previous && play.who == null && play.how == null && play.scorers.isEmpty()) continue
            var index = records.indexOfFirst { it.play.seqno == play.seqno && it.play.inning == play.inning &&
                it.play.isTop == play.isTop && it.play.how != null && play.how != null }
            if (index < 0) index = records.indexOfFirst { it.play.startScore < play.endScore &&
                play.startScore < it.play.endScore && (it.play.how == null || play.how != null) }
            val previousRecord = records.getOrNull(index)
            val pendingOverlap = ledger.records.any { it.play.how == null &&
                it.play.startScore < play.endScore && play.startScore < it.play.endScore }
            if (previousRecord == null && play.endScore <= previous && !pendingOverlap) continue
            if (previousRecord != null && previousRecord.play.how != null && play.how == null) continue
            val next = ScoreRecord(previousRecord?.id ?: (play.startScore + 1), play)
            if (next != previousRecord) {
                if (index >= 0) records[index] = next else records += next
                updates += ScoreUpdate(next, silent = previousRecord != null || pendingOverlap)
            }
            // 한 플레이의 나머지 점수를 먼저 별도 임시 알림으로 받았으면 상세 확인 뒤 합친다.
            if (play.how != null) {
                val absorbed = records.filter { it.id != next.id && it.play.how == null &&
                    it.play.startScore >= play.startScore && it.play.endScore <= play.endScore }
                superseded += absorbed.map { it.id }
                records.removeAll(absorbed.toSet())
            }
        }
    }
    return ScoreReconciliation(ScoreLedger(baseline, records.takeLast(60)), updates, superseded.distinct())
}
