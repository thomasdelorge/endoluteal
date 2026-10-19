# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Médicaments : catalogue en tuiles, sections et menus déroulants ; contre-indications par substance ; prises récentes limitées à 24 h ; prises de l’agenda préfixées par 💊 ; export frise avec nombre de prises par jour et par médicament.
- Aide à la prise : limites par substance (mg sur une période en heures), rappel par médicament, écran incompatibilités et classes retirés de l’UI ; tuiles de prises récentes simplifiées.
- Plans récurrents (réglages) : liste en tuiles, édition au clic, suppression en édition, boutons à la place des liens.
- Catalogues Réglages (annuaire, médicaments, substances, plans, TENS) : « + » en tête, édition plein écran, barre bas supprimer / valider ; spécialité médecin en menu déroulant ; doc `docs/architecture/SETTINGS_CATALOG_UX.md`.
- Frise : filtres par type d’événement (tout / aucun / multi), mode avec ou sans détails ; prises, symptômes, soins/TENS et hospitalisations dans la liste.
- Frise : types et professionnels en menus multi-sélection uniquement ; filtre par spécialité retiré.
- Récap du jour, étape Douleurs : section « Événements du jour » retirée (carte du corps et échelles 5D inchangées).
- Récap du jour, étape Général : alcool et impact sur la journée en menus déroulants.
- Récap du jour : « Modifier » ouvre l’assistant à la première étape (Cycle).
- Récap du jour, étape Cycle : phase, saignements et pertes en menus déroulants (une saisie par champ).
- Récap du jour, étape Douleurs : carte du corps puis échelles 5D et notes (deux écrans, Continuer entre les deux).

## [0.11.3] - 2026-10-19

### Added

- Récap du jour, étape Général : étourdissements et vertiges (échelles comme maux de tête et nausées).

## [0.11.2] - 2026-10-19

### Fixed

- Récap du jour : en édition, l’étape Cycle (saignement, pertes) reprenait d’anciennes valeurs et n’enregistrait pas les changements.

## [0.11.1] - 2026-10-19

### Changed

- Récap quotidien, étape Douleurs : saisie par points sur la silhouette (comme symptôme et prise), avec reprise des anciens min/max par zone sous forme de points approximatifs.

## [0.11.0] - 2026-10-16

### Added

- Symptômes renseignables : étourdissements et vertiges.
- Éditeur de rendez-vous redécoupé en sections (rendez-vous, quand, remboursement, compte-rendu) avec un CR multiligne et un titre éditable.
- La fiche de prise de médicament enregistre la douleur après la prise.
- Les saisies du jour sont regroupées en tuiles par type (rendez-vous, médicaments, autres).

### Changed

- Un rendez-vous modifié localement (titre, lieu, description) n'est plus écrasé par la synchronisation calendrier suivante.
- Le rappel de compte-rendu d'un rendez-vous ne se déclenche plus rétroactivement à la synchronisation : uniquement à la fin du rendez-vous.
- Suppression du bouton « Prendre » redondant sur les cartes de médicaments.

## [0.10.0] - 2026-10-14

### Fixed

- Démarrage : Room refusait d'ouvrir la base (migration 10 et reset destructif se chevauchaient).
- Mise à jour depuis la 0.9.0 : la migration des rendez-vous correspond au schéma.

### Changed

- Annuaire : une tuile par professionnel, édition au tap, ajout par le bouton +.
- À partir de la 0.9.0, une évolution de schéma ne réinitialise plus les données.
- Rendez-vous : coût, statut de remboursement, parts CPAM et mutuelle, reste à charge calculé.
- Import calendrier : toutes les 4 h, seuls le titre et le lieu suivent le calendrier. Une suppression côté calendrier laisse le rendez-vous.
- Rendez-vous : le lieu s’ouvre dans une application de navigation.
- Les exports JSON indiquent la version de l’application.
- Réglages regroupés en Santé, Rappels, Apparence, Données et Sécurité.
- Apparence : un seul menu combine le mode clair, sombre ou système et la couleur.
- Thèmes Solarized, Catppuccin et Dracula.
- La frise enregistre son PDF dans un fichier choisi, sans ouvrir le partage.
- Examens et médicaments d'une hospitalisation se saisissent dans une fenêtre, puis s'affichent en liste.
- Médicaments : une tuile par traitement, avec un bandeau rouge si la prochaine prise est trop tôt.
- Une prise passée se modifie ou se supprime depuis la tuile du médicament.

