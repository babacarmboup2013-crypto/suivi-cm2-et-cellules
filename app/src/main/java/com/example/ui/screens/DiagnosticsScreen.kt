package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActivityStat
import com.example.data.ClassStat
import com.example.data.DiagnosticStatus
import com.example.data.DomainStat
import com.example.data.TestType
import com.example.ui.components.DiagnosticBadge
import com.example.ui.components.EmptyPromptCard
import com.example.ui.components.PeriodFilterStrip
import com.example.ui.components.SectionCard
import com.example.ui.components.diagnosticColor
import com.example.ui.theme.LocalSuiviColors
import com.example.viewmodel.SuiviDashboardData
import kotlin.math.roundToInt

@Composable
fun DiagnosticsScreen(
    data: SuiviDashboardData,
    onTestTypeFilter: (TestType?) -> Unit,
    onMonthFilter: (String?) -> Unit,
    onWeekFilter: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    onAddAssessment: () -> Unit
) {
    val synthesis = data.synthesis
    val semantic = LocalSuiviColors.current
    val evaluatedClasses = synthesis.classStats.filter { it.evaluatedCount > 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Statistiques par Activités & Classes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Diagnostic pédagogique : points forts, points à améliorer et points à renforcer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            PeriodFilterStrip(
                filters = data.filters,
                onTestTypeChange = onTestTypeFilter,
                onMonthChange = onMonthFilter,
                onWeekChange = onWeekFilter,
                onClearFilters = onClearFilters
            )
        }

        if (synthesis.totalAssessments == 0) {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.Analytics,
                    title = "Aucune statistique disponible",
                    description = "Saisissez des résultats de tests hebdomadaires ou mensuels pour identifier automatiquement les points forts, les points à améliorer et les points à renforcer par activité et par classe.",
                    actionLabel = if (data.schools.isNotEmpty()) "Saisir un test" else null,
                    onAction = if (data.schools.isNotEmpty()) onAddAssessment else null
                )
            }
            return@LazyColumn
        }

        // 1. Diagnostic 3-Bucket Summary (Points forts / À améliorer / À renforcer)
        item {
            SectionCard(
                title = "Diagnostic par Activités",
                subtitle = "Basé sur vos seuils : Fort ≥ ${data.settings.maxMasteryPct.roundToInt()}% • Min ≥ ${data.settings.minMasteryPct.roundToInt()}%"
            ) {
                DiagnosticBucket(
                    title = "Points forts (Points bons)",
                    icon = Icons.Rounded.CheckCircle,
                    color = semantic.masteryMax,
                    items = synthesis.strengths,
                    emptyText = "Aucune activité n'atteint encore le seuil de maîtrise maximale."
                )
                Spacer(Modifier.height(12.dp))
                DiagnosticBucket(
                    title = "Points à améliorer",
                    icon = Icons.Rounded.TrendingUp,
                    color = semantic.masteryImprove,
                    items = synthesis.toImprove,
                    emptyText = "Aucune activité dans la zone intermédiaire."
                )
                Spacer(Modifier.height(12.dp))
                DiagnosticBucket(
                    title = "Points à renforcer (Remédiation prioritaire)",
                    icon = Icons.Rounded.PriorityHigh,
                    color = semantic.masteryReinforce,
                    items = synthesis.toReinforce,
                    emptyText = "Bravo ! Aucune activité sous le seuil minimal."
                )
            }
        }

        // 2. Domain Summary (LC /100, Maths /100, ESVS /80: DM /40 & DD /40)
        item {
            SectionCard(
                title = "Performance par Domaine Disciplinaire",
                subtitle = "Langue & Communication • Mathématiques • ESVS (DM + DD)"
            ) {
                synthesis.domainStats.forEachIndexed { idx, domain ->
                    DomainStatRow(domain = domain)
                    if (idx < synthesis.domainStats.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }

        // 3. Detailed per-activity mastery bars
        item {
            SectionCard(
                title = "Détail des 8 Activités (Ressources & Compétences)",
                subtitle = "Comparaison du taux de maîtrise Global, Garçon (G) et Fille (F)"
            ) {
                synthesis.activityStats.forEachIndexed { idx, stat ->
                    ActivityProgressRow(stat = stat)
                    if (idx < synthesis.activityStats.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // 4. Statistics by Class (Ranking across the 40 CM2 classes)
        item {
            val strongClasses = evaluatedClasses.filter { it.status == DiagnosticStatus.POINT_FORT }
            val improveClasses = evaluatedClasses.filter { it.status == DiagnosticStatus.A_AMELIORER }
            val reinforceClasses = evaluatedClasses.filter { it.status == DiagnosticStatus.A_RENFORCER }

            SectionCard(
                title = "Statistiques par Classes CM2 (${evaluatedClasses.size} évaluées)",
                subtitle = "Forts: ${strongClasses.size} • À améliorer: ${improveClasses.size} • À renforcer: ${reinforceClasses.size}"
            ) {
                evaluatedClasses.forEachIndexed { idx, cls ->
                    ClassDiagnosticRow(rank = idx + 1, stat = cls)
                    if (idx < evaluatedClasses.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticBucket(
    title: String,
    icon: ImageVector,
    color: Color,
    items: List<ActivityStat>,
    emptyText: String
) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$title (${items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = color
                )
            }
            Spacer(Modifier.height(6.dp))
            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• ${stat.key.label} (/${stat.key.maxScore.toInt()})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${stat.masteryRatePct.roundToInt()}% (Moy. ${String.format("%.1f", stat.avgScore)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainStatRow(domain: DomainStat) {
    val semantic = LocalSuiviColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Moy. ${String.format("%.1f", domain.avgScore)} / ${domain.maxScore.toInt()} • G: ${domain.boysMasteryPct.roundToInt()}% • F: ${domain.girlsMasteryPct.roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${domain.masteryRatePct.roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = diagnosticColor(domain.status)
                )
                DiagnosticBadge(status = domain.status)
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (domain.masteryRatePct / 100.0).toFloat().coerceIn(0f, 1f) },
            color = diagnosticColor(domain.status),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
        )
    }
}

@Composable
private fun ActivityProgressRow(stat: ActivityStat) {
    val semantic = LocalSuiviColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stat.key.label} (/${stat.key.maxScore.toInt()})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "G ${stat.boysMasteryPct.roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.boy,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "F ${stat.girlsMasteryPct.roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.girl,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${stat.masteryRatePct.roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = diagnosticColor(stat.status)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (stat.masteryRatePct / 100.0).toFloat().coerceIn(0f, 1f) },
            color = diagnosticColor(stat.status),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
        )
    }
}

@Composable
private fun ClassDiagnosticRow(rank: Int, stat: ClassStat) {
    val semantic = LocalSuiviColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#$rank • ${stat.school.code} ${stat.school.name} (${stat.school.className})",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stat.evaluatedCount} copies • Moy. ${String.format("%.1f", stat.avgTotal)}/280 • G ${stat.boysMasteryPct.roundToInt()}% / F ${stat.girlsMasteryPct.roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (stat.strongestActivity != null) {
                        Text(
                            text = "Fort : ${stat.strongestActivity.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.masteryMax
                        )
                    }
                    if (stat.weakestActivity != null) {
                        Text(
                            text = "À renforcer : ${stat.weakestActivity.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.masteryReinforce
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stat.masteryRatePct.roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = diagnosticColor(stat.status)
                )
                DiagnosticBadge(status = stat.status)
            }
        }
    }
}
