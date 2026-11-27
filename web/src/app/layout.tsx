import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Suivi CM2 — Console web",
  description:
    "Suivi des écoles CM2, tests hebdomadaires et mensuels, statistiques et gestion des rôles Superadministrateur, Administrateur et Opérateur.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
