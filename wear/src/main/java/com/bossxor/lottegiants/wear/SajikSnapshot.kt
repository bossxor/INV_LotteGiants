package com.bossxor.lottegiants.wear

object WearPaths {
    const val SNAPSHOT = "/sajik/snapshot"
    const val EVENT = "/sajik/event"
}

data class SajikSnapshot(
    val updatedAt: Long = 0L,
    val status: String = "",
    val lotteScore: Int = 0,
    val oppScore: Int = 0,
    val opponent: String = "",
    val inning: String = "",
    val ball: Int = 0,
    val strike: Int = 0,
    val out: Int = 0,
    val on1: Boolean = false,
    val on2: Boolean = false,
    val on3: Boolean = false,
    val pitcher: String = "",
    val batter: String = "",
    val nextBatter: String = "",
    val lotteBatting: Boolean = false,
    val highlight: String = "",
    val startTime: String = "",
    val starterLotte: String = "",
    val starterOpp: String = "",
    val rank: Int = 0,
    val remaining: Int = 0,
    val raceLine: String = "",
) {
    val scoreLine: String get() = "$lotteScore:$oppScore"
    val complicationText: String get() = "$scoreLine ${inning.ifBlank { "—" }}"
    val bsoLine: String get() = "B$ball S$strike O$out"
    val basesLine: String get() = buildString {
        append(if (on1) "1" else "·")
        append(if (on2) "2" else "·")
        append(if (on3) "3" else "·")
    }
    val matchupLine: String
        get() = when {
            pitcher.isNotBlank() && batter.isNotBlank() -> "$pitcher vs $batter"
            highlight.isNotBlank() -> highlight
            opponent.isNotBlank() -> opponent
            else -> ""
        }

    companion object {
        val EMPTY = SajikSnapshot()

        fun fromDataMap(map: com.google.android.gms.wearable.DataMap): SajikSnapshot = SajikSnapshot(
            updatedAt = map.getLong("updatedAt"),
            status = map.getString("status").orEmpty(),
            lotteScore = map.getInt("lotteScore"),
            oppScore = map.getInt("oppScore"),
            opponent = map.getString("opponent").orEmpty(),
            inning = map.getString("inning").orEmpty(),
            ball = map.getInt("ball"),
            strike = map.getInt("strike"),
            out = map.getInt("out"),
            on1 = map.getBoolean("on1"),
            on2 = map.getBoolean("on2"),
            on3 = map.getBoolean("on3"),
            pitcher = map.getString("pitcher").orEmpty(),
            batter = map.getString("batter").orEmpty(),
            nextBatter = map.getString("nextBatter").orEmpty(),
            lotteBatting = map.getBoolean("lotteBatting"),
            highlight = map.getString("highlight").orEmpty(),
            startTime = map.getString("startTime").orEmpty(),
            starterLotte = map.getString("starterLotte").orEmpty(),
            starterOpp = map.getString("starterOpp").orEmpty(),
            rank = map.getInt("rank"),
            remaining = map.getInt("remaining"),
            raceLine = map.getString("raceLine").orEmpty(),
        )
    }
}
