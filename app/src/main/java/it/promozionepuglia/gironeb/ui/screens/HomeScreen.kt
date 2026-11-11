package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.bigMatch
import it.promozionepuglia.gironeb.data.form
import it.promozionepuglia.gironeb.data.matchesOf
import it.promozionepuglia.gironeb.data.position
import it.promozionepuglia.gironeb.data.standingOf
import it.promozionepuglia.gironeb.data.stats
import it.promozionepuglia.gironeb.data.table
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.MatchList
import it.promozionepuglia.gironeb.ui.components.ResultChip
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.components.StandingsTable
import it.promozionepuglia.gironeb.ui.components.TeamBadge
import it.promozionepuglia.gironeb.ui.components.formatKickoff
import it.promozionepuglia.gironeb.ui.components.formatNewsDate
import it.promozionepuglia.gironeb.ui.components.formatUpdated
import it.promozionepuglia.gironeb.ui.components.openUrl
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import java.util.Locale

@Composable
fun HomeScreen(
    snapshot: LeagueSnapshot,
    scorers: List<Scorer>,
    error: String?,
    onTeamClick: (String) -> Unit,
    onOpenMatches: () -> Unit,
    onOpenStandings: () -> Unit,
    onOpenScorers: () -> Unit,
    onOpenNews: () -> Unit,
    onMatchClick: (Match) -> Unit = {},
    favorite: String? = null,
    onChooseFavorite: (String) -> Unit = {},
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val stats = snapshot.stats()
    val round = snapshot.nextRound
    val big = snapshot.bigMatch(round)
    val live = snapshot.liveMatches
    val table = snapshot.table()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (error != null) item {
            Text("$error. Mostro gli ultimi dati salvati.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            if (favorite != null) {
                FavoriteCard(snapshot, favorite, onTeamClick, onMatchClick)
            } else {
                ChooseFavorite(onChooseFavorite)
            }
        }
        if (live.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.live))
                    Text("IN DIRETTA", color = c.live, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenMatches) { Text("Giornata") }
                }
            }
            item { MatchList(live, onMatchClick) }
            item { LiveFootnote() }
        }
        // Se il big match è in corso è già tra le partite in diretta.
        if (big != null && !big.isLive) item {
            Card(Modifier.clickable { onMatchClick(big) }) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (big.isPlayed) "${round}ª giornata · Big match" else "${round}ª giornata · Prossimo big match",
                        color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                    )
                    Text(formatKickoff(big.kickoff), color = c.muted, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigMatchTeam(big.homeKey, snapshot, onTeamClick, Modifier.weight(1f))
                    Text(
                        if (big.isPlayed) "${big.homeGoals} - ${big.awayGoals}" else "VS",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (big.isPlayed) c.text else c.muted,
                    )
                    BigMatchTeam(big.awayKey, snapshot, onTeamClick, Modifier.weight(1f))
                }
                FilledTonalButton(onClick = onOpenMatches, modifier = Modifier.fillMaxWidth()) {
                    Text("Tutte le partite della giornata")
                }
            }
        }
        item {
            Card(padding = 14.dp) {
                SectionTitle(if (live.isNotEmpty()) "Classifica in tempo reale" else "Classifica") {
                    TextButton(onClick = onOpenStandings) { Text("Completa") }
                }
                // Le prime cinque, più la squadra seguita se è più in basso.
                val shown = table.take(5) + table.drop(5).filter { it.teamKey == favorite }
                StandingsTable(shown, onTeamClick, highlight = setOfNotNull(favorite), compact = true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val top = scorers.firstOrNull()
                StatTile(
                    "Capocannoniere", top?.goals?.toString() ?: "–", c.accent,
                    top?.name ?: "", top?.let { Teams.byKey(it.teamKey).name } ?: "",
                    Modifier.weight(1f), onOpenScorers,
                )
                StatTile(
                    "Gol in campionato", stats.totalGoals.toString(), c.accent2,
                    String.format(Locale.ITALIAN, "%.2f a partita", stats.averagePerMatch),
                    "dopo ${snapshot.lastPlayedRound} giornate",
                    Modifier.weight(1f), onOpenScorers,
                )
            }
        }
        snapshot.news.firstOrNull()?.let { news ->
            item {
                Card(Modifier.clickable { openUrl(context, news.url) }, padding = 14.dp) {
                    Text(
                        "ULTIMA NOTIZIA · ${news.source.uppercase()}",
                        color = c.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    )
                    Text(news.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatNewsDate(news.date), color = c.muted, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenNews) { Text("Tutte le notizie") }
                    }
                }
            }
        }
        item {
            Text(
                formatUpdated(snapshot.updatedAtMillis) + " · Fonti: calciowebpuglia.it, salentosport.net",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}

/** "La mia squadra": posizione, ultime 5 e la partita in corso o la prossima (o l'ultima, a fine campionato). */
@Composable
private fun FavoriteCard(snapshot: LeagueSnapshot, key: String, onTeamClick: (String) -> Unit, onMatchClick: (Match) -> Unit) {
    val c = AppTheme.colors
    val team = Teams.byKey(key)
    val row = snapshot.standingOf(key)
    val all = snapshot.matchesOf(key)
    val current = all.firstOrNull { it.isLive } ?: all.firstOrNull { !it.isPlayed }
    val last = all.lastOrNull { it.isPlayed }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.clickable { onTeamClick(key) }, padding = 14.dp) {
            Text("LA MIA SQUADRA", color = c.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(
                Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TeamBadge(team, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (row != null) {
                        Text("${snapshot.position(key)}º · ${row.total.points} punti", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { snapshot.form(key).forEach { ResultChip(it) } }
            }
        }
        val shown = listOfNotNull(last?.takeIf { current?.isLive != true }, current)
        if (shown.isNotEmpty()) MatchList(shown, onMatchClick, showRound = true)
    }
}

/** Senza squadra seguita: i loghi del girone, uno tocco per sceglierla. */
@Composable
private fun ChooseFavorite(onChoose: (String) -> Unit) {
    val c = AppTheme.colors
    Card(padding = 14.dp) {
        Text("Segui la tua squadra", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Toccane il logo: la troverai qui in cima, evidenziata in classifica, e riceverai i gol delle sue partite.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Teams.all.sortedBy { it.name }, key = { it.key }) { team ->
                Box(
                    Modifier.clip(CircleShape).clickable { onChoose(team.key) }.padding(4.dp)
                        .semantics { contentDescription = "Segui ${team.name}" },
                ) { TeamBadge(team, 40.dp) }
            }
        }
    }
}

@Composable
internal fun BigMatchTeam(key: String, snapshot: LeagueSnapshot, onClick: (String) -> Unit, modifier: Modifier) {
    val team = Teams.byKey(key)
    val pos = snapshot.position(key)
    val pts = snapshot.standingOf(key)?.total?.points
    Column(
        modifier.clickable { onClick(key) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamBadge(team, 52.dp)
        Text(team.name, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
        if (pos > 0) Text("${pos}º · $pts pt", color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatTile(
    label: String, value: String, valueColor: androidx.compose.ui.graphics.Color,
    line1: String, line2: String, modifier: Modifier, onClick: () -> Unit,
) {
    Card(modifier.clickable(onClick = onClick), padding = 14.dp) {
        Text(label, color = AppTheme.colors.muted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = valueColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(line1, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(line2, color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
