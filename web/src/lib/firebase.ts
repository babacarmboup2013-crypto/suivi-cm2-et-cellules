import { getApp, getApps, initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, GoogleAuthProvider, type Auth } from "firebase/auth";
import { getFirestore, type Firestore } from "firebase/firestore";

const read = (value: string | undefined) => (value ?? "").trim();

const firebaseConfig = {
  apiKey: read(process.env.NEXT_PUBLIC_FIREBASE_API_KEY),
  authDomain: read(process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN),
  projectId: read(process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID),
  storageBucket: read(process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET),
  messagingSenderId: read(process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID),
  appId: read(process.env.NEXT_PUBLIC_FIREBASE_APP_ID),
};

/** L'application Android utilise une base Firestore nommée, pas la base `(default)`. */
const databaseId =
  read(process.env.NEXT_PUBLIC_FIREBASE_DATABASE_ID) ||
  "ai-studio-android-suivicm2-d0fbd686-7c39-42f8-97e3-108c900c1d0f";

export const REQUIRED_FIREBASE_VARS = [
  "NEXT_PUBLIC_FIREBASE_API_KEY",
  "NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN",
  "NEXT_PUBLIC_FIREBASE_PROJECT_ID",
  "NEXT_PUBLIC_FIREBASE_APP_ID",
] as const;

export const missingFirebaseVars = REQUIRED_FIREBASE_VARS.filter(
  (name) => !firebaseConfig[name as keyof typeof firebaseConfig]
);

export const isFirebaseConfigured = missingFirebaseVars.length === 0;

/**
 * Firebase Auth lève `auth/invalid-api-key` dès que `apiKey` est vide. Sur Vercel,
 * une variable manquante faisait donc planter le rendu client avant même que
 * l'écran de connexion s'affiche. Des valeurs de secours non vides permettent
 * d'initialiser les objets sans erreur ; l'interface refuse ensuite tout accès
 * et affiche la liste des variables à corriger.
 */
const safeConfig = {
  apiKey: firebaseConfig.apiKey || "missing-api-key",
  authDomain: firebaseConfig.authDomain || "missing.firebaseapp.com",
  projectId: firebaseConfig.projectId || "missing-project",
  storageBucket: firebaseConfig.storageBucket || "missing.appspot.com",
  messagingSenderId: firebaseConfig.messagingSenderId || "0",
  appId: firebaseConfig.appId || "1:0:web:0",
};

let app: FirebaseApp;
let authInstance: Auth;
let firestore: Firestore;
let initError: string | null = null;

try {
  app = getApps().length ? getApp() : initializeApp(safeConfig);
  authInstance = getAuth(app);
  firestore = getFirestore(app, databaseId);
} catch (cause) {
  initError = cause instanceof Error ? cause.message : "Initialisation Firebase impossible.";
  const fallbackName = "suivi-cm2-fallback";
  const fallbackApp = getApps().some((item) => item.name === fallbackName)
    ? getApp(fallbackName)
    : initializeApp(safeConfig, fallbackName);
  authInstance = getAuth(fallbackApp);
  firestore = getFirestore(fallbackApp, databaseId);
}

export const auth = authInstance;
export const db = firestore;
export const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: "select_account" });
export const firebaseInitError = initError;
export const firebaseReady = isFirebaseConfigured && !firebaseInitError;
