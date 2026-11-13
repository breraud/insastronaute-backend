# Admin Users Frontend Implementation Flow

This document describes how the frontend should implement admin player listing and player moderation detail.

## Scope

Build these admin views:

- player search table with filters and pagination;
- player detail overview with currencies, points, and counters;
- challenge submission history for the player as submitter or participant;
- challenges currently available to the selected player;
- manual approved challenge submission;
- friendships and pending friend requests;
- virtual and physical card collections;
- assigned physical card instances with links to card instance admin actions.

All endpoints are under `/api/admin/**`, so the logged-in user must have `ADMIN`.
All state-changing browser requests must include `X-XSRF-TOKEN` from `GET /api/csrf`.

## Recommended Navigation

Use this structure:

- `Players`
  - searchable player table
  - player detail page or drawer
- `Player Detail`
  - overview
  - submissions
  - friends
  - cards
  - admin actions

The detail view should be reachable from user ids returned by other admin screens, especially card instance search and submission moderation.

## Player Search

Use:

```http
GET /api/admin/users?query={query}&departmentId={departmentId}&gender={gender}&hiddenFromLeaderboards={flag}&page={page}&size={size}&sort={field},{direction}
```

All filters are optional.

Frontend search fields:

- free text: display name, email, or Keycloak subject;
- department;
- gender;
- leaderboard visibility;
- page size;
- sort.

Default table columns:

- display name;
- email;
- department;
- gender;
- tokens;
- virtual collection points;
- physical collection points;
- challenge points;
- hidden from leaderboards;
- created date;
- updated date;
- actions.

Primary row action:

- open `GET /api/admin/users/{userId}`.

Secondary row action:

- toggle leaderboard visibility with `PATCH /api/admin/users/{userId}/leaderboard-visibility`.

## Player Detail Load

Use:

```http
GET /api/admin/users/{userId}
```

This is the main frontend bootstrap endpoint for a player moderation page. It returns the player, stats, submissions, friendships, card collections, and assigned physical card instances.

Example response shape:

```json
{
  "user": {
    "id": "99999999-9999-9999-9999-999999999999",
    "keycloakSub": "keycloak-sub",
    "email": "alice@example.com",
    "displayName": "Alice",
    "gender": "FEMALE",
    "departmentId": "11111111-1111-1111-1111-111111111111",
    "department": "INFO",
    "tokens": 12,
    "virtualCollectionPoints": 3,
    "physicalCollectionPoints": 2,
    "challengePoints": 42,
    "hiddenFromLeaderboards": false,
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z"
  },
  "stats": {
    "submissionCount": 4,
    "pendingSubmissionCount": 1,
    "waitingConfirmationSubmissionCount": 0,
    "validatedSubmissionCount": 2,
    "rejectedSubmissionCount": 1,
    "friendshipCount": 3,
    "acceptedFriendshipCount": 2,
    "pendingFriendshipCount": 1,
    "virtualCardCount": 5,
    "physicalCardCount": 2,
    "physicalCardInstanceCount": 2
  },
  "submissions": [],
  "friendships": [],
  "cardCollections": [],
  "physicalCardInstances": []
}
```

Frontend behavior:

- show a loading skeleton for the whole detail page;
- render the overview even when lists are empty;
- keep the response in one page-level query cache entry keyed by `userId`;
- refresh the detail after any action that can change currencies, points, card ownership, or leaderboard visibility.

## Overview Panel

Show immutable identity fields separately from balances:

- identity: display name, email, Keycloak subject, department, gender, created date;
- currencies and points: tokens, challenge points, virtual collection points, physical collection points;
- status: hidden from leaderboards;
- counters from `stats`.

Actions:

- toggle leaderboard visibility;
- copy user id;
- copy Keycloak subject;
- jump to card instance search filtered by `ownerUserId`;
- jump to submission tab.

## Submission Tab

Use the `submissions` array from the detail response.

Recommended columns:

- submitted date;
- status;
- challenge title;
- category;
- validation mode;
- submitter;
- participant-only marker;
- proof type;
- base points;
- bonus points;
- validated date;
- messages enabled;
- action.

Submission rows contain:

- `submissionId`;
- `status`;
- `participantOnly`;
- proof metadata;
- challenge summary;
- participants.

For a detail action:

- if the admin needs moderation controls, open `GET /api/moderation/submissions/{submissionId}`;
- if the submission is pending and needs proof access or decision, follow the review-lock flow from `submission-moderation.md`;
- if only historical inspection is needed, the data in `GET /api/admin/users/{userId}` is enough for a compact read-only row expansion.

Important frontend rule:

- proof metadata never contains storage keys or public URLs.
- for proof media, use `GET /api/moderation/submissions/{submissionId}/proofs/{index}/content` after taking the review lock; the API streams private bytes and never returns an S3 URL.
- for text proofs, render `proofData` directly.

