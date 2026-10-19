# UX des catalogues (Réglages)

Écrans où l’utilisatrice gère des **listes configurables** : annuaire
professionnels, médicaments, substances, plans récurrents, programmes TENS.

## Pattern liste

- **Navigation** : `ScreenBack` + **`+`** (`IconButton`, haut droite) sur la même ligne.
- **Contenu** : une `LutealCard` par entrée ; **clic tuile → édition**.
- **Ne pas** : action destructive sur la tuile (pas de poubelle inline), `TextButton` « Ajouter » sous la liste, `AlertDialog` pour l’édition.

Composant partagé : `SettingsListHeader`.

## Pattern édition

- Écran plein (sous-écran Réglages), pas de dialog.
- `BackHandler` : retour liste puis sortie du sous-écran parent.
- Corps scrollable ; **barre fixe en bas** : `FormEditorFooter` (composant design system).
  - **Gauche** (optionnel) : action destructive (rouge + icône poubelle) → confirmation si suppression définitive.
  - **Droite** : enregistrer (bouton + coche verte).

Référence : `app/src/main/java/fr/luteal/app/navigation/settings/professionals/ProfessionalsSettingsScreen.kt`.

## Choix de champs

| Besoin | Composant |
|--------|-----------|
| Une valeur parmi N (formulaire) | `SettingsDropdownField` |
| Plusieurs valeurs (formulaire / filtre multi) | `SettingsMultiSelectDropdown` |
| Filtre rapide, peu d’options visibles (frise, agenda) | `FilterChip` + `FlowRow` |

## Exceptions métier

- **Médicaments** : pas de suppression ; **archiver** depuis le footer gauche en édition (dialog existant), jamais depuis la liste.
- **Substances** : pas de suppression dans l’UI catalogue actuelle (footer = enregistrer seulement).

## Écrans alignés

| Écran | Fichier |
|-------|---------|
| Annuaire | `settings/professionals/ProfessionalsSettingsScreen.kt` |
| Médicaments / substances | `settings/meds/MedsSettingsScreen.kt` |
| Plans récurrents | `settings/meds/TreatmentPlansSettingsScreen.kt` |
| Programmes TENS | `settings/care/CareSettingsScreen.kt` |

## Hors scope

- Tuiles d’entrée du hub `SettingsScreen.kt`.
- Feuilles agenda (`SheetActions`, `EntryScaffold`) : alignement optionnel ultérieur.
