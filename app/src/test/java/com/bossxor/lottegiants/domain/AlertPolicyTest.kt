package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.data.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class AlertPolicyTest {

    @Test
    fun scoringPresetKeepsScoreAndHomerun() {
        val types = typesForPreset(AlertPreset.SCORING)
        assertTrue(NotificationType.SCORE in types)
        assertTrue(NotificationType.HOMERUN in types)
        assertTrue(NotificationType.LEAD_CHANGE in types)
        assertFalse(NotificationType.INNING_CHANGE in types)
        assertFalse(NotificationType.FAVORITE_AT_BAT in types)
    }

    @Test
    fun favoritesPresetKeepsFavoriteEvents() {
        val types = typesForPreset(AlertPreset.FAVORITES)
        assertTrue(NotificationType.FAVORITE_AT_BAT in types)
        assertTrue(NotificationType.FAVORITE_PITCHING in types)
        assertFalse(NotificationType.SCORE in types)
    }

    @Test
    fun quietHoursWrapMidnight() {
        assertTrue(isQuietHour(LocalTime.of(23, 30), 23, 8))
        assertTrue(isQuietHour(LocalTime.of(2, 0), 23, 8))
        assertFalse(isQuietHour(LocalTime.of(9, 0), 23, 8))
        assertFalse(isQuietHour(LocalTime.of(22, 0), 23, 8))
    }

    @Test
    fun liveOnlyBlocksNonLiveExceptPregame() {
        val now = LocalTime.of(15, 0)
        assertFalse(
            shouldEmitAlert(
                typeEnabled = true,
                liveOnly = true,
                gameIsLive = false,
                quietEnabled = false,
                quietStartHour = 23,
                quietEndHour = 8,
                now = now,
                type = NotificationType.SCORE,
            ),
        )
        assertTrue(
            shouldEmitAlert(
                typeEnabled = true,
                liveOnly = true,
                gameIsLive = false,
                quietEnabled = false,
                quietStartHour = 23,
                quietEndHour = 8,
                now = now,
                type = NotificationType.PREGAME_REMINDER,
            ),
        )
        assertTrue(
            shouldEmitAlert(
                typeEnabled = true,
                liveOnly = true,
                gameIsLive = true,
                quietEnabled = false,
                quietStartHour = 23,
                quietEndHour = 8,
                now = now,
                type = NotificationType.SCORE,
            ),
        )
    }

    @Test
    fun quietHoursSkipEvenWhenLive() {
        assertFalse(
            shouldEmitAlert(
                typeEnabled = true,
                liveOnly = false,
                gameIsLive = true,
                quietEnabled = true,
                quietStartHour = 23,
                quietEndHour = 8,
                now = LocalTime.of(1, 0),
                type = NotificationType.SCORE,
            ),
        )
    }

    @Test
    fun disabledTypeNeverFires() {
        assertFalse(
            shouldEmitAlert(
                typeEnabled = false,
                liveOnly = false,
                gameIsLive = true,
                quietEnabled = false,
                quietStartHour = 23,
                quietEndHour = 8,
                now = LocalTime.of(15, 0),
                type = NotificationType.HOMERUN,
            ),
        )
    }

    @Test
    fun allPresetHasEveryType() {
        assertEquals(NotificationType.entries.toSet(), typesForPreset(AlertPreset.ALL))
    }
}
