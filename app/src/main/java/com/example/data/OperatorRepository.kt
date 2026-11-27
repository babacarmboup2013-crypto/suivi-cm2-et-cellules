package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * Manages Admin-created Data Entry Operators (Utilisateurs de saisie) and Persistent School Students (Élèves).
 * Synchronizes denormalized `operatorEmails` onto school, student, and assessment documents for 1:1 rule enforcement.
 */
class OperatorRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Connexion Google requise.")
        if (uid != expectedUserId) {
            throw IllegalStateException("Session utilisateur modifiée ; opération annulée.")
        }
        return uid
    }

    /** Vérifie seulement la session ; les règles Firestore vérifient le rôle de l'appelant. */
    private fun requireSignedIn(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Connexion Google requise.")

    private fun currentLowerEmail(): String =
        auth.currentUser?.email?.trim()?.lowercase().orEmpty()

    fun observeAllOperators(): Flow<List<DataEntryOperator>> = flow {
        requireSignedIn()
        emitAll(
            db.collection("operators")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DataEntryOperator::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<DataEntryOperator> { it.schoolCode }.thenBy { it.fullName.lowercase() })
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun saveOperator(
        adminUid: String,
        operatorId: String?,
        fullName: String,
        email: String,
        school: School,
        active: Boolean,
        newPin: String?,
        currentOperators: List<DataEntryOperator>,
        allStudents: List<Student>,
        allAssessments: List<Assessment>
    ): Result<GeneratedSchoolCode?> {
        val cleanName = fullName.trim().replace(Regex("\\s+"), " ").take(80)
        val cleanEmail = email.trim().lowercase().take(120)
        if (cleanName.isBlank() || school.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Nom complet et école assignée obligatoires."))
        }
        if (cleanEmail.isNotBlank() && (!cleanEmail.contains("@") || !cleanEmail.contains("."))) {
            return Result.failure(IllegalArgumentException("Adresse e-mail Google invalide."))
        }

        val isUpdate = !operatorId.isNullOrBlank()
        val effectivePin: String? = when {
            !newPin.isNullOrBlank() -> {
                val normalized = AccessCodeSecurity.normalizeCode(newPin)
                if (!AccessCodeSecurity.isValidFormat(normalized)) {
                    return Result.failure(
                        IllegalArgumentException("Le code PIN opérateur doit contenir 4 à 16 caractères.")
                    )
                }
                normalized
            }
            !isUpdate -> AccessCodeSecurity.generateAccessCode()
            else -> null
        }

        val hashedPin = effectivePin?.let { raw ->
            withContext(Dispatchers.Default) { AccessCodeSecurity.hashAccessCode(raw) }
        }

        var docPath = "operators/${operatorId ?: "new"}"
        return try {
            val uid = requireSignedIn()
            val ownerUid = school.adminUid.ifBlank { adminUid.ifBlank { uid } }
            val ownerEmail = school.adminEmail.ifBlank { currentLowerEmail() }
            val col = db.collection("operators")
            val oldOperator = operatorId?.let { id -> currentOperators.firstOrNull { it.id == id } }
            val resolvedId: String

            if (isUpdate) {
                val docRef = col.document(operatorId!!)
                docPath = docRef.path
                resolvedId = docRef.id
                val payload = mutableMapOf<String, Any>(
                    "fullName" to cleanName,
                    "email" to cleanEmail,
                    "schoolId" to school.id,
                    "schoolCode" to school.code.take(24),
                    "schoolName" to school.name.take(100),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (hashedPin != null) {
                    payload["pinHash"] = hashedPin.hashHex
                    payload["pinSalt"] = hashedPin.saltHex
                }
                docRef.update(payload).await()
            } else {
                val docRef = col.document()
                docPath = docRef.path
                resolvedId = docRef.id
                docRef.set(
                    mapOf(
                        "adminUid" to ownerUid,
                        "adminEmail" to ownerEmail,
                        "fullName" to cleanName,
                        "email" to cleanEmail,
                        "schoolId" to school.id,
                        "schoolCode" to school.code.take(24),
                        "schoolName" to school.name.take(100),
                        "role" to DataEntryOperator.ROLE_OPERATOR,
                        "active" to active,
                        "pinHash" to (hashedPin?.hashHex ?: ""),
                        "pinSalt" to (hashedPin?.saltHex ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Recompute & sync operatorEmails for affected school(s)
            val updatedOperatorModel = DataEntryOperator(
                id = resolvedId,
                adminUid = ownerUid,
                adminEmail = ownerEmail,
                fullName = cleanName,
                email = cleanEmail,
                schoolId = school.id,
                schoolCode = school.code,
                schoolName = school.name,
                active = active
            )
            val mergedOperators = currentOperators.filterNot { it.id == resolvedId } + updatedOperatorModel
            val affectedSchoolIds = buildSet {
                add(school.id)
                oldOperator?.schoolId?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            affectedSchoolIds.forEach { targetSchoolId ->
                syncOperatorEmailsForSchool(
                    schoolId = targetSchoolId,
                    operators = mergedOperators,
                    students = allStudents,
                    assessments = allAssessments
                )
            }

            val generated = effectivePin?.let { pin ->
                GeneratedSchoolCode(
                    schoolCode = "${school.code} • Opérateur",
                    schoolName = "$cleanName (${school.name})",
                    accessCode = pin
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

    suspend fun toggleOperatorActive(
        adminUid: String,
        operator: DataEntryOperator,
        newActive: Boolean,
        currentOperators: List<DataEntryOperator>,
        allStudents: List<Student>,
        allAssessments: List<Assessment>
    ): Result<Unit> {
        val docPath = "operators/${operator.id}"
        return try {
            requireSignedIn()
            db.collection("operators").document(operator.id)
                .update(
                    mapOf(
                        "active" to newActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            val merged = currentOperators.map {
                if (it.id == operator.id) it.copy(active = newActive) else it
            }
            syncOperatorEmailsForSchool(
                schoolId = operator.schoolId,
                operators = merged,
                students = allStudents,
                assessments = allAssessments
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteOperator(
        adminUid: String,
        operator: DataEntryOperator,
        currentOperators: List<DataEntryOperator>,
        allStudents: List<Student>,
        allAssessments: List<Assessment>
    ): Result<Unit> {
        val docPath = "operators/${operator.id}"
        return try {
            requireSignedIn()
            db.collection("operators").document(operator.id).delete().await()
            val remaining = currentOperators.filterNot { it.id == operator.id }
            syncOperatorEmailsForSchool(
                schoolId = operator.schoolId,
                operators = remaining,
                students = allStudents,
                assessments = allAssessments
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveStudent(
        userId: String,
        studentId: String?,
        school: School,
        fullName: String,
        gender: Gender,
        matricule: String
    ): Result<Unit> {
        val cleanName = fullName.trim().replace(Regex("\\s+"), " ").take(80)
        val cleanMatricule = matricule.trim().take(30)
        if (cleanName.isBlank() || school.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Nom de l'élève et école obligatoires."))
        }

        val isUpdate = !studentId.isNullOrBlank()
        var docPath = "students/${studentId ?: "new"}"
        return try {
            val uid = requireSignedIn()
            val ownerAdminUid = school.adminUid.ifBlank { uid }
            val ownerAdminEmail = school.adminEmail.ifBlank { currentLowerEmail() }
            val col = db.collection("students")
            if (isUpdate) {
                val docRef = col.document(studentId!!)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "fullName" to cleanName,
                        "gender" to gender.name,
                        "matricule" to cleanMatricule,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val docRef = col.document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "adminUid" to ownerAdminUid,
                        "adminEmail" to ownerAdminEmail,
                        "schoolId" to school.id,
                        "schoolCode" to school.code.take(24),
                        "schoolName" to school.name.take(100),
                        "className" to school.className.take(40),
                        "fullName" to cleanName,
                        "gender" to gender.name,
                        "matricule" to cleanMatricule,
                        "operatorEmails" to school.operatorEmails.take(20),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> {
        val docPath = "students/$studentId"
        return try {
            requireSignedIn()
            require(studentId.isNotBlank()) { "Identifiant élève invalide." }
            db.collection("students").document(studentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun syncOperatorEmailsForSchool(
        schoolId: String,
        operators: List<DataEntryOperator>,
        students: List<Student>,
        assessments: List<Assessment>
    ) {
        if (schoolId.isBlank()) return
        val activeEmails = operators
            .filter { it.schoolId == schoolId && it.active && it.email.isNotBlank() }
            .map { it.email.trim().lowercase() }
            .distinct()
            .take(20)

        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
        val updateMap = mapOf(
            "operatorEmails" to activeEmails,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ops.add(db.collection("schools").document(schoolId) to updateMap)
        students.filter { it.schoolId == schoolId && it.id.isNotBlank() }.forEach { stu ->
            ops.add(db.collection("students").document(stu.id) to updateMap)
        }
        assessments.filter { it.schoolId == schoolId && it.id.isNotBlank() }.forEach { eval ->
            ops.add(db.collection("assessments").document(eval.id) to updateMap)
        }

        // Firestore batches support up to 500 writes; chunk safely at 400
        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, map) -> batch.update(ref, map) }
            batch.commit().await()
        }
    }
}
