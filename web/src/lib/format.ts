export function formatNumber(value: number, digits = 1): string {
  return value.toLocaleString("fr-FR", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function formatPercent(value: number, digits = 1): string {
  return `${formatNumber(value, digits)} %`;
}

export function formatDate(timestamp: { toDate: () => Date } | null | undefined): string {
  if (!timestamp) return "—";
  try {
    return timestamp.toDate().toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  } catch {
    return "—";
  }
}

export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}
