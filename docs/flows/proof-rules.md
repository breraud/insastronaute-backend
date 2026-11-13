# Proof Rules

## Règles à respecter

Types de preuve :

- `MEDIA` : un ou plusieurs fichiers ;
- `TEXT` : texte libre, sans fichier, porté par `comment`.

Types de fichiers acceptés pour `MEDIA` :

- images : JPEG, PNG, WebP ;
- vidéos : MP4, WebM, QuickTime.

Limites :

- taille totale maximale d'une preuve : 15 Mo ;
- pas de limite métier spécifique sur le nombre de vidéos ;
- pas d'interdiction de mélanger images et vidéos ;
- les fichiers doivent être confirmés avec `/complete` avant la soumission ;
- les uploads non terminés expirent par défaut après 15 minutes ;
- les brouillons expirent par défaut après 24 heures.

## Résumé du flow fichier

```text
Client
  -> POST /api/uploads/proofs/direct ou /direct/batch
Backend
  -> crée un uploadId, impose une clé S3 unique, renvoie uploadUrl + fields
Client
  -> POST multipart/form-data vers uploadUrl S3
Client
  -> POST /api/uploads/proofs/{uploadId}/complete avec body vide
Backend
  -> vérifie l'objet S3, confirme le brouillon, renvoie uploadId + remainingSizeBytes
Client
  -> POST /api/challenges/{challengeId}/submit avec proofType MEDIA + proofUploadIds
Backend
  -> attache les uploads confirmés à la soumission
  -> ajoute le comment MEDIA comme message joueur initial si présent
  -> garde le fil fermé aux joueurs jusqu'à un message modération ou une activation manuelle
```
