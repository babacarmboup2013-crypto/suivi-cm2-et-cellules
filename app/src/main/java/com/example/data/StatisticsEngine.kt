package com.example.data

data class ActivityStat(
    val key: ActivityKey,
    val count: Int,
    val avgScore: Double,
    val avgPct: Double,
    val masteryRatePct: Double,
    val boysCount: Int,
    val boysMasteryPct: Double,
    val boysAvgScore: Double,
    val girlsCount: Int,
    val girlsMasteryPct: Double,
    val girlsAvgScore: Double,
    val minPointThreshold: Double,
    val maxPointThreshold: Double,
    val status: DiagnosticStatus
)

data class DomainStat(
    val label: String,
    val maxScore: Double,
    val avgScore: Double,
    val masteryRatePct: Double,
    val boysMasteryPct: Double,
    val girlsMasteryPct: Double,
    val status: DiagnosticStatus
)

data class GenderSummary(
    val gender: Gender,
    val count: Int,
    val avgTotal: Double,
    val masteryRatePct: Double,
    val maxMasteryCount: Int,
    val minMasteryCount: Int,
    val toImproveCount: Int,
    val toReinforceCount: Int
)

data class ClassStat(
    val school: School,
    val evaluatedCount: Int,
    val boysEvaluated: Int,
    val girlsEvaluated: Int,
    val avgTotal: Double,
    val masteryRatePct: Double,
    val boysMasteryPct: Double,
    val girlsMasteryPct: Double,
    val maxCount: Int,
    val minCount: Int,
    val improveCount: Int,
    val reinforceCount: Int,
    val strongestActivity: ActivityKey?,
    val weakestActivity: ActivityKey?,
    val status: DiagnosticStatus
)

data class PeriodTrendPoint(
    val label: String,
    val boysMasteryPct: Double,
    val girlsMasteryPct: Double,
    val overallMasteryPct: Double,
    val count: Int
)

data class GlobalSynthesis(
    val totalSchoolsConfigured: Int,
    val activeSchoolsCount: Int,
    val totalAssessments: Int,
    val avgTotalScore: Double,
    val overallMasteryPct: Double,
    val boysSummary: GenderSummary,
    val girlsSummary: GenderSummary,
    val activityStats: List<ActivityStat>,
    val domainStats: List<DomainStat>,
    val classStats: List<ClassStat>,
    val weeklyTrend: List<PeriodTrendPoint>,
    val monthlyTrend: List<PeriodTrendPoint>,
    val strengths: List<ActivityStat>,
    val toImprove: List<ActivityStat>,
    val toReinforce: List<ActivityStat>
)

object StatisticsEngine {

    fun classifyMasteryRate(ratePct: Double, settings: ThresholdSettings): DiagnosticStatus = when {
        ratePct >= settings.maxMasteryPct -> DiagnosticStatus.POINT_FORT
        ratePct >= settings.minMasteryPct -> DiagnosticStatus.A_AMELIORER
        else -> DiagnosticStatus.A_RENFORCER
    }

    fun compute(
        schools: List<School>,
        filteredAssessments: List<Assessment>,
        allAssessmentsForTrends: List<Assessment>,
        settings: ThresholdSettings
    ): GlobalSynthesis {
        val totalCount = filteredAssessments.size
        val avgTotal = if (totalCount > 0) filteredAssessments.map { it.totalScore }.average() else 0.0
        val admittedCount = filteredAssessments.count {
            MasteryDecision.compute(it.totalScore, Baremes.TOTAL_MAX, settings).isAdmitted
        }
        val overallMasteryPct = if (totalCount > 0) (admittedCount * 100.0) / totalCount else 0.0

        val boysSummary = computeGenderSummary(Gender.GARCON, filteredAssessments, settings)
        val girlsSummary = computeGenderSummary(Gender.FILLE, filteredAssessments, settings)

        val activityStats = ActivityKey.entries.map { key ->
            computeActivityStat(key, filteredAssessments, settings)
        }

        val domainStats = listOf(
            computeDomainStat("Langue & Communication", Baremes.LC_TOTAL_MAX, filteredAssessments, settings) { it.lcTotal },
            computeDomainStat("Mathématiques", Baremes.MATH_TOTAL_MAX, filteredAssessments, settings) { it.mathTotal },
            computeDomainStat("ESVS (Global)", Baremes.ESVS_TOTAL_MAX, filteredAssessments, settings) { it.esvsTotal },
            computeDomainStat("• Découverte du Monde", Baremes.DM_TOTAL_MAX, filteredAssessments, settings) { it.dmTotal },
            computeDomainStat("• Développement Durable", Baremes.DD_TOTAL_MAX, filteredAssessments, settings) { it.ddTotal }
        )

        val assessmentsBySchool = filteredAssessments.groupBy { it.schoolId }
        val classStats = schools.map { school ->
            val list = assessmentsBySchool[school.id].orEmpty()
            computeClassStat(school, list, settings)
        }.sortedWith(
            compareByDescending<ClassStat> { it.evaluatedCount > 0 }
                .thenByDescending { it.masteryRatePct }
                .thenBy { it.school.code }
        )

        val weeklyTrend = (1L..4L).map { week ->
            val items = allAssessmentsForTrends.filter {
                it.testTypeEnum == TestType.HEBDOMADAIRE && it.weekNumber == week
            }
            computeTrendPoint("S$week", items, settings)
        }

        val monthlyTrend = SCHOOL_MONTHS.mapNotNull { month ->
            val items = allAssessmentsForTrends.filter {
                it.testTypeEnum == TestType.MENSUEL && it.month == month
            }
            if (items.isEmpty()) null else computeTrendPoint(month.take(3), items, settings)
        }

        val activeActivities = if (totalCount > 0) activityStats else emptyList()
        val strengths = activeActivities.filter { it.status == DiagnosticStatus.POINT_FORT }
            .sortedByDescending { it.masteryRatePct }
        val toImprove = activeActivities.filter { it.status == DiagnosticStatus.A_AMELIORER }
            .sortedBy { it.masteryRatePct }
        val toReinforce = activeActivities.filter { it.status == DiagnosticStatus.A_RENFORCER }
            .sortedBy { it.masteryRatePct }

        return GlobalSynthesis(
            totalSchoolsConfigured = schools.size,
            activeSchoolsCount = assessmentsBySchool.keys.size,
            totalAssessments = totalCount,
            avgTotalScore = avgTotal,
            overallMasteryPct = overallMasteryPct,
            boysSummary = boysSummary,
            girlsSummary = girlsSummary,
            activityStats = activityStats,
            domainStats = domainStats,
            classStats = classStats,
            weeklyTrend = weeklyTrend,
            monthlyTrend = monthlyTrend,
            strengths = strengths,
            toImprove = toImprove,
            toReinforce = toReinforce
        )
    }

