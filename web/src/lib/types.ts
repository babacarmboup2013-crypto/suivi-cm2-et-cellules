import type { Timestamp } from "firebase/firestore";

/** Rôles de la hiérarchie : SUPERADMIN > ADMIN > OPERATOR. */
export type SessionRole = "SUPERADMIN" | "ADMIN" | "OPERATOR" | "NONE";

export const ROLE_LABELS: Record<SessionRole, string> = {
  SUPERADMIN: "Superadministrateur",
  ADMIN: "Administrateur",
  OPERATOR: "Utilisateur de saisie",
  NONE: "Accès non autorisé",
};

export type Gender = "GARCON" | "FILLE";
export type TestType = "HEBDOMADAIRE" | "MENSUEL";
export type MasteryDecision =
  | "MAITRISE_MAXIMALE"
  | "MAITRISE_MINIMALE"
  | "A_AMELIORER"
  | "A_RENFORCER";

export const DECISION_LABELS: Record<MasteryDecision, string> = {
  MAITRISE_MAXIMALE: "Maîtrise Maximale",
  MAITRISE_MINIMALE: "Maîtrise Minimale",
  A_AMELIORER: "À améliorer",
  A_RENFORCER: "À renforcer",
};

export const DECISION_SHORT: Record<MasteryDecision, string> = {
  MAITRISE_MAXIMALE: "M. Max",
  MAITRISE_MINIMALE: "M. Min",
  A_AMELIORER: "À amél.",
  A_RENFORCER: "À renf.",
};

export interface School {
  id: string;
  adminUid: string;
  adminEmail: string;
  code: string;
  name: string;
  className: string;
  zone: string;
  teacherName: string;
  boysEnrolled: number;
  girlsEnrolled: number;
  operatorEmails: string[];
  accessCodeHash: string;
  accessCodeSalt: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface DataEntryOperator {
  id: string;
  adminUid: string;
  adminEmail: string;
  fullName: string;
  email: string;
  schoolId: string;
  schoolCode: string;
  schoolName: string;
  role: "OPERATEUR_SAISIE";
  active: boolean;
  pinHash: string;
  pinSalt: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface Student {
  id: string;
  adminUid: string;
  adminEmail: string;
  schoolId: string;
  schoolCode: string;
  schoolName: string;
  className: string;
  fullName: string;
  gender: Gender;
  matricule: string;
  operatorEmails: string[];
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface Assessment {
  id: string;
  adminUid: string;
  adminEmail: string;
  schoolId: string;
  schoolName: string;
  className: string;
  studentId: string;
  studentName: string;
  gender: Gender;
  testType: TestType;
  month: string;
  weekNumber: number;
  lcRessources: number;
  lcCompetences: number;
  mathRessources: number;
  mathCompetences: number;
  dmRessources: number;
  dmCompetences: number;
  ddRessources: number;
  ddCompetences: number;
  totalScore: number;
  decision: MasteryDecision;
  recordedBy: string;
  operatorEmails: string[];
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface ThresholdSettings {
  id: string;
  adminUid: string;
  adminEmail: string;
  maxMasteryPct: number;
  minMasteryPct: number;
  partialMasteryPct: number;
  academicYear: string;
  districtName: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface AdministratorAccount {
  id: string;
  email: string;
  fullName: string;
  uid: string;
  active: boolean;
  createdBy: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface SuperAdminAccount {
  id: string;
  email: string;
  fullName: string;
  active: boolean;
  createdBy: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface FilterState {
  testType: TestType | null;
  month: string | null;
  weekNumber: number | null;
  schoolId: string | null;
  gender: Gender | null;
}

export interface GeneratedSchoolCode {
  schoolCode: string;
  schoolName: string;
  accessCode: string;
}

export const DEFAULT_FILTERS: FilterState = {
  testType: null,
  month: null,
  weekNumber: null,
  schoolId: null,
  gender: null,
};

export const DEFAULT_SETTINGS: ThresholdSettings = {
  id: "",
  adminUid: "",
  adminEmail: "",
  maxMasteryPct: 75,
  minMasteryPct: 50,
  partialMasteryPct: 35,
  academicYear: "2026-2027",
  districtName: "Circonscription CM2 — 40 Écoles",
  createdAt: null,
  updatedAt: null,
};

export const lcTotal = (a: Assessment) => a.lcRessources + a.lcCompetences;
export const mathTotal = (a: Assessment) => a.mathRessources + a.mathCompetences;
export const dmTotal = (a: Assessment) => a.dmRessources + a.dmCompetences;
export const ddTotal = (a: Assessment) => a.ddRessources + a.ddCompetences;
export const esvsTotal = (a: Assessment) => dmTotal(a) + ddTotal(a);
export const totalEnrolled = (s: School) => s.boysEnrolled + s.girlsEnrolled;

export const periodLabel = (a: Assessment) =>
  a.testType === "MENSUEL" ? `Grand Test • ${a.month}` : `Sem. ${a.weekNumber} • ${a.month}`;
