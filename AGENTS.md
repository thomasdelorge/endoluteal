# Endoluteal — guide pour agents

Application Android de suivi de l'endométriose (hard-fork de
[Luteal](https://github.com/JoeriKaiser/luteal)). Roadmap et issues :
[docs/product/ENDOLUTEAL_ROADMAP.md](docs/product/ENDOLUTEAL_ROADMAP.md).

## Architecture

- Carte des douleurs (pins, CHOIR, récap vs journal) :
  [docs/architecture/PAIN_BODY_MAP.md](docs/architecture/PAIN_BODY_MAP.md).
- Catalogues Réglages (tuiles, `+`, édition, footer) :
  [docs/architecture/SETTINGS_CATALOG_UX.md](docs/architecture/SETTINGS_CATALOG_UX.md).

## Stack

- Module unique `:app`, Kotlin, Jetpack Compose, Hilt, ViewModel + `StateFlow`
- Room (SQLite local), DataStore, WorkManager / `AlarmManager` pour les rappels
- Modèle cible : table `journal_events` + tables d'agrégation (voir roadmap)

Packages Kotlin `fr.luteal.*` conservés ; `applicationId` =
`io.github.thomasdelorge.endoluteal` (suffixe `.dev` en debug).

## Invariants

1. **Données de santé hors de l'appareil**, sauf aide au CR RDV : permission `INTERNET` uniquement pour OpenRouter (transcription / résumé sur action explicite, clé utilisatrice).
2. **Enregistré vs estimé** : ne jamais présenter une saisie utilisatrice comme
   un calcul ou une prédiction.
3. **Migrations Room** : avant le schéma 11 (app 0.9.0), un reset des versions 1 à 9
   est accepté. La 10 a `MIGRATION_10_11` : Room interdit de la lister dans
   `fallbackToDestructiveMigrationFrom`. À partir du schéma 11, toute évolution est
   une migration additive testée. Une migration manquante fait échouer l'ouverture,
   elle n'efface pas les données. Un export JSON antérieur doit se réimporter :
   nouveaux champs optionnels avec défaut, clés inconnues ignorées, `schema_version`
   1 tant que les fichiers 0.9.0 restent lisibles.
4. **Toute nouvelle table** doit être couverte par export/import JSON (backup).
5. **Chaînes UI en français** (`res/values/strings.xml`).
6. **Alertes médicamenteuses** : aide à la prise, pas un avis médical (mention
   UI).

## Commandes

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest   # CI locale / PR
make run      # install + lance sur appareil/émulateur
make logcat   # logs du processus debug
```

## Workflow Git

- Conventional Commits (`feat:`, `fix:`, `chore:`, …)
- Une issue GitHub par PR ; corps de commit avec `Closes #n`
- PR vers `thomasdelorge/Endoluteal` (pas l'upstream Luteal)
- `gh repo set-default thomasdelorge/Endoluteal`

## Terminé quand

- Critères d'acceptation de l'issue cochés
- Tests unitaires pour toute logique non triviale ajoutée
- `./gradlew assembleDebug lintDebug testDebugUnitTest` vert
- Scénario manuel de l'issue rejoué sur émulateur si UI
- Entrée sous `[Unreleased]` dans `CHANGELOG.md` si comportement visible
