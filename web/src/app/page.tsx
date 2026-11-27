"use client";

import dynamic from "next/dynamic";
import { ErrorBoundary } from "@/components/ErrorBoundary";

// Firebase Auth et Firestore sont initialisés côté navigateur uniquement :
// les variables NEXT_PUBLIC_* sont injectées au build, mais aucun appel Firebase
// ne doit être effectué pendant le pré-rendu serveur.
const HomeClient = dynamic(() => import("@/components/HomeClient"), {
  ssr: false,
  loading: () => (
    <main className="flex min-h-screen items-center justify-center text-sm text-slate-500">
      Chargement de Suivi CM2…
    </main>
  ),
});

export default function HomePage() {
  return (
    <ErrorBoundary>
      <HomeClient />
    </ErrorBoundary>
  );
}
