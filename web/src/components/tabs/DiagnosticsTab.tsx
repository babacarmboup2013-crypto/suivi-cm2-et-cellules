"use client";

import { useMemo } from "react";
import type { AppData } from "@/lib/useData";
import type { FilterState } from "@/lib/types";
import { computeSynthesis, STATUS_LABELS } from "@/lib/stats";
import { formatNumber, formatPercent } from "@/lib/format";
import { Badge, Card, EmptyState, StatCard } from "../ui";
import { applyFilters, FilterBar, trendScope } from "../FilterBar";

export function DiagnosticsTab({
  data,
  filters,
  onFiltersChange,
}: {
  data: AppData;
  filters: FilterState;
  onFiltersChange: (filters: FilterState) => void;
}) {
  const synthesis = useMemo(() => {
    const filtered = applyFilters(data.assessments, filters);
    return computeSynthesis(data.schools, filtered, trendScope(data.assessments, filters), data.settings);
  }, [data, filters]);

  const groups = [
    { title: "Points forts", items: synthesis.strengths, tone: "green" as const },
    { title: "À améliorer", items: synthesis.toImprove, tone: "amber" as const },
    { title: "À renforcer", items: synthesis.toReinforce, tone: "red" as const },
  ];

  return (
    <div className="space-y-5">
      <FilterBar filters={filters} onChange={onFiltersChange} schools={data.schools} />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Seuil maîtrise max"
          value={`${data.settings.maxMasteryPct} %`}
          hint={`${formatNumber((280 * data.settings.maxMasteryPct) / 100)} points / 280`}
        />
        <StatCard
          label="Seuil maîtrise min"
          value={`${data.settings.minMasteryPct} %`}
          hint={`${formatNumber((280 * data.settings.minMasteryPct) / 100)} points / 280`}
        />
        <StatCard
          label="Seuil partiel"
          value={`${data.settings.partialMasteryPct} %`}
          hint={`${formatNumber((280 * data.settings.partialMasteryPct) / 100)} points / 280`}
        />
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        {groups.map((group) => (
          <Card key={group.title}>
            <h2 className="mb-3 text-base font-bold">{group.title}</h2>
            {group.items.length === 0 ? (
              <EmptyState message="Aucune activité dans cette catégorie." />
            ) : (
              <div className="space-y-2">
                {group.items.map((item) => (
                  <div key={item.key} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2">
                    <span className="text-sm">{item.label}</span>
                    <Badge tone={group.tone}>{formatPercent(item.masteryRatePct)}</Badge>
                  </div>
                ))}
              </div>
            )}
          </Card>
        ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-base font-bold">Tendance hebdomadaire</h2>
          <TrendBars points={synthesis.weeklyTrend} />
        </Card>
        <Card>
          <h2 className="mb-4 text-base font-bold">Tendance des grands tests</h2>
          <TrendBars points={synthesis.monthlyTrend} />
        </Card>
      </div>

      <Card>
        <h2 className="mb-4 text-base font-bold">Diagnostic par école</h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px]">
            <thead>
              <tr>
                <th className="table-header">École</th>
                <th className="table-header">Évalués</th>
                <th className="table-header">Maîtrise G</th>
                <th className="table-header">Maîtrise F</th>
                <th className="table-header">Point fort</th>
                <th className="table-header">Point faible</th>
                <th className="table-header">Statut</th>
              </tr>
            </thead>
            <tbody>
              {synthesis.classStats.map((entry) => (
                <tr key={entry.school.id}>
                  <td className="table-cell font-medium">
                    {entry.school.code} • {entry.school.name}
                  </td>
                  <td className="table-cell">{entry.evaluatedCount}</td>
                  <td className="table-cell">{formatPercent(entry.boysMasteryPct)}</td>
                  <td className="table-cell">{formatPercent(entry.girlsMasteryPct)}</td>
                  <td className="table-cell">{entry.strongestActivity ?? "—"}</td>
                  <td className="table-cell">{entry.weakestActivity ?? "—"}</td>
                  <td className="table-cell">
                    <Badge tone={entry.status === "POINT_FORT" ? "green" : entry.status === "A_AMELIORER" ? "amber" : "red"}>
                      {STATUS_LABELS[entry.status]}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

function TrendBars({
  points,
}: {
  points: Array<{ label: string; boysMasteryPct: number; girlsMasteryPct: number; overallMasteryPct: number; count: number }>;
}) {
  if (!points.length) return <EmptyState message="Aucune tendance disponible." />;
  return (
    <div className="flex h-56 items-end gap-3">
      {points.map((point) => (
        <div key={point.label} className="flex flex-1 flex-col items-center gap-1">
          <div className="flex h-full w-full items-end justify-center gap-1">
            <div
              className="w-3 rounded-t bg-brand-500"
              style={{ height: `${Math.max(2, point.boysMasteryPct)}%` }}
              title={`Garçons : ${formatPercent(point.boysMasteryPct)}`}
            />
            <div
              className="w-3 rounded-t bg-pink-400"
              style={{ height: `${Math.max(2, point.girlsMasteryPct)}%` }}
              title={`Filles : ${formatPercent(point.girlsMasteryPct)}`}
            />
          </div>
          <span className="text-xs font-semibold text-slate-600">{point.label}</span>
          <span className="text-[10px] text-slate-400">{point.count}</span>
        </div>
      ))}
    </div>
  );
}
