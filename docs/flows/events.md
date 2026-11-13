# Events

Ce document décrit le flow côté admin et côté joueur pour créer, publier, consulter, s'inscrire et gérer les listes d'attente des événements.

Les événements couvrent trois sources :

- `TOURNAMENT` : événements issus des tournois ;
- `GLOBAL` : événements globaux ;
- `DEPARTMENT` : événements portés par un ou plusieurs départements.

Les permissions fines par source ou département seront ajoutées plus tard. Pour l'instant, toute la gestion admin passe par `/api/admin/events` et nécessite le rôle `ADMIN`.

## Règles générales

L'utilisateur doit être authentifié pour lire les événements visibles et gérer ses inscriptions.

Pour les requêtes navigateur qui modifient l'état (`POST`, `PUT`, `DELETE`), le client envoie le token CSRF.

```http
GET /api/csrf
```

Ensuite :

```http
X-XSRF-TOKEN: csrf-token-exemple
```

Un événement est visible par un joueur uniquement si :

- `publishedAt` est renseigné ;
- `publishedAt` est dans le passé ;
- l'événement n'a aucun département cible, ou le département du joueur fait partie des départements ciblés.

Un événement est indicatif si `registrationEnabled=false`.

Un événement accepte des inscriptions si `registrationEnabled=true`.

Pour les quotas :

- `capacity: null` ou `capacity: 0` signifie inscriptions illimitées ;
- `capacity > 0` limite le nombre d'inscrits `REGISTERED` ;
- si le quota est plein et `waitlistEnabled=true`, les nouvelles inscriptions passent en `WAITLISTED` ;
- si le quota est plein et `waitlistEnabled=false`, l'API retourne `409 Conflict`.

Quand un joueur désinscrit une place `REGISTERED`, le plus ancien joueur `WAITLISTED` est automatiquement promu en `REGISTERED`.

## Modèle Event

