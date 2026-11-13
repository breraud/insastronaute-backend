# Questionnaires d'événement — flow

Certains événements ont besoin de poser des questions à l'inscription : à pied ou en courant pour le pique-nique, végétarien ou non pour l'AlphaNight.

## Deux types de questions

| Type | Usage | Restitution |
|---|---|---|
| `SINGLE_CHOICE` | « À pied ou en courant ? » | comptée, alimente le camembert |
| `FREE_TEXT` | « Allergies ? » | liste de réponses, jamais graphée |

Une question à choix exige **au moins deux options**. Une question à texte libre n'en prend aucune.

## Côté admin

### Lire et remplacer le questionnaire

```http
GET /api/admin/events/{eventId}/questions
PUT /api/admin/events/{eventId}/questions
```

```json
{
  "questions": [
    {
      "id": "…",
      "label": "À pied ou en courant ?",
      "type": "SINGLE_CHOICE",
      "required": true,
      "options": [
        { "id": "…", "label": "À pied" },
        { "label": "En courant" }
      ]
    },
    { "label": "Allergies ?", "type": "FREE_TEXT", "required": false }
  ]
}
```

Le `PUT` remplace tout le questionnaire en un appel, mais **il n'est pas destructeur par défaut** : une question ou une option envoyée **avec son `id`** est mise à jour sur place. Renommer « Végétarien » ne jette donc pas les réponses qui pointent dessus. Une option sans `id` est créée. Ce qui n'est pas renvoyé est supprimé, avec ses réponses — c'est la seule lecture honnête de « l'admin l'a retirée ».

### Lire les résultats

```http
GET /api/admin/events/{eventId}/questionnaire
```

```json
{
  "eventId": "…",
  "title": "AlphaNight",
  "registrationCount": 51,
  "questions": [
    {
      "questionId": "…",
      "label": "Régime ?",
      "type": "SINGLE_CHOICE",
      "required": true,
      "answeredCount": 42,
      "unansweredCount": 9,
      "options": [
        { "optionId": "…", "label": "Classique",  "count": 28, "percentage": 67 },
        { "optionId": "…", "label": "Végétarien", "count": 14, "percentage": 33 }
      ],
      "textAnswers": []
    },
    {
      "questionId": "…",
      "label": "Allergies ?",
      "type": "FREE_TEXT",
      "answeredCount": 6,
      "unansweredCount": 45,
      "options": [],
      "textAnswers": [
        { "displayName": "Marie Dupont", "value": "arachides" }
      ]
    }
  ]
}
```

**Les pourcentages portent sur ceux qui ont répondu**, pas sur les inscrits. Une question ajoutée tard n'a donc pas toutes ses parts écrasées par le silence ; les non-répondants sont comptés à part dans `unansweredCount`, ce qui dit exactement qui relancer.

## Côté joueur

### Récupérer le formulaire

```http
GET /api/events/{eventId}/questions
```

Renvoie une liste vide quand l'événement ne demande rien — le client peut donc inscrire directement sans étape supplémentaire.

### S'inscrire avec ses réponses

```http
POST /api/events/{eventId}/registrations
```

```json
{
  "answers": [
    { "questionId": "…", "optionId": "…" },
    { "questionId": "…", "textValue": "arachides" }
  ]
}
```

Le corps est **optionnel** : un événement sans question s'inscrit comme avant. La validation se fait dans la même transaction que la prise de place, donc une réponse refusée annule aussi l'inscription — on ne se retrouve jamais inscrit avec un questionnaire à moitié rempli.

Refusé en `400` : une question obligatoire sans réponse, une option qui n'appartient pas à sa question, un texte envoyé à une question à choix, une réponse visant une question d'un autre événement.

### Modifier ses réponses

```http
GET /api/events/{eventId}/registrations/answers
PUT /api/events/{eventId}/registrations/answers
```

Tant que les inscriptions sont ouvertes. Sans ça, un joueur devenu végétarien devrait se désinscrire et se réinscrire — et perdrait sa place si le quota s'est rempli entre-temps.

Vider une réponse facultative la **supprime** au lieu de laisser traîner une ancienne valeur que personne n'a voulu garder.

## Contraintes de données

Une réponse porte exactement une forme — une option **ou** un texte, jamais les deux, jamais aucune. C'est une contrainte `CHECK` en base, pas une convention applicative. Une réponse est unique par couple (inscription, question) : répondre à nouveau est une mise à jour, pas une seconde ligne. Supprimer une inscription emporte ses réponses.
