package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Assessment
import com.example.data.ClassStat
import com.example.data.School
import com.example.data.StatisticsEngine
import com.example.data.Student
import com.example.ui.components.DecisionBadge
import com.example.ui.components.DiagnosticBadge
import com.example.ui.components.EmptyPromptCard
import com.example.ui.components.GenderActivityBarChart
import com.example.ui.components.GenderDecisionDonutSection
import com.example.ui.components.GenderPill
import com.example.ui.components.SectionCard
import com.example.ui.components.diagnosticColor
import com.example.ui.components.formatScoreInput
import com.example.ui.theme.LocalSuiviColors
import com.example.viewmodel.SuiviDashboardData
import kotlin.math.roundToInt

@Composable
fun SchoolsScreen(
    data: SuiviDashboardData,
    onAddSchool: () -> Unit,
    onEditSchool: (School) -> Unit,
    onDeleteSchool: (School) -> Unit,
    onBootstrap40Schools: () -> Unit,
    onGenerateMissingCodes: () -> Unit,
    onSetAccessCode: (School) -> Unit,
    onRequestUnlockSchool: (school: School, onUnlocked: () -> Unit) -> Unit,
    onLockSchool: (String) -> Unit,
    onAddAssessmentForSchool: (String) -> Unit,
    onAddAssessmentForStudent: (Student) -> Unit,
    onEditAssessment: (Assessment) -> Unit,
    onDeleteAssessment: (Assessment) -> Unit,
    onAddStudent: (School) -> Unit,
    onEditStudent: (School, Student) -> Unit,
    onDeleteStudent: (Student) -> Unit
) {
    var selectedSchoolId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val selectedStat = remember(selectedSchoolId, data.synthesis.classStats) {
        data.synthesis.classStats.firstOrNull { it.school.id == selectedSchoolId }
    }

    // Keep detail view gated if the school gets re-locked
    if (selectedStat != null && data.isSchoolUnlocked(selectedStat.school)) {
        BackHandler { selectedSchoolId = null }
        SchoolDetailView(
            stat = selectedStat,
            data = data,
            onBack = { selectedSchoolId = null },
            onEditSchool = { onEditSchool(selectedStat.school) },
            onSetAccessCode = { onSetAccessCode(selectedStat.school) },
            onLockSchool = {
                onLockSchool(selectedStat.school.id)
                selectedSchoolId = null
            },
            onAddAssessment = { onAddAssessmentForSchool(selectedStat.school.id) },
            onAddAssessmentForStudent = onAddAssessmentForStudent,
            onEditAssessment = onEditAssessment,
            onDeleteAssessment = onDeleteAssessment,
            onAddStudent = { onAddStudent(selectedStat.school) },
            onEditStudent = { student -> onEditStudent(selectedStat.school, student) },
            onDeleteStudent = onDeleteStudent
        )
        return
    }

    val unprotectedCount = remember(data.schools) { data.schools.count { !it.hasAccessCode } }
    val filteredStats = remember(data.synthesis.classStats, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) data.synthesis.classStats
        else data.synthesis.classStats.filter {
            it.school.name.lowercase().contains(q) ||
                it.school.code.lowercase().contains(q) ||
                it.school.zone.lowercase().contains(q) ||
                it.school.className.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("schools_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            data.confidentialSchool != null -> "Mon École CM2 — ${data.confidentialSchool.code}"
                            !data.isAdmin -> "Mon École CM2"
                            else -> "Réseau des 40 Écoles CM2"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (data.isAdmin) {
                            "${data.schools.size} école(s) • ${data.schools.count { it.hasAccessCode }} protégée(s) par code d'accès"
                        } else {
                            "Accès confidentiel limité à votre école • ${data.students.size} élève(s) mémorisé(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (data.isAdmin && data.confidentialSchool == null) {
                    OutlinedButton(
                        onClick = onAddSchool,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_school_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("École")
                    }
                }
            }
        }

        if (data.isAdmin && data.confidentialSchool == null && data.schools.size < 40) {
            item {
                Button(
                    onClick = onBootstrap40Schools,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bootstrap_40_schools_button")
                ) {
                    Icon(Icons.Rounded.School, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (data.schools.isEmpty()) "Initialiser les 40 écoles CM2 avec codes d'accès"
                        else "Compléter les ${40 - data.schools.size} écoles CM2 restantes"
                    )
                }
            }
        }

        if (data.isAdmin && data.confidentialSchool == null && unprotectedCount > 0) {
            item {
                OutlinedButton(
                    onClick = onGenerateMissingCodes,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_missing_codes_button")
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Générer les codes d'accès manquants ($unprotectedCount école(s))")
                }
            }
        }

        if (data.schools.size > 1) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Rechercher une école, un code ou une zone...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schools_search_input")
                )
            }
        }

        if (filteredStats.isEmpty()) {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.School,
                    title = if (data.schools.isEmpty()) "Aucune école CM2" else "Aucun résultat",
                    description = when {
                        data.schools.isEmpty() && data.isAdmin ->
                            "Initialisez les 40 écoles CM2 avec un code d'accès confidentiel par école."
                        data.schools.isEmpty() ->
                            "Aucune école n'est encore assignée à votre compte. Contactez votre administrateur."
                        else ->
                            "Aucune école ne correspond à « $search »."
                    },
                    actionLabel = if (data.isAdmin && data.schools.isEmpty()) "Initialiser les 40 écoles CM2" else null,
                    onAction = if (data.isAdmin && data.schools.isEmpty()) onBootstrap40Schools else null
                )
            }
        } else {
            items(filteredStats, key = { it.school.id }) { stat ->
                val isUnlocked = data.isSchoolUnlocked(stat.school)
                SchoolStatCard(
                    stat = stat,
                    isAdmin = data.isAdmin,
                    isUnlocked = isUnlocked,
                    isConfidentialMode = data.confidentialSchool != null,
                    onClick = {
                        if (isUnlocked) {
                            selectedSchoolId = stat.school.id
                        } else {
                            onRequestUnlockSchool(stat.school) {
                                selectedSchoolId = stat.school.id
                            }
                        }
                    },
                    onEdit = { onEditSchool(stat.school) },
                    onDelete = { onDeleteSchool(stat.school) },
                    onSetAccessCode = { onSetAccessCode(stat.school) },
                    onLock = { onLockSchool(stat.school.id) },
                    onAddTest = {
                        if (isUnlocked) {
                            onAddAssessmentForSchool(stat.school.id)
                        } else {
                            onRequestUnlockSchool(stat.school) {
                                onAddAssessmentForSchool(stat.school.id)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SchoolStatCard(
    stat: ClassStat,
    isAdmin: Boolean,
    isUnlocked: Boolean,
    isConfidentialMode: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetAccessCode: () -> Unit,
    onLock: () -> Unit,
    onAddTest: () -> Unit
) {
    val semantic = LocalSuiviColors.current
    val school = stat.school
    val isLocked = school.hasAccessCode && !isUnlocked

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isLocked) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("school_card_${school.code}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = school.code,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = school.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(school.className)
                                if (school.zone.isNotBlank()) append(" • ${school.zone}")
                                if (school.totalEnrolled > 0) append(" • ${school.totalEnrolled} inscrits")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isAdmin) {
                    Row {
                        IconButton(onClick = onSetAccessCode) {
                            Icon(
                                Icons.Rounded.Key,
                                contentDescription = "Gérer le code d'accès",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.cd_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isConfidentialMode) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Confidentiality status strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = when {
                    !school.hasAccessCode -> MaterialTheme.colorScheme.secondary
                    isLocked -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> semantic.masteryMax
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                !school.hasAccessCode -> Icons.Rounded.Shield
                                isLocked -> Icons.Rounded.Lock
                                else -> Icons.Rounded.LockOpen
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = when {
                                !school.hasAccessCode -> "Code d'accès non défini"
                                isLocked -> "Confidentiel • Code requis"
                                else -> "Accès déverrouillé"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor
                        )
                    }
                }

                if (isAdmin && school.hasAccessCode && isUnlocked && !isConfidentialMode) {
                    OutlinedButton(
                        onClick = onLock,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verrouiller", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isLocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (stat.evaluatedCount > 0) {
                            "${stat.evaluatedCount} évaluation(s) protégée(s) • Taux ${stat.masteryRatePct.roundToInt()}%"
                        } else {
                            "Saisissez le code d'accès pour ouvrir cette école"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ouvrir", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (stat.evaluatedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stat.evaluatedCount} copies • Moy. ${String.format("%.1f", stat.avgTotal)}/280",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DiagnosticBadge(status = stat.status)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (stat.masteryRatePct / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = diagnosticColor(stat.status),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Maîtrise : ${stat.masteryRatePct.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "G ${stat.boysMasteryPct.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.boy
                        )
                        Text(
                            text = "F ${stat.girlsMasteryPct.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.girl
                        )
                    }
                    if (stat.weakestActivity != null) {
                        Text(
                            text = "À renf. : ${stat.weakestActivity.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.masteryReinforce
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aucun test saisi pour cette période",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onAddTest,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Saisir un test", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolDetailView(
    stat: ClassStat,
    data: SuiviDashboardData,
    onBack: () -> Unit,
    onEditSchool: () -> Unit,
    onSetAccessCode: () -> Unit,
    onLockSchool: () -> Unit,
    onAddAssessment: () -> Unit,
    onAddAssessmentForStudent: (Student) -> Unit,
    onEditAssessment: (Assessment) -> Unit,
    onDeleteAssessment: (Assessment) -> Unit,
    onAddStudent: () -> Unit,
    onEditStudent: (Student) -> Unit,
    onDeleteStudent: (Student) -> Unit
) {
    val schoolAssessments = remember(data.filteredAssessments, stat.school.id) {
        data.filteredAssessments.filter { it.schoolId == stat.school.id }
    }
    val schoolStudents = remember(data.students, stat.school.id) {
        data.studentsForSchool(stat.school.id)
    }
    val classSynthesis = remember(stat.school, schoolAssessments, data.settings) {
        StatisticsEngine.compute(
            schools = listOf(stat.school),
            filteredAssessments = schoolAssessments,
            allAssessmentsForTrends = schoolAssessments,
            settings = data.settings
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("school_detail_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("school_detail_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                    Column {
                        Text(
                            text = "${stat.school.code} • ${stat.school.name}",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stat.school.className} • ${stat.school.zone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (data.isAdmin) {
                    Row {
                        IconButton(onClick = onSetAccessCode) {
                            Icon(Icons.Rounded.Key, contentDescription = "Code d'accès")
                        }
                        if (stat.school.hasAccessCode && data.confidentialSchool == null) {
                            IconButton(onClick = onLockSchool) {
                                Icon(Icons.Rounded.Lock, contentDescription = "Verrouiller l'école")
                            }
                        }
                        IconButton(onClick = onEditSchool) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_edit))
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddAssessment,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("school_detail_add_test")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Saisir un test", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onAddStudent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("school_detail_add_student")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Élève")
                }
            }
        }

        // Persistent Student Roster Card
        item {
            SectionCard(
                title = "Élèves CM2 mémorisés (${schoolStudents.size})",
                subtitle = "Enregistrés automatiquement — sélection directe sans ressaisie aux prochains tests"
            ) {
                if (schoolStudents.isEmpty()) {
                    Text(
                        text = "Aucun élève enregistré pour cette classe. Ajoutez un élève ou saisissez directement un premier test : il sera mémorisé automatiquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        schoolStudents.forEach { stu ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_row_${stu.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = stu.fullName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            GenderPill(stu.genderEnum)
                                        }
                                        if (stu.matricule.isNotBlank()) {
                                            Text(
                                                text = "Matricule : ${stu.matricule}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = { onAddAssessmentForStudent(stu) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("grade_student_${stu.id}")
                                        ) {
                                            Text("Noter", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(onClick = { onEditStudent(stu) }) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = stringResource(R.string.cd_edit),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteStudent(stu) }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = stringResource(R.string.cd_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (schoolAssessments.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Résultats Garçon vs Fille — ${stat.school.className}",
                    subtitle = "Moyenne ${String.format("%.1f", classSynthesis.avgTotalScore)}/280 • Maîtrise ${classSynthesis.overallMasteryPct.roundToInt()}%"
                ) {
                    GenderDecisionDonutSection(
                        boys = classSynthesis.boysSummary,
                        girls = classSynthesis.girlsSummary
                    )
                }
            }

            item {
                SectionCard(
                    title = "Activités de la classe (Garçon vs Fille)",
                    subtitle = "Points forts : ${stat.strongestActivity?.label ?: "—"} • À renforcer : ${stat.weakestActivity?.label ?: "—"}"
                ) {
                    GenderActivityBarChart(
                        activityStats = classSynthesis.activityStats,
                        minThresholdPct = data.settings.minMasteryPct,
                        maxThresholdPct = data.settings.maxMasteryPct
                    )
                }
            }

            items(schoolAssessments, key = { it.id }) { eval ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(eval.studentName, style = MaterialTheme.typography.titleMedium)
                                GenderPill(eval.genderEnum)
                            }
                            Row {
                                IconButton(onClick = { onEditAssessment(eval) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_edit))
                                }
                                IconButton(onClick = { onDeleteAssessment(eval) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.cd_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Text(
                            text = buildString {
                                append("${eval.periodLabel} • LC ${formatScoreInput(eval.lcTotal)}/100 • Math ${formatScoreInput(eval.mathTotal)}/100 • ESVS ${formatScoreInput(eval.esvsTotal)}/80")
                                if (eval.recordedBy.isNotBlank()) append(" • Par ${eval.recordedBy}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total : ${formatScoreInput(eval.totalScore)} / 280",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            DecisionBadge(decision = eval.decisionEnum)
                        }
                    }
                }
            }
        } else {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.Add,
                    title = "Aucune copie enregistrée",
                    description = "Saisissez le premier test hebdomadaire ou grand test mensuel pour cette classe CM2."
                )
            }
        }
    }
}
