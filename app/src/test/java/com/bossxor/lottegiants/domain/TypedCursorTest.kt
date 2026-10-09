package com.bossxor.lottegiants.domain

import org.junit.Assert.*
import org.junit.Test
class TypedCursorTest {
    @Test fun version2059JsonArrayMigratesAllFieldsAndLedgers() {
        val old = """{"parts":["g","99","3","2","true,false,true","p","b","LIVE","next","p,q","first;;third","8","false"],"focusScores":{"confirmedTotal":3}}"""
        // Unknown legacy ledger fields are tolerated; named fields and runners still migrate.
        val c = LiveEventCursor.decode(old)
        assertEquals("g", c.gameId); assertEquals(99, c.seqno); assertEquals(3, c.focusScore)
        assertEquals(GameStatus.LIVE, c.status); assertEquals(setOf("p", "q"), c.seenPitcherCodes)
        assertEquals(NamedBases("first", null, "third"), c.chanceBases)
        assertEquals(8, c.inning); assertEquals(false, c.isTop); assertEquals(c, LiveEventCursor.decode(c.encode()))
        assertFalse(c.encode().contains("parts"))
    }
    @Test fun legacyArrayPreservesPendingScoreEventsForSilentEnrichment() {
        val ledger = reconcileScores(ScoreLedger(), emptyList(), 1, 0, 2, true, emptyList()).ledger
        val serialized = kotlinx.serialization.json.Json.encodeToString(ScoreLedger.serializer(), ledger)
        val old = "{\"parts\":[\"g\",\"7\",\"2\",\"0\",\"\",\"\",\"\",\"LIVE\"],\"focusScores\":$serialized}"
        assertEquals(ledger, LiveEventCursor.decode(old).focusScores)
    }
    @Test fun malformedStorageStartsWithEmptyCursor() {
        assertEquals(LiveEventCursor(), LiveEventCursor.decode("{broken"))
    }
    @Test fun typedCursorRetainsScoreEnrichmentState() {
        val ledger = reconcileScores(ScoreLedger(), emptyList(), 1, 0, 2, true, emptyList()).ledger
        val c = LiveEventCursor(gameId = "g", seqno = 7, status = GameStatus.LIVE, focusScores = ledger)
        assertEquals(c, LiveEventCursor.decode(c.encode()))
    }
}
