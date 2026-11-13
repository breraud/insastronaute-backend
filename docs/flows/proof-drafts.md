# Proof Draft Flow

## Récupérer le brouillon

Cette route sert à reconstruire l'état courant du brouillon côté client.

Elle est utile quand :

- l'utilisateur revient sur la page après avoir déjà uploadé des fichiers ;
- le front veut afficher la liste des preuves déjà prêtes ;
- le front veut afficher le nom original des fichiers ;
- le front veut afficher un aperçu media ;
- le front veut connaître la taille restante avant d'autoriser un nouvel upload ;
- le front veut savoir quels `uploadId` envoyer dans la soumission finale.

La route ne prépare pas un nouvel upload et ne confirme rien. Elle ne fait que lire le brouillon existant du user authentifié pour un défi donné.

```http
GET /api/uploads/proofs?challengeId=11111111-1111-1111-1111-111111111111
```

Réponse :

```json
{
  "remainingSizeBytes": 15483640,
  "files": [
    {
      "uploadId": "22222222-2222-2222-2222-222222222222",
      "originalFileName": "preuve.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 245000,
      "status": "READY",
      "previewUrl": "https://s3.example.com/insastronaute-proofs/proofs/..."
    }
  ]
}
```

Le client utilise :

- `previewUrl` pour afficher un aperçu ;
- `uploadId` pour supprimer ou soumettre ;
- `originalFileName` pour afficher le nom choisi par l'utilisateur ;
- `contentType` et `sizeBytes` pour afficher les infos fichier ;
- `remainingSizeBytes` pour afficher la limite restante.

Il ne doit pas utiliser de clé S3 pour soumettre.

Exemple d'usage front :

```js
const draft = await fetch(`/api/uploads/proofs?challengeId=${challengeId}`)
  .then((response) => response.json());

setProofFiles(draft.files);
setRemainingSizeBytes(draft.remainingSizeBytes);
```

## Supprimer un fichier du brouillon

Suppression par `uploadId` :

```http
DELETE /api/uploads/proofs/22222222-2222-2222-2222-222222222222
X-XSRF-TOKEN: csrf-token-exemple
```

Réponse :

```http
200 OK
```

On ne prévoit pas de suppression par clé S3 côté client. La suppression se fait uniquement avec `uploadId`.
