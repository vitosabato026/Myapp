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
}
