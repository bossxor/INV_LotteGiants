package com.bossxor.lottegiants.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GiantsRepository.get(app)

    private val _snapshot = MutableStateFlow<LiveSnapshot?>(null)
    val snapshot: StateFlow<LiveSnapshot?> = _snapshot.asStateFlow()

    private val _standings = MutableStateFlow<List<TeamStanding>>(emptyList())
    val standings: StateFlow<List<TeamStanding>> = _standings.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pollJob: Job? = null

    init {
        // 캐시 먼저 보여주고 네트워크 갱신
        viewModelScope.launch {
            _snapshot.value = repo.store.loadSnapshot()
        }
        viewModelScope.launch {
            runCatching { repo.fetchStandings() }.onSuccess { _standings.value = it }
        }
    }

    /** 화면이 보이는 동안 폴링 (경기 중 15초, 그 외 60초) */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refreshOnce()
                val live = _snapshot.value?.lotteGame?.status == GameStatus.LIVE
                delay(if (live) 15_000L else 60_000L)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun refreshOnce() {
        runCatching { repo.refreshSnapshot() }
            .onSuccess {
                _snapshot.value = it
                _error.value = null
                WidgetUpdater.updateAll(getApplication())
            }
            .onFailure { _error.value = it.message }
    }

    fun refreshStandings() {
        viewModelScope.launch {
            runCatching { repo.fetchStandings() }.onSuccess { _standings.value = it }
        }
    }
}
