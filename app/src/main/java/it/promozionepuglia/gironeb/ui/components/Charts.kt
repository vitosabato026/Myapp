package it.promozionepuglia.gironeb.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.promozionepuglia.gironeb.data.GOAL_BANDS
import it.promozionepuglia.gironeb.data.GoalTiming
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import kotlin.math.abs

/**
 * Posizione in classifica giornata per giornata: il 1º posto in alto. Una sola serie, quindi
 * niente legenda; l'ultimo punto ha l'etichetta e toccando un punto si legge la sua giornata.
 */
@Composable
fun PositionChart(points: List<Pair<Int, Int>>, teams: Int, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.muted)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = c.text, fontWeight = FontWeight.Bold)
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val description = points.joinToString { (r, p) -> "${r}ª giornata ${p}º" }

    Canvas(
        modifier.fillMaxWidth().height(170.dp)
            .semantics { contentDescription = "Posizione in classifica: $description" }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val left = 36.dp.toPx()
                    val right = size.width - 16.dp.toPx()
                    val step = if (points.size > 1) (right - left) / (points.size - 1) else 0f
                    selected = points.indices.minBy { abs(left + it * step - tap.x) }
                }
            },
    ) {
        val left = 36.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val step = if (points.size > 1) (right - left) / (points.size - 1) else 0f
        fun x(i: Int) = if (points.size > 1) left + i * step else (left + right) / 2
        fun y(pos: Int) = top + (pos - 1) * (bottom - top) / (teams - 1).coerceAtLeast(1)

        // Griglia discreta: 1º, metà classifica, ultimo posto.
        for (pos in listOf(1, (teams + 1) / 2, teams)) {
            drawLine(
                c.line, Offset(left, y(pos)), Offset(right, y(pos)), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            val t = measurer.measure("${pos}º", labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 8.dp.toPx(), y(pos) - t.size.height / 2))
        }
        // Giornate sull'asse x: tutte fino a 15, poi una ogni 5.
        points.forEachIndexed { i, (round, _) ->
            if (points.size <= 15 || round % 5 == 0 || i == points.lastIndex) {
                val t = measurer.measure("$round", labelStyle)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2, bottom + 6.dp.toPx()))
            }
        }
        // Linea da 2 dp, marcatori da 8 dp con un anello del colore della superficie.
        for (i in 1..points.lastIndex) {
            drawLine(
                c.chartFor, Offset(x(i - 1), y(points[i - 1].second)), Offset(x(i), y(points[i].second)),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        points.forEachIndexed { i, (_, pos) ->
            val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(c.surface, r + 2.dp.toPx(), Offset(x(i), y(pos)))
            drawCircle(c.chartFor, r, Offset(x(i), y(pos)))
        }
        // Etichetta del punto scelto (di partenza l'ultimo), sopra o sotto per restare nel grafico.
        val (round, pos) = points[selected]
        val label = measurer.measure("${round}ª: ${pos}º", valueStyle)
        val lx = (x(selected) - label.size.width / 2).coerceIn(0f, size.width - label.size.width)
        val ly = if (y(pos) - 10.dp.toPx() - label.size.height > 0) y(pos) - 10.dp.toPx() - label.size.height else y(pos) + 10.dp.toPx()
        drawText(label, topLeft = Offset(lx, ly))
    }
}

/**
 * Gol fatti e subiti per fascia di minuti: due barre sottili per fascia, con il valore scritto
 * accanto (fa anche da tabella) e la legenda sopra.
 */
@Composable
fun GoalTimingChart(timing: GoalTiming) {
    val c = AppTheme.colors
    val max = maxOf(1, timing.scored.maxOrNull() ?: 0, timing.conceded.maxOrNull() ?: 0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(c.chartFor, "Fatti")
            LegendItem(c.chartAgainst, "Subiti")
        }
        GOAL_BANDS.forEachIndexed { i, band ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$band: ${timing.scored[i]} fatti, ${timing.conceded[i]} subiti"
                },
            ) {
                Text(band, color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Bar(timing.scored[i], max, c.chartFor)
                    Bar(timing.conceded[i], max, c.chartAgainst)
                }
            }
        }
    }
}

@Composable
private fun Bar(value: Int, max: Int, color: Color) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) {
            if (value > 0) {
                Box(
                    Modifier.fillMaxWidth(value / max.toFloat()).height(8.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(color),
                )
            } else {
                Box(Modifier.width(2.dp).height(8.dp).background(c.line))
            }
        }
        Text("$value", color = c.text2, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.width(18.dp))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, color = AppTheme.colors.text2, style = MaterialTheme.typography.labelMedium)
    }
}

/** Nota sotto un grafico (ad esempio i gol esclusi). */
@Composable
fun ChartNote(text: String) {
    Text(text, color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}
