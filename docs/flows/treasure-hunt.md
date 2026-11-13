# Chasse au trésor

Ce document décrit l'API de configuration et de jeu de la chasse au trésor.

Les joueurs doivent être authentifiés. Les routes d'administration sont sous `/api/admin/treasure-hunt/**` et nécessitent le rôle `ADMIN`. Toute requête navigateur qui modifie l'état doit fournir le header CSRF `X-XSRF-TOKEN` obtenu via `GET /api/csrf`.

## Modèle

La chasse contient six voies, chacune liée à un département. Leur `position`, de 1 à 6, définit l'ordre des codes demandés pour ouvrir le coffre. Une voie contient des étapes (`checkpoints`) numérotées et validées dans l'ordre.

Chaque étape et le coffre contiennent une liste ordonnée de contenus :

- `RICH_TEXT` : texte riche ;
- `MEDIA` : image, vidéo ou PDF privé ;
- `ARTICLE` : lien vers un article existant avec `articleId` ;
- `FINAL_CODE` : délivre le code personnel de coffre de la voie.

Il est possible d'ajouter, modifier, supprimer et réordonner librement ces contenus. `FINAL_CODE` ne peut pas être ajouté au coffre et doit être placé sur la dernière étape de sa voie. Une voie peut temporairement ne pas en contenir pendant sa préparation.

Les médias sont stockés dans le bucket privé sous `treasure-hunt/`. JPEG, PNG, WebP, MP4, WebM, QuickTime et PDF sont acceptés, jusqu'à 15 MB. Les URL retournées aux joueurs sont pré-signées pour 15 minutes.

## Configuration admin

### Activer la chasse

Pendant la préparation, la chasse est inaccessible aux joueurs. Un administrateur peut consulter son état puis l'activer ou la désactiver :

```text
GET  /api/admin/treasure-hunt/status
POST /api/admin/treasure-hunt/activate
POST /api/admin/treasure-hunt/deactivate
```

L'activation vérifie qu'il y a six voies de départements distincts, exactement trois étapes codées par voie, un unique `FINAL_CODE` sur la troisième étape de chaque voie, et au moins un contenu dans le coffre. En cas de configuration incomplète, elle conserve l'état inactif et retourne `{"active":false,"violations":["..."]}`.

Toute création, modification, suppression ou réorganisation de la configuration désactive automatiquement la chasse. Le client d'administration doit donc relire `GET /status` après chaque mutation et demander une nouvelle activation lorsque la configuration est prête.

### Initialiser et organiser les voies

L'initialisation n'est possible qu'une seule fois et requiert six départements distincts :

```http
POST /api/admin/treasure-hunt/paths/initialize
Content-Type: application/json

{"departmentIds":["...", "...", "...", "...", "...", "..."]}
```

Lister et renommer une voie :

```text
GET /api/admin/treasure-hunt/paths
PUT /api/admin/treasure-hunt/paths/{position}   { "name": "Voie informatique" }
```

Réordonner les six voies en fournissant exactement leurs six identifiants :

```http
PUT /api/admin/treasure-hunt/paths/order
Content-Type: application/json

{"ids":["path-6", "path-1", "path-2", "path-3", "path-4", "path-5"]}
```

### Configurer les étapes

```text
GET    /api/admin/treasure-hunt/paths/{pathId}/checkpoints
POST   /api/admin/treasure-hunt/paths/{pathId}/checkpoints       { "code": "INFO-001", "inputTool": "KEYPAD" }
PUT    /api/admin/treasure-hunt/checkpoints/{checkpointId}       { "code": "do ré mi", "inputTool": "PIANO" }
DELETE /api/admin/treasure-hunt/checkpoints/{checkpointId}
```

La création place l'étape en dernière position. La suppression est autorisée pour toute étape et renumérote les suivantes. Les réponses de liste, création, modification et réordonnancement utilisent le DTO suivant :

```json
{
  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "pathId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "stepNumber": 1,
  "code": "INFO-001",
  "inputTool": "KEYPAD"
}
```

Le champ `code` est réservé aux routes d'administration. Il est enregistré avec les espaces extérieurs retirés ; son hash normalisé est conservé séparément pour la validation joueur. Les anciennes étapes, créées avant cette évolution, peuvent retourner `"code": null` : leur code d'origine ne peut pas être reconstitué depuis le hash et doit être enregistré à nouveau par un administrateur.

