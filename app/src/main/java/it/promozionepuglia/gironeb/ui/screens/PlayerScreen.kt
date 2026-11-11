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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.goalsOfPlayer
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.components.TeamBadge
import it.promozionepuglia.gironeb.ui.theme.AppTheme

/** Un marcatore: totale, posizione nella classifica marcatori e gol partita per partita. */
@Composable
fun PlayerScreen(
    snapshot: LeagueSnapshot,
    scorers: List<Scorer>,
    teamKey: String,
    name: String,
    onMatchClick: (Match) -> Unit,
    onTeamClick: (String) -> Unit,
) {
    val c = AppTheme.colors
    val team = Teams.byKey(teamKey)
    val scorer = scorers.firstOrNull { it.name == name && it.teamKey == teamKey }
    // A pari gol, stessa posizione.
    val rank = scorer?.let { s -> scorers.indexOfFirst { it.goals == s.goals } + 1 }
    val byMatch = snapshot.goalsOfPlayer(name, teamKey)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card {
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.padding(top = 6.dp).clickable { onTeamClick(teamKey) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TeamBadge(team, 22.dp)
                    Text(team.name, color = c.text2)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Gol", "${scorer?.goals ?: 0}", Modifier.weight(1f))
                Stat("Rigori", "${scorer?.penalties ?: 0}", Modifier.weight(1f))
                Stat("Marcatori", rank?.let { "${it}º" } ?: "–", Modifier.weight(1f))
            }
        }
        if (byMatch.isNotEmpty()) item {
            Card(padding = 0.dp) {
                SectionTitle("Gol partita per partita", Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 4.dp))
                byMatch.forEachIndexed { i, (m, goals) ->
                    if (i > 0) HorizontalDivider(color = c.divider)
                    val home = m.homeKey == teamKey
                    val opponent = Teams.byKey(if (home) m.awayKey else m.homeKey)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onMatchClick(m) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${m.round}ª", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(26.dp))
                        TeamBadge(opponent, 22.dp)
                        Column(Modifier.weight(1f)) {
                            Text((if (home) "vs " else "@ ") + opponent.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                goals.joinToString { g -> g.minute + if (g.penalty) " rig." else "" },
                                color = c.muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("${m.homeGoals}-${m.awayGoals}", color = if (m.isLive) c.live else c.text2, fontWeight = FontWeight.Medium)
                        Text("${goals.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(22.dp))
                    }
                }
            }
        }
        item {
            Text(
                "Dai tabellini di calciowebpuglia.it: i giocatori sono riconosciuti dal cognome come scritto nel tabellino.",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, padding = 12.dp) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}
