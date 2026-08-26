package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.data.NotificationType
import java.time.LocalTime

enum class AlertPreset(val label: String) {
    ALL("전부"),
    SCORING("득점·홈런만"),
    FAVORITES("즐겨찾기만"),
}

fun typesForPreset(preset: AlertPreset): Set<NotificationType> = when (preset) {
    AlertPreset.ALL -> NotificationType.entries.toSet()
    AlertPreset.SCORING -> setOf(
        NotificationType.SCORE,
        NotificationType.CONCEDING,
        NotificationType.HOMERUN,
        NotificationType.LEAD_CHANGE,
        NotificationType.GAME_START,
        NotificationType.GAME_END,
        NotificationType.CANCELED,
        NotificationType.PREGAME_REMINDER,
    )
    AlertPreset.FAVORITES -> setOf(
        NotificationType.FAVORITE_AT_BAT,
        NotificationType.FAVORITE_PITCHING,
        NotificationType.FAVORITE_ROSTER,
        NotificationType.GAME_START,
        NotificationType.GAME_END,
        NotificationType.CANCELED,
    )
}

fun isQuietHour(now: LocalTime, startHour: Int, endHour: Int): Boolean {
    val h = now.hour
    if (startHour == endHour) return false
    return if (startHour < endHour) {
        h in startHour until endHour
    } else {
        h >= startHour || h < endHour
    }
}

fun isLiveOnlyExempt(type: NotificationType): Boolean = type in setOf(
    NotificationType.PREGAME_REMINDER,
    NotificationType.LINEUP,
    NotificationType.ROSTER,
    NotificationType.FAVORITE_ROSTER,
    NotificationType.CANCELED,
)

fun shouldEmitAlert(
    typeEnabled: Boolean,
    liveOnly: Boolean,
    gameIsLive: Boolean,
    quietEnabled: Boolean,
    quietStartHour: Int,
    quietEndHour: Int,
    now: LocalTime,
    type: NotificationType,
): Boolean {
    if (!typeEnabled) return false
    if (quietEnabled && isQuietHour(now, quietStartHour, quietEndHour)) return false
    if (liveOnly && !gameIsLive && !isLiveOnlyExempt(type)) return false
    return true
}

fun HotColdZone.toCell(): HotColdCell {
    val z = (if (zone <= 0) 1 else zone) - 1
    return HotColdCell(
        row = z / 3,
        col = z % 3,
        value = ((heat.coerceIn(1, 5) - 3) / 2f),
    )
}

fun HotColdCell.toZone(): HotColdZone {
    val zone = (row.coerceAtLeast(0) * 3 + col.coerceAtLeast(0) + 1).coerceIn(1, 13)
    val heat = ((value + 1f) * 2f + 1f).toInt().coerceIn(1, 5)
    return HotColdZone(zone = zone, heat = heat)
}
