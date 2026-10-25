package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.Goal
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.ScorersCalculator
import it.promozionepuglia.gironeb.data.findMatch
import it.promozionepuglia.gironeb.data.form
import it.promozionepuglia.gironeb.data.newsAbout
import it.promozionepuglia.gironeb.data.otherLeg
import it.promozionepuglia.gironeb.data.position
import it.promozionepuglia.gironeb.data.standingOf
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.ResultChip
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.components.formatKickoff
import it.promozionepuglia.gironeb.ui.components.formatNewsDate
import it.promozionepuglia.gironeb.ui.components.liveLabel
import it.promozionepuglia.gironeb.ui.components.openUrl
import it.promozionepuglia.gironeb.ui.theme.AppTheme

/** Dettaglio di una partita: risultato, gol minuto per minuto, confronto, andata/ritorno e notizie. */
@Composable
fun MatchScreen(
    snapshot: LeagueSnapshot,
    round: Int,
    homeKey: String,
    onTeamClick: (String) -> Unit,
    onMatchClick: (Match) -> Unit,
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val m = snapshot.findMatch(round, homeKey)
    if (m == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Partita non trovata nel calendario", color = c.muted)
        }
        return
    }
    val goals = ScorersCalculator.goals(m)
    val other = snapshot.otherLeg(m)
    val news = snapshot.newsAbout(m)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(m, snapshot, onTeamClick) }
        if (m.hasScore) item {
            Card(padding = 14.dp) {
                SectionTitle("Gol")
                when {
                    m.isForfeit -> Muted("Risultato assegnato a tavolino dal Giudice sportivo.")
                    m.homeGoals == 0 && m.awayGoals == 0 -> Muted("Nessun gol finora.".takeIf { m.isLive } ?: "Nessun gol.")
                    goals.isEmpty() -> Muted(m.scorers.ifBlank { "Marcatori non ancora pubblicati." })
                    else -> goals.forEach { GoalRow(it, m) }
                }
                if (m.isLive) Muted("I marcatori compaiono qualche minuto dopo il gol, quando la fonte li pubblica.")
            }
        }
        item { Comparison(m, snapshot) }
        if (other != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (other.round < m.round) "Andata" else "Ritorno",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp),
                )
                MatchCard(other, onTeamClick, onMatchClick)
            }
        }
        if (news.isNotEmpty()) item {
            Card(padding = 14.dp) {
                SectionTitle("Notizie sulla partita")
                news.forEach { item ->
                    Column(Modifier.fillMaxWidth().clickable { openUrl(context, item.url) }.padding(vertical = 8.dp)) {
                        Text(item.title, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${item.source} · ${formatNewsDate(item.date)}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { openUrl(context, LeagueRepository.TUTTOCAMPO_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text("Il girone su Tuttocampo", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Header(m: Match, snapshot: LeagueSnapshot, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    Card {
        Text(
            "${m.round}ª giornata · ${formatKickoff(m.kickoff)}",
            color = c.muted, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BigMatchTeam(m.homeKey, snapshot, onTeamClick, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (m.hasScore) "${m.homeGoals} - ${m.awayGoals}" else "VS",
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        m.isLive -> c.live
                        m.hasScore -> c.text
                        else -> c.muted
                    },
                )
                Text(
                    when {
                        m.isLive -> liveLabel(m)
                        m.isForfeit -> "A TAVOLINO"
                        m.isPlayed -> "FINALE"
                        else -> "DA GIOCARE"
                    },
                    color = if (m.isLive) c.live else c.muted, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            BigMatchTeam(m.awayKey, snapshot, onTeamClick, Modifier.weight(1f))
        }
    }
}

/** Un gol: a sinistra quelli della squadra di casa, a destra quelli degli ospiti. */
@Composable
private fun GoalRow(g: Goal, m: Match) {
    val c = AppTheme.colors
    val detail = buildString {
        append(if (g.ownGoal) "Autogol" else g.scorer)
        if (g.penalty) append(" (rig.)")
    }
    val side = when (g.teamKey) {
        m.homeKey -> TextAlign.Start
        m.awayKey -> TextAlign.End
        else -> TextAlign.Center
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (side == TextAlign.Start) detail else "",
            modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (side == TextAlign.Center) "${g.minute} $detail" else g.minute,
            color = c.accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = if (side == TextAlign.Center) Modifier.weight(2f) else Modifier.width(56.dp),
        )
        Text(
            if (side == TextAlign.End) detail else "",
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le due squadre a confronto, con i dati della classifica. */
@Composable
private fun Comparison(m: Match, snapshot: LeagueSnapshot) {
    val home = snapshot.standingOf(m.homeKey)?.total
    val away = snapshot.standingOf(m.awayKey)?.total
    if (home == null || away == null) return
    val c = AppTheme.colors
    Card(padding = 14.dp) {
        SectionTitle("Le squadre in classifica")
        listOf(
            "Posizione" to ("${snapshot.position(m.homeKey)}º" to "${snapshot.position(m.awayKey)}º"),
            "Punti" to ("${home.points}" to "${away.points}"),
            "Vittorie" to ("${home.won}" to "${away.won}"),
            "Gol fatti" to ("${home.goalsFor}" to "${away.goalsFor}"),
            "Gol subiti" to ("${home.goalsAgainst}" to "${away.goalsAgainst}"),
        ).forEach { (label, values) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(values.first, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(label, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Text(values.second, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                snapshot.form(m.homeKey).forEach { ResultChip(it) }
            }
            Text("Ultime 5", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End)) {
                snapshot.form(m.awayKey).forEach { ResultChip(it) }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}
