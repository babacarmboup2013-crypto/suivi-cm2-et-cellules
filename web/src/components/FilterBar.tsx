"use client";

import { SCHOOL_MONTHS, WEEK_OPTIONS } from "@/lib/constants";
import type { FilterState, Gender, TestType } from "@/lib/types";
import type { School } from "@/lib/types";
import { SelectField } from "./ui";

export function FilterBar({
  filters,
  onChange,
  schools,
}: {
  filters: FilterState;
  onChange: (filters: FilterState) => void;
  schools: School[];
}) {
  const set = (patch: Partial<FilterState>) => onChange({ ...filters, ...patch });
  return (
    <div className="card mb-5">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <SelectField
          label="Type de test"
          value={filters.testType ?? ""}
          onChange={(value) => set({ testType: (value || null) as TestType | null })}
          options={[
            { value: "", label: "Tous les tests" },
            { value: "HEBDOMADAIRE", label: "Test Hebdomadaire" },
            { value: "MENSUEL", label: "Grand Test Mensuel" },
          ]}
        />
        <SelectField
          label="Mois"
          value={filters.month ?? ""}
          onChange={(value) => set({ month: value || null })}
          options={[
            { value: "", label: "Tous les mois" },
            ...SCHOOL_MONTHS.map((month) => ({ value: month, label: month })),
          ]}
        />
        <SelectField
          label="Semaine"
          value={filters.weekNumber?.toString() ?? ""}
          onChange={(value) => set({ weekNumber: value ? Number(value) : null })}
          options={[
            { value: "", label: "Toutes" },
            ...WEEK_OPTIONS.map((week) => ({ value: String(week), label: `Semaine ${week}` })),
          ]}
        />
        <SelectField
          label="École"
          value={filters.schoolId ?? ""}
          onChange={(value) => set({ schoolId: value || null })}
          options={[
            { value: "", label: "Toutes les écoles" },
            ...schools.map((school) => ({
              value: school.id,
              label: `${school.code} • ${school.name}`,
            })),
          ]}
        />
        <SelectField
          label="Genre"
          value={filters.gender ?? ""}
          onChange={(value) => set({ gender: (value || null) as Gender | null })}
          options={[
            { value: "", label: "Garçons et filles" },
            { value: "GARCON", label: "Garçons" },
            { value: "FILLE", label: "Filles" },
          ]}
        />
      </div>
      <button
        type="button"
        className="btn-secondary mt-3 text-xs"
        onClick={() => onChange({ testType: null, month: null, weekNumber: null, schoolId: null, gender: null })}
      >
        Réinitialiser les filtres
      </button>
    </div>
  );
}

export function applyFilters(assessments: import("@/lib/types").Assessment[], filters: FilterState) {
  return assessments.filter((item) => {
    if (filters.testType && item.testType !== filters.testType) return false;
    if (filters.month && item.month !== filters.month) return false;
    if (filters.weekNumber && item.testType === "HEBDOMADAIRE" && item.weekNumber !== filters.weekNumber) return false;
    if (filters.schoolId && item.schoolId !== filters.schoolId) return false;
    if (filters.gender && item.gender !== filters.gender) return false;
    return true;
  });
}

export function trendScope(assessments: import("@/lib/types").Assessment[], filters: FilterState) {
  return assessments.filter((item) => {
    if (filters.schoolId && item.schoolId !== filters.schoolId) return false;
    if (filters.gender && item.gender !== filters.gender) return false;
    return true;
  });
}
