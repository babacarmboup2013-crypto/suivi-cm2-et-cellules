"use client";

import { useMemo, useState } from "react";
import { SCHOOL_MONTHS, BAREMES } from "@/lib/constants";
import { formatNumber } from "@/lib/format";
import { periodLabel, DECISION_LABELS, type Assessment, type Gender, type TestType } from "@/lib/types";
import type { AuthSession } from "@/lib/useAuth";
import type { AppData } from "@/lib/useData";
import { saveAssessment, deleteAssessment } from "@/lib/mutations";
import { Badge, Card, EmptyState, Field, Modal, Notice, SelectField } from "../ui";

const SCORE_FIELDS = [
  { key: "lcRessources", label: "LC — Ressources (/40)", max: 40 },
  { key: "lcCompetences", label: "LC — Compétences (/60)", max: 60 },
  { key: "mathRessources", label: "Maths — Ressources (/40)", max: 40 },
  { key: "mathCompetences", label: "Maths — Compétences (/60)", max: 60 },
  { key: "dmRessources", label: "DM — Ressources (/24)", max: 24 },
  { key: "dmCompetences", label: "DM — Compétences (/16)", max: 16 },
  { key: "ddRessources", label: "DD — Ressources (/24)", max: 24 },
  { key: "ddCompetences", label: "DD — Compétences (/16)", max: 16 },
] as const;

type ScoreKey = (typeof SCORE_FIELDS)[number]["key"];

interface TestForm {
  schoolId: string;
  studentId: string;
  studentName: string;
  gender: Gender;
  testType: TestType;
  month: string;
  weekNumber: string;
  scores: Record<ScoreKey, string>;
}

const emptyScores = Object.fromEntries(SCORE_FIELDS.map((field) => [field.key, "0"])) as Record<ScoreKey, string>;

