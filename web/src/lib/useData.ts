"use client";

import { useEffect, useMemo, useState } from "react";
import {
  collection,
  doc,
  onSnapshot,
  type DocumentData,
  type QueryDocumentSnapshot,
} from "firebase/firestore";
import { db } from "./firebase";
import { normalizeEmail } from "./format";
import { DEFAULT_SETTINGS } from "./types";
import type {
  AdministratorAccount,
  Assessment,
  DataEntryOperator,
  School,
  Student,
  ThresholdSettings,
} from "./types";
import type { AuthSession } from "./useAuth";

function mapDocument<T>(snapshot: QueryDocumentSnapshot<DocumentData>): T {
  return { id: snapshot.id, ...(snapshot.data() as Omit<T, "id">) } as T;
}

export function useCollection<T>(name: string, enabled = true) {
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!enabled) {
      setItems([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    const unsubscribe = onSnapshot(
      collection(db, name),
      (snapshot) => {
        setItems(snapshot.docs.map((document) => mapDocument<T>(document)));
        setLoading(false);
      },
      () => {
        setItems([]);
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [name, enabled]);

  return { items, loading };
}

export interface AppData {
  schools: School[];
  operators: DataEntryOperator[];
  students: Student[];
  assessments: Assessment[];
  settings: ThresholdSettings;
  administrators: AdministratorAccount[];
  loading: boolean;
  ownerUid: string;
  ownerEmail: string;
}

/**
 * Les règles Firestore filtrent déjà les documents autorisés. Ce hook applique
 * en plus un filtrage client pour refléter la portée du rôle affiché.
 */
export function useAppData(session: AuthSession): AppData {
  const enabled = Boolean(session.user);
  const schoolsAll = useCollection<School>("schools", enabled);
  const operatorsAll = useCollection<DataEntryOperator>("operators", enabled);
  const studentsAll = useCollection<Student>("students", enabled);
  const assessmentsAll = useCollection<Assessment>("assessments", enabled);
  const settingsAll = useCollection<ThresholdSettings>("settings", enabled);
  const administratorsAll = useCollection<AdministratorAccount>("administrators", enabled);

  const uid = session.user?.uid ?? "";
  const email = normalizeEmail(session.email);

  const ownerUid = session.isSuperAdmin ? uid : session.isAdmin ? uid : "";
  const ownerEmail = email;

  const scoped = useMemo(() => {
    const belongs = (item: { adminUid: string; adminEmail: string }) => {
      if (session.isSuperAdmin) return true;
      if (session.isAdmin) {
        return item.adminUid === uid || normalizeEmail(item.adminEmail) === email;
      }
      return false;
    };

    const schools = schoolsAll.items.filter(belongs);
    const operators = operatorsAll.items.filter(belongs);
    const students = studentsAll.items.filter(belongs);
    const assessments = assessmentsAll.items.filter(belongs);

    if (session.isOperator) {
      const assignedSchoolIds = new Set(
        operatorsAll.items
          .filter((op) => normalizeEmail(op.email) === email && op.active)
          .map((op) => op.schoolId)
      );
      return {
        schools: schoolsAll.items.filter((school) => assignedSchoolIds.has(school.id)),
        operators: [] as DataEntryOperator[],
        students: studentsAll.items.filter((item) => assignedSchoolIds.has(item.schoolId)),
        assessments: assessmentsAll.items.filter((item) => assignedSchoolIds.has(item.schoolId)),
      };
    }

    return { schools, operators, students, assessments };
  }, [
    session.isSuperAdmin,
    session.isAdmin,
    session.isOperator,
    schoolsAll.items,
    operatorsAll.items,
    studentsAll.items,
    assessmentsAll.items,
    uid,
    email,
  ]);

  const settings = useMemo(() => {
    const candidate =
      settingsAll.items.find((item) => item.adminUid === uid) ??
      settingsAll.items.find((item) => normalizeEmail(item.adminEmail) === email) ??
      settingsAll.items[0];
    return candidate ?? DEFAULT_SETTINGS;
  }, [settingsAll.items, uid, email]);

  return {
    schools: scoped.schools,
    operators: scoped.operators,
    students: scoped.students,
    assessments: scoped.assessments,
    settings,
    administrators: session.isSuperAdmin ? administratorsAll.items : [],
    loading:
      enabled &&
      (schoolsAll.loading ||
        operatorsAll.loading ||
        studentsAll.loading ||
        assessmentsAll.loading ||
        settingsAll.loading ||
        (session.isSuperAdmin && administratorsAll.loading)),
    ownerUid,
    ownerEmail,
  };
}
