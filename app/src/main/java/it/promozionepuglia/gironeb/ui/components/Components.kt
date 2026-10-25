package it.promozionepuglia.gironeb.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.ROME
import it.promozionepuglia.gironeb.data.Team
import it.promozionepuglia.gironeb.data.estimatedMinute
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cerchio colorato con la sigla della squadra. */
@Composable
fun TeamBadge(team: Team, size: Dp = 24.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color(team.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Teams.abbreviation(team),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
            maxLines = 1,
        )
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Quadratino V/N/P per il risultato visto da una squadra. */
@Composable
fun ResultChip(result: Char) {
    val c = AppTheme.colors
    val bg = when (result) { 'V' -> c.win; 'N' -> c.draw; else -> c.loss }
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(result.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Ora corrente a Roma, aggiornata periodicamente per far avanzare il minuto delle partite in corso. */
@Composable
fun rememberNow(periodMillis: Long = 15_000): ZonedDateTime {
    val now by produceState(ZonedDateTime.now(ROME)) {
        while (true) {
            delay(periodMillis)
            value = ZonedDateTime.now(ROME)
        }
    }
    return now
}

/** "LIVE · 37'", oppure solo "LIVE" prima del fischio d'inizio. */
@Composable
fun liveLabel(m: Match): String {
    val minute = m.estimatedMinute(rememberNow())
    return if (minute == null) "LIVE" else "LIVE · $minute"
}

/** Apre un link in una Custom Tab, o nell'app esterna (Instagram/Facebook) se installata. */
fun openUrl(context: Context, url: String, preferExternalApp: Boolean = false) {
    val uri = url.toUri()
    if (preferExternalApp) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) { /* ripiega sulla Custom Tab */ }
    }
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private val italian = Locale.ITALIAN

/** "dom, 25/10/26 - 17:00" -> "Dom 25/10 · 17:00". */
fun formatKickoff(raw: String): String {
    val m = Regex("""(\w+),\s*(\d{2}/\d{2})/\d{2}\s*-\s*(\d{1,2}:\d{2})""").find(raw) ?: return raw
    val (day, date, time) = m.destructured
    return "${day.replaceFirstChar { it.titlecase(italian) }} $date · $time"
}

/** Orario della partita, es. "17:00". */
fun kickoffTime(raw: String): String = Regex("""\d{1,2}:\d{2}""").find(raw)?.value ?: "–"

/** Data di una notizia: "2 ore fa", "ieri", "19 ottobre". */
fun formatNewsDate(iso: String, now: LocalDateTime = LocalDateTime.now()): String {
    val date = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return ""
    val minutes = Duration.between(date, now).toMinutes()
    return when {
        minutes < 1 -> "adesso"
        minutes < 60 -> "$minutes min fa"
        minutes < 24 * 60 -> "${minutes / 60} ore fa"
        date.toLocalDate() == now.toLocalDate().minusDays(1) -> "ieri"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", italian))
    }
}

fun formatUpdated(millis: Long): String {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
    return "Aggiornato " + t.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", italian))
}
