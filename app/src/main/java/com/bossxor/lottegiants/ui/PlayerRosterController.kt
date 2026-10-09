package com.bossxor.lottegiants.ui

import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class PlayerRosterController(scope: CoroutineScope, private val repo: GiantsRepository) {
    val players = MutableStateFlow<List<EntryPlayer>>(emptyList())
    val loading = MutableStateFlow(false)
    val team = MutableStateFlow("")
    private val requests = LatestRequest(scope)
    fun load(code: String, force: Boolean, backgroundOnly: Boolean) = requests.launch {
        try {
            if (!backgroundOnly) {
                loading.value = true
                val cached = result { repo.store.jerseyRoster(code, kboToday().year) }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) { team.value = code; players.value = cached }
            }
            val list = result { repo.fetchTeamJerseyRoster(code, force) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) { team.value = code; players.value = list }
        } finally { if (isCurrent) loading.value = false }
    }
}
