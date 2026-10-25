package it.promozionepuglia.gironeb.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fuso degli orari pubblicati dalla fonte. */
val ROME: ZoneId = ZoneId.of("Europe/Rome")

private val kickoffPattern = Regex("""(\d{2})/(\d{2})/(\d{2})\s*-\s*(\d{1,2}):(\d{2})""")

/** Inizio della partita da "dom, 25/10/26 - 17:00". */
fun Match.kickoffAt(): ZonedDateTime? {
    val (d, mo, y, h, mi) = kickoffPattern.find(kickoff)?.destructured ?: return null
    return runCatching {
        LocalDateTime.of(2000 + y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(ROME)
    }.getOrNull()
}

/**
 * Minuto di gioco stimato dall'orario d'inizio (la fonte non lo pubblica):
 * due tempi da 45' con 15' di intervallo. Null prima del fischio d'inizio.
 */
fun Match.estimatedMinute(now: ZonedDateTime): String? {
    val start = kickoffAt() ?: return null
    val elapsed = Duration.between(start, now).toMinutes()
    return when {
        elapsed < 0 -> null
        elapsed < 45 -> "${elapsed + 1}'"
        elapsed < 48 -> "45'+"
        elapsed < 63 -> "Intervallo"
        elapsed < 108 -> "${elapsed - 17}'"
        else -> "90'+"
    }
}

object LivePolling {
    /** Aggiornamento durante le partite in corso. */
    const val LIVE_MILLIS = 30_000L

    /** Con l'app chiusa si controlla un po' meno spesso, per la batteria. */
    const val BACKGROUND_LIVE_MILLIS = 60_000L

    /** Attorno al fischio d'inizio, per accorgersi subito che una partita è partita. */
    const val KICKOFF_MILLIS = 60_000L

    /** Al massimo si aspetta tanto prima di ricontrollare, se ci sono partite in arrivo. */
    const val MAX_WAIT_MILLIS = 30 * 60_000L

    private val beforeKickoff = Duration.ofMinutes(20)
    private val afterKickoff = Duration.ofHours(3)
    private val horizon = Duration.ofHours(12)

    /**
     * Fra quanto riscaricare i dati mentre l'app è aperta, o null se non serve:
     * nessuna partita in corso, appena iniziata o in programma entro 12 ore.
     */
    fun nextDelayMillis(snapshot: LeagueSnapshot, now: ZonedDateTime): Long? {
        if (snapshot.matches.any { it.isLive }) return LIVE_MILLIS
        val untilWindow = snapshot.matches
            .filter { !it.isPlayed }
            .mapNotNull { it.kickoffAt() }
            .filter { now.isBefore(it.plus(afterKickoff)) && it.minus(horizon).isBefore(now) }
            .minOfOrNull { Duration.between(now, it.minus(beforeKickoff)).toMillis() }
            ?: return null
        return untilWindow.coerceIn(KICKOFF_MILLIS, MAX_WAIT_MILLIS)
    }

    /**
     * Fra quanto controllare con l'app chiusa, per le notifiche: come [nextDelayMillis] ma al massimo
     * ogni minuto, e se non c'è nulla a breve, all'avvicinarsi della prossima partita in calendario.
     */
    fun backgroundDelayMillis(snapshot: LeagueSnapshot, now: ZonedDateTime): Long? {
        nextDelayMillis(snapshot, now)?.let { return maxOf(it, BACKGROUND_LIVE_MILLIS) }
        return snapshot.matches
            .filter { !it.isPlayed }
            .mapNotNull { it.kickoffAt()?.minus(beforeKickoff) }
            .filter { it.isAfter(now) }
            .minOfOrNull { Duration.between(now, it).toMillis() }
    }
}

/** Un nuovo gol da notificare; [goal] è null se la fonte non ha ancora scritto il marcatore. */
data class GoalAlert(val match: Match, val goal: Goal?) {
    /** Identifica il risultato, per non notificare due volte lo stesso gol. */
    val key: String get() = "${match.round}-${match.homeKey}-${match.awayKey}-${match.homeGoals}-${match.awayGoals}"
}

object GoalDetector {
    private val recent = Duration.ofHours(4)

    /**
     * Le partite in diretta (o appena finite) il cui punteggio è salito rispetto all'aggiornamento
     * precedente. Senza dati precedenti non si notifica nulla (primo avvio), e nemmeno i gol annullati
     * o i risultati di partite non seguite in diretta, ad esempio aprendo l'app giorni dopo.
     */
    fun newGoals(previous: LeagueSnapshot?, current: LeagueSnapshot, now: ZonedDateTime): List<GoalAlert> {
        if (previous == null) return emptyList()
        val before = previous.matches.associateBy { Triple(it.round, it.homeKey, it.awayKey) }
        return current.matches.mapNotNull { m ->
            if (!m.hasScore || m.isForfeit) return@mapNotNull null
            val old = before[Triple(m.round, m.homeKey, m.awayKey)] ?: return@mapNotNull null
            if (!m.isLive && !old.isLive) return@mapNotNull null
            val start = m.kickoffAt() ?: return@mapNotNull null
            if (now.isAfter(start.plus(recent))) return@mapNotNull null
            val oldTotal = (old.homeGoals ?: 0) + (old.awayGoals ?: 0)
            if (m.homeGoals!! + m.awayGoals!! <= oldTotal) return@mapNotNull null
            val goals = ScorersCalculator.goals(m)
            // Il marcatore si indica solo se il tabellino è già allineato al risultato.
            GoalAlert(m, goals.lastOrNull()?.takeIf { goals.size == m.homeGoals + m.awayGoals })
        }
    }
}
