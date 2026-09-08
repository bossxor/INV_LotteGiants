package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.FavoritePlayer
import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsBackup(
    val version: Int = 1,
    val favorites: List<FavoritePlayer> = emptyList(),
    val notifications: Map<String, Boolean> = emptyMap(),
    val liveEnabled: Boolean = true,
    val liveMode: String = "",
    val liveLeadMinutes: Int = 120,
    val theme: String = "",
    val widgetOpacity: Int = 100,
    val widgetShowOppLogo: Boolean = true,
    val alertsLiveOnly: Boolean = false,
    val alertVibrate: Boolean = true,
    val quietEnabled: Boolean = false,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 8,
)
