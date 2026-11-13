# Défis automatiques

Les défis dont `validationMode` vaut `AUTO` sont validés exclusivement par le moteur automatique. La route joueur `POST /api/challenges/{challengeId}/submit` les refuse avec un statut HTTP `400`, le champ d'erreur `validationMode` et le message `Les défis automatiques ne peuvent pas être soumis par un joueur.` Le rejet intervient après le chargement du joueur et du défi, mais avant la résolution des participants, les contrôles d'éligibilité, le rattachement d'une preuve ou la création d'une soumission.

## Configuration admin et métadonnées

Une création ou mise à jour AUTO doit fournir :

```json
{
  "validationMode": "AUTO",
  "autoChallengeType": "PERMANENT_QR_SCANNED",
  "autoChallengeParameters": {}
}
```

Pour un défi `ADMIN`, le service efface `autoChallengeType` et `autoChallengeParameters`. Pour un défi `AUTO`, le registre exige un type connu, convertit le JSON vers le type de paramètres déclaré, puis exécute sa validation. Les erreurs ciblent `autoChallengeType` ou `autoChallengeParameters`.

L'endpoint admin authentifié `GET /api/admin/challenges/auto-types` décrit les types disponibles :

```json
[
  {
    "key": "PERMANENT_QR_SCANNED",
    "publicName": "QR permanent scanne",
    "description": "Valide le defi lorsque le QR permanent est scanne.",
    "parameters": []
  }
]
```

Chaque entrée de `parameters` expose `name`, `label`, `type`, `required`, `defaultValue`, `minimum`, `maximum` et `options`. L'interface admin peut donc construire un formulaire sans connaître les implémentations Java.

## Cycle d'évaluation

À chaque déclenchement, le scheduler :

1. empêche deux exécutions simultanées dans la même JVM ;
2. acquiert dans Redis le verrou `scheduler:auto-challenges` avec un propriétaire unique et une durée limitée ;
3. charge les défis AUTO actifs à l'instant de début du scan ;
4. valide à nouveau leur type et leurs paramètres ; une configuration historique invalide est journalisée et ignorée ;
5. demande au type des pages de candidats, avec un curseur UUID et la taille de lot configurée ;
6. attribue chaque page dans une transaction indépendante ;
7. libère le verrou Redis seulement si l'exécution en est toujours propriétaire.

Une erreur sur un défi est journalisée et n'empêche pas l'évaluation des défis suivants. Un type qui renvoie un curseur identique au précédent est arrêté comme défectueux.

Propriétés disponibles :

| Propriété Spring | Variable d'environnement | Valeur par défaut |
| --- | --- | --- |
| `app.auto-challenges.scheduler.enabled` | `APP_AUTO_CHALLENGES_SCHEDULER_ENABLED` | `true` |
| `app.auto-challenges.scheduler.cron` | `APP_AUTO_CHALLENGES_SCHEDULER_CRON` | `0 0 * * * *` (toutes les heures) |
| `app.auto-challenges.scheduler.batch-size` | `APP_AUTO_CHALLENGES_SCHEDULER_BATCH_SIZE` | `250` |
| `app.auto-challenges.scheduler.lock-duration` | `APP_AUTO_CHALLENGES_SCHEDULER_LOCK_DURATION` | `PT55M` |

La désactivation empêche la création du composant scheduler. Le cron est interprété par Spring. La durée suit le format `Duration` de Spring/ISO-8601.

## Éligibilité, rétroactivité et idempotence

La détection d'un type retourne des candidats, pas des gagnants définitifs. Juste avant l'insertion, le service recharge le défi et les joueurs puis applique les règles communes : défi actif et non supprimé, ciblage département, prérequis et absence de soumission bloquante (`WAITING_CONFIRMATION`, `PENDING` ou `VALIDATED`) pour le défi. Ce filtrage s'exécute dans une transaction de lecture bornée au lot afin que les associations LAZY de ciblage et de prérequis restent accessibles. Chaque tentative d'écriture utilise ensuite une transaction `REQUIRES_NEW`, qui suspend cette lecture le temps de l'insertion et dont un rollback n'annule pas les succès précédents du lot.

Le moteur est rétroactif : à chaque scan, un type peut retourner tout joueur dont l'état courant satisfait sa condition, même si cet état précède la création du défi. Les dates d'activité, le ciblage et les prérequis sont néanmoins vérifiés au moment de l'attribution.

Une récompense crée directement une soumission `VALIDATED` avec `proofType = "AUTO"`, `proofData = ""`, `messagesEnabled = false` et les mêmes valeurs pour `createdAt` et `validatedAt`. PostgreSQL maintient par triggers une réservation active `(challenge_id, user_id)` pour le propriétaire et chaque participant de toute soumission bloquante. Une insertion, une transition vers un statut bloquant ou l'ajout d'un participant réserve atomiquement tous les membres ; une transition vers un autre statut ou une suppression les libère. Deux opérations concurrentes qui impliquent le même membre entrent donc en collision sur `challenge_active_memberships_pkey` et l'opération perdante est entièrement rollbackée. Le moteur AUTO classe cette collision comme attendue et poursuit les candidats suivants. La table historique `challenge_completion_claims` reste séparée et continue de protéger les complétions `VALIDATED`.

## Type `PERMANENT_QR_SCANNED`

Ce type n'accepte aucun paramètre : `autoChallengeParameters` doit être exactement un objet JSON vide `{}`. Il parcourt, par UUID croissant et par pages, les joueurs dont l'indicateur permanent de scan QR est déjà enregistré. Cette condition permanente explique sa rétroactivité : un joueur ayant scanné avant la création du défi devient candidat lors du prochain scan.

## Ajouter un type

Les contrats du moteur sont dans `challenge/auto/api`, puis le registre,
l'évaluation, l'attribution et la planification sont isolés dans leurs packages
respectifs. Chaque implémentation concrète vit dans un package autonome sous
`challenge/auto/types/<nom-du-type>`. Le moteur central ne dépend jamais d'une
implémentation concrète.

1. Créer un composant qui implémente `AutoChallengeType<P>` avec une clé stable et unique.
2. Définir le record ou la classe `P`, puis retourner sa classe dans `parametersType()`.
3. Fournir le nom public, la description et les métadonnées de formulaire dans `parameters()`.
4. Redéfinir `validate(P)` uniquement lorsque le type possède des invariants
   métier supplémentaires ; la méthode par défaut convient aux types sans règle
   additionnelle.
5. Implémenter `findCandidates(P, UUID afterUserId, int limit)` avec des pages bornées et un curseur strictement progressif ; retourner `nextCursor = null` sur la dernière page.
6. Ajouter des tests du parsing, de la validation, de la pagination et de la condition métier. Le composant Spring est découvert automatiquement par le registre.

Le type ne doit pas créer de soumission ni attribuer de points lui-même. Ces opérations restent centralisées dans le moteur pour conserver l'éligibilité commune, les transactions bornées et l'idempotence.

La checklist complète, le squelette d'implémentation et les règles de dépendance
sont documentés dans `challenge/auto/types/README.md`. Chaque package de type
possède également son propre `README.md` avec sa configuration, sa requête de
candidats, ses index et ses hypothèses métier.