Réponse type :

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "Tournoi de volley",
  "shortDescription": "Tournoi ouvert aux équipes de 4 joueurs.",
  "location": "Gymnase INSA",
  "coverImageUrl": "https://s3.example.com/insastronaute-public/events/11111111-1111-1111-1111-111111111111/cover/cover.jpg",
  "source": "TOURNAMENT",
  "startsAt": "2026-09-12T09:00:00Z",
  "endsAt": "2026-09-12T18:00:00Z",
  "publishedAt": "2026-09-01T08:00:00Z",
  "registrationEnabled": true,
  "capacity": 24,
  "waitlistEnabled": true,
  "article": {
    "id": "22222222-2222-2222-2222-222222222222",
    "title": "Règles du tournoi de volley"
  },
  "categories": [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "name": "Sport",
      "slug": "sport"
    }
  ],
  "targetDepartments": [
    {
      "id": "44444444-4444-4444-4444-444444444444",
      "name": "Informatique",
      "shortName": "INFO"
    }
  ],
  "registeredCount": 24,
  "waitlistedCount": 3,
  "currentUserRegistrationStatus": "WAITLISTED",
  "registeredFriends": [
    {
      "userId": "55555555-5555-5555-5555-555555555555",
      "displayName": "Marie Dupont",
      "departmentId": "44444444-4444-4444-4444-444444444444",
      "status": "REGISTERED"
    },
    {
      "userId": "66666666-6666-6666-6666-666666666666",
      "displayName": "Paul Martin",
      "departmentId": null,
      "status": "WAITLISTED"
    }
  ],
  "createdAt": "2026-08-20T10:00:00Z",
  "updatedAt": "2026-08-21T12:00:00Z"
}
```

`currentUserRegistrationStatus` vaut `REGISTERED`, `WAITLISTED`, ou `null`. Il est surtout utile côté joueur. Dans les réponses admin, il est `null`.

`registeredFriends` liste les amis acceptés du joueur courant qui ont une inscription sur cet événement, triés par `displayName`. Chaque entrée porte son propre `status` : `REGISTERED` pour une place confirmée, `WAITLISTED` pour une place en liste d'attente. Pour n'afficher que les amis réellement inscrits, filtrer sur `status="REGISTERED"`. Le tableau est vide quand le joueur n'a aucun ami inscrit, et il est toujours vide dans les réponses admin, qui ne sont rattachées à aucun joueur. Seules les amitiés `ACCEPTED` sont prises en compte : les demandes en attente n'apparaissent pas.

## Flow Admin

### 1. Préparer les dépendances

Pour construire le formulaire admin, le client charge généralement :

```http
GET /api/admin/event-categories
GET /api/departments
GET /api/admin/articles
```

Les articles sont optionnels. Un event peut exister sans article.

Les départements cibles sont optionnels. Une liste vide signifie visible par tous les joueurs authentifiés.

### 2. Gérer les catégories

Lister :

```http
GET /api/admin/event-categories
```

Créer :

```http
POST /api/admin/event-categories
X-XSRF-TOKEN: csrf-token-exemple
Content-Type: application/json
```

```json
{
  "name": "Sport"
}
```

Modifier :

```http
PUT /api/admin/event-categories/{categoryId}
X-XSRF-TOKEN: csrf-token-exemple
Content-Type: application/json
```

```json
{
  "name": "Soirée"
}
```

Supprimer :

```http
DELETE /api/admin/event-categories/{categoryId}
X-XSRF-TOKEN: csrf-token-exemple
```

### 3. Lister les événements admin

```http
GET /api/admin/events
```

Retourne tous les événements, publiés ou non, triés par date de début.

Le client peut afficher :

- statut de publication : `publishedAt` vide, futur, ou passé ;
- source ;
- dates ;
- quota ;
- nombre d'inscrits et de waitlistés.

### 4. Créer un événement

```http
POST /api/admin/events
X-XSRF-TOKEN: csrf-token-exemple
Content-Type: application/json
```

Payload :

```json
{
  "title": "Tournoi de volley",
  "shortDescription": "Tournoi ouvert aux équipes de 4 joueurs.",
  "location": "Gymnase INSA",
  "coverImageUrl": "https://cdn.example.com/events/volley-cover.jpg",
  "source": "TOURNAMENT",
  "startsAt": "2026-09-12T09:00:00Z",
  "endsAt": "2026-09-12T18:00:00Z",
  "publishedAt": "2026-09-01T08:00:00Z",
  "registrationEnabled": true,
  "capacity": 24,
  "waitlistEnabled": true,
  "articleId": "22222222-2222-2222-2222-222222222222",
  "categoryIds": [
    "33333333-3333-3333-3333-333333333333"
  ],
  "targetDepartmentIds": [
    "44444444-4444-4444-4444-444444444444"
  ]
}
```

Réponse `201 Created` : l'objet event complet.

Champs requis :

- `title` ;
- `shortDescription` ;
- `source` ;
- `startsAt`.

Champs optionnels :

- `location` ;
- `coverImageUrl` ;
- `endsAt` ;
- `publishedAt` ;
- `registrationEnabled` ;
- `capacity` ;
- `waitlistEnabled` ;
- `articleId` ;
- `categoryIds` ;
- `targetDepartmentIds`.

Pour créer un brouillon invisible, envoyer `publishedAt: null` ou omettre le champ.

`coverImageUrl` accepte une URL externe HTTP(S). Si l'ancienne cover était une image uploadée dans S3 par l'API, changer cette URL supprime l'ancien objet S3 en best effort.

Pour créer un événement informatif sans inscription :

```json
{
  "registrationEnabled": false,
  "capacity": null,
  "waitlistEnabled": false
}
```

### 5. Modifier un événement

```http
PUT /api/admin/events/{eventId}
X-XSRF-TOKEN: csrf-token-exemple
Content-Type: application/json
```

Le payload est le même que pour la création. L'update remplace les champs modifiables, y compris :

- dates ;
- publication ;
- quota ;
- article lié ;
- catégories ;
- départements cibles.

Attention côté produit : réduire `capacity` sous le nombre actuel d'inscrits ne désinscrit pas automatiquement les joueurs déjà `REGISTERED`. La limite s'applique aux nouvelles inscriptions.

### 6. Uploader une cover d'événement

Utiliser cette route quand l'admin choisit une image locale. Le backend détecte le vrai type de fichier, accepte JPEG, PNG et WebP jusqu'à 15 Mo, stocke l'objet dans le bucket public S3, met à jour `coverImageUrl`, et supprime l'ancienne cover S3 gérée par l'API si elle existait.

```http
POST /api/admin/events/{eventId}/cover
X-XSRF-TOKEN: csrf-token-exemple
Content-Type: multipart/form-data
```

Form fields :

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `file` | File | yes | JPEG, PNG, or WebP. Maximum size: 15 MB. |

Réponse `200 OK` : l'objet event complet.

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "Tournoi de volley",
  "shortDescription": "Tournoi ouvert aux équipes de 4 joueurs.",
  "location": "Gymnase INSA",
  "coverImageUrl": "https://s3.example.com/insastronaute-public/events/11111111-1111-1111-1111-111111111111/cover/2c6f8c20-feb4-4ef5-99b6-5c65db78e7b3-cover.jpg",
  "source": "TOURNAMENT",
  "startsAt": "2026-09-12T09:00:00Z",
  "endsAt": "2026-09-12T18:00:00Z",
  "publishedAt": "2026-09-01T08:00:00Z",
  "registrationEnabled": true,
  "capacity": 24,
  "waitlistEnabled": true,
  "article": null,
  "categories": [],
  "targetDepartments": [],
  "registeredCount": 0,
  "waitlistedCount": 0,
  "currentUserRegistrationStatus": null,
  "registeredFriends": [],
  "createdAt": "2026-08-20T10:00:00Z",
  "updatedAt": "2026-08-21T12:00:00Z"
}
```

Les covers uploadées sont stockées sous :

```text
events/{eventId}/cover/{generatedId}-{filename}
```

### 7. Supprimer un événement

```http
DELETE /api/admin/events/{eventId}
X-XSRF-TOKEN: csrf-token-exemple
```

Effet backend :

