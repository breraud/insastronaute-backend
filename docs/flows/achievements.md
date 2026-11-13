# Succès — flow admin et joueur

Un succès est composé par un administrateur : un titre et une description libres, **une phrase clé** paramétrée, et une récompense en tokens. Les phrases clé sont des plugins Spring découverts au démarrage ; le catalogue s'étoffe sans toucher au moteur.

## Catalogue de phrases clé

```http
GET /api/admin/achievements/types
```

```json
[
  {
    "key": "CARD_CATEGORY_OBTAINED",
    "publicName": "Obtenir des cartes d'une catégorie",
    "description": "Se déclenche quand le joueur possède un nombre donné de cartes distinctes dans une catégorie.",
    "parameters": [
      { "name": "categoryId", "label": "Catégorie de carte", "type": "CARD_CATEGORY", "required": true },
      { "name": "count", "label": "Nombre de cartes distinctes", "type": "INTEGER", "required": true, "minimum": 1, "maximum": 10000 }
    ]
  }
]
```

Le panel admin génère le formulaire à partir de `parameters` : il n'a aucune connaissance des phrases en dur. Le type `CARD_CATEGORY` indique de proposer un sélecteur alimenté par `/api/admin/card-categories`.

Premier lot :

| Clé | Phrase rendue | Paramètres | Déclencheur |
|---|---|---|---|
| `CARD_CATEGORY_OBTAINED` | Obtiens N cartes de catégorie X | `categoryId`, `count` | `CARD_OBTAINED` |
| `CARD_CATEGORY_COMPLETION` | Atteins X % de la catégorie Y | `categoryId`, `percentage` | `CARD_OBTAINED` |
| `CARD_DUPLICATE_COUNT` | Obtiens N fois une même carte | `copies` | `CARD_OBTAINED` |
| `CHALLENGES_VALIDATED` | Fais valider N défis | `count` | `CHALLENGE_VALIDATED` |
| `CHALLENGE_STREAK` | Fais valider un défi N jours d'affilée | `days` | `CHALLENGE_VALIDATED` |
| `TOURNAMENTS_ATTENDED` | Sois présent à N tournois | `count` | `TOURNAMENT_SETTLED` |
| `TOURNAMENT_PODIUMS` | Termine N fois sur le podium | `count` | `TOURNAMENT_SETTLED` |
| `EVENT_REGISTRATIONS` | Inscris-toi à N événements | `count` | `EVENT_REGISTERED` |
| `FRIENDS_COUNT` | Ajoute N amis | `count` | `FRIEND_ACCEPTED` |
| `TREASURE_CHECKPOINTS` | Valide N checkpoints | `count` | `TREASURE_CHECKPOINT_VALIDATED` |
| `QR_SCANNED` | Scanne le QR INSAstronaute | — | `QR_SCANNED` |
| `PLACE_BONUS_PIXELS_USED` | Utilise N pixels bonus sur r/place | `count` | `PLACE_PIXEL_PLACED` |
| `TOKENS_BALANCE` | Détiens N tokens en même temps | `amount` | *balayage horaire* |
| `LEADERBOARD_RANK` | Entre dans le top N d'un classement | `metric`, `maxRank` | *balayage horaire* |
| `STS_RUNS_FINISHED` | Termine N runs d'Aventure | `count` | `STS_RUN_ENDED` |
| `STS_BEST_FLOOR` | Atteins l'étage N | `floor` | `STS_RUN_ENDED` |
| `STS_BEST_SCORE` | Termine une run avec N points | `score` | `STS_RUN_ENDED` |
| `STS_PVP_WINS` | Gagne N duels | `count`, `includeFriendly` | `STS_PVP_BATTLE_SETTLED` |
| `STS_PVP_ELO` | Atteins N d'elo | `elo` | `STS_PVP_BATTLE_SETTLED` |

### Ce que la donnée ne permet pas

Cinq phrases ne sont pas ce qu'on aurait spontanément écrit :

- **`QR_SCANNED` n'a pas de paramètre** : il n'existe qu'un seul QR permanent, et le scanner bascule un booléen sur le joueur. « N QR scannés » n'a pas de sens ici.
- **`PLACE_BONUS_PIXELS_USED` ne compte pas tous les pixels** : le canvas r/place vit dans un `PlaceCanvasStore` sans historique par joueur. Seuls les pixels *bonus*, gagnés via les défis, sont comptés sur l'utilisateur.
- **« Run terminée » veut dire *retraitée*** : `StsRunStatus.Completed` existe dans l'enum mais n'est **jamais assigné** dans le code. La retraite est la seule fin qui banque un score et crédite des tokens ; `Failed` est une mort, `Abandoned` un abandon.
- **Pas de succès par personnage** : le catalogue des personnages est chargé depuis S3 au runtime selon la `dataVersion`, donc impossible à énumérer dans un menu déroulant. Un code mal tapé donnerait un succès silencieusement indéblocable.
- **« Tokens dépensés » n'existe pas** : il n'y a aucun registre de transactions de tokens. `TOKENS_BALANCE` mesure ce qu'un joueur *détient*, pas ce qu'il a dépensé. Créer la phrase demanderait d'abord une table de mouvements.

`TOKENS_BALANCE` et `LEADERBOARD_RANK` ne déclarent **volontairement aucun déclencheur**. Un solde bouge depuis une demi-douzaine d'endroits, et débloquer un succès crédite lui-même des tokens — le chemin à chaud se rappellerait donc lui-même. Les rangs, eux, ne changent qu'à la publication d'un snapshot, déjà planifiée. Le balayage horaire couvre les deux sans ce risque.

Les catégories de cartes sont l'axe de rareté du jeu : « Obtiens une carte commune » et « Obtiens une carte légendaire » sont **la même phrase** avec un `categoryId` différent.

