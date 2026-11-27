package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOutGoogle
import com.example.data.AdministratorAccount
import com.example.data.Assessment
import com.example.data.DataEntryOperator
import com.example.data.OperatorRepository
import com.example.data.School
import com.example.data.Student
import com.example.data.SuiviRepository
import com.example.ui.components.AddEditAssessmentDialog
import com.example.ui.components.AddEditOperatorDialog
import com.example.ui.components.AddEditSchoolDialog
import com.example.ui.components.AddEditStudentDialog
import com.example.ui.components.GeneratedAccessCodesDialog
import com.example.ui.components.SchoolConfidentialModeDialog
import com.example.ui.components.SetSchoolAccessCodeDialog
import com.example.ui.components.ThresholdSettingsDialog
import com.example.ui.components.UnlockSchoolDialog
import com.example.ui.screens.AddAdministratorDialog
import com.example.ui.screens.AssessmentsScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.OperatorsScreen
import com.example.ui.screens.SchoolsScreen
import com.example.ui.screens.SuperAdminScreen
import com.example.ui.screens.SynthesisScreen
import com.example.ui.theme.SuiviCm2Theme
import com.example.viewmodel.SuiviDashboardData
import com.example.viewmodel.SuiviViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SYNTHESIS(R.string.tab_synthesis, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    SCHOOLS(R.string.tab_schools, Icons.Rounded.School, Icons.Outlined.School),
    TESTS(R.string.tab_tests, Icons.Rounded.Assignment, Icons.Outlined.Assignment),
    DIAGNOSTICS(R.string.tab_stats, Icons.Rounded.Analytics, Icons.Outlined.Analytics),
    ACCESS(R.string.tab_access, Icons.Rounded.AdminPanelSettings, Icons.Outlined.AdminPanelSettings),
    SUPERADMIN(R.string.tab_superadmin, Icons.Rounded.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuiviCm2Theme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedSuiviApp(
                currentUserId = user.uid,
                currentUserEmail = user.email.orEmpty(),
                userDisplayName = user.displayName ?: user.email ?: "Inspecteur CM2"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedSuiviApp(
    currentUserId: String,
    currentUserEmail: String,
    userDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: SuiviViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY manquant dans CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SuiviViewModel(
                    repository = SuiviRepository(db),
                    operatorRepository = OperatorRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = userDisplayName
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val generatedCodes by viewModel.generatedCodes.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.SYNTHESIS) }
    var showSchoolDialog by remember { mutableStateOf(false) }
    var editingSchool by remember { mutableStateOf<School?>(null) }

    var showAssessmentDialog by remember { mutableStateOf(false) }
    var editingAssessment by remember { mutableStateOf<Assessment?>(null) }
    var preselectedSchoolId by remember { mutableStateOf<String?>(null) }
    var preselectedStudent by remember { mutableStateOf<Student?>(null) }

    var showOperatorDialog by remember { mutableStateOf(false) }
    var editingOperator by remember { mutableStateOf<DataEntryOperator?>(null) }
    var preselectedOperatorSchoolId by remember { mutableStateOf<String?>(null) }

    var showStudentDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var studentDialogSchool by remember { mutableStateOf<School?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    // Access-code confidentiality dialog states
    var showAdministratorDialog by remember { mutableStateOf(false) }
    var showSuperSchoolDialog by remember { mutableStateOf(false) }
    var selectedSuperAdmin by remember { mutableStateOf<AdministratorAccount?>(null) }

    var showSchoolModeDialog by remember { mutableStateOf(false) }
    var schoolToUnlock by remember { mutableStateOf<School?>(null) }
    var onAfterUnlockCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var schoolForCodeSetup by remember { mutableStateOf<School?>(null) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val dashboardData = (uiState as? UiState.Success)?.data
    val visibleTabs = remember(dashboardData?.isAdmin, dashboardData?.isSuperAdmin) {
        when {
            dashboardData?.isSuperAdmin == true -> MainTab.entries
            dashboardData?.isAdmin == true -> MainTab.entries.filter { it != MainTab.SUPERADMIN }
            else -> MainTab.entries.filter { it != MainTab.ACCESS && it != MainTab.SUPERADMIN }
        }
    }
    LaunchedEffect(dashboardData?.isAdmin, dashboardData?.isSuperAdmin, visibleTabs) {
        if (currentTab !in visibleTabs) {
            currentTab = MainTab.SYNTHESIS
        }
    }

    val unlockedSchools = remember(dashboardData) {
        dashboardData?.schools?.filter { dashboardData.isSchoolUnlocked(it) }.orEmpty()
    }

    fun requestUnlock(school: School, afterUnlock: () -> Unit = {}) {
        schoolToUnlock = school
        onAfterUnlockCallback = afterUnlock
    }

    fun openNewAssessment(targetSchoolId: String?, targetStudent: Student? = null) {
        val data = dashboardData ?: return
        if (data.schools.isEmpty()) return
        val resolvedSchoolId = targetStudent?.schoolId ?: targetSchoolId
        val targetSchool = resolvedSchoolId?.let { id -> data.schools.firstOrNull { it.id == id } }
        if (targetSchool != null && !data.isSchoolUnlocked(targetSchool)) {
            requestUnlock(targetSchool) {
                editingAssessment = null
                preselectedSchoolId = targetSchool.id
                preselectedStudent = targetStudent
                showAssessmentDialog = true
            }
            return
        }
        val available = data.schools.filter { data.isSchoolUnlocked(it) }
        if (available.isEmpty()) {
            showSchoolModeDialog = true
        } else {
            editingAssessment = null
            preselectedSchoolId = resolvedSchoolId ?: available.first().id
            preselectedStudent = targetStudent
            showAssessmentDialog = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitleText = when {
                                    dashboardData?.isGoogleOperatorAccount == true -> {
                                        val op = dashboardData.activeOperator
                                        val schoolLabel = dashboardData.confidentialSchool?.let { "${it.code} (${it.name})" }
                                            ?: "École assignée"
                                        if (op != null) "${op.fullName} • $schoolLabel" else schoolLabel
                                    }
                                    dashboardData?.activeOperator != null && dashboardData.confidentialSchool != null -> {
                                        "Agent ${dashboardData.activeOperator.fullName} • ${dashboardData.confidentialSchool.code}"
                                    }
                                    dashboardData?.confidentialSchool != null -> {
                                        "Mode École • ${dashboardData.confidentialSchool.code} (${dashboardData.confidentialSchool.name})"
                                    }
                                    else -> userDisplayName
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dashboardData?.confidentialSchool != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        actions = {
                            if (dashboardData != null && dashboardData.isAdmin && dashboardData.schools.isNotEmpty()) {
                                IconButton(
                                    onClick = { showSchoolModeDialog = true },
                                    modifier = Modifier.testTag("toolbar_school_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = "Mode École Confidentiel",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (dashboardData != null && dashboardData.isAdmin && dashboardData.unlockedSchoolIds.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.lockAllSchools() },
                                    modifier = Modifier.testTag("toolbar_lock_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Verrouiller toutes les écoles"
                                    )
                                }
                            }
                            if (dashboardData?.isAdmin == true) {
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.testTag("toolbar_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = stringResource(R.string.cd_settings)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { signOutGoogle(credentialManager, scope) },
                                modifier = Modifier.testTag("toolbar_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.cd_sign_out)
                                )
                            }
                        }
                    )

                    // Confidential Mode / Isolated School Session Banner
                    val activeSchool = dashboardData?.confidentialSchool
                    if (activeSchool != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (dashboardData.isGoogleOperatorAccount) {
                                            "Espace École : ${activeSchool.code} • ${activeSchool.name}"
                                        } else {
                                            "Confidentiel : ${activeSchool.code} • ${activeSchool.name}"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!dashboardData.isGoogleOperatorAccount) {
                                    TextButton(
                                        onClick = { viewModel.exitConfidentialMode() },
                                        modifier = Modifier.testTag("exit_confidential_mode_button")
                                    ) {
                                        Text(
                                            text = "Quitter",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        visibleTabs.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (dashboardData != null) {
                    val isAdmin = dashboardData.isAdmin
                    val isSuperAdminTab = currentTab == MainTab.SUPERADMIN
                    val isSchoolCreationMode = isAdmin && !isSuperAdminTab &&
                        (currentTab == MainTab.SCHOOLS || dashboardData.schools.isEmpty())
                    val isOperatorCreationMode = isAdmin && !isSuperAdminTab &&
                        currentTab == MainTab.ACCESS && dashboardData.schools.isNotEmpty()
                    val canAddAssessment = !isSuperAdminTab && dashboardData.schools.isNotEmpty()

                    if (isSchoolCreationMode || isOperatorCreationMode || canAddAssessment) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                when {
                                    isSchoolCreationMode -> {
                                        editingSchool = null
                                        showSchoolDialog = true
                                    }
                                    isOperatorCreationMode -> {
                                        editingOperator = null
                                        preselectedOperatorSchoolId = null
                                        showOperatorDialog = true
                                    }
                                    else -> {
                                        openNewAssessment(
                                            targetSchoolId = dashboardData.confidentialSchool?.id ?: dashboardData.filters.schoolId,
                                            targetStudent = null
                                        )
                                    }
                                }
                            },
                            icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add)) },
                            text = {
                                Text(
                                    when {
                                        isSchoolCreationMode -> stringResource(R.string.add_school)
                                        isOperatorCreationMode -> stringResource(R.string.add_operator)
                                        else -> stringResource(R.string.add_assessment)
                                    }
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("primary_fab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        visibleTabs.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        when (val state = uiState) {
                            is UiState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Synchronisation des 40 écoles CM2...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            is UiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Erreur de synchronisation",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { signOutGoogle(credentialManager, scope) }) {
                                        Text("Se reconnecter")
                                    }
                                }
                            }

                            is UiState.Success -> {
                                MainTabContent(
                                    tab = currentTab,
                                    data = state.data,
                                    viewModel = viewModel,
                                    onAddSchool = {
                                        editingSchool = null
                                        showSchoolDialog = true
                                    },
                                    onEditSchool = { school ->
                                        editingSchool = school
                                        showSchoolDialog = true
                                    },
                                    onSetAccessCode = { school ->
                                        schoolForCodeSetup = school
                                    },
                                    onRequestUnlockSchool = { school, afterUnlock ->
                                        requestUnlock(school, afterUnlock)
                                    },
                                    onAddAssessment = { schoolId, student ->
                                        openNewAssessment(schoolId, student)
                                    },
                                    onEditAssessment = { assessment ->
                                        editingAssessment = assessment
                                        preselectedSchoolId = assessment.schoolId
                                        preselectedStudent = null
                                        showAssessmentDialog = true
                                    },
                                    onAddStudent = { school ->
                                        editingStudent = null
                                        studentDialogSchool = school
                                        showStudentDialog = true
                                    },
                                    onEditStudent = { school, student ->
                                        editingStudent = student
                                        studentDialogSchool = school
                                        showStudentDialog = true
                                    },
                                    onAddOperator = {
                                        editingOperator = null
                                        preselectedOperatorSchoolId = null
                                        showOperatorDialog = true
                                    },
                                    onEditOperator = { op ->
                                        editingOperator = op
                                        preselectedOperatorSchoolId = op.schoolId
                                        showOperatorDialog = true
                                    },
                                    onAddAdministrator = { showAdministratorDialog = true },
                                    onToggleAdministrator = { admin ->
                                        viewModel.setAdministratorActive(admin.email, !admin.active)
                                    },
                                    onAddSchoolForAdmin = { admin ->
                                        selectedSuperAdmin = admin
                                        showSuperSchoolDialog = true
                                    },
                                    onAddOperatorForSchool = { school ->
                                        editingOperator = null
                                        preselectedOperatorSchoolId = school.id
                                        showOperatorDialog = true
                                    },
                                    onOpenConfidentialLogin = { showSchoolModeDialog = true },
                                    onOpenSettings = { showSettingsDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSchoolDialog && dashboardData != null && dashboardData.isAdmin) {
        val nextNum = (dashboardData.schools.size + 1).toString().padStart(2, '0')
        AddEditSchoolDialog(
            initial = editingSchool,
            nextCodeSuggestion = "EC-$nextNum",
            onDismiss = { showSchoolDialog = false },
            onConfirm = { code, name, className, zone, teacher, boys, girls, newAccessCode ->
                viewModel.saveSchool(
                    schoolId = editingSchool?.id,
                    code = code,
                    name = name,
                    className = className,
                    zone = zone,
                    teacherName = teacher,
                    boysEnrolled = boys,
                    girlsEnrolled = girls,
                    newAccessCode = newAccessCode
                ) {
                    showSchoolDialog = false
                }
            }
        )
    }

    if (showAdministratorDialog && dashboardData?.isSuperAdmin == true) {
        AddAdministratorDialog(
            onDismiss = { showAdministratorDialog = false },
            onConfirm = { email, fullName ->
                viewModel.createAdministrator(email, fullName) {
                    showAdministratorDialog = false
                }
            }
        )
    }

    val superAdminForSchool = selectedSuperAdmin
    if (showSuperSchoolDialog && superAdminForSchool != null && dashboardData?.isSuperAdmin == true) {
        val nextNum = (dashboardData.schools.size + 1).toString().padStart(2, '0')
        AddEditSchoolDialog(
            initial = null,
            nextCodeSuggestion = "EC-$nextNum",
            onDismiss = { showSuperSchoolDialog = false },
            onConfirm = { code, name, className, zone, teacher, boys, girls, newAccessCode ->
                viewModel.saveSchoolForAdmin(
                    adminUid = superAdminForSchool.uid,
                    adminEmail = superAdminForSchool.email,
                    code = code,
                    name = name,
                    className = className,
                    zone = zone,
                    teacherName = teacher,
                    boysEnrolled = boys,
                    girlsEnrolled = girls,
                    newAccessCode = newAccessCode
                ) {
                    showSuperSchoolDialog = false
                    selectedSuperAdmin = null
                }
            }
        )
    }

    if (showAssessmentDialog && dashboardData != null && unlockedSchools.isNotEmpty()) {
        AddEditAssessmentDialog(
            initial = editingAssessment,
            schools = unlockedSchools,
            allStudents = dashboardData.students,
            existingAssessments = dashboardData.allAssessments,
            preselectedSchoolId = preselectedSchoolId,
            preselectedStudent = preselectedStudent,
            settings = dashboardData.settings,
            onDismiss = {
                showAssessmentDialog = false
                preselectedStudent = null
            },
            onConfirm = { school, selectedStudentId, studentName, gender, testType, month, week, lcR, lcC, mR, mC, dmR, dmC, ddR, ddC ->
                viewModel.saveAssessment(
                    assessmentId = editingAssessment?.id,
                    school = school,
                    selectedStudentId = selectedStudentId,
                    existingSchoolStudents = dashboardData.studentsForSchool(school.id),
                    studentName = studentName,
                    gender = gender,
                    testType = testType,
                    month = month,
                    weekNumber = week,
                    lcRessources = lcR,
                    lcCompetences = lcC,
                    mathRessources = mR,
                    mathCompetences = mC,
                    dmRessources = dmR,
                    dmCompetences = dmC,
                    ddRessources = ddR,
                    ddCompetences = ddC,
                    settings = dashboardData.settings,
                    recordedBy = dashboardData.recordedByLabel
                ) {
                    showAssessmentDialog = false
                    preselectedStudent = null
                }
            }
        )
    }

    if (showOperatorDialog && dashboardData != null && dashboardData.isAdmin && dashboardData.schools.isNotEmpty()) {
        AddEditOperatorDialog(
            initial = editingOperator,
            schools = dashboardData.schools,
            preselectedSchoolId = preselectedOperatorSchoolId,
            onDismiss = { showOperatorDialog = false },
            onConfirm = { fullName, email, school, active, newPin ->
                viewModel.saveOperator(
                    operatorId = editingOperator?.id,
                    fullName = fullName,
                    email = email,
                    school = school,
                    active = active,
                    newPin = newPin,
                    currentOperators = dashboardData.operators,
                    allStudents = dashboardData.students,
                    allAssessments = dashboardData.allAssessments
                ) {
                    showOperatorDialog = false
                }
            }
        )
    }

    val targetStuSchool = studentDialogSchool
    if (showStudentDialog && targetStuSchool != null) {
        AddEditStudentDialog(
            school = targetStuSchool,
            initial = editingStudent,
            onDismiss = { showStudentDialog = false },
            onConfirm = { fullName, gender, matricule ->
                viewModel.saveStudent(
                    studentId = editingStudent?.id,
                    school = targetStuSchool,
                    fullName = fullName,
                    gender = gender,
                    matricule = matricule
                ) {
                    showStudentDialog = false
                }
            }
        )
    }

    if (showSettingsDialog && dashboardData != null && dashboardData.isAdmin) {
        ThresholdSettingsDialog(
            current = dashboardData.settings,
            onDismiss = { showSettingsDialog = false },
            onSave = { maxPct, minPct, partialPct, year, district ->
                viewModel.saveSettings(
                    maxMasteryPct = maxPct,
                    minMasteryPct = minPct,
                    partialMasteryPct = partialPct,
                    academicYear = year,
                    districtName = district
                ) {
                    showSettingsDialog = false
                }
            }
        )
    }

    val pendingSchool = schoolToUnlock
    if (pendingSchool != null) {
        UnlockSchoolDialog(
            school = pendingSchool,
            onDismiss = {
                schoolToUnlock = null
                onAfterUnlockCallback = null
            },
            onVerify = { code, enterConfidentialMode ->
                viewModel.verifySchoolAccessCode(pendingSchool, code, enterConfidentialMode)
            },
            onUnlocked = {
                val callback = onAfterUnlockCallback
                schoolToUnlock = null
                onAfterUnlockCallback = null
                callback?.invoke()
            }
        )
    }

    if (showSchoolModeDialog && dashboardData != null && dashboardData.schools.isNotEmpty()) {
        SchoolConfidentialModeDialog(
            schools = dashboardData.schools,
            operators = dashboardData.operators,
            onDismiss = { showSchoolModeDialog = false },
            onVerifySchool = { school, code ->
                viewModel.verifySchoolAccessCode(school, code, enterConfidentialMode = true)
            },
            onVerifyOperator = { operator, pin ->
                viewModel.verifyOperatorPin(operator, pin)
            },
            onSuccess = {
                showSchoolModeDialog = false
            }
        )
    }

    val codeSetupSchool = schoolForCodeSetup
    if (codeSetupSchool != null && dashboardData?.isAdmin == true) {
        SetSchoolAccessCodeDialog(
            school = codeSetupSchool,
            onDismiss = { schoolForCodeSetup = null },
            onSave = { newCode ->
                viewModel.updateSchoolAccessCode(codeSetupSchool, newCode) {
                    schoolForCodeSetup = null
                }
            }
        )
    }

    if (generatedCodes.isNotEmpty()) {
        GeneratedAccessCodesDialog(
            codes = generatedCodes,
            onDismiss = { viewModel.dismissGeneratedCodes() }
        )
    }
}

@Composable
private fun MainTabContent(
    tab: MainTab,
    data: SuiviDashboardData,
    viewModel: SuiviViewModel,
    onAddSchool: () -> Unit,
    onEditSchool: (School) -> Unit,
    onSetAccessCode: (School) -> Unit,
    onRequestUnlockSchool: (School, () -> Unit) -> Unit,
    onAddAssessment: (String?, Student?) -> Unit,
    onEditAssessment: (Assessment) -> Unit,
    onAddStudent: (School) -> Unit,
    onEditStudent: (School, Student) -> Unit,
    onAddOperator: () -> Unit,
    onEditOperator: (DataEntryOperator) -> Unit,
    onAddAdministrator: () -> Unit,
    onToggleAdministrator: (AdministratorAccount) -> Unit,
    onAddSchoolForAdmin: (AdministratorAccount) -> Unit,
    onAddOperatorForSchool: (School) -> Unit,
    onOpenConfidentialLogin: () -> Unit,
    onOpenSettings: () -> Unit
) {
    when (tab) {
        MainTab.SYNTHESIS -> SynthesisScreen(
            data = data,
            onTestTypeFilter = viewModel::setTestTypeFilter,
            onMonthFilter = viewModel::setMonthFilter,
            onWeekFilter = viewModel::setWeekFilter,
            onClearFilters = viewModel::clearFilters,
            onBootstrap40Schools = { viewModel.bootstrapFortySchools(data.schools) },
            onAddAssessment = { onAddAssessment(data.confidentialSchool?.id, null) },
            onOpenSettings = onOpenSettings
        )

        MainTab.SCHOOLS -> SchoolsScreen(
            data = data,
            onAddSchool = onAddSchool,
            onEditSchool = onEditSchool,
            onDeleteSchool = { viewModel.deleteSchool(it.id) },
            onBootstrap40Schools = { viewModel.bootstrapFortySchools(data.schools) },
            onGenerateMissingCodes = { viewModel.generateMissingAccessCodes(data.schools) },
            onSetAccessCode = onSetAccessCode,
            onRequestUnlockSchool = onRequestUnlockSchool,
            onLockSchool = viewModel::lockSchool,
            onAddAssessmentForSchool = { schoolId -> onAddAssessment(schoolId, null) },
            onAddAssessmentForStudent = { student -> onAddAssessment(student.schoolId, student) },
            onEditAssessment = onEditAssessment,
            onDeleteAssessment = { viewModel.deleteAssessment(it.id) },
            onAddStudent = onAddStudent,
            onEditStudent = onEditStudent,
            onDeleteStudent = { viewModel.deleteStudent(it.id) }
        )

        MainTab.TESTS -> AssessmentsScreen(
            data = data,
            onTestTypeFilter = viewModel::setTestTypeFilter,
            onMonthFilter = viewModel::setMonthFilter,
            onWeekFilter = viewModel::setWeekFilter,
            onGenderFilter = viewModel::setGenderFilter,
            onClearFilters = viewModel::clearFilters,
            onRequestUnlockSchool = { school -> onRequestUnlockSchool(school) {} },
            onAddAssessment = { onAddAssessment(data.confidentialSchool?.id ?: data.filters.schoolId, null) },
            onEditAssessment = onEditAssessment,
            onDeleteAssessment = { viewModel.deleteAssessment(it.id) }
        )

        MainTab.DIAGNOSTICS -> DiagnosticsScreen(
            data = data,
            onTestTypeFilter = viewModel::setTestTypeFilter,
            onMonthFilter = viewModel::setMonthFilter,
            onWeekFilter = viewModel::setWeekFilter,
            onClearFilters = viewModel::clearFilters,
            onAddAssessment = { onAddAssessment(data.confidentialSchool?.id, null) }
        )

        MainTab.ACCESS -> OperatorsScreen(
            data = data,
            onAddOperator = onAddOperator,
            onEditOperator = onEditOperator,
            onToggleOperatorActive = { op, active ->
                viewModel.toggleOperatorActive(
                    operator = op,
                    newActive = active,
                    currentOperators = data.operators,
                    allStudents = data.students,
                    allAssessments = data.allAssessments
                )
            },
            onDeleteOperator = { op ->
                viewModel.deleteOperator(
                    operator = op,
                    currentOperators = data.operators,
                    allStudents = data.students,
                    allAssessments = data.allAssessments
                )
            },
            onAddSchool = onAddSchool,
            onBootstrap40Schools = { viewModel.bootstrapFortySchools(data.schools) },
            onGenerateMissingCodes = { viewModel.generateMissingAccessCodes(data.schools) },
            onSetSchoolAccessCode = onSetAccessCode,
            onOpenConfidentialLogin = onOpenConfidentialLogin
        )

        MainTab.SUPERADMIN -> SuperAdminScreen(
            data = data,
            onAddAdministrator = onAddAdministrator,
            onToggleAdministrator = onToggleAdministrator,
            onAddSchoolForAdmin = onAddSchoolForAdmin,
            onAddOperatorForSchool = onAddOperatorForSchool
        )
    }
}
