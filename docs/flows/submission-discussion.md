# Submission Discussion Lifecycle

## Lifecycle de discussion de soumission

Chaque soumission possède un fil de discussion partagé entre les membres de la soumission et l'équipe de modération.

### Visibilité

Côté joueur :

- route de lecture : `GET /api/challenges/submissions/{submissionId}/messages` ;
- route d'écriture : `POST /api/challenges/submissions/{submissionId}/messages` ;
- seuls le submitter et les participants de la soumission peuvent accéder au fil ;
- les messages de modération sont anonymisés avec `authorName: "Équipe de modération"` ;
- les preuves ne sont pas exposées par ces endpoints.

Côté modération :

- route de lecture : `GET /api/moderation/submissions/{submissionId}/messages` ;
- route d'écriture : `POST /api/moderation/submissions/{submissionId}/messages` ;
- les mêmes règles d'éligibilité que la file de modération s'appliquent ;
- les identités réelles des auteurs sont visibles ;
- les médias de preuve restent accessibles séparément avec `GET /api/moderation/submissions/{submissionId}/proofs/{index}/content` après prise du verrou de review.

### Ouverture et fermeture

Par défaut, les joueurs ne peuvent pas écrire dans le fil.

Le fil devient écrivable côté joueur dans deux cas :

1. un modérateur poste le premier message ;
2. un modérateur active explicitement l'écriture avec `PATCH /api/moderation/submissions/{submissionId}/messages/enabled`.

Le premier message modération ouvre donc automatiquement la discussion joueur.

Un modérateur peut ouvrir ou fermer l'écriture joueur :

```http
PATCH /api/moderation/submissions/44444444-4444-4444-4444-444444444444/messages/enabled
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "enabled": true
}
```

ou :

```json
{
  "enabled": false
}
```

Fermer le fil bloque seulement les nouveaux messages joueur. Les modérateurs gardent la lecture et peuvent le rouvrir.

### Écriture joueur

Une fois le fil ouvert, un joueur peut poster :

```http
POST /api/challenges/submissions/44444444-4444-4444-4444-444444444444/messages
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "content": "J'ai ajouté une précision sur la vidéo."
}
```

Réponse :

```json
{
  "id": "55555555-5555-5555-5555-555555555555",
  "authorType": "PLAYER",
  "authorUserId": "99999999-9999-9999-9999-999999999999",
  "authorName": "Alice",
  "currentUser": true,
  "content": "J'ai ajouté une précision sur la vidéo.",
  "createdAt": "2026-06-24T10:00:00Z"
}
```

Si aucun modérateur n'a encore ouvert le fil, l'API renvoie `403 Forbidden`.

### Écriture modération

Un modérateur peut poster :

```http
POST /api/moderation/submissions/44444444-4444-4444-4444-444444444444/messages
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "content": "Peux-tu préciser où se trouve l'élément demandé ?"
}
```

Ce message ouvre automatiquement l'écriture joueur si elle était encore fermée.

### Soumission refusée

Une soumission refusée ne peut plus recevoir de nouveaux messages :

- `POST /api/challenges/submissions/{submissionId}/messages` renvoie `409 Conflict` ;
- `POST /api/moderation/submissions/{submissionId}/messages` renvoie `409 Conflict` ;
- `PATCH /api/moderation/submissions/{submissionId}/messages/enabled` renvoie `409 Conflict`.

Le fil reste lisible selon les droits habituels.

### Lien avec les anciens commentaires

Le champ `comment` d'une soumission `MEDIA` reste accepté comme remarque initiale et devient aussi un message joueur dans le fil.
Le champ `moderationComment` d'un refus reste accepté pour la décision et devient aussi un message modération dans le fil.
