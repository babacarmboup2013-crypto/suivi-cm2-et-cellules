package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Assessment
import com.example.data.Baremes
import com.example.data.Gender
import com.example.data.MasteryDecision
import com.example.data.SCHOOL_MONTHS
import com.example.data.School
import com.example.data.Student
import com.example.data.TestType
import com.example.data.ThresholdSettings
import com.example.ui.theme.LocalSuiviColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditAssessmentDialog(
    initial: Assessment?,
    schools: List<School>,
    allStudents: List<Student>,
    existingAssessments: List<Assessment>,
    preselectedSchoolId: String?,
    preselectedStudent: Student? = null,
    settings: ThresholdSettings,
    onDismiss: () -> Unit,
    onConfirm: (
        school: School,
        selectedStudentId: String?,
        studentName: String,
        gender: Gender,
        testType: TestType,
        month: String,
        weekNumber: Int,
        lcRes: Double,
        lcComp: Double,
        mathRes: Double,
        mathComp: Double,
        dmRes: Double,
        dmComp: Double,
        ddRes: Double,
        ddComp: Double
    ) -> Unit
) {
    val defaultSchool = schools.firstOrNull {
        it.id == (initial?.schoolId ?: preselectedStudent?.schoolId ?: preselectedSchoolId)
    } ?: schools.firstOrNull()

    var selectedSchool by remember { mutableStateOf(defaultSchool) }
    var schoolMenuExpanded by remember { mutableStateOf(false) }

    val schoolStudents = remember(allStudents, selectedSchool?.id) {
        val sid = selectedSchool?.id ?: return@remember emptyList()
        allStudents.filter { it.schoolId == sid }.sortedBy { it.fullName.lowercase() }
    }

    val initialLinkedStudent = remember(initial, preselectedStudent, schoolStudents) {
        when {
            preselectedStudent != null -> preselectedStudent
            initial != null && initial.studentId.isNotBlank() ->
                schoolStudents.firstOrNull { it.id == initial.studentId }
            initial != null ->
                schoolStudents.firstOrNull { it.normalizedName == initial.studentName.trim().lowercase() }
            else -> null
        }
    }

    var selectedStudent by remember { mutableStateOf(initialLinkedStudent) }
    var isNewStudentMode by remember {
        mutableStateOf(
            initial != null && initialLinkedStudent == null ||
                (preselectedStudent == null && schoolStudents.isEmpty())
        )
    }
    var studentMenuExpanded by remember { mutableStateOf(false) }

    var studentName by remember {
        mutableStateOf(
            preselectedStudent?.fullName ?: initial?.studentName ?: ""
        )
    }
    var gender by remember {
        mutableStateOf(
            preselectedStudent?.genderEnum ?: initial?.genderEnum ?: Gender.GARCON
        )
    }
    var testType by remember { mutableStateOf(initial?.testTypeEnum ?: TestType.HEBDOMADAIRE) }
    var month by remember { mutableStateOf(initial?.month ?: SCHOOL_MONTHS[1]) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var weekNumber by remember {
        mutableStateOf(if (initial != null && initial.weekNumber > 0) initial.weekNumber.toInt() else 1)
    }

    var lcResTxt by remember { mutableStateOf(initial?.lcRessources?.let { formatScoreInput(it) } ?: "") }
    var lcCompTxt by remember { mutableStateOf(initial?.lcCompetences?.let { formatScoreInput(it) } ?: "") }
    var mathResTxt by remember { mutableStateOf(initial?.mathRessources?.let { formatScoreInput(it) } ?: "") }
    var mathCompTxt by remember { mutableStateOf(initial?.mathCompetences?.let { formatScoreInput(it) } ?: "") }
    var dmResTxt by remember { mutableStateOf(initial?.dmRessources?.let { formatScoreInput(it) } ?: "") }
    var dmCompTxt by remember { mutableStateOf(initial?.dmCompetences?.let { formatScoreInput(it) } ?: "") }
    var ddResTxt by remember { mutableStateOf(initial?.ddRessources?.let { formatScoreInput(it) } ?: "") }
    var ddCompTxt by remember { mutableStateOf(initial?.ddCompetences?.let { formatScoreInput(it) } ?: "") }

    // Track which students in this school already have a grade for the currently selected period
    val gradedStudentIdsForPeriod = remember(existingAssessments, selectedSchool?.id, testType, month, weekNumber) {
        val sid = selectedSchool?.id ?: return@remember emptySet()
        val expectedWeek = if (testType == TestType.MENSUEL) 0L else weekNumber.toLong()
        existingAssessments
            .filter {
                it.schoolId == sid &&
                    it.testTypeEnum == testType &&
                    it.month == month &&
                    it.weekNumber == expectedWeek
            }
            .flatMap { listOfNotNull(it.studentId.takeIf { id -> id.isNotBlank() }, it.studentName.trim().lowercase()) }
            .toSet()
    }

    val lcRes = parseScore(lcResTxt, Baremes.LC_RESSOURCES_MAX)
    val lcComp = parseScore(lcCompTxt, Baremes.LC_COMPETENCES_MAX)
    val mathRes = parseScore(mathResTxt, Baremes.MATH_RESSOURCES_MAX)
    val mathComp = parseScore(mathCompTxt, Baremes.MATH_COMPETENCES_MAX)
    val dmRes = parseScore(dmResTxt, Baremes.DM_RESSOURCES_MAX)
    val dmComp = parseScore(dmCompTxt, Baremes.DM_COMPETENCES_MAX)
    val ddRes = parseScore(ddResTxt, Baremes.DD_RESSOURCES_MAX)
    val ddComp = parseScore(ddCompTxt, Baremes.DD_COMPETENCES_MAX)

    val lcTotal = lcRes + lcComp
    val mathTotal = mathRes + mathComp
    val esvsTotal = dmRes + dmComp + ddRes + ddComp
    val grandTotal = lcTotal + mathTotal + esvsTotal
    val liveDecision = MasteryDecision.compute(grandTotal, Baremes.TOTAL_MAX, settings)
    val semantic = LocalSuiviColors.current

    val effectiveStudentName = if (!isNewStudentMode && selectedStudent != null) {
        selectedStudent!!.fullName
    } else {
        studentName.trim()
    }
    val effectiveGender = if (!isNewStudentMode && selectedStudent != null) {
        selectedStudent!!.genderEnum
    } else {
        gender
    }
    val canSubmit = selectedSchool != null && effectiveStudentName.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Saisir un test CM2" else "Modifier le test CM2",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Total & Décision Header Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total : ${formatScoreInput(grandTotal)} / 280",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "LC ${formatScoreInput(lcTotal)}/100 • Math ${formatScoreInput(mathTotal)}/100 • ESVS ${formatScoreInput(esvsTotal)}/80",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DecisionBadge(decision = liveDecision, compact = true)
                    }
                }

                // School selector (locked on edit per immutable schoolId rule)
                ExposedDropdownMenuBox(
                    expanded = schoolMenuExpanded && initial == null && schools.size > 1,
                    onExpandedChange = {
                        if (initial == null && schools.size > 1) schoolMenuExpanded = it
                    }
                ) {
                    OutlinedTextField(
                        value = selectedSchool?.let { "${it.code} • ${it.name} (${it.className})" } ?: "Aucune école",
                        onValueChange = {},
                        readOnly = true,
                        enabled = initial == null && schools.size > 1,
                        label = { Text("École CM2") },
                        trailingIcon = {
                            if (initial == null && schools.size > 1) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolMenuExpanded)
                            }
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = schoolMenuExpanded && initial == null && schools.size > 1,
                        onDismissRequest = { schoolMenuExpanded = false }
                    ) {
                        schools.forEach { school ->
                            DropdownMenuItem(
                                text = { Text("${school.code} • ${school.name} (${school.className})") },
                                onClick = {
                                    selectedSchool = school
                                    val nextList = allStudents.filter { it.schoolId == school.id }
                                    selectedStudent = null
                                    studentName = ""
                                    isNewStudentMode = nextList.isEmpty()
                                    schoolMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Period selection BEFORE student picker so the already-graded checkmarks match the period!
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TestType.entries.forEach { type ->
                        FilterChip(
                            selected = testType == type,
                            onClick = { testType = type },
                            label = { Text(type.label) },
                            modifier = Modifier.testTag("test_type_chip_${type.name.lowercase()}")
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = monthMenuExpanded,
                        onExpandedChange = { monthMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = month,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mois") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = monthMenuExpanded,
                            onDismissRequest = { monthMenuExpanded = false }
                        ) {
                            SCHOOL_MONTHS.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        month = m
                                        monthMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (testType == TestType.HEBDOMADAIRE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..4).forEach { w ->
                                FilterChip(
                                    selected = weekNumber == w,
                                    onClick = { weekNumber = w },
                                    label = { Text("S$w") }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // --- PERSISTENT STUDENT SELECTOR (Zero re-typing for returning students) ---
                if (schoolStudents.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isNewStudentMode,
                            onClick = { isNewStudentMode = false },
                            leadingIcon = {
                                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Liste (${schoolStudents.size})") },
                            modifier = Modifier.testTag("mode_existing_student_chip")
                        )
                        FilterChip(
                            selected = isNewStudentMode,
                            onClick = {
                                isNewStudentMode = true
                                selectedStudent = null
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Nouvel élève") },
                            modifier = Modifier.testTag("mode_new_student_chip")
                        )
                    }
                }

                if (!isNewStudentMode && schoolStudents.isNotEmpty()) {
                    // Dropdown picker from saved school students
                    ExposedDropdownMenuBox(
                        expanded = studentMenuExpanded,
                        onExpandedChange = { studentMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedStudent?.let { "${it.fullName} (${it.genderEnum.label})" }
                                ?: "Sélectionner un élève enregistré...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Élève mémorisé dans la base") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("assessment_student_picker")
                        )
                        ExposedDropdownMenu(
                            expanded = studentMenuExpanded,
                            onDismissRequest = { studentMenuExpanded = false }
                        ) {
                            schoolStudents.forEach { stu ->
                                val alreadyGraded = stu.id in gradedStudentIdsForPeriod ||
                                    stu.normalizedName in gradedStudentIdsForPeriod
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = stu.fullName,
                                                fontWeight = if (alreadyGraded) FontWeight.Normal else FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            GenderPill(stu.genderEnum)
                                            if (alreadyGraded) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Déjà évalué",
                                                    tint = semantic.masteryMax,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedStudent = stu
                                        studentName = stu.fullName
                                        gender = stu.genderEnum
                                        studentMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Quick 1-tap chips for ungraded students (up to 6 visible)
                    val ungradedQuickPicks = remember(schoolStudents, gradedStudentIdsForPeriod) {
                        schoolStudents
                            .filterNot {
                                it.id in gradedStudentIdsForPeriod ||
                                    it.normalizedName in gradedStudentIdsForPeriod
                            }
                            .take(6)
                    }
                    if (ungradedQuickPicks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Sélection rapide (non encore notés sur cette période) :",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ungradedQuickPicks.forEach { stu ->
                                    FilterChip(
                                        selected = selectedStudent?.id == stu.id,
                                        onClick = {
                                            selectedStudent = stu
                                            studentName = stu.fullName
                                            gender = stu.genderEnum
                                        },
                                        label = {
                                            Text(
                                                text = "${stu.fullName} (${stu.genderEnum.shortLabel})",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it.take(80) },
                        label = { Text("Nom et prénom de l'élève") },
                        supportingText = {
                            Text("L'élève sera mémorisé automatiquement pour tous les prochains tests.")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("assessment_student_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Genre :", style = MaterialTheme.typography.labelLarge)
                        Gender.entries.forEach { g ->
                            FilterChip(
                                selected = gender == g,
                                onClick = { gender = g },
                                label = { Text(g.label) },
                                modifier = Modifier.testTag("gender_chip_${g.name.lowercase()}")
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 1. Langue & Communication (/100)
                ScoreSectionHeader("Langue et Communication", "${formatScoreInput(lcTotal)} / 100")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreNumberField(
                        label = "Ressources /40",
                        value = lcResTxt,
                        max = Baremes.LC_RESSOURCES_MAX,
                        onValueChange = { lcResTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_lc_res")
                    )
                    ScoreNumberField(
                        label = "Compétences /60",
                        value = lcCompTxt,
                        max = Baremes.LC_COMPETENCES_MAX,
                        onValueChange = { lcCompTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_lc_comp")
                    )
                }

                // 2. Mathématiques (/100)
                ScoreSectionHeader("Mathématiques", "${formatScoreInput(mathTotal)} / 100")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreNumberField(
                        label = "Ressources /40",
                        value = mathResTxt,
                        max = Baremes.MATH_RESSOURCES_MAX,
                        onValueChange = { mathResTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_math_res")
                    )
                    ScoreNumberField(
                        label = "Compétences /60",
                        value = mathCompTxt,
                        max = Baremes.MATH_COMPETENCES_MAX,
                        onValueChange = { mathCompTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_math_comp")
                    )
                }

                // 3. ESVS (/80)
                ScoreSectionHeader("ESVS — Découverte du Monde", "${formatScoreInput(dmRes + dmComp)} / 40")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreNumberField(
                        label = "DM Ress. /24",
                        value = dmResTxt,
                        max = Baremes.DM_RESSOURCES_MAX,
                        onValueChange = { dmResTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_dm_res")
                    )
                    ScoreNumberField(
                        label = "DM Comp. /16",
                        value = dmCompTxt,
                        max = Baremes.DM_COMPETENCES_MAX,
                        onValueChange = { dmCompTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_dm_comp")
                    )
                }

                ScoreSectionHeader("ESVS — Développement Durable", "${formatScoreInput(ddRes + ddComp)} / 40")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreNumberField(
                        label = "DD Ress. /24",
                        value = ddResTxt,
                        max = Baremes.DD_RESSOURCES_MAX,
                        onValueChange = { ddResTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_dd_res")
                    )
                    ScoreNumberField(
                        label = "DD Comp. /16",
                        value = ddCompTxt,
                        max = Baremes.DD_COMPETENCES_MAX,
                        onValueChange = { ddCompTxt = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("score_dd_comp")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val school = selectedSchool ?: return@Button
                    onConfirm(
                        school,
                        if (!isNewStudentMode) selectedStudent?.id else null,
                        effectiveStudentName,
                        effectiveGender,
                        testType,
                        month,
                        if (testType == TestType.MENSUEL) 0 else weekNumber,
                        lcRes,
                        lcComp,
                        mathRes,
                        mathComp,
                        dmRes,
                        dmComp,
                        ddRes,
                        ddComp
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.testTag("save_assessment_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ScoreSectionHeader(title: String, subtotal: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtotal,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreNumberField(
    label: String,
    value: String,
    max: Double,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsed = value.replace(",", ".").toDoubleOrNull()
    val isOverMax = parsed != null && parsed > max
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(5)
            onValueChange(filtered)
        },
        label = { Text(label) },
        isError = isOverMax,
        supportingText = if (isOverMax) {
            { Text("Max ${max.toInt()}") }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

private fun parseScore(raw: String, max: Double): Double {
    val value = raw.replace(",", ".").toDoubleOrNull() ?: 0.0
    return value.coerceIn(0.0, max)
}