- l'événement est supprimé ;
- ses inscriptions et waitlist entries sont supprimées par cascade.
- sa cover S3 gérée par l'API est supprimée en best effort.

### 8. Récupérer les inscrits et la waitlist

Tous les inscrits, `REGISTERED` puis `WAITLISTED` :

```http
GET /api/admin/events/{eventId}/registrants
```

Réponse :

```json
[
  {
    "registrationId": "55555555-5555-5555-5555-555555555555",
    "status": "REGISTERED",
    "registeredAt": "2026-09-01T09:15:00Z",
    "userId": "66666666-6666-6666-6666-666666666666",
    "email": "alice@example.com",
    "displayName": "Alice",
    "departmentId": "44444444-4444-4444-4444-444444444444"
  }
]
```

Uniquement la liste d'attente :

```http
GET /api/admin/events/{eventId}/waitlist
```

Le tri suit l'ordre d'arrivée.

## Flow Joueur

### 1. Lister les événements visibles

```http
GET /api/events
```

Le backend filtre automatiquement selon publication et département cible.

Le client peut grouper les événements par date, source ou catégorie. Pour afficher l'état d'inscription du joueur courant, lire `currentUserRegistrationStatus`.

États UI recommandés :

- `registrationEnabled=false` : afficher l'événement sans bouton d'inscription ;
- `currentUserRegistrationStatus=REGISTERED` : afficher "Inscrit" et une action de désinscription ;
- `currentUserRegistrationStatus=WAITLISTED` : afficher "En liste d'attente" et une action de désinscription ;
- `currentUserRegistrationStatus=null` et places disponibles : afficher "S'inscrire" ;
- `currentUserRegistrationStatus=null`, quota plein, waitlist activée : afficher "Rejoindre la liste d'attente" ;
- `currentUserRegistrationStatus=null`, quota plein, waitlist désactivée : afficher complet.

Le client peut considérer un quota comme plein quand :

```text
capacity > 0 && registeredCount >= capacity
```

Pour l'accroche sociale, lire `registeredFriends` sur chaque événement : afficher les amis dont `status="REGISTERED"` comme inscrits, et ceux dont `status="WAITLISTED"` comme en liste d'attente. Le tableau est déjà trié par `displayName`, donc un rendu tronqué du type "Marie, Paul et 3 autres amis y vont" est stable d'un appel à l'autre. Aucun appel supplémentaire à `/api/friends` n'est nécessaire.

### 2. S'inscrire

```http
POST /api/events/{eventId}/registrations
X-XSRF-TOKEN: csrf-token-exemple
```

Réponse :

```json
{
  "id": "55555555-5555-5555-5555-555555555555",
  "event": {
    "id": "11111111-1111-1111-1111-111111111111",
    "title": "Tournoi de volley",
    "currentUserRegistrationStatus": "REGISTERED"
  },
  "status": "REGISTERED",
  "registeredAt": "2026-09-01T09:15:00Z"
}
```

Le statut retourné peut être :

- `REGISTERED` si une place est disponible ou si l'événement est illimité ;
- `WAITLISTED` si le quota est plein et la waitlist est activée.

Erreurs courantes :

- `400 Bad Request` : inscriptions fermées ;
- `404 Not Found` : event introuvable ou non visible pour ce joueur ;
- `409 Conflict` : déjà inscrit, ou quota plein sans waitlist.

### 3. Se désinscrire

```http
DELETE /api/events/{eventId}/registrations
X-XSRF-TOKEN: csrf-token-exemple
```

Réponse `200 OK` sans payload.

Effet backend :

- l'inscription du joueur est supprimée ;
- si le joueur libère une place `REGISTERED`, le plus ancien `WAITLISTED` passe automatiquement en `REGISTERED`.

Le client devrait recharger `/api/events` ou `/api/events/registrations` après l'action.

### 4. Récupérer ses inscriptions

```http
GET /api/events/registrations
```

Retourne les événements où le joueur est `REGISTERED` ou `WAITLISTED`.

```json
[
  {
    "id": "55555555-5555-5555-5555-555555555555",
    "event": {
      "id": "11111111-1111-1111-1111-111111111111",
      "title": "Tournoi de volley",
      "startsAt": "2026-09-12T09:00:00Z",
      "currentUserRegistrationStatus": "REGISTERED"
    },
    "status": "REGISTERED",
    "registeredAt": "2026-09-01T09:15:00Z"
  }
]
```

### 5. Récupérer uniquement ses listes d'attente

```http
GET /api/events/waitlist
```

Retourne seulement les inscriptions `WAITLISTED`.

## Notes frontend

Les dates sont des `Instant` ISO 8601 en UTC. Le frontend peut les afficher dans la timezone utilisateur.

Le backend ne ferme pas encore automatiquement les inscriptions après `endsAt`. Si le produit veut ce comportement, il faudra ajouter une règle dédiée.

Le backend ne notifie pas encore les joueurs promus depuis la waitlist. Après une désinscription, les clients doivent donc recharger leur état ou attendre une future feature de notification.

Les events liés à des tournois sont représentés par `source=TOURNAMENT`, mais il n'existe pas encore de table de liaison tournoi dans cette première version.
