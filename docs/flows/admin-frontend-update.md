# Admin Frontend Update

Cette note liste les changements API à intégrer côté front admin après la mise à jour Events, Articles publiés et Défis de groupe.

## Events

Les DTO et payloads admin d'événements ont deux champs optionnels supplémentaires :

- `location`: lieu affichable côté joueur, chaîne jusqu'à 255 caractères, nullable.
- `coverImageUrl`: URL d'image de couverture affichable côté joueur, chaîne jusqu'à 2048 caractères, nullable.

Ces champs sont présents dans :

```http
GET /api/admin/events
GET /api/admin/events/{eventId}
POST /api/admin/events
PUT /api/admin/events/{eventId}
GET /api/events
```

Payload admin à envoyer en création/update :

```json
{
  "title": "Tournoi de volley",
  "shortDescription": "Tournoi ouvert aux équipes de 4 joueurs.",
  "location": "Gymnase INSA",
  "coverImageUrl": "https://s3.example.com/insastronaute-public/articles/.../cover.jpg",
  "source": "TOURNAMENT",
  "startsAt": "2026-09-12T09:00:00Z",
  "endsAt": "2026-09-12T18:00:00Z",
  "publishedAt": "2026-09-01T08:00:00Z",
  "registrationEnabled": true,
  "capacity": 24,
  "waitlistEnabled": true,
  "articleId": "22222222-2222-2222-2222-222222222222",
  "categoryIds": ["33333333-3333-3333-3333-333333333333"],
  "targetDepartmentIds": ["44444444-4444-4444-4444-444444444444"]
}
```

Front admin attendu :

- ajouter deux champs au formulaire event ;
- préremplir ces champs en édition ;
- préserver les valeurs existantes lors d'un `PUT`, car l'update remplace le payload editable complet ;
- afficher `location` et `coverImageUrl` dans la preview admin si elle existe.

Enums confirmés :

- `source`: `TOURNAMENT`, `GLOBAL`, `DEPARTMENT`.
- `currentUserRegistrationStatus`: `REGISTERED`, `WAITLISTED`, ou `null`.

## Articles

Les routes de gestion admin restent :

```http
GET /api/admin/articles
POST /api/admin/articles
GET /api/admin/articles/{articleId}
POST /api/admin/articles/{articleId}/media
GET /api/admin/articles/{articleId}/media
PUT /api/admin/articles/{articleId}
DELETE /api/admin/articles/{articleId}
```

Les articles publiés sont maintenant lisibles côté utilisateur authentifié :

```http
GET /api/articles
GET /api/articles/{articleId}
```

Impact front admin :

- ne pas déplacer les écrans d'édition admin vers `/api/articles` ;
- utiliser `/api/articles/{articleId}` uniquement pour une preview "comme côté joueur" si besoin ;
- les articles avec `status=WRITING` restent invisibles sur les routes utilisateur.

## Défis De Groupe

Les écrans admin/modération existants ne changent pas de route. Les nouveaux endpoints sont côté joueur, mais ils peuvent expliquer des états vus par les admins :

```http
GET /api/challenges/submissions/{submissionId}
DELETE /api/challenges/submissions/{submissionId}
POST /api/challenges/submissions/{submissionId}/participants/resend
```

La réponse joueur du détail contient `participants[]` avec :

```json
{
  "userId": "66666666-6666-6666-6666-666666666666",
  "displayName": "Alice",
  "email": "alice@example.com",
  "confirmationStatus": "PENDING",
  "invitedAt": "2026-06-16T12:01:00Z",
  "respondedAt": null,
  "submitter": false
}
```

Statuts participants :

- `ACCEPTED`
- `PENDING`
- `DECLINED`

`GET /api/challenges/submissions/results` expose maintenant aussi `submissionId`. Si le front admin partage des composants avec le front joueur, ne plus déduire l'id de soumission à partir du défi : utiliser directement `submissionId`.

## Checklist Front

- Ajouter `location` et `coverImageUrl` aux types TypeScript `EventDto` et `EventRequest`.
- Ajouter `submissionId` à `SubmissionResultDto`.
- Ajouter les champs de participant groupe si les types sont partagés.
- Garder les écritures d'articles sur `/api/admin/articles/**`.
- Rafraîchir les caches/listes events après création/update pour récupérer les deux nouveaux champs.
