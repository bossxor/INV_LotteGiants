package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

internal enum class SnapshotKind { FULL, LIVE }
internal data class SnapshotIdentity(val team: String, val preferredGame: String, val day: String)
private data class SnapshotRequest(val identity: SnapshotIdentity, val kind: SnapshotKind, val generation: Long)

/** 조회끼리는 대기하지 않고, 게시/디스크 저장 단계만 짧게 직렬화한다. */
internal class SnapshotCoordinator(
    private val read: suspend () -> LiveSnapshot?,
    private val write: suspend (LiveSnapshot, SnapshotIdentity) -> Unit,
    private val identity: suspend () -> SnapshotIdentity,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val publishLock = Mutex()
    private val flight = SingleFlight<SnapshotRequest, LiveSnapshot>()
    private val generation = AtomicLong()
    private val sequence = AtomicLong()
    @Volatile private var memory: LiveSnapshot? = null
    private var liveAt = 0L
    private var fullAt = 0L
    private var gameRevision = 0L
    private var publishedIdentity: SnapshotIdentity? = null

    suspend fun invalidate() = publishLock.withLock {
        generation.incrementAndGet()
        memory = null
        liveAt = 0L; fullAt = 0L; gameRevision = 0L
        publishedIdentity = null
    }

    suspend fun latest(): LiveSnapshot? = publishLock.withLock {
        (memory ?: read()?.also { memory = it })?.takeIf { it.myTeamCode == identity().team }
    }

    suspend fun fresh(kind: SnapshotKind, ttlMs: Long): LiveSnapshot? = publishLock.withLock {
        val snapshot = memory ?: return@withLock null
        val stamp = if (kind == SnapshotKind.FULL) fullAt else liveAt
        snapshot.takeIf { publishedIdentity == identity() && stamp > 0 && clock() - stamp in 0 until ttlMs &&
            !snapshotStaleForKboDay(it.updatedAtMillis) }
    }

    suspend fun refresh(kind: SnapshotKind, force: Boolean, ttlMs: Long,
        fetch: suspend () -> LiveSnapshot): LiveSnapshot {
        if (!force) fresh(kind, ttlMs)?.let { return it }
        val request = SnapshotRequest(identity(), kind, generation.get())
        return flight.run(request) {
            if (!force) fresh(kind, ttlMs)?.let { return@run it }
            val revision = sequence.incrementAndGet()
            val value = fetch()
            publishLock.withLock {
                if (generation.get() != request.generation || identity() != request.identity ||
                    value.myTeamCode != request.identity.team) throw CancellationException("조회 대상 변경")
                val current = memory
                val oldSeq = current?.lotteGame?.recentTexts?.maxOfOrNull { it.seqno } ?: -1
                val newSeq = value.lotteGame?.recentTexts?.maxOfOrNull { it.seqno } ?: -1
                val older = current != null && (revision < gameRevision ||
                    (current.lotteGame?.gameId == value.lotteGame?.gameId && oldSeq >= 0 && newSeq >= 0 && newSeq < oldSeq))
                val next = when {
                    older && kind == SnapshotKind.FULL -> preserveLive(value, current!!)
                    older -> current!!
                    kind == SnapshotKind.LIVE && current != null -> preserveLive(current, value)
                    else -> value
                }
                write(next, request.identity)
                memory = next
                publishedIdentity = request.identity
                if (!older) gameRevision = revision
                val now = clock()
                if (kind == SnapshotKind.FULL) fullAt = now
                if (!older || kind == SnapshotKind.FULL) liveAt = now
                next
            }
        }
    }

    /** 보조 데이터와 새 라이브 상황을 합친다. 라이브 시간으로 전체 자료 TTL을 갱신하지 않는다. */
    private fun preserveLive(full: LiveSnapshot, live: LiveSnapshot): LiveSnapshot {
        val current = live.lotteGame
        val extras = full.lotteGame?.takeIf { it.gameId == current?.gameId }
        val game = if (current != null && extras != null) current.copy(
            preview = extras.preview ?: current.preview,
            lotteRank = extras.lotteRank.takeIf { it > 0 } ?: current.lotteRank,
            opponentRank = extras.opponentRank.takeIf { it > 0 } ?: current.opponentRank,
            keyPlays = current.keyPlays.ifEmpty { extras.keyPlays },
            provisionalMvpName = current.provisionalMvpName.ifBlank { extras.provisionalMvpName },
            provisionalMvpLine = current.provisionalMvpLine.ifBlank { extras.provisionalMvpLine },
            crowdCount = current.crowdCount.ifBlank { extras.crowdCount },
            gameDuration = current.gameDuration.ifBlank { extras.gameDuration },
        ) else current
        return full.copy(updatedAtMillis = maxOf(full.updatedAtMillis, live.updatedAtMillis),
            lotteGame = game, nextLotteGame = full.nextLotteGame?.takeIf { it.gameId != game?.gameId },
            todayLotteGames = live.todayLotteGames, otherGames = live.otherGames,
            winProbSeries = live.winProbSeries, pitchLocations = live.pitchLocations,
            hotColdZone = live.hotColdZone.ifEmpty { if (extras != null) full.hotColdZone else emptyList() })
    }
}
