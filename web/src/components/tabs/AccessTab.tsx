"use client";

import { useMemo, useState } from "react";
import { normalizeEmail } from "@/lib/format";
import type { DataEntryOperator } from "@/lib/types";
import type { AuthSession } from "@/lib/useAuth";
import type { AppData } from "@/lib/useData";
import { deleteOperator, saveOperator, setOperatorActive } from "@/lib/mutations";
import { Badge, Card, EmptyState, Field, Modal, Notice, SelectField } from "../ui";

export function AccessTab({ session, data }: { session: AuthSession; data: AppData }) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<DataEntryOperator | null>(null);
  const [form, setForm] = useState({ fullName: "", email: "", schoolId: "", active: true });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return data.operators;
    return data.operators.filter((op) =>
      [op.fullName, op.email, op.schoolCode, op.schoolName].some((value) =>
        value.toLowerCase().includes(needle)
      )
    );
  }, [data.operators, query]);

  const openDialog = (operator: DataEntryOperator | null) => {
    setEditing(operator);
    setForm({
      fullName: operator?.fullName ?? "",
      email: operator?.email ?? "",
      schoolId: operator?.schoolId ?? data.schools[0]?.id ?? "",
      active: operator?.active ?? true,
    });
    setError(null);
    setOpen(true);
  };

  const save = async () => {
    const school = data.schools.find((item) => item.id === form.schoolId);
    if (!school) return;
    setBusy(true);
    setError(null);
    try {
      await saveOperator(
        editing?.id ?? null,
        {
          fullName: form.fullName,
          email: normalizeEmail(form.email),
          school,
          active: form.active,
        },
        session.user!.uid,
        session.email,
        data.operators
      );
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Enregistrement impossible.");
    } finally {
      setBusy(false);
    }
  };

  const activeCount = data.operators.filter((op) => op.active).length;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Administration des accès</h2>
          <p className="text-sm text-slate-500">
            {activeCount} opérateur(s) actif(s) sur {data.operators.length}
          </p>
        </div>
        <button type="button" className="btn-primary" disabled={!data.schools.length} onClick={() => openDialog(null)}>
          + Nouvel opérateur
        </button>
      </div>
      {error && !open && <Notice tone="red">{error}</Notice>}

      <Card>
        <input
          className="input mb-4"
          placeholder="Rechercher un opérateur (nom, e-mail, école…)"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {visible.length === 0 ? (
          <EmptyState message="Aucun opérateur de saisie." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[780px]">
              <thead>
                <tr>
                  <th className="table-header">Nom</th>
                  <th className="table-header">E-mail Google</th>
                  <th className="table-header">École</th>
                  <th className="table-header">Statut</th>
                  <th className="table-header">Action</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((operator) => (
                  <tr key={operator.id}>
                    <td className="table-cell font-medium">{operator.fullName}</td>
                    <td className="table-cell">{operator.email || "—"}</td>
                    <td className="table-cell">
                      {operator.schoolCode} • {operator.schoolName}
                    </td>
                    <td className="table-cell">
                      <label className="flex cursor-pointer items-center gap-2">
                        <input
                          type="checkbox"
                          checked={operator.active}
                          onChange={(event) =>
                            void setOperatorActive(operator, event.target.checked, data.operators).catch(
                              (cause: unknown) =>
                                setError(cause instanceof Error ? cause.message : "Mise à jour impossible.")
                            )
                          }
                          className="h-4 w-4 accent-brand-600"
                        />
                        <Badge tone={operator.active ? "green" : "red"}>
                          {operator.active ? "Actif" : "Suspendu"}
                        </Badge>
                      </label>
                    </td>
                    <td className="table-cell">
                      <div className="flex gap-3">
                        <button type="button" className="text-sm font-semibold text-brand-600 hover:underline" onClick={() => openDialog(operator)}>Modifier</button>
                        <button type="button" className="text-sm font-semibold text-red-600 hover:underline" onClick={() => void deleteOperator(operator, data.operators)}>Supprimer</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2 className="mb-3 text-base font-bold">Codes d&apos;accès par école</h2>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {data.schools.map((school) => (
            <div key={school.id} className="rounded-xl border border-slate-100 px-3 py-2">
              <p className="text-sm font-semibold">{school.code} • {school.name}</p>
              <p className="mt-1 text-xs text-slate-500">
                {data.operators.filter((op) => op.schoolId === school.id && op.active).length} agent(s) actif(s)
              </p>
              <Badge tone={school.accessCodeHash ? "green" : "amber"}>
                {school.accessCodeHash ? "Protégée par code" : "Sans code"}
              </Badge>
            </div>
          ))}
        </div>
      </Card>

      <Modal open={open} title={editing ? "Modifier l'opérateur" : "Nouvel opérateur"} onClose={() => setOpen(false)}>
        <div className="space-y-4">
          <Field label="Nom complet" value={form.fullName} onChange={(value) => setForm((current) => ({ ...current, fullName: value }))} required />
          <Field label="E-mail Google" value={form.email} onChange={(value) => setForm((current) => ({ ...current, email: value }))} placeholder="saisie.ecole01@gmail.com" />
          <SelectField
            label="École assignée"
            value={form.schoolId}
            onChange={(value) => setForm((current) => ({ ...current, schoolId: value }))}
            options={data.schools.map((school) => ({ value: school.id, label: `${school.code} • ${school.name}` }))}
          />
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
              className="h-4 w-4 accent-brand-600"
            />
            Compte actif
          </label>
          {error && <Notice tone="red">{error}</Notice>}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setOpen(false)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={busy || !form.fullName.trim()} onClick={() => void save()}>
            {busy ? "Enregistrement…" : "Enregistrer"}
          </button>
        </div>
      </Modal>
    </div>
  );
}
