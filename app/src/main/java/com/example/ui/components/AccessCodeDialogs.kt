package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.AccessCodeSecurity
import com.example.data.GeneratedSchoolCode
import com.example.data.School
import com.example.viewmodel.AccessVerificationResult
import kotlinx.coroutines.launch

@Composable
fun UnlockSchoolDialog(
    school: School,
    onDismiss: () -> Unit,
    onVerify: suspend (code: String, enterConfidentialMode: Boolean) -> AccessVerificationResult,
    onUnlocked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var codeInput by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var enterConfidentialMode by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Accès confidentiel — ${school.code}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Saisissez le code d'accès de ${school.name} (${school.className}) pour consulter ou saisir ses données élèves.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { raw ->
                        codeInput = raw.filter { it.isLetterOrDigit() }.take(16).uppercase()
                        errorMessage = null
                    },
                    label = { Text("Code d'accès de l'école") },
                    singleLine = true,
                    visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCode = !showCode }) {
                            Icon(
                                imageVector = if (showCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showCode) "Masquer le code" else "Afficher le code"
                            )
                        }
                    },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_school_code_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = enterConfidentialMode,
                        onCheckedChange = { enterConfidentialMode = it },
                        modifier = Modifier.testTag("unlock_confidential_mode_checkbox")
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Ouvrir en Mode École isolé (masque les 39 autres écoles)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isVerifying = true
                    errorMessage = null
                    scope.launch {
                        when (val res = onVerify(codeInput, enterConfidentialMode)) {
                            AccessVerificationResult.Success -> {
                                isVerifying = false
                                onUnlocked()
                            }
                            is AccessVerificationResult.InvalidCode -> {
                                isVerifying = false
                                errorMessage = "Code d'accès incorrect (${res.remainingAttempts} essai(s) restant(s))."
                            }
                            is AccessVerificationResult.LockedOut -> {
                                isVerifying = false
                                errorMessage = "Trop de tentatives. Réessayez dans ${res.secondsRemaining} s."
                            }
                            is AccessVerificationResult.Error -> {
                                isVerifying = false
                                errorMessage = res.message
                            }
                        }
                    }
                },
                enabled = codeInput.length >= 4 && !isVerifying,
                modifier = Modifier.testTag("confirm_unlock_school_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Déverrouiller")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolConfidentialModeDialog(
    schools: List<School>,
    operators: List<com.example.data.DataEntryOperator> = emptyList(),
    onDismiss: () -> Unit,
    onVerifySchool: suspend (school: School, code: String) -> AccessVerificationResult,
    onVerifyOperator: suspend (operator: com.example.data.DataEntryOperator, pin: String) -> AccessVerificationResult,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val activePinOperators = remember(operators) {
        operators.filter { it.active && it.hasPersonalPin }
    }
    var useOperatorMode by remember { mutableStateOf(activePinOperators.isNotEmpty()) }

    var selectedSchool by remember { mutableStateOf(schools.firstOrNull()) }
    var schoolMenuExpanded by remember { mutableStateOf(false) }

    var selectedOperator by remember { mutableStateOf(activePinOperators.firstOrNull()) }
    var operatorMenuExpanded by remember { mutableStateOf(false) }

    var codeInput by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Session Saisie École", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ouvrez une session isolée : seules les données de l'école choisie sont accessibles (les fonctions Administrateur et les 39 autres écoles sont verrouillées).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activePinOperators.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = useOperatorMode,
                            onClick = {
                                useOperatorMode = true
                                codeInput = ""
                                errorMessage = null
                            },
                            label = { Text("Agent de saisie") }
                        )
                        androidx.compose.material3.FilterChip(
                            selected = !useOperatorMode,
                            onClick = {
                                useOperatorMode = false
                                codeInput = ""
                                errorMessage = null
                            },
                            label = { Text("Code École") }
                        )
                    }
                }

                if (useOperatorMode && activePinOperators.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = operatorMenuExpanded,
                        onExpandedChange = { operatorMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedOperator?.let { "${it.fullName} (${it.schoolCode})" }
                                ?: "Choisir un utilisateur",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Utilisateur de saisie") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = operatorMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = operatorMenuExpanded,
                            onDismissRequest = { operatorMenuExpanded = false }
                        ) {
                            activePinOperators.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text("${op.fullName} • ${op.schoolCode} (${op.schoolName})") },
                                    onClick = {
                                        selectedOperator = op
                                        errorMessage = null
                                        operatorMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { raw ->
                            codeInput = raw.filter { it.isLetterOrDigit() }.take(16).uppercase()
                            errorMessage = null
                        },
                        label = { Text("Code PIN personnel") },
                        singleLine = true,
                        visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCode = !showCode }) {
                                Icon(
                                    imageVector = if (showCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showCode) "Masquer" else "Afficher"
                                )
                            }
                        },
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { msg -> { Text(msg) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("operator_mode_pin_input")
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = schoolMenuExpanded,
                        onExpandedChange = { schoolMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedSchool?.let { "${it.code} • ${it.name}" } ?: "Aucune école",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("École CM2") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
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
                                        errorMessage = null
                                        schoolMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedSchool?.hasAccessCode == true) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { raw ->
                                codeInput = raw.filter { it.isLetterOrDigit() }.take(16).uppercase()
                                errorMessage = null
                            },
                            label = { Text("Code d'accès confidentiel de l'école") },
                            singleLine = true,
                            visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showCode = !showCode }) {
                                    Icon(
                                        imageVector = if (showCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showCode) "Masquer" else "Afficher"
                                    )
                                }
                            },
                            isError = errorMessage != null,
                            supportingText = errorMessage?.let { msg -> { Text(msg) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("school_mode_code_input")
                        )
                    } else if (selectedSchool != null) {
                        Text(
                            text = "Cette école n'a pas encore de code d'accès défini. Créez-en un depuis l'onglet Accès.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isVerifying = true
                    errorMessage = null
                    scope.launch {
                        val res = if (useOperatorMode && activePinOperators.isNotEmpty()) {
                            val op = selectedOperator ?: return@launch
                            onVerifyOperator(op, codeInput)
                        } else {
                            val school = selectedSchool ?: return@launch
                            onVerifySchool(school, codeInput)
                        }
                        when (res) {
                            AccessVerificationResult.Success -> {
                                isVerifying = false
                                onSuccess()
                            }
                            is AccessVerificationResult.InvalidCode -> {
                                isVerifying = false
                                errorMessage = "Code incorrect (${res.remainingAttempts} essai(s) restant(s))."
                            }
                            is AccessVerificationResult.LockedOut -> {
                                isVerifying = false
                                errorMessage = "Trop de tentatives. Patientez ${res.secondsRemaining} s."
                            }
                            is AccessVerificationResult.Error -> {
                                isVerifying = false
                                errorMessage = res.message
                            }
                        }
                    }
                },
                enabled = !isVerifying && if (useOperatorMode && activePinOperators.isNotEmpty()) {
                    selectedOperator != null && codeInput.length >= 4
                } else {
                    selectedSchool != null && (!selectedSchool!!.hasAccessCode || codeInput.length >= 4)
                },
                modifier = Modifier.testTag("enter_school_mode_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Ouvrir la session")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
fun SetSchoolAccessCodeDialog(
    school: School,
    onDismiss: () -> Unit,
    onSave: (newCode: String) -> Unit
) {
    var code by remember { mutableStateOf(AccessCodeSecurity.generateAccessCode()) }
    var showCode by remember { mutableStateOf(true) }
    val valid = AccessCodeSecurity.isValidFormat(code)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (school.hasAccessCode) "Modifier le code — ${school.code}" else "Créer un code — ${school.code}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ce code d'accès protège la confidentialité des élèves et résultats de ${school.name}. Il est chiffré (PBKDF2-SHA256) avant sauvegarde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        code = raw.filter { it.isLetterOrDigit() }.take(16).uppercase()
                    },
                    label = { Text("Code d'accès (4 à 16 caractères)") },
                    singleLine = true,
                    visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { code = AccessCodeSecurity.generateAccessCode() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Générer un code aléatoire")
                            }
                            IconButton(onClick = { showCode = !showCode }) {
                                Icon(
                                    imageVector = if (showCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showCode) "Masquer" else "Afficher"
                                )
                            }
                        }
                    },
                    isError = code.isNotEmpty() && !valid,
                    supportingText = {
                        Text(
                            if (valid) "Format valide — notez ce code pour le remettre au directeur."
                            else "Minimum 4 caractères (lettres ou chiffres)."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_access_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(code) },
                enabled = valid,
                modifier = Modifier.testTag("confirm_set_access_code_button")
            ) {
                Text("Enregistrer le code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun GeneratedAccessCodesDialog(
    codes: List<GeneratedSchoolCode>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (codes.size == 1) "Code d'accès généré" else "${codes.size} codes d'accès confidentiels",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pour garantir la confidentialité, ces codes ne sont jamais stockés en clair. Copiez-les ou notez-les maintenant pour les transmettre à chaque école.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    codes.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.schoolCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.schoolName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = item.accessCode,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (idx < codes.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val text = codes.joinToString("\n") {
                            "${it.schoolCode} (${it.schoolName}) : ${it.accessCode}"
                        }
                        clipboardManager.setText(AnnotatedString(text))
                        copied = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_generated_codes_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Codes copiés dans le presse-papiers !" else "Copier la liste des codes")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_generated_codes_button")
            ) {
                Text("J'ai noté les codes")
            }
        }
    )
}
