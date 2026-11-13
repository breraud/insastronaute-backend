# Tournois — flow admin

Un tournoi est un événement dont `source = "TOURNAMENT"`. Les joueurs s'y inscrivent par les endpoints d'événement habituels ; l'onglet Tournois du PanelAdmin sert ensuite à pointer les présents et à convertir cette présence en points de classement.

Les points de tournoi remplacent l'ancienne métrique `TOKENS` du leaderboard. Les tokens restent le porte-monnaie dépensé dans les packs et le jeu, mais ne sont plus classés.

## Droits

`/api/admin/tournaments/**` est ouvert aux mêmes rôles que l'administration des événements : `ADMIN`, `RESPOS_EVENT`, `SOUS_RESPO_EVENT`, `RESPO_DEPART`, `SOUS_RESPO_DEPART`, `RESPO_TOURNOIS`, `SOUS_RESPO_TOURNOIS`.

Seule exception : inscrire un joueur à la main (§ 3 bis) est réservé à `ADMIN`, `RESPO_TOURNOIS` et `SOUS_RESPO_TOURNOIS`.

## 1. Lister les tournois

```http
GET /api/admin/tournaments
```

Renvoie tous les événements `TOURNAMENT`, du plus récent au plus ancien :

```json
[
  {
    "eventId": "11111111-1111-1111-1111-111111111111",
    "title": "Tournoi de volley",
    "location": "Gymnase",
    "startsAt": "2026-09-12T09:00:00Z",
    "endsAt": "2026-09-12T18:00:00Z",
    "hasEnded": true,
    "registeredCount": 24,
    "waitlistedCount": 3,
    "attendedCount": 21,
    "attendanceUnmarkedCount": 0,
    "settled": true,
    "settledAt": "2026-09-12T19:30:00Z",
    "participationPoints": 10,
    "totalPointsAwarded": 285
  }
]
```

`attendanceUnmarkedCount` compte les inscrits que personne n'a encore pointés : c'est l'indicateur « il reste du travail sur ce tournoi ».

## 2. Ouvrir un tournoi

```http
GET /api/admin/tournaments/{eventId}
```

```json
{
  "tournament": { "...": "identique au résumé ci-dessus" },
  "participants": [
    {
      "registrationId": "…",
      "userId": "…",
      "displayName": "Marie Dupont",
      "email": "marie@insa…",
      "departmentId": "…",
      "departmentShortName": "INFO",
      "status": "REGISTERED",
      "attended": true,
      "registeredAt": "2026-09-01T09:15:00Z",
      "awardedParticipationPoints": 10,
      "awardedVictoryPoints": 50,
      "awardedTotalPoints": 60,
      "awardedTierLabel": "1er",
      "awardedTeamName": "Les Fusées"
    }
  ],
  "victoryTiers": [
    { "position": 0, "label": "1er", "points": 50, "teamName": "Les Fusées", "memberUserIds": ["…", "…"] },
    { "position": 1, "label": "2e", "points": 25, "teamName": null, "memberUserIds": ["…"] }
  ]
}
```

Les participants sont triés inscrits d'abord, puis par `displayName`. La barre de recherche du panel filtre côté client sur `displayName`, `email` et `departmentShortName` — la liste est bornée, aucun aller-retour serveur n'est nécessaire.

`attended` est à trois états : `true` présent, `false` absent, `null` pas encore pointé. La distinction compte : `null` n'est pas une absence, c'est un travail en attente.

Les champs `awarded*` reflètent ce qui a **déjà** été crédité, pas ce qui sera crédité au prochain règlement. Ils sont à zéro tant que le tournoi n'a pas été validé.

## 3. Pointer les présences

```http
PUT /api/admin/tournaments/{eventId}/attendance
```

```json
{
  "entries": [
    { "userId": "…", "attended": true },
    { "userId": "…", "attended": false },
    { "userId": "…", "attended": null }
  ]
}
```

N'attribue aucun point : sert à sauvegarder le pointage au fil de l'eau, par exemple pendant l'accueil des joueurs. `attended: null` efface un pointage. Un `userId` non inscrit au tournoi renvoie `400`.

## 3 bis. Inscrire un joueur à la main

