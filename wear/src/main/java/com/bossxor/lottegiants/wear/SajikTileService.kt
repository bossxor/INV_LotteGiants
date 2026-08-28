package com.bossxor.lottegiants.wear

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
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

    override fun onCreate() {
        super.onCreate()
        SnapshotRepository.hydrate(this)
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val snap = SnapshotRepository.current
        val pad = DimensionBuilders.dp(8f)
        val root = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(pad).setEnd(pad).setTop(pad).setBottom(pad)
                            .build(),
                    )
                    .build(),
            )
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder(this, snap.scoreLine.ifBlank { getString(R.string.no_game) })
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build(),
                    )
                    .addContent(
                        Text.Builder(
                            this,
                            snap.inning.ifBlank {
                                when (snap.status) {
                                    "BEFORE" -> snap.startTime.ifBlank { "예정" }
                                    "ENDED" -> "종료"
                                    else -> snap.status.ifBlank { "—" }
                                }
                            },
                        )
                            .setTypography(Typography.TYPOGRAPHY_TITLE3)
                            .setColor(argb(0xFFF0C75A.toInt()))
                            .build(),
                    )
                    .addContent(
                        Text.Builder(
                            this,
                            snap.let {
                                when (it.status) {
                                    "LIVE" -> "${it.bsoLine}  ${it.basesLine}"
                                    else -> it.raceLine.ifBlank { it.opponent.ifBlank { "롯데" } }
                                }
                            },
                        )
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setMaxLines(2)
                            .setColor(argb(0xFFCCCCCC.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .build()

        val layout = LayoutElementBuilders.Layout.Builder().setRoot(root).build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
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
        const val RESOURCES_VERSION = "1"
    }
}
