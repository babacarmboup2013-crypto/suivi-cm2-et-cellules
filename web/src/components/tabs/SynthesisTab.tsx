"use client";

import { useMemo, useState } from "react";
import type { AppData } from "@/lib/useData";
import type { AuthSession } from "@/lib/useAuth";
import type { FilterState } from "@/lib/types";
import { computeSynthesis, STATUS_LABELS } from "@/lib/stats";
import { formatNumber, formatPercent } from "@/lib/format";
import { DECISION_LABELS } from "@/lib/types";
import { saveSettings } from "@/lib/mutations";
import { BAREMES } from "@/lib/constants";
import { Badge, Card, EmptyState, Field, Modal, Notice, StatCard } from "../ui";
import { applyFilters, FilterBar, trendScope } from "../FilterBar";

const toneForStatus = (status: string) =>
  status === "POINT_FORT" ? "green" : status === "A_AMELIORER" ? "amber" : "red";

export function SynthesisTab({
  session,
  data,
  filters,
  onFiltersChange,
}: {
  session: AuthSession;
  data: AppData;
  filters: FilterState;
  onFiltersChange: (filters: FilterState) => void;
}) {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsForm, setSettingsForm] = useState({
    maxMasteryPct: String(data.settings.maxMasteryPct),
    minMasteryPct: String(data.settings.minMasteryPct),
    partialMasteryPct: String(data.settings.partialMasteryPct),
    academicYear: data.settings.academicYear,
    districtName: data.settings.districtName,
  });
  const [settingsBusy, setSettingsBusy] = useState(false);
  const [settingsError, setSettingsError] = useState<string | null>(null);

  const synthesis = useMemo(() => {
    const filtered = applyFilters(data.assessments, filters);
    return computeSynthesis(
      data.schools,
      filtered,
      trendScope(data.assessments, filters),
      data.settings
    );
  }, [data, filters]);

  const openSettings = () => {
    setSettingsForm({
      maxMasteryPct: String(data.settings.maxMasteryPct),
      minMasteryPct: String(data.settings.minMasteryPct),
      partialMasteryPct: String(data.settings.partialMasteryPct),
      academicYear: data.settings.academicYear,
      districtName: data.settings.districtName,
    });
    setSettingsError(null);
    setSettingsOpen(true);
  };

  const saveThresholds = async () => {
    setSettingsBusy(true);
    setSettingsError(null);
    try {
      await saveSettings(
        data.ownerUid || session.user!.uid,
        session.email,
        {
          ...data.settings,
          maxMasteryPct: Number(settingsForm.maxMasteryPct),
          minMasteryPct: Number(settingsForm.minMasteryPct),
          partialMasteryPct: Number(settingsForm.partialMasteryPct),
          academicYear: settingsForm.academicYear,
          districtName: settingsForm.districtName,
        }
      );
      setSettingsOpen(false);
    } catch (cause) {
      setSettingsError(cause instanceof Error ? cause.message : "Enregistrement impossible.");
    } finally {
      setSettingsBusy(false);
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Synthèse</h2>
          <p className="text-sm text-slate-500">
            {data.settings.districtName} · Année {data.settings.academicYear} · Seuils {data.settings.maxMasteryPct} / {data.settings.minMasteryPct} / {data.settings.partialMasteryPct} %
          </p>
        </div>
        {session.isAdmin && (
          <button type="button" className="btn-secondary" onClick={openSettings}>
            Configurer les seuils
          </button>
        )}
      </div>

      <FilterBar filters={filters} onChange={onFiltersChange} schools={data.schools} />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Écoles configurées" value={synthesis.totalSchoolsConfigured} />
        <StatCard label="Écoles évaluées" value={synthesis.activeSchoolsCount} />
        <StatCard label="Tests enregistrés" value={synthesis.totalAssessments} />
        <StatCard
          label="Score moyen / 280"
          value={formatNumber(synthesis.avgTotalScore)}
          hint={`Maîtrise globale : ${formatPercent(synthesis.overallMasteryPct)}`}
        />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-base font-bold">Synthèse par genre</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {[synthesis.boysSummary, synthesis.girlsSummary].map((summary) => (
              <div key={summary.gender} className="rounded-xl bg-slate-50 p-4">
                <p className="text-sm font-semibold">
                  {summary.gender === "GARCON" ? "Garçons" : "Filles"}
                </p>
                <p className="mt-2 text-2xl font-bold">{formatPercent(summary.masteryRatePct)}</p>
                <p className="text-xs text-slate-500">
                  {summary.count} évaluation(s) · moyenne {formatNumber(summary.avgTotal)} / 280
                </p>
                <div className="mt-3 grid grid-cols-2 gap-1 text-xs text-slate-600">
                  <span>M. Max : {summary.maxMasteryCount}</span>
                  <span>M. Min : {summary.minMasteryCount}</span>
                  <span>À amél. : {summary.toImproveCount}</span>
                  <span>À renf. : {summary.toReinforceCount}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card>
          <h2 className="mb-4 text-base font-bold">Domaines</h2>
          <div className="space-y-3">
            {synthesis.domainStats.map((domain) => (
              <div key={domain.label}>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium">{domain.label}</span>
                  <Badge tone={toneForStatus(domain.status)}>{STATUS_LABELS[domain.status]}</Badge>
                </div>
                <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-brand-500"
                    style={{ width: `${Math.min(100, domain.masteryRatePct)}%` }}
                  />
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  {formatNumber(domain.avgScore)} / {domain.maxScore} · G {formatPercent(domain.boysMasteryPct, 0)} · F {formatPercent(domain.girlsMasteryPct, 0)}
                </p>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <Card>
        <h2 className="mb-4 text-base font-bold">Activités détaillées</h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px]">
            <thead>
              <tr>
                <th className="table-header">Activité</th>
                <th className="table-header">Moyenne</th>
                <th className="table-header">Maîtrise</th>
                <th className="table-header">Garçons</th>
                <th className="table-header">Filles</th>
                <th className="table-header">Seuil min</th>
                <th className="table-header">Statut</th>
              </tr>
            </thead>
            <tbody>
              {synthesis.activityStats.map((activity) => (
                <tr key={activity.key}>
                  <td className="table-cell font-medium">{activity.label}</td>
                  <td className="table-cell">
                    {formatNumber(activity.avgScore)} / {activity.maxScore}
                  </td>
                  <td className="table-cell">{formatPercent(activity.masteryRatePct)}</td>
                  <td className="table-cell">{formatPercent(activity.boysMasteryPct)}</td>
                  <td className="table-cell">{formatPercent(activity.girlsMasteryPct)}</td>
                  <td className="table-cell">{formatNumber(activity.minPointThreshold)}</td>
                  <td className="table-cell">
                    <Badge tone={toneForStatus(activity.status)}>{STATUS_LABELS[activity.status]}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-base font-bold">Classement des écoles</h2>
          {synthesis.classStats.length === 0 ? (
            <EmptyState message="Aucune école à afficher." />
          ) : (
            <div className="max-h-96 space-y-2 overflow-y-auto pr-1">
              {synthesis.classStats.map((entry) => (
                <div
                  key={entry.school.id}
                  className="flex items-center justify-between rounded-xl border border-slate-100 px-3 py-2"
                >
                  <div>
                    <p className="text-sm font-semibold">
                      {entry.school.code} • {entry.school.name}
                    </p>
                    <p className="text-xs text-slate-500">
                      {entry.evaluatedCount} test(s) · {formatNumber(entry.avgTotal)} / 280
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold">{formatPercent(entry.masteryRatePct)}</p>
                    <Badge tone={toneForStatus(entry.status)}>{STATUS_LABELS[entry.status]}</Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <h2 className="mb-4 text-base font-bold">Répartition des décisions</h2>
          <div className="space-y-2">
            {(Object.keys(DECISION_LABELS) as Array<keyof typeof DECISION_LABELS>).map((decision) => {
              const count = data.assessments.filter((item) => item.decision === decision).length;
              return (
                <div key={decision} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm">
                  <span>{DECISION_LABELS[decision]}</span>
                  <span className="font-bold">{count}</span>
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      <Modal
        open={settingsOpen}
        title="Seuils de maîtrise"
        description="Les seuils servent au calcul des décisions et aux diagnostics par activité."
        onClose={() => setSettingsOpen(false)}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label="Maîtrise maximale (%)"
            type="number"
            min={50}
            max={100}
            value={settingsForm.maxMasteryPct}
            onChange={(value) => setSettingsForm((current) => ({ ...current, maxMasteryPct: value }))}
          />
          <Field
            label="Maîtrise minimale (%)"
            type="number"
            min={30}
            max={95}
            value={settingsForm.minMasteryPct}
            onChange={(value) => setSettingsForm((current) => ({ ...current, minMasteryPct: value }))}
          />
          <Field
            label="Maîtrise partielle (%)"
            type="number"
            min={10}
            max={80}
            value={settingsForm.partialMasteryPct}
            onChange={(value) => setSettingsForm((current) => ({ ...current, partialMasteryPct: value }))}
          />
          <Field
            label="Année scolaire"
            value={settingsForm.academicYear}
            onChange={(value) => setSettingsForm((current) => ({ ...current, academicYear: value }))}
          />
          <Field
            label="Circonscription"
            value={settingsForm.districtName}
            onChange={(value) => setSettingsForm((current) => ({ ...current, districtName: value }))}
          />
        </div>
        <div className="mt-4 rounded-xl bg-brand-50 px-4 py-3 text-xs text-brand-800">
          Équivalences /280 : max {formatNumber((BAREMES.TOTAL_MAX * Number(settingsForm.maxMasteryPct)) / 100, 0)} pts ·
          min {formatNumber((BAREMES.TOTAL_MAX * Number(settingsForm.minMasteryPct)) / 100, 0)} pts ·
          partiel {formatNumber((BAREMES.TOTAL_MAX * Number(settingsForm.partialMasteryPct)) / 100, 0)} pts
        </div>
        {settingsError && <div className="mt-4"><Notice tone="red">{settingsError}</Notice></div>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setSettingsOpen(false)}>Annuler</button>
          <button type="button" className="btn-primary" disabled={settingsBusy} onClick={() => void saveThresholds()}>
            {settingsBusy ? "Enregistrement…" : "Enregistrer"}
          </button>
        </div>
      </Modal>
    </div>
  );
}