```http
POST /api/admin/tournaments/{eventId}/registrations
```

```json
{ "userId": "…" }
```

Pour le joueur qui se présente sans s'être inscrit. Réservé à `ADMIN`, `RESPO_TOURNOIS` et `SOUS_RESPO_TOURNOIS`. Renvoie le détail du tournoi, comme le § 2.

- Passe outre ce qui encadre l'inscription en ligne : inscriptions fermées, capacité, départements ciblés, blocage du staff.
- Le joueur entre toujours **inscrit**, jamais en liste d'attente, même si le tournoi est complet.
- Déjà en liste d'attente : il est confirmé. Déjà inscrit : rien ne change, et son pointage est conservé.
- Un compte supprimé ou inconnu renvoie `404`. Le joueur n'est pas notifié.

## 4. Attribuer les points

```http
POST /api/admin/tournaments/{eventId}/settle
```

```json
{
  "participationPoints": 10,
  "attendance": [ { "userId": "…", "attended": true } ],
  "victoryTiers": [
    { "label": "1er", "points": 50, "teamName": "Les Fusées", "memberUserIds": ["…", "…"] },
    { "label": "2e", "points": 25, "teamName": null, "memberUserIds": ["…"] }
  ],
  "notifyPlayers": true
}
```

- `attendance` est optionnel : il permet d'enregistrer le pointage final et de valider en un seul appel.
- `victoryTiers` est ordonné ; la `position` est dérivée de l'ordre du tableau. Un palier porte plusieurs `memberUserIds` d'un coup, ce qui couvre les victoires en équipe.
- `notifyPlayers` vaut `true` par défaut.

Chaque joueur marqué présent reçoit `participationPoints`. Les membres d'un palier reçoivent en plus les points du palier. Les paliers sont intégralement remplacés à chaque règlement : retirer un palier de l'interface le retire côté serveur.

Réponse :

```json
{
  "awardedPlayerCount": 21,
  "pointsGranted": 285,
  "pointsRevoked": 0,
  "notificationsSent": 21,
  "tournament": { "...": "le détail rafraîchi, comme au point 2" }
}
```

### Règles refusées en 400

- un vainqueur qui n'est pas marqué présent ;
- un joueur présent dans deux paliers ;
- un palier sans vainqueur ;
- un `userId` qui n'est pas inscrit au tournoi ;
- un événement dont la `source` n'est pas `TOURNAMENT`.

### Idempotence

Chaque attribution est écrite dans un registre (`tournament_point_awards`). Rejouer un règlement compare le nouveau résultat à ce registre et n'applique que la différence :

- valider deux fois de suite ne double pas les points ;
- passer la participation de 10 à 25 crédite 15 de plus, pas 25 ;
- décocher un joueur lui reprend ses points et supprime sa ligne de registre.

C'est ce qui rend l'écran corrigeable : on peut rouvrir un tournoi déjà validé, ajuster une présence ou un palier, revalider, et le solde de chacun reste juste.

## 5. Annuler un règlement

```http
DELETE /api/admin/tournaments/{eventId}/settlement
```

Reprend tous les points attribués sur ce tournoi et vide le registre. Le pointage des présences est conservé, ce qui permet de revalider derrière avec un autre barème.

## Notifications

Chaque joueur dont le solde bouge reçoit une notification `TOURNAMENT_POINTS_AWARDED` :

```json
{
  "type": "TOURNAMENT_POINTS_AWARDED",
  "title": "Points de tournoi reçus",
  "body": "Tu as gagné 60 points de tournoi pour « Tournoi de volley » : 10 de participation et 50 pour la place 1er avec Les Fusées.",
  "payload": {
    "eventId": "…",
    "eventTitle": "Tournoi de volley",
    "participationPoints": 10,
    "victoryPoints": 50,
    "totalPoints": 60,
    "tierLabel": "1er",
    "teamName": "Les Fusées",
    "sourceType": "TOURNAMENT"
  }
}
```

Un joueur dont le solde ne bouge pas n'est pas notifié : revalider sans rien changer n'envoie rien. Un échec d'envoi est journalisé et ne fait jamais perdre les points déjà crédités.