## Créer un succès

```http
POST /api/admin/achievements
```

```json
{
  "title": "Encore ?!",
  "description": "Obtiens 100x une même carte",
  "iconUrl": null,
  "tokenReward": 50,
  "typeKey": "CARD_DUPLICATE_COUNT",
  "typeParameters": { "copies": 100 },
  "active": true,
  "hidden": false,
  "displayOrder": 0
}
```

Les paramètres sont validés contre la phrase **avant** persistance : un succès mal formé n'atteint jamais la boucle d'évaluation. La réponse contient `phrase`, le rendu lisible ("Obtiens 100 fois une même carte"), à afficher en aperçu sous le formulaire.

`hidden: true` masque titre, description et progression tant que le succès n'est pas débloqué : le joueur voit « Succès secret ».

`DELETE /api/admin/achievements/{id}` supprime le succès et ses déblocages, mais **laisse les tokens déjà versés** aux joueurs.

L'administration exige le rôle `ADMIN` : un succès frappe de la monnaie.

## Liste joueur

```http
GET /api/achievements
```

```json
{
  "unlockedCount": 7,
  "totalCount": 24,
  "completionPercentage": 29,
  "tokensEarned": 320,
  "achievements": [
    {
      "id": "…",
      "title": "Encore ?!",
      "description": "Obtiens 100x une même carte",
      "phrase": "Obtiens 100 fois une même carte",
      "iconUrl": null,
      "tokenReward": 50,
      "hidden": false,
      "unlocked": false,
      "unlockedAt": null,
      "progressCurrent": 47,
      "progressTarget": 100,
      "progressPercentage": 47
    }
  ]
}
```

La progression est **calculée à la lecture**, jamais stockée. C'est ce qui permet à un succès créé aujourd'hui d'afficher immédiatement la bonne progression sur l'historique existant, et ce qui garantit qu'aucun compteur ne peut dériver de la donnée qu'il décrit. `progressPercentage` est arrondi à l'inférieur : un succès inachevé n'affiche jamais 100 %.

Sur un succès `hidden` non débloqué, `phrase`, `iconUrl` et la progression sont neutralisés — révéler 3/10 vendrait la mèche.

## Popup

Le déblocage est une **file**, pas une diffusion. Un succès débloqué reste en attente jusqu'à ce que le client confirme l'avoir affiché :

```http
GET  /api/achievements/unseen
POST /api/achievements/unseen/acknowledge
```

```json
[
  {
    "unlockId": "…",
    "achievementId": "…",
    "title": "Encore ?!",
    "description": "Obtiens 100x une même carte",
    "iconUrl": null,
    "tokensAwarded": 50,
    "unlockedAt": "2026-08-24T21:14:03Z"
  }
]
```

Le corps de `acknowledge` est une liste d'`unlockId` ; vide ou absent, il acquitte tout ce qui est en attente. Un toast survit donc à un rafraîchissement de page, contrairement à un push qu'on manquerait.

Une notification `ACHIEVEMENT_UNLOCKED` est créée en parallèle, avec le même contenu dans son `payload`.

### Recommandation d'intégration

Interroger `/unseen` au même rythme que les notifications, et acquitter après affichage du toast. Le déblocage lui-même est immédiat côté serveur : il est évalué à la validation de l'action, pas au prochain scan.

## Complétion d'un ami

```http
GET /api/achievements/players/{userId}/completion
```

```json
{ "userId": "…", "unlockedCount": 12, "totalCount": 24, "completionPercentage": 50 }
```

Réservé au demandeur lui-même et à ses amis **acceptés** ; sinon `403`. Seuls les succès actifs comptent, au numérateur comme au dénominateur, pour qu'archiver un succès ne gonfle pas artificiellement un ratio.

## Quand un succès se débloque

Deux chemins, complémentaires par construction :

- **À chaud.** Le service métier publie un `AchievementTriggerEvent` ; le moteur réévalue ce joueur après commit, uniquement sur les succès dont la phrase écoute ce déclencheur. C'est ce qui fait tomber la popup pendant que le joueur regarde encore son pack. Les services métier annoncent à leur propre granularité — un pack de cinq cartes appelle cinq fois, une soumission de groupe une fois par membre y compris l'auteur —, et le publieur ne garde qu'un événement par couple (joueur, déclencheur) et par transaction : la réévaluation lit l'état commité, les répétitions rendraient la même réponse.
- **À froid.** Un balayage horaire (`0 30 * * * *`) rattrape ce que le chemin à chaud ne peut structurellement pas voir : un succès créé aujourd'hui sur l'historique d'hier, une action survenue pendant une indisponibilité, une condition qu'aucun événement métier n'annonce. Chaque phrase exclut en SQL les joueurs qui détiennent déjà le succès, de sorte que le balayage rétrécit à mesure qu'il attribue au lieu de re-parcourir chaque heure une population entièrement servie.

Le déblocage est idempotent via la contrainte unique `(achievement_id, user_id)` : deux déclencheurs concurrents s'y disputent, et le perdant signale « déjà débloqué » au lieu de payer deux fois.

Déclencheurs actuellement publiés : `CARD_OBTAINED` (pack, attribution admin, carte physique scannée), `CHALLENGE_VALIDATED` (décision de modération et validation automatique, pour tous les membres d'une soumission de groupe), `TOURNAMENT_SETTLED` (règlement d'un tournoi), `EVENT_REGISTERED` (inscription à un événement), `FRIEND_ACCEPTED` (les deux côtés d'une amitié acceptée), `TREASURE_CHECKPOINT_VALIDATED` (checkpoint validé), `QR_SCANNED` (premier scan uniquement, pas un re-scan), `PLACE_PIXEL_PLACED` (uniquement quand un pixel bonus est consommé).
