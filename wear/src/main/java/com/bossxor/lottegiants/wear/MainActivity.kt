package com.bossxor.lottegiants.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SnapshotRepository.hydrate(this)
        lifecycleScope.launch { loadSnapshot() }
        setContent {
            MaterialTheme {
                val snap by SnapshotRepository.snapshot.collectAsState()
                Scaffold(timeText = { TimeText() }) {
                    SajikSummaryScreen(snap)
                }
            }
        }
    }

    private suspend fun loadSnapshot() {
        runCatching {
            Wearable.getDataClient(this)
                .getDataItems()
                .await()
                .use { buffer ->
                    buffer.forEach { item ->
                        if (item.uri.path == WearPaths.SNAPSHOT) {
                            SnapshotRepository.updateFromDataMap(
                                this,
                                DataMapItem.fromDataItem(item).dataMap,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun SajikSummaryScreen(snap: SajikSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (snap.updatedAt == 0L && snap.status.isBlank()) {
            Text(
                text = stringResource(R.string.no_game),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1,
            )
            return@Column
        }
        Text(
            text = if (snap.opponent.isBlank()) "롯데" else "롯데 vs ${snap.opponent}",
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = snap.scoreLine,
            fontSize = 34.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = snap.inning.ifBlank {
                when (snap.status) {
                    "BEFORE" -> snap.startTime.ifBlank { "예정" }
                    "ENDED" -> "종료"
                    "CANCELED" -> "취소"
                    else -> snap.status.ifBlank { "—" }
                }
            },
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        if (snap.status == "LIVE") {
            Text(
                text = "${snap.bsoLine}  ${snap.basesLine}",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
            )
        }
        val matchup = snap.matchupLine
        if (matchup.isNotBlank()) {
            Text(
                text = matchup,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
