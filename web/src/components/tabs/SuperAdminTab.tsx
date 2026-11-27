"use client";

import { useMemo, useState } from "react";
import { normalizeEmail } from "@/lib/format";
import type { AdministratorAccount, GeneratedSchoolCode, School } from "@/lib/types";
import type { AuthSession } from "@/lib/useAuth";
import type { AppData } from "@/lib/useData";
import {
  bootstrapSchools,
  createAdministrator,
  generateMissingCodes,
  saveOperator,
  saveSchoolForAdmin,
  setAdministratorActive,
} from "@/lib/mutations";
import { Badge, Card, EmptyState, Field, Modal, Notice, SelectField } from "../ui";

type Dialog = "administrator" | "school" | "operator" | null;

export function SuperAdminTab({ session, data }: { session: AuthSession; data: AppData }) {
  const [dialog, setDialog] = useState<Dialog>(null);
  const [selectedAdmin, setSelectedAdmin] = useState<AdministratorAccount | null>(null);
  const [selectedSchool, setSelectedSchool] = useState<School | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generated, setGenerated] = useState<GeneratedSchoolCode[]>([]);

  const [adminForm, setAdminForm] = useState({ email: "", fullName: "" });
  const [schoolForm, setSchoolForm] = useState({
    code: "", name: "", className: "CM2 A", zone: "", teacherName: "", boys: "0", girls: "0", accessCode: "",
  });
  const [operatorForm, setOperatorForm] = useState({ fullName: "", email: "", active: true });

  const schoolCountByAdmin = useMemo(() => {
    const map = new Map<string, number>();
    data.schools.forEach((school) => {
      const key = normalizeEmail(school.adminEmail || school.adminUid);
      map.set(key, (map.get(key) ?? 0) + 1);
    });
    return map;
  }, [data.schools]);

  const openSchoolDialog = (admin: AdministratorAccount) => {
    setSelectedAdmin(admin);
    setSchoolForm({
      code: `EC-${String(data.schools.length + 1).padStart(2, "0")}`,
      name: "", className: "CM2 A", zone: "", teacherName: "", boys: "0", girls: "0", accessCode: "",
    });
    setError(null);
    setDialog("school");
  };

  const openOperatorDialog = (school: School) => {
    setSelectedSchool(school);
    setOperatorForm({ fullName: "", email: "", active: true });
    setError(null);
    setDialog("operator");
  };

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      let generatedForSchool: GeneratedSchoolCode[] = [];
      if (dialog === "administrator") {
        await createAdministrator(adminForm.email, adminForm.fullName, session.user!.uid);
      } else if (dialog === "school" && selectedAdmin) {
        const result = await saveSchoolForAdmin(
          selectedAdmin.uid || selectedAdmin.email,
          selectedAdmin.email,
          {
            code: schoolForm.code,
            name: schoolForm.name,
            className: schoolForm.className,
            zone: schoolForm.zone,
            teacherName: schoolForm.teacherName,
            boysEnrolled: Number(schoolForm.boys) || 0,
            girlsEnrolled: Number(schoolForm.girls) || 0,
            accessCode: schoolForm.accessCode || undefined,
          },
          session.user!.uid,
          session.email
        );
        generatedForSchool = result ? [result] : [];
      } else if (dialog === "operator" && selectedSchool) {
        await saveOperator(
          null,
          {
            fullName: operatorForm.fullName,
            email: normalizeEmail(operatorForm.email),
            school: selectedSchool,
            active: operatorForm.active,
          },
          session.user!.uid,
          session.email,
          data.operators
        );
      }
      setGenerated(dialog === "school" ? generatedForSchool : []);
      setDialog(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Opération impossible.");
    } finally {
      setBusy(false);
    }
  };

  const runBulk = async (mode: "bootstrap" | "codes") => {
    setBusy(true);
    setError(null);
    try {
      const result =
        mode === "bootstrap"
          ? await bootstrapSchools(
              session.user!.uid,
              session.email,
              data.schools.map((school) => school.code)
            )
          : await generateMissingCodes(data.schools);
      setGenerated(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Opération impossible.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-5">
      <Card className="bg-gradient-to-br from-brand-50 to-white">
        <h2 className="text-xl font-bold">Espace Superadministrateur</h2>
        <p className="mt-1 text-sm text-slate-600">
          {data.administrators.length} administrateur(s) · {data.schools.length} école(s) · {data.operators.length} opérateur(s)
        </p>
        <p className="mt-2 text-sm text-slate-500">
          Vous créez les administrateurs. Chaque administrateur gère ensuite ses écoles,
          ses élèves et ses opérateurs. Vous pouvez aussi créer une école ou un opérateur
          directement pour un administrateur.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" className="btn-primary" onClick={() => { setAdminForm({ email: "", fullName: "" }); setError(null); setDialog("administrator"); }}>
            + Nouvel administrateur
          </button>
          <button type="button" className="btn-secondary" disabled={busy} onClick={() => void runBulk("bootstrap")}>
            Initialiser / compléter 40 écoles
          </button>
          <button type="button" className="btn-secondary" disabled={busy} onClick={() => void runBulk("codes")}>
            Générer les codes manquants
          </button>
        </div>
      </Card>

      {generated.length > 0 && (
        <Notice tone="green">
          <p className="font-semibold">Codes d&apos;accès générés — à noter maintenant, ils ne sont plus affichés ensuite :</p>
          <div className="mt-2 max-h-52 space-y-1 overflow-y-auto font-mono text-sm">
            {generated.map((item) => (
              <p key={item.schoolCode}>
                {item.schoolCode} • {item.schoolName} : <strong>{item.accessCode}</strong>
              </p>
            ))}
          </div>
          <button type="button" className="btn-secondary mt-2 text-xs" onClick={() => setGenerated([])}>
            J&apos;ai noté les codes
          </button>
        </Notice>
      )}
      {error && <Notice tone="red">{error}</Notice>}

      <Card>
        <h3 className="mb-3 text-base font-bold">Administrateurs</h3>
        {data.administrators.length === 0 ? (
          <EmptyState message="Aucun administrateur créé pour le moment." />
        ) : (
          <div className="space-y-3">
            {data.administrators.map((admin) => (
              <div key={admin.id} className="rounded-xl border border-slate-100 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{admin.fullName || admin.email}</p>
                    <p className="text-sm text-slate-500">{admin.email}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {schoolCountByAdmin.get(normalizeEmail(admin.email)) ?? 0} école(s) rattachée(s)
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone={!admin.active ? "red" : admin.uid ? "green" : "amber"}>
                      {!admin.active ? "Suspendu" : admin.uid ? "Compte activé" : "En attente de connexion"}
                    </Badge>
                    <button type="button" className="btn-secondary text-xs" onClick={() => openSchoolDialog(admin)}>+ École</button>
                    <button
                      type="button"
                      className="btn-secondary text-xs"
                      onClick={() =>
                        void setAdministratorActive(admin.email, !admin.active).catch(
                          (cause: unknown) =>
                            setError(cause instanceof Error ? cause.message : "Mise à jour impossible.")
                        )
                      }
                    >
                      {admin.active ? "Suspendre" : "Réactiver"}
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <h3 className="mb-3 text-base font-bold">Écoles et opérateurs de saisie</h3>
        {data.schools.length === 0 ? (
          <EmptyState message="Aucune école." />
        ) : (
          <div className="space-y-2">
            {data.schools.map((school) => (
              <div key={school.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-100 px-3 py-2">
                <div>
                  <p className="text-sm font-semibold">{school.code} • {school.name}</p>
                  <p className="text-xs text-slate-500">
                    Admin : {school.adminEmail || school.adminUid || "non assigné"} ·{" "}
                    {data.operators.filter((op) => op.schoolId === school.id).length} opérateur(s)
                  </p>
                </div>
                <button type="button" className="btn-secondary text-xs" onClick={() => openOperatorDialog(school)}>
                  + Opérateur
                </button>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Modal
        open={dialog === "administrator"}
        title="Nouvel administrateur"
        description="L'e-mail Google sert d'identifiant lors de la première connexion."
        onClose={() => setDialog(null)}
      >
        <div className="space-y-4">
          <Field label="Nom complet" value={adminForm.fullName} onChange={(value) => setAdminForm((current) => ({ ...current, fullName: value }))} required />
          <Field label="E-mail Google" value={adminForm.email} onChange={(value) => setAdminForm((current) => ({ ...current, email: value.toLowerCase() }))} required />
          {error && <Notice tone="red">{error}</Notice>}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setDialog(null)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={busy || !adminForm.email.includes("@") || !adminForm.fullName.trim()} onClick={() => void submit()}>
            {busy ? "Création…" : "Créer"}
          </button>
        </div>
      </Modal>

      <Modal
        open={dialog === "school"}
        title="Créer une école pour cet administrateur"
        description={selectedAdmin ? `Administrateur : ${selectedAdmin.fullName || selectedAdmin.email}` : undefined}
        onClose={() => setDialog(null)}
        wide
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Code" value={schoolForm.code} onChange={(value) => setSchoolForm((current) => ({ ...current, code: value }))} required />
          <Field label="Nom de l'école" value={schoolForm.name} onChange={(value) => setSchoolForm((current) => ({ ...current, name: value }))} required />
          <Field label="Classe" value={schoolForm.className} onChange={(value) => setSchoolForm((current) => ({ ...current, className: value }))} />
          <Field label="Zone" value={schoolForm.zone} onChange={(value) => setSchoolForm((current) => ({ ...current, zone: value }))} />
          <Field label="Enseignant" value={schoolForm.teacherName} onChange={(value) => setSchoolForm((current) => ({ ...current, teacherName: value }))} />
          <Field label="Code d'accès (optionnel)" value={schoolForm.accessCode} onChange={(value) => setSchoolForm((current) => ({ ...current, accessCode: value }))} />
          <Field label="Garçons" type="number" value={schoolForm.boys} onChange={(value) => setSchoolForm((current) => ({ ...current, boys: value }))} />
          <Field label="Filles" type="number" value={schoolForm.girls} onChange={(value) => setSchoolForm((current) => ({ ...current, girls: value }))} />
        </div>
        {error && <div className="mt-4"><Notice tone="red">{error}</Notice></div>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setDialog(null)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={busy || !schoolForm.code.trim() || !schoolForm.name.trim()} onClick={() => void submit()}>
            {busy ? "Création…" : "Créer l'école"}
          </button>
        </div>
      </Modal>

      <Modal
        open={dialog === "operator"}
        title="Ajouter un opérateur pour cette école"
        description={selectedSchool ? `${selectedSchool.code} • ${selectedSchool.name}` : undefined}
        onClose={() => setDialog(null)}
      >
        <div className="space-y-4">
          <Field label="Nom complet" value={operatorForm.fullName} onChange={(value) => setOperatorForm((current) => ({ ...current, fullName: value }))} required />
          <Field label="E-mail Google" value={operatorForm.email} onChange={(value) => setOperatorForm((current) => ({ ...current, email: value.toLowerCase() }))} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={operatorForm.active} onChange={(event) => setOperatorForm((current) => ({ ...current, active: event.target.checked }))} className="h-4 w-4 accent-brand-600" />
            Compte actif
          </label>
          {error && <Notice tone="red">{error}</Notice>}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setDialog(null)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={busy || !operatorForm.fullName.trim()} onClick={() => void submit()}>
            {busy ? "Création…" : "Créer l'opérateur"}
          </button>
        </div>
      </Modal>
    </div>
  );
}
