"use client";

import { AppShell } from "./AppShell";
import { firebaseInitError, missingFirebaseVars } from "@/lib/firebase";
import { useAuth } from "@/lib/useAuth";
import { useAppData } from "@/lib/useData";

function ConfigurationScreen({ error }: { error: string | null }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
      <div className="card max-w-2xl space-y-3">
        <h1 className="text-lg font-bold text-red-700">Configuration Firebase incomplète</h1>
        <p className="text-sm text-slate-600">
          Les variables d&apos;environnement Vercel doivent être renseignées puis un nouveau
          déploiement doit être effectué (les variables <code>NEXT_PUBLIC_*</code> sont figées
          au moment du build).
        </p>
        {missingFirebaseVars.length > 0 && (
          <ul className="list-inside list-disc rounded-xl bg-red-50 p-4 font-mono text-xs text-red-800">
            {missingFirebaseVars.map((name) => (
              <li key={name}>{name}</li>
            ))}
          </ul>
        )}
        {error && (
          <pre className="max-h-40 overflow-auto rounded-xl bg-slate-900 p-4 text-xs text-slate-100">
            {error}
          </pre>
        )}
        <p className="text-xs text-slate-500">
          Voir <code>web/README.md</code> pour la liste complète et la procédure.
        </p>
      </div>
    </main>
  );
}

function ReadyApp() {
  const session = useAuth();
  const data = useAppData(session);
  return <AppShell session={session} data={data} configured />;
}

export default function HomeClient() {
  if (missingFirebaseVars.length > 0 || firebaseInitError) {
    return <ConfigurationScreen error={firebaseInitError} />;
  }
  return <ReadyApp />;
}