export function TestsTab({ session, data }: { session: AuthSession; data: AppData }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Assessment | null>(null);
  const [form, setForm] = useState<TestForm>({
    schoolId: data.schools[0]?.id ?? "",
    studentId: "",
    studentName: "",
    gender: "GARCON",
    testType: "HEBDOMADAIRE",
    month: SCHOOL_MONTHS[1],
    weekNumber: "1",
    scores: { ...emptyScores },
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return data.assessments;
    return data.assessments.filter((item) =>
      [item.studentName, item.schoolName, item.className, item.month, periodLabel(item)].some(
        (value) => value.toLowerCase().includes(needle)
      )
    );
  }, [data.assessments, query]);

  const openDialog = (assessment: Assessment | null) => {
    setEditing(assessment);
    if (assessment) {
      setForm({
        schoolId: assessment.schoolId,
        studentId: assessment.studentId,
        studentName: assessment.studentName,
        gender: assessment.gender,
        testType: assessment.testType,
        month: assessment.month,
        weekNumber: String(assessment.weekNumber),
        scores: Object.fromEntries(SCORE_FIELDS.map((field) => [field.key, String(assessment[field.key])])) as Record<ScoreKey, string>,
      });
    } else {
      setForm({
        schoolId: data.schools[0]?.id ?? "",
        studentId: "",
        studentName: "",
        gender: "GARCON",
        testType: "HEBDOMADAIRE",
        month: SCHOOL_MONTHS[1],
        weekNumber: "1",
        scores: { ...emptyScores },
      });
    }
    setError(null);
    setOpen(true);
  };

  const setScore = (key: ScoreKey) => (value: string) =>
    setForm((current) => ({ ...current, scores: { ...current.scores, [key]: value } }));

  const save = async () => {
    const school = data.schools.find((item) => item.id === form.schoolId);
    if (!school) return;
    setBusy(true);
    setError(null);
    try {
      const createdStudent = await saveAssessment(
        editing?.id ?? null,
        {
          school,
          studentId: form.studentId,
          studentName: form.studentName,
          gender: form.gender,
          testType: form.testType,
          month: form.month,
          weekNumber: form.testType === "MENSUEL" ? 0 : Number(form.weekNumber),
          lcRessources: Number(form.scores.lcRessources),
          lcCompetences: Number(form.scores.lcCompetences),
          mathRessources: Number(form.scores.mathRessources),
          mathCompetences: Number(form.scores.mathCompetences),
          dmRessources: Number(form.scores.dmRessources),
          dmCompetences: Number(form.scores.dmCompetences),
          ddRessources: Number(form.scores.ddRessources),
          ddCompetences: Number(form.scores.ddCompetences),
          settings: data.settings,
          recordedBy: session.user?.displayName || session.email,
          students: data.students,
        },
        session.user!.uid,
        session.email
      );
      setOpen(false);
      setMessage(
        createdStudent
          ? "Test enregistré et nouvel élève mémorisé."
          : editing
            ? "Test mis à jour."
            : "Test enregistré."
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Enregistrement impossible.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (assessment: Assessment) => {
    if (!window.confirm(`Supprimer le test de ${assessment.studentName} ?`)) return;
    setError(null);
    try {
      await deleteAssessment(assessment.id);
      setMessage("Test supprimé.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Suppression impossible.");
    }
  };

  const total = SCORE_FIELDS.reduce((sum, field) => sum + (Number(form.scores[field.key]) || 0), 0);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Tests enregistrés</h2>
          <p className="text-sm text-slate-500">{data.assessments.length} évaluation(s)</p>
        </div>
        <button type="button" className="btn-primary" disabled={!data.schools.length} onClick={() => openDialog(null)}>
          + Nouveau test
        </button>
      </div>
      {message && <Notice tone="green">{message}</Notice>}
      {error && !open && <Notice tone="red">{error}</Notice>}

      <Card>
        <input
          className="input mb-4"
          placeholder="Rechercher un test (élève, école, mois…)"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {visible.length === 0 ? (
          <EmptyState message={data.assessments.length ? "Aucun test ne correspond à la recherche." : "Aucun test enregistré."} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[840px]">
              <thead>
                <tr>
                  <th className="table-header">École</th>
                  <th className="table-header">Élève</th>
                  <th className="table-header">Période</th>
                  <th className="table-header">Total</th>
                  <th className="table-header">Décision</th>
                  <th className="table-header">Saisi par</th>
                  <th className="table-header">Action</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((assessment) => (
                  <tr key={assessment.id}>
                    <td className="table-cell">{assessment.schoolName}</td>
                    <td className="table-cell font-medium">{assessment.studentName}</td>
                    <td className="table-cell">{periodLabel(assessment)}</td>
                    <td className="table-cell font-bold">{formatNumber(assessment.totalScore)}</td>
                    <td className="table-cell">
                      <Badge tone={
                        assessment.decision === "MAITRISE_MAXIMALE" ? "green" :
                        assessment.decision === "MAITRISE_MINIMALE" ? "blue" :
                        assessment.decision === "A_AMELIORER" ? "amber" : "red"
                      }>
                        {DECISION_LABELS[assessment.decision]}
                      </Badge>
                    </td>
                    <td className="table-cell">{assessment.recordedBy || "—"}</td>
                    <td className="table-cell">
                      <div className="flex gap-3">
                        <button type="button" className="text-sm font-semibold text-brand-600 hover:underline" onClick={() => openDialog(assessment)}>Modifier</button>
                        <button type="button" className="text-sm font-semibold text-red-600 hover:underline" onClick={() => void remove(assessment)}>Supprimer</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal open={open} title={editing ? "Modifier le test" : "Nouveau test"} onClose={() => setOpen(false)} wide>
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label="École"
            value={form.schoolId}
            onChange={(value) => setForm((current) => ({ ...current, schoolId: value }))}
            options={data.schools.map((school) => ({ value: school.id, label: `${school.code} • ${school.name}` }))}
          />
          <SelectField
            label="Élève existant (optionnel)"
            value={form.studentId}
            onChange={(value) => {
              const student = data.students.find((item) => item.id === value);
              setForm((current) => ({
                ...current,
                studentId: value,
                studentName: student?.fullName ?? current.studentName,
                gender: student?.gender ?? current.gender,
              }));
            }}
            options={[
              { value: "", label: "Nouvel élève" },
              ...data.students
                .filter((student) => student.schoolId === form.schoolId)
                .map((student) => ({ value: student.id, label: student.fullName })),
            ]}
          />
          <Field label="Nom de l'élève" value={form.studentName} onChange={(value) => setForm((current) => ({ ...current, studentName: value }))} required />
          <SelectField
            label="Genre"
            value={form.gender}
            onChange={(value) => setForm((current) => ({ ...current, gender: value as Gender }))}
            options={[{ value: "GARCON", label: "Garçon" }, { value: "FILLE", label: "Fille" }]}
          />
          <SelectField
            label="Type de test"
            value={form.testType}
            onChange={(value) => setForm((current) => ({ ...current, testType: value as TestType }))}
            options={[{ value: "HEBDOMADAIRE", label: "Test Hebdomadaire" }, { value: "MENSUEL", label: "Grand Test Mensuel" }]}
          />
          <SelectField
            label="Mois"
            value={form.month}
            onChange={(value) => setForm((current) => ({ ...current, month: value }))}
            options={SCHOOL_MONTHS.map((month) => ({ value: month, label: month }))}
          />
          <Field
            label={form.testType === "MENSUEL" ? "Semaine (non utilisée)" : "Semaine"}
            type="number"
            min={1}
            max={4}
            value={form.testType === "MENSUEL" ? "0" : form.weekNumber}
            onChange={(value) => setForm((current) => ({ ...current, weekNumber: value }))}
          />
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          {SCORE_FIELDS.map((field) => (
            <Field
              key={field.key}
              label={field.label}
              type="number"
              min={0}
              max={field.max}
              value={form.scores[field.key]}
              onChange={setScore(field.key)}
            />
          ))}
        </div>

        <div className="mt-4 flex items-center justify-between rounded-xl bg-brand-50 px-4 py-3">
          <span className="text-sm font-semibold text-brand-800">Total : {formatNumber(total)} / {BAREMES.TOTAL_MAX}</span>
          <span className="text-xs text-brand-700">La décision est calculée automatiquement.</span>
        </div>

        {error && <div className="mt-4"><Notice tone="red">{error}</Notice></div>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setOpen(false)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={busy || !form.studentName.trim()} onClick={() => void save()}>
            {busy ? "Enregistrement…" : "Enregistrer"}
          </button>
        </div>
      </Modal>
    </div>
  );
}
