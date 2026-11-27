package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.AccessCodeSecurity
import com.example.data.Gender
import com.example.data.School
import com.example.data.Student
import com.example.data.ThresholdSettings

@Composable
fun AddEditSchoolDialog(
    initial: School?,
    nextCodeSuggestion: String,
    onDismiss: () -> Unit,
    onConfirm: (
        code: String,
        name: String,
        className: String,
        zone: String,
        teacherName: String,
        boys: Int,
        girls: Int,
        newAccessCode: String?
    ) -> Unit
) {
    var code by remember { mutableStateOf(initial?.code ?: nextCodeSuggestion) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var className by remember { mutableStateOf(initial?.className ?: "CM2 A") }
    var zone by remember { mutableStateOf(initial?.zone ?: "Zone Nord") }
    var teacher by remember { mutableStateOf(initial?.teacherName ?: "") }
    var boysText by remember { mutableStateOf(initial?.boysEnrolled?.toString() ?: "0") }
    var girlsText by remember { mutableStateOf(initial?.girlsEnrolled?.toString() ?: "0") }
    var accessCode by remember {
        mutableStateOf(if (initial == null) AccessCodeSecurity.generateAccessCode() else "")
    }
    var showAccessCode by remember { mutableStateOf(initial == null) }

    val accessCodeValid = accessCode.isBlank() || AccessCodeSecurity.isValidFormat(accessCode)
    val canSubmit = code.trim().isNotEmpty() &&
        name.trim().isNotEmpty() &&
        className.trim().isNotEmpty() &&
        accessCodeValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Nouvelle école CM2" else "Modifier l'école CM2",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(24) },
                        label = { Text("Code") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.42f)
                            .testTag("school_code_input")
                    )
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it.take(40) },
                        label = { Text("Classe") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.58f)
                            .testTag("school_class_input")
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("Nom de l'école") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_name_input")
                )
                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it.take(80) },
                    label = { Text("Zone / Circonscription") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it.take(80) },
                    label = { Text("Enseignant(e) CM2 (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = boysText,
                        onValueChange = { boysText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Inscrits Garçons") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = girlsText,
                        onValueChange = { girlsText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Inscrites Filles") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = accessCode,
                    onValueChange = { raw ->
                        accessCode = raw.filter { it.isLetterOrDigit() }.take(16).uppercase()
                    },
                    label = {
                        Text(
                            if (initial == null) "Code d'accès confidentiel"
                            else "Nouveau code d'accès (optionnel)"
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showAccessCode) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { accessCode = AccessCodeSecurity.generateAccessCode() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Générer un code")
                            }
                            IconButton(onClick = { showAccessCode = !showAccessCode }) {
                                Icon(
                                    imageVector = if (showAccessCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showAccessCode) "Masquer" else "Afficher"
                                )
                            }
                        }
                    },
                    isError = !accessCodeValid,
                    supportingText = {
                        Text(
                            when {
                                !accessCodeValid -> "4 à 16 caractères (lettres/chiffres)."
                                initial == null -> "Protège les données de cette école (chiffré PBKDF2)."
                                else -> "Laissez vide pour conserver le code d'accès actuel."
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_access_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        code.trim(),
                        name.trim(),
                        className.trim(),
                        zone.trim(),
                        teacher.trim(),
                        boysText.toIntOrNull()?.coerceIn(0, 300) ?: 0,
                        girlsText.toIntOrNull()?.coerceIn(0, 300) ?: 0,
                        accessCode.trim().takeIf { it.isNotBlank() }
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.testTag("save_school_button")
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
fun AddEditStudentDialog(
    school: School,
    initial: Student?,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, gender: Gender, matricule: String) -> Unit
) {
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var gender by remember { mutableStateOf(initial?.genderEnum ?: Gender.GARCON) }
    var matricule by remember { mutableStateOf(initial?.matricule ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Nouvel élève — ${school.code}" else "Modifier l'élève",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${school.name} (${school.className}) — Une fois enregistré, cet élève apparaît automatiquement pour chaque test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it.take(80) },
                    label = { Text("Nom et prénom de l'élève") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_fullname_input")
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
                            label = { Text(g.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = matricule,
                    onValueChange = { matricule = it.take(30) },
                    label = { Text("Matricule / N° d'ordre (optionnel)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_matricule_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fullName.trim(), gender, matricule.trim()) },
                enabled = fullName.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_student_button")
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
fun ThresholdSettingsDialog(
    current: ThresholdSettings,
    onDismiss: () -> Unit,
    onSave: (
        maxPct: Double,
        minPct: Double,
        partialPct: Double,
        academicYear: String,
        districtName: String
    ) -> Unit
) {
    var maxText by remember { mutableStateOf(current.maxMasteryPct.toInt().toString()) }
    var minText by remember { mutableStateOf(current.minMasteryPct.toInt().toString()) }
    var partialText by remember { mutableStateOf(current.partialMasteryPct.toInt().toString()) }
    var yearText by remember { mutableStateOf(current.academicYear) }
    var districtText by remember { mutableStateOf(current.districtName) }

    val maxPct = (maxText.toDoubleOrNull() ?: 75.0).coerceIn(50.0, 100.0)
    val minPct = (minText.toDoubleOrNull() ?: 50.0).coerceIn(30.0, maxPct)
    val partialPct = (partialText.toDoubleOrNull() ?: 35.0).coerceIn(10.0, minPct)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Seuils de maîtrise & Barèmes", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = districtText,
                    onValueChange = { districtText = it.take(100) },
                    label = { Text("Circonscription / IEF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.take(20) },
                    label = { Text("Année scolaire") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Max (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Min (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = partialText,
                        onValueChange = { partialText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Partiel (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Équivalence en points (Min ${minPct.toInt()}% / Max ${maxPct.toInt()}%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ThresholdPreviewRow("LC / Math Ressources (/40)", 40.0, minPct, maxPct)
                    ThresholdPreviewRow("LC / Math Compétences (/60)", 60.0, minPct, maxPct)
                    ThresholdPreviewRow("ESVS DM / DD Ress. (/24)", 24.0, minPct, maxPct)
                    ThresholdPreviewRow("ESVS DM / DD Comp. (/16)", 16.0, minPct, maxPct)
                    ThresholdPreviewRow("ESVS Global (/80)", 80.0, minPct, maxPct)
                    HorizontalDivider()
                    ThresholdPreviewRow("TOTAL GÉNÉRAL (/280)", 280.0, minPct, maxPct, bold = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(maxPct, minPct, partialPct, yearText.trim(), districtText.trim()) },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun ThresholdPreviewRow(
    label: String,
    maxScore: Double,
    minPct: Double,
    maxPct: Double,
    bold: Boolean = false
) {
    val minPts = formatScoreInput(maxScore * minPct / 100.0)
    val maxPts = formatScoreInput(maxScore * maxPct / 100.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "≥ $minPts  •  ≥ $maxPts",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

fun formatScoreInput(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
}
