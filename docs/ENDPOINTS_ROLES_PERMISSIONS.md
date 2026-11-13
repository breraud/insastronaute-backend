# Cartographie des Endpoints Backend & Matrice des Permissions / Rôles

Ce document recense les **246 endpoints** de l'API backend Spring Boot d'INSAstronaute, regroupés en **18 modules fonctionnels**.

L'objectif de cette cartographie est d'établir avec vous la matrice des droits et rôles par endpoint.

---

## Légende des Rôles Potentiels (Exemples pour la discussion)
- **`PUBLIC`** : Accessible sans authentification
- **`USER`** / **`STUDENT`** : Utilisateur / Étudiant authentifié
- **`ADMIN`** : Administrateur système global
- **`ACCES_SITE_ADMIN`** : Accès de base au panel admin
- **`RESPOS_DEFIS`** / **`SOUS_RESPO_DEFIS`** : Gestion des défis et modération
- **`RESPOS_EVENT`** / **`SOUS_RESPO_EVENT`** : Gestion des événements et chasses au trésor
- **`RESPO_CARTES`** / **`SOUS_RESPO_CARTES`** : Gestion des cartes et decks
- **`RESPO_GOODIES`** / **`SOUS_RESPO_GOODIES`** : Gestion de la boutique et précommandes
- **`RESPO_DEPART`** / **`SOUS_RESPO_DEPART`** : Gestion des départements et liens
- **`RESPO_TOURNOIS`** / **`SOUS_RESPO_TOURNOIS`** : Gestion des tournois

---

