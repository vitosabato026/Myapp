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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.Goal
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.NewsItem
import it.promozionepuglia.gironeb.data.ScorersCalculator
import it.promozionepuglia.gironeb.data.Tuttocampo
import it.promozionepuglia.gironeb.data.isPastFirstHalf
import it.promozionepuglia.gironeb.data.findMatch
import it.promozionepuglia.gironeb.data.form
import it.promozionepuglia.gironeb.data.halfTimeScore
import it.promozionepuglia.gironeb.data.newsAbout
import it.promozionepuglia.gironeb.data.otherLeg
import it.promozionepuglia.gironeb.data.position
import it.promozionepuglia.gironeb.data.standingOf
import it.promozionepuglia.gironeb.data.table
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.MatchList
import it.promozionepuglia.gironeb.ui.components.ResultChip
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.components.StandingsTable
import it.promozionepuglia.gironeb.ui.components.formatKickoff
import it.promozionepuglia.gironeb.ui.components.formatNewsDate
import it.promozionepuglia.gironeb.ui.components.liveLabel
import it.promozionepuglia.gironeb.ui.components.openUrl
import it.promozionepuglia.gironeb.ui.components.rememberNow
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Dettaglio di una partita, a schede: Dettagli, Classifica e, se ce ne sono, Notizie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    snapshot: LeagueSnapshot,
    round: Int,
    homeKey: String,
    onTeamClick: (String) -> Unit,
    onMatchClick: (Match) -> Unit,
) {
    val c = AppTheme.colors
    val m = snapshot.findMatch(round, homeKey)
    if (m == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Partita non trovata nel calendario", color = c.muted)
        }
        return
    }
    val news = snapshot.newsAbout(m)
    val tabs = listOf("Dettagli", "Classifica") + (if (news.isNotEmpty()) listOf("Notizie") else emptyList())
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { Header(m, snapshot, onTeamClick) }
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = c.bg, contentColor = c.text) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(title) }, selectedContentColor = c.accent, unselectedContentColor = c.muted,
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (tabs[page]) {
                "Dettagli" -> Details(m, snapshot, onMatchClick)
                "Classifica" -> LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    item {
                        StandingsTable(snapshot.table(), onTeamClick, highlight = setOf(m.homeKey, m.awayKey))
                    }
                }
                else -> News(news)
            }
        }
    }
}

@Composable
private fun Details(m: Match, snapshot: LeagueSnapshot, onMatchClick: (Match) -> Unit) {
    val context = LocalContext.current
    val goals = ScorersCalculator.goals(m)
    val other = snapshot.otherLeg(m)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (m.hasScore) item {
            Card(padding = 14.dp) {
                SectionTitle("Gol")
                when {
                    m.isForfeit -> Muted("Risultato assegnato a tavolino dal Giudice sportivo.")
                    m.homeGoals == 0 && m.awayGoals == 0 -> {
                        Muted("Nessun gol finora.".takeIf { m.isLive } ?: "Nessun gol.")
                        if (m.isPlayed) Milestone("Finale", "0 - 0")
                    }
                    goals.isEmpty() -> Muted(m.scorers.ifBlank { "Marcatori non ancora pubblicati." })
                    else -> Timeline(m, goals)
                }
                if (m.isLive) Muted("I marcatori compaiono qualche minuto dopo il gol, quando la fonte li pubblica.")
            }
        }
        item { Comparison(m, snapshot) }
        if (other != null) item {
            MatchList(listOf(other), onMatchClick, title = if (other.round < m.round) "ANDATA" else "RITORNO", showRound = true)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tuttocampo.matchUrl(snapshot, m)?.let { url ->
                    FilledTonalButton(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text("La partita su Tuttocampo", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(onClick = { openUrl(context, Tuttocampo.roundUrl(m.round)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("La ${m.round}ª giornata su Tuttocampo")
                }
                Muted("Su Tuttocampo formazioni, ammonizioni e cronaca, quando pubblicate.")
            }
        }
    }
}

/**
 * Gol in ordine di minuto con il separatore dell'intervallo (punteggio ricavato dai minuti,
 * quando il tabellino lo permette) e, a partita finita, quello del finale.
 */
@Composable
private fun Timeline(m: Match, goals: List<Goal>) {
    val (first, second) = goals.partition { it.sortKey < 4600 }
    val halfTime = m.halfTimeScore(goals)
    val pastHalfTime = m.isPlayed || second.isNotEmpty() || (m.isLive && m.isPastFirstHalf(rememberNow()))
    first.forEach { GoalRow(it, m) }
    if (pastHalfTime) Milestone("Intervallo", halfTime?.let { "${it.first} - ${it.second}" })
    second.forEach { GoalRow(it, m) }
    if (m.isPlayed) Milestone("Finale", "${m.homeGoals} - ${m.awayGoals}")
}

@Composable
private fun Milestone(label: String, score: String?) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = c.divider)
        Text(
            if (score != null) "$label  $score" else label, color = c.muted, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = c.divider)
    }
}

@Composable
private fun News(news: List<NewsItem>) {
    val c = AppTheme.colors
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        news.forEach { item ->
            item {
                Card(Modifier.clickable { openUrl(context, item.url) }, padding = 14.dp) {
                    Text(item.title, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("${item.source} · ${formatNewsDate(item.date)}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
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