### Manual Challenge Submission

Load the player's eligible challenges before opening the creation form:

```http
GET /api/admin/users/{userId}/available-challenges
```

The response uses the player challenge DTO and the same eligibility rules as the player-facing challenge list. Show only entries where `canStillComplete` is `true`; do not duplicate prerequisite, department, or activity checks in the frontend.

Create the correction with:

```http
POST /api/admin/users/{userId}/challenge-submissions
Content-Type: application/json
X-XSRF-TOKEN: <token>

{
  "challengeId": "22222222-2222-2222-2222-222222222222",
  "comment": "Validation réalisée au stand."
}
```

The submission is immediately `VALIDATED`, has no uploaded proof, and recalculates the player's challenge points. Reload both the player detail and available-challenge list after success. Surface `400` or `409` errors when eligibility changed between the list read and creation request.

## Friends Tab

Use the `friendships` array from the detail response.

Recommended columns:

- other user display name;
- other user email;
- status;
- direction;
- created date;
- responded date;
- action.

`direction` is from the selected player's perspective:

- `OUTGOING`: selected player requested the relation;
- `INCOMING`: selected player received the relation.

Actions:

- open the other player's admin detail with `GET /api/admin/users/{friendUserId}`;
- filter the table by `ACCEPTED` or `PENDING`.

The current backend exposes this tab as read-only. Friend moderation actions should not be invented in the frontend until dedicated admin endpoints exist.

## Cards Tab

Use two sections.

### Card Collections

Use `cardCollections`.

Recommended columns:

- collection type: `VIRTUAL` or `PHYSICAL`;
- card name;
- type/category;
- number/display order;
- quantity;
- first discovered date;
- updated date.

Virtual cards are quantity-based.
Physical collection counts are derived from assigned physical instances and should match `physicalCardInstances` totals for normal data.

### Physical Card Instances

Use `physicalCardInstances`.

Recommended columns:

- public code;
- card name;
- type;
- number;
- assigned date;
- print batch id;
- front page id;
- back page id;
- actions.

Actions:

- open `/api/admin/cards/instances/{instanceId}` detail;
- reassign/release/delete through the admin card instance flow;
- open print batch or printed page lookup when `printRunId`, `frontPrintPageId`, or `backPrintPageId` is present.

Do not grant physical ownership through the virtual grant endpoint. Physical cards must be managed as unique card instances.

### Player-Scoped Card Corrections

For a virtual card, add quantity from the player detail page with:

```http
POST /api/admin/users/{userId}/cards
Content-Type: application/json
X-XSRF-TOKEN: <token>

{
  "cardId": "22222222-2222-2222-2222-222222222222",
  "collectionType": "VIRTUAL",
  "quantity": 1
}
```

Remove virtual quantity with `DELETE /api/admin/users/{userId}/cards` and the same payload. The backend rejects a removal greater than the player's held quantity.

For physical cards, first select existing unassigned instances from the card instance search, then assign them from the player page:

```http
POST /api/admin/users/{userId}/cards
Content-Type: application/json
X-XSRF-TOKEN: <token>

{
  "collectionType": "PHYSICAL",
  "cardInstanceIds": ["33333333-3333-3333-3333-333333333333"]
}
```

Use `DELETE /api/admin/users/{userId}/cards` with the same physical payload to release instances selected from this player's list. This releases them to inventory; it does not delete the card definition or printed instance.

## Mutation And Refresh Rules

After these actions, invalidate and reload `GET /api/admin/users/{userId}`:

- `PATCH /api/admin/users/{userId}/leaderboard-visibility`;
- `POST /api/admin/users/{userId}/challenge-submissions`;
- `POST /api/admin/users/{userId}/cards`;
- `DELETE /api/admin/users/{userId}/cards`;
- challenge moderation decision for a submission involving this user;
- card instance assign, reassign, release, update, or delete involving this user;
- virtual card grant involving this user;
- deck pull or any future admin currency/points adjustment involving this user.

For a detail page with nested tabs, prefer optimistic UI only for small status toggles. For point totals, card counts, and submission status, reload from the backend.

## Error States

Handle these cases:

- `401 Unauthorized`: session expired; send the admin through login/session recovery.
- `403 Forbidden`: authenticated user is not an admin.
- `404 Not Found`: user no longer exists locally or has not synchronized from Keycloak yet.
- `409 Conflict`: action-level conflict from linked moderation/card flows.

Empty states should be explicit:

- no submissions;
- no friends or requests;
- no virtual cards;
- no assigned physical cards.

## Related Flows

- `submission-moderation.md` for review locks, proof URLs, and decisions.
- `submission-discussion.md` for moderation/player discussion threads.
- `card-admin-frontend.md` for physical card instance actions and virtual grants.
- `friends.md` for player-side friendship lifecycle.
