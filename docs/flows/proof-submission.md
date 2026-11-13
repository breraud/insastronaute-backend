# Flow client d'envoi de preuve

Ce document décrit le flow cible côté client pour envoyer une preuve.

Objectif du contrat :

- le client manipule principalement des `uploadId` ;
- le client n'envoie pas les IDs des participants dans le flow de preuve ;
- le client ne choisit pas le nom final du fichier sur S3 ;
- le backend renvoie uniquement les champs utiles au client ;
- la confirmation d'upload ne répète pas les métadonnées déjà envoyées.

> Note backend actuelle : le code existant génère déjà une clé S3 avec un UUID, donc deux fichiers avec le même nom utilisateur ne devraient pas se bloquer côté S3. Le flow cible ci-dessous formalise mieux cette idée : le nom S3 est imposé par le backend et peut être indexé (`001.jpg`, `002.jpg`, etc.) ou généré de manière unique.

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

## Ce que le client doit retenir

Pour une preuve fichier, le client doit retenir :

- `uploadId` : identifiant métier de l'upload, utilisé pour confirmer et soumettre ;
- `uploadUrl` : URL S3 à appeler directement ;
- `fields` : champs du formulaire S3 à renvoyer tels quels.

Le client ne doit pas avoir besoin de retenir :

- `participantUserIds` ;
- des headers S3 vides ;
- le `contentType` ou la taille après la préparation ;
- le nom final S3 ;
- les clés S3 pour soumettre le défi.

La clé S3 existe toujours dans les `fields` du formulaire signé, car S3 en a besoin pour savoir où ranger le fichier. En revanche, elle ne doit pas être exposée comme champ métier séparé dans les réponses `/direct` ou `/complete`.

## Nom des fichiers sur S3

Le client peut envoyer le nom original du fichier pour affichage :

```json
{
  "originalFileName": "preuve.jpg"
}
```

Mais le backend impose le nom final sur S3.

Exemples acceptables :

```text
proofs/{userId}/{challengeId}/{draftId}/001.jpg
proofs/{userId}/{challengeId}/{draftId}/002.jpg
proofs/{userId}/{challengeId}/{draftId}/003.png
```

ou :

```text
proofs/{userId}/{challengeId}/{draftId}/{uploadId}.jpg
```

Conséquence : si l'utilisateur upload deux fichiers qui s'appellent tous les deux `preuve.jpg`, ça ne bloque pas. Le backend garde éventuellement `originalFileName` pour l'affichage, mais la clé S3 reste unique.

## Cas simple : envoyer un media

### 1. Préparer l'upload

```http
POST /api/uploads/proofs/direct
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "challengeId": "11111111-1111-1111-1111-111111111111",
  "originalFileName": "preuve.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 245000
}
```

Réponse :

```json
{
  "uploadId": "22222222-2222-2222-2222-222222222222",
  "uploadUrl": "https://s3.example.com/insastronaute-proofs",
  "fields": {
    "key": "proofs/99999999-9999-9999-9999-999999999999/11111111-1111-1111-1111-111111111111/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/001.jpg",
    "Content-Type": "image/jpeg",
    "policy": "base64-policy",
    "x-amz-algorithm": "AWS4-HMAC-SHA256",
    "x-amz-credential": "access-key/20260618/eu-fr/s3/aws4_request",
    "x-amz-date": "20260618T120000Z",
    "x-amz-signature": "signature-exemple"
  },
  "expiresInSeconds": 900
}
```

Pourquoi pas de `headers` ?

Parce que le flow utilise un presigned POST S3. Les informations de signature sont dans `fields`, pas dans des headers HTTP à ajouter manuellement. Le navigateur crée lui-même le header `Content-Type: multipart/form-data; boundary=...` au moment du `fetch`.

### 2. Envoyer le fichier à S3

Le client envoie le fichier directement au stockage, pas au backend.

```js
async function uploadProofFile(file, preparedUpload) {
  const formData = new FormData();

  for (const [key, value] of Object.entries(preparedUpload.fields)) {
    formData.append(key, value);
  }

  formData.append("file", file);

  const response = await fetch(preparedUpload.uploadUrl, {
    method: "POST",
    body: formData
  });

  if (!response.ok) {
    throw new Error("Upload S3 échoué");
  }
}
```

