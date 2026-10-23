package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.bigMatch
import it.promozionepuglia.gironeb.data.position
import it.promozionepuglia.gironeb.data.standingOf
import it.promozionepuglia.gironeb.data.stats
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.SectionTitle
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
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val stats = snapshot.stats()
    val round = snapshot.nextRound
    val big = snapshot.bigMatch(round)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (error != null) item {
            Text("$error. Mostro gli ultimi dati salvati.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (big != null) item {
            Card {
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
                SectionTitle("Classifica") { TextButton(onClick = onOpenStandings) { Text("Completa") } }
                snapshot.standings.take(5).forEachIndexed { i, row ->
                    val team = Teams.byKey(row.teamKey)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onTeamClick(row.teamKey) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${i + 1}", color = c.muted, modifier = Modifier.width(18.dp), textAlign = TextAlign.End)
                        TeamBadge(team)
                        Text(team.name, modifier = Modifier.weight(1f))
                        Text("${row.total.points}", fontWeight = FontWeight.Bold)
                    }
                }
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

@Composable
private fun BigMatchTeam(key: String, snapshot: LeagueSnapshot, onClick: (String) -> Unit, modifier: Modifier) {
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
