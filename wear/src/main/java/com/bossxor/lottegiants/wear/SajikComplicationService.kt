package com.bossxor.lottegiants.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

class SajikComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        listener.onComplicationData(buildData())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) buildData(preview = true) else null

    private fun buildData(preview: Boolean = false): ShortTextComplicationData {
        val snap = if (preview) {
            SajikSnapshot(lotteScore = 3, oppScore = 2, inning = "7회말")
        } else {
            SnapshotRepository.current
        }
        val text = if (snap.updatedAt == 0L && snap.status.isBlank()) {
            getString(R.string.no_game)
        } else {
            snap.complicationText
        }
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(text).build(),
        ).build()
    }
}
