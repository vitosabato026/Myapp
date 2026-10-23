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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.form
import it.promozionepuglia.gironeb.data.nextMatchOf
import it.promozionepuglia.gironeb.data.playedBy
import it.promozionepuglia.gironeb.data.position
import it.promozionepuglia.gironeb.data.resultFor
import it.promozionepuglia.gironeb.data.standingOf
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.ResultChip
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.components.TeamBadge
import it.promozionepuglia.gironeb.ui.components.formatKickoff
import it.promozionepuglia.gironeb.ui.components.openUrl
import it.promozionepuglia.gironeb.ui.theme.AppTheme

@Composable
fun TeamScreen(snapshot: LeagueSnapshot, scorers: List<Scorer>, teamKey: String, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val team = Teams.byKey(teamKey)
    val row = snapshot.standingOf(teamKey)
    val pos = snapshot.position(teamKey)
    val form = snapshot.form(teamKey)
    val next = snapshot.nextMatchOf(teamKey)
    val played = snapshot.playedBy(teamKey)
    val teamScorers = scorers.filter { it.teamKey == teamKey }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TeamBadge(team, 64.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(team.fullName, style = MaterialTheme.typography.titleLarge)
                        if (row != null) {
                            Text("${pos}º in classifica · ${row.total.points} punti", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (form.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { form.forEach { ResultChip(it) } }
                        }
                    }
                }
            }
        }
        if (row != null) item {
            val t = row.total
            val stats = listOf(
                t.won to "Vittorie", t.drawn to "Pareggi", t.lost to "Sconfitte",
                t.goalsFor to "Gol fatti", t.goalsAgainst to "Gol subiti",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (stats.map { it.first.toString() to it.second } + ((if (t.goalDiff > 0) "+" else "") + t.goalDiff to "Differenza"))
                    .chunked(3).forEach { rowItems ->
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
        if (next != null) item {
            val home = next.homeKey == teamKey
            val opp = Teams.byKey(if (home) next.awayKey else next.homeKey)
            Card(Modifier.clickable { onTeamClick(opp.key) }, padding = 14.dp) {
                Text("Prossima partita · ${next.round}ª giornata", color = c.muted, style = MaterialTheme.typography.labelMedium)
                Text(
                    (if (home) "In casa con " else "In trasferta a ") + opp.name,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp),
                )
                Text(formatKickoff(next.kickoff), color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (played.isNotEmpty()) item {
            Card(padding = 14.dp) {
                SectionTitle("Partite giocate")
                played.forEach { m ->
                    val home = m.homeKey == teamKey
                    val opp = Teams.byKey(if (home) m.awayKey else m.homeKey)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onTeamClick(opp.key) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${m.round}ª", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(26.dp))
                        Text((if (home) "" else "@ ") + opp.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${m.homeGoals}-${m.awayGoals}", fontWeight = FontWeight.Bold)
                        m.resultFor(teamKey)?.let { ResultChip(it) }
                    }
                }
            }
        }
        if (teamScorers.isNotEmpty()) item {
            Card(padding = 14.dp) {
                SectionTitle("Marcatori")
                teamScorers.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(s.name, modifier = Modifier.weight(1f))
                        Text("${s.goals} gol", fontWeight = FontWeight.Bold)
                    }
                }
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
private fun SocialButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(label, fontWeight = FontWeight.Medium) }
}
