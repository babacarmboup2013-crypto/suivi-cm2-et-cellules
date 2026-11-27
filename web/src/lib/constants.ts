export const BOOTSTRAP_SUPERADMIN_EMAIL = "babacarmboup2013@gmail.com";

export const SCHOOL_MONTHS = [
  "Octobre",
  "Novembre",
  "Décembre",
  "Janvier",
  "Février",
  "Mars",
  "Avril",
  "Mai",
  "Juin",
] as const;

export const BAREMES = {
  LC_RESSOURCES_MAX: 40,
  LC_COMPETENCES_MAX: 60,
  LC_TOTAL_MAX: 100,
  MATH_RESSOURCES_MAX: 40,
  MATH_COMPETENCES_MAX: 60,
  MATH_TOTAL_MAX: 100,
  DM_RESSOURCES_MAX: 24,
  DM_COMPETENCES_MAX: 16,
  DM_TOTAL_MAX: 40,
  DD_RESSOURCES_MAX: 24,
  DD_COMPETENCES_MAX: 16,
  DD_TOTAL_MAX: 40,
  ESVS_TOTAL_MAX: 80,
  TOTAL_MAX: 280,
} as const;

export const ACTIVITIES = [
  { key: "lcRessources", label: "LC — Ressources", short: "LC Res", domain: "Langue & Comm.", max: BAREMES.LC_RESSOURCES_MAX },
  { key: "lcCompetences", label: "LC — Compétences", short: "LC Comp", domain: "Langue & Comm.", max: BAREMES.LC_COMPETENCES_MAX },
  { key: "mathRessources", label: "Maths — Ressources", short: "Math Res", domain: "Mathématiques", max: BAREMES.MATH_RESSOURCES_MAX },
  { key: "mathCompetences", label: "Maths — Compétences", short: "Math Comp", domain: "Mathématiques", max: BAREMES.MATH_COMPETENCES_MAX },
  { key: "dmRessources", label: "Découv. Monde — Ress.", short: "DM Res", domain: "ESVS (DM)", max: BAREMES.DM_RESSOURCES_MAX },
  { key: "dmCompetences", label: "Découv. Monde — Comp.", short: "DM Comp", domain: "ESVS (DM)", max: BAREMES.DM_COMPETENCES_MAX },
  { key: "ddRessources", label: "Dével. Durable — Ress.", short: "DD Res", domain: "ESVS (DD)", max: BAREMES.DD_RESSOURCES_MAX },
  { key: "ddCompetences", label: "Dével. Durable — Comp.", short: "DD Comp", domain: "ESVS (DD)", max: BAREMES.DD_COMPETENCES_MAX },
] as const;

export type ActivityKey = (typeof ACTIVITIES)[number]["key"];

export const WEEK_OPTIONS = [1, 2, 3, 4, 5];