    private fun computeGenderSummary(
        gender: Gender,
        assessments: List<Assessment>,
        settings: ThresholdSettings
    ): GenderSummary {
        val items = assessments.filter { it.genderEnum == gender }
        val count = items.size
        val avg = if (count > 0) items.map { it.totalScore }.average() else 0.0
        var maxCount = 0
        var minCount = 0
        var improveCount = 0
        var reinforceCount = 0
        items.forEach { a ->
            when (MasteryDecision.compute(a.totalScore, Baremes.TOTAL_MAX, settings)) {
                MasteryDecision.MAITRISE_MAXIMALE -> maxCount++
                MasteryDecision.MAITRISE_MINIMALE -> minCount++
                MasteryDecision.A_AMELIORER -> improveCount++
                MasteryDecision.A_RENFORCER -> reinforceCount++
            }
        }
        val admitted = maxCount + minCount
        val masteryPct = if (count > 0) (admitted * 100.0) / count else 0.0
        return GenderSummary(
            gender = gender,
            count = count,
            avgTotal = avg,
            masteryRatePct = masteryPct,
            maxMasteryCount = maxCount,
            minMasteryCount = minCount,
            toImproveCount = improveCount,
            toReinforceCount = reinforceCount
        )
    }

    private fun computeActivityStat(
        key: ActivityKey,
        assessments: List<Assessment>,
        settings: ThresholdSettings
    ): ActivityStat {
        val minThreshold = settings.pointThreshold(key.maxScore, settings.minMasteryPct)
        val maxThreshold = settings.pointThreshold(key.maxScore, settings.maxMasteryPct)
        val count = assessments.size
        val avgScore = if (count > 0) assessments.map { key.extractScore(it) }.average() else 0.0
        val avgPct = if (key.maxScore > 0.0) (avgScore / key.maxScore) * 100.0 else 0.0
        val mastered = assessments.count { key.extractScore(it) >= minThreshold }
        val masteryRate = if (count > 0) (mastered * 100.0) / count else 0.0

        val boys = assessments.filter { it.genderEnum == Gender.GARCON }
        val boysAvg = if (boys.isNotEmpty()) boys.map { key.extractScore(it) }.average() else 0.0
        val boysRate = if (boys.isNotEmpty()) (boys.count { key.extractScore(it) >= minThreshold } * 100.0) / boys.size else 0.0

        val girls = assessments.filter { it.genderEnum == Gender.FILLE }
        val girlsAvg = if (girls.isNotEmpty()) girls.map { key.extractScore(it) }.average() else 0.0
        val girlsRate = if (girls.isNotEmpty()) (girls.count { key.extractScore(it) >= minThreshold } * 100.0) / girls.size else 0.0

        return ActivityStat(
            key = key,
            count = count,
            avgScore = avgScore,
            avgPct = avgPct,
            masteryRatePct = masteryRate,
            boysCount = boys.size,
            boysMasteryPct = boysRate,
            boysAvgScore = boysAvg,
            girlsCount = girls.size,
            girlsMasteryPct = girlsRate,
            girlsAvgScore = girlsAvg,
            minPointThreshold = minThreshold,
            maxPointThreshold = maxThreshold,
            status = classifyMasteryRate(masteryRate, settings)
        )
    }