`inputTool` désigne l'outil par lequel le secret de l'étape doit arriver, parmi `KEYPAD`, `PIANO`, `DIAL`, `RADIO`, `SAFE`, `TELEGRAPH` et `PADLOCK`. À la création, un champ absent vaut `KEYPAD` ; à la modification, un champ absent laisse l'outil inchangé, afin qu'un panneau resté en cache ne ramène pas une étape piano au clavier.

Le hash est calculé sur la forme **canonique** du secret, celle que la validation joueur reconstruira — pour `PIANO`, la suite de notes recollée par tirets (`do ré♯ mi` → `DO-RE#-MI`). Le champ `code` garde la forme lisible saisie par l'administrateur. Deux conséquences :

- l'unicité porte sur la mélodie et non sur son écriture : `DO RE MI` et `do ré mi` entrent en conflit (`409 Ce code est déjà utilisé.`) ;
- un secret que son outil ne sait pas lire est refusé à l'écriture par un `400` explicite (`DO H MI` n'est pas une mélodie), et une étape déjà en base dans ce cas empêche l'activation de la chasse. Aucune longueur n'est imposée : une mélodie d'une seule note est valide.

Pour modifier l'ordre, fournir tous les identifiants d'étape de la voie :

```http
PUT /api/admin/treasure-hunt/paths/{pathId}/checkpoints/order
Content-Type: application/json

{"ids":["checkpoint-3", "checkpoint-1", "checkpoint-2"]}
```

L'API joueur ne renvoie jamais le code de validation ni son hash.

### Configurer les contenus d'une étape

```text
GET    /api/admin/treasure-hunt/checkpoints/{checkpointId}/contents
POST   /api/admin/treasure-hunt/checkpoints/{checkpointId}/contents
POST   /api/admin/treasure-hunt/checkpoints/{checkpointId}/contents/media
PUT    /api/admin/treasure-hunt/checkpoints/{checkpointId}/contents/order
PUT    /api/admin/treasure-hunt/contents/{contentId}
DELETE /api/admin/treasure-hunt/contents/{contentId}
```

Exemples de création :

```json
{ "type": "RICH_TEXT", "text": "<p>Indice suivant...</p>" }
```

```json
{ "type": "ARTICLE", "articleId": "11111111-1111-1111-1111-111111111111" }
```

Pour un média, envoyer le fichier multipart dans le champ `file`. La route de réorganisation attend `{ "ids": ["..."] }` avec l'ensemble des contenus de l'étape.

### Configurer le coffre

Le coffre utilise exactement le même modèle de contenus :

```text
GET  /api/admin/treasure-hunt/vault/contents
POST /api/admin/treasure-hunt/vault/contents
POST /api/admin/treasure-hunt/vault/contents/media
PUT  /api/admin/treasure-hunt/vault/contents/order
```

Les routes `PUT` et `DELETE /contents/{contentId}` servent aussi à modifier ou retirer les contenus du coffre.

## Flow joueur

### Valider une étape

```http
POST /api/treasure-hunt/checkpoints/validate
Content-Type: application/json

{"tool":"PIANO","secret":"DO-RE#-MI"}
```

`tool` est une chaîne libre, normalisée en majuscules ; un nom que l'énumération ne connaît pas n'est pas une erreur, c'est un outil qu'aucune étape n'attend. `secret` est normalisé **selon l'outil** : pour `PIANO`, découpage sur tout séparateur puis repliage de chaque jeton sur l'une des douze notes (`RÉ` → `RE`, `♯` → `#`, `RÉb` → `DO#`) ; pour tout autre outil, `trim` et majuscules.

Aucun champ n'est obligatoire, volontairement : un `@NotBlank` ou un `400` de désérialisation désignerait l'outil attendu aussi sûrement qu'un message explicite. Deux champs restent acceptés pour les clients antérieurs — `code`, l'ancien nom de `secret`, lu seulement si `secret` est absent, et `pathId`, appliqué comme filtre de contrôle quand il est présent.

