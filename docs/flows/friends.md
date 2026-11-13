# Flow client des amis

Ce document décrit le flow côté client pour chercher des utilisateurs, envoyer une demande d'ami, accepter ou refuser une demande, et lister ses amis.

Objectif du contrat :

- le client cherche des utilisateurs via `/api/friends/search` ;
- une demande d'ami est toujours créée avec le statut `PENDING` ;
- seul le destinataire de la demande peut l'accepter ou la refuser ;
- une amitié acceptée est représentée par une seule ligne en base, peu importe le sens requester/addressee ;
- les notifications informent l'utilisateur, mais ne remplacent pas les endpoints d'action.

## Règles générales

L'utilisateur doit être authentifié.

Pour les requêtes navigateur qui modifient l'état (`POST`, `DELETE`, etc.), le client envoie le token CSRF.

```http
GET /api/csrf
```

Réponse :

```json
{
  "parameterName": "_csrf",
  "headerName": "X-XSRF-TOKEN",
  "token": "csrf-token-exemple"
}
```

Ensuite :

```http
X-XSRF-TOKEN: csrf-token-exemple
```

## Chercher des utilisateurs

Le client cherche dans les utilisateurs de l'application pour envoyer des demandes d'ami.

```http
GET /api/friends/search?query=alice
```

`query` est obligatoire et doit contenir au moins 3 caractères après trim. Le client ne doit donc pas déclencher la recherche pour `a`, `al`, ou une chaîne vide.

Réponse :

```json
{
  "content": [
    {
      "user": {
        "id": "11111111-1111-1111-1111-111111111111",
        "displayName": "Alice",
        "email": "alice@example.com",
        "departmentId": "22222222-2222-2222-2222-222222222222"
      },
      "relationStatus": "NONE"
    }
  ]
}
```

Valeurs possibles de `relationStatus` :

- `NONE` : aucune relation, le client peut afficher "Ajouter" ;
- `PENDING_SENT` : l'utilisateur courant a déjà envoyé une demande ;
- `PENDING_RECEIVED` : l'autre utilisateur a déjà envoyé une demande à l'utilisateur courant ;
- `FRIEND` : les deux utilisateurs sont déjà amis.

## Chercher ses contacts

Pour certains flows, par exemple choisir des personnes liées au joueur courant, le client peut chercher uniquement :

- les utilisateurs du même département que l'utilisateur courant ;
- les amis déjà acceptés, même s'ils ne sont pas dans le même département.

```http
GET /api/friends/contacts/search?query=alice
```

`query` suit la même règle que `/api/friends/search` : obligatoire, au moins 3 caractères après trim.

La réponse a le même format que `/api/friends/search`, avec `user` et `relationStatus`.

## Envoyer une demande d'ami

Si `relationStatus` vaut `NONE`, le client peut envoyer une demande.

```http
POST /api/friends/requests/11111111-1111-1111-1111-111111111111
X-XSRF-TOKEN: csrf-token-exemple
```

Réponse :

```json
{
  "id": "33333333-3333-3333-3333-333333333333",
  "requester": {
    "id": "44444444-4444-4444-4444-444444444444",
    "displayName": "Brieuc",
    "email": "brieuc@example.com",
    "departmentId": "55555555-5555-5555-5555-555555555555"
  },
  "addressee": {
    "id": "11111111-1111-1111-1111-111111111111",
    "displayName": "Alice",
    "email": "alice@example.com",
    "departmentId": "22222222-2222-2222-2222-222222222222"
  },
  "status": "PENDING",
  "createdAt": "2026-06-20T13:00:00Z",
  "respondedAt": null
}
```

Effet backend :

- une ligne `friendships` est créée avec `requester = utilisateur courant`, `addressee = utilisateur cible`, `status = PENDING` ;
- le destinataire reçoit une notification `FRIEND_REQUEST_RECEIVED`.

Payload de la notification reçue :

```json
{
  "friendRequestId": "33333333-3333-3333-3333-333333333333",
  "requesterUserId": "44444444-4444-4444-4444-444444444444"
}
```

## Notifications et actions

Les notifications sont informatives. Elles ne font pas l'acceptation directement.

Le client peut afficher une action depuis une notification, mais cette action doit appeler l'endpoint friends correspondant.

