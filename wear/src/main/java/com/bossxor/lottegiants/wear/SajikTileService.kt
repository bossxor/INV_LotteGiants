package com.bossxor.lottegiants.wear

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class SajikTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val snap = SnapshotRepository.current
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        Text.Builder(this, snap.scoreLine)
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                            .build(),
                    )
                    .addContent(
                        Text.Builder(this, snap.inning.ifBlank { snap.status.ifBlank { "—" } })
                            .setTypography(Typography.TYPOGRAPHY_TITLE2)
                            .build(),
                    )
                    .addContent(
                        Text.Builder(this, "${snap.bsoLine}  주${snap.basesLine}")
                            .setTypography(Typography.TYPOGRAPHY_BODY2)
                            .build(),
                    )
                    .addContent(
                        Text.Builder(this, snap.matchupLine.ifBlank { snap.opponent })
                            .setMaxLines(2)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build(),
                    )
                    .build(),
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )

    private companion object {
        const val RESOURCES_VERSION = "0"
    }
}
