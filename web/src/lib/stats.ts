import { ACTIVITIES, BAREMES, SCHOOL_MONTHS, type ActivityKey } from "./constants";
import {
  dmTotal,
  ddTotal,
  lcTotal,
  mathTotal,
  type Assessment,
  type Gender,
  type MasteryDecision,
  type School,
  type ThresholdSettings,
} from "./types";

export type DiagnosticStatus = "POINT_FORT" | "A_AMELIORER" | "A_RENFORCER";

export const STATUS_LABELS: Record<DiagnosticStatus, string> = {
  POINT_FORT: "Point fort",
  A_AMELIORER: "À améliorer",
  A_RENFORCER: "À renforcer",
};

export interface ActivityStat {
  key: ActivityKey;
  label: string;
  short: string;
  maxScore: number;
  count: number;
  avgScore: number;
  avgPct: number;
  masteryRatePct: number;
  boysCount: number;
  boysMasteryPct: number;
  boysAvgScore: number;
  girlsCount: number;
  girlsMasteryPct: number;
  girlsAvgScore: number;
  minPointThreshold: number;
  maxPointThreshold: number;
  status: DiagnosticStatus;
}

export interface DomainStat {
  label: string;
  maxScore: number;
  avgScore: number;
  masteryRatePct: number;
  boysMasteryPct: number;
  girlsMasteryPct: number;
  status: DiagnosticStatus;
}

export interface GenderSummary {
  gender: Gender;
  count: number;
  avgTotal: number;
  masteryRatePct: number;
  maxMasteryCount: number;
  minMasteryCount: number;
  toImproveCount: number;
  toReinforceCount: number;
}

export interface ClassStat {
  school: School;
  evaluatedCount: number;
  boysEvaluated: number;
  girlsEvaluated: number;
  avgTotal: number;
  masteryRatePct: number;
  boysMasteryPct: number;
  girlsMasteryPct: number;
  maxCount: number;
  minCount: number;
  improveCount: number;
  reinforceCount: number;
  strongestActivity: ActivityKey | null;
  weakestActivity: ActivityKey | null;
  status: DiagnosticStatus;
}

export interface PeriodTrendPoint {
  label: string;
  boysMasteryPct: number;
  girlsMasteryPct: number;
  overallMasteryPct: number;
  count: number;
}

export interface GlobalSynthesis {
  totalSchoolsConfigured: number;
  activeSchoolsCount: number;
  totalAssessments: number;
  avgTotalScore: number;
  overallMasteryPct: number;
  boysSummary: GenderSummary;
  girlsSummary: GenderSummary;
  activityStats: ActivityStat[];
  domainStats: DomainStat[];
  classStats: ClassStat[];
  weeklyTrend: PeriodTrendPoint[];
  monthlyTrend: PeriodTrendPoint[];
  strengths: ActivityStat[];
  toImprove: ActivityStat[];
  toReinforce: ActivityStat[];
}

const average = (values: number[]) =>
  values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0;

export function pointThreshold(settings: ThresholdSettings, maxScore: number, pct: number) {
  return (maxScore * pct) / 100;
}

export function decisionFor(total: number, settings: ThresholdSettings): MasteryDecision {
  const pct = BAREMES.TOTAL_MAX > 0 ? (total / BAREMES.TOTAL_MAX) * 100 : 0;
  if (pct >= settings.maxMasteryPct) return "MAITRISE_MAXIMALE";
  if (pct >= settings.minMasteryPct) return "MAITRISE_MINIMALE";
  if (pct >= settings.partialMasteryPct) return "A_AMELIORER";
  return "A_RENFORCER";
}

export function isAdmitted(assessment: Assessment, settings: ThresholdSettings): boolean {
  return decisionFor(assessment.totalScore, settings) === "MAITRISE_MAXIMALE" ||
    decisionFor(assessment.totalScore, settings) === "MAITRISE_MINIMALE";
}

export function classifyMasteryRate(rate: number, settings: ThresholdSettings): DiagnosticStatus {
  if (rate >= settings.maxMasteryPct) return "POINT_FORT";
  if (rate >= settings.minMasteryPct) return "A_AMELIORER";
  return "A_RENFORCER";
}

function activityScore(key: ActivityKey, assessment: Assessment): number {
  switch (key) {
    case "lcRessources": return assessment.lcRessources;
    case "lcCompetences": return assessment.lcCompetences;
    case "mathRessources": return assessment.mathRessources;
    case "mathCompetences": return assessment.mathCompetences;
    case "dmRessources": return assessment.dmRessources;
    case "dmCompetences": return assessment.dmCompetences;
    case "ddRessources": return assessment.ddRessources;
    case "ddCompetences": return assessment.ddCompetences;
  }
}

