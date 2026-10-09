package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.FavoritePlayer
internal data class AlertPolicySnapshot(val enabled: Set<NotificationType>, val liveOnly: Boolean,
    val vibrate: Boolean, val quietEnabled: Boolean, val quietStart: Int, val quietEnd: Int,
    val chanceAtBatChange: Boolean, val favorites: List<FavoritePlayer>)
