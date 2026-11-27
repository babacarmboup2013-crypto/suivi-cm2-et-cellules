"use client";

import { AppShell } from "./AppShell";
import { isFirebaseConfigured } from "@/lib/firebase";
import { useAuth } from "@/lib/useAuth";
import { useAppData } from "@/lib/useData";

export default function HomeClient() {
  const session = useAuth();
  const data = useAppData(session);

  return <AppShell session={session} data={data} configured={isFirebaseConfigured} />;
}
