package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.StandingKind
import it.promozionepuglia.gironeb.data.table
import it.promozionepuglia.gironeb.ui.components.StandingsTable
import it.promozionepuglia.gironeb.ui.theme.AppTheme

@Composable
fun StandingsScreen(snapshot: LeagueSnapshot, favorite: String? = null, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    var kind by rememberSaveable { mutableStateOf(StandingKind.TOTAL) }
    val entries = snapshot.table(kind)
    val live = entries.any { it.previousPosition != null }

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
            if (live) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.live))
                    Text("IN TEMPO REALE", color = c.live, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("· frecce rispetto all'inizio della giornata", color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    "Dopo la ${snapshot.lastPlayedRound}ª giornata",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        item {
            StandingsTable(entries, onTeamClick, highlight = setOfNotNull(favorite), showZones = kind == StandingKind.TOTAL)
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
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}
