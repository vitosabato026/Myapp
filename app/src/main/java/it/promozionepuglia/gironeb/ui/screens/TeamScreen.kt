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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.StandingLine
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.form
import it.promozionepuglia.gironeb.data.goalTiming
import it.promozionepuglia.gironeb.data.matchesOf
import it.promozionepuglia.gironeb.data.position
import it.promozionepuglia.gironeb.data.positionsByRound
import it.promozionepuglia.gironeb.data.standingOf
import it.promozionepuglia.gironeb.data.teamRecord
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.ChartNote
import it.promozionepuglia.gironeb.ui.components.GoalTimingChart
import it.promozionepuglia.gironeb.ui.components.MatchList
import it.promozionepuglia.gironeb.ui.components.PositionChart
import it.promozionepuglia.gironeb.ui.components.ResultChip
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.components.TeamBadge
import it.promozionepuglia.gironeb.ui.components.openUrl
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Pagina squadra, a schede come su Sofascore: Panoramica, Partite, Marcatori. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    snapshot: LeagueSnapshot,
    scorers: List<Scorer>,
    teamKey: String,
    onMatchClick: (Match) -> Unit = {},
    favorite: String? = null,
    onToggleFavorite: (String?) -> Unit = {},
    onPlayerClick: (Scorer) -> Unit = {},
    onTeamClick: (String) -> Unit,
) {
    val c = AppTheme.colors
    val team = Teams.byKey(teamKey)
    val row = snapshot.standingOf(teamKey)
    val tabs = listOf("Panoramica", "Partite", "Marcatori")
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val isFavorite = favorite == teamKey

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TeamBadge(team, 60.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(team.fullName, style = MaterialTheme.typography.titleLarge)
                        if (row != null) {
                            Text("${snapshot.position(teamKey)}º in classifica · ${row.total.points} punti", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        val form = snapshot.form(teamKey)
                        if (form.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { form.forEach { ResultChip(it) } }
                    }
                    IconButton(onClick = { onToggleFavorite(if (isFavorite) null else teamKey) }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFavorite) "Non seguire più ${team.name}" else "Segui ${team.name}",
                            tint = if (isFavorite) c.accent else c.muted,
                        )
                    }
                }
            }
        }
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = c.bg, contentColor = c.text) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(title) }, selectedContentColor = c.accent, unselectedContentColor = c.muted,
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> Overview(snapshot, teamKey, onMatchClick)
                1 -> Matches(snapshot, teamKey, onMatchClick)
                else -> TeamScorers(scorers.filter { it.teamKey == teamKey }, onPlayerClick)
            }
        }
    }
}

@Composable
private fun Overview(snapshot: LeagueSnapshot, teamKey: String, onMatchClick: (Match) -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val team = Teams.byKey(teamKey)
    val row = snapshot.standingOf(teamKey)
    val all = snapshot.matchesOf(teamKey)
    val current = all.firstOrNull { it.isLive } ?: all.firstOrNull { !it.isPlayed }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (current != null) item {
            MatchList(listOf(current), onMatchClick, title = if (current.isLive) "IN DIRETTA" else "PROSSIMA PARTITA", showRound = true)
        }
        if (row != null) item {
            val t = row.total
            val stats = listOf(
                "${t.won}" to "Vittorie", "${t.drawn}" to "Pareggi", "${t.lost}" to "Sconfitte",
                "${t.goalsFor}" to "Gol fatti", "${t.goalsAgainst}" to "Gol subiti",
                (if (t.goalDiff > 0) "+" else "") + t.goalDiff to "Differenza",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { (v, l) ->
                            Card(Modifier.weight(1f), padding = 12.dp) {
                                Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(l, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        val positions = snapshot.positionsByRound(teamKey)
        if (positions.size >= 2) item {
            Card(padding = 14.dp) {
                SectionTitle("Andamento in classifica")
                PositionChart(positions, teams = snapshot.standings.size, modifier = Modifier.padding(top = 8.dp))
                ChartNote("Posizione dopo ogni giornata. Tocca un punto per leggerlo.")
            }
        }
        item {
            val timing = snapshot.goalTiming(teamKey)
            if (timing.scored.sum() + timing.conceded.sum() > 0) {
                Card(padding = 14.dp) {
                    SectionTitle("Quando segna e quando subisce", Modifier.padding(bottom = 8.dp))
                    GoalTimingChart(timing)
                    if (timing.unknown > 0) {
                        ChartNote("${timing.unknown} gol senza minuto o squadra nel tabellino non sono contati.")
                    }
                }
            }
        }
        if (row != null) item {
            val record = snapshot.teamRecord(teamKey)
            Card(padding = 14.dp) {
                SectionTitle("Rendimento")
                RecordRow("In casa", row.home)
                RecordRow("In trasferta", row.away)
                HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 6.dp))
                ValueRow("Partite senza gol subiti", "${record.cleanSheets} su ${record.played}")
                ValueRow("Partite senza segnare", "${record.failedToScore} su ${record.played}")
            }
        }
        item {
            Card(padding = 14.dp) {
                SectionTitle("Notizie dal club")
                Text(
                    "Segui i profili ufficiali per comunicati, formazioni e foto.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp),
                )
                if (team.instagram == null && team.facebook == null) {
                    Text("Profilo ufficiale non ancora trovato.", color = c.muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    team.instagram?.let { url ->
                        SocialButton("Instagram", Color(0xFFC13584), Modifier.weight(1f)) { openUrl(context, url, preferExternalApp = true) }
                    }
                    team.facebook?.let { url ->
                        SocialButton("Facebook", Color(0xFF1877F2), Modifier.weight(1f)) { openUrl(context, url, preferExternalApp = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(label: String, l: StandingLine) {
    ValueRow(label, "${l.won}V ${l.drawn}N ${l.lost}P · ${l.goalsFor}-${l.goalsAgainst} · ${l.points} pt")
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppTheme.colors.text2, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** Prima le partite in corso o da giocare, poi quelle giocate dalla più recente. */
@Composable
private fun Matches(snapshot: LeagueSnapshot, teamKey: String, onMatchClick: (Match) -> Unit) {
    val all = snapshot.matchesOf(teamKey)
    val upcoming = all.filter { !it.isPlayed }
    val played = all.filter { it.isPlayed }.reversed()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (upcoming.isNotEmpty()) item { MatchList(upcoming, onMatchClick, title = "DA GIOCARE", showRound = true) }
        if (played.isNotEmpty()) item { MatchList(played, onMatchClick, title = "GIOCATE", showRound = true) }
    }
}

@Composable
private fun TeamScorers(scorers: List<Scorer>, onPlayerClick: (Scorer) -> Unit) {
    val c = AppTheme.colors
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (scorers.isEmpty()) item { Text("Nessun gol ancora.", color = c.muted) }
        else item {
            Card(padding = 0.dp) {
                scorers.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = c.divider)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPlayerClick(s) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${i + 1}", color = c.muted, modifier = Modifier.width(20.dp), textAlign = TextAlign.End)
                        Text(s.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (s.penalties > 0) Text("${s.penalties} rig.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Text("${s.goals}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(label, fontWeight = FontWeight.Medium) }
}
