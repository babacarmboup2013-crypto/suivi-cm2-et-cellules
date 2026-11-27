package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.data.AdministratorAccount
import com.example.data.School
import com.example.viewmodel.SuiviDashboardData

@Composable
fun SuperAdminScreen(
    data: SuiviDashboardData,
    onAddAdministrator: () -> Unit,
    onToggleAdministrator: (AdministratorAccount) -> Unit,
    onAddSchoolForAdmin: (AdministratorAccount) -> Unit,
    onAddOperatorForSchool: (School) -> Unit
) {
    val operatorCountBySchool = remember(data.operators) {
        data.operators.groupingBy { it.schoolId }.eachCount()
    }
    val schoolCountByAdmin = remember(data.schools) {
        data.schools.groupingBy { it.adminEmail.ifBlank { it.adminUid } }.eachCount()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Espace Superadministrateur",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${data.administrators.size} administrateur(s) • ${data.schools.size} école(s) • " +
                            "${data.operators.size} opérateur(s) de saisie",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Vous créez les administrateurs. Chaque administrateur gère ensuite ses écoles, " +
                            "ses élèves et ses opérateurs. Vous pouvez aussi créer une école et un opérateur " +
                            "directement pour un administrateur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Administrateurs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onAddAdministrator,
                    modifier = Modifier.testTag("superadmin_add_admin_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("  Nouvel administrateur")
                }
            }
        }

        if (data.administrators.isEmpty()) {
            item {
                Text(
                    text = "Aucun administrateur créé pour le moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(data.administrators, key = { it.id }) { admin ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = admin.fullName.ifBlank { admin.email },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = admin.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            !admin.active -> "Compte suspendu"
                            admin.isClaimed -> "Compte activé (UID lié)"
                            else -> "En attente de première connexion Google"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (admin.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${schoolCountByAdmin[admin.email] ?: 0} école(s) rattachée(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAddSchoolForAdmin(admin) },
                            modifier = Modifier.weight(1f).testTag("superadmin_add_school_for_${admin.id}")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("  École")
                        }
                        TextButton(
                            onClick = { onToggleAdministrator(admin) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (admin.active) Icons.Rounded.PersonOff else Icons.Rounded.CheckCircle,
                                contentDescription = null
                            )
                            Text(if (admin.active) "  Suspendre" else "  Réactiver")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Écoles et opérateurs de saisie",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(data.schools, key = { it.id }) { school ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${school.code} • ${school.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Administrateur : ${school.adminEmail.ifBlank { school.adminUid.ifBlank { "non assigné" } }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${operatorCountBySchool[school.id] ?: 0} opérateur(s) de saisie",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { onAddOperatorForSchool(school) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("  Ajouter un opérateur")
                    }
                }
            }
        }
    }
}

@Composable
fun AddAdministratorDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, fullName: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    val emailValid = email.isNotBlank() && email.contains("@") && email.contains(".")
    val canSubmit = emailValid && fullName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel administrateur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it.take(80) },
                    label = { Text("Nom complet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_fullname_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim().lowercase().take(120) },
                    label = { Text("E-mail Google") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_email_input"),
                    supportingText = {
                        Text("Cet e-mail servira d'identifiant lors de la connexion Google.")
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(email, fullName) },
                enabled = canSubmit,
                modifier = Modifier.testTag("save_admin_button")
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
