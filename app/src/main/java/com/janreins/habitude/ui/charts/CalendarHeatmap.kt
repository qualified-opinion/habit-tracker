package com.janreins.habitude.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.janreins.habitude.domain.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How one heatmap cell is drawn. A null fill draws nothing. */
data class HeatCell(val fill: Color?, val outlined: Boolean = false, val dot: Boolean = false)

/**
 * A GitHub-style calendar: one column per week (Monday at the top), the current week on
 * the right. Tap a day to see what happened in the caption underneath, next to anything
 * [action] puts there for that day (such as a button to change it), with [details] below.
 */
@Composable
fun CalendarHeatmap(
    today: LocalDate,
    weeks: Int,
    cell: (LocalDate) -> HeatCell,
    describe: (LocalDate) -> String,
    modifier: Modifier = Modifier,
    hint: String = "Tap a day to see it",
    action: @Composable (LocalDate) -> Unit = {},
    details: @Composable (LocalDate) -> Unit = {},
) {
    val firstDay = Stats.weekStart(today).minusWeeks((weeks - 1).toLong())
    val selection = remember(today, weeks) { mutableStateOf<LocalDate?>(null) }
    var selected by selection
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val ring = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val gutter = 18.dp
    val header = 16.dp
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMM") }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(header + 17.dp * 7)) {
            val columnWidth = (maxWidth - gutter) / weeks
            val rowHeight = minOf(columnWidth, 17.dp)
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val colStep = (size.width - gutter.toPx()) / weeks
                val rowStep = minOf(colStep, (size.height - header.toPx()) / 7)
                val gap = 2.dp.toPx()
                val box = minOf(colStep, rowStep) - gap
                val radius = CornerRadius(3.dp.toPx())

                // Weekday hints on the left: Mon, Wed, Fri
                listOf(0 to "M", 2 to "W", 4 to "F").forEach { (row, label) ->
                    val layout = measurer.measure(label, labelStyle)
                    drawText(
                        layout,
                        topLeft = Offset(0f, header.toPx() + row * rowStep + (box - layout.size.height) / 2),
                    )
                }

                for (col in 0 until weeks) {
                    val weekStart = firstDay.plusWeeks(col.toLong())
                    if (weekStart.dayOfMonth <= 7) {
                        drawText(
                            measurer.measure(weekStart.format(monthFormat), labelStyle),
                            topLeft = Offset(gutter.toPx() + col * colStep, 0f),
                        )
                    }
                    for (row in 0..6) {
                        val day = weekStart.plusDays(row.toLong())
                        if (day.isAfter(today)) continue
                        val spec = cell(day)
                        val topLeft = Offset(gutter.toPx() + col * colStep, header.toPx() + row * rowStep)
                        val cellSize = Size(box, box)
                        spec.fill?.let { drawRoundRect(it, topLeft, cellSize, radius) }
                        if (spec.outlined) {
                            drawRoundRect(ring.copy(alpha = 0.35f), topLeft, cellSize, radius, style = Stroke(1.5.dp.toPx()))
                        }
                        if (spec.dot) {
                            drawCircle(surface, radius = box * 0.18f, center = topLeft + Offset(box / 2, box / 2))
                        }
                        if (day == selected) {
                            drawRoundRect(
                                ring,
                                topLeft - Offset(gap / 2, gap / 2),
                                Size(box + gap, box + gap),
                                radius,
                                style = Stroke(2.dp.toPx()),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.padding(start = gutter, top = header).semantics {
                    isTraversalGroup = true
                    collectionInfo = CollectionInfo(7, weeks)
                },
            ) {
                repeat(weeks) { col ->
                    Column(Modifier.width(columnWidth)) {
                        repeat(7) { row ->
                            val day = firstDay.plusDays(col * 7L + row)
                            val cellModifier = Modifier.width(columnWidth).height(rowHeight)
                            if (day.isAfter(today)) {
                                Spacer(cellModifier)
                            } else {
                                val onClick = remember(day, selection) {
                                    { selection.value = if (selection.value == day) null else day }
                                }
                                Box(cellModifier.semantics {
                                    contentDescription = describe(day)
                                    this.selected = day == selected
                                    role = Role.Button
                                    collectionItemInfo = CollectionItemInfo(row, 1, col, 1)
                                }.clickable(onClick = onClick))
                            }
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Text(
                selected?.let(describe) ?: hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            selected?.let { action(it) }
        }
        selected?.let { details(it) }
    }
}