NOTE : TLM = Tout le Monde
J = Joueurs (i.e rien d'autre que student)
 STAFF = Tout ce qui est autre que student (un des respos ou admin quoi)

## 1. Authentification, Session & Système (4 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/csrf` | `CsrfController` | _PermitAll (Public)_ | `[TLM (Public)]` |
| `POST` | `/api/scan` | `QrLinkController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/session/` | `SessionController` | _Authentifié (`/api/**`)_ | `[TLM (Auth)]` |
| `GET` | `/q` | `QrLinkController` | _PermitAll (Public)_ | `[J]` |

## 10. Administration - Chasse au Trésor (22 endpoints) [ADMIN pour tlm]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `POST` | `/api/admin/treasure-hunt/activate` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `DELETE` | `/api/admin/treasure-hunt/checkpoints/{checkpointId}` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/checkpoints/{checkpointId}` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/treasure-hunt/checkpoints/{checkpointId}/contents` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/checkpoints/{checkpointId}/contents` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/checkpoints/{checkpointId}/contents/media` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/checkpoints/{checkpointId}/contents/order` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `DELETE` | `/api/admin/treasure-hunt/contents/{contentId}` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/contents/{contentId}` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/deactivate` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/treasure-hunt/paths` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/paths/initialize` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/paths/order` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/treasure-hunt/paths/{pathId}/checkpoints` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/paths/{pathId}/checkpoints` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/paths/{pathId}/checkpoints/order` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/paths/{position}` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/treasure-hunt/status` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/treasure-hunt/vault/contents` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/vault/contents` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/treasure-hunt/vault/contents/media` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/treasure-hunt/vault/contents/order` | `AdminTreasureHuntController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |

## 10. Chasse au Trésor (Joueur) (4 endpoints) 

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `POST` | `/api/treasure-hunt/checkpoints/validate` | `TreasureHuntController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/treasure-hunt/progress` | `TreasureHuntController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/treasure-hunt/vault/codes` | `TreasureHuntController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/treasure-hunt/vault/unlock` | `TreasureHuntController` | _Authentifié (`/api/**`)_ | `[J]` |

## 10bis. Administration - Tournois (6 endpoints) [EVENT_ROLES]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/tournaments` | `AdminTournamentController` | `EVENT_ADMIN_ROLES` (SecurityConfig) | `[ADMIN, RESPOS_EVENT, SOUS_RESPO_EVENT, RESPO_DEPART, SOUS_RESPO_DEPART, RESPO_TOURNOIS, SOUS_RESPO_TOURNOIS]` |
| `GET` | `/api/admin/tournaments/{eventId}` | `AdminTournamentController` | `EVENT_ADMIN_ROLES` (SecurityConfig) | idem |
| `PUT` | `/api/admin/tournaments/{eventId}/attendance` | `AdminTournamentController` | `EVENT_ADMIN_ROLES` (SecurityConfig) | idem |
| `POST` | `/api/admin/tournaments/{eventId}/registrations` | `AdminTournamentController` | `@PreAuthorize("hasAnyRole('ADMIN', 'RESPO_TOURNOIS', 'SOUS_RESPO_TOURNOIS')")` | `[ADMIN, RESPO_TOURNOIS, SOUS_RESPO_TOURNOIS]` |
| `POST` | `/api/admin/tournaments/{eventId}/settle` | `AdminTournamentController` | `EVENT_ADMIN_ROLES` (SecurityConfig) | idem |
| `DELETE` | `/api/admin/tournaments/{eventId}/settlement` | `AdminTournamentController` | `EVENT_ADMIN_ROLES` (SecurityConfig) | idem |

## 10ter. Succès (4 endpoints joueur + 5 admin)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/achievements` | `AchievementController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/achievements/unseen` | `AchievementController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/achievements/unseen/acknowledge` | `AchievementController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/achievements/players/{userId}/completion` | `AchievementController` | _Authentifié + amitié acceptée (service)_ | `[J, amis uniquement]` |
| `GET` | `/api/admin/achievements/types` | `AdminAchievementController` | `ADMIN` (règle `/api/admin/**`) | `[ADMIN]` |
| `GET` | `/api/admin/achievements` | `AdminAchievementController` | `ADMIN` (règle `/api/admin/**`) | `[ADMIN]` |
| `POST` | `/api/admin/achievements` | `AdminAchievementController` | `ADMIN` (règle `/api/admin/**`) | `[ADMIN]` |
| `PUT` | `/api/admin/achievements/{achievementId}` | `AdminAchievementController` | `ADMIN` (règle `/api/admin/**`) | `[ADMIN]` |
| `DELETE` | `/api/admin/achievements/{achievementId}` | `AdminAchievementController` | `ADMIN` (règle `/api/admin/**`) | `[ADMIN]` |

Note : l'administration des succès n'a volontairement pas de règle dédiée dans `SecurityConfig` — elle retombe sur `ADMIN` parce qu'un succès crédite des tokens. À élargir explicitement si un rôle respo doit pouvoir en créer.

## 11. Administration - Articles & Journal (12 endpoints) [STAFF]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/articles/` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `POST` | `/api/admin/articles/` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `DELETE` | `/api/admin/articles/{articleId}` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `GET` | `/api/admin/articles/{articleId}` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `PUT` | `/api/admin/articles/{articleId}` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `POST` | `/api/admin/articles/{articleId}/cover` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `GET` | `/api/admin/articles/{articleId}/media` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `POST` | `/api/admin/articles/{articleId}/media` | `AdminArticleController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `GET` | `/api/admin/categories/` | `AdminCategoryController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `POST` | `/api/admin/categories/` | `AdminCategoryController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `DELETE` | `/api/admin/categories/{id}` | `AdminCategoryController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `PUT` | `/api/admin/categories/{id}` | `AdminCategoryController` | _Authentifié (`/api/**`)_ | `[STAFF]` |

## 11. Articles & Journal (Public/Joueur) (3 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/articles/` | `ArticleController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/articles/{articleId}` | `ArticleController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/categories/` | `CategoryController` | _Authentifié (`/api/**`)_ | `[J]` |

## 12. Administration - Leaderboard (4 endpoints) [SOUS_RESPOS_DEFIS]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/leaderboards/` | `AdminLeaderboardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPOS_DEFIS]` |
| `PATCH` | `/api/admin/leaderboards/` | `AdminLeaderboardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPOS_DEFIS]` |
| `GET` | `/api/admin/leaderboards/latest` | `AdminLeaderboardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPOS_DEFIS]` |
| `POST` | `/api/admin/leaderboards/release` | `AdminLeaderboardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPOS_DEFIS]` |

## 12. Leaderboard / Classement (Public/Joueur) (1 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/leaderboards/departments` | `LeaderboardController` | _Authentifié (`/api/**`)_ | `[J]` |

## 13. Administration - Places & Carte (2 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/place/config/` | `AdminPlaceController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/place/config/` | `AdminPlaceController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |

## 13. Places & Carte (Joueur) (1 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/place/canvas` | `PlaceController` | _Authentifié (`/api/**`)_ | `[J]` |

## 14. Redirections & QR Links (2 endpoints) [SOUS_RESPO_DEFIS]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/qr-link/` | `AdminQrLinkController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `PUT` | `/api/admin/qr-link/` | `AdminQrLinkController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |

## 15. Administration - Assets & Configuration du Jeu (14 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `POST` | `/api/admin/game/assets` | `AdminGameAssetController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/game/configs/` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `DELETE` | `/api/admin/game/configs/folders` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/game/configs/folders` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/game/configs/folders` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/game/configs/releases` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/game/configs/releases` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/game/configs/releases/latest` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/game/configs/releases/upload` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `DELETE` | `/api/admin/game/configs/releases/{version}` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/game/configs/releases/{version}/files/{*fileName}` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `DELETE` | `/api/admin/game/configs/{*fileName}` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/game/configs/{*fileName}` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PUT` | `/api/admin/game/configs/{*fileName}` | `AdminGameConfigController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |

## 15. Assets du Jeu (Public/Joueur) (1 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/public/game` | `GameAssetController` | _PermitAll (Public)_ | `[TLM (Public)]` |

## 16. STS (Import & Outils Données Cards) (12 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/sts/catalog/characters` | `StsCatalogController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/sts/catalog/enemies` | `StsCatalogController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/sts/catalog/enemy-pool` | `StsCatalogController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/sts/runs/current` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/nodes/{nodeId}/complete` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/nodes/{nodeId}/enter` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/reset` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/retire` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/sts/runs/{runId}/retreat-preview` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/retreat/continue` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/rewards/{rewardId}/claim` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |

## 17. Administration - Liens de vérification des départements (2 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/department-verification-links/` | `AdminDepartmentVerificationLinkController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `PUT` | `/api/admin/department-verification-links/` | `AdminDepartmentVerificationLinkController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |

## 17. Départements (Public/Joueur) (1 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/departments/` | `DepartmentController` | _Authentifié (`/api/**`)_ | `[J]` |

## 18. Endpoints de Test & Sécurité (2 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/test` | `SecurityTestController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/test/user` | `SecurityTestController` | _Authentifié (`/api/**`)_ | `[TLM (Auth)]` |

## 2. Administration - Utilisateurs (8 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/users` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `GET` | `/api/admin/users/{userId}` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[STAFF]` |
| `GET` | `/api/admin/users/{userId}/available-challenges` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `DELETE` | `/api/admin/users/{userId}/cards` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/users/{userId}/cards` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/users/{userId}/challenge-submissions` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[SOUS_REPOS_DEFIS]` |
| `PATCH` | `/api/admin/users/{userId}/leaderboard-visibility` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `PATCH` | `/api/admin/users/{userId}/tokens` | `AdminUserController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |

## 2. Utilisateurs (Profil) (6 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/leaderboards/users` | `LeaderboardController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/leaderboards/users/total` | `LeaderboardController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/users/me` | `UserController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/users/me/reset-first-scan` | `UserController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/users/profile` | `UserController` | _Authentifié (`/api/**`)_ | `[J]` |
| `PATCH` | `/api/users/profile` | `UserController` | _Authentifié (`/api/**`)_ | `[J]` |

## 3. Amis & Social (11 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/friends/` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/friends/contacts/search` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/friends/requests/incoming` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/friends/requests/outgoing` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/friends/requests/{requestId}` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/friends/requests/{requestId}/accept` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/friends/requests/{requestId}/decline` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/friends/requests/{userId}` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/friends/search` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/friends/{userId}` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/friends/{userId}/profile` | `FriendController` | _Authentifié (`/api/**`)_ | `[J]` |

## 4. Notifications (4 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/notifications/` | `NotificationController` | _Authentifié (`/api/**`)_ | `[J]` |
| `PATCH` | `/api/notifications/read-all` | `NotificationController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/notifications/unread-count` | `NotificationController` | _Authentifié (`/api/**`)_ | `[J]` |
| `PATCH` | `/api/notifications/{id}/read` | `NotificationController` | _Authentifié (`/api/**`)_ | `[J]` |

## 5. Modération (Validation des Preuves) (11 endpoints) [SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/moderation/submissions/pending` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `GET` | `/api/moderation/submissions/{id}` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `POST` | `/api/moderation/submissions/{id}/decide` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `GET` | `/api/moderation/submissions/{id}/messages` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `POST` | `/api/moderation/submissions/{id}/messages` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `PATCH` | `/api/moderation/submissions/{id}/messages/enabled` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `GET` | `/api/moderation/submissions/{id}/proofs/{index}/content` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `DELETE` | `/api/moderation/submissions/{id}/review-lock` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `POST` | `/api/moderation/submissions/{id}/review-lock` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `PUT` | `/api/moderation/submissions/{id}/review-lock/heartbeat` | `AdminSubmissionController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |
| `GET` | `/api/moderation/test` | `SecurityTestController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS, SOUS_RESPO_DEPART]` |

## 5. Upload de Preuves (Joueur) (5 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/uploads/proofs/` | `ProofUploadController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/uploads/proofs/direct` | `ProofUploadController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/uploads/proofs/direct/batch` | `ProofUploadController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/uploads/proofs/{uploadId}` | `ProofUploadController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/uploads/proofs/{uploadId}/complete` | `ProofUploadController` | _Authentifié (`/api/**`)_ | `[J]` |

## 6. Administration - Défis & Catégories (9 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/challenge-categories/` | `AdminChallengeCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `POST` | `/api/admin/challenge-categories/` | `AdminChallengeCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `DELETE` | `/api/admin/challenge-categories/{name}` | `AdminChallengeCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `PUT` | `/api/admin/challenge-categories/{name}` | `AdminChallengeCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `GET` | `/api/admin/challenges/` | `AdminChallengeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `POST` | `/api/admin/challenges/` | `AdminChallengeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `GET` | `/api/admin/challenges/auto-types` | `AdminChallengeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `DELETE` | `/api/admin/challenges/{id}` | `AdminChallengeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |
| `PUT` | `/api/admin/challenges/{id}` | `AdminChallengeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_DEFIS]` |

## 6. Défis (Joueur) (15 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/challenges/` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/challenges/favorites` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/challenges/submissions/pending-validation` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/challenges/submissions/results` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/challenges/submissions/waiting-confirmation` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/challenges/submissions/{id}` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/challenges/submissions/{id}` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/challenges/submissions/{id}/messages` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/challenges/submissions/{id}/messages` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/challenges/submissions/{id}/participants/accept` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/challenges/submissions/{id}/participants/decline` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/challenges/submissions/{id}/participants/resend` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/challenges/{id}/favorite` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/challenges/{id}/favorite` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/challenges/{id}/submit` | `ChallengeController` | _Authentifié (`/api/**`)_ | `[J]` |

## 7. Administration - Événements & Catégories (12 endpoints) [SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/event-categories/` | `AdminEventCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `POST` | `/api/admin/event-categories/` | `AdminEventCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `DELETE` | `/api/admin/event-categories/{categoryId}` | `AdminEventCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `PUT` | `/api/admin/event-categories/{categoryId}` | `AdminEventCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `GET` | `/api/admin/events/` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `POST` | `/api/admin/events/` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `DELETE` | `/api/admin/events/{eventId}` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `GET` | `/api/admin/events/{eventId}` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `PUT` | `/api/admin/events/{eventId}` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `POST` | `/api/admin/events/{eventId}/cover` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `GET` | `/api/admin/events/{eventId}/registrants` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |
| `GET` | `/api/admin/events/{eventId}/waitlist` | `AdminEventController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_EVENT, SOUS_RESPO_DEPART, SOUS_RESPO_TOURNOIS]` |

## 7. Événements (Joueur) (7 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/events/` | `EventController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/events/registrations` | `EventController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/events/waitlist` | `EventController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/events/{eventId}/registrations` | `EventController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/events/{eventId}/registrations` | `EventController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/sts/catalog/events` | `StsCatalogController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/sts/runs/{runId}/events/{eventInstanceId}/choose` | `StsRunController` | _Authentifié (`/api/**`)_ | `[J]` |

## 8. Administration - Cartes (Catalogue & Archives) (32 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/card-categories/` | `AdminCardCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES, RESPOS_DEFIS (lecture seule)]` |
| `POST` | `/api/admin/card-categories/` | `AdminCardCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/card-categories/{id}` | `AdminCardCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PUT` | `/api/admin/card-categories/{id}` | `AdminCardCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/card-categories/{id}/image` | `AdminCardCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PATCH` | `/api/admin/card-categories/{id}/image` | `AdminCardCategoryController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/card-types/` | `AdminCardTypeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES, RESPOS_DEFIS (lecture seule)]` |
| `POST` | `/api/admin/card-types/` | `AdminCardTypeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/card-types/{id}` | `AdminCardTypeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PUT` | `/api/admin/card-types/{id}` | `AdminCardTypeController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/cards/` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES, RESPOS_DEFIS (lecture seule)]` |
| `POST` | `/api/admin/cards/` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PUT` | `/api/admin/cards/categories/{categoryId}/order` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/cards/cover` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PATCH` | `/api/admin/cards/cover` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/cards/export` | `AdminCardArchiveController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `POST` | `/api/admin/cards/import` | `AdminCardArchiveController` | _Authentifié (`/api/**`)_ | `[ADMIN]` |
| `GET` | `/api/admin/cards/instances` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/cards/instances` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/cards/instances/{instanceId}` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/cards/instances/{instanceId}` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PATCH` | `/api/admin/cards/instances/{instanceId}` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PUT` | `/api/admin/cards/instances/{instanceId}` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/cards/instances/{instanceId}/assignment` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/cards/instances/{instanceId}/assignment` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/cards/{id}` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PATCH` | `/api/admin/cards/{id}` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/cards/{id}/collections` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PATCH` | `/api/admin/cards/{id}/image` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/cards/{id}/instances` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/cards/{id}/instances` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/cards/{id}/instances/batch` | `AdminCardController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |

## 8. Administration - Decks (3 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `POST` | `/api/admin/decks/` | `AdminDeckController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PATCH` | `/api/admin/decks/{deckId}` | `AdminDeckController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PUT` | `/api/admin/decks/{deckId}/drop-tiers/order` | `AdminDeckController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |

