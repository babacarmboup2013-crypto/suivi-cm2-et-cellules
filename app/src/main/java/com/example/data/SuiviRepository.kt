package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SuiviRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Connexion Google requise avant d'accéder à Firestore.")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Session utilisateur modifiée ; opération annulée.")
        }
        return uid
    }

    /** Vérifie seulement que la session est ouverte ; les règles Firestore vérifient le rôle. */
    private fun requireSignedIn(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Connexion Google requise avant d'accéder à Firestore.")

    private fun currentLowerEmail(): String =
        auth.currentUser?.email?.trim()?.lowercase().orEmpty()

    private fun verifiedLowerEmail(expectedEmail: String): String? {
        val user = auth.currentUser ?: return null
        if (!user.isEmailVerified) return null
        val clean = expectedEmail.trim().lowercase()
        val authEmail = user.email?.trim()?.lowercase().orEmpty()
        return if (clean.isNotBlank() && clean == authEmail) clean else null
    }

    // --- ADMIN STREAMS (1:1 parity with adminUid == request.auth.uid) ---

    fun observeAdminSchools(userId: String): Flow<List<School>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("schools")
                .whereEqualTo("adminUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        School::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.code }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "schools", uid)
                    throw error
                }
        )
    }

    fun observeAdminOperators(userId: String): Flow<List<DataEntryOperator>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("operators")
                .whereEqualTo("adminUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DataEntryOperator::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<DataEntryOperator> { it.schoolCode }.thenBy { it.fullName.lowercase() })
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "operators", uid)
                    throw error
                }
        )
    }

    fun observeAdminStudents(userId: String): Flow<List<Student>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("students")
                .whereEqualTo("adminUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "students", uid)
                    throw error
                }
        )
    }

    fun observeAdminAssessments(userId: String): Flow<List<Assessment>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("assessments")
                .whereEqualTo("adminUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Assessment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "assessments", uid)
                    throw error
                }
        )
    }

    // --- OPERATOR STREAMS (1:1 parity with isVerifiedEmailIn(operatorEmails)) ---

    fun observeMyOperatorAssignments(userId: String, email: String): Flow<List<DataEntryOperator>> = flow {
        val uid = requireUserId(userId)
        val verifiedEmail = verifiedLowerEmail(email)
        if (verifiedEmail == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("operators")
                .whereEqualTo("email", verifiedEmail)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DataEntryOperator::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "operators", uid)
                    emit(emptyList())
                }
        )
    }

    fun observeOperatorSchools(userId: String, email: String): Flow<List<School>> = flow {
        val uid = requireUserId(userId)
        val verifiedEmail = verifiedLowerEmail(email)
        if (verifiedEmail == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("schools")
                .whereArrayContains("operatorEmails", verifiedEmail)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        School::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.code }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "schools", uid)
                    emit(emptyList())
                }
        )
    }

    fun observeOperatorStudents(userId: String, email: String): Flow<List<Student>> = flow {
        val uid = requireUserId(userId)
        val verifiedEmail = verifiedLowerEmail(email)
        if (verifiedEmail == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("students")
                .whereArrayContains("operatorEmails", verifiedEmail)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "students", uid)
                    emit(emptyList())
                }
        )
    }

    fun observeOperatorAssessments(userId: String, email: String): Flow<List<Assessment>> = flow {
        val uid = requireUserId(userId)
        val verifiedEmail = verifiedLowerEmail(email)
        if (verifiedEmail == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("assessments")
                .whereArrayContains("operatorEmails", verifiedEmail)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Assessment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.LIST, "assessments", uid)
                    emit(emptyList())
                }
        )
    }

    fun observeSettings(userId: String): Flow<ThresholdSettings> = flow {
        val uid = requireUserId(userId)
        val path = "settings/$uid"
        emitAll(
            db.collection("settings").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            ThresholdSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: ThresholdSettings(id = uid, adminUid = uid)
                    } else {
                        ThresholdSettings(id = uid, adminUid = uid)
                    }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, OperationType.GET, path, uid)
                    emit(ThresholdSettings(id = uid, adminUid = uid))
                }
        )
    }

    // --- SUPERADMIN / GLOBAL STREAMS ---

    fun observeSuperAdmin(uid: String): Flow<SuperAdminAccount?> = flow {
        val signedUid = requireUserId(uid)
        emitAll(
            db.collection("superAdmins").document(signedUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        SuperAdminAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { emit(null) }
        )
    }

    fun observeAdministratorByEmail(email: String): Flow<AdministratorAccount?> = flow {
        requireSignedIn()
        val normalized = email.trim().lowercase()
        if (normalized.isBlank() || normalized.contains("/")) {
            emit(null)
            return@flow
        }
        emitAll(
            db.collection("administrators").document(normalized)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        AdministratorAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { emit(null) }
        )
    }

    fun observeAllAdministrators(): Flow<List<AdministratorAccount>> = flow {
        requireSignedIn()
        emitAll(
            db.collection("administrators")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AdministratorAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.email }
                }
                .catch {
                    // Un administrateur ordinaire n'a pas le droit de lister ce collection.
                    emit(emptyList())
                }
        )
    }

    fun observeAllSchools(): Flow<List<School>> = flow {
        requireSignedIn()
        emitAll(
            db.collection("schools")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        School::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.code }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeAllStudents(): Flow<List<Student>> = flow {
        requireSignedIn()
        emitAll(
            db.collection("students")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeAllAssessments(): Flow<List<Assessment>> = flow {
        requireSignedIn()
        emitAll(
            db.collection("assessments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Assessment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeAllSettings(): Flow<List<ThresholdSettings>> = flow {
        requireSignedIn()
        emitAll(
            db.collection("settings")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ThresholdSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun createAdministrator(
        email: String,
        fullName: String,
        createdBy: String
    ): Result<String> {
        val normalized = email.trim().lowercase()
        val cleanName = fullName.trim().replace(Regex("\\s+"), " ").take(80)
        if (normalized.isBlank() || !normalized.contains("@") || !normalized.contains(".")) {
            return Result.failure(IllegalArgumentException("Adresse e-mail Google invalide."))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Nom complet de l'administrateur obligatoire."))
        }
        val docPath = "administrators/$normalized"
        return try {
            requireUserId(createdBy)
            db.collection("administrators").document(normalized)
                .set(
                    mapOf(
                        "email" to normalized,
                        "fullName" to cleanName,
                        "uid" to "",
                        "active" to true,
                        "createdBy" to createdBy,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setAdministratorActive(
        email: String,
        active: Boolean,
        actingUserId: String
    ): Result<Unit> {
        val normalized = email.trim().lowercase()
        val docPath = "administrators/$normalized"
        return try {
            requireUserId(actingUserId)
            db.collection("administrators").document(normalized)
                .update("active", active, "updatedAt" to FieldValue.serverTimestamp())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun claimAdministratorAccount(email: String, uid: String): Result<Unit> {
        val normalized = email.trim().lowercase()
        val docPath = "administrators/$normalized"
        return try {
            requireUserId(uid)
            db.collection("administrators").document(normalized)
                .update("uid", uid, "updatedAt" to FieldValue.serverTimestamp())
                .await()
            linkAdministratorDocuments(normalized, uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /** Remplace l'UID provisoire (e-mail) par l'UID Firebase réel sur toutes les données de l'administrateur. */
    private suspend fun linkAdministratorDocuments(email: String, uid: String) {
        val collections = listOf("schools", "operators", "students", "assessments")
        collections.forEach { name ->
            val snapshot = db.collection(name).whereEqualTo("adminUid", email).get().await()
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    batch.update(doc.reference, mapOf("adminUid" to uid, "updatedAt" to FieldValue.serverTimestamp()))
                }
                batch.commit().await()
            }
        }
    }

    // --- ADMIN SCHOOL MUTATIONS ---

    suspend fun saveSchool(
        userId: String,
        schoolId: String?,
        code: String,
        name: String,
        className: String,
        zone: String,
        teacherName: String,
        boysEnrolled: Int,
        girlsEnrolled: Int,
        newAccessCode: String? = null,
        ownerUid: String? = null,
        ownerEmail: String = ""
    ): Result<GeneratedSchoolCode?> {
        val cleanCode = code.trim().take(24)
        val cleanName = name.trim().take(100)
        val cleanClass = className.trim().take(40)
        val cleanZone = zone.trim().take(80)
        val cleanTeacher = teacherName.trim().take(80)
        val safeBoys = boysEnrolled.coerceIn(0, 300)
        val safeGirls = girlsEnrolled.coerceIn(0, 300)

        if (cleanCode.isBlank() || cleanName.isBlank() || cleanClass.isBlank()) {
            return Result.failure(IllegalArgumentException("Code, nom d'école et classe CM2 obligatoires."))
        }

        val isUpdate = !schoolId.isNullOrBlank()
        val effectiveCodeToHash: String? = when {
            !newAccessCode.isNullOrBlank() -> {
                val normalized = AccessCodeSecurity.normalizeCode(newAccessCode)
                if (!AccessCodeSecurity.isValidFormat(normalized)) {
                    return Result.failure(
                        IllegalArgumentException("Le code d'accès doit contenir 4 à 16 caractères alphanumériques.")
                    )
                }
                normalized
            }
            !isUpdate -> AccessCodeSecurity.generateAccessCode()
            else -> null
        }

        val hashed = effectiveCodeToHash?.let { raw ->
            withContext(Dispatchers.Default) { AccessCodeSecurity.hashAccessCode(raw) }
        }

        var docPath = "schools/${schoolId ?: "new"}"
        return try {
            val uid = requireUserId(userId)
            val effectiveOwnerUid = ownerUid?.takeIf { it.isNotBlank() } ?: uid
            val effectiveOwnerEmail = ownerEmail.trim().lowercase().ifBlank { currentLowerEmail() }
            val col = db.collection("schools")
            if (isUpdate) {
                val docRef = col.document(schoolId!!)
                docPath = docRef.path
                val payload = mutableMapOf<String, Any>(
                    "code" to cleanCode,
                    "name" to cleanName,
                    "className" to cleanClass,
                    "zone" to cleanZone,
                    "teacherName" to cleanTeacher,
                    "boysEnrolled" to safeBoys,
                    "girlsEnrolled" to safeGirls,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (hashed != null) {
                    payload["accessCodeHash"] = hashed.hashHex
                    payload["accessCodeSalt"] = hashed.saltHex
                }
                docRef.update(payload).await()
            } else {
                val docRef = col.document()
                docPath = docRef.path
                val payload = mutableMapOf<String, Any>(
                    "adminUid" to effectiveOwnerUid,
                    "adminEmail" to effectiveOwnerEmail,
                    "code" to cleanCode,
                    "name" to cleanName,
                    "className" to cleanClass,
                    "zone" to cleanZone,
                    "teacherName" to cleanTeacher,
                    "boysEnrolled" to safeBoys,
                    "girlsEnrolled" to safeGirls,
                    "operatorEmails" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (hashed != null) {
                    payload["accessCodeHash"] = hashed.hashHex
                    payload["accessCodeSalt"] = hashed.saltHex
                }
                docRef.set(payload).await()
            }
            val generated = effectiveCodeToHash?.let {
                GeneratedSchoolCode(
                    schoolCode = cleanCode,
                    schoolName = cleanName,
                    accessCode = it
                )
            }
            Result.success(generated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSchoolAccessCode(
        userId: String,
        school: School,
        newAccessCode: String
    ): Result<GeneratedSchoolCode> {
        val normalized = AccessCodeSecurity.normalizeCode(newAccessCode)
        if (!AccessCodeSecurity.isValidFormat(normalized)) {
            return Result.failure(
                IllegalArgumentException("Le code d'accès doit contenir 4 à 16 caractères alphanumériques.")
            )
        }
        val docPath = "schools/${school.id}"
        return try {
            requireUserId(userId)
            require(school.id.isNotBlank()) { "Identifiant d'école invalide." }
            val hashed = withContext(Dispatchers.Default) {
                AccessCodeSecurity.hashAccessCode(normalized)
            }
            db.collection("schools").document(school.id)
                .update(
                    mapOf(
                        "accessCodeHash" to hashed.hashHex,
                        "accessCodeSalt" to hashed.saltHex,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(
                GeneratedSchoolCode(
                    schoolCode = school.code,
                    schoolName = school.name,
                    accessCode = normalized
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSchool(userId: String, schoolId: String): Result<Unit> {
        val docPath = "schools/$schoolId"
        return try {
            requireUserId(userId)
            require(schoolId.isNotBlank()) { "Identifiant d'école invalide." }
            db.collection("schools").document(schoolId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun bootstrapFortySchools(
        userId: String,
        existingSchools: List<School>
    ): Result<List<GeneratedSchoolCode>> {
        val path = "schools"
        return try {
            val uid = requireUserId(userId)
            val existingCodes = existingSchools.map { it.code.uppercase() }.toSet()
            val zones = listOf("Zone Nord", "Zone Sud", "Zone Est", "Zone Ouest")
            val generatedList = mutableListOf<GeneratedSchoolCode>()

            val prepared = withContext(Dispatchers.Default) {
                val list = mutableListOf<Triple<Int, String, HashedAccessCode>>()
                for (i in 1..40) {
                    val num = i.toString().padStart(2, '0')
                    val code = "EC-$num"
                    if (code !in existingCodes) {
                        val rawPin = AccessCodeSecurity.generateAccessCode()
                        val hashed = AccessCodeSecurity.hashAccessCode(rawPin)
                        list.add(Triple(i, rawPin, hashed))
                    }
                }
                list
            }

            if (prepared.isNotEmpty()) {
                val batch = db.batch()
                val col = db.collection("schools")
                val safeUidPrefix = uid.filter { it.isLetterOrDigit() }.take(12).ifBlank { "adm" }
                prepared.forEach { (i, rawPin, hashed) ->
                    val num = i.toString().padStart(2, '0')
                    val code = "EC-$num"
                    val name = "École Élémentaire $num"
                    val docRef = col.document("${safeUidPrefix}_ecole_$num")
                    val zone = zones[(i - 1) / 10]
                    batch.set(
                        docRef,
                        mapOf(
                            "adminUid" to uid,
                            "adminEmail" to currentLowerEmail(),
                            "code" to code,
                            "name" to name,
                            "className" to "CM2 A",
                            "zone" to zone,
                            "teacherName" to "",
                            "boysEnrolled" to 0,
                            "girlsEnrolled" to 0,
                            "operatorEmails" to emptyList<String>(),
                            "accessCodeHash" to hashed.hashHex,
                            "accessCodeSalt" to hashed.saltHex,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    generatedList.add(
                        GeneratedSchoolCode(
                            schoolCode = code,
                            schoolName = name,
                            accessCode = rawPin
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(generatedList)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun generateMissingAccessCodes(
        userId: String,
        schools: List<School>
    ): Result<List<GeneratedSchoolCode>> {
        val path = "schools"
        return try {
            requireUserId(userId)
            val unprotected = schools.filter { !it.hasAccessCode && it.id.isNotBlank() }
            if (unprotected.isEmpty()) return Result.success(emptyList())

            val prepared = withContext(Dispatchers.Default) {
                unprotected.map { school ->
                    val rawPin = AccessCodeSecurity.generateAccessCode()
                    val hashed = AccessCodeSecurity.hashAccessCode(rawPin)
                    Triple(school, rawPin, hashed)
                }
            }

            val batch = db.batch()
            val col = db.collection("schools")
            val generated = mutableListOf<GeneratedSchoolCode>()
            prepared.forEach { (school, rawPin, hashed) ->
                batch.update(
                    col.document(school.id),
                    mapOf(
                        "accessCodeHash" to hashed.hashHex,
                        "accessCodeSalt" to hashed.saltHex,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                generated.add(
                    GeneratedSchoolCode(
                        schoolCode = school.code,
                        schoolName = school.name,
                        accessCode = rawPin
                    )
                )
            }
            batch.commit().await()
            Result.success(generated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- ASSESSMENTS WITH AUTOMATIC PERSISTENT STUDENT REGISTRATION ---

    suspend fun saveAssessment(
        userId: String,
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
        recordedBy: String
    ): Result<Boolean> {
        val cleanStudent = studentName.trim().replace(Regex("\\s+"), " ").take(80)
        val cleanMonth = month.trim().take(30)
        val cleanRecorder = recordedBy.trim().take(80)
        if (cleanStudent.isBlank() || school.id.isBlank() || cleanMonth.isBlank()) {
            return Result.failure(IllegalArgumentException("Élève, école et mois obligatoires."))
        }

        val lcRes = roundHalf(lcRessources.coerceIn(0.0, Baremes.LC_RESSOURCES_MAX))
        val lcComp = roundHalf(lcCompetences.coerceIn(0.0, Baremes.LC_COMPETENCES_MAX))
        val mathRes = roundHalf(mathRessources.coerceIn(0.0, Baremes.MATH_RESSOURCES_MAX))
        val mathComp = roundHalf(mathCompetences.coerceIn(0.0, Baremes.MATH_COMPETENCES_MAX))
        val dmRes = roundHalf(dmRessources.coerceIn(0.0, Baremes.DM_RESSOURCES_MAX))
        val dmComp = roundHalf(dmCompetences.coerceIn(0.0, Baremes.DM_COMPETENCES_MAX))
        val ddRes = roundHalf(ddRessources.coerceIn(0.0, Baremes.DD_RESSOURCES_MAX))
        val ddComp = roundHalf(ddCompetences.coerceIn(0.0, Baremes.DD_COMPETENCES_MAX))

        val total = roundHalf(lcRes + lcComp + mathRes + mathComp + dmRes + dmComp + ddRes + ddComp)
            .coerceIn(0.0, Baremes.TOTAL_MAX)
        val decision = MasteryDecision.compute(total, Baremes.TOTAL_MAX, settings)
        val safeWeek = if (testType == TestType.MENSUEL) 0 else weekNumber.coerceIn(1, 5)

        val isUpdate = !assessmentId.isNullOrBlank()
        var docPath = "assessments/${assessmentId ?: "new"}"
        return try {
            val uid = requireUserId(userId)
            val ownerAdminUid = school.adminUid.ifBlank { uid }
            val ownerAdminEmail = school.adminEmail.ifBlank { currentLowerEmail() }
            val normalizedName = cleanStudent.lowercase()
            val matchedStudent = existingSchoolStudents.firstOrNull {
                (!selectedStudentId.isNullOrBlank() && it.id == selectedStudentId) ||
                    it.normalizedName == normalizedName
            }

            val batch = db.batch()
            val studentIdToLink: String
            val createdNewStudent: Boolean

            if (matchedStudent != null) {
                studentIdToLink = matchedStudent.id
                createdNewStudent = false
            } else {
                val studentRef = db.collection("students").document()
                studentIdToLink = studentRef.id
                createdNewStudent = true
                batch.set(
                    studentRef,
                    mapOf(
                        "adminUid" to ownerAdminUid,
                        "adminEmail" to ownerAdminEmail,
                        "schoolId" to school.id,
                        "schoolCode" to school.code.take(24),
                        "schoolName" to school.name.take(100),
                        "className" to school.className.take(40),
                        "fullName" to cleanStudent,
                        "gender" to gender.name,
                        "matricule" to "",
                        "operatorEmails" to school.operatorEmails.take(20),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val assessmentsCol = db.collection("assessments")
            if (isUpdate) {
                val docRef = assessmentsCol.document(assessmentId!!)
                docPath = docRef.path
                batch.update(
                    docRef,
                    mapOf(
                        "studentId" to studentIdToLink,
                        "studentName" to cleanStudent,
                        "gender" to gender.name,
                        "testType" to testType.name,
                        "month" to cleanMonth,
                        "weekNumber" to safeWeek,
                        "lcRessources" to lcRes,
                        "lcCompetences" to lcComp,
                        "mathRessources" to mathRes,
                        "mathCompetences" to mathComp,
                        "dmRessources" to dmRes,
                        "dmCompetences" to dmComp,
                        "ddRessources" to ddRes,
                        "ddCompetences" to ddComp,
                        "totalScore" to total,
                        "decision" to decision.name,
                        "recordedBy" to cleanRecorder,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val docRef = assessmentsCol.document()
                docPath = docRef.path
                batch.set(
                    docRef,
                    mapOf(
                        "adminUid" to ownerAdminUid,
                        "adminEmail" to ownerAdminEmail,
                        "schoolId" to school.id,
                        "schoolName" to school.name.take(100),
                        "className" to school.className.take(40),
                        "studentId" to studentIdToLink,
                        "studentName" to cleanStudent,
                        "gender" to gender.name,
                        "testType" to testType.name,
                        "month" to cleanMonth,
                        "weekNumber" to safeWeek,
                        "lcRessources" to lcRes,
                        "lcCompetences" to lcComp,
                        "mathRessources" to mathRes,
                        "mathCompetences" to mathComp,
                        "dmRessources" to dmRes,
                        "dmCompetences" to dmComp,
                        "ddRessources" to ddRes,
                        "ddCompetences" to ddComp,
                        "totalScore" to total,
                        "decision" to decision.name,
                        "recordedBy" to cleanRecorder,
                        "operatorEmails" to school.operatorEmails.take(20),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(createdNewStudent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAssessment(userId: String, assessmentId: String): Result<Unit> {
        val docPath = "assessments/$assessmentId"
        return try {
            requireUserId(userId)
            require(assessmentId.isNotBlank()) { "Identifiant d'évaluation invalide." }
            db.collection("assessments").document(assessmentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveSettings(
        userId: String,
        maxMasteryPct: Double,
        minMasteryPct: Double,
        partialMasteryPct: Double,
        academicYear: String,
        districtName: String
    ): Result<Unit> {
        val safeMax = maxMasteryPct.coerceIn(50.0, 100.0)
        val safeMin = minMasteryPct.coerceIn(30.0, safeMax)
        val safePartial = partialMasteryPct.coerceIn(10.0, safeMin)
        val cleanYear = academicYear.trim().ifBlank { "2026-2027" }.take(20)
        val cleanDistrict = districtName.trim().take(100)

        val docPath = "settings/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("settings").document(uid)
            val existingDoc = docRef.get().await()
            if (existingDoc.exists()) {
                docRef.update(
                    mapOf(
                        "maxMasteryPct" to safeMax,
                        "minMasteryPct" to safeMin,
                        "partialMasteryPct" to safePartial,
                        "academicYear" to cleanYear,
                        "districtName" to cleanDistrict,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "adminUid" to uid,
                        "adminEmail" to currentLowerEmail(),
                        "maxMasteryPct" to safeMax,
                        "minMasteryPct" to safeMin,
                        "partialMasteryPct" to safePartial,
                        "academicYear" to cleanYear,
                        "districtName" to cleanDistrict,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun roundHalf(value: Double): Double = (value * 2.0).roundToInt() / 2.0

    private fun logStreamErrorIfSignedIn(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
