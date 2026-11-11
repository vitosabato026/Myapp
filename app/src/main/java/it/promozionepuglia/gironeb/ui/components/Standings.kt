package it.promozionepuglia.gironeb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.promozionepuglia.gironeb.data.TableEntry
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.ui.theme.AppColors
import it.promozionepuglia.gironeb.ui.theme.AppTheme

/**
 * Zone di classifica. Valori indicativi: da allineare al comunicato del
 * Comitato Regionale Puglia per la stagione 2026/27.
 */
object Zones {
    const val PROMOTION = 1
    val PLAYOFF = 2..5
    val PLAYOUT = 12..15
    const val RELEGATION = 16
}

fun AppColors.zoneColor(pos: Int): Color? = when {
    pos == Zones.PROMOTION -> zonePromotion
    pos in Zones.PLAYOFF -> zonePlayoff
    pos in Zones.PLAYOUT -> zonePlayout
    pos >= Zones.RELEGATION -> zoneRelegation
    else -> null
}

/**
 * La classifica. In diretta mostra la freccia di chi sale o scende rispetto all'inizio della
 * giornata e il punteggio parziale; le squadre in [highlight] hanno la riga evidenziata.
 */
@Composable
fun StandingsTable(
    entries: List<TableEntry>,
    onTeamClick: (String) -> Unit,
    highlight: Set<String> = emptySet(),
    showZones: Boolean = true,
    compact: Boolean = false,
) {
    val c = AppTheme.colors
    val live = entries.any { it.previousPosition != null }
    val numStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text2, textAlign = TextAlign.Center)
    Column {
        HeaderRow(numStyle.copy(color = c.muted, fontWeight = FontWeight.Medium), live, compact)
        entries.forEach { e ->
            val team = Teams.byKey(e.teamKey)
            val l = e.line
            HorizontalDivider(color = c.divider)
            Row(
                Modifier.fillMaxWidth().height(44.dp)
                    .background(if (e.teamKey in highlight) c.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onTeamClick(e.teamKey) }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.width(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    val zone = if (showZones) c.zoneColor(e.position) else null
                    Box(Modifier.size(6.dp).clip(CircleShape).background(zone ?: Color.Transparent))
                    Text("${e.position}", color = c.muted, modifier = Modifier.padding(start = 4.dp))
                }
                if (live) Movement(e)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamBadge(team, 22.dp)
                    Text(
                        team.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        fontWeight = if (e.teamKey in highlight) FontWeight.Bold else null,
                    )
                    e.live?.let { m ->
                        // Punteggio dal punto di vista della squadra della riga: verde se vince, rosso se perde.
                        val mine = if (m.homeKey == e.teamKey) m.homeGoals else m.awayGoals
                        val theirs = if (m.homeKey == e.teamKey) m.awayGoals else m.homeGoals
                        val color = when {
                            mine == null || theirs == null -> c.muted
                            mine > theirs -> c.win
                            mine < theirs -> c.live
                            else -> c.muted
                        }
                        Text(
                            "$mine-$theirs", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { contentDescription = "in corso, $mine a $theirs" },
                        )
                    }
                    if (e.penalty > 0 && showZones) {
                        Text("-${e.penalty}", color = c.zoneRelegation, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!compact) {
                    // In diretta servono spazio per freccia e punteggio: restano G, DR e PT.
                    Num(l.played, numStyle)
                    if (!live) { Num(l.won, numStyle); Num(l.drawn, numStyle); Num(l.lost, numStyle) }
                    Text((if (l.goalDiff > 0) "+" else "") + l.goalDiff, style = numStyle, modifier = Modifier.width(32.dp))
                } else {
                    Num(l.played, numStyle)
                }
                Text(
                    "${l.points}", style = numStyle.copy(color = c.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    modifier = Modifier.width(30.dp),
                )
            }
        }
    }
}

/** Freccia verde se la squadra sale rispetto all'inizio della giornata, rossa se scende. */
@Composable
private fun Movement(e: TableEntry) {
    val c = AppTheme.colors
    val delta = (e.previousPosition ?: e.position) - e.position
    val (symbol, color, description) = when {
        delta > 0 -> Triple("▲", c.win, "sale di $delta")
        delta < 0 -> Triple("▼", c.live, "scende di ${-delta}")
        else -> Triple("•", c.muted, "stessa posizione")
    }
    Text(
        symbol, color = color, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
        modifier = Modifier.width(14.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun HeaderRow(style: TextStyle, live: Boolean, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("#", style = style.copy(textAlign = TextAlign.Start), modifier = Modifier.width(28.dp))
        if (live) Text("", modifier = Modifier.width(14.dp))
        Text("Squadra", style = style.copy(textAlign = TextAlign.Start), modifier = Modifier.weight(1f))
        if (!compact) {
            (if (live) listOf("G") else listOf("G", "V", "N", "P")).forEach { Text(it, style = style, modifier = Modifier.width(24.dp)) }
            Text("DR", style = style, modifier = Modifier.width(32.dp))
        } else {
            Text("G", style = style, modifier = Modifier.width(24.dp))
        }
        Text("PT", style = style.copy(textAlign = TextAlign.End), modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun Num(value: Int, style: TextStyle) {
    Text("$value", style = style, modifier = Modifier.width(24.dp))
}
