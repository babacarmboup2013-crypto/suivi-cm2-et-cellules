package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SessionRole(val label: String) {
    SUPERADMIN("Superadministrateur"),
    ADMIN("Administrateur"),
    OPERATOR("Utilisateur de saisie")
}

enum class Gender(val label: String, val shortLabel: String) {
    GARCON("Garçon", "G"),
    FILLE("Fille", "F");

    companion object {
        fun fromRaw(raw: String?): Gender =
            entries.firstOrNull { it.name == raw } ?: GARCON
    }
}

enum class TestType(val label: String, val shortLabel: String) {
    HEBDOMADAIRE("Test Hebdomadaire", "Hebdo"),
    MENSUEL("Grand Test Mensuel", "Mensuel");

    companion object {
        fun fromRaw(raw: String?): TestType =
            entries.firstOrNull { it.name == raw } ?: HEBDOMADAIRE
    }
}

enum class MasteryDecision(val label: String, val shortLabel: String) {
    MAITRISE_MAXIMALE("Maîtrise Maximale", "M. Max"),
    MAITRISE_MINIMALE("Maîtrise Minimale", "M. Min"),
    A_AMELIORER("À améliorer", "À amél."),
    A_RENFORCER("À renforcer", "À renf.");

    val isAdmitted: Boolean
        get() = this == MAITRISE_MAXIMALE || this == MAITRISE_MINIMALE

    companion object {
        fun fromRaw(raw: String?): MasteryDecision =
            entries.firstOrNull { it.name == raw } ?: A_RENFORCER

        fun compute(
            totalScore: Double,
            maxScore: Double = Baremes.TOTAL_MAX,
            settings: ThresholdSettings = ThresholdSettings()
        ): MasteryDecision {
            val pct = if (maxScore > 0.0) (totalScore / maxScore) * 100.0 else 0.0
            return when {
                pct >= settings.maxMasteryPct -> MAITRISE_MAXIMALE
                pct >= settings.minMasteryPct -> MAITRISE_MINIMALE
                pct >= settings.partialMasteryPct -> A_AMELIORER
                else -> A_RENFORCER
            }
        }
    }
}

enum class DiagnosticStatus(val label: String) {
    POINT_FORT("Point fort"),
    A_AMELIORER("À améliorer"),
    A_RENFORCER("À renforcer")
}

object Baremes {
    const val LC_RESSOURCES_MAX = 40.0
    const val LC_COMPETENCES_MAX = 60.0
    const val LC_TOTAL_MAX = 100.0

    const val MATH_RESSOURCES_MAX = 40.0
    const val MATH_COMPETENCES_MAX = 60.0
    const val MATH_TOTAL_MAX = 100.0

    const val DM_RESSOURCES_MAX = 24.0
    const val DM_COMPETENCES_MAX = 16.0
    const val DM_TOTAL_MAX = 40.0

    const val DD_RESSOURCES_MAX = 24.0
    const val DD_COMPETENCES_MAX = 16.0
    const val DD_TOTAL_MAX = 40.0

    const val ESVS_TOTAL_MAX = 80.0
    const val TOTAL_MAX = 280.0
}

enum class ActivityKey(
    val label: String,
    val shortLabel: String,
    val domainLabel: String,
    val maxScore: Double
) {
    LC_RESSOURCES("LC — Ressources", "LC Res", "Langue & Comm.", Baremes.LC_RESSOURCES_MAX),
    LC_COMPETENCES("LC — Compétences", "LC Comp", "Langue & Comm.", Baremes.LC_COMPETENCES_MAX),
    MATH_RESSOURCES("Maths — Ressources", "Math Res", "Mathématiques", Baremes.MATH_RESSOURCES_MAX),
    MATH_COMPETENCES("Maths — Compétences", "Math Comp", "Mathématiques", Baremes.MATH_COMPETENCES_MAX),
    DM_RESSOURCES("Découv. Monde — Ress.", "DM Res", "ESVS (DM)", Baremes.DM_RESSOURCES_MAX),
    DM_COMPETENCES("Découv. Monde — Comp.", "DM Comp", "ESVS (DM)", Baremes.DM_COMPETENCES_MAX),
    DD_RESSOURCES("Dével. Durable — Ress.", "DD Res", "ESVS (DD)", Baremes.DD_RESSOURCES_MAX),
    DD_COMPETENCES("Dével. Durable — Comp.", "DD Comp", "ESVS (DD)", Baremes.DD_COMPETENCES_MAX);

    fun extractScore(a: Assessment): Double = when (this) {
        LC_RESSOURCES -> a.lcRessources
        LC_COMPETENCES -> a.lcCompetences
        MATH_RESSOURCES -> a.mathRessources
        MATH_COMPETENCES -> a.mathCompetences
        DM_RESSOURCES -> a.dmRessources
        DM_COMPETENCES -> a.dmCompetences
        DD_RESSOURCES -> a.ddRessources
        DD_COMPETENCES -> a.ddCompetences
    }
}

