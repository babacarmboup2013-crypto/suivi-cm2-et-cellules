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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.DataEntryOperator
import com.example.data.School
import com.example.ui.components.EmptyPromptCard
import com.example.ui.components.KpiTile
import com.example.ui.theme.LocalSuiviColors
import com.example.viewmodel.SuiviDashboardData

@Composable
fun OperatorsScreen(
    data: SuiviDashboardData,
    onAddOperator: () -> Unit,
    onEditOperator: (DataEntryOperator) -> Unit,
    onToggleOperatorActive: (DataEntryOperator, Boolean) -> Unit,
    onDeleteOperator: (DataEntryOperator) -> Unit,
    onAddSchool: () -> Unit,
    onBootstrap40Schools: () -> Unit,
    onGenerateMissingCodes: () -> Unit,
    onSetSchoolAccessCode: (School) -> Unit,
    onOpenConfidentialLogin: () -> Unit
) {
    val semantic = LocalSuiviColors.current
    var search by remember { mutableStateOf("") }

    val activeOpsCount = remember(data.operators) { data.operators.count { it.active } }
    val unprotectedSchoolsCount = remember(data.schools) { data.schools.count { !it.hasAccessCode } }

    val filteredOperators = remember(data.operators, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) data.operators
        else data.operators.filter {
            it.fullName.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.schoolCode.lowercase().contains(q) ||
                it.schoolName.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("operators_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Admin governance banner
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Administration des Accès",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Créez les écoles, les codes d'accès confidentiels et les utilisateurs de saisie par école.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // 2-column KPI summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiTile(
                    label = "Utilisateurs de saisie",
                    value = "$activeOpsCount/${data.operators.size}",
                    caption = "actifs dans les écoles",
                    icon = Icons.Rounded.Badge,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    label = "Élèves mémorisés",
                    value = "${data.students.size}",
                    caption = "réutilisés à chaque test",
                    icon = Icons.Rounded.School,
                    accentColor = semantic.masteryMax,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Primary Admin action bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddOperator,
                        enabled = data.schools.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_operator_button")
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Créer un utilisateur", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onAddSchool,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("admin_add_school_quick_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("École")
                    }
                }

                if (data.schools.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenConfidentialLogin,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_operator_session_button")
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir une session Utilisateur de saisie / École isolée")
                    }
                }

                if (data.schools.size < 40) {
                    OutlinedButton(
                        onClick = onBootstrap40Schools,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (data.schools.isEmpty()) "Initialiser les 40 écoles CM2"
                            else "Compléter les ${40 - data.schools.size} écoles CM2 restantes"
                        )
                    }
                }

                if (unprotectedSchoolsCount > 0) {
                    OutlinedButton(
                        onClick = onGenerateMissingCodes,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Générer les codes d'accès manquants ($unprotectedSchoolsCount école(s))")
                    }
                }
            }
        }

        // Search input
        if (data.operators.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Rechercher un utilisateur, un e-mail ou une école...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operators_search_input")
                )
            }
        }

        // Operators list
        item {
            Text(
                text = "Utilisateurs de saisie (${filteredOperators.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (filteredOperators.isEmpty()) {
            item {
                EmptyPromptCard(
                    icon = Icons.Rounded.Badge,
                    title = if (data.operators.isEmpty()) "Aucun utilisateur de saisie" else "Aucun résultat",
                    description = if (data.schools.isEmpty()) {
                        "Initialisez ou créez d'abord les écoles CM2 avant d'y affecter des utilisateurs de saisie."
                    } else if (data.operators.isEmpty()) {
                        "Créez un utilisateur de saisie pour chaque école afin qu'il puisse enregistrer les élèves et saisir les tests de son établissement en toute confidentialité."
                    } else {
                        "Aucun utilisateur ne correspond à « $search »."
                    },
                    actionLabel = if (data.schools.isNotEmpty() && data.operators.isEmpty()) "Créer un utilisateur" else null,
                    onAction = if (data.schools.isNotEmpty() && data.operators.isEmpty()) onAddOperator else null
                )
            }
        } else {
            items(filteredOperators, key = { it.id }) { op ->
                OperatorCard(
                    operator = op,
                    onToggleActive = { checked -> onToggleOperatorActive(op, checked) },
                    onEdit = { onEditOperator(op) },
                    onDelete = { onDeleteOperator(op) }
                )
            }
        }

        // Quick School Access Codes summary
        if (data.schools.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Codes d'accès des écoles (${data.schools.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(data.schools, key = { "access_${it.id}" }) { school ->
                val opCount = data.operators.count { it.schoolId == school.id && it.active }
                val stuCount = data.students.count { it.schoolId == school.id }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${school.code} • ${school.name}",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$opCount agent(s) actif(s) • $stuCount élève(s) • ${if (school.hasAccessCode) "Code protégé" else "Sans code"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onSetSchoolAccessCode(school) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (school.hasAccessCode) "Changer" else "Créer", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorCard(
    operator: DataEntryOperator,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalSuiviColors.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (operator.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("operator_card_${operator.id}")
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
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = operator.schoolCode,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = operator.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = operator.schoolName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = operator.active,
                    onCheckedChange = onToggleActive
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (operator.hasGoogleEmail) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = operator.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = if (operator.active) semantic.masteryMax else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = buildString {
                                append(if (operator.active) "Actif" else "Suspendu")
                                if (operator.hasPersonalPin) append(" • Code PIN chiffré")
                                if (operator.hasGoogleEmail) append(" • Accès Google")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        }
    }
}
