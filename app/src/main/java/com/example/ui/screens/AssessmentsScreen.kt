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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Assessment
import com.example.data.Baremes
import com.example.data.Gender
import com.example.data.MasteryDecision
import com.example.data.School
import com.example.data.TestType
import com.example.data.ThresholdSettings
import com.example.ui.components.DecisionBadge
import com.example.ui.components.EmptyPromptCard
import com.example.ui.components.GenderPill
import com.example.ui.components.PeriodFilterStrip
import com.example.ui.components.formatScoreInput
import com.example.viewmodel.SuiviDashboardData

@Composable
fun AssessmentsScreen(
    data: SuiviDashboardData,
    onTestTypeFilter: (TestType?) -> Unit,
    onMonthFilter: (String?) -> Unit,
    onWeekFilter: (Int?) -> Unit,
    onGenderFilter: (Gender?) -> Unit,
    onClearFilters: () -> Unit,
    onRequestUnlockSchool: (School) -> Unit,
    onAddAssessment: () -> Unit,
    onEditAssessment: (Assessment) -> Unit,
    onDeleteAssessment: (Assessment) -> Unit
) {
    var search by remember { mutableStateOf("") }

    val schoolsById = remember(data.schools) { data.schools.associateBy { it.id } }

    // Partition assessments into unlocked (visible) vs locked (masked for confidentiality)
    val (visibleAssessments, lockedBySchool) = remember(data.filteredAssessments, schoolsById, data.unlockedSchoolIds, data.confidentialSchool) {
        val visible = mutableListOf<Assessment>()
        val lockedMap = linkedMapOf<School, Int>()
        data.filteredAssessments.forEach { eval ->
            val school = schoolsById[eval.schoolId]
            if (school == null || data.isSchoolUnlocked(school)) {
                visible.add(eval)
            } else {
                lockedMap[school] = (lockedMap[school] ?: 0) + 1
            }
        }
        visible to lockedMap
    }

    val displayed = remember(visibleAssessments, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) visibleAssessments
        else visibleAssessments.filter {
            it.studentName.lowercase().contains(q) ||
                it.schoolName.lowercase().contains(q) ||
                it.className.lowercase().contains(q) ||
                it.month.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("assessments_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Registre des Tests CM2",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${displayed.size} affiché(s) • ${lockedBySchool.values.sum()} protégé(s) par code d'accès",
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

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = data.filters.gender == null,
                    onClick = { onGenderFilter(null) },
                    label = { Text("Tous genres") }
                )
                FilterChip(
                    selected = data.filters.gender == Gender.GARCON,
                    onClick = { onGenderFilter(if (data.filters.gender == Gender.GARCON) null else Gender.GARCON) },
                    label = { Text("Garçons") }
                )
                FilterChip(
                    selected = data.filters.gender == Gender.FILLE,
                    onClick = { onGenderFilter(if (data.filters.gender == Gender.FILLE) null else Gender.FILLE) },
                    label = { Text("Filles") }
                )
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher un élève ou une école déverrouillée...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assessments_search_input")
            )
        }

        // Protected / locked school confidentiality cards
        if (lockedBySchool.isNotEmpty()) {
            items(lockedBySchool.entries.toList(), key = { "locked_${it.key.id}" }) { (school, count) ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${school.code} • ${school.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$count résultat(s) élève masqué(s) pour confidentialité",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onRequestUnlockSchool(school) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Code", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (displayed.isEmpty() && lockedBySchool.isEmpty()) {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.Assignment,
                    title = "Aucun test trouvé",
                    description = if (data.schools.isEmpty()) {
                        if (data.isAdmin) {
                            "Commencez par initialiser ou ajouter une école CM2 dans l'onglet 40 Écoles."
                        } else {
                            "Aucune école n'est encore affectée à votre compte. Contactez votre administrateur."
                        }
                    } else {
                        "Saisissez un test hebdomadaire ou un grand test mensuel pour suivre la progression de vos classes CM2."
                    },
                    actionLabel = if (data.schools.isNotEmpty()) "Saisir un test" else null,
                    onAction = if (data.schools.isNotEmpty()) onAddAssessment else null
                )
            }
        } else {
            items(displayed, key = { it.id }) { assessment ->
                AssessmentCard(
                    assessment = assessment,
                    settings = data.settings,
                    onEdit = { onEditAssessment(assessment) },
                    onDelete = { onDeleteAssessment(assessment) }
                )
            }
        }
    }
}

@Composable
private fun AssessmentCard(
    assessment: Assessment,
    settings: ThresholdSettings,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val computedDecision = MasteryDecision.compute(assessment.totalScore, Baremes.TOTAL_MAX, settings)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("assessment_card_${assessment.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = assessment.studentName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        GenderPill(assessment.genderEnum)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("${assessment.schoolName} (${assessment.className}) • ${assessment.periodLabel}")
                            if (assessment.recordedBy.isNotBlank()) append(" • Par ${assessment.recordedBy}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DomainMiniChip(
                    title = "LC /100",
                    detail = "R:${formatScoreInput(assessment.lcRessources)}/40 • C:${formatScoreInput(assessment.lcCompetences)}/60",
                    total = formatScoreInput(assessment.lcTotal),
                    modifier = Modifier.weight(1f)
                )
                DomainMiniChip(
                    title = "Math /100",
                    detail = "R:${formatScoreInput(assessment.mathRessources)}/40 • C:${formatScoreInput(assessment.mathCompetences)}/60",
                    total = formatScoreInput(assessment.mathTotal),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            DomainMiniChip(
                title = "ESVS /80",
                detail = "DM (R:${formatScoreInput(assessment.dmRessources)}/24, C:${formatScoreInput(assessment.dmCompetences)}/16) • DD (R:${formatScoreInput(assessment.ddRessources)}/24, C:${formatScoreInput(assessment.ddCompetences)}/16)",
                total = formatScoreInput(assessment.esvsTotal),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatScoreInput(assessment.totalScore),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "/ 280 pts (${((assessment.totalScore / 280.0) * 100.0).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                DecisionBadge(decision = computedDecision)
            }
        }
    }
}

@Composable
private fun DomainMiniChip(
    title: String,
    detail: String,
    total: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = total,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
