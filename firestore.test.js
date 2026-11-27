const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_inspecteur_01";
const OTHER_UID = "other_admin_02";
const OP_UID = "operator_ecole01";
const OP_EMAIL = "saisie.ecole01@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSchool(adminUid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    adminUid,
    code: "EC-01",
    name: "École Élémentaire 01",
    className: "CM2 A",
    zone: "Zone Nord",
    teacherName: "M. Diop",
    boysEnrolled: 24,
    girlsEnrolled: 26,
    operatorEmails: [OP_EMAIL],
    accessCodeHash: "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
    accessCodeSalt: "00112233445566778899aabbccddeeff",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validOperator(adminUid, schoolId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    adminUid,
    fullName: "Mme Fall",
    email: OP_EMAIL,
    schoolId,
    schoolCode: "EC-01",
    schoolName: "École Élémentaire 01",
    role: "OPERATEUR_SAISIE",
    active: true,
    pinHash: "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
    pinSalt: "aabbccddeeff00112233445566778899",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validStudent(adminUid, schoolId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    adminUid,
    schoolId,
    schoolCode: "EC-01",
    schoolName: "École Élémentaire 01",
    className: "CM2 A",
    fullName: "Awa Ndiaye",
    gender: "FILLE",
    matricule: "CM2-001",
    operatorEmails: [OP_EMAIL],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validAssessment(adminUid, schoolId, studentId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    adminUid,
    schoolId,
    schoolName: "École Élémentaire 01",
    className: "CM2 A",
    studentId,
    studentName: "Awa Ndiaye",
    gender: "FILLE",
    testType: "HEBDOMADAIRE",
    month: "Novembre",
    weekNumber: 2,
    lcRessources: 32,
    lcCompetences: 48,
    mathRessources: 30,
    mathCompetences: 46,
    dmRessources: 19,
    dmCompetences: 13,
    ddRessources: 20,
    ddCompetences: 14,
    totalScore: 222,
    decision: "MAITRISE_MAXIMALE",
    recordedBy: "Mme Fall",
    operatorEmails: [OP_EMAIL],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users are denied across all collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("schools").get());
  await assertFails(db.collection("operators").get());
  await assertFails(db.collection("students").get());
  await assertFails(db.collection("assessments").get());
  await assertFails(db.collection("settings").doc(ADMIN_UID).get());
});