    private fun computeDomainStat(
        label: String,
        maxScore: Double,
        assessments: List<Assessment>,
        settings: ThresholdSettings,
        selector: (Assessment) -> Double
    ): DomainStat {
        val minThreshold = settings.pointThreshold(maxScore, settings.minMasteryPct)
        val count = assessments.size
        val avg = if (count > 0) assessments.map(selector).average() else 0.0
        val rate = if (count > 0) (assessments.count { selector(it) >= minThreshold } * 100.0) / count else 0.0
        val boys = assessments.filter { it.genderEnum == Gender.GARCON }
        val boysRate = if (boys.isNotEmpty()) (boys.count { selector(it) >= minThreshold } * 100.0) / boys.size else 0.0
        val girls = assessments.filter { it.genderEnum == Gender.FILLE }
        val girlsRate = if (girls.isNotEmpty()) (girls.count { selector(it) >= minThreshold } * 100.0) / girls.size else 0.0
        return DomainStat(
            label = label,
            maxScore = maxScore,
            avgScore = avg,
            masteryRatePct = rate,
            boysMasteryPct = boysRate,
            girlsMasteryPct = girlsRate,
            status = classifyMasteryRate(rate, settings)
        )
    }

    private fun computeClassStat(
        school: School,
        assessments: List<Assessment>,
        settings: ThresholdSettings
    ): ClassStat {
        val count = assessments.size
        val boys = assessments.filter { it.genderEnum == Gender.GARCON }
        val girls = assessments.filter { it.genderEnum == Gender.FILLE }
        val avgTotal = if (count > 0) assessments.map { it.totalScore }.average() else 0.0
        var maxCount = 0
        var minCount = 0
        var improveCount = 0
        var reinforceCount = 0
        assessments.forEach { a ->
            when (MasteryDecision.compute(a.totalScore, Baremes.TOTAL_MAX, settings)) {
                MasteryDecision.MAITRISE_MAXIMALE -> maxCount++
                MasteryDecision.MAITRISE_MINIMALE -> minCount++
                MasteryDecision.A_AMELIORER -> improveCount++
                MasteryDecision.A_RENFORCER -> reinforceCount++
            }
        }
        val admitted = maxCount + minCount
        val rate = if (count > 0) (admitted * 100.0) / count else 0.0
        val boysRate = if (boys.isNotEmpty()) {
            (boys.count { MasteryDecision.compute(it.totalScore, Baremes.TOTAL_MAX, settings).isAdmitted } * 100.0) / boys.size
        } else 0.0
        val girlsRate = if (girls.isNotEmpty()) {
            (girls.count { MasteryDecision.compute(it.totalScore, Baremes.TOTAL_MAX, settings).isAdmitted } * 100.0) / girls.size
        } else 0.0

        val actRates = if (count > 0) {
            ActivityKey.entries.associateWith { key ->
                val threshold = settings.pointThreshold(key.maxScore, settings.minMasteryPct)
                (assessments.count { key.extractScore(it) >= threshold } * 100.0) / count
            }
        } else emptyMap()

        val strongest = actRates.maxByOrNull { it.value }?.key
        val weakest = actRates.minByOrNull { it.value }?.key

        return ClassStat(
            school = school,
            evaluatedCount = count,
            boysEvaluated = boys.size,
            girlsEvaluated = girls.size,
            avgTotal = avgTotal,
            masteryRatePct = rate,
            boysMasteryPct = boysRate,
            girlsMasteryPct = girlsRate,
            maxCount = maxCount,
            minCount = minCount,
            improveCount = improveCount,
            reinforceCount = reinforceCount,
            strongestActivity = strongest,
            weakestActivity = weakest,
            status = classifyMasteryRate(rate, settings)
        )
    }

    private fun computeTrendPoint(
        label: String,
        assessments: List<Assessment>,
        settings: ThresholdSettings
    ): PeriodTrendPoint {
        val count = assessments.size
        val overall = if (count > 0) {
            (assessments.count { MasteryDecision.compute(it.totalScore, Baremes.TOTAL_MAX, settings).isAdmitted } * 100.0) / count
        } else 0.0
        val boys = assessments.filter { it.genderEnum == Gender.GARCON }
        val boysRate = if (boys.isNotEmpty()) {
            (boys.count { MasteryDecision.compute(it.totalScore, Baremes.TOTAL_MAX, settings).isAdmitted } * 100.0) / boys.size
        } else 0.0
        val girls = assessments.filter { it.genderEnum == Gender.FILLE }
        val girlsRate = if (girls.isNotEmpty()) {
            (girls.count { MasteryDecision.compute(it.totalScore, Baremes.TOTAL_MAX, settings).isAdmitted } * 100.0) / girls.size
        } else 0.0
        return PeriodTrendPoint(
            label = label,
            boysMasteryPct = boysRate,
            girlsMasteryPct = girlsRate,
            overallMasteryPct = overall,
            count = count
        )
    }
}