## [0.9.0] - 2026-10-14

### Fixed

- Feuilles de saisie et onglets principaux : contenu dans la zone sûre (encoche, barre de statut, navigation système).
- Feuilles de saisie : une seule flèche retour en haut ; enregistrement épinglé en bas.
- Retour système : ferme la feuille ou le sous-écran au lieu de quitter l’application.
- Agenda : fond flouté et voile lors de l’ouverture du menu + ; le bouton + se masque en bas de liste scrollable.
- Rendez-vous médical : choix du professionnel via un menu déroulant de l’annuaire.
- Notifications de rappel : ouverture de l’app au tap, report +10/+30 qui retire la notif, actions utiles (3 max).

### Changed

- Carte du corps : points de douleur positionnés sur le schéma (intensité et type par point), pour une heatmap future.
- Carte du corps : plusieurs points par zone sur la silhouette ; saisie par liste des régions CHOIR retirée (zones internes inchangées).
- Fiche de douleur sur le schéma : actions Enregistrer, Annuler et Retirer regroupées sous le formulaire.
- Fiche de douleur : confirmation avant de retirer un point.

## [0.8.0] - 2026-10-12

### Changed

- Carte du corps : schéma clinique CHOIR (femme, face et dos), régions cliquables.

## [0.7.0] - 2026-10-12

### Changed

- Retour et enregistrement signalés par des icônes dans les feuilles de saisie et la fiche médicament.
- Fiche médicament : plus de champ « forme », archivage par icône poubelle.
- Événements du jour en tuiles cliquables, y compris les prises de médicament modifiables ou supprimables.
- Calendrier en tête de l’agenda ; l’ajout d’historique de cycles reste sur la carte du jour.
- Les plans de médicaments récurrents se configurent dans les Réglages.
- Programmes TENS modifiables directement dans les Réglages > Traitements.
- Le menu + de l’agenda propose TENS avec programme, durée et rappel optionnel une heure après la fin.
- Suppression des bannières informatives « Données et confidentialité » dans les Réglages.
- Carte du corps : silhouette féminine et points de douleur sur les zones touchées.
- Numéro de version affiché en bas des Réglages.
- La suppression d’un rendez-vous est dans le menu en haut à droite de la fiche.
- Les plafonds de dose des substances courantes sont renseignés depuis les RCP déjà cités, sans écraser une saisie.
- La question « traitement hormonal continu » n’est plus proposée à la saisie.
- La section « Données de démonstration » est retirée des Réglages.
- Les centres d’attention ne sont plus proposés dans les Réglages.
- La légende des phases et observations est retirée de l’agenda.
- La durée d’un TENS se choisit parmi 30 min, 1 h, 1 h 30 et 2 h.
- Les réglages de soins ne proposent plus que les programmes TENS.
- Carte du corps : silhouette féminine en image, et le récap peut défiler jusqu’à l’étape suivante.
- Les saisies longues s’ouvrent en plein écran.
- Les programmes TENS n’ont plus de durée enregistrée : elle se choisit à chaque séance.
- L’onglet Médicaments propose « Noter une prise » sans historique préalable.
- Le récap enregistré du jour sélectionné s’affiche en lecture seule sous le calendrier.
- Carte du corps : silhouette féminine retracée (domaine public) et zones recalées.
- L’échelle de douleur 0–10 devient un curseur pleine largeur avec une phrase d’aide par niveau.
- Récap douleur : une courte explication sous chaque question des cinq douleurs.
- Récap psychologique : visages sur les échelles à cinq niveaux.
- Récap selles : types Bristol en rangée 1–7 avec description sous la sélection.
- Récap du jour : le bouton fixe « Continuer » enregistre l’étape courante puis avance.
- Agenda : une seule carte « Récap du jour » en lecture seule avec « Modifier » ou « Compléter ».
- Curseur de douleur : le chiffre 0–10 précède la phrase d’aide.
- Récap du jour : bouton « Précédent » et navigation libre entre les étapes.
- Réglages : thème et palette en menus déroulants.
- Heures de sauvegarde auto, plans de traitement et rappel quotidien : sélecteur d’heure Android.

