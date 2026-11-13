# Submission Moderation Flow

## Récupérer une preuve soumise en modération

Une preuve soumise ne se récupère pas avec les routes de brouillon. Le modérateur passe par les routes de modération, et l'API relaie les médias privés sans exposer de clé ni d'URL S3.

Préconditions :

- l'utilisateur est authentifié ;
- l'utilisateur a un rôle de modération autorisé ;
- la soumission est visible pour ce modérateur selon les règles de sensibilité, genre et département ;
- pour lire les fichiers ou décider la soumission, le modérateur doit posséder le verrou de review.

### 1. Lister les soumissions à traiter

```http
GET /api/moderation/submissions/pending
```

Réponse simplifiée :

```json
[
  {
    "submissionId": "44444444-4444-4444-4444-444444444444",
    "challengeTitle": "Photo equipe",
    "playerName": "Alice",
    "playerDepartment": "INFO",
    "proofType": "MEDIA",
    "proofMedia": [
      { "index": 0, "contentType": "image/jpeg" }
    ],
    "comment": "Voici ma preuve",
    "reviewLocked": false,
    "reviewLockedByCurrentUser": false,
    "submittedAt": "2026-06-18T12:00:00Z"
  }
]
```

`proofMedia` ne contient que des descripteurs opaques. Le front construit la route de lecture backend avec le `submissionId` et l'`index`.

Pour une preuve `TEXT`, il n'y a pas d'URL à demander : le contenu de la preuve est dans `proofData`.

### 2. Récupérer le détail d'une soumission

Le front peut charger l'écran détail avec un seul endpoint :

```http
GET /api/moderation/submissions/44444444-4444-4444-4444-444444444444
```

Réponse simplifiée :

```json
{
  "submissionId": "44444444-4444-4444-4444-444444444444",
  "status": "PENDING",
  "submitterName": "Alice",
  "submitterDepartment": "Département Alpha",
  "proofType": "MEDIA",
  "proofData": null,
  "proofMedia": [{ "index": 0, "contentType": "image/jpeg" }],
  "comment": "Voici ma preuve",
  "moderationComment": null,
  "bonusPoints": 10,
  "messagesEnabled": true,
  "reviewLocked": true,
  "reviewLockedByCurrentUser": true,
  "reviewStartedAt": "2026-06-24T10:00:00Z",
  "challenge": {
    "id": "11111111-1111-1111-1111-111111111111",
    "title": "Équations linéaires",
    "description": "Résoudre correctement l'exercice.",
    "pointsReward": 150,
    "category": "Mathématiques",
    "validationMode": "ADMIN",
    "sensitive": false,
    "minGroupSize": 1,
    "maxGroupSize": 3
  },
  "participants": [
    {
      "userId": "99999999-9999-9999-9999-999999999999",
      "displayName": "Alice",
      "email": "alice@example.com"
    }
  ],
  "messages": [
    {
      "authorType": "PLAYER",
      "authorName": "Alice",
      "content": "Voici une précision.",
      "currentUser": false
    }
  ]
}
```

Cet endpoint ne renvoie ni clé de stockage ni URL temporaire. Il expose uniquement les descripteurs (`index` et `contentType`) nécessaires pour demander le contenu au backend après acquisition du verrou de review.

### 3. Prendre le verrou de review

```http
POST /api/moderation/submissions/44444444-4444-4444-4444-444444444444/review-lock
X-XSRF-TOKEN: csrf-token-exemple
```

Réponse :

```http
204 No Content
```

Le verrou expire après 15 minutes. Si une autre personne possède déjà le verrou, l'API renvoie `409 Conflict`.

### 4. Lire une preuve média via le backend

```http
GET /api/moderation/submissions/44444444-4444-4444-4444-444444444444/proofs/0/content
Range: bytes=0-
```

Le backend revérifie à chaque lecture le rôle de modération, l'accès au défi et la possession du verrou, puis relaie le flux depuis le stockage privé. Il répond `200 OK` ou `206 Partial Content` pour les vidéos, sans révéler l'adresse S3. Les réponses sont marquées `Cache-Control: no-store`.

Cas d'erreur importants :

- `400 Bad Request` si la preuve est de type `TEXT`, car il n'y a pas de fichier ;
- `403 Forbidden` si le modérateur n'a pas le droit de consulter cette soumission ;
- `409 Conflict` si le verrou de review n'appartient pas au modérateur courant.

### 5. Décider ou relâcher le verrou

Après consultation, le modérateur peut décider :

```http
POST /api/moderation/submissions/44444444-4444-4444-4444-444444444444/decide
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "status": "VALIDATED",
  "moderationComment": null
}
```

ou, s'il quitte la review sans décider :

```http
DELETE /api/moderation/submissions/44444444-4444-4444-4444-444444444444/review-lock
X-XSRF-TOKEN: csrf-token-exemple
```
