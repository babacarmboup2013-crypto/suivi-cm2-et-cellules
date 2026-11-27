# Suivi CM2 — Console web (Next.js)

Console web du projet **Suivi CM2**, alignée sur l’application Android Kotlin. Elle
utilise **la même base Firestore nommée**, les mêmes rôles et les mêmes règles de
sécurité : `SUPERADMIN` > `ADMIN` > `OPERATOR`.

> Vercel héberge uniquement cette application web. L’APK Android reste publié via
> Google Play ou Firebase App Distribution, et les règles Firestore se déploient
> avec Firebase CLI.

## 1. Configuration Firebase

1. Dans la console Firebase du projet `ai-studio-android-suivicm2-d0fbd686`,
   activer **Authentication → Sign-in method → Google**.
2. Ajouter le domaine de déploiement Vercel (`<projet>.vercel.app` et le domaine
   personnalisé éventuel) dans **Authentication → Settings → Authorized domains**.
   Ajouter aussi `localhost` pour le développement.
3. Vérifier que l’ID de base Firestore utilisé est bien
   `ai-studio-android-suivicm2-d0fbd686-7c39-42f8-97e3-108c900c1d0f`
   (base nommée, et non `(default)`).
4. Copier `.env.example` vers `.env.local` et renseigner les valeurs de
   **Project settings → Your apps → Web app**.

```bash
cp .env.example .env.local
npm install
npm run dev
```

Ouvrir <http://localhost:3000>.

## 2. Variables d’environnement Vercel

Ajouter les mêmes variables dans **Project Settings → Environment Variables**
(pour Production, Preview et Development) :

| Variable | Description |
|---|---|
| `NEXT_PUBLIC_FIREBASE_API_KEY` | Clé API Web Firebase |
| `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | `ai-studio-android-suivicm2-d0fbd686.firebaseapp.com` |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | `ai-studio-android-suivicm2-d0fbd686` |
| `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET` | Bucket Storage Firebase |
| `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID` | Sender ID FCM |
| `NEXT_PUBLIC_FIREBASE_APP_ID` | ID de l’app Web Firebase |
| `NEXT_PUBLIC_FIREBASE_DATABASE_ID` | ID de la base Firestore nommée Android |

Ces variables sont **publiques par conception** (préfixe `NEXT_PUBLIC_`). La
sécurité ne repose pas sur leur confidentialité mais sur `firestore.rules` :
refus par défaut, e-mail Google vérifié, propriétaire by `adminUid`/`adminEmail`,
liste `operatorEmails` et rôles strictement contrôlés.

## 3. Règles Firestore

Les règles sont à la racine du dépôt (`../firestore.rules`). Après toute
modification :

```bash
firebase deploy --only firestore:rules --project ai-studio-android-suivicm2-d0fbd686
```

Les tests de règles se lancent avec l’émulateur Firestore :

```bash
firebase emulators:start --only firestore --project demo-no-project
node --test firestore.test.js
```

## 4. Comptes et rôles

- Compte d’amorçage superadministrateur : `babacarmboup2013@gmail.com`.
- Un superadministrateur crée des administrateurs dans `administrators/{email}`
  puis des écoles et des opérateurs pour ces administrateurs.
- À la première connexion Google d’un administrateur invité, la web lie son UID
  Firebase au document `administrators/{email}` et propage le vrai `adminUid`
  sur ses écoles, opérateurs, élèves et tests — exactement comme Android.
- Un opérateur est reconnu par son e-mail Google vérifié présent dans
  `operatorEmails` de l’école et de ses copies.
- Un compte Google non enregistré voit un écran « Accès non autorisé ».

## 5. Onglets

| Onglet | Contenu | Visibilité |
|---|---|---|
| Synthèse | KPI, filtres, par genre, domaines, activités, classement, seuils | Tous |
| Écoles | CRUD écoles, codes d’accès, élèves de chaque école | Tous |
| Tests | Registre des copies, saisie/édition avec barème /280 | Tous |
| Diagnostics | Points forts / à améliorer / à renforcer, tendances, par école | Tous |
| Accès | Opérateurs de saisie, statut actif/suspendu | Admin, Superadmin |
| Superadmin | Administrateurs, écoles, opérateurs, initialisation des 40 écoles | Superadmin |

## 6. Compatibilité Android

- Mêmes barèmes : LC 40/60/100, Maths 40/60/100, DM 24/16/40, DD 24/16/40,
  ESVS 80, total 280.
- Mêmes seuils par défaut : 75 % / 50 % / 35 %, année `2026-2027`.
- Décision recalculée comme `MasteryDecision.compute` ; notes arrondies à 0,5.
- Test mensuel enregistré avec `weekNumber = 0`, test hebdomadaire avec S1–S4.
- Codes d’accès hachés en PBKDF2-HMAC-SHA256 (100 000 itérations, 256 bits,
  sel 16 octets), compatibles avec `AccessCodeSecurity.kt`. Le code en clair
  n’est affiché qu’une seule fois à la création.
- Statistiques portées fidèlement de `StatisticsEngine.kt` (`src/lib/stats.ts`).

## 7. Commandes utiles

```bash
npm run dev        # serveur local
npm run typecheck  # vérification TypeScript
npm run lint       # ESLint
npm run build      # build de production (identique à Vercel)
```