test("Admin manages schools, operators, students, assessments and settings", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "babacarmboup2013@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(adminDb.collection("schools").doc("school_01").set(validSchool(ADMIN_UID)));
  await assertSucceeds(adminDb.collection("operators").doc("op_01").set(validOperator(ADMIN_UID, "school_01")));

  // Atomic batch creating student + assessment on first test
  const batch = adminDb.batch();
  batch.set(adminDb.collection("students").doc("stu_01"), validStudent(ADMIN_UID, "school_01"));
  batch.set(adminDb.collection("assessments").doc("eval_01"), validAssessment(ADMIN_UID, "school_01", "stu_01"));
  await assertSucceeds(batch.commit());

  // Scoped admin queries succeed; unfiltered list fails
  await assertSucceeds(adminDb.collection("schools").where("adminUid", "==", ADMIN_UID).get());
  await assertSucceeds(adminDb.collection("operators").where("adminUid", "==", ADMIN_UID).get());
  await assertSucceeds(adminDb.collection("students").where("adminUid", "==", ADMIN_UID).get());
  await assertSucceeds(adminDb.collection("assessments").where("adminUid", "==", ADMIN_UID).get());
  // Le compte d'amorçage est superadministrateur : il voit toutes les écoles.
  await assertSucceeds(adminDb.collection("schools").get());

  // Settings
  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    adminDb.collection("settings").doc(ADMIN_UID).set({
      adminUid: ADMIN_UID,
      maxMasteryPct: 75,
      minMasteryPct: 50,
      partialMasteryPct: 35,
      academicYear: "2026-2027",
      districtName: "IEF Dakar",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Assigned verified school operator can read school, register students & record tests, but cannot modify schools or operators", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("schools").doc("school_01").set(validSchool(ADMIN_UID));
    await db.collection("schools").doc("school_02").set(validSchool(ADMIN_UID, { code: "EC-02", operatorEmails: [] }));
    await db.collection("operators").doc("op_01").set(validOperator(ADMIN_UID, "school_01"));
  });

  const opDb = testEnv.authenticatedContext(OP_UID, {
    email: OP_EMAIL,
    email_verified: true,
  }).firestore();

  // Operator sees ONLY assigned school_01 and own active operator assignment
  await assertSucceeds(opDb.collection("schools").where("operatorEmails", "array-contains", OP_EMAIL).get());
  await assertSucceeds(opDb.collection("operators").where("email", "==", OP_EMAIL).where("active", "==", true).get());
  await assertSucceeds(opDb.collection("students").where("operatorEmails", "array-contains", OP_EMAIL).get());
  await assertSucceeds(opDb.collection("assessments").where("operatorEmails", "array-contains", OP_EMAIL).get());
  await assertSucceeds(opDb.collection("schools").doc("school_01").get());
  await assertFails(opDb.collection("schools").doc("school_02").get());

  // Operator CANNOT edit/delete school or create operators
  await assertFails(opDb.collection("schools").doc("school_01").update({ name: "Hacked", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(opDb.collection("operators").doc("op_fake").set(validOperator(ADMIN_UID, "school_01")));

  // Operator CAN register a student and record an assessment for their assigned school_01
  await assertSucceeds(opDb.collection("students").doc("stu_op_1").set(validStudent(ADMIN_UID, "school_01")));
  await assertSucceeds(
    opDb.collection("assessments").doc("eval_op_1").set(validAssessment(ADMIN_UID, "school_01", "stu_op_1"))
  );

  // Operator CANNOT record students/assessments for unassigned school_02
  await assertFails(opDb.collection("students").doc("stu_other").set(validStudent(ADMIN_UID, "school_02")));

  // Operator CANNOT tamper with operatorEmails on student update
  await assertFails(
    opDb.collection("students").doc("stu_op_1").update({
      operatorEmails: [OP_EMAIL, "intruder@gmail.com"],
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Email spoofing test: unverified email operator is rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_01").set(validSchool(ADMIN_UID));
  });

  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: OP_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofDb.collection("schools").doc("school_01").get());
  await assertFails(spoofDb.collection("schools").where("operatorEmails", "array-contains", OP_EMAIL).get());
});

test("Cross-admin isolation & role escalation prevention", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_01").set(validSchool(ADMIN_UID));
  });

  const otherDb = testEnv.authenticatedContext(OTHER_UID, {
    email: "other@gmail.com",
    email_verified: true,
  }).firestore();

  await assertFails(otherDb.collection("schools").doc("school_01").get());
  await assertFails(otherDb.collection("schools").doc("school_01").delete());

  // Role escalation rejected
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email_verified: true }).firestore();
  await assertFails(
    adminDb.collection("operators").doc("op_escalate").set(validOperator(ADMIN_UID, "school_01", { role: "ADMIN" }))
  );
});

test("Superadmin creates an administrator, a school and its data-entry operator", async () => {
  const superDb = testEnv.authenticatedContext("super_uid", {
    email: "babacarmboup2013@gmail.com",
    email_verified: true,
  }).firestore();

  const adminEmail = "admin.ecole02@gmail.com";
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    superDb.collection("administrators").doc(adminEmail).set({
      email: adminEmail,
      fullName: "Mamadou Diop",
      uid: "",
      active: true,
      createdBy: "super_uid",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    superDb.collection("schools").doc("school_super").set(
      validSchool(adminEmail, { adminEmail: adminEmail, code: "EC-41" })
    )
  );

  await assertSucceeds(
    superDb.collection("operators").doc("op_super").set(
      validOperator(adminEmail, "school_super", { adminEmail: adminEmail })
    )
  );

  // L'administrateur invité peut lire son école avant même d'avoir lié son UID.
  const invitedAdminDb = testEnv.authenticatedContext("admin_uid_02", {
    email: adminEmail,
    email_verified: true,
  }).firestore();

  await assertSucceeds(invitedAdminDb.collection("schools").doc("school_super").get());
  await assertSucceeds(
    invitedAdminDb.collection("administrators").doc(adminEmail).update({
      uid: "admin_uid_02",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
