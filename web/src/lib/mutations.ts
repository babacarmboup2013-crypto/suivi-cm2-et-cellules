import {
  addDoc,
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type DocumentReference,
} from "firebase/firestore";
import { db } from "./firebase";
import { normalizeEmail } from "./format";
import { generateAccessCode, hashAccessCode } from "./security";
import { BAREMES } from "./constants";
import type {
  Assessment,
  DataEntryOperator,
  Gender,
  GeneratedSchoolCode,
  MasteryDecision,
  School,
  Student,
  TestType,
  ThresholdSettings,
} from "./types";

export interface SchoolInput {
  code: string;
  name: string;
  className: string;
  zone: string;
  teacherName: string;
  boysEnrolled: number;
  girlsEnrolled: number;
  accessCode?: string;
}

export interface OperatorInput {
  fullName: string;
  email: string;
  school: School;
  active: boolean;
}

interface OperatorAccess {
  schoolId: string;
  email: string;
  active: boolean;
}

export interface StudentInput {
  fullName: string;
  gender: Gender;
  matricule: string;
  school: School;
}

export interface AssessmentInput {
  school: School;
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
  settings: ThresholdSettings;
  recordedBy: string;
  students: Student[];
}

const clean = (value: string) => value.trim().replace(/\s+/g, " ");
const normalizedName = (value: string) => clean(value).toLowerCase();
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const roundHalf = (value: number) => Math.round(value * 2) / 2;
const score = (value: number, max: number) => clamp(roundHalf(Number(value) || 0), 0, max);

function ownerOf(school: School, currentUid: string, currentEmail: string) {
  return {
    adminUid: school.adminUid || school.adminEmail || currentUid,
    adminEmail: normalizeEmail(school.adminEmail || currentEmail),
  };
}

function decide(total: number, settings: ThresholdSettings): MasteryDecision {
  const pct = BAREMES.TOTAL_MAX > 0 ? (total / BAREMES.TOTAL_MAX) * 100 : 0;
  if (pct >= settings.maxMasteryPct) return "MAITRISE_MAXIMALE";
  if (pct >= settings.minMasteryPct) return "MAITRISE_MINIMALE";
  if (pct >= settings.partialMasteryPct) return "A_AMELIORER";
  return "A_RENFORCER";
}

/* -------------------------------------------------------------------------- */
/* Administrateurs                                                            */
/* -------------------------------------------------------------------------- */

