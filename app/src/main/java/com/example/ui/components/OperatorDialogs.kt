package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.AccessCodeSecurity
import com.example.data.DataEntryOperator
import com.example.data.School

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditOperatorDialog(
    initial: DataEntryOperator?,
    schools: List<School>,
    preselectedSchoolId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        fullName: String,
        email: String,
        school: School,
        active: Boolean,
        newPin: String?
    ) -> Unit
) {
    val defaultSchool = schools.firstOrNull {
        it.id == (initial?.schoolId ?: preselectedSchoolId)
    } ?: schools.firstOrNull()

    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var selectedSchool by remember { mutableStateOf(defaultSchool) }
    var schoolMenuExpanded by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var pinCode by remember {
        mutableStateOf(if (initial == null) AccessCodeSecurity.generateAccessCode() else "")
    }
    var showPin by remember { mutableStateOf(initial == null) }

    val pinValid = pinCode.isBlank() || AccessCodeSecurity.isValidFormat(pinCode)
    val cleanEmail = email.trim().lowercase()
    val emailValid = cleanEmail.isBlank() || (cleanEmail.contains("@") && cleanEmail.contains("."))
    val canSubmit = fullName.trim().isNotEmpty() && selectedSchool != null && pinValid && emailValid

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (initial == null) "Nouvel utilisateur de saisie" else "Modifier l'utilisateur de saisie",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Cet agent pourra uniquement gérer les élèves et saisir les tests de son école assignée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it.take(80) },
                    label = { Text("Nom complet de l'agent de saisie") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operator_fullname_input")
                )

                ExposedDropdownMenuBox(
                    expanded = schoolMenuExpanded,
                    onExpandedChange = { schoolMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSchool?.let { "${it.code} • ${it.name} (${it.className})" } ?: "Aucune école",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("École assignée") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("operator_school_picker")
                    )
                    ExposedDropdownMenu(
                        expanded = schoolMenuExpanded,
                        onDismissRequest = { schoolMenuExpanded = false }
                    ) {
                        schools.forEach { school ->
                            DropdownMenuItem(
                                text = { Text("${school.code} • ${school.name} (${school.className})") },
                                onClick = {
                                    selectedSchool = school
                                    schoolMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(120) },
                    label = { Text("E-mail Google (optionnel, accès multi-appareils)") },
                    singleLine = true,
                    isError = !emailValid,
                    supportingText = {
                        Text(
                            if (!emailValid) "Adresse e-mail invalide."
                            else "Si renseigné, cet agent peut se connecter avec son propre compte Google sur son téléphone."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operator_email_input")
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { raw ->
                        pinCode = raw.filter { it.isLetterOrDigit() }.take(16).uppercase()
                    },
                    label = {
                        Text(
                            if (initial == null) "Code d'accès personnel (PIN)"
                            else "Nouveau code PIN (laisser vide pour conserver)"
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { pinCode = AccessCodeSecurity.generateAccessCode() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Générer un code PIN")
                            }
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    imageVector = if (showPin) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPin) "Masquer" else "Afficher"
                                )
                            }
                        }
                    },
                    isError = !pinValid,
                    supportingText = {
                        Text(
                            if (!pinValid) "4 à 16 caractères alphanumériques."
                            else "Permet l'ouverture de session de saisie isolée sur appareil partagé."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operator_pin_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (active) "Compte actif (accès autorisé)" else "Compte suspendu",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                        modifier = Modifier.testTag("operator_active_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val school = selectedSchool ?: return@Button
                    onConfirm(
                        fullName.trim(),
                        cleanEmail,
                        school,
                        active,
                        pinCode.trim().takeIf { it.isNotBlank() }
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.testTag("save_operator_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
