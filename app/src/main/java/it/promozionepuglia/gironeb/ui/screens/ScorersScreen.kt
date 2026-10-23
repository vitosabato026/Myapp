package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.data.stats
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.SectionTitle
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import java.util.Locale

@Composable
fun ScorersScreen(snapshot: LeagueSnapshot, scorers: List<Scorer>, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    val stats = snapshot.stats()
    val max = scorers.firstOrNull()?.goals ?: 1
    // Classifica "a pari merito": stessa posizione per stesso numero di gol.
    val positions = scorers.map { s -> scorers.indexOfFirst { it.goals == s.goals } + 1 }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Gol totali", stats.totalGoals.toString(), Modifier.weight(1f))
                Tile("Media a partita", String.format(Locale.ITALIAN, "%.2f", stats.averagePerMatch), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val atk = stats.bestAttack
                val def = stats.bestDefense
                Tile("Miglior attacco", atk?.let { Teams.byKey(it.teamKey).name } ?: "–", Modifier.weight(1f),
                    suffix = atk?.total?.goalsFor?.toString(), suffixColor = c.accent, onClick = atk?.let { { onTeamClick(it.teamKey) } })
                Tile("Miglior difesa", def?.let { Teams.byKey(it.teamKey).name } ?: "–", Modifier.weight(1f),
                    suffix = def?.let { "${it.total.goalsAgainst} subiti" }, suffixColor = c.accent2, onClick = def?.let { { onTeamClick(it.teamKey) } })
            }
        }
        item {
            SectionTitle("Classifica marcatori", Modifier.padding(top = 6.dp)) {
                Text("Gol (rig.)", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        itemsIndexed(scorers.take(40)) { i, s ->
            Column(Modifier.fillMaxWidth().clickable { onTeamClick(s.teamKey) }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${positions[i]}", color = c.muted, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Medium)
                        Text(Teams.byKey(s.teamKey).name, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${s.goals}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (s.penalties > 0) "(${s.penalties})" else "", color = c.muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(26.dp))
                }
                Box(
                    Modifier.padding(start = 32.dp, top = 4.dp, end = 36.dp).fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(c.line),
                ) {
                    Box(Modifier.fillMaxWidth(s.goals / max.toFloat()).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
                }
            }
        }
        item {
            Text(
                "Classifica ricavata dai tabellini di calciowebpuglia.it. Esclusi autogol e gare vinte a tavolino.",
                color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Tile(
    label: String, value: String, modifier: Modifier,
    suffix: String? = null, suffixColor: Color = Color.Unspecified, onClick: (() -> Unit)? = null,
) {
    Card(if (onClick != null) modifier.clickable(onClick = onClick) else modifier, padding = 12.dp) {
        Text(label, color = AppTheme.colors.muted, style = MaterialTheme.typography.labelMedium)
        if (suffix == null) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(value, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(suffix, color = suffixColor, fontWeight = FontWeight.Bold)
        }
    }
}
