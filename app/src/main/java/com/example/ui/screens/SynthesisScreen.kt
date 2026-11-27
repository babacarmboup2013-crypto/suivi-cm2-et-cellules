package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ActivityStat
import com.example.data.TestType
import com.example.ui.components.DiagnosticBadge
import com.example.ui.components.EmptyPromptCard
import com.example.ui.components.GenderActivityBarChart
import com.example.ui.components.GenderDecisionDonutSection
import com.example.ui.components.KpiTile
import com.example.ui.components.PeriodEvolutionLineChart
import com.example.ui.components.PeriodFilterStrip
import com.example.ui.components.SectionCard
import com.example.ui.components.formatScoreInput
import com.example.ui.theme.LocalSuiviColors
import com.example.viewmodel.SuiviDashboardData
import kotlin.math.roundToInt

@Composable
fun SynthesisScreen(
    data: SuiviDashboardData,
    onTestTypeFilter: (TestType?) -> Unit,
    onMonthFilter: (String?) -> Unit,
    onWeekFilter: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    onBootstrap40Schools: () -> Unit,
    onAddAssessment: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val synthesis = data.synthesis
    val settings = data.settings
    val semantic = LocalSuiviColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("synthesis_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // District header banner
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!data.isAdmin && data.confidentialSchool != null) {
                                "${data.confidentialSchool.code} • ${data.confidentialSchool.name}"
                            } else {
                                settings.districtName.ifBlank { "Circonscription CM2 — 40 Écoles" }
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Année ${settings.academicYear} • Seuils : Min ${settings.minMasteryPct.roundToInt()}% (${formatScoreInput(settings.pointThreshold(280.0, settings.minMasteryPct))}/280) • Max ${settings.maxMasteryPct.roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    if (data.isAdmin) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("open_thresholds_quick_button")
                        ) {
                            Text("Seuils")
                        }
                    }
                }
            }
        }

        // Global Period Filter Strip (Hebdo / Mensuel + Mois + Semaine)
        item {
            PeriodFilterStrip(
                filters = data.filters,
                onTestTypeChange = onTestTypeFilter,
                onMonthChange = onMonthFilter,
                onWeekChange = onWeekFilter,
                onClearFilters = onClearFilters
            )
        }

        // Quick 40 schools setup banner if not yet initialized (Admin only)
        if (data.schools.isEmpty() && data.isAdmin) {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.School,
                    title = "Démarrez le suivi des 40 écoles CM2",
                    description = "Initialisez en un clic la grille de vos 40 écoles CM2 (EC-01 à EC-40), puis créez vos utilisateurs de saisie et enregistrez les tests.",
                    actionLabel = "Initialiser les 40 écoles CM2",
                    onAction = onBootstrap40Schools
                )
            }
        }

        // 2x2 KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (data.isAdmin) {
                        KpiTile(
                            label = "Écoles CM2",
                            value = "${synthesis.activeSchoolsCount}/${synthesis.totalSchoolsConfigured.coerceAtLeast(40)}",
                            caption = "${synthesis.totalSchoolsConfigured} configurées",
                            icon = Icons.Rounded.School,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        KpiTile(
                            label = "Élèves mémorisés",
                            value = "${data.students.size}",
                            caption = data.confidentialSchool?.className ?: "classe CM2",
                            icon = Icons.Rounded.School,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    KpiTile(
                        label = "Copies évaluées",
                        value = "${synthesis.totalAssessments}",
                        caption = "G: ${synthesis.boysSummary.count} • F: ${synthesis.girlsSummary.count}",
                        icon = Icons.Rounded.Groups,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiTile(
                        label = "Taux de maîtrise",
                        value = "${synthesis.overallMasteryPct.roundToInt()}%",
                        caption = "Seuil ≥ ${settings.minMasteryPct.roundToInt()}%",
                        icon = Icons.Rounded.TrendingUp,
                        accentColor = if (synthesis.overallMasteryPct >= settings.minMasteryPct) {
                            semantic.masteryMax
                        } else {
                            semantic.masteryReinforce
                        },
                        modifier = Modifier.weight(1f)
                    )
                    KpiTile(
                        label = "Moyenne globale",
                        value = "${String.format("%.1f", synthesis.avgTotalScore)}",
                        caption = "sur 280 points",
                        icon = Icons.Rounded.Assessment,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Empty assessment CTA when schools exist but no tests recorded yet
        if (synthesis.totalAssessments == 0 && data.schools.isNotEmpty()) {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.Add,
                    title = "Aucun résultat pour cette période",
                    description = "Enregistrez un test hebdomadaire ou un grand test mensuel pour générer automatiquement les diagrammes Garçon/Fille et la synthèse globale.",
                    actionLabel = "Saisir un test CM2",
                    onAction = onAddAssessment
                )
            }
        }

        // Diagram 1: Gender Decision Donut Comparison (Garçon vs Fille)
        item {
            SectionCard(
                title = "Synthèse par Genre — Garçon vs Fille",
                subtitle = "Répartition des décisions (Maîtrise Max, Min, À améliorer, À renforcer)"
            ) {
                GenderDecisionDonutSection(
                    boys = synthesis.boysSummary,
                    girls = synthesis.girlsSummary
                )
            }
        }

        // Diagram 2: Grouped Bar Chart by Activity (Garçon vs Fille) with Threshold Lines
        item {
            SectionCard(
                title = "Maîtrise par Activité selon le Genre",
                subtitle = "Pourcentage d'élèves atteignant le seuil minimal sur chaque barème"
            ) {
                GenderActivityBarChart(
                    activityStats = synthesis.activityStats,
                    minThresholdPct = settings.minMasteryPct,
                    maxThresholdPct = settings.maxMasteryPct
                )
            }
        }

        // Diagram 3: Weekly & Monthly Evolution by Gender
        item {
            SectionCard(
                title = "Évolution Hebdomadaire (S1 – S4)",
                subtitle = "Courbe de maîtrise Garçon vs Fille sur les tests hebdomadaires"
            ) {
                PeriodEvolutionLineChart(
                    points = synthesis.weeklyTrend,
                    minThresholdPct = settings.minMasteryPct
                )
            }
        }

        item {
            SectionCard(
                title = "Évolution des Grands Tests Mensuels",
                subtitle = "Progression mensuelle Garçon vs Fille sur l'année scolaire"
            ) {
                PeriodEvolutionLineChart(
                    points = synthesis.monthlyTrend,
                    minThresholdPct = settings.minMasteryPct
                )
            }
        }

        // Official Barème & Threshold Synthesis Table
        item {
            SectionCard(
                title = "Synthèse Globale des Barèmes & Seuils",
                subtitle = "LC (40/60) • Maths (40/60) • ESVS 80 (DM 24/16, DD 24/16) • Total /280"
            ) {
                BaremeHeaderRow()
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                synthesis.activityStats.forEachIndexed { idx, stat ->
                    BaremeStatRow(stat = stat, hasData = synthesis.totalAssessments > 0)
                    if (idx < synthesis.activityStats.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Quick action footer if schools < 40 (Admin only)
        if (data.isAdmin && data.schools.isNotEmpty() && data.schools.size < 40) {
            item {
                Button(
                    onClick = onBootstrap40Schools,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complete_40_schools_button")
                ) {
                    Icon(Icons.Rounded.School, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compléter automatiquement jusqu'à 40 écoles CM2")
                }
            }
        }
    }
}

@Composable
private fun BaremeHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Activité (Barème)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(2.1f)
        )
        Text(
            text = "Seuil",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = "G / F",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = "Global",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun BaremeStatRow(stat: ActivityStat, hasData: Boolean) {
    val semantic = LocalSuiviColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2.1f)) {
            Text(
                text = stat.key.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Moy. ${String.format("%.1f", stat.avgScore)} / ${stat.key.maxScore.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "≥${formatScoreInput(stat.minPointThreshold)}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f)
        )
        Row(
            modifier = Modifier.weight(1.2f),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (stat.boysCount > 0) "${stat.boysMasteryPct.roundToInt()}%" else "—",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = semantic.boy
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (stat.girlsCount > 0) "${stat.girlsMasteryPct.roundToInt()}%" else "—",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = semantic.girl
            )
        }
        Column(
            modifier = Modifier.weight(1.2f),
            horizontalAlignment = Alignment.End
        ) {
            if (hasData) {
                Text(
                    text = "${stat.masteryRatePct.roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                DiagnosticBadge(status = stat.status)
            } else {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
