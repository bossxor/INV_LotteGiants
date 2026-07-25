package com.bossxor.lottegiants

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.ui.LotteGiantsTheme
import com.bossxor.lottegiants.ui.MainViewModel
import com.bossxor.lottegiants.ui.screens.AllGamesScreen
import com.bossxor.lottegiants.ui.screens.LiveScreen
import com.bossxor.lottegiants.ui.screens.SettingsScreen
import com.bossxor.lottegiants.ui.screens.StandingsScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotifIfNeeded()

        setContent {
            LotteGiantsTheme {
                val snapshot by vm.snapshot.collectAsState()
                val standings by vm.standings.collectAsState()
                val error by vm.error.collectAsState()

                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> {
                                vm.startPolling()
                                if (snapshot?.lotteGame?.status == GameStatus.LIVE) {
                                    LiveScoreService.start(this@MainActivity)
                                }
                            }
                            Lifecycle.Event.ON_STOP -> vm.stopPolling()
                            else -> {}
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                AppScaffold(
                    snapshot = snapshot,
                    standings = standings,
                    error = error,
                    loading = snapshot == null && error == null
                )
            }
        }
    }

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun AppScaffold(
    snapshot: com.bossxor.lottegiants.domain.LiveSnapshot?,
    standings: List<com.bossxor.lottegiants.domain.TeamStanding>,
    error: String?,
    loading: Boolean,
) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("라이브") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("전체") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("순위") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("설정") }
                )
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> LiveScreen(snapshot, error, loading)
                1 -> AllGamesScreen(snapshot)
                2 -> StandingsScreen(standings)
                3 -> SettingsScreen()
            }
        }
    }
}
