package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.StandingKind
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.line
import it.promozionepuglia.gironeb.data.standings
import it.promozionepuglia.gironeb.ui.components.TeamBadge
import it.promozionepuglia.gironeb.ui.theme.AppColors
import it.promozionepuglia.gironeb.ui.theme.AppTheme

/**
 * Zone di classifica. Valori indicativi: da allineare al comunicato del
 * Comitato Regionale Puglia per la stagione 2026/27.
 */
private object Zones {
    const val PROMOTION = 1
    val PLAYOFF = 2..5
    val PLAYOUT = 12..15
    const val RELEGATION = 16
}

private fun AppColors.zoneColor(pos: Int): Color? = when {
    pos == Zones.PROMOTION -> zonePromotion
    pos in Zones.PLAYOFF -> zonePlayoff
    pos in Zones.PLAYOUT -> zonePlayout
    pos >= Zones.RELEGATION -> zoneRelegation
    else -> null
}

@Composable
fun StandingsScreen(snapshot: LeagueSnapshot, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    var kind by rememberSaveable { mutableStateOf(StandingKind.TOTAL) }
    val rows = snapshot.standings(kind)
    val numStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text2, textAlign = TextAlign.Center)

    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                listOf(StandingKind.TOTAL to "Totale", StandingKind.HOME to "Casa", StandingKind.AWAY to "Trasferta").forEach { (k, label) ->
                    FilterChip(
                        selected = kind == k, onClick = { kind = k }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.accent, selectedLabelColor = c.onAccent,
                        ),
                    )
                }
            }
            Text(
                "Dopo la ${snapshot.lastPlayedRound}ª giornata",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            HeaderRow(numStyle.copy(color = c.muted, fontWeight = FontWeight.Medium))
        }
        itemsIndexed(rows, key = { _, r -> r.teamKey }) { i, row ->
            val team = Teams.byKey(row.teamKey)
            val l = row.line(kind)
            val pos = i + 1
            HorizontalDivider(color = c.divider)
            Row(
                Modifier.fillMaxWidth().height(44.dp).clickable { onTeamClick(row.teamKey) }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.width(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    val zone = if (kind == StandingKind.TOTAL) c.zoneColor(pos) else null
                    Box(Modifier.size(6.dp).clip(CircleShape).background(zone ?: Color.Transparent))
                    Text("$pos", color = c.muted, modifier = Modifier.padding(start = 4.dp))
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamBadge(team, 22.dp)
                    Text(team.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (row.penalty > 0 && kind == StandingKind.TOTAL) {
                        Text("-${row.penalty}", color = c.zoneRelegation, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Num(l.played, numStyle); Num(l.won, numStyle); Num(l.drawn, numStyle); Num(l.lost, numStyle)
                Text((if (l.goalDiff > 0) "+" else "") + l.goalDiff, style = numStyle, modifier = Modifier.width(32.dp))
                Text("${l.points}", style = numStyle.copy(color = c.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.End), modifier = Modifier.width(30.dp))
            }
        }
        if (kind == StandingKind.TOTAL) item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            ) {
                Legend(c.zonePromotion, "Eccellenza")
                Legend(c.zonePlayoff, "Play-off")
                Legend(c.zonePlayout, "Play-out")
                Legend(c.zoneRelegation, "Retrocessione")
            }
            if (snapshot.standings.any { it.penalty > 0 }) {
                Text("−N: punti di penalizzazione", color = c.muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun HeaderRow(style: TextStyle) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("#", style = style.copy(textAlign = TextAlign.Start), modifier = Modifier.width(28.dp))
        Text("Squadra", style = style.copy(textAlign = TextAlign.Start), modifier = Modifier.weight(1f))
        listOf("G", "V", "N", "P").forEach { Text(it, style = style, modifier = Modifier.width(24.dp)) }
        Text("DR", style = style, modifier = Modifier.width(32.dp))
        Text("PT", style = style.copy(textAlign = TextAlign.End), modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun Num(value: Int, style: TextStyle) {
    Text("$value", style = style, modifier = Modifier.width(24.dp))
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}