Le fichier doit être ajouté en dernier dans le `FormData`.

Réponse S3 attendue :

```http
204 No Content
```

ou :

```http
201 Created
```

Le client n'a pas besoin d'exploiter un body S3.

### 3. Confirmer l'upload

La confirmation ne répète pas `contentType` ni `sizeBytes`, car le backend les connaît déjà depuis la préparation.

```http
POST /api/uploads/proofs/22222222-2222-2222-2222-222222222222/complete
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{}
```

Réponse :

```json
{
  "uploadId": "22222222-2222-2222-2222-222222222222",
  "remainingSizeBytes": 15483640
}
```

À cette étape, le backend vérifie réellement le fichier dans S3 :

- l'objet existe ;
- la taille réelle correspond à la taille déclarée à la préparation ;
- le type réel du fichier est autorisé.

`remainingSizeBytes` est utile ici, car c'est après validation réelle du fichier que le client peut savoir combien il reste dans le brouillon.

### 4. Soumettre le défi

La soumission finale utilise les `uploadId`, pas les clés S3.

```http
POST /api/challenges/11111111-1111-1111-1111-111111111111/submit
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "proofType": "MEDIA",
  "proofUploadIds": [
    "22222222-2222-2222-2222-222222222222"
  ],
  "comment": "Voici ma preuve"
}
```

Réponse :

```http
200 OK
```

Le backend traite la soumission comme une preuve `MEDIA`.
Si `comment` est fourni, il est aussi ajouté au fil de discussion de la soumission comme premier message joueur.

## Cas batch : plusieurs medias

Pour plusieurs fichiers, le client demande plusieurs uploads d'un coup.

```http
POST /api/uploads/proofs/direct/batch
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "challengeId": "11111111-1111-1111-1111-111111111111",
  "files": [
    {
      "originalFileName": "preuve.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 245000
    },
    {
      "originalFileName": "preuve-video.mp4",
      "contentType": "video/mp4",
      "sizeBytes": 12000000
    }
  ]
}
```

Réponse :

```json
{
  "files": [
    {
      "uploadId": "22222222-2222-2222-2222-222222222222",
      "uploadUrl": "https://s3.example.com/insastronaute-proofs",
      "fields": {
        "key": "proofs/99999999-9999-9999-9999-999999999999/11111111-1111-1111-1111-111111111111/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/001.jpg",
        "Content-Type": "image/jpeg",
        "policy": "base64-policy-1",
        "x-amz-signature": "signature-1"
      },
      "expiresInSeconds": 900
    },
    {
      "uploadId": "33333333-3333-3333-3333-333333333333",
      "uploadUrl": "https://s3.example.com/insastronaute-proofs",
      "fields": {
        "key": "proofs/99999999-9999-9999-9999-999999999999/11111111-1111-1111-1111-111111111111/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/002.mp4",
        "Content-Type": "video/mp4",
        "policy": "base64-policy-2",
        "x-amz-signature": "signature-2"
      },
      "expiresInSeconds": 900
    }
  ]
}
```

Le batch peut contenir plusieurs types de medias. Le backend vérifie seulement que chaque fichier est autorisé et que le total du brouillon ne dépasse pas 15 Mo.

Ensuite, pour chaque fichier :

1. envoyer le fichier à S3 avec son `uploadUrl` et ses `fields` ;
2. appeler `/complete` avec son `uploadId`.

Soumission finale :

```json
{
  "proofType": "MEDIA",
  "proofUploadIds": [
    "22222222-2222-2222-2222-222222222222",
    "33333333-3333-3333-3333-333333333333"
  ],
  "comment": "Photo et vidéo pour la preuve"
}
```

## Cas preuve texte

Une preuve texte ne passe pas par le flow d'upload.

```http
POST /api/challenges/11111111-1111-1111-1111-111111111111/submit
Content-Type: application/json
X-XSRF-TOKEN: csrf-token-exemple
```

Body :

```json
{
  "proofType": "TEXT",
  "comment": "J'ai réalisé le défi."
}
```

Réponse :

```http
200 OK
```

Le backend traite la soumission comme une preuve `TEXT`.

Pour `TEXT`, `comment` est le contenu de la preuve et il est obligatoire. Pour `MEDIA`, `comment` reste un texte facultatif d'accompagnement.

