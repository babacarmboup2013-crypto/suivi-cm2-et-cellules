"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/** Remplace l'erreur générique « client-side exception » par un message exploitable. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Suivi CM2 — erreur d'interface", error, info.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <div className="card max-w-2xl space-y-3">
          <h1 className="text-lg font-bold text-red-700">Une erreur est survenue</h1>
          <p className="text-sm text-slate-600">
            L&apos;application n&apos;a pas pu afficher cette page. Le détail ci-dessous permet
            d&apos;identifier la cause.
          </p>
          <pre className="max-h-64 overflow-auto rounded-xl bg-slate-900 p-4 text-xs text-slate-100">
            {error.message}
            {error.stack ? `\n\n${error.stack}` : ""}
          </pre>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-primary" onClick={() => window.location.reload()}>
              Recharger la page
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => this.setState({ error: null })}
            >
              Réessayer
            </button>
          </div>
        </div>
      </main>
    );
  }
}
