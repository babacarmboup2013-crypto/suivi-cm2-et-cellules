package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccessCodeSecurity
import com.example.data.AdministratorAccount
import com.example.data.Assessment
import com.example.data.DataEntryOperator
import com.example.data.Gender
import com.example.data.GeneratedSchoolCode
import com.example.data.GlobalSynthesis
import com.example.data.OperatorRepository
import com.example.data.School
import com.example.data.SessionRole
import com.example.data.StatisticsEngine
import com.example.data.Student
import com.example.data.SuiviRepository
import com.example.data.SuperAdminAccount
import com.example.data.TestType
import com.example.data.ThresholdSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AccessVerificationResult {
    data object Success : AccessVerificationResult
    data class InvalidCode(val remainingAttempts: Int) : AccessVerificationResult
    data class LockedOut(val secondsRemaining: Long) : AccessVerificationResult
    data class Error(val message: String) : AccessVerificationResult
}

data class FilterState(
    val testType: TestType? = null,
    val month: String? = null,
    val weekNumber: Int? = null,
    val schoolId: String? = null,
    val gender: Gender? = null,
    val searchQuery: String = ""
)

data class SuiviDashboardData(
    val role: SessionRole,
    val isSuperAdmin: Boolean = false,
    val isGoogleOperatorAccount: Boolean = false,
    val activeOperator: DataEntryOperator? = null,
    val schools: List<School>,
    val administrators: List<AdministratorAccount> = emptyList(),
    val operators: List<DataEntryOperator> = emptyList(),
    val students: List<Student> = emptyList(),
    val allAssessments: List<Assessment>,
    val filteredAssessments: List<Assessment>,
    val settings: ThresholdSettings,
    val filters: FilterState,
    val synthesis: GlobalSynthesis,
    val unlockedSchoolIds: Set<String> = emptySet(),
    val confidentialSchool: School? = null,
    val recordedByLabel: String = ""
) {
    val isAdmin: Boolean get() = role == SessionRole.ADMIN || role == SessionRole.SUPERADMIN

    fun isSchoolUnlocked(school: School): Boolean {
        if (isGoogleOperatorAccount) return true
        if (!school.hasAccessCode) return true
        return school.id in unlockedSchoolIds || confidentialSchool?.id == school.id
    }

    fun studentsForSchool(schoolId: String): List<Student> =
        students.filter { it.schoolId == schoolId }
}

private data class SessionPrivacyState(
    val unlockedSchoolIds: Set<String> = emptySet(),
    val confidentialSchoolId: String? = null,
    val activeOperatorId: String? = null
)

private data class AdminWorkspaceBundle(
    val schools: List<School>,
    val operators: List<DataEntryOperator>,
    val students: List<Student>,
    val assessments: List<Assessment>
)

private data class OperatorWorkspaceBundle(
    val myAssignments: List<DataEntryOperator>,
    val schools: List<School>,
    val students: List<Student>,
    val assessments: List<Assessment>
)

private data class GlobalWorkspaceBundle(
    val schools: List<School>,
    val operators: List<DataEntryOperator>,
    val students: List<Student>,
    val assessments: List<Assessment>,
    val settings: List<ThresholdSettings>,
    val administrators: List<AdministratorAccount>
)

private data class UiContextBundle(
    val admin: AdminWorkspaceBundle,
    val operator: OperatorWorkspaceBundle,
    val settings: ThresholdSettings,
    val filters: FilterState,
    val privacy: SessionPrivacyState
)

private data class RoleContextBundle(
    val superAdmin: SuperAdminAccount?,
    val selfAdministrator: AdministratorAccount?,
    val global: GlobalWorkspaceBundle
)