Secret inconnu, outil faux, `pathId` faux, étape pas encore atteinte : une seule et même réponse, `400` avec le message `Rien ne s'ouvre. Ce code est erroné — ou pas encore disponible.` Toute distinction trahirait l'outil attendu. Le `403 Merci de revenir plus tard` de l'étape prématurée a disparu de cet endpoint. La limite est de **30 essais par minute** : les leurres consomment des appels, et explorer l'établi ne doit pas épuiser le quota.

La réponse contient les contenus dans l'ordre configuré. Un média possède `mediaUrl` ; un article possède `articleId`.

```json
{
  "pathId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "stepNumber":1,
  "contents":[
    {"type":"RICH_TEXT", "text":"<p>Indice suivant...</p>", "mediaUrl":null, "articleId":null},
    {"type":"ARTICLE", "text":null, "mediaUrl":null, "articleId":"11111111-1111-1111-1111-111111111111"}
  ],
  "vaultCode":null
}
```

La validation répétée renvoie la même récompense logique. Lorsqu'une étape contient `FINAL_CODE`, le code personnel existant est renvoyé ou créé une seule fois. Ce code est tiré aléatoirement et contient exactement six caractères parmi `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` ; `I`, `O`, `0` et `1` sont exclus pour éviter les confusions visuelles. Deux joueurs peuvent recevoir le même code, mais les codes des voies d'un même joueur sont uniques.

### Codes et ouverture du coffre

```text
GET  /api/treasure-hunt/vault/codes
POST /api/treasure-hunt/vault/unlock   { "codes": ["...", "...", "...", "...", "...", "..."] }
```

Les six codes alphanumériques doivent être présents et dans l'ordre courant des voies. La réponse d'ouverture renvoie la liste ordonnée des contenus du coffre, avec les URLs pré-signées pour les médias.

## Erreurs principales

- `400 Bad Request` : saisie refusée (secret inconnu, mauvais outil, `pathId` faux ou étape précédente non validée — un seul message pour les quatre), mélodie illisible à l'écriture, ordre incomplet ou dupliqué, combinaison de coffre incorrecte, média absent lors d'une création `MEDIA`, ou `FINAL_CODE` hors dernière étape ;
- `403 Forbidden` : la chasse n'est pas active, ou elle est masquée ;
- `404 Not Found` : voie, étape, contenu, article ou utilisateur introuvable ;
- `409 Conflict` : secret déjà utilisé par une autre étape, mélodies comprises.

## Ordre de déploiement

**Le backend part avant les fronts.** Il est rétro-compatible avec le front en production — `pathId` reste accepté, `code` reste lu à la place de `secret`, une étape sans outil attend le clavier — alors que l'inverse est faux : l'ancien DTO de validation exige `pathId` et `code` sous `@Valid`, si bien qu'un nouveau front qui envoie `{tool, secret}` contre un ancien backend voit **toute** saisie refusée par un `400` de validation de corps.

Le backend se déploie par script manuel et les fronts par Cloudflare Pages : les deux ne partent jamais ensemble, et l'ordre est donc à tenir à la main. Les étapes piano créées avant le passage au hash canonique gardent un hash calculé sur la chaîne brute et restent injouables : il faut les enregistrer à nouveau depuis le panneau pour que leur hash soit recalculé.

### Exception : étendre la liste des outils jouables

Pour cette liste précise, l'ordre général ci-dessus s'inverse. Elle est déclarée trois fois :
`TOOLS` dans le dépôt insastral (`src/pages/chasse/tools/index.ts`) dessine réellement les
outils côté joueur et en est l'autorité de fait ; `TreasureHuntInputTool.IMPLEMENTED_BY_THE_PLAYER_FRONT`
dans ce backend et `playableInputTools` dans PanelAdmin
(`src/features/treasureHunt/treasureHuntTypes.ts`) n'en sont que des copies. Rien — ni type
ni test — ne les relie.

L'ordre à tenir : on étend d'abord `TOOLS`, on déploie le front joueur, et seulement après
on allonge la liste du backend (PanelAdmin peut suivre à tout moment, elle n'habille qu'un
sélecteur). Front d'abord, l'écart est sans danger : la garde d'activation reste trop
stricte et l'outil neuf ne sert pas encore. Backend d'abord, l'écart est celui que cette
garde existe pour empêcher : une chasse s'activerait avec une étape qu'aucun front ne sait
afficher.