function computeGenderSummary(
  gender: Gender,
  assessments: Assessment[],
  settings: ThresholdSettings
): GenderSummary {
  const items = assessments.filter((item) => item.gender === gender);
  const counts = {
    maxMasteryCount: 0,
    minMasteryCount: 0,
    toImproveCount: 0,
    toReinforceCount: 0,
  };
  items.forEach((item) => {
    const decision = decisionFor(item.totalScore, settings);
    if (decision === "MAITRISE_MAXIMALE") counts.maxMasteryCount += 1;
    else if (decision === "MAITRISE_MINIMALE") counts.minMasteryCount += 1;
    else if (decision === "A_AMELIORER") counts.toImproveCount += 1;
    else counts.toReinforceCount += 1;
  });
  const admitted = counts.maxMasteryCount + counts.minMasteryCount;
  return {
    gender,
    count: items.length,
    avgTotal: average(items.map((item) => item.totalScore)),
    masteryRatePct: items.length ? (admitted * 100) / items.length : 0,
    ...counts,
  };
}

function computeActivityStat(
  key: (typeof ACTIVITIES)[number],
  assessments: Assessment[],
  settings: ThresholdSettings
): ActivityStat {
  const minThreshold = pointThreshold(settings, key.max, settings.minMasteryPct);
  const maxThreshold = pointThreshold(settings, key.max, settings.maxMasteryPct);
  const boys = assessments.filter((item) => item.gender === "GARCON");
  const girls = assessments.filter((item) => item.gender === "FILLE");
  const mastered = assessments.filter((item) => activityScore(key.key, item) >= minThreshold).length;
  const boysMastered = boys.filter((item) => activityScore(key.key, item) >= minThreshold).length;
  const girlsMastered = girls.filter((item) => activityScore(key.key, item) >= minThreshold).length;
  const avgScore = average(assessments.map((item) => activityScore(key.key, item)));

  return {
    key: key.key,
    label: key.label,
    short: key.short,
    maxScore: key.max,
    count: assessments.length,
    avgScore,
    avgPct: key.max > 0 ? (avgScore / key.max) * 100 : 0,
    masteryRatePct: assessments.length ? (mastered * 100) / assessments.length : 0,
    boysCount: boys.length,
    boysMasteryPct: boys.length ? (boysMastered * 100) / boys.length : 0,
    boysAvgScore: average(boys.map((item) => activityScore(key.key, item))),
    girlsCount: girls.length,
    girlsMasteryPct: girls.length ? (girlsMastered * 100) / girls.length : 0,
    girlsAvgScore: average(girls.map((item) => activityScore(key.key, item))),
    minPointThreshold: minThreshold,
    maxPointThreshold: maxThreshold,
    status: classifyMasteryRate(
      assessments.length ? (mastered * 100) / assessments.length : 0,
      settings
    ),
  };
}

function computeDomainStat(
  label: string,
  maxScore: number,
  assessments: Assessment[],
  settings: ThresholdSettings,
  selector: (item: Assessment) => number
): DomainStat {
  const minThreshold = pointThreshold(settings, maxScore, settings.minMasteryPct);
  const boys = assessments.filter((item) => item.gender === "GARCON");
  const girls = assessments.filter((item) => item.gender === "FILLE");
  const rate = assessments.length
    ? (assessments.filter((item) => selector(item) >= minThreshold).length * 100) / assessments.length
    : 0;
  return {
    label,
    maxScore,
    avgScore: average(assessments.map(selector)),
    masteryRatePct: rate,
    boysMasteryPct: boys.length
      ? (boys.filter((item) => selector(item) >= minThreshold).length * 100) / boys.length
      : 0,
    girlsMasteryPct: girls.length
      ? (girls.filter((item) => selector(item) >= minThreshold).length * 100) / girls.length
      : 0,
    status: classifyMasteryRate(rate, settings),
  };
}

function computeClassStat(
  school: School,
  assessments: Assessment[],
  settings: ThresholdSettings
): ClassStat {
  const boys = assessments.filter((item) => item.gender === "GARCON");
  const girls = assessments.filter((item) => item.gender === "FILLE");
  const counts = { maxCount: 0, minCount: 0, improveCount: 0, reinforceCount: 0 };
  assessments.forEach((item) => {
    const decision = decisionFor(item.totalScore, settings);
    if (decision === "MAITRISE_MAXIMALE") counts.maxCount += 1;
    else if (decision === "MAITRISE_MINIMALE") counts.minCount += 1;
    else if (decision === "A_AMELIORER") counts.improveCount += 1;
    else counts.reinforceCount += 1;
  });
  const admitted = counts.maxCount + counts.minCount;
  const rate = assessments.length ? (admitted * 100) / assessments.length : 0;
  const actRates = assessments.length
    ? ACTIVITIES.map((activity) => ({
        key: activity.key,
        value:
          (assessments.filter(
            (item) => activityScore(activity.key, item) >= pointThreshold(settings, activity.max, settings.minMasteryPct)
          ).length *
            100) /
          assessments.length,
      }))
    : [];
  const strongest = actRates.length
    ? actRates.reduce((best, item) => (item.value > best.value ? item : best)).key
    : null;
  const weakest = actRates.length
    ? actRates.reduce((worst, item) => (item.value < worst.value ? item : worst)).key
    : null;

  return {
    school,
    evaluatedCount: assessments.length,
    boysEvaluated: boys.length,
    girlsEvaluated: girls.length,
    avgTotal: average(assessments.map((item) => item.totalScore)),
    masteryRatePct: rate,
    boysMasteryPct: boys.length
      ? (boys.filter((item) => isAdmitted(item, settings)).length * 100) / boys.length
      : 0,
    girlsMasteryPct: girls.length
      ? (girls.filter((item) => isAdmitted(item, settings)).length * 100) / girls.length
      : 0,
    ...counts,
    strongestActivity: strongest,
    weakestActivity: weakest,
    status: classifyMasteryRate(rate, settings),
  };
}