val SCHOOL_MONTHS = listOf(
    "Octobre",
    "Novembre",
    "Décembre",
    "Janvier",
    "Février",
    "Mars",
    "Avril",
    "Mai",
    "Juin"
)

@Keep
@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val code: String = "",
    val name: String = "",
    val className: String = "CM2 A",
    val zone: String = "",
    val teacherName: String = "",
    val boysEnrolled: Long = 0L,
    val girlsEnrolled: Long = 0L,
    val operatorEmails: List<String> = emptyList(),
    val accessCodeHash: String = "",
    val accessCodeSalt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val totalEnrolled: Long get() = boysEnrolled + girlsEnrolled
    val hasAccessCode: Boolean get() = accessCodeHash.isNotBlank() && accessCodeSalt.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class DataEntryOperator(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val fullName: String = "",
    val email: String = "",
    val schoolId: String = "",
    val schoolCode: String = "",
    val schoolName: String = "",
    val role: String = ROLE_OPERATOR,
    val active: Boolean = true,
    val pinHash: String = "",
    val pinSalt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val hasPersonalPin: Boolean get() = pinHash.isNotBlank() && pinSalt.isNotBlank()
    val hasGoogleEmail: Boolean get() = email.isNotBlank()

    companion object {
        const val ROLE_OPERATOR = "OPERATEUR_SAISIE"
    }
}

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val schoolId: String = "",
    val schoolCode: String = "",
    val schoolName: String = "",
    val className: String = "CM2 A",
    val fullName: String = "",
    val gender: String = Gender.GARCON.name,
    val matricule: String = "",
    val operatorEmails: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val genderEnum: Gender get() = Gender.fromRaw(gender)
    val normalizedName: String get() = fullName.trim().lowercase().replace(Regex("\\s+"), " ")
}

@Keep
@IgnoreExtraProperties
data class Assessment(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val className: String = "CM2 A",
    val studentId: String = "",
    val studentName: String = "",
    val gender: String = Gender.GARCON.name,
    val testType: String = TestType.HEBDOMADAIRE.name,
    val month: String = "Novembre",
    val weekNumber: Long = 1L,
    val lcRessources: Double = 0.0,
    val lcCompetences: Double = 0.0,
    val mathRessources: Double = 0.0,
    val mathCompetences: Double = 0.0,
    val dmRessources: Double = 0.0,
    val dmCompetences: Double = 0.0,
    val ddRessources: Double = 0.0,
    val ddCompetences: Double = 0.0,
    val totalScore: Double = 0.0,
    val decision: String = MasteryDecision.A_RENFORCER.name,
    val recordedBy: String = "",
    val operatorEmails: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val genderEnum: Gender get() = Gender.fromRaw(gender)
    val testTypeEnum: TestType get() = TestType.fromRaw(testType)
    val decisionEnum: MasteryDecision get() = MasteryDecision.fromRaw(decision)

    val lcTotal: Double get() = lcRessources + lcCompetences
    val mathTotal: Double get() = mathRessources + mathCompetences
    val dmTotal: Double get() = dmRessources + dmCompetences
    val ddTotal: Double get() = ddRessources + ddCompetences
    val esvsTotal: Double get() = dmTotal + ddTotal

    val periodLabel: String
        get() = if (testTypeEnum == TestType.MENSUEL) {
            "Grand Test • $month"
        } else {
            "Sem. $weekNumber • $month"
        }
}

@Keep
@IgnoreExtraProperties
data class ThresholdSettings(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val maxMasteryPct: Double = 75.0,
    val minMasteryPct: Double = 50.0,
    val partialMasteryPct: Double = 35.0,
    val academicYear: String = "2026-2027",
    val districtName: String = "Circonscription CM2 — 40 Écoles",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun pointThreshold(maxScore: Double, pct: Double): Double = (maxScore * pct) / 100.0
}

/**
 * Compte administrateur créé par le superadministrateur.
 * L'identifiant du document est l'e-mail Google normalisé (minuscules), ce qui
 * permet aux règles Firestore de retrouver le compte sans requête supplémentaire.
 * Le champ [uid] est renseigné automatiquement lors de la première connexion
 * de l'administrateur concerné.
 */
@Keep
@IgnoreExtraProperties
data class AdministratorAccount(
    @DocumentId val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val uid: String = "",
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isClaimed: Boolean get() = uid.isNotBlank()
}

/** Compte superadministrateur, identifié par l'UID Firebase. */
@Keep
@IgnoreExtraProperties
data class SuperAdminAccount(
    @DocumentId val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