export async function createAdministrator(
  email: string,
  fullName: string,
  createdBy: string
): Promise<void> {
  const normalized = normalizeEmail(email);
  if (!normalized.includes("@") || !normalized.includes(".")) {
    throw new Error("Adresse e-mail Google invalide.");
  }
  if (!clean(fullName)) throw new Error("Le nom complet est obligatoire.");
  await setDoc(doc(db, "administrators", normalized), {
    email: normalized,
    fullName: clean(fullName).slice(0, 80),
    uid: "",
    active: true,
    createdBy,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function setAdministratorActive(
  email: string,
  active: boolean
): Promise<void> {
  await updateDoc(doc(db, "administrators", normalizeEmail(email)), {
    active,
    updatedAt: serverTimestamp(),
  });
}

/* -------------------------------------------------------------------------- */
/* Écoles et codes d'accès                                                    */
/* -------------------------------------------------------------------------- */

export async function saveSchool(
  input: SchoolInput,
  existing: School | null,
  ownerUid: string,
  ownerEmail: string
): Promise<GeneratedSchoolCode | null> {
  const code = clean(input.code).slice(0, 24);
  const name = clean(input.name).slice(0, 100);
  if (!code) throw new Error("Le code de l'école est obligatoire.");
  if (!name) throw new Error("Le nom de l'école est obligatoire.");

  let plainCode = input.accessCode?.trim() || "";
  let generatedCode = "";
  if (!plainCode && !existing) {
    generatedCode = generateAccessCode();
    plainCode = generatedCode;
  }
  const hashed = plainCode ? await hashAccessCode(plainCode) : null;

  const payload = {
    adminUid: ownerUid,
    adminEmail: normalizeEmail(ownerEmail),
    code,
    name,
    className: clean(input.className || "CM2 A").slice(0, 40) || "CM2 A",
    zone: clean(input.zone).slice(0, 80),
    teacherName: clean(input.teacherName).slice(0, 80),
    boysEnrolled: clamp(Math.round(input.boysEnrolled), 0, 300),
    girlsEnrolled: clamp(Math.round(input.girlsEnrolled), 0, 300),
    operatorEmails: existing?.operatorEmails ?? [],
    accessCodeHash: hashed?.hashHex ?? existing?.accessCodeHash ?? "",
    accessCodeSalt: hashed?.saltHex ?? existing?.accessCodeSalt ?? "",
    createdAt: existing?.createdAt ?? serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  if (existing) {
    const { createdAt: _createdAt, ...updatable } = payload;
    await updateDoc(doc(db, "schools", existing.id), updatable);
  } else {
    await addDoc(collection(db, "schools"), payload);
  }

  return generatedCode ? { schoolCode: code, schoolName: name, accessCode: generatedCode } : null;
}

export async function saveSchoolForAdmin(
  adminUid: string,
  adminEmail: string,
  input: SchoolInput,
  currentUid: string,
  currentEmail: string
): Promise<GeneratedSchoolCode | null> {
  return saveSchool(
    input,
    null,
    adminUid || adminEmail || currentUid,
    normalizeEmail(adminEmail || currentEmail)
  );
}

export async function deleteSchool(schoolId: string): Promise<void> {
  await deleteDoc(doc(db, "schools", schoolId));
}

/** Initialise ou complète le réseau des 40 écoles, comme sur Android. */
export async function bootstrapSchools(
  ownerUid: string,
  ownerEmail: string,
  existingCodes: string[]
): Promise<GeneratedSchoolCode[]> {
  const existing = new Set(existingCodes.map((code) => clean(code).toUpperCase()));
  const missing = Array.from({ length: 40 }, (_, index) => index + 1).filter(
    (number) => !existing.has(`EC-${String(number).padStart(2, "0")}`)
  );
  if (!missing.length) return [];

  const zones = ["Nord", "Sud", "Est", "Ouest"];
  const batch = writeBatch(db);
  const generated: GeneratedSchoolCode[] = [];

  for (const number of missing) {
    const schoolCode = `EC-${String(number).padStart(2, "0")}`;
    const schoolName = `École Élémentaire ${String(number).padStart(2, "0")}`;
    const accessCode = generateAccessCode();
    const hashed = await hashAccessCode(accessCode);
    batch.set(doc(collection(db, "schools")), {
      adminUid: ownerUid,
      adminEmail: normalizeEmail(ownerEmail),
      code: schoolCode,
      name: schoolName,
      className: "CM2 A",
      zone: zones[(number - 1) % zones.length],
      teacherName: "",
      boysEnrolled: 0,
      girlsEnrolled: 0,
      operatorEmails: [],
      accessCodeHash: hashed.hashHex,
      accessCodeSalt: hashed.saltHex,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    generated.push({ schoolCode, schoolName, accessCode });
  }

  await batch.commit();
  return generated;
}

export async function generateMissingCodes(
  schools: School[]
): Promise<GeneratedSchoolCode[]> {
  const targets = schools.filter((school) => !school.accessCodeHash);
  if (!targets.length) return [];
  const batch = writeBatch(db);
  const generated: GeneratedSchoolCode[] = [];
  for (const school of targets) {
    const accessCode = generateAccessCode();
    const hashed = await hashAccessCode(accessCode);
    batch.update(doc(db, "schools", school.id), {
      accessCodeHash: hashed.hashHex,
      accessCodeSalt: hashed.saltHex,
      updatedAt: serverTimestamp(),
    });
    generated.push({
      schoolCode: school.code,
      schoolName: school.name,
      accessCode,
    });
  }
  await batch.commit();
  return generated;
}

/* -------------------------------------------------------------------------- */
/* Opérateurs de saisie                                                       */
/* -------------------------------------------------------------------------- */

export async function saveOperator(
  operatorId: string | null,
  input: OperatorInput,
  currentUid: string,
  currentEmail: string,
  operators: DataEntryOperator[]
): Promise<void> {
  const owner = ownerOf(input.school, currentUid, currentEmail);
  const payload = {
    adminUid: owner.adminUid,
    adminEmail: owner.adminEmail,
    fullName: clean(input.fullName).slice(0, 80),
    email: normalizeEmail(input.email),
    schoolId: input.school.id,
    schoolCode: input.school.code,
    schoolName: input.school.name,
    role: "OPERATEUR_SAISIE" as const,
    active: input.active,
    pinHash: "",
    pinSalt: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  if (operatorId) {
    const { createdAt: _createdAt, ...updatable } = payload;
    await updateDoc(doc(db, "operators", operatorId), updatable);
  } else {
    await addDoc(collection(db, "operators"), payload);
  }

  const access = {
    schoolId: input.school.id,
    email: payload.email,
    active: payload.active,
  };
  const next = operatorId
    ? operators.map((op) => (op.id === operatorId ? { ...op, ...access } : op))
    : [...operators, access];
  await syncOperatorEmails(input.school.id, next);
}

export async function setOperatorActive(
  operator: DataEntryOperator,
  active: boolean,
  operators: DataEntryOperator[] = []
): Promise<void> {
  await updateDoc(doc(db, "operators", operator.id), {
    active,
    updatedAt: serverTimestamp(),
  });
  const next = operators.length
    ? operators.map((op) => (op.id === operator.id ? { ...op, active } : op))
    : [{ ...operator, active }];
  await syncOperatorEmails(operator.schoolId, next);
}

export async function deleteOperator(
  operator: DataEntryOperator,
  operators: DataEntryOperator[]
): Promise<void> {
  await deleteDoc(doc(db, "operators", operator.id));
  await syncOperatorEmails(
    operator.schoolId,
    operators.filter((op) => op.id !== operator.id)
  );
}

export async function syncOperatorEmails(
  schoolId: string,
  operators: OperatorAccess[]
): Promise<void> {
  const emails = Array.from(
    new Set(
      operators
        .filter((op) => op.schoolId === schoolId && op.active && normalizeEmail(op.email))
        .map((op) => normalizeEmail(op.email))
    )
  ).slice(0, 20);

  const updates: Array<{
    ref: DocumentReference;
    data: Record<string, unknown>;
  }> = [
    {
      ref: doc(db, "schools", schoolId),
      data: { operatorEmails: emails, updatedAt: serverTimestamp() },
    },
  ];

  for (const collectionName of ["students", "assessments"]) {
    const snapshot = await getDocs(
      query(collection(db, collectionName), where("schoolId", "==", schoolId))
    );
    snapshot.docs.forEach((document) => {
      const current: string[] = document.data().operatorEmails ?? [];
      const currentSet = new Set(current.map(normalizeEmail));
      if (
        current.length !== emails.length ||
        emails.some((email) => !currentSet.has(email))
      ) {
        updates.push({
          ref: document.ref,
          data: { operatorEmails: emails, updatedAt: serverTimestamp() },
        });
      }
    });
  }

  // Limite Firestore : 500 opérations par lot.
  for (let index = 0; index < updates.length; index += 400) {
    const batch = writeBatch(db);
    updates.slice(index, index + 400).forEach((update) => {
      batch.update(update.ref, update.data);
    });
    await batch.commit();
  }
}

export async function addOperatorEmail(
  school: School,
  email: string
): Promise<void> {
  const normalized = normalizeEmail(email);
  if (!normalized) return;
  await updateDoc(doc(db, "schools", school.id), {
    operatorEmails: arrayUnion(normalized),
    updatedAt: serverTimestamp(),
  });
}

export async function removeOperatorEmail(
  school: School,
  email: string
): Promise<void> {
  await updateDoc(doc(db, "schools", school.id), {
    operatorEmails: arrayRemove(normalizeEmail(email)),
    updatedAt: serverTimestamp(),
  });
}

/* -------------------------------------------------------------------------- */
/* Élèves                                                                     */
/* -------------------------------------------------------------------------- */

export async function saveStudent(
  studentId: string | null,
  input: StudentInput,
  currentUid: string,
  currentEmail: string
): Promise<void> {
  const owner = ownerOf(input.school, currentUid, currentEmail);
  const payload = {
    adminUid: owner.adminUid,
    adminEmail: owner.adminEmail,
    schoolId: input.school.id,
    schoolCode: input.school.code,
    schoolName: input.school.name,
    className: input.school.className || "CM2 A",
    fullName: clean(input.fullName).slice(0, 80),
    gender: input.gender,
    matricule: clean(input.matricule).slice(0, 30),
    operatorEmails: input.school.operatorEmails ?? [],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  if (!payload.fullName) throw new Error("Le nom de l'élève est obligatoire.");
  if (studentId) {
    const { createdAt: _createdAt, ...updatable } = payload;
    await updateDoc(doc(db, "students", studentId), updatable);
  } else {
    await addDoc(collection(db, "students"), payload);
  }
}

export async function deleteStudent(studentId: string): Promise<void> {
  await deleteDoc(doc(db, "students", studentId));
}

/* -------------------------------------------------------------------------- */
/* Évaluations                                                                */
/* -------------------------------------------------------------------------- */

export async function saveAssessment(
  assessmentId: string | null,
  input: AssessmentInput,
  currentUid: string,
  currentEmail: string
): Promise<boolean> {
  const owner = ownerOf(input.school, currentUid, currentEmail);
  const studentFullName = clean(input.studentName).slice(0, 80);
  if (!studentFullName) throw new Error("Le nom de l'élève est obligatoire.");

  const scores = {
    lcRessources: score(input.lcRessources, BAREMES.LC_RESSOURCES_MAX),
    lcCompetences: score(input.lcCompetences, BAREMES.LC_COMPETENCES_MAX),
    mathRessources: score(input.mathRessources, BAREMES.MATH_RESSOURCES_MAX),
    mathCompetences: score(input.mathCompetences, BAREMES.MATH_COMPETENCES_MAX),
    dmRessources: score(input.dmRessources, BAREMES.DM_RESSOURCES_MAX),
    dmCompetences: score(input.dmCompetences, BAREMES.DM_COMPETENCES_MAX),
    ddRessources: score(input.ddRessources, BAREMES.DD_RESSOURCES_MAX),
    ddCompetences: score(input.ddCompetences, BAREMES.DD_COMPETENCES_MAX),
  };
  const total =
    scores.lcRessources +
    scores.lcCompetences +
    scores.mathRessources +
    scores.mathCompetences +
    scores.dmRessources +
    scores.dmCompetences +
    scores.ddRessources +
    scores.ddCompetences;

  const batch = writeBatch(db);
  let studentId = input.studentId;
  let createdStudent = false;

  if (!studentId) {
    const existing = input.students.find(
      (student) =>
        student.schoolId === input.school.id &&
        normalizedName(student.fullName) === normalizedName(studentFullName)
    );
    if (existing) {
      studentId = existing.id;
    } else {
      const studentRef = doc(collection(db, "students"));
      batch.set(studentRef, {
        adminUid: owner.adminUid,
        adminEmail: owner.adminEmail,
        schoolId: input.school.id,
        schoolCode: input.school.code,
        schoolName: input.school.name,
        className: input.school.className || "CM2 A",
        fullName: studentFullName,
        gender: input.gender,
        matricule: "",
        operatorEmails: input.school.operatorEmails ?? [],
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      studentId = studentRef.id;
      createdStudent = true;
    }
  }

  const fields = {
    adminUid: owner.adminUid,
    adminEmail: owner.adminEmail,
    schoolId: input.school.id,
    schoolName: input.school.name,
    className: input.school.className || "CM2 A",
    studentId,
    studentName: studentFullName,
    gender: input.gender,
    testType: input.testType,
    month: clean(input.month).slice(0, 30) || "Novembre",
    // Un grand test mensuel n'a pas de semaine : Android enregistre 0.
    weekNumber:
      input.testType === "MENSUEL" ? 0 : clamp(Math.round(input.weekNumber), 1, 4),
    ...scores,
    totalScore: clamp(total, 0, BAREMES.TOTAL_MAX),
    decision: decide(total, input.settings),
    recordedBy: clean(input.recordedBy || "Web").slice(0, 80),
    operatorEmails: input.school.operatorEmails ?? [],
    updatedAt: serverTimestamp(),
  };

  if (assessmentId) {
    batch.update(doc(db, "assessments", assessmentId), fields);
  } else {
    batch.set(doc(collection(db, "assessments")), {
      ...fields,
      createdAt: serverTimestamp(),
    });
  }
  await batch.commit();
  return createdStudent;
}

export async function deleteAssessment(assessmentId: string): Promise<void> {
  await deleteDoc(doc(db, "assessments", assessmentId));
}

/* -------------------------------------------------------------------------- */
/* Seuils                                                                     */
/* -------------------------------------------------------------------------- */

export async function saveSettings(
  adminUid: string,
  adminEmail: string,
  settings: ThresholdSettings
): Promise<void> {
  const maxMasteryPct = clamp(Number(settings.maxMasteryPct) || 75, 50, 100);
  const minMasteryPct = clamp(Number(settings.minMasteryPct) || 50, 30, Math.min(95, maxMasteryPct));
  const partialMasteryPct = clamp(
    Number(settings.partialMasteryPct) || 35,
    10,
    Math.max(10, minMasteryPct)
  );
  const payload = {
    adminUid,
    adminEmail: normalizeEmail(adminEmail || settings.adminEmail),
    maxMasteryPct,
    minMasteryPct,
    partialMasteryPct,
    academicYear: clean(settings.academicYear).slice(0, 20) || "2026-2027",
    districtName: clean(settings.districtName).slice(0, 100),
    createdAt: settings.createdAt ?? serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  if (settings.id) {
    const { createdAt: _createdAt, ...updatable } = payload;
    await updateDoc(doc(db, "settings", settings.id), updatable);
  } else {
    await setDoc(doc(db, "settings", adminUid), payload);
  }
}
