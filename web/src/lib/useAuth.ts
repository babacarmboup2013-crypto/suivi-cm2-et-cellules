"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { onAuthStateChanged, signInWithPopup, signOut, type User } from "firebase/auth";
import {
  collection,
  doc,
  getDocs,
  limit,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
  type DocumentSnapshot,
} from "firebase/firestore";
import { auth, db, googleProvider } from "./firebase";
import { BOOTSTRAP_SUPERADMIN_EMAIL } from "./constants";
import { normalizeEmail } from "./format";
import type {
  AdministratorAccount,
  DataEntryOperator,
  SessionRole,
  SuperAdminAccount,
} from "./types";

export interface AuthSession {
  user: User | null;
  role: SessionRole;
  loading: boolean;
  isSuperAdmin: boolean;
  isAdmin: boolean;
  isOperator: boolean;
  email: string;
  superAdminAccount: SuperAdminAccount | null;
  administratorAccount: AdministratorAccount | null;
  activeOperator: DataEntryOperator | null;
  error: string | null;
}

function fromSnapshot<T>(snapshot: DocumentSnapshot): T | null {
  if (!snapshot.exists()) return null;
  return { id: snapshot.id, ...(snapshot.data() as Omit<T, "id">) } as T;
}

/**
 * Çaution : les règles Firestore restent la source de vérité. Ce hook ne fait
 * qu'interpréter les documents pour afficher la bonne interface et lier le
 * compte administrateur à la première connexion Google.
 */
export function useAuth(): AuthSession & {
  login: () => Promise<void>;
  logout: () => Promise<void>;
} {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [superAdminAccount, setSuperAdminAccount] = useState<SuperAdminAccount | null>(null);
  const [administratorAccount, setAdministratorAccount] = useState<AdministratorAccount | null>(null);
  const [operators, setOperators] = useState<DataEntryOperator[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [roleLoading, setRoleLoading] = useState(true);
  const claimedRef = useRef<string | null>(null);
  const resolvedRef = useRef({ super: false, admin: false, operator: false });

  useEffect(() => {
    return onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser);
      setLoading(false);
      setError(null);
      claimedRef.current = null;
      resolvedRef.current = { super: false, admin: false, operator: false };
      setRoleLoading(Boolean(nextUser));
      if (!nextUser) {
        setSuperAdminAccount(null);
        setAdministratorAccount(null);
        setOperators([]);
      }
    });
  }, []);

  const email = normalizeEmail(user?.email ?? "");

  useEffect(() => {
    if (!user?.uid) return;
    const unsubscribe = onSnapshot(
      doc(db, "superAdmins", user.uid),
      (snapshot) => {
        setSuperAdminAccount(fromSnapshot<SuperAdminAccount>(snapshot));
        resolvedRef.current.super = true;
      },
      () => {
        setSuperAdminAccount(null);
        resolvedRef.current.super = true;
      }
    );
    return unsubscribe;
  }, [user?.uid]);

  useEffect(() => {
    if (!user?.uid || !email) return;
    const unsubscribe = onSnapshot(
      doc(db, "administrators", email),
      (snapshot) => {
        const account = fromSnapshot<AdministratorAccount>(snapshot);
        setAdministratorAccount(account);
        resolvedRef.current.admin = true;
        if (
          account?.active &&
          (!account.uid || account.uid === user.uid) &&
          claimedRef.current !== user.uid
        ) {
          claimedRef.current = user.uid;
          void claimAdministratorAccount(email, user.uid);
        }
      },
      () => {
        setAdministratorAccount(null);
        resolvedRef.current.admin = true;
      }
    );
    return unsubscribe;
  }, [user?.uid, email]);

  useEffect(() => {
    if (!user?.uid || !email) return;
    const q = query(
      collection(db, "operators"),
      where("email", "==", email),
      limit(10)
    );
    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        setOperators(
          snapshot.docs.map((document) => ({
            id: document.id,
            ...(document.data() as Omit<DataEntryOperator, "id">),
          }))
        );
        resolvedRef.current.operator = true;
      },
      () => {
        setOperators([]);
        resolvedRef.current.operator = true;
      }
    );
    return unsubscribe;
  }, [user?.uid, email]);

  useEffect(() => {
    if (
      user &&
      resolvedRef.current.super &&
      resolvedRef.current.admin &&
      resolvedRef.current.operator
    ) {
      setRoleLoading(false);
    }
  }, [user, superAdminAccount, administratorAccount, operators]);

  const isSuperAdmin = Boolean(
    user &&
      (superAdminAccount?.active === true || email === BOOTSTRAP_SUPERADMIN_EMAIL)
  );
  const isAdministrator = Boolean(
    user &&
      administratorAccount?.active === true &&
      (!administratorAccount.uid || administratorAccount.uid === user.uid)
  );
  const isOperator = !isSuperAdmin && !isAdministrator && operators.some((op) => op.active);
  const role: SessionRole = isSuperAdmin
    ? "SUPERADMIN"
    : isAdministrator
      ? "ADMIN"
      : isOperator
        ? "OPERATOR"
        : "NONE";

  const activeOperator = useMemo(
    () => operators.find((op) => op.active && normalizeEmail(op.email) === email) ?? null,
    [operators, email]
  );

  const login = useCallback(async () => {
    setError(null);
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Connexion Google impossible.");
    }
  }, []);

  const logout = useCallback(async () => {
    await signOut(auth);
  }, []);

  return {
    user,
    role,
    loading: loading || roleLoading,
    isSuperAdmin,
    isAdmin: isSuperAdmin || isAdministrator,
    isOperator,
    email,
    superAdminAccount,
    administratorAccount,
    activeOperator,
    error,
    login,
    logout,
  };
}

/**
 * Lie l'UID Firebase au compte administrateur puis remplace l'UID provisoire
 * (l'e-mail) par le vrai UID sur toutes les données existantes.
 */
async function claimAdministratorAccount(email: string, uid: string): Promise<void> {
  try {
    const accountRef = doc(db, "administrators", email);
    await updateDoc(accountRef, { uid, updatedAt: serverTimestamp() });

    const collections = ["schools", "operators", "students", "assessments"];
    for (const name of collections) {
      const snapshot = await getDocs(
        query(collection(db, name), where("adminUid", "==", email))
      );
      for (let index = 0; index < snapshot.docs.length; index += 400) {
        const batch = writeBatch(db);
        snapshot.docs.slice(index, index + 400).forEach((document) => {
          batch.update(document.ref, {
            adminUid: uid,
            updatedAt: serverTimestamp(),
          });
        });
        await batch.commit();
      }
    }
  } catch {
    // Les simples opérateurs ou les comptes non autorisés échouent silencieusement.
  }
}
