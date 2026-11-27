package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActivityStat
import com.example.data.GenderSummary
import com.example.data.PeriodTrendPoint
import com.example.ui.theme.LocalSuiviColors
import kotlin.math.roundToInt

@Composable
fun GenderLegendRow(modifier: Modifier = Modifier) {
    val semantic = LocalSuiviColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendChip(color = semantic.boy, label = "Garçon")
        LegendChip(color = semantic.girl, label = "Fille")
    }
}

@Composable
fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Grouped Bar Chart comparing Garçon vs Fille mastery rates (%) across all 8 CM2 activities,
 * with horizontal dashed threshold lines for Seuil Minimal and Seuil Maximal.
 */
@Composable
fun GenderActivityBarChart(
    activityStats: List<ActivityStat>,
    minThresholdPct: Double,
    maxThresholdPct: Double,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSuiviColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val thresholdColor = semantic.masteryMax
    val minLineColor = semantic.masteryImprove

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GenderLegendRow()
            Text(
                text = "Seuils ${minThresholdPct.roundToInt()}% / ${maxThresholdPct.roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val width = size.width
                val height = size.height
                val topPad = 8.dp.toPx()
                val bottomPad = 6.dp.toPx()
                val chartHeight = (height - topPad - bottomPad).coerceAtLeast(1f)

                // Horizontal grid lines at 0%, 25%, 50%, 75%, 100%
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                    val y = topPad + chartHeight * (1f - frac)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Dashed mastery threshold lines
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                val minY = topPad + chartHeight * (1f - (minThresholdPct.toFloat() / 100f).coerceIn(0f, 1f))
                drawLine(
                    color = minLineColor.copy(alpha = 0.85f),
                    start = Offset(0f, minY),
                    end = Offset(width, minY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect
                )
                val maxY = topPad + chartHeight * (1f - (maxThresholdPct.toFloat() / 100f).coerceIn(0f, 1f))
                drawLine(
                    color = thresholdColor.copy(alpha = 0.85f),
                    start = Offset(0f, maxY),
                    end = Offset(width, maxY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect
                )

                if (activityStats.isNotEmpty()) {
                    val slotWidth = width / activityStats.size
                    val barGap = (slotWidth * 0.08f).coerceAtLeast(2.dp.toPx())
                    val barWidth = ((slotWidth * 0.64f) - barGap) / 2f
                    val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                    activityStats.forEachIndexed { index, stat ->
                        val slotStart = index * slotWidth
                        val groupStart = slotStart + (slotWidth - (barWidth * 2f + barGap)) / 2f

                        val boyFrac = (stat.boysMasteryPct.toFloat() / 100f).coerceIn(0f, 1f)
                        val boyH = (chartHeight * boyFrac).coerceAtLeast(if (stat.boysCount > 0) 3.dp.toPx() else 0f)
                        if (boyH > 0f) {
                            drawRoundRect(
                                color = semantic.boy,
                                topLeft = Offset(groupStart, topPad + chartHeight - boyH),
                                size = Size(barWidth, boyH),
                                cornerRadius = radius
                            )
                        }

                        val girlFrac = (stat.girlsMasteryPct.toFloat() / 100f).coerceIn(0f, 1f)
                        val girlH = (chartHeight * girlFrac).coerceAtLeast(if (stat.girlsCount > 0) 3.dp.toPx() else 0f)
                        if (girlH > 0f) {
                            drawRoundRect(
                                color = semantic.girl,
                                topLeft = Offset(groupStart + barWidth + barGap, topPad + chartHeight - girlH),
                                size = Size(barWidth, girlH),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            activityStats.forEach { stat ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stat.key.shortLabel.replace(" ", "\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Dual Gender Donut Diagram showing Mastery Decision distribution for Garçon vs Fille side by side.
 */
@Composable
fun GenderDecisionDonutSection(
    boys: GenderSummary,
    girls: GenderSummary,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSuiviColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GenderDonutCard(
                title = "Garçons",
                accentColor = semantic.boy,
                summary = boys,
                modifier = Modifier.weight(1f)
            )
            GenderDonutCard(
                title = "Filles",
                accentColor = semantic.girl,
                summary = girls,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendChip(color = semantic.masteryMax, label = "M. Max")
            LegendChip(color = semantic.masteryMin, label = "M. Min")
            LegendChip(color = semantic.masteryImprove, label = "À amél.")
            LegendChip(color = semantic.masteryReinforce, label = "À renf.")
        }
    }
}

@Composable
private fun GenderDonutCard(
    title: String,
    accentColor: Color,
    summary: GenderSummary,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSuiviColors.current
    val emptyTrack = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$title (${summary.count})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(110.dp)) {
                val stroke = 14.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)

                drawArc(
                    color = emptyTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )

                if (summary.count > 0) {
                    val slices = listOf(
                        summary.maxMasteryCount to semantic.masteryMax,
                        summary.minMasteryCount to semantic.masteryMin,
                        summary.toImproveCount to semantic.masteryImprove,
                        summary.toReinforceCount to semantic.masteryReinforce
                    )
                    var startAngle = -90f
                    slices.forEach { (value, color) ->
                        if (value > 0) {
                            val sweep = (value.toFloat() / summary.count.toFloat()) * 360f
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${summary.masteryRatePct.roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = "Maîtrise",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Moy. ${String.format("%.1f", summary.avgTotal)} / 280",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Dual-line Trend Chart showing Weekly (S1–S4) or Monthly Grand Test progression for Garçon vs Fille.
 */
@Composable
fun PeriodEvolutionLineChart(
    points: List<PeriodTrendPoint>,
    minThresholdPct: Double,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSuiviColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val thresholdColor = semantic.masteryImprove

    Column(modifier = modifier.fillMaxWidth()) {
        if (points.isEmpty() || points.all { it.count == 0 }) {
            Text(
                text = "Enregistrez des évaluations pour visualiser la courbe de progression par genre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            return
        }

        GenderLegendRow()
        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val width = size.width
            val height = size.height
            val padX = 16.dp.toPx()
            val padY = 12.dp.toPx()
            val usableW = (width - padX * 2).coerceAtLeast(1f)
            val usableH = (height - padY * 2).coerceAtLeast(1f)

            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = padY + usableH * (1f - frac)
                drawLine(
                    color = gridColor,
                    start = Offset(padX, y),
                    end = Offset(width - padX, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Threshold dashed line
            val thY = padY + usableH * (1f - (minThresholdPct.toFloat() / 100f).coerceIn(0f, 1f))
            drawLine(
                color = thresholdColor.copy(alpha = 0.8f),
                start = Offset(padX, thY),
                end = Offset(width - padX, thY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            val stepX = if (points.size > 1) usableW / (points.size - 1) else 0f

            fun buildPath(selector: (PeriodTrendPoint) -> Double): Pair<Path, List<Offset>> {
                val path = Path()
                val offsets = mutableListOf<Offset>()
                var started = false
                points.forEachIndexed { idx, pt ->
                    if (pt.count > 0) {
                        val x = if (points.size == 1) width / 2f else padX + idx * stepX
                        val y = padY + usableH * (1f - (selector(pt).toFloat() / 100f).coerceIn(0f, 1f))
                        offsets.add(Offset(x, y))
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }
                return path to offsets
            }

            val (boyPath, boyDots) = buildPath { it.boysMasteryPct }
            val (girlPath, girlDots) = buildPath { it.girlsMasteryPct }

            if (boyDots.size >= 2) {
                drawPath(boyPath, color = semantic.boy, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            if (girlDots.size >= 2) {
                drawPath(girlPath, color = semantic.girl, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }

            boyDots.forEach { pt ->
                drawCircle(color = semantic.boy, radius = 5.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
            }
            girlDots.forEach { pt ->
                drawCircle(color = semantic.girl, radius = 5.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { pt ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (pt.count > 0) {
                        Text(
                            text = "${pt.overallMasteryPct.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
