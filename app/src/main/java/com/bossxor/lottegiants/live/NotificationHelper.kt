package com.bossxor.lottegiants.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.destination
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_LOGO_URL
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.WinProbPoint
import com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_DEFAULT
import com.bossxor.lottegiants.domain.shouldPostLiveNotification
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.suspendLabel
import com.bossxor.lottegiants.domain.WinProb
import com.bossxor.lottegiants.domain.estimateLotteWinProb
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.teamNameToCode
import com.bossxor.lottegiants.widget.WidgetAssets

object NotificationHelper {

    const val CHANNEL_LIVE = "live_score"
    /**
     * 스코어카드(커스텀 RemoteViews)용. 배경은 시스템 알림색을 그대로 쓰고, 작은 아이콘만 롯데 레드.
     */
    const val CHANNEL_LIVE_CARD = "live_score_card_v4"
    /**
     * Now Bar/Live Update용.
     * v2: One UI 9는 채널이 DEFAULT여도 되지만 HIGH가 칩 노출이 더 잘 된다.
     * 채널 중요도는 생성 후 코드로 못 바꿔서 ID를 올렸다.
     */
    const val CHANNEL_LIVE_NOW = "live_score_nowbar_v2"
    const val CHANNEL_SCORE = "event_score"
    const val CHANNEL_CONCEDE = "event_concede"
    const val CHANNEL_PITCHER = "event_pitcher"
    const val CHANNEL_HOMERUN = "event_homerun"
    const val CHANNEL_CHANCE = "event_chance"
    const val CHANNEL_LEAD = "event_lead"
    const val CHANNEL_INNING = "event_inning"
    const val CHANNEL_EIGHTH = "event_eighth"
    const val CHANNEL_EXTRA = "event_extra"
    const val CHANNEL_GAME = "event_game"
    const val CHANNEL_PREGAME = "event_pregame"
    // v2: 기존 채널은 중요도가 낮아 헤드업으로 뜨지 않았다. 채널 중요도는 생성 후 코드로 못 바꾼다.
    const val CHANNEL_LINEUP = "event_lineup_v2"
    const val CHANNEL_CANCEL = "event_cancel"
    const val CHANNEL_FAVORITE = "event_favorite"
    const val CHANNEL_ROSTER = "event_roster"
    const val CHANNEL_RACE = "event_race"
    const val CHANNEL_ALERT_WATCH = "alert_watch_v1"

    const val LIVE_NOTIFICATION_ID = 1001

    /** 종료·취소 알림은 2시간 뒤 스스로 사라진다 (서비스가 멈춘 뒤에도 남는 걸 막는다). */
    private const val FINISHED_NOTIFICATION_TIMEOUT_MS = 2 * 60 * 60 * 1000L

