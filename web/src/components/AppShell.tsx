"use client";

import { useState } from "react";
import type { AuthSession } from "@/lib/useAuth";
import type { AppData } from "@/lib/useData";
import type { FilterState } from "@/lib/types";
import { DEFAULT_FILTERS } from "@/lib/types";
import { ROLE_LABELS } from "@/lib/types";
import { LoginScreen } from "./LoginScreen";
import { SynthesisTab } from "./tabs/SynthesisTab";
import { SchoolsTab } from "./tabs/SchoolsTab";
import { TestsTab } from "./tabs/TestsTab";
import { DiagnosticsTab } from "./tabs/DiagnosticsTab";
import { AccessTab } from "./tabs/AccessTab";
import { SuperAdminTab } from "./tabs/SuperAdminTab";

export type TabKey =
  | "synthesis"
  | "schools"
  | "tests"
  | "diagnostics"
  | "access"
  | "superadmin";

const TABS: Array<{ key: TabKey; label: string; adminOnly?: boolean; superOnly?: boolean }> = [
  { key: "synthesis", label: "Synthèse" },
  { key: "schools", label: "Écoles" },
  { key: "tests", label: "Tests" },
  { key: "diagnostics", label: "Diagnostics" },
  { key: "access", label: "Accès", adminOnly: true },
  { key: "superadmin", label: "Superadmin", superOnly: true },
];

export function AppShell({
  session,
  data,
  configured,
}: {
  session: AuthSession & { login: () => Promise<void>; logout: () => Promise<void> };
  data: AppData;
  configured: boolean;
}) {
  const [tab, setTab] = useState<TabKey>("synthesis");
  const [filters, setFilters] = useState<FilterState>(DEFAULT_FILTERS);

  if (session.loading) {
    return (
      <main className="flex min-h-screen items-center justify-center text-sm text-slate-500">
        Connexion à Firebase…
      </main>
    );
  }

  if (!session.user) {
    return (
      <LoginScreen
        onLogin={() => void session.login()}
        error={session.error}
        configured={configured}
      />
    );
  }

  if (session.role === "NONE") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <div className="card max-w-md space-y-3 text-center">
          <h1 className="text-lg font-bold">Accès non autorisé</h1>
          <p className="text-sm text-slate-600">
            Le compte {session.email} n&apos;est pas enregistré comme superadministrateur,
            administrateur ou opérateur de saisie. Contactez l&apos;administrateur de votre
            circonscription.
          </p>
          <button type="button" onClick={() => void session.logout()} className="btn-primary">
            Se déconnecter
          </button>
        </div>
      </main>
    );
  }

  const visibleTabs = TABS.filter((item) => {
    if (item.superOnly) return session.isSuperAdmin;
    if (item.adminOnly) return session.isAdmin;
    return true;
  });
  const activeTab = visibleTabs.some((item) => item.key === tab) ? tab : "synthesis";

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-600 text-sm font-bold text-white">
              CM2
            </div>
            <div>
              <h1 className="text-base font-bold leading-tight text-slate-900">Suivi CM2</h1>
              <p className="text-xs text-slate-500">
                {session.user.displayName || session.email} · {ROLE_LABELS[session.role]}
              </p>
            </div>
          </div>
          <button type="button" onClick={() => void session.logout()} className="btn-secondary">
            Déconnexion
          </button>
        </div>
        <nav className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 pb-2">
          {visibleTabs.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setTab(item.key)}
              className={`whitespace-nowrap rounded-lg px-3 py-2 text-sm font-semibold transition ${
                activeTab === item.key
                  ? "bg-brand-600 text-white"
                  : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              {item.label}
            </button>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-6">
        {data.loading ? (
          <div className="card text-center text-sm text-slate-500">Synchronisation des données…</div>
        ) : (
          <>
            {activeTab === "synthesis" && (
              <SynthesisTab session={session} data={data} filters={filters} onFiltersChange={setFilters} />
            )}
            {activeTab === "schools" && <SchoolsTab session={session} data={data} />}
            {activeTab === "tests" && <TestsTab session={session} data={data} />}
            {activeTab === "diagnostics" && (
              <DiagnosticsTab data={data} filters={filters} onFiltersChange={setFilters} />
            )}
            {activeTab === "access" && <AccessTab session={session} data={data} />}
            {activeTab === "superadmin" && <SuperAdminTab session={session} data={data} />}
          </>
        )}
      </main>
    </div>
  );
}