class SuiviViewModel(
    private val repository: SuiviRepository,
    private val operatorRepository: OperatorRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserDisplayName: String
) : ViewModel() {

    init {
        // Un administrateur créé par le superadministrateur revendique son compte
        // dès sa première connexion Google. Les refus sont silencieux pour les
        // simples opérateurs ou les comptes non autorisés.
        if (currentUserEmail.isNotBlank() &&
            !currentUserEmail.equals(BOOTSTRAP_SUPERADMIN_EMAIL, ignoreCase = true)
        ) {
            viewModelScope.launch {
                runCatching { repository.claimAdministratorAccount(currentUserEmail, currentUserId) }
            }
        }
    }

    private val _filters = MutableStateFlow(FilterState())
    val filters: StateFlow<FilterState> = _filters.asStateFlow()

    private val _privacyState = MutableStateFlow(SessionPrivacyState())

    private val _generatedCodes = MutableStateFlow<List<GeneratedSchoolCode>>(emptyList())
    val generatedCodes: StateFlow<List<GeneratedSchoolCode>> = _generatedCodes.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // In-memory brute-force protection per schoolId / operatorId
    private val failedAttempts = mutableMapOf<String, Int>()
    private val lockoutUntilMs = mutableMapOf<String, Long>()

    private val adminBundleFlow: Flow<AdminWorkspaceBundle> = combine(
        repository.observeAdminSchools(currentUserId),
        repository.observeAdminOperators(currentUserId),
        repository.observeAdminStudents(currentUserId),
        repository.observeAdminAssessments(currentUserId)
    ) { schools, operators, students, assessments ->
        AdminWorkspaceBundle(schools, operators, students, assessments)
    }

    private val operatorBundleFlow: Flow<OperatorWorkspaceBundle> = combine(
        repository.observeMyOperatorAssignments(currentUserId, currentUserEmail),
        repository.observeOperatorSchools(currentUserId, currentUserEmail),
        repository.observeOperatorStudents(currentUserId, currentUserEmail),
        repository.observeOperatorAssessments(currentUserId, currentUserEmail)
    ) { myOps, schools, students, assessments ->
        OperatorWorkspaceBundle(myOps, schools, students, assessments)
    }

    private val settingsFlow: Flow<ThresholdSettings> = repository.observeSettings(currentUserId)

    private val superAdminFlow: Flow<SuperAdminAccount?> = repository.observeSuperAdmin(currentUserId)
    private val administratorSelfFlow: Flow<AdministratorAccount?> =
        repository.observeAdministratorByEmail(currentUserEmail)
    private val allAdministratorsFlow: Flow<List<AdministratorAccount>> = repository.observeAllAdministrators()

    private val globalCoreBundleFlow = combine(
        repository.observeAllSchools(),
        operatorRepository.observeAllOperators(),
        repository.observeAllStudents(),
        repository.observeAllAssessments()
    ) { schools, operators, students, assessments ->
        AdminWorkspaceBundle(schools, operators, students, assessments)
    }

    private val globalBundleFlow: Flow<GlobalWorkspaceBundle> = combine(
        globalCoreBundleFlow,
        repository.observeAllSettings(),
        allAdministratorsFlow
    ) { core, settings, administrators ->
        GlobalWorkspaceBundle(
            schools = core.schools,
            operators = core.operators,
            students = core.students,
            assessments = core.assessments,
            settings = settings,
            administrators = administrators
        )
    }

    private val uiContextBundleFlow: Flow<UiContextBundle> = combine(
        adminBundleFlow,
        operatorBundleFlow,
        settingsFlow,
        _filters,
        _privacyState
    ) { adminBundle, opBundle, settings, filters, privacy ->
        UiContextBundle(adminBundle, opBundle, settings, filters, privacy)
    }

    private val roleContextBundleFlow: Flow<RoleContextBundle> = combine(
        superAdminFlow,
        administratorSelfFlow,
        globalBundleFlow
    ) { superAdmin, selfAdministrator, global ->
        RoleContextBundle(superAdmin, selfAdministrator, global)
    }

    val uiState: StateFlow<UiState<SuiviDashboardData>> = combine(
        uiContextBundleFlow,
        roleContextBundleFlow
    ) { context, roles ->
        val adminBundle = context.admin
        val opBundle = context.operator
        val settings = context.settings
        val filters = context.filters
        val privacy = context.privacy
        val cleanEmail = currentUserEmail.trim().lowercase()

        val isSuperAdmin = roles.superAdmin?.active == true ||
            cleanEmail == BOOTSTRAP_SUPERADMIN_EMAIL
        val isAdministratorRecord = roles.selfAdministrator?.active == true &&
            (roles.selfAdministrator.uid.isBlank() || roles.selfAdministrator.uid == currentUserId)
        val isPureGoogleOperator = !isSuperAdmin && !isAdministratorRecord &&
            opBundle.myAssignments.isNotEmpty() &&
            adminBundle.schools.isEmpty()

        val baseSchools = if (isSuperAdmin) roles.global.schools else adminBundle.schools
        val baseOperators = if (isSuperAdmin) roles.global.operators else adminBundle.operators
        val baseStudents = if (isSuperAdmin) roles.global.students else adminBundle.students
        val baseAssessments = if (isSuperAdmin) roles.global.assessments else adminBundle.assessments

        val role: SessionRole
        val activeOperator: DataEntryOperator?
        val activeConfidentialSchool: School?
        val scopedSchools: List<School>
        val scopedStudents: List<Student>
        val scopedAssessments: List<Assessment>
        val exposedOperators: List<DataEntryOperator>
        val exposedAdministrators: List<AdministratorAccount>
        val unlockedIds: Set<String>
        val recordedByLabel: String

        if (isPureGoogleOperator) {
            role = SessionRole.OPERATOR
            activeOperator = opBundle.myAssignments.firstOrNull()
            activeConfidentialSchool = opBundle.schools.firstOrNull()
            scopedSchools = opBundle.schools
            scopedStudents = opBundle.students
            scopedAssessments = opBundle.assessments
            exposedOperators = emptyList()
            exposedAdministrators = emptyList()
            unlockedIds = opBundle.schools.map { it.id }.toSet()
            recordedByLabel = activeOperator?.fullName?.ifBlank { currentUserDisplayName }
                ?: currentUserDisplayName.ifBlank { cleanEmail }
        } else {
            val sharedOp = privacy.activeOperatorId?.let { opId ->
                baseOperators.firstOrNull { it.id == opId && it.active }
            }
            val confSchool = when {
                sharedOp != null -> baseSchools.firstOrNull { it.id == sharedOp.schoolId }
                privacy.confidentialSchoolId != null -> baseSchools.firstOrNull { it.id == privacy.confidentialSchoolId }
                else -> null
            }
            role = when {
                sharedOp != null || confSchool != null -> SessionRole.OPERATOR
                isSuperAdmin -> SessionRole.SUPERADMIN
                else -> SessionRole.ADMIN
            }
            activeOperator = sharedOp
            activeConfidentialSchool = confSchool
            scopedSchools = if (confSchool != null) listOf(confSchool) else baseSchools
            scopedStudents = if (confSchool != null) {
                baseStudents.filter { it.schoolId == confSchool.id }
            } else {
                baseStudents
            }
            scopedAssessments = if (confSchool != null) {
                baseAssessments.filter { it.schoolId == confSchool.id }
            } else {
                baseAssessments
            }
            exposedOperators = if (role == SessionRole.OPERATOR) emptyList() else baseOperators
            exposedAdministrators = if (isSuperAdmin) roles.global.administrators else emptyList()
            unlockedIds = privacy.unlockedSchoolIds
            recordedByLabel = sharedOp?.fullName?.ifBlank { currentUserDisplayName }
                ?: currentUserDisplayName.ifBlank {
                    if (isSuperAdmin) "Superadministrateur" else "Administrateur"
                }
        }

        val filtered = scopedAssessments.filter { a ->
            val matchType = filters.testType == null || a.testTypeEnum == filters.testType
            val matchMonth = filters.month == null || a.month == filters.month
            val matchWeek = filters.weekNumber == null ||
                (a.testTypeEnum == TestType.HEBDOMADAIRE && a.weekNumber.toInt() == filters.weekNumber)
            val matchSchool = filters.schoolId == null || a.schoolId == filters.schoolId
            val matchGender = filters.gender == null || a.genderEnum == filters.gender
            matchType && matchMonth && matchWeek && matchSchool && matchGender
        }

        val trendScope = scopedAssessments.filter { a ->
            val matchSchool = filters.schoolId == null || a.schoolId == filters.schoolId
            val matchGender = filters.gender == null || a.genderEnum == filters.gender
            matchSchool && matchGender
        }

        val synthesis = StatisticsEngine.compute(
            schools = scopedSchools,
            filteredAssessments = filtered,
            allAssessmentsForTrends = trendScope,
            settings = settings
        )

        SuiviDashboardData(
            role = role,
            isSuperAdmin = isSuperAdmin,
            isGoogleOperatorAccount = isPureGoogleOperator,
            activeOperator = activeOperator,
            schools = scopedSchools,
            administrators = exposedAdministrators,
            operators = exposedOperators,
            students = scopedStudents,
            allAssessments = scopedAssessments,
            filteredAssessments = filtered,
            settings = settings,
            filters = filters,
            synthesis = synthesis,
            unlockedSchoolIds = unlockedIds,
            confidentialSchool = activeConfidentialSchool,
            recordedByLabel = recordedByLabel
        )
    }
        .map<SuiviDashboardData, UiState<SuiviDashboardData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Erreur d'observation Firestore", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Erreur de chargement des données CM2."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    // --- ACCESS CODE & OPERATOR PIN VERIFICATION ---

    suspend fun verifySchoolAccessCode(
        school: School,
        rawCode: String,
        enterConfidentialMode: Boolean = false
    ): AccessVerificationResult {
        if (!school.hasAccessCode) {
            unlockInMemory(school.id, enterConfidentialMode, operatorId = null)
            return AccessVerificationResult.Success
        }
        return verifyRateLimited(
            key = "school_${school.id}",
            rawInput = rawCode,
            storedHash = school.accessCodeHash,
            storedSalt = school.accessCodeSalt
        ) {
            unlockInMemory(school.id, enterConfidentialMode, operatorId = null)
            _snackbarMessage.value = if (enterConfidentialMode) {
                "Mode Saisie École activé : ${school.code} • ${school.name}"
            } else {
                "Accès autorisé pour ${school.code} • ${school.name}"
            }
        }
    }

    suspend fun verifyOperatorPin(
        operator: DataEntryOperator,
        rawPin: String
    ): AccessVerificationResult {
        if (!operator.active) {
            return AccessVerificationResult.Error("Cet utilisateur de saisie est désactivé.")
        }
        if (!operator.hasPersonalPin) {
            return AccessVerificationResult.Error("Aucun code PIN personnel n'est défini pour cet opérateur.")
        }
        return verifyRateLimited(
            key = "op_${operator.id}",
            rawInput = rawPin,
            storedHash = operator.pinHash,
            storedSalt = operator.pinSalt
        ) {
            unlockInMemory(operator.schoolId, enterConfidentialMode = true, operatorId = operator.id)
            _snackbarMessage.value = "Session de saisie ouverte : ${operator.fullName} (${operator.schoolCode})"
        }
    }

    private suspend fun verifyRateLimited(
        key: String,
        rawInput: String,
        storedHash: String,
        storedSalt: String,
        onVerified: () -> Unit
    ): AccessVerificationResult {
        val now = System.currentTimeMillis()
        val lockedUntil = lockoutUntilMs[key] ?: 0L
        if (now < lockedUntil) {
            val secs = ((lockedUntil - now) / 1000L).coerceAtLeast(1L)
            return AccessVerificationResult.LockedOut(secs)
        }

        val isValid = withContext(Dispatchers.Default) {
            AccessCodeSecurity.verifyAccessCode(
                rawInput = rawInput,
                storedHashHex = storedHash,
                storedSaltHex = storedSalt
            )
        }

        return if (isValid) {
            failedAttempts.remove(key)
            lockoutUntilMs.remove(key)
            onVerified()
            AccessVerificationResult.Success
        } else {
            val attempts = (failedAttempts[key] ?: 0) + 1
            if (attempts >= MAX_ATTEMPTS) {
                failedAttempts[key] = 0
                lockoutUntilMs[key] = now + LOCKOUT_DURATION_MS
                AccessVerificationResult.LockedOut(LOCKOUT_DURATION_MS / 1000L)
            } else {
                failedAttempts[key] = attempts
                AccessVerificationResult.InvalidCode(MAX_ATTEMPTS - attempts)
            }
        }
    }

    private fun unlockInMemory(schoolId: String, enterConfidentialMode: Boolean, operatorId: String?) {
        val current = _privacyState.value
        _privacyState.value = current.copy(
            unlockedSchoolIds = current.unlockedSchoolIds + schoolId,
            confidentialSchoolId = if (enterConfidentialMode) schoolId else current.confidentialSchoolId,
            activeOperatorId = if (enterConfidentialMode) operatorId else current.activeOperatorId
        )
    }

    fun lockSchool(schoolId: String) {
        val current = _privacyState.value
        _privacyState.value = current.copy(
            unlockedSchoolIds = current.unlockedSchoolIds - schoolId,
            confidentialSchoolId = if (current.confidentialSchoolId == schoolId) null else current.confidentialSchoolId,
            activeOperatorId = if (current.confidentialSchoolId == schoolId) null else current.activeOperatorId
        )
        _snackbarMessage.value = "Données de l'école verrouillées."
    }

    fun lockAllSchools() {
        _privacyState.value = SessionPrivacyState()
        _snackbarMessage.value = "Toutes les écoles ont été verrouillées."
    }

    fun exitConfidentialMode() {
        val current = _privacyState.value
        val cid = current.confidentialSchoolId
        _privacyState.value = current.copy(
            unlockedSchoolIds = if (cid != null) current.unlockedSchoolIds - cid else current.unlockedSchoolIds,
            confidentialSchoolId = null,
            activeOperatorId = null
        )
        _snackbarMessage.value = "Retour au mode Administrateur."
    }

    fun dismissGeneratedCodes() {
        _generatedCodes.value = emptyList()
    }

    // --- FILTERS ---

    fun setTestTypeFilter(type: TestType?) {
        _filters.value = _filters.value.copy(
            testType = type,
            weekNumber = if (type == TestType.MENSUEL) null else _filters.value.weekNumber
        )
    }

    fun setMonthFilter(month: String?) {
        _filters.value = _filters.value.copy(month = month)
    }

    fun setWeekFilter(week: Int?) {
        _filters.value = _filters.value.copy(weekNumber = week)
    }

    fun setSchoolFilter(schoolId: String?) {
        _filters.value = _filters.value.copy(schoolId = schoolId)
    }

    fun setGenderFilter(gender: Gender?) {
        _filters.value = _filters.value.copy(gender = gender)
    }

    fun clearFilters() {
        _filters.value = FilterState()
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // --- SUPERADMIN MUTATIONS ---

    fun createAdministrator(
        email: String,
        fullName: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createAdministrator(email, fullName, currentUserId)
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = "Administrateur créé : $email"
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de créer l'administrateur."
            }
        }
    }

    fun setAdministratorActive(
        email: String,
        active: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.setAdministratorActive(email, active, currentUserId)
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = if (active) {
                    "Administrateur réactivé : $email"
                } else {
                    "Administrateur suspendu : $email"
                }
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de modifier l'administrateur."
            }
        }
    }

    fun saveSchoolForAdmin(
        adminUid: String,
        adminEmail: String,
        code: String,
        name: String,
        className: String,
        zone: String,
        teacherName: String,
        boysEnrolled: Int,
        girlsEnrolled: Int,
        newAccessCode: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveSchool(
                userId = currentUserId,
                schoolId = null,
                code = code,
                name = name,
                className = className,
                zone = zone,
                teacherName = teacherName,
                boysEnrolled = boysEnrolled,
                girlsEnrolled = girlsEnrolled,
                newAccessCode = newAccessCode,
                ownerUid = adminUid.ifBlank { adminEmail },
                ownerEmail = adminEmail
            )
            _isBusy.value = false
            result.onSuccess { generated ->
                if (generated != null) _generatedCodes.value = listOf(generated)
                _snackbarMessage.value = "École $code créée pour ${adminEmail.ifBlank { "cet administrateur" }}."
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de créer l'école."
            }
        }
    }

    // --- ADMIN SCHOOL MUTATIONS ---

    fun saveSchool(
        schoolId: String?,
        code: String,
        name: String,
        className: String,
        zone: String,
        teacherName: String,
        boysEnrolled: Int,
        girlsEnrolled: Int,
        newAccessCode: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveSchool(
                userId = currentUserId,
                schoolId = schoolId,
                code = code,
                name = name,
                className = className,
                zone = zone,
                teacherName = teacherName,
                boysEnrolled = boysEnrolled,
                girlsEnrolled = girlsEnrolled,
                newAccessCode = newAccessCode
            )
            _isBusy.value = false
            result.onSuccess { generatedCode ->
                if (generatedCode != null) {
                    _generatedCodes.value = listOf(generatedCode)
                }
                _snackbarMessage.value = if (schoolId == null) {
                    "École CM2 créée avec code d'accès confidentiel."
                } else {
                    "École CM2 mise à jour."
                }
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible d'enregistrer l'école."
            }
        }
    }

    fun updateSchoolAccessCode(
        school: School,
        newAccessCode: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateSchoolAccessCode(currentUserId, school, newAccessCode)
            _isBusy.value = false
            result.onSuccess { generated ->
                val current = _privacyState.value
                _privacyState.value = current.copy(unlockedSchoolIds = current.unlockedSchoolIds - school.id)
                _generatedCodes.value = listOf(generated)
                _snackbarMessage.value = "Code d'accès mis à jour pour ${school.code}."
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de modifier le code d'accès."
            }
        }
    }

    fun generateMissingAccessCodes(schools: List<School>) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.generateMissingAccessCodes(currentUserId, schools)
            _isBusy.value = false
            result.onSuccess { generated ->
                if (generated.isNotEmpty()) {
                    _generatedCodes.value = generated
                    _snackbarMessage.value = "${generated.size} codes d'accès confidentiels générés."
                } else {
                    _snackbarMessage.value = "Toutes les écoles disposent déjà d'un code d'accès."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Erreur lors de la génération des codes."
            }
        }
    }

    fun deleteSchool(schoolId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteSchool(currentUserId, schoolId)
            _isBusy.value = false
            result.onSuccess {
                if (_filters.value.schoolId == schoolId) {
                    _filters.value = _filters.value.copy(schoolId = null)
                }
                val current = _privacyState.value
                _privacyState.value = current.copy(
                    unlockedSchoolIds = current.unlockedSchoolIds - schoolId,
                    confidentialSchoolId = if (current.confidentialSchoolId == schoolId) null else current.confidentialSchoolId
                )
                _snackbarMessage.value = "École supprimée."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de supprimer l'école."
            }
        }
    }

    fun bootstrapFortySchools(existingSchools: List<School>) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.bootstrapFortySchools(currentUserId, existingSchools)
            _isBusy.value = false
            result.onSuccess { generated ->
                if (generated.isNotEmpty()) {
                    _generatedCodes.value = generated
                    _snackbarMessage.value = "${generated.size} écoles CM2 initialisées avec codes d'accès uniques."
                } else {
                    _snackbarMessage.value = "Les 40 écoles CM2 sont déjà configurées."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Erreur d'initialisation des 40 écoles."
            }
        }
    }

    // --- ADMIN OPERATOR MUTATIONS ---

    fun saveOperator(
        operatorId: String?,
        fullName: String,
        email: String,
        school: School,
        active: Boolean,
        newPin: String?,
        currentOperators: List<DataEntryOperator>,
        allStudents: List<Student>,
        allAssessments: List<Assessment>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = operatorRepository.saveOperator(
                adminUid = currentUserId,
                operatorId = operatorId,
                fullName = fullName,
                email = email,
                school = school,
                active = active,
                newPin = newPin,
                currentOperators = currentOperators,
                allStudents = allStudents,
                allAssessments = allAssessments
            )
            _isBusy.value = false
            result.onSuccess { generatedPin ->
                if (generatedPin != null) {
                    _generatedCodes.value = listOf(generatedPin)
                }
                _snackbarMessage.value = if (operatorId == null) {
                    "Utilisateur de saisie créé pour ${school.code}."
                } else {
                    "Utilisateur de saisie mis à jour."
                }
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible d'enregistrer l'utilisateur de saisie."
            }
        }
    }

    fun toggleOperatorActive(
        operator: DataEntryOperator,
        newActive: Boolean,
        currentOperators: List<DataEntryOperator>,
        allStudents: List<Student>,
        allAssessments: List<Assessment>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = operatorRepository.toggleOperatorActive(
                adminUid = currentUserId,
                operator = operator,
                newActive = newActive,
                currentOperators = currentOperators,
                allStudents = allStudents,
                allAssessments = allAssessments
            )
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = if (newActive) {
                    "Accès réactivé pour ${operator.fullName}."
                } else {
                    "Accès suspendu pour ${operator.fullName}."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de modifier le statut."
            }
        }
    }

    fun deleteOperator(
        operator: DataEntryOperator,
        currentOperators: List<DataEntryOperator>,
        allStudents: List<Student>,
        allAssessments: List<Assessment>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = operatorRepository.deleteOperator(
                adminUid = currentUserId,
                operator = operator,
                currentOperators = currentOperators,
                allStudents = allStudents,
                allAssessments = allAssessments
            )
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = "Utilisateur de saisie supprimé."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de supprimer l'utilisateur."
            }
        }
    }

    // --- STUDENT ROSTER MUTATIONS (Admin & assigned Operators) ---

    fun saveStudent(
        studentId: String?,
        school: School,
        fullName: String,
        gender: Gender,
        matricule: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = operatorRepository.saveStudent(
                userId = currentUserId,
                studentId = studentId,
                school = school,
                fullName = fullName,
                gender = gender,
                matricule = matricule
            )
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = if (studentId == null) {
                    "Élève ajouté à la liste de ${school.code}."
                } else {
                    "Fiche élève mise à jour."
                }
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible d'enregistrer l'élève."
            }
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = operatorRepository.deleteStudent(currentUserId, studentId)
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = "Élève retiré de la liste."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de supprimer l'élève."
            }
        }
    }

    // --- ASSESSMENTS (auto-registers student on first test) ---

    fun saveAssessment(
        assessmentId: String?,
        school: School,
        selectedStudentId: String?,
        existingSchoolStudents: List<Student>,
        studentName: String,
        gender: Gender,
        testType: TestType,
        month: String,
        weekNumber: Int,
        lcRessources: Double,
        lcCompetences: Double,
        mathRessources: Double,
        mathCompetences: Double,
        dmRessources: Double,
        dmCompetences: Double,
        ddRessources: Double,
        ddCompetences: Double,
        settings: ThresholdSettings,
        recordedBy: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveAssessment(
                userId = currentUserId,
                assessmentId = assessmentId,
                school = school,
                selectedStudentId = selectedStudentId,
                existingSchoolStudents = existingSchoolStudents,
                studentName = studentName,
                gender = gender,
                testType = testType,
                month = month,
                weekNumber = weekNumber,
                lcRessources = lcRessources,
                lcCompetences = lcCompetences,
                mathRessources = mathRessources,
                mathCompetences = mathCompetences,
                dmRessources = dmRessources,
                dmCompetences = dmCompetences,
                ddRessources = ddRessources,
                ddCompetences = ddCompetences,
                settings = settings,
                recordedBy = recordedBy
            )
            _isBusy.value = false
            result.onSuccess { createdNewStudent ->
                _snackbarMessage.value = when {
                    assessmentId != null -> "Résultat du test mis à jour."
                    createdNewStudent -> "Test enregistré et élève mémorisé pour les prochains tests."
                    else -> "Résultat du test enregistré."
                }
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible d'enregistrer le test."
            }
        }
    }

    fun deleteAssessment(assessmentId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteAssessment(currentUserId, assessmentId)
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = "Évaluation supprimée."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible de supprimer l'évaluation."
            }
        }
    }

    fun saveSettings(
        maxMasteryPct: Double,
        minMasteryPct: Double,
        partialMasteryPct: Double,
        academicYear: String,
        districtName: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveSettings(
                userId = currentUserId,
                maxMasteryPct = maxMasteryPct,
                minMasteryPct = minMasteryPct,
                partialMasteryPct = partialMasteryPct,
                academicYear = academicYear,
                districtName = districtName
            )
            _isBusy.value = false
            result.onSuccess {
                _snackbarMessage.value = "Seuils de maîtrise enregistrés."
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Impossible d'enregistrer les seuils."
            }
        }
    }

    private companion object {
        const val TAG = "SuiviViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L
        const val BOOTSTRAP_SUPERADMIN_EMAIL = "babacarmboup2013@gmail.com"
    }
}