## 8. Administration - Impression Physique de Cartes (13 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/batches` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/batches/pages/{printPageId}` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/batches/regenerate-pdfs` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/batches/{id}` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/batches/{id}` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/batches/{id}/cards` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/batches/{id}/pdf` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/card-print-runs` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/card-print-templates` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `GET` | `/api/admin/card-print-templates` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `POST` | `/api/admin/card-print-templates` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `DELETE` | `/api/admin/card-print-templates/{id}` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |
| `PUT` | `/api/admin/card-print-templates/{id}/calibration` | `AdminCardPrintController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_CARTES]` |

## 8. Cartes & Collection (Joueur) (4 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/cards/cover` | `CardController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/cards/deck` | `CardController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/cards/instances/claim` | `CardController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/sts/catalog/cards` | `StsCatalogController` | _Authentifié (`/api/**`)_ | `[J]` |

## 8. Decks (Joueur) (3 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/decks/` | `DeckController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/decks/{deckId}` | `DeckController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/decks/{deckId}/pull` | `DeckController` | _Authentifié (`/api/**`)_ | `[J]` |

## 9. Administration - Boutique & Créneaux de Retrait (20 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/admin/shop/orders` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `PUT` | `/api/admin/shop/orders/{orderId}/status` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `GET` | `/api/admin/shop/products` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `POST` | `/api/admin/shop/products` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `DELETE` | `/api/admin/shop/products/{productId}` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `GET` | `/api/admin/shop/products/{productId}` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `PUT` | `/api/admin/shop/products/{productId}` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `POST` | `/api/admin/shop/products/{productId}/photos` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `PUT` | `/api/admin/shop/products/{productId}/photos/order` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `DELETE` | `/api/admin/shop/products/{productId}/photos/{photoId}` | `AdminShopController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `GET` | `/api/admin/shop/reservation-slots` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `POST` | `/api/admin/shop/reservation-slots/event` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `POST` | `/api/admin/shop/reservation-slots/custom` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `POST` | `/api/admin/shop/reservation-slots/recurring` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `PUT` | `/api/admin/shop/reservation-slots/{slotId}` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `DELETE` | `/api/admin/shop/reservation-slots/{slotId}` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `POST` | `/api/admin/shop/reservation-slots/{slotId}/activate` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `DELETE` | `/api/admin/shop/reservation-slots/series/{seriesId}` | `AdminShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `GET` | `/api/admin/shop/settings` | `AdminShopSettingsController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |
| `PUT` | `/api/admin/shop/settings` | `AdminShopSettingsController` | _Authentifié (`/api/**`)_ | `[SOUS_RESPO_GOODIES]` |

## 9. Boutique (Joueur) (9 endpoints)

| Méthode | Endpoint Path | Contrôleur | Sécurité Actuelle (`@PreAuthorize`) | Rôles / Permissions à Valider |
|---|---|---|---|---|
| `GET` | `/api/shop/cart` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/shop/cart/items/{productId}` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `PUT` | `/api/shop/cart/items/{productId}` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/shop/orders` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `POST` | `/api/shop/orders` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `DELETE` | `/api/shop/orders/{orderId}` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/shop/products` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/shop/products/{productId}` | `ShopController` | _Authentifié (`/api/**`)_ | `[J]` |
| `GET` | `/api/shop/reservation-slots` | `ShopReservationSlotController` | _Authentifié (`/api/**`)_ | `[J]` |