function computeTrendPoint(
  label: string,
  assessments: Assessment[],
  settings: ThresholdSettings
): PeriodTrendPoint {
  const boys = assessments.filter((item) => item.gender === "GARCON");
  const girls = assessments.filter((item) => item.gender === "FILLE");
  return {
    label,
    boysMasteryPct: boys.length
      ? (boys.filter((item) => isAdmitted(item, settings)).length * 100) / boys.length
      : 0,
    girlsMasteryPct: girls.length
      ? (girls.filter((item) => isAdmitted(item, settings)).length * 100) / girls.length
      : 0,
    overallMasteryPct: assessments.length
      ? (assessments.filter((item) => isAdmitted(item, settings)).length * 100) / assessments.length
      : 0,
    count: assessments.length,
  };
}

export function computeSynthesis(
  schools: School[],
  filteredAssessments: Assessment[],
  allAssessmentsForTrends: Assessment[],
  settings: ThresholdSettings
): GlobalSynthesis {
  const totalCount = filteredAssessments.length;
  const admittedCount = filteredAssessments.filter((item) => isAdmitted(item, settings)).length;
  const activityStats = ACTIVITIES.map((activity) =>
    computeActivityStat(activity, filteredAssessments, settings)
  );
  const bySchool = new Map<string, Assessment[]>();
  filteredAssessments.forEach((item) => {
    const list = bySchool.get(item.schoolId) ?? [];
    list.push(item);
    bySchool.set(item.schoolId, list);
  });

  const classStats = schools
    .map((school) => computeClassStat(school, bySchool.get(school.id) ?? [], settings))
    .sort((left, right) => {
      if ((right.evaluatedCount > 0) !== (left.evaluatedCount > 0)) {
        return Number(right.evaluatedCount > 0) - Number(left.evaluatedCount > 0);
      }
      if (right.masteryRatePct !== left.masteryRatePct) {
        return right.masteryRatePct - left.masteryRatePct;
      }
      return left.school.code.localeCompare(right.school.code);
    });

  const weeklyTrend = [1, 2, 3, 4].map((week) =>
    computeTrendPoint(
      `S${week}`,
      allAssessmentsForTrends.filter(
        (item) => item.testType === "HEBDOMADAIRE" && item.weekNumber === week
      ),
      settings
    )
  );
  const monthlyTrend = SCHOOL_MONTHS.map((month) => {
    const items = allAssessmentsForTrends.filter(
      (item) => item.testType === "MENSUEL" && item.month === month
    );
    return items.length ? computeTrendPoint(month.slice(0, 3), items, settings) : null;
  }).filter((item): item is PeriodTrendPoint => item !== null);

  const domainSelectors: Array<[string, number, (item: Assessment) => number]> = [
    ["Langue & Communication", BAREMES.LC_TOTAL_MAX, lcTotal],
    ["Mathématiques", BAREMES.MATH_TOTAL_MAX, mathTotal],
    ["ESVS (Global)", BAREMES.ESVS_TOTAL_MAX, (item) => dmTotal(item) + ddTotal(item)],
    ["• Découverte du Monde", BAREMES.DM_TOTAL_MAX, dmTotal],
    ["• Développement Durable", BAREMES.DD_TOTAL_MAX, ddTotal],
  ];

  return {
    totalSchoolsConfigured: schools.length,
    activeSchoolsCount: bySchool.size,
    totalAssessments: totalCount,
    avgTotalScore: average(filteredAssessments.map((item) => item.totalScore)),
    overallMasteryPct: totalCount ? (admittedCount * 100) / totalCount : 0,
    boysSummary: computeGenderSummary("GARCON", filteredAssessments, settings),
    girlsSummary: computeGenderSummary("FILLE", filteredAssessments, settings),
    activityStats,
    domainStats: domainSelectors.map(([label, max, selector]) =>
      computeDomainStat(label, max, filteredAssessments, settings, selector)
    ),
    classStats,
    weeklyTrend,
    monthlyTrend,
    strengths: totalCount
      ? activityStats
          .filter((item) => item.status === "POINT_FORT")
          .sort((left, right) => right.masteryRatePct - left.masteryRatePct)
      : [],
    toImprove: totalCount
      ? activityStats
          .filter((item) => item.status === "A_AMELIORER")
          .sort((left, right) => left.masteryRatePct - right.masteryRatePct)
      : [],
    toReinforce: totalCount
      ? activityStats
          .filter((item) => item.status === "A_RENFORCER")
          .sort((left, right) => left.masteryRatePct - right.masteryRatePct)
      : [],
  };
}
