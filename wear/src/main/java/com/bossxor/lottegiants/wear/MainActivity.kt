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
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { loadSnapshot() }
        setContent {
            MaterialTheme {
                val snap by SnapshotRepository.snapshot.collectAsState()
                SajikSummaryScreen(snap)
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
                            SnapshotRepository.updateFromDataMap(DataMapItem.fromDataItem(item).dataMap)
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (snap.updatedAt == 0L && snap.status.isBlank()) {
            Text(
                text = stringResource(R.string.no_game),
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        Text(
            text = snap.scoreLine,
            style = MaterialTheme.typography.display1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = snap.inning.ifBlank { snap.status },
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${snap.bsoLine}  주${snap.basesLine}",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
        )
        val matchup = snap.matchupLine
        if (matchup.isNotBlank()) {
            Text(
                text = matchup,
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