    private const val COLOR_LOTTE = 0xFFD00F31.toInt()
    private const val COLOR_OPPONENT = 0xFF9AA0A6.toInt()
    private const val COLOR_TRACK = 0xFF4A4F55.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) =
            NotificationChannel(id, name, importance).also { nm.createNotificationChannel(it) }

        ch(CHANNEL_LIVE, "실시간 스코어", NotificationManager.IMPORTANCE_LOW)
        ch(CHANNEL_LIVE_CARD, "실시간 스코어카드", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CHANNEL_LIVE_NOW, "Now Bar 실시간 점수", NotificationManager.IMPORTANCE_HIGH)
        runCatching { nm.deleteNotificationChannel("live_score_nowbar") }
        runCatching { nm.deleteNotificationChannel("live_score_nowbar_v2") }
        runCatching { nm.deleteNotificationChannel("live_score_card_v2") }
        runCatching { nm.deleteNotificationChannel("live_score_card_v3") }
        ch(CHANNEL_SCORE, "득점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CONCEDE, "실점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_PITCHER, "투수 교체")
        ch(CHANNEL_HOMERUN, "홈런", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CHANCE, "득점권 찬스", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_LEAD, "역전/동점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_INNING, "이닝 교대")
        ch(CHANNEL_EIGHTH, "8회말", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_EXTRA, "연장", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_GAME, "경기 시작/종료", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_PREGAME, "경기 30분 전")
        ch(CHANNEL_LINEUP, "선발 라인업", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CANCEL, "경기 취소", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_FAVORITE, "즐겨찾기 선수", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CHANNEL_ROSTER, "엔트리 등말소", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_RACE, "매직·트래직", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CHANNEL_ALERT_WATCH, "엔트리·라인업 감시", NotificationManager.IMPORTANCE_MIN)

        runCatching { nm.deleteNotificationChannel("event_lineup") }
    }

    fun buildAlertWatchNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALERT_WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("엔트리·라인업 감시 중")
            .setContentText("앱을 열지 않아도 공시를 확인합니다")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    /**
     * 실시간 스코어 알림에 올릴 경기.
     * 경기 중이면 그 경기. 오늘 종료·취소는 결과 카드. 예정은 시작 [leadMinutes] 전부터만.
     * 다음 경기(내일)는 창에 들어왔을 때만.
     */
    fun liveNotificationGame(
        snap: LiveSnapshot?,
        allowUpcoming: Boolean = true,
        leadMinutes: Int = LIVE_LEAD_MINUTES_DEFAULT,
        nowMillis: Long = System.currentTimeMillis(),
        ignoreLeadWindow: Boolean = false,
    ): LotteGameInfo? {
        if (snap == null) return null
        val today = kboToday().toString()
        val g = snap.lotteGame
        if (g != null && g.status == GameStatus.LIVE) return g
        if (g != null && (g.gameDate == today || g.gameDate.isBlank())) {
            if (g.status == GameStatus.ENDED || g.status == GameStatus.CANCELED) return g
            if (ignoreLeadWindow || shouldPostLiveNotification(g, leadMinutes, nowMillis)) return g
            return null
        }
        if (allowUpcoming) {
            snap.nextLotteGame
                ?.takeIf { ignoreLeadWindow || shouldPostLiveNotification(it, leadMinutes, nowMillis) }
                ?.let { return it }
        }
        snap.lastLotteGame?.takeIf { it.gameDate == today }?.let { return it }
        return null
    }

    fun cancelLive(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(LIVE_NOTIFICATION_ID)
    }

    /**
     * pinned·lead 창 안이면 알림만 갱신한다. 경기 전 '다시 표시'는 FGS 없이 유지.
     * LIVE일 때만 [LiveScoreService]를 켠다.
     */
    suspend fun refreshLiveNotificationIfNeeded(context: Context) {
        val app = context.applicationContext
        val repo = com.bossxor.lottegiants.data.GiantsRepository.get(app)
        if (!repo.store.isLiveScoreEnabled()) return
        createChannels(app)
        val lead = repo.store.liveLeadMinutes()
        val pinned = repo.store.isLiveNotificationPinned()
        val snap = repo.store.loadSnapshot() ?: return
        val game = liveNotificationGame(
            snap,
            allowUpcoming = true,
            leadMinutes = lead,
            ignoreLeadWindow = pinned,
        ) ?: return
        if (!pinned && !shouldPostLiveNotification(game, lead)) return
        val mode = repo.store.liveDisplayMode()
        val n = buildLiveNotification(app, game, mode, snap.winProbSeries)
        app.getSystemService(NotificationManager::class.java)
            .notify(LIVE_NOTIFICATION_ID, n)
        if (game.status == GameStatus.LIVE) {
            LiveScoreService.start(app, forceShow = pinned)
        }
    }

    fun buildLiveNotification(
        context: Context,
        game: LotteGameInfo?,
        mode: LiveDisplayMode = LiveDisplayMode.LOCK_NOW,
        winProbSeries: List<WinProbPoint> = emptyList(),
    ): Notification {
        val intent = PendingIntent.getActivity(
            context, 0,
            openAppIntent(context, "live", game?.gameId.orEmpty()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val scoreTitle = if (game == null) {
            "롯데 라이브"
        } else {
            "롯데 ${game.lotteScore} : ${game.opponentScore} ${game.opponentName}"
        }
        val summary = gameSummary(game)
        val compactLine = gameCompactLine(game)
        val chipText = nowBarChipText(game)
        val headerLine = if (game != null && game.status == GameStatus.LIVE && !game.isSuspended) {
            buildString {
                append(game.inningLabel)
                append(if (game.isLotteBatting) " · 롯데 공격" else " · 상대 공격")
            }
        } else {
            compactLine
        }

        val (title, text) = when (mode) {
            LiveDisplayMode.STATUS_SCORE -> {
                val shortTitle = if (game == null) "롯데 라이브"
                else "롯데 ${game.lotteScore}:${game.opponentScore}"
                shortTitle to (game?.inningLabel ?: "")
            }
            LiveDisplayMode.FULL -> scoreTitle to summary
            LiveDisplayMode.LOCK_NOW -> scoreTitle to headerLine
        }

        // 모든 표시 모드는 스코어카드. `상세 알림`만 펼칠 때 큰 카드.
        val useScorecard = game != null
        val useBigCard = mode == LiveDisplayMode.FULL
        // 경기가 끝나면 서비스가 멈춰도 알림은 남는다. 손으로 지울 수 있게 두고 스스로 만료시킨다.
        val finished = game != null &&
            (game.status == GameStatus.ENDED || game.status == GameStatus.CANCELED)

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_CARD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(!finished)
            .setAutoCancel(finished)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(COLOR_LOTTE)
        if (finished) builder.setTimeoutAfter(FINISHED_NOTIFICATION_TIMEOUT_MS)

        val hide = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, GameAlarmReceiver::class.java).setAction(GameSchedulerWorker.ACTION_HIDE_LIVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (useScorecard) {
            val compact = buildLiveCompactViews(context, game!!)
            val big = if (useBigCard) buildLiveRemoteViews(context, game, winProbSeries) else compact
            builder
                .setColor(COLOR_LOTTE)
                .setColorized(false)
                .setCustomContentView(compact)
                .setCustomBigContentView(big)
                .setCustomHeadsUpContentView(compact)
                .setDeleteIntent(hide)
                .setSubText(if (mode == LiveDisplayMode.LOCK_NOW) chipText else null)
                .setShortCriticalText(if (mode == LiveDisplayMode.LOCK_NOW) chipText else null)
        } else {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { "예정 경기 없음" }))
                .setDeleteIntent(hide)
        }

        return builder.build()
    }

    /** Now Bar 칩은 대략 7자면 잘린다. 점수는 `3:2`, 전이면 `18:30`. */
    fun nowBarChipText(game: LotteGameInfo?): String {
        val raw = when {
            game == null -> "대기"
            game.status == GameStatus.BEFORE -> {
                val t = game.startTime.trim()
                Regex("""\d{1,2}:\d{2}""").find(t)?.value ?: t.ifBlank { "예정" }
            }
            else -> "${game.lotteScore}:${game.opponentScore}"
        }
        return if (raw.length <= 7) raw else raw.take(7)
    }

    fun canPostNowBar(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    data class NowBarStatus(
        val apiOk: Boolean,
        val canPost: Boolean,
        val livePosted: Boolean,
        val promotable: Boolean,
        val promoted: Boolean,
    )

    fun nowBarStatus(context: Context): NowBarStatus {
        val apiOk = Build.VERSION.SDK_INT >= 36
        val canPost = canPostNowBar(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val posted = nm.activeNotifications.firstOrNull { it.id == LIVE_NOTIFICATION_ID }?.notification
        val sample = posted ?: buildLiveNotification(context, null)
        val promotable = apiOk && runCatching { sample.hasPromotableCharacteristics() }.getOrDefault(false)
        val promoted = apiOk && posted != null &&
            runCatching { posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0 }.getOrDefault(false)
        return NowBarStatus(
            apiOk = apiOk,
            canPost = canPost,
            livePosted = posted != null,
            promotable = promotable,
            promoted = promoted,
        )
    }

    fun nowBarStatusLabel(status: NowBarStatus): String = when {
        !status.apiOk -> "이 기기는 라이브 알림(Now Bar)을 지원하지 않습니다."
        !status.canPost ->
            "잠금화면 칩(Now Bar)이 꺼져 있을 수 있습니다. 아래 설정에서 라이브 알림을 켜 보세요."
        status.promoted -> "Now Bar에 표시 중입니다."
        status.livePosted && status.promotable ->
            "승격 가능한 알림입니다. 칩이 안 보이면 잠금화면 Now bar 목록에서 사직스코어를 켜 보세요."
        status.livePosted && !status.promotable ->
            "지금 알림은 승격 조건에 안 맞습니다. 아래 다시 표시를 눌러 주세요."
        else -> "실시간 스코어를 켜면 잠금화면·상태바 칩에 점수가 올라갑니다."
    }

    fun openNowBarSettings(context: Context): Boolean {
        val pkg = Uri.parse("package:${context.packageName}")
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val candidates = listOf(
            Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                .setData(pkg)
                .addFlags(flags),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(flags),
        )
        for (intent in candidates) {
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** 알림 접힘 상태용 한 줄 */
    private fun gameCompactLine(game: LotteGameInfo?): String {
        if (game == null) return "대기 중"
        if (game.isSuspended) return game.suspendLabel
        if (game.status != GameStatus.LIVE) {
            return game.inningLabel.ifBlank { game.opponentName }
        }
        return buildString {
            append(game.inningLabel)
            append("  ${basesLabel(game)}")
            if (game.currentPitcherName.isNotBlank()) {
                append("  투 ${game.currentPitcherName}")
                if (game.currentPitcherPitchCount > 0) append("(${game.currentPitcherPitchCount})")
            }
            if (game.currentBatterName.isNotBlank()) {
                append("  타 ")
                if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
                append(game.currentBatterName)
            }
        }
    }

    /** 루타앱 경기요약에 가까운 전체 텍스트 (요약 탭과 동일 소스) */
    private fun gameSummary(game: LotteGameInfo?): String {
        if (game == null) return "대기 중"
        if (game.status != GameStatus.LIVE) {
            return buildString {
                append(game.inningLabel.ifBlank { game.statusText.ifBlank { game.opponentName } })
                if (game.stadium.isNotBlank()) append("\n구장  ${game.stadium}")
                if (game.lotteStartingPitcher.isNotBlank() || game.opponentStartingPitcher.isNotBlank()) {
                    append("\n선발  롯데 ${game.lotteStartingPitcher.ifBlank { "-" }}")
                    append("  ·  ${game.opponentName} ${game.opponentStartingPitcher.ifBlank { "-" }}")
                }
            }
        }
        return buildString {
            append(game.inningLabel)
            if (game.isLotteBatting) append("  ·  롯데 공격") else append("  ·  상대 공격")
            append("\n루상  ${basesLabel(game)}")
            append("\n투수  ${game.currentPitcherName.ifBlank { "-" }}")
            if (game.currentPitcherPitchCount > 0) append(" (${game.currentPitcherPitchCount}구)")
            append("\n타자  ")
            if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
            append(game.currentBatterName.ifBlank { "-" })
            if (game.nextBatterName.isNotBlank()) {
                append("\n다음 타자  ${game.nextBatterName}")
            }
            if (game.stadium.isNotBlank()) append("\n구장  ${game.stadium}")
        }
    }

    private fun basesLabel(game: LotteGameInfo): String {
        val parts = buildList {
            if (game.onBase1) {
                add(if (game.runnerOn1Order > 0) "1루(${game.runnerOn1Order}번)" else "1루")
            }
            if (game.onBase2) {
                add(if (game.runnerOn2Order > 0) "2루(${game.runnerOn2Order}번)" else "2루")
            }
            if (game.onBase3) {
                add(if (game.runnerOn3Order > 0) "3루(${game.runnerOn3Order}번)" else "3루")
            }
        }
        return if (parts.isEmpty()) "주자 없음" else parts.joinToString("·")
    }

    private data class CardSides(
        val awayName: String,
        val homeName: String,
        val awayScore: Int,
        val homeScore: Int,
        val awayCode: String,
        val homeCode: String,
        val awayLogoUrl: String,
        val homeLogoUrl: String,
    )

    private fun cardSides(game: LotteGameInfo): CardSides {
        val oppCode = game.opponentCode.ifBlank { teamNameToCode(game.opponentName) }
        val lotteLogo = game.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL }
        val oppLogo = game.opponentLogoUrl.ifBlank { teamLogoUrl(oppCode) }
        return CardSides(
            awayName = if (game.isHome) game.opponentName else "롯데",
            homeName = if (game.isHome) "롯데" else game.opponentName,
            awayScore = if (game.isHome) game.opponentScore else game.lotteScore,
            homeScore = if (game.isHome) game.lotteScore else game.opponentScore,
            awayCode = if (game.isHome) oppCode else LOTTE_TEAM_CODE,
            homeCode = if (game.isHome) LOTTE_TEAM_CODE else oppCode,
            awayLogoUrl = if (game.isHome) oppLogo else lotteLogo,
            homeLogoUrl = if (game.isHome) lotteLogo else oppLogo,
        )
    }

    private fun kickoffTime(game: LotteGameInfo): String {
        val t = game.startTime.trim()
        return Regex("""\d{1,2}:\d{2}""").find(t)?.value ?: t.ifBlank { "예정" }
    }

    private fun statusPillText(game: LotteGameInfo): String = when {
        game.isSuspended -> game.suspendLabel
        game.status == GameStatus.ENDED -> "종료"
        game.status == GameStatus.CANCELED -> game.cancelLabel.ifBlank { "취소" }
        game.status == GameStatus.BEFORE -> kickoffTime(game)
        else -> game.inningLabel.ifBlank { "LIVE" }
    }

    private fun inningPillBackground(game: LotteGameInfo): Int = when {
        game.isSuspended -> R.drawable.notif_inning_pill_gold
        game.status == GameStatus.LIVE -> R.drawable.notif_inning_pill
        game.status == GameStatus.CANCELED -> R.drawable.notif_inning_pill_cancel
        game.status == GameStatus.ENDED -> R.drawable.notif_inning_pill_muted
        else -> R.drawable.notif_inning_pill_gold
    }

    private fun awayStarter(game: LotteGameInfo): String =
        if (game.isHome) game.opponentStartingPitcher else game.lotteStartingPitcher

    private fun homeStarter(game: LotteGameInfo): String =
        if (game.isHome) game.lotteStartingPitcher else game.opponentStartingPitcher

    /** 접힌 알림·헤드업용 한 줄 카드 (큰 카드는 64dp에서 잘린다) */
    private fun buildLiveCompactViews(context: Context, game: LotteGameInfo): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.notification_live_compact)
        val side = cardSides(game)
        rv.setTextViewText(R.id.notif_c_away_score, "${side.awayScore}")
        rv.setTextViewText(R.id.notif_c_home_score, "${side.homeScore}")
        rv.setTextViewText(R.id.notif_c_inning, statusPillText(game))
        rv.setInt(R.id.notif_c_inning, "setBackgroundResource", inningPillBackground(game))

        val live = game.status == GameStatus.LIVE && !game.isSuspended
        val before = game.status == GameStatus.BEFORE
        rv.setViewVisibility(R.id.notif_c_bases, if (live) View.VISIBLE else View.GONE)
        if (live) {
            rv.setImageViewResource(
                R.id.notif_c_bases,
                WidgetAssets.basesDrawable(game.onBase1, game.onBase2, game.onBase3),
            )
        }
        // 끝난 경기는 점수·종료만으로 충분하다. 좁은 한 줄에 승패까지 넣으면 잘린다.
        // 경기 전은 구장(또는 선발)을 오른쪽에 둔다.
        val note = when {
            live -> buildString {
                append("${game.out}아웃")
                if (game.currentBatterName.isNotBlank()) append(" · ${game.currentBatterName}")
            }
            before -> game.stadium.ifBlank {
                val starter = game.lotteStartingPitcher.ifBlank { game.opponentStartingPitcher }
                if (starter.isNotBlank()) "선발 $starter" else ""
            }
            else -> ""
        }
        rv.setViewVisibility(R.id.notif_c_note, if (note.isNotBlank()) View.VISIBLE else View.GONE)
        if (note.isNotBlank()) rv.setTextViewText(R.id.notif_c_note, note)
        rv.setImageViewBitmap(
            R.id.notif_c_away_logo,
            WidgetAssets.loadTeamLogoBitmapCachedOnly(context, side.awayCode, side.awayName),
        )
        rv.setImageViewBitmap(
            R.id.notif_c_home_logo,
            WidgetAssets.loadTeamLogoBitmapCachedOnly(context, side.homeCode, side.homeName),
        )
        return rv
    }

    private fun buildLiveRemoteViews(
        context: Context,
        game: LotteGameInfo,
        winProbSeries: List<WinProbPoint> = emptyList(),
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.notification_live)
        val side = cardSides(game)
        val awayName = side.awayName
        val homeName = side.homeName
        val awayCode = side.awayCode
        val homeCode = side.homeCode
        val awayLogoUrl = side.awayLogoUrl
        val homeLogoUrl = side.homeLogoUrl

        rv.setTextViewText(R.id.notif_away_name, awayName)
        rv.setTextViewText(R.id.notif_home_name, homeName)
        rv.setTextViewText(R.id.notif_away_score, "${side.awayScore}")
        rv.setTextViewText(R.id.notif_home_score, "${side.homeScore}")
        rv.setTextViewText(R.id.notif_inning, statusPillText(game))
        rv.setInt(R.id.notif_inning, "setBackgroundResource", inningPillBackground(game))
        // 루상·BSO는 진행 중일 때만 뜻이 있다. 끝난 경기에 빈 다이아몬드를 두면 주자가 있는 것처럼 읽힌다.
        val showBases = game.status == GameStatus.LIVE && !game.isSuspended
        rv.setViewVisibility(R.id.notif_bso_row, if (showBases) View.VISIBLE else View.GONE)
        if (showBases) {
            rv.setImageViewResource(
                R.id.notif_bases,
                WidgetAssets.basesDrawable(game.onBase1, game.onBase2, game.onBase3),
            )
        }
        val pitcherLine: String
        val batterLine: String
        when (game.status) {
            GameStatus.LIVE -> {
                pitcherLine = buildString {
                    append("투수 ")
                    append(game.currentPitcherName.ifBlank { "-" })
                    if (game.currentPitcherPitchCount > 0) {
                        append(" ")
                        append(game.currentPitcherPitchCount)
                        append("구")
                    }
                }
                batterLine = buildString {
                    append("타자 ")
                    if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
                    append(game.currentBatterName.ifBlank { "-" })
                }
            }
            GameStatus.BEFORE -> {
                pitcherLine = "선발 ${awayStarter(game).ifBlank { "-" }}"
                batterLine = "선발 ${homeStarter(game).ifBlank { "-" }}"
            }
            GameStatus.CANCELED -> {
                pitcherLine = if (game.stadium.isNotBlank()) "구장 ${game.stadium}" else ""
                batterLine = ""
            }
            GameStatus.ENDED -> {
                pitcherLine = buildString {
                    append("승 ")
                    append(game.winPitcherName.ifBlank { "-" })
                    if (game.savePitcherName.isNotBlank()) {
                        append(" · 세 ")
                        append(game.savePitcherName)
                    }
                }
                batterLine = "패 ${game.losePitcherName.ifBlank { "-" }}"
            }
        }
        rv.setTextViewText(R.id.notif_pitcher_line, pitcherLine)
        rv.setTextViewText(R.id.notif_batter_line, batterLine)
        val showWinProb = WinProb.shouldShowWinProbBar(game)
        rv.setViewVisibility(R.id.notif_winprob_row, if (showWinProb) View.VISIBLE else View.GONE)
        fun setDots(ids: IntArray, count: Int, kind: Char) {
            ids.forEachIndexed { i, id ->
                rv.setImageViewResource(id, WidgetAssets.countDot(i < count, kind))
            }
        }
        val ball = if (showBases) game.ball else 0
        val strike = if (showBases) game.strike else 0
        val out = if (showBases) game.out else 0
        setDots(intArrayOf(R.id.notif_b0, R.id.notif_b1, R.id.notif_b2, R.id.notif_b3), ball, 'B')
        setDots(intArrayOf(R.id.notif_s0, R.id.notif_s1, R.id.notif_s2), strike, 'S')
        setDots(intArrayOf(R.id.notif_o0, R.id.notif_o1, R.id.notif_o2), out, 'O')

        val lotteProb = WinProb.resolveDisplayFocusProb(
            game,
            winProbSeries.lastOrNull()?.homeProb,
        ) ?: estimateLotteWinProb(game)
        val (awayProb, homeProb) = WinProb.awayHomeFromFocus(game, lotteProb)
        if (showWinProb) {
            val (awayPct, homePct) = WinProb.displayPercents(awayProb, homeProb)
            rv.setTextViewText(R.id.notif_winprob_left, "${awayName} ${awayPct}%")
            rv.setTextViewText(R.id.notif_winprob_right, "${homePct}% ${homeName}")
            val bar = WidgetAssets.winProbBarBitmap(
                leftProb = awayProb.toFloat(),
                leftColor = WidgetAssets.winProbBarColor(awayCode),
                rightColor = WidgetAssets.winProbBarColor(homeCode),
            )
            rv.setImageViewBitmap(R.id.notif_winprob_bar, bar)
        }

        val awayBmp = WidgetAssets.loadTeamLogoBitmapCachedOnly(context, awayCode, awayName)
        val homeBmp = WidgetAssets.loadTeamLogoBitmapCachedOnly(context, homeCode, homeName)
        rv.setImageViewBitmap(R.id.notif_away_logo, awayBmp)
        rv.setImageViewBitmap(R.id.notif_home_logo, homeBmp)
        return rv
    }

    fun openAppIntent(
        context: Context,
        openTab: String = "live",
        gameId: String = "",
        detailTab: String? = null,
    ): Intent {
        val uri = when (openTab.lowercase()) {
            "entry", "roster" -> Uri.parse("sajik://entry")
            "standings", "standing" -> Uri.parse("sajik://standings")
            else -> {
                val id = gameId.trim()
                val tab = detailTab?.trim().orEmpty()
                when {
                    id.isNotBlank() && tab.isNotBlank() -> Uri.parse("sajik://game/$id?tab=$tab")
                    id.isNotBlank() -> Uri.parse("sajik://game/$id")
                    else -> Uri.parse("sajik://live")
                }
            }
        }
        return Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
            .putExtra(MainActivity.EXTRA_GAME_ID, gameId)
            .apply {
                if (!detailTab.isNullOrBlank()) putExtra(MainActivity.EXTRA_DETAIL_TAB, detailTab)
            }
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
    }

    fun notifyEvent(
        context: Context,
        type: NotificationType,
        title: String,
        text: String,
        id: Int,
        gameId: String = "",
        detailTab: String? = null,
    ) {
        val channel = when (type) {
            NotificationType.SCORE -> CHANNEL_SCORE
            NotificationType.CONCEDING -> CHANNEL_CONCEDE
            NotificationType.PITCHER_CHANGE -> CHANNEL_PITCHER
            NotificationType.HOMERUN -> CHANNEL_HOMERUN
            NotificationType.SCORING_CHANCE -> CHANNEL_CHANCE
            NotificationType.LEAD_CHANGE -> CHANNEL_LEAD
            NotificationType.INNING_CHANGE -> CHANNEL_INNING
            NotificationType.EIGHTH_INNING -> CHANNEL_EIGHTH
            NotificationType.EXTRA_INNINGS -> CHANNEL_EXTRA
            NotificationType.GAME_START, NotificationType.GAME_END -> CHANNEL_GAME
            NotificationType.PREGAME_REMINDER -> CHANNEL_PREGAME
            NotificationType.LINEUP -> CHANNEL_LINEUP
            NotificationType.CANCELED -> CHANNEL_CANCEL
            NotificationType.ROSTER -> CHANNEL_ROSTER
            NotificationType.FAVORITE_AT_BAT,
            NotificationType.FAVORITE_PITCHING,
            NotificationType.FAVORITE_ROSTER,
            -> CHANNEL_FAVORITE
            NotificationType.RACE_NUMBER -> CHANNEL_RACE
        }
        val dest = type.destination()
        val openTab = dest.first
        val tab = detailTab ?: dest.second
        val content = PendingIntent.getActivity(
            context,
            id,
            openAppIntent(context, openTab, gameId, tab),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