## [0.6.0] - 2026-10-12

### Fixed

- Silhouette de douleur dessinée dans l’écran, alignée sur les zones touchables.

### Changed

- Saisie d’un plan de médicament : liste déroulante des médicaments déjà enregistrés, dates au calendrier (JJ-MM-AAAA), bouton Enregistrer plein.
- Un rendez-vous ouvert depuis la frise se modifie dans la même fiche que l’agenda, et exige un professionnel.
- Un programme TENS se nomme à la saisie et se retrouve dans les Réglages, où il se renomme et se supprime.
- Un professionnel de santé se modifie et se supprime depuis les Réglages.

## [0.5.0] - 2026-10-12

### Added

- Choix du thème clair, sombre, ou celui de l’appareil, dans les Réglages.
- Quatre couleurs d’interface : vert, bleu, prune et sable.

### Changed

- Silhouette de localisation lisible en thème sombre.
- Dates affichées au format JJ-MM-AAAA, heures au format 24 h.
- Choix du médicament dans un menu déroulant filtrable.
- Un rendez-vous importé garde son titre et le praticien choisi au prochain import.
- Les boutons Enregistrer des saisies sont des boutons remplis.
- Annuler est à gauche et Enregistrer à droite dans les feuilles de saisie.
- Le filtre de spécialité de la frise est un menu à choix multiples.

## [0.4.0] - 2026-10-12

### Added

- Agenda d'accueil : calendrier, événements du jour et saisie du récap.
- Échelle de douleur 0-10 et silhouette de localisation.
- Rappels exacts reportables (+10 min, +30 min, +1 h).
- Base de médicaments, alertes de prise et saisie d'une prise avec contrôle de douleur.
- Plans de traitement et rappel groupé. La prédiction de règles est masquée sous traitement hormonal continu.
- Sauvegarde automatique dans un dossier local.
- Saisie d’un symptôme douloureux depuis l’agenda (localisation, intensité, notes).
- Soins non médicamenteux : catalogue, séance avec durée et rappel de douleur 1 h après la fin.
- Hospitalisation sur plusieurs jours dans l’agenda (lieu, motif, examens, médicaments).
- Rendez-vous manuels liés à un professionnel (spécialité, lieu, compte rendu).
- Récap quotidien en cartes avec enregistrement par étape et rappel quotidien.
- Étapes Cycle et Douleurs du récap (5D, carte corporelle min/max, dyspareunie masquable).
- Étapes Psy, Général et Intestinal du récap ; suppression de l’ancienne saisie journalière.
- Questionnaire mensuel inspiré (pas le texte EHP-5), score 0–100 et export des résultats.
- Historique des scores du questionnaire mensuel dans les Réglages.
- Import des rendez-vous médicaux depuis l’agenda du téléphone (filtres, synchro périodique).
- Rappel et saisie du compte rendu à la fin d’un rendez-vous.
- Frise des rendez-vous et hospitalisations, avec résumé depuis le dernier rendez-vous.
- Export PDF de la frise filtrée et partage vers une autre application.
- Résumé de l’efficacité des prises et des soins à partir des contrôles de douleur.
- Chiffrement optionnel des sauvegardes (PBKDF2 + AES-256-GCM) avec mot de passe choisi et saisie à l’import.

### Changed

- Application entièrement en français ; accueil sur le Journal (plus d’onglet
  Aujourd’hui).
- Suppression du mode Duo, des widgets, de la synchronisation cloud et du
  schéma Room Luteal (base `endoluteal.db`, sauvegardes `endoluteal/v1`).

- Local dev tooling (`mise.toml`, `make avd` / `emu` / `run` / `logcat`).
- CI: debug APK artifact on PRs; signed release APK on version tags for
  Obtainium.
