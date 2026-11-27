"use client";

import { Notice } from "./ui";

export function LoginScreen({
  onLogin,
  error,
  configured,
}: {
  onLogin: () => void;
  error: string | null;
  configured: boolean;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-50 via-white to-slate-100 p-6">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-2xl font-bold text-white shadow-lg shadow-brand-200">
            CM2
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">Suivi CM2</h1>
          <p className="mt-2 text-sm text-slate-600">
            40 écoles • Tests hebdomadaires et mensuels • Statistiques par genre
          </p>
        </div>

        <div className="card space-y-4">
          <h2 className="text-lg font-bold">Connexion</h2>
          <p className="text-sm text-slate-600">
            Utilisez le compte Google associé à votre rôle (superadministrateur,
            administrateur ou opérateur de saisie).
          </p>
          {!configured && (
            <Notice tone="red">
              La configuration Firebase est manquante. Copiez <code>.env.example</code> vers{" "}
              <code>.env.local</code> puis renseignez les clés de la console Firebase.
            </Notice>
          )}
          {error && <Notice tone="red">{error}</Notice>}
          <button
            type="button"
            onClick={onLogin}
            disabled={!configured}
            className="btn-primary w-full py-3"
          >
            <svg className="h-5 w-5" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#4285F4" d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.38 6.65v5.52h7.09c4.15-3.83 6.57-9.47 6.57-16.18z" />
              <path fill="#34A853" d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.1-5.52c-1.97 1.32-4.49 2.1-7.46 2.1-5.73 0-10.59-3.87-12.32-9.08H4.47v5.59A22 22 0 0 0 24 46z" />
              <path fill="#FBBC05" d="M11.68 28.17a13.2 13.2 0 0 1 0-8.34v-5.59H4.47a22 22 0 0 0 0 19.52l7.21-5.59z" />
              <path fill="#EA4335" d="M24 10.58c3.23 0 6.13 1.11 8.41 3.28l6.31-6.31C34.91 4.17 29.94 2 24 2A22 22 0 0 0 4.47 14.24l7.21 5.59C13.41 14.62 18.27 10.58 24 10.58z" />
            </svg>
            Continuer avec Google
          </button>
          <p className="text-xs text-slate-400">
            L&apos;accès est contrôlé par les règles Firestore : seuls les comptes
            autorisés peuvent lire ou écrire les données.
          </p>
        </div>
      </div>
    </main>
  );
}
