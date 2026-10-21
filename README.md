# Panneau Admin MineNorthRP (`minenorth_admin`)

Forge 1.20.1. Commande **`/mnadmin`** : un seul écran pour gérer les joueurs (en ligne **et hors ligne**).

## Compilation
1. Les jars des autres mods sont déjà dans `libs/` (eurobank 1.0.0, permis 1.3.0, rp_vehicles 1.0.0).
   Si tu changes une version, mets à jour `gradle.properties`.
2. `./gradlew build` → `build/libs/minenorth_admin-1.0.0.jar` (serveur ET clients).

Les mods (EuroBank, Permis, Véhicules, Police, Secours) sont **optionnels** : un onglet n'apparaît que si son mod est installé.

## Noms
Partout dans le panneau, les joueurs sont affichés avec le nom RP de leur carte d'identité (mod `minenorthidentite`),
sinon leur pseudo s'ils n'en ont pas encore. La recherche et les champs « joueur » acceptent le nom RP (« Prénom Nom »)
ou le pseudo.

## Onglets
- **Joueurs** : recherche, fiche joueur, TP vers / TP ici.
  - Banque : solde (définir / ajouter / retirer), ouvrir un compte, banquier, carte bancaire, annuler une dette / refuser une demande de prêt.
  - Permis : donner (durée en jours, vide = config, 0 = permanent), retirer, nouvelle carte, points, effacer les délais d'examen.
  - Garage : garage et fourrière ; supprimer, libérer de la fourrière, transférer à un autre joueur.
  - Police (si MineNorth Police est installé) : faire entrer un joueur dans la police (au grade le plus bas) ou l'en retirer,
    hors ligne aussi, changer son grade, et lui donner la tablette ou l'équipement (connecté).
    Permissions « Police : voir » et « Police : ajouter / retirer / grades ».
  - Pompiers (si MineNorth Secours est installé) : nommer un joueur pompier (choix du grade : Chef des secours, Médecin, Secouriste),
    changer son grade, le retirer (hors ligne aussi) et lui donner la tablette (connecté). Permissions « Pompiers : voir » et
    « Pompiers : nommer / grades ». Nécessite le mod Secours à jour (nouvelle API `SecoursApi`).
  - Grade (si le mod MineNorth VIP est installé) : voir le grade d'un joueur, lui en donner un (durée en jours, vide = permanent), le changer, le renouveler ou le retirer, hors ligne aussi. Permissions « Grades VIP : voir » et « Grades VIP : donner / retirer » (donnée au rôle Gérant à la mise à jour).
  - Inventaire + ender chest : copier, prendre, supprimer, tout vider, donner l'objet en main. Hors ligne : modifie le fichier `playerdata`.
- **Propriétés** (onglet en haut, si MineNorth Portes à jour est installé) : gestion des maisons, appartements, locaux, garages… sans commande.
  - Liste paginée (13 par page, molette = page suivante) et recherchable (nom, catégorie, titulaire, catalogue ; Entrée ou « Chercher »), avec filtres : tous, libres, occupés, à vendre, à louer, sans propriétaire, location échue dans moins de 48 h. Colonnes : nom, catégorie, statut (libre / occupé / loué / à vendre / à louer), prix, titulaire.
  - Fiche d'un bien : catégorie, nombre de portes, position, propriétaire, locataire et fin de bail, fiche de vente ou de location, prix d'achat, colocataires. Actions : attribuer le propriétaire (nom RP, pseudo ou UUID, joueur hors ligne aussi), installer un locataire (sans paiement), ajouter / retirer un colocataire, mettre en vente / en location, changer le prix de la fiche, retirer la fiche, changer la catégorie, se téléporter sur place, **expulser l'occupant** et **libérer le bien** (double clic de confirmation, vérifié aussi côté serveur), journal du bien (achats, ventes, locations, actions du staff, ouvertures par un tiers).
  - Fiche joueur → section « Propriétés » : ses biens (propriétaire ou locataire), bouton pour ouvrir la fiche d'un bien ou voir la liste filtrée sur ce joueur.
  - Permissions « Propriétés : voir » (`PROPRIETES_VIEW`) et « Propriétés : attribuer / vendre / libérer » (`PROPRIETES_EDIT`), données une fois au rôle qui gère le staff (Gérant) à la mise à jour ; la téléportation demande aussi « Téléportation ». Chaque action est écrite au journal du panneau (module `propriétés`).
  - Nécessite **minenorth-portes** à jour (API `fr.minenorth.portes.api.PropertyAdminApi`, appelée par réflexion : le panneau compile et démarre sans). Il n'existe pas de loyer à retard dans Portes (la location est payée d'avance et expire), d'où le filtre « location échue < 48 h ».
- **État** (onglet en haut, si MineNorth État est installé) : solde du trésor, impôt sur le compte et impôt sur le salaire (fixer ou revenir à la valeur de la config),
  maire (nommer / retirer), ouvrir / clôturer / annuler une élection. Permissions « État : voir » et « État : impôt / maire / élections ».
- **Supprimer un joueur** (bouton rouge dans sa fiche, double clic, joueur déconnecté) : efface TOUTES ses données —
  fichiers du monde (inventaire, ender chest, position, succès, stats), compte bancaire et prêts, permis, garage et
  fourrière, grade police, rôle staff. S'il revient, il repart de zéro. Permission « Supprimer un joueur » (propriétaire et Gérant par défaut).
- **Staff** : rôles (nom, rang, permissions) et membres. Invisible pour les joueurs : `/mnadmin` n'existe même pas pour eux.
- **Journal** : toutes les actions, aussi écrites dans `logs/minenorth_admin.log`.

## Rôles et rangs
- Op niveau 4 (configurable) = **propriétaire** : tous les droits, sans rôle.
- Rôles par défaut : Modérateur (10), Admin (50), Gérant (90). Modifiables / supprimables.
- On ne gère que les rôles et membres de **rang inférieur** au sien, on ne donne que les permissions qu'on a,
  et on ne peut pas agir sur un staff de rang supérieur ou égal.

## Config
`world/serverconfig/minenorth_admin-server.toml` : niveau d'op propriétaire, taille du journal, fichier de log,
prévenir le joueur quand le staff modifie sa banque / ses permis.

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).