Exemple :

1. Le client reçoit ou liste une notification `FRIEND_REQUEST_RECEIVED`.
2. Le client lit `payload.friendRequestId`.
3. Si l'utilisateur clique sur "Accepter", le client appelle :

```http
POST /api/friends/requests/33333333-3333-3333-3333-333333333333/accept
X-XSRF-TOKEN: csrf-token-exemple
```

La notification elle-même peut être marquée comme lue séparément :

```http
PATCH /api/notifications/{notificationId}/read
X-XSRF-TOKEN: csrf-token-exemple
```

Marquer une notification comme lue ne change jamais le statut de la demande d'ami.

## Lister les demandes reçues

```http
GET /api/friends/requests/incoming
```

Retourne les demandes `PENDING` dont l'utilisateur courant est le destinataire.

## Accepter une demande

Seul le destinataire de la demande peut accepter.

```http
POST /api/friends/requests/33333333-3333-3333-3333-333333333333/accept
X-XSRF-TOKEN: csrf-token-exemple
```

Effet backend :

- la ligne `friendships` passe à `status = ACCEPTED` ;
- `respondedAt` est renseigné ;
- le demandeur initial reçoit une notification `FRIEND_REQUEST_ACCEPTED`.

Payload de la notification envoyée au demandeur initial :

```json
{
  "friendshipId": "33333333-3333-3333-3333-333333333333",
  "friendUserId": "11111111-1111-1111-1111-111111111111"
}
```

## Refuser une demande

Seul le destinataire de la demande peut refuser.

```http
DELETE /api/friends/requests/33333333-3333-3333-3333-333333333333/decline
X-XSRF-TOKEN: csrf-token-exemple
```

Effet backend :

- la demande `PENDING` est supprimée ;
- aucune amitié n'est créée.

## Annuler une demande envoyée

Le demandeur peut annuler sa propre demande tant qu'elle est encore `PENDING`.

```http
DELETE /api/friends/requests/33333333-3333-3333-3333-333333333333
X-XSRF-TOKEN: csrf-token-exemple
```

## Lister les demandes envoyées

```http
GET /api/friends/requests/outgoing
```

Retourne les demandes `PENDING` créées par l'utilisateur courant.

## Lister ses amis

```http
GET /api/friends
```

Réponse :

```json
[
  {
    "friendshipId": "33333333-3333-3333-3333-333333333333",
    "user": {
      "id": "11111111-1111-1111-1111-111111111111",
      "displayName": "Alice",
      "email": "alice@example.com",
      "departmentId": "22222222-2222-2222-2222-222222222222"
    },
    "friendsSince": "2026-06-20T13:05:00Z"
  }
]
```

## Consulter le profil d'un ami

Le client peut afficher le profil d'un ami accepté : un non-ami reçoit `404` pour ne pas révéler l'existence d'un compte.

```http
GET /api/friends/11111111-1111-1111-1111-111111111111/profile
```

Réponse :

```json
{
  "fullName": "Alice",
  "department": "ITI",
  "commonFriendsCount": 3,
  "virtualCardsCompletionPercent": 25.0,
  "physicalCardsCompletionPercent": 50.0,
  "challengesCompletionPercent": 75.0,
  "challengePointsRank": 4
}
```

Les pourcentages de cartes comptent les cartes actives distinctes de chaque collection. La progression de défis porte sur les défis actifs et accessibles à l'ami ; le rang correspond au dernier snapshot publié du classement `CHALLENGE_POINTS` et peut être `null` avant sa première publication.

## Supprimer un ami

Chaque membre de l'amitié peut supprimer la relation.

```http
DELETE /api/friends/11111111-1111-1111-1111-111111111111
X-XSRF-TOKEN: csrf-token-exemple
```

Effet backend :

- la ligne `friendships` acceptée entre les deux utilisateurs est supprimée.

## Contraintes importantes

- Un utilisateur ne peut pas s'ajouter lui-même.
- Il ne peut exister qu'une seule relation entre deux utilisateurs.
- Une relation inverse ne peut pas être créée en doublon.
- Une demande déjà en attente bloque une nouvelle demande dans l'autre sens.
- Une amitié déjà acceptée bloque toute nouvelle demande.
