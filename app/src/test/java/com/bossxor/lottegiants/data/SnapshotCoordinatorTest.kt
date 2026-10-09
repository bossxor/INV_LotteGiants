package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SnapshotCoordinatorTest {
    private var selection = SnapshotIdentity("LT", "", kboToday().toString())
    private var disk: LiveSnapshot? = null
    private var writes = 0
    private fun coordinator() = SnapshotCoordinator({ disk }, { snapshot, _ -> disk = snapshot; writes++ }, { selection })
    private fun snapshot(score: Int, team: String = "LT", id: String = "game") = LiveSnapshot(
        updatedAtMillis = System.currentTimeMillis(), myTeamCode = team,
        lotteGame = LotteGameInfo(id, kboToday().toString(), "14:00", "사직", true, "LG", "LG",
            status = GameStatus.LIVE, lotteScore = score))

    @Test fun livePublishesWhileFullFetchIsBlockedAndLateFullKeepsLiveScore() = runBlocking {
        val c = coordinator(); val gate = CompletableDeferred<Unit>()
        val full = async(start = CoroutineStart.UNDISPATCHED) {
            c.refresh(SnapshotKind.FULL, true, 4000) { gate.await(); snapshot(1).copy(widgetRaceLine = "metadata") }
        }
        val live = withTimeout(1000) { c.refresh(SnapshotKind.LIVE, true, 4000) { snapshot(3) } }
        assertFalse(full.isCompleted); assertEquals(3, live.lotteGame!!.lotteScore)
        gate.complete(Unit)
        val merged = full.await()
        assertEquals(3, merged.lotteGame!!.lotteScore); assertEquals("metadata", merged.widgetRaceLine)
        assertEquals(merged, disk)
    }
    @Test fun simultaneousIdenticalRefreshHasOneFetchAndOnePublication() = runBlocking {
        val c = coordinator(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val callers = (1..20).map { async(start = CoroutineStart.UNDISPATCHED) {
            c.refresh(SnapshotKind.LIVE, true, 4000) { calls++; gate.await(); snapshot(2) }
        } }
        gate.complete(Unit); callers.awaitAll()
        assertEquals(1, calls); assertEquals(1, writes)
    }
    @Test fun selectionChangeDuringStorageTransactionCannotPublishMemory() = runBlocking {
        val c = SnapshotCoordinator({ disk }, { _, expected ->
            selection = selection.copy(team = "LG")
            if (selection != expected) throw CancellationException("selection changed in transaction")
        }, { selection })
        try { c.refresh(SnapshotKind.LIVE, true, 0) { snapshot(1) }; fail() }
        catch (_: CancellationException) { }
        assertNull(disk); assertNull(c.latest())
    }
    @Test fun teamSwitchInvalidatesResponseBeforeDiskWrite() = runBlocking {
        val c = coordinator(); val gate = CompletableDeferred<Unit>()
        val old = async(start = CoroutineStart.UNDISPATCHED) { c.refresh(SnapshotKind.FULL, true, 4000) { gate.await(); snapshot(1) } }
        selection = selection.copy(team = "LG"); c.invalidate(); gate.complete(Unit)
        try { old.await(); fail("old team published") } catch (_: CancellationException) { }
        assertEquals(0, writes); assertNull(c.latest())
    }
    @Test fun preferredGameSwitchInvalidatesResponseWithoutExplicitClear() = runBlocking {
        val c = coordinator(); val gate = CompletableDeferred<Unit>()
        val old = async(start = CoroutineStart.UNDISPATCHED) { c.refresh(SnapshotKind.LIVE, true, 4000) { gate.await(); snapshot(1) } }
        selection = selection.copy(preferredGame = "DH2"); gate.complete(Unit)
        try { old.await(); fail("old game published") } catch (_: CancellationException) { }
        assertEquals(0, writes)
    }
    @Test fun lateFullFromFirstGameCannotReplaceSecondGame() = runBlocking {
        val c = coordinator(); val gate = CompletableDeferred<Unit>()
        val full = async(start = CoroutineStart.UNDISPATCHED) { c.refresh(SnapshotKind.FULL, true, 4000) { gate.await(); snapshot(1, id = "DH1") } }
        c.refresh(SnapshotKind.LIVE, true, 4000) { snapshot(2, id = "DH2") }
        gate.complete(Unit); assertEquals("DH2", full.await().lotteGame!!.gameId)
    }
    @Test fun completedSnapshotIsReusedUntilForced() = runBlocking {
        val c = coordinator(); var calls = 0
        repeat(3) { c.refresh(SnapshotKind.LIVE, false, 4000) { calls++; snapshot(2) } }
        assertEquals(1, calls)
        c.refresh(SnapshotKind.LIVE, true, 4000) { calls++; snapshot(3) }
        assertEquals(2, calls)
    }
    @Test fun livePublicationDoesNotMarkFullMetadataFresh() = runBlocking {
        val c = coordinator()
        c.refresh(SnapshotKind.LIVE, true, 4000) { snapshot(2) }
        assertNull(c.fresh(SnapshotKind.FULL, 60000)); assertNotNull(c.fresh(SnapshotKind.LIVE, 4000))
    }
    @Test fun staleRelaySequenceCannotRegressCurrentGame() = runBlocking {
        val c = coordinator()
        fun at(seq: Int, score: Int) = snapshot(score).let { it.copy(lotteGame = it.lotteGame!!.copy(recentTexts = listOf(RelayText(seq, "", 1, 1, true)))) }
        c.refresh(SnapshotKind.LIVE, true, 0) { at(20, 3) }
        assertEquals(3, c.refresh(SnapshotKind.FULL, true, 0) { at(10, 1) }.lotteGame!!.lotteScore)
    }
}
