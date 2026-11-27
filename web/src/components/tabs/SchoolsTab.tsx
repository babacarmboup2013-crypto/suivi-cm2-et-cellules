"use client";

import { useState } from "react";
import type { Gender, School, Student } from "@/lib/types";
import type { AuthSession } from "@/lib/useAuth";
import type { AppData } from "@/lib/useData";
import {
  deleteSchool,
  deleteStudent,
  saveSchool,
  saveStudent,
} from "@/lib/mutations";
import type { GeneratedSchoolCode } from "@/lib/types";
import { Badge, Card, EmptyState, Field, Modal, Notice, SelectField } from "../ui";

interface SchoolForm {
  code: string;
  name: string;
  className: string;
  zone: string;
  teacherName: string;
  boysEnrolled: string;
  girlsEnrolled: string;
  accessCode: string;
}

const emptyForm: SchoolForm = {
  code: "",
  name: "",
  className: "CM2 A",
  zone: "",
  teacherName: "",
  boysEnrolled: "0",
  girlsEnrolled: "0",
  accessCode: "",
};

export function SchoolsTab({ session, data }: { session: AuthSession; data: AppData }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<School | null>(null);
  const [form, setForm] = useState<SchoolForm>(emptyForm);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [generated, setGenerated] = useState<GeneratedSchoolCode[]>([]);

  const [studentsFor, setStudentsFor] = useState<School | null>(null);
  const [editingStudent, setEditingStudent] = useState<Student | null>(null);
  const [studentForm, setStudentForm] = useState({ fullName: "", gender: "GARCON" as Gender, matricule: "" });

  const openDialog = (school: School | null) => {
    setEditing(school);
    setForm(
      school
        ? {
            code: school.code,
            name: school.name,
            className: school.className,
            zone: school.zone,
            teacherName: school.teacherName,
            boysEnrolled: String(school.boysEnrolled),
            girlsEnrolled: String(school.girlsEnrolled),
            accessCode: "",
          }
        : { ...emptyForm, code: `EC-${String(data.schools.length + 1).padStart(2, "0")}` }
    );
    setError(null);
    setOpen(true);
  };

  const set = (key: keyof SchoolForm) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }));

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await saveSchool(
        {
          code: form.code,
          name: form.name,
          className: form.className,
          zone: form.zone,
          teacherName: form.teacherName,
          boysEnrolled: Number(form.boysEnrolled) || 0,
          girlsEnrolled: Number(form.girlsEnrolled) || 0,
          accessCode: form.accessCode.trim() || undefined,
        },
        editing,
        editing?.adminUid || data.ownerUid || session.user!.uid,
        editing?.adminEmail || data.ownerEmail || session.email
      );
      setGenerated(result ? [result] : []);
      setOpen(false);
      setMessage(editing ? "École mise à jour." : "École créée.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Enregistrement impossible.");
    } finally {
      setBusy(false);
    }
  };

  const removeSchool = async (school: School) => {
    if (!window.confirm(`Supprimer l'école ${school.code} • ${school.name} ?`)) return;
    try {
      await deleteSchool(school.id);
      setMessage("École supprimée.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Suppression impossible.");
    }
  };

  const openStudents = (school: School) => {
    setStudentsFor(school);
    setEditingStudent(null);
    setStudentForm({ fullName: "", gender: "GARCON", matricule: "" });
    setError(null);
  };

  const saveStudentRecord = async () => {
    if (!studentsFor) return;
    setBusy(true);
    setError(null);
    try {
      await saveStudent(
        editingStudent?.id ?? null,
        {
          fullName: studentForm.fullName,
          gender: studentForm.gender,
          matricule: studentForm.matricule,
          school: studentsFor,
        },
        session.user!.uid,
        session.email
      );
      setEditingStudent(null);
      setStudentForm({ fullName: "", gender: "GARCON", matricule: "" });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Enregistrement impossible.");
    } finally {
      setBusy(false);
    }
  };

  const students = studentsFor
    ? data.students.filter((student) => student.schoolId === studentsFor.id)
    : [];

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Écoles CM2</h2>
          <p className="text-sm text-slate-500">
            {data.schools.length} école(s) ·{" "}
            {data.schools.reduce((sum, school) => sum + school.boysEnrolled + school.girlsEnrolled, 0)}{" "}
            élève(s) inscrit(s)
          </p>
        </div>
        {session.isAdmin && (
          <button type="button" className="btn-primary" onClick={() => openDialog(null)}>
            + Nouvelle école
          </button>
        )}
      </div>
      {message && <Notice tone="green">{message}</Notice>}
      {error && !open && !studentsFor && <Notice tone="red">{error}</Notice>}
      {generated.length > 0 && (
        <Notice tone="green">
          <p className="font-semibold">Code d&apos;accès généré — à noter maintenant, il n&apos;est plus affiché ensuite :</p>
          {generated.map((item) => (
            <p key={item.schoolCode} className="mt-1 font-mono text-lg font-bold">
              {item.schoolCode} • {item.schoolName} : {item.accessCode}
            </p>
          ))}
          <button type="button" className="btn-secondary mt-2 text-xs" onClick={() => setGenerated([])}>
            J&apos;ai noté les codes
          </button>
        </Notice>
      )}

      <Card>
        {data.schools.length === 0 ? (
          <EmptyState message="Aucune école. Créez la première école pour commencer." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px]">
              <thead>
                <tr>
                  <th className="table-header">Code</th>
                  <th className="table-header">Nom</th>
                  <th className="table-header">Classe</th>
                  <th className="table-header">Zone</th>
                  <th className="table-header">Effectif</th>
                  <th className="table-header">Accès</th>
                  {session.isAdmin && <th className="table-header">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {data.schools.map((school) => (
                  <tr key={school.id}>
                    <td className="table-cell font-semibold">{school.code}</td>
                    <td className="table-cell">{school.name}</td>
                    <td className="table-cell">{school.className}</td>
                    <td className="table-cell">{school.zone || "—"}</td>
                    <td className="table-cell">
                      {school.boysEnrolled} G / {school.girlsEnrolled} F
                    </td>
                    <td className="table-cell">
                      <Badge tone={school.accessCodeHash ? "green" : "amber"}>
                        {school.accessCodeHash ? "Code actif" : "Sans code"}
                      </Badge>
                    </td>
                    {session.isAdmin && (
                      <td className="table-cell">
                        <div className="flex flex-wrap gap-3">
                          <button type="button" className="text-sm font-semibold text-brand-600 hover:underline" onClick={() => openDialog(school)}>
                            Modifier
                          </button>
                          <button type="button" className="text-sm font-semibold text-brand-600 hover:underline" onClick={() => openStudents(school)}>
                            Élèves ({data.students.filter((s) => s.schoolId === school.id).length})
                          </button>
                          <button type="button" className="text-sm font-semibold text-red-600 hover:underline" onClick={() => void removeSchool(school)}>
                            Supprimer
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal
        open={open}
        title={editing ? "Modifier l'école" : "Nouvelle école"}
        description="Un code confidentiel est généré et haché (PBKDF2-SHA256) à la création, comme dans l'application Android."
        onClose={() => setOpen(false)}
        wide
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Code" value={form.code} onChange={set("code")} required />
          <Field label="Nom de l'école" value={form.name} onChange={set("name")} required />
          <Field label="Classe" value={form.className} onChange={set("className")} />
          <Field label="Zone" value={form.zone} onChange={set("zone")} />
          <Field label="Enseignant" value={form.teacherName} onChange={set("teacherName")} />
          <Field
            label={editing ? "Nouveau code d'accès (optionnel)" : "Code d'accès (optionnel)"}
            value={form.accessCode}
            onChange={set("accessCode")}
            placeholder="4 à 16 caractères alphanumériques"
          />
          <Field label="Garçons inscrits" type="number" value={form.boysEnrolled} onChange={set("boysEnrolled")} min={0} max={300} />
          <Field label="Filles inscrites" type="number" value={form.girlsEnrolled} onChange={set("girlsEnrolled")} min={0} max={300} />
        </div>
        {error && <div className="mt-4"><Notice tone="red">{error}</Notice></div>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setOpen(false)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={busy || !form.code.trim() || !form.name.trim()} onClick={() => void save()}>
            {busy ? "Enregistrement…" : "Enregistrer"}
          </button>
        </div>
      </Modal>

      <Modal
        open={Boolean(studentsFor)}
        title={studentsFor ? `Élèves — ${studentsFor.code} • ${studentsFor.name}` : "Élèves"}
        description="Les élèves mémorisés permettent de sélectionner un nom lors de la saisie d'un test."
        onClose={() => setStudentsFor(null)}
        wide
      >
        {students.length === 0 ? (
          <EmptyState message="Aucun élève mémorisé dans cette école." />
        ) : (
          <div className="mb-4 space-y-2">
            {students.map((student) => (
              <div key={student.id} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2">
                <span className="text-sm">
                  <strong>{student.fullName}</strong> · {student.gender === "GARCON" ? "Garçon" : "Fille"}
                  {student.matricule ? ` · ${student.matricule}` : ""}
                </span>
                <div className="flex gap-3">
                  <button
                    type="button"
                    className="text-sm font-semibold text-brand-600 hover:underline"
                    onClick={() => {
                      setEditingStudent(student);
                      setStudentForm({ fullName: student.fullName, gender: student.gender, matricule: student.matricule });
                    }}
                  >
                    Modifier
                  </button>
                  <button
                    type="button"
                    className="text-sm font-semibold text-red-600 hover:underline"
                    onClick={() => void deleteStudent(student.id)}
                  >
                    Supprimer
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="rounded-xl border border-slate-200 p-4">
          <h3 className="mb-3 text-sm font-bold">
            {editingStudent ? "Modifier l'élève" : "Ajouter un élève"}
          </h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Nom complet" value={studentForm.fullName} onChange={(value) => setStudentForm((current) => ({ ...current, fullName: value }))} required />
            <Field label="Matricule" value={studentForm.matricule} onChange={(value) => setStudentForm((current) => ({ ...current, matricule: value }))} />
            <SelectField
              label="Genre"
              value={studentForm.gender}
              onChange={(value) => setStudentForm((current) => ({ ...current, gender: value as Gender }))}
              options={[{ value: "GARCON", label: "Garçon" }, { value: "FILLE", label: "Fille" }]}
            />
          </div>
          {error && <div className="mt-3"><Notice tone="red">{error}</Notice></div>}
          <div className="mt-4 flex justify-end gap-2">
            {editingStudent && (
              <button type="button" className="btn-secondary" onClick={() => setEditingStudent(null)}>Annuler</button>
            )}
            <button type="button" className="btn-primary" disabled={busy || !studentForm.fullName.trim()} onClick={() => void saveStudentRecord()}>
              {busy ? "Enregistrement…" : "Enregistrer"}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
