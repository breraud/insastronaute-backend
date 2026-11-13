# INSAstronaute Web API

Backend API for the INSAstronaute application. It manages authenticated users, departments, challenges, proof uploads, moderation workflows, and public leaderboards.

The project is a Spring Boot application backed by PostgreSQL, Redis, Keycloak/OIDC, and S3-compatible object storage.

## Quick Start

Clone the public repository and run the tests and JAR build with JDK 25:

```bash
git clone https://github.com/breraud/insastronaute-backend.git
cd insastronaute-backend
./gradlew test bootJar
```

The tests use an embedded H2 database. Running the API also requires PostgreSQL,
Redis, a configured Keycloak client, and S3-compatible storage. See the configuration
and run commands below; Compose starts the API, PostgreSQL and Redis, while Keycloak
and object storage must be supplied separately.

For local Compose configuration, copy `.env.example` to `.env` and replace the
placeholders. Use `.env.prod.example` for a deployment. Gradle does not load `.env`
automatically: export the variables when running the API outside Compose.

## License

This project's code is distributed under [PolyForm Noncommercial 1.0.0](LICENSE).
It permits use, modification and redistribution for the purposes allowed by the
license, including noncommercial purposes. Dependencies and bundled third-party
components retain their respective licenses and notices.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidance.

## Main Features

- Keycloak login with OAuth2/OIDC.
- Session and CSRF endpoints for a browser frontend.
- User synchronization from Keycloak profile, roles, and groups.
- Department-aware challenge listing and submissions.
- Admin challenge management.
- Extensible automatic challenge engine with scheduled, idempotent awards.
- Admin player-profile corrections: approved manual challenge submissions and player-scoped virtual/physical card catalogue management.
- Moderator review of pending submissions.
- Proof upload storage in S3-compatible storage.
- Article drafts with WYSIWYG HTML content, cover images, and public media uploads.
- Event questionnaires asked at registration, with per-option tallies for the organiser.
- Events with publication windows, department targeting, optional article links, quotas, registrations, and waitlists.
- Reservation shop with an admin-managed product catalogue, product photos, one cart per user, and preorder processing.
- Collection points scale per card category, paying tokens on acquisition and feeding the collection leaderboards.
- Collectible card deck with public S3 image and thumbnail storage for cards and card categories, including admin-hidden categories.
- PDF-based physical card print templates with calibrated card slots and printable claim codes.
- Purchasable card decks with dynamic drop tiers and server-side weighted pulls.
- Stage 1 STS run backend with catalog reads, server-owned map progression, encounters, rewards, and token grants.
- Friend requests, friend acceptance, and friend notifications.
- Accepted-friend profile reads with collection, challenge, and ranking progress.
- User and department leaderboard snapshots.
- Tournament attendance check-in that converts presence and podium placements into leaderboard points.
- Extensible achievements built from parameterised key phrases, paying tokens and raising an in-app popup.
- Real-time gamified r/place canvas with challenge-earned bonus pixels.
- OpenAPI/Swagger documentation for authenticated users.
- Flyway database migrations.

## Tech Stack

- Java 25
- Spring Boot 4
- Gradle
- PostgreSQL 15
- Redis 8
- Flyway
- Keycloak
- AWS SDK S3 client, usable with S3-compatible services
- Docker and Docker Compose

## Project Layout

```text
src/main/java/fr/insastronaute/webapi
├── auth          Session endpoints and Keycloak user synchronization
├── article       Article drafts, cover images, HTML publishing, and public media uploads
├── card          Collectible cards, player decks, and admin card management
├── challenge     Challenge, submission, admin, and moderation logic
├── common        Shared config, security, CSRF, CORS, and error handling
├── department    Department API and repository
├── event         Events, categories, registrations, and waitlists
├── friend        Friend requests, friendship listing, and friend search
├── leaderboard   Leaderboard release and read APIs
├── proof         Proof draft/upload logic
├── shop          Product catalogue, carts, preorder workflow, and product photos
├── storage       S3 client configuration
├── sts           Stage 1 STS catalogs, run state, map progression, and rewards
└── user          User profile and admin user endpoints

src/main/resources/db/migration
└── Flyway SQL migrations
```

## Requirements

Install these before running the application:

- JDK 25
- Docker and Docker Compose
- A running Keycloak realm
- An S3-compatible storage service

For tests, the project uses H2 and does not need PostgreSQL.

## Configuration

The application reads configuration from environment variables. The most important variables are:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | HTTP port used by Spring Boot | `3457` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5433/insastronaute` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username | required |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password | required |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `SPRING_SESSION_STORE_TYPE` | HTTP session backend; production should use Redis | `redis` |
| `SPRING_SESSION_REDIS_NAMESPACE` | Redis namespace for Spring Session keys | `insastronaute:sessions` |
| `SERVER_SERVLET_SESSION_TIMEOUT` | Server-side JSESSIONID lifetime | `8h` |
| `SERVER_SERVLET_SESSION_COOKIE_MAX_AGE` | Browser JSESSIONID cookie lifetime | `8h` |
| `SERVER_SERVLET_SESSION_COOKIE_HTTP_ONLY` | Mark JSESSIONID as HttpOnly | `true` |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | Mark JSESSIONID as Secure; set `true` behind HTTPS | `false` |
| `SERVER_SERVLET_SESSION_COOKIE_SAME_SITE` | JSESSIONID SameSite policy; use `none` for cross-site HTTPS SPA deployments | `lax` |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | Maximum size for one multipart file upload; increase for admin game build uploads | `20MB` |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | Maximum multipart request size, including form overhead; increase for admin game build uploads | `25MB` |
| `SERVER_TOMCAT_MAX_PART_COUNT` | Maximum number of multipart parts; must be higher than the number of uploaded game files plus form fields | `1000` |
| `KEYCLOAK_CLIENT_ID` | Keycloak client id | `inte-backend` |
| `KEYCLOAK_CLIENT_SECRET` | Keycloak client secret | required |
| `KEYCLOAK_ISSUER_URI` | Keycloak realm issuer URL | `http://localhost:8081/realms/inte` |
| `KEYCLOAK_DEPARTMENT_GROUP_SYNC_ENABLED` | Synchronize managed Keycloak groups from the `department` claim at login | `true` |
| `KEYCLOAK_DEPARTMENT_GROUP_SYNC_CONNECT_TIMEOUT` | Keycloak Admin API connection timeout | `2s` |
| `KEYCLOAK_DEPARTMENT_GROUP_SYNC_READ_TIMEOUT` | Keycloak Admin API response timeout | `5s` |
| `APP_SECURITY_FRONTEND_BASE_URL` | Frontend URL used after login/logout | `http://localhost:5173` |
| `DOMAIN` | Production cookie/CORS domain | empty |
| `APP_SECURITY_ALLOWED_SUBDOMAINS` | Extra production subdomains for CORS | `admin,app,s3` |
| `APP_AUTH_GATE_SECRET` | HMAC secret for admission tokens; use a unique random value for each deployment | development default; replace in deployment |
| `APP_SECURITY_PRODUCTION_HARDENING_ENABLED` | Fail startup when the frontend is not HTTPS or the cookie domain is missing | `false` |
| `QR_LINK_PUBLIC_URL` | Permanent API URL encoded in the printed QR code and displayed in PanelAdmin | `https://api.example.com/q` |
| `QR_LINK_SCAN_LANDING_URL` | INSAstral landing used while QR challenge mode is enabled | `https://frontend.example.com/scan` |
| `APP_AUTO_CHALLENGES_SCHEDULER_ENABLED` | Enable scheduled automatic challenge evaluation | `true` |
| `APP_AUTO_CHALLENGES_SCHEDULER_CRON` | Spring cron expression for automatic challenge evaluation | `0 0 * * * *` |
| `APP_AUTO_CHALLENGES_SCHEDULER_BATCH_SIZE` | Maximum candidates requested from an automatic type per page | `250` |
| `APP_AUTO_CHALLENGES_SCHEDULER_LOCK_DURATION` | Redis distributed-lock TTL for one evaluation run | `PT55M` |
| `APP_ACHIEVEMENTS_CATCH_UP_ENABLED` | Enable the hourly achievement catch-up sweep | `true` |
| `APP_ACHIEVEMENTS_CATCH_UP_CRON` | Spring cron expression for the catch-up sweep | `0 30 * * * *` |
| `S3_ENDPOINT` | S3-compatible endpoint | empty |
| `S3_PUBLIC_ENDPOINT` | Public browser-accessible S3 endpoint for proof uploads, proof read URLs, card assets, card category images, deck images, game assets, game configs, article/event/shop images, and article media | `S3_ENDPOINT` |
| `S3_ACCESS_KEY` | S3 access key | empty |
| `S3_SECRET_KEY` | S3 secret key | empty |
| `S3_REGION` | S3 region | `eu-fr` |
| `S3_BUCKET` | Bucket for proof files | `insastronaute-proofs` |
| `S3_PUBLIC_BUCKET` | Public bucket for card images, thumbnails, card category images, deck images, game assets, game configs, article/event/shop images, and article media | `insastronaute-public` |
| `S3_BUCKET_CORS_ENABLED` | Apply proof bucket CORS from frontend allowed origins at startup | `true` |
| `S3_PUBLIC_BUCKET_RUN_ACL_HARDENING_ENABLED` | At startup, scan `card-print/runs/` in the public bucket and set existing run PDF object ACLs to private; useful as a one-time migration for old objects | `false` |
| `S3_FAIL_ON_POLICY_ERROR` | Fail startup when the public bucket policy cannot be applied | `false` |
| `S3_FAIL_ON_CORS_ERROR` | Fail startup when proof-bucket CORS cannot be applied | `false` |
| `S3_FAIL_ON_ACL_ERROR` | Fail startup when print-run ACL hardening fails | `false` |
| `APP_PROOFS_UPLOAD_TIMEOUT_MINUTES` | Timeout for unfinished direct uploads | `15` |
| `APP_ARTICLES_MEDIA_CLEANUP_ENABLED` | Delete orphaned article media from the public S3 bucket on a schedule | `true` |
| `APP_ARTICLES_MEDIA_CLEANUP_CRON` | Cron for article media cleanup | `0 0 3 * * *` |
| `APP_GAME_CONFIG_OBJECT_PREFIX` | Public S3 prefix where admin JSON game configs are listed, read, and overwritten | `GameData/current/dev/` |
| `APP_GAME_CONFIG_RELEASES_OBJECT_PREFIX` | Public S3 prefix where versioned game config releases and latest metadata are stored | `GameData/versions/` |

Use `.env.prod.example` as a starting point for deployment configuration:

```bash
cp .env.prod.example .env
```

Production Compose enables the hardening and S3 fail-fast flags and binds the
backend to `127.0.0.1` by default. Supply PostgreSQL, Redis, S3-compatible storage,
and Keycloak on the same Docker network when using `compose.prod.yaml`.

GitHub Actions runs the test suite and builds the application JAR on a hosted runner.
Deployment is configured independently by each installation.

Then replace every `change-me` value. Generate a unique auth-gate signing secret
for the deployment and set `APP_AUTH_GATE_SECRET`, for example using
`openssl rand -hex 32`. Keep this value private and stable between restarts; the
built-in development default is unsuitable for a deployment.

Do not commit real secrets.

## Running With Docker Compose

Build and start the stack:

```bash
docker compose up --build
```

This starts:

- `app`: the Spring Boot API
- `postgres`: PostgreSQL database
- `redis`: Redis

The API is published on port `3457` by both local and production compose files.

## Running Locally With Gradle

Start PostgreSQL and Redis first. You can use the compose services, or your own local services.

Export the required variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/insastronaute
export SPRING_DATASOURCE_USERNAME=your_db_user
export SPRING_DATASOURCE_PASSWORD=your_db_password
export KEYCLOAK_CLIENT_SECRET=your_keycloak_client_secret
export KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/inte
export S3_ENDPOINT=http://localhost:8333
export S3_PUBLIC_ENDPOINT=http://localhost:8333
export S3_ACCESS_KEY=your_s3_access_key
export S3_SECRET_KEY=your_s3_secret_key
export S3_PUBLIC_BUCKET=insastronaute-public
```

Run the API:

```bash
./gradlew bootRun
```

By default, the API is available at:

```text
http://localhost:3457
```

## Database

Flyway migrations are stored in:

```text
src/main/resources/db/migration
```

Spring validates the JPA schema at startup:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

This means database structure changes should be made through new Flyway migrations.

## Authentication And Roles

The API is protected by Keycloak OIDC login.

Security rules:

- `/api/session` and `/api/csrf` are public.
- `/api/**` requires authentication.
- Published article reads under `/api/articles/**` require authentication; article management routes under `/api/admin/articles/**` require `ADMIN`.
- `/api/moderation/**` requires `ADMIN`, `RESPOS_DEFIS`, `SOUS_RESPO_DEFIS`, or `RESPO_DEPART`; submission visibility and decisions are filtered by challenge sensitivity, moderator gender, and the submitter department verification link.
- `/api/admin/**` requires `ADMIN`.
- `/api/sts/**` requires authentication.
- Swagger UI is authenticated.

Roles are extracted from the Keycloak token and mapped to Spring roles.

At login, the backend reads the multivalued `department` claim and synchronizes
the user's membership in the managed Keycloak groups `CFI`, `EP`, `GM`, `ITI`,
`MECA`, and `MRIE`. Matching is case-insensitive. Managed groups absent from the
claim are removed, while all unrelated groups are preserved. Unknown claim
values are ignored. Configure a Keycloak protocol mapper so `department` is
available to the OIDC user, and enable service accounts plus client-credentials
authentication on `KEYCLOAK_CLIENT_ID`. Its service account needs permission to
list realm groups and to view and manage user group membership (typically the
appropriate `realm-management` group-query, user-view, and user-management
roles).

Keycloak Admin API failures are logged but do not reject login. The local user
record immediately prefers `department`, because the current token's `groups`
claim was issued before the Admin API update; when `department` is absent it
falls back to `groups` for compatibility.

## CSRF

State-changing browser requests must send the CSRF token.

Fetch the token from:

```text
GET /api/csrf
```

Then send it in the header:

```text
X-XSRF-TOKEN: <token>
```

The token is also exposed through the `XSRF-TOKEN` cookie.

## Main API Areas

## Permanent QR Link

`GET /q` is public and is the stable URL to encode in the printed QR code. When challenge mode is disabled it redirects immediately to the external HTTPS URL configured by an administrator. When challenge mode is enabled, anonymous visitors start Keycloak login immediately and return to the INSAstral scan landing; authenticated visitors go directly to that landing.

Administrators read and update the singleton configuration through `GET` and `PUT /api/admin/qr-link`. The update accepts `redirectUrl` and `challengeModeEnabled`; only absolute HTTPS destinations are allowed. The player landing calls authenticated, CSRF-protected `POST /api/scan`. An active first scan permanently sets `users.scanned=true`, repeated scans return `ALREADY_SCANNED`, and inactive mode returns `404` without modifying the user.

See [the QR link flow](docs/flows/qr-link.md) for the complete browser and authentication behavior.

## Shop

The authenticated shop API is available under `/api/shop/**`:

- `GET /api/shop/products` and `GET /api/shop/products/{productId}` expose the catalogue, including out-of-stock state, ordered photos, price in euro cents, and the optional linked article.
- `GET /api/shop/cart`, `PUT /api/shop/cart/items/{productId}`, and `DELETE /api/shop/cart/items/{productId}` manage the authenticated user's single cart.
- `POST /api/shop/orders` submits the cart as a preorder. `GET /api/shop/orders` lists the user's preorders, and `DELETE /api/shop/orders/{orderId}` cancels a pending preorder.

Administration routes are under `/api/admin/shop/**` and require `ADMIN`. They provide product CRUD, product photo upload/order/deletion, preorder filtering, and status transitions through `PENDING`, `VALIDATED`, `COMPLETED`, and `CANCELLED`. The shop does not process payments or exact stock counts. Product photos are stored in the public S3 bucket under `shop/products/{productId}/`.

Frontend teams should use the [shop business flow guide](docs/shop-frontend-business-flow.md) for screen behavior, state transitions, payload examples, unavailable-product handling, and integration rules.

### Mode de test : collection complète pour tout le monde

`PUT /api/admin/cards/collection-test-mode` (`{"allCardsOwned": true}`) fait voir à chaque joueur une collection complète : de quoi essayer ce que les cartes débloquent — decks PvP en tête — sans avoir à les distribuer. `GET` sur la même route lit l'état.

L'interrupteur **ne donne aucune carte**. Aucune ligne de collection n'est écrite, et le refermer suffit à revenir à l'état réel. Il agit à un seul endroit : la collection *servie* par `GET /api/cards/deck`, que tous les clients lisent. Ce que chacun possède vraiment continue de décider du classement, des succès et des points de collection, qui interrogent la base directement.

`CardService` expose donc deux lectures, et la distinction n'est pas cosmétique : `getDeck` rend ce que le joueur voit, `getOwnedDeck` ce qu'il possède. Qui décide d'une récompense doit lire la seconde — l'attribution de cartes de fin de run, servie par la première, conclurait que tout est acquis et cesserait de donner quoi que ce soit, en silence.

`GET /api/sts/pvp/collection` renvoie le drapeau à côté de la liste, sans quoi un joueur verrait tout le catalogue et croirait l'avoir acquis. Un deck enregistré pendant l'ouverture reste tel quel après la fermeture ; c'est au prochain enregistrement qu'il se heurte de nouveau à la règle.

## r/place Event

The r/place event is configured by an administrator through `GET` and `PUT /api/admin/place/config`.
Canvas width and height are each limited to `1..2000` (maximum `2000 x 2000`). A size-changing update must include
`resizeMode: "PRESERVE_CENTERED"` or `resizeMode: "RESET"`; unchanged dimensions omit it.
`PRESERVE_CENTERED` retains the centered overlap and crops equally from opposite edges, while
`RESET` creates a blank canvas. All `/api/place/**` endpoints require an authenticated user and
state-changing calls require the usual `X-XSRF-TOKEN` header.

Players place pixels with `POST /api/place/pixel`:

```json
{ "x": 12, "y": 34, "color": "#AABBCC" }
```

Colors must use `#RRGGBB`. A successful response includes `x`, `y`, `color`, `revision`,
`cooldownType`, `remainingCooldownMs`, and `nextPlacementAt`; clients start the returned cooldown
immediately. Stable placement errors use `PLACE_INACTIVE`, `PLACE_INVALID_COORDINATES`, and
`PLACE_COOLDOWN`; cooldown responses also include `remainingCooldownMs` and `Retry-After`.

### Pixel bonus payant

Un joueur peut payer pour poser sans attendre : `POST /api/place/pixel` avec `bonus: true`. L'achat ne prend ni n'avance le cooldown en cours — c'est exactement ce qu'il achète — et la réponse porte alors `purchased: true`, le solde restant (`tokens`) et le prix du suivant (`nextBonusPrice`).

Deux achats d'affilée sont refusés (`PLACE_BONUS_CHAINED`) : il faut un pixel posé au cooldown normal entre les deux, sinon le cooldown ne serait plus une attente mais un tarif. Le prix monte d'un cran à chaque achat, pour la même raison à l'échelle de l'événement. Un solde trop court renvoie `PLACE_BONUS_INSUFFICIENT_TOKENS`.

`GET /api/place/bonus` donne au front de quoi afficher — ou griser — le bouton sans deviner : `price`, `tokens`, `affordable`, `blockedByPreviousPurchase`, `available`.

Le prix de départ (30 jetons) et sa hausse par achat (1 jeton) se règlent dans la configuration admin, via `bonusPixelPrice` et `bonusPixelPriceIncrement`. Les deux champs sont facultatifs à l'envoi : absents, ils laissent les valeurs en place, pour qu'un client plus ancien puisse continuer à configurer le reste. Une hausse à 0 fige le prix.

Ces pixels achetés sont comptés séparément de ceux gagnés aux défis : les succès r/place restent liés au jeu, pas au solde.

`GET /api/place/canvas` returns an `image/png` snapshot with mandatory `X-Place-Revision`.
Subscribe over STOMP to `/topic/canvas-updates` after connecting to `/ws/place`. Pixel messages
are `{ type: "PIXEL", x, y, color, revision: "43" }`; resize messages are
`{ type: "CANVAS_RESET", revision: "44" }`. Revisions are canonical decimal strings so browsers
never lose integer precision. Events intentionally contain no player identity. Clients
reload the snapshot after reconnects, revision gaps, or `CANVAS_RESET`.

Resize orchestration holds a bounded, owner-token Redis lease from before the source read through
database commit or compensation. An owner-safe heartbeat renews the lease throughout long resize
calculations and transactions; a synchronous owner check in the transaction's `beforeCommit`
callback forces rollback and canvas compensation if ownership was lost. The heartbeat is stopped
before compare-and-delete release. Placements receive
retryable `PLACE_RESIZE_IN_PROGRESS` while it is held without consuming cooldown or bonus state.
Failed candidates and compensated generations are removed. A reset outbox row is committed in the
same SQL transaction as the configuration. After commit, publication is attempted before
best-effort removal of the inactive old generation; broker failures remain queued for scheduled
retry and cannot change the successful response. Revision PNG caches expire.

See [docs/flows/place.md](docs/flows/place.md) for the complete frontend integration flow and response contracts.

### Chasse au trésor

Les joueurs authentifiés lisent leur progression par voie avec `GET /api/treasure-hunt/progress`, puis valident un checkpoint avec `POST /api/treasure-hunt/checkpoints/validate` (`{"pathId":"...","code":"..."}`). Le code doit appartenir à la voie sélectionnée ; un code inconnu ou rattaché à une autre voie renvoie le même message générique. `GET /api/treasure-hunt/vault/codes` reste disponible pour lire les codes personnels de coffre, et `POST /api/treasure-hunt/vault/unlock` (`{"codes":["..."]}`) ouvre le coffre. Chaque code de coffre est tiré aléatoirement et contient exactement six caractères parmi `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (`I`, `O`, `0` et `1` sont exclus). Un même code peut appartenir à plusieurs joueurs, mais reste unique entre les six voies d'un joueur. Les six codes doivent être envoyés dans l'ordre courant des voies.

Les routes d'administration sont sous `/api/admin/treasure-hunt/**` et nécessitent le rôle `ADMIN`. Toute mutation de configuration désactive la chasse, qui doit être réactivée explicitement après vérification. Les six voies peuvent être réordonnées par `PUT /paths/order`, et les étapes d'une voie par `PUT /paths/{pathId}/checkpoints/order`. Toute étape peut être supprimée ; les suivantes sont renumérotées. Les réponses admin des étapes contiennent `id`, `pathId`, `stepNumber` et le `code` enregistré, afin qu'il puisse être relu et modifié. Les étapes créées avant cette évolution retournent `code: null` jusqu'à leur prochain enregistrement. Les étapes et le coffre possèdent des contenus ordonnés (`RICH_TEXT`, `MEDIA`, `ARTICLE`, `FINAL_CODE`) configurables par les routes `/checkpoints/{checkpointId}/contents` et `/vault/contents`; un article est référencé par son identifiant. `FINAL_CODE` ne peut être ajouté que sur la dernière étape de sa voie. Les médias sont stockés dans le bucket privé `S3_BUCKET`, sous `treasure-hunt/`. Les formats admis sont JPEG, PNG, WebP, MP4, WebM, QuickTime et PDF, dans la limite de 15 MB. Les URL de lecture joueur sont pré-signées pour 15 minutes ; l'API joueur ne renvoie ni hash, ni code de checkpoint, ni chemin S3 interne.

La visibilité est un interrupteur distinct de l'activation. `PUT /api/admin/treasure-hunt/visibility` (`{"visible": false}`) retire entièrement la chasse du front joueur, et `GET /api/treasure-hunt/visibility` (`{"visible": ...}`) est ce que le front lit avant même d'afficher une tuile. Les deux notions sont séparées parce que toute retouche de contenu désactive la chasse : perdre le choix d'affichage à chaque correction de texte serait un piège. Une chasse masquée est aussi fermée — un lien gardé en favori ne rouvre pas ce qu'on a retiré.

See [docs/flows/treasure-hunt.md](docs/flows/treasure-hunt.md) for the detailed admin and player flow.

### STS Stage 1

The first STS backend layer reads Stage 1 JSON files from the latest game config release in the public S3 bucket. Required release paths include:

```text
catalogs/cards.json
catalogs/characters/*.json
catalogs/enemies.json
EnemyData/*.json
EnemyPool/EnemyPool.json
gameplay/map_generation.json
gameplay/reward_tables.json
gameplay/run_score_rewards.json
gameplay/relics.json
gameplay/encounter_tables.json
events/EventData.json
```

Supported Stage 1 routes are available under `/api/sts`:

```text
GET /api/sts/catalog/cards
GET /api/sts/catalog/characters
GET /api/sts/catalog/enemies
GET /api/sts/catalog/enemy-pool
GET /api/sts/catalog/events
POST /api/sts/runs
GET /api/sts/runs/current
POST /api/sts/runs/{runId}/nodes/{nodeId}/enter
POST /api/sts/runs/{runId}/nodes/{nodeId}/complete
POST /api/sts/runs/{runId}/rewards/{rewardId}/claim
POST /api/sts/runs/{runId}/events/{eventInstanceId}/choose
POST /api/sts/runs/{runId}/retire
POST /api/sts/runs/{runId}/reset
```

`POST /api/sts/runs` returns the authenticated player's active run when one exists. Otherwise, it builds the starting deck server-side from `catalogs/cards.json`. The start deck is deterministic: cards with `startingCount > 0` are included when `favoredCharacter` is `Starting`, the selected character, or `Aucun`; each card is duplicated `startingCount` times. At most one active run is allowed per player.

See [docs/flows/game-playing.md](docs/flows/game-playing.md) for the frontend run lifecycle, combat completion, event choices, reward claiming, and stale-state handling.

#### Acte de départ, tables par acte et cartes secrètes

`EnemyPool/EnemyPool.json` porte `startingAct`, **numéroté comme le joueur le lit** : `"startingAct": 3` fait démarrer toute nouvelle run sur l'Acte 3. Les `minAct`/`maxAct` des entrées du même fichier restent, eux, des index internes commençant à zéro — c'est la seule asymétrie du fichier, et elle est voulue : ce réglage-ci se pose en pensant à un numéro d'acte affiché. Une valeur absente vaut `1`, et une valeur au-delà de `maxAct` est ramenée au dernier acte. Le score de fin de run et les jetons comptent alors les actes **franchis** et non l'index atteint : une run qui démarre à l'Acte 3 et s'y arrête n'a rien traversé.

```json
{
  "maxAct": 3,
  "startingAct": 1,
  "enemies": [ { "minAct": 0, "maxAct": 0, "...": "..." } ]
}
```

`gameplay/reward_tables.json` accepte une déclinaison par acte sous `byAct` (ou `acts`, ou `perAct`), à n'importe quel niveau : la section, la configuration d'un type de nœud, ou le conteneur `rarityWeights` lui-même. Les numéros s'écrivent comme dans `startingAct` (`"1"` est l'Acte 1 ; `"act2"` et `"Acte 2"` se lisent aussi). La table retenue est **la dernière écrite qui ne dépasse pas l'acte courant** : à l'Acte 3, une table qui ne connaît que les actes 1 et 2 répond avec celle de l'acte 2. Le repli ne remonte jamais — un acte antérieur à toute déclinaison lit la table non déclinée, faute de quoi le premier acte hériterait des raretés de fin de partie.

```json
"cardChoices": {
  "choicesPerReward": 3,
  "rarityWeights": { "normal": { "Common": 100, "Legendary": 5 } },
  "byAct": {
    "2": { "rarityWeights": { "normal": { "Common": 60, "Legendary": 15 } } },
    "3": { "rarityWeights": { "normal": { "Common": 20, "Legendary": 40 } } }
  }
}
```

`gameplay/run_score_rewards.json` accepte `tokens.gameOverMultiplier` : la part du gain de fin de run qu'une **défaite** rapporte quand même, `0` par défaut — sans réglage, perdre ne rapporte rien, comme avant. Le multiplicateur s'applique au nombre de jetons et non au score, pour que le plancher `minimumReward` reste ce que vaut une run menée à son terme : une run perdue n'a pas à l'atteindre. Le produit est tronqué, donc une part qui ne vaut pas un jeton n'en vaut aucun. Le retrait volontaire, lui, paie toujours plein tarif.

```json
"tokens": { "scorePerToken": 20, "minimumReward": 10, "rounding": "NEAREST", "gameOverMultiplier": 0.25 }
```

Deux marqueurs de carte gouvernent le pool secret, écrits dans `tags` comme les autres. Une carte marquée `Secret` ne peut tomber dans **aucune** table de récompense — combat, élite, boss, event, carte offerte par un event — tant que le compte n'a pas ouvert le pool. Jouer une carte marquée `SecretUnlock` l'ouvre, pour le compte et définitivement : la découverte survit à la mort, à l'abandon, et vaut pour toutes les runs suivantes, tous personnages confondus. L'ouverture est datée sur `users.sts_secret_cards_unlocked_at` et n'est jamais reprise. `SecretUnlock` n'est pas `Secret` : la carte qui ouvre la porte reste offrable en récompense, sans quoi rien ne pourrait l'ouvrir.

| Area | Endpoints |
| --- | --- |
| Session | `GET /api/session` |
| CSRF | `GET /api/csrf` |
| Users | `GET /api/users/me`, `GET /api/users/profile`, `PATCH /api/users/profile` |
| Departments | `GET /api/departments` |
| Challenges | `GET /api/challenges`, `GET /api/challenges/favorites`, `POST /api/challenges/{id}/favorite`, `DELETE /api/challenges/{id}/favorite`, `GET /api/challenges/submissions/results`, `GET /api/challenges/submissions/waiting-confirmation`, `GET /api/challenges/submissions/pending-validation`, `GET /api/challenges/submissions/{id}`, `DELETE /api/challenges/submissions/{id}`, `GET /api/challenges/submissions/{id}/messages`, `POST /api/challenges/submissions/{id}/messages`, `POST /api/challenges/{id}/submit`, `POST /api/challenges/submissions/{id}/participants/accept`, `POST /api/challenges/submissions/{id}/participants/decline`, `POST /api/challenges/submissions/{id}/participants/resend` |
| Friends | `GET /api/friends/search`, `GET /api/friends/contacts/search`, `GET /api/friends`, `GET /api/friends/{userId}/profile`, `GET /api/friends/requests/incoming`, `GET /api/friends/requests/outgoing`, `POST /api/friends/requests/{userId}`, `POST /api/friends/requests/{requestId}/accept`, `DELETE /api/friends/requests/{requestId}/decline`, `DELETE /api/friends/requests/{requestId}`, `DELETE /api/friends/{userId}` |
| Notifications | `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all` |
| Articles | `GET /api/articles`, `GET /api/articles/{articleId}` |
| Admin articles | `GET /api/admin/articles`, `POST /api/admin/articles`, `GET /api/admin/articles/{articleId}`, `POST /api/admin/articles/{articleId}/media`, `GET /api/admin/articles/{articleId}/media`, `PUT /api/admin/articles/{articleId}`, `DELETE /api/admin/articles/{articleId}` |
| Article categories | `GET /api/categories`, `/api/admin/categories` |
| Events | `GET /api/events`, `GET /api/events/registrations`, `GET /api/events/waitlist`, `POST /api/events/{eventId}/registrations`, `DELETE /api/events/{eventId}/registrations` |
| Admin events | `GET /api/admin/events`, `POST /api/admin/events`, `GET /api/admin/events/{eventId}`, `PUT /api/admin/events/{eventId}`, `POST /api/admin/events/{eventId}/cover`, `DELETE /api/admin/events/{eventId}`, `GET /api/admin/events/{eventId}/registrants`, `GET /api/admin/events/{eventId}/waitlist` |
| Admin event categories | `/api/admin/event-categories` |
| Treasure hunt | `GET /api/treasure-hunt/visibility`, `GET /api/treasure-hunt/progress`, `POST /api/treasure-hunt/checkpoints/validate`, `GET /api/treasure-hunt/vault/codes`, `POST /api/treasure-hunt/vault/unlock` |
| Admin treasure hunt | `PUT /api/admin/treasure-hunt/visibility`, `GET /api/admin/treasure-hunt/paths`, `POST /api/admin/treasure-hunt/paths/initialize`, `PUT /api/admin/treasure-hunt/paths/{position}`, `GET /api/admin/treasure-hunt/paths/{pathId}/checkpoints`, `POST /api/admin/treasure-hunt/paths/{pathId}/checkpoints`, `PUT /api/admin/treasure-hunt/checkpoints/{checkpointId}`, `POST /api/admin/treasure-hunt/checkpoints/{checkpointId}/media`, `DELETE /api/admin/treasure-hunt/checkpoints/{checkpointId}`, `POST /api/admin/treasure-hunt/vault` |
| Cards | `GET /api/cards/deck?collectionType=VIRTUAL\|PHYSICAL`, `POST /api/cards/instances/claim` |
| STS card images | `GET /api/sts/cardsImage` |
| Decks | `GET /api/decks`, `GET /api/decks/{deckId}`, `POST /api/decks/{deckId}/pull` |
| Proof uploads | `/api/uploads/proofs` |
| Moderation | `/api/moderation/submissions`, `GET /api/moderation/submissions/{id}`, `GET /api/moderation/submissions/{id}/messages`, `POST /api/moderation/submissions/{id}/messages`, `PATCH /api/moderation/submissions/{id}/messages/enabled` |
| Admin challenges | `/api/admin/challenges` |
| Admin challenge categories | `/api/admin/challenge-categories` |
| Admin card categories | `GET /api/admin/card-categories`, `POST /api/admin/card-categories`, `PUT /api/admin/card-categories/{id}`, `DELETE /api/admin/card-categories/{id}` |
| Admin cards | `GET /api/admin/cards`, `GET /api/admin/cards/export`, `POST /api/admin/cards/import`, `POST /api/admin/cards`, `PATCH /api/admin/cards/cover`, `DELETE /api/admin/cards/cover`, `PATCH /api/admin/cards/{id}`, `PATCH /api/admin/cards/{id}/image`, `PUT /api/admin/cards/categories/{categoryId}/order`, `DELETE /api/admin/cards/{id}`, `POST /api/admin/cards/{id}/collections?collectionType=VIRTUAL`, `GET /api/admin/cards/instances`, `POST /api/admin/cards/instances`, `GET /api/admin/cards/instances/{instanceId}`, `PATCH /api/admin/cards/instances/{instanceId}`, `PUT /api/admin/cards/instances/{instanceId}`, `GET /api/admin/cards/{id}/instances`, `POST /api/admin/cards/{id}/instances`, `POST /api/admin/cards/{id}/instances/batch`, `POST /api/admin/cards/instances/{instanceId}/assignment`, `DELETE /api/admin/cards/instances/{instanceId}/assignment`, `DELETE /api/admin/cards/instances/{instanceId}` |
| Admin card print | `GET /api/admin/card-print-templates`, `POST /api/admin/card-print-templates`, `DELETE /api/admin/card-print-templates`, `PUT /api/admin/card-print-templates/{id}/calibration`, `POST /api/admin/card-print-templates/{id}/preview`, `DELETE /api/admin/card-print-templates/{id}`, `POST /api/admin/card-print-runs`, `GET /api/admin/batches`, `GET /api/admin/batches/{id}`, `GET /api/admin/batches/{id}/pdf`, `DELETE /api/admin/batches/{id}`, `GET /api/admin/batches/{id}/cards`, `GET /api/admin/batches/pages/{printPageId}` |
| Admin decks | `POST /api/admin/decks`, `PATCH /api/admin/decks/{deckId}`, `DELETE /api/admin/decks/{deckId}`, `PUT /api/admin/decks/{deckId}/drop-tiers/order`, `GET|POST /api/admin/decks/tier-templates`, `PUT|DELETE /api/admin/decks/tier-templates/{templateId}` |
| Admin users | `GET /api/admin/users`, `GET /api/admin/users/{userId}`, `GET /api/admin/users/{userId}/available-challenges`, `POST /api/admin/users/{userId}/challenge-submissions`, `POST /api/admin/users/{userId}/cards`, `DELETE /api/admin/users/{userId}/cards`, `PATCH /api/admin/users/{userId}/leaderboard-visibility` |
| Admin department verification | `/api/admin/department-verification-links` |
| Leaderboards | `/api/leaderboards/users`, `/api/leaderboards/departments` |
| Achievements | `GET /api/achievements`, `GET /api/achievements/unseen`, `POST /api/achievements/unseen/acknowledge`, `GET /api/achievements/players/{userId}/completion` |
| Admin achievements | `GET /api/admin/achievements`, `GET /api/admin/achievements/types`, `POST /api/admin/achievements`, `PUT /api/admin/achievements/{id}`, `DELETE /api/admin/achievements/{id}` |
| Admin tournaments | `GET /api/admin/tournaments`, `GET /api/admin/tournaments/{eventId}`, `PUT /api/admin/tournaments/{eventId}/attendance`, `POST /api/admin/tournaments/{eventId}/settle`, `DELETE /api/admin/tournaments/{eventId}/settlement` |
| Admin leaderboards | `/api/admin/leaderboards` |

### Coefficients des classements départementaux

Les classements de départements conservent par défaut le score brut (`RAW_TOTAL`) : la migration ne modifie donc aucun score publié. Un administrateur peut, métrique par métrique, choisir une formule de compensation après avoir renseigné les effectifs théoriques dans `GET` / `PUT /api/admin/leaderboards/departments/settings`.

- `RAW_TOTAL` : total des points, sans correction ;
- `PER_REGISTERED_NORMALIZED` : moyenne par inscrit × plus grand effectif théorique ;
- `BALANCED_SQRT` : la formule recommandée, avec une pénalité modérée par racine carrée du taux de remplissage ;
- `FULL_PARTICIPATION` : pénalité proportionnelle au taux de remplissage.

La comparaison live est réservée à l’administration : `GET /api/admin/leaderboards/departments/formula-preview?metric=...` retourne les quatre résultats, coefficients et rangs sans écrire de snapshot. Une formule autre que le score brut est refusée tant qu’un département participant n’a pas d’effectif théorique. Les snapshots historiques ne sont jamais réécrits ; formule et effectifs sont inclus dans leur empreinte de fraîcheur. Les endpoints publics ne retournent toujours que rang, nom et score final.
| Admin action register | `GET /api/admin/action-events`, `GET /api/admin/action-events/types` |

Article media uploads are admin-only. The backend detects the real file type and accepts JPEG, PNG, WebP, MP4, and WebM files up to 15 MB; the file extension must match the detected type.

Challenge `globalQuota` limits how many chronologically validated submissions receive points when it is greater than `0`.
Submissions validated after the quota still complete the challenge, but award `0` points. Use `0` or omit the field for no global quota.

Challenge categories are managed by admins. `DEFAULT` is the protected fallback category; challenges created without a category use it, and deleting another category reassigns its challenges to `DEFAULT`.

Published articles are readable by authenticated users with `GET /api/articles` and `GET /api/articles/{articleId}`. Drafts and writing articles remain available only through the admin article endpoints.

Events are managed by admins under `/api/admin/events`. Each event has a title, short description, optional location, optional cover image URL, source (`TOURNAMENT`, `GLOBAL`, or `DEPARTMENT`), start date, optional end date, optional publication date, optional linked article, categories, and target departments. Admins can either set an external HTTP(S) cover URL or upload a JPEG, PNG, or WebP cover to S3 with `POST /api/admin/events/{eventId}/cover`. A `null` or future `publishedAt` hides the event from players; an empty target department list makes it visible to every authenticated player.

Player registrations use `POST /api/events/{eventId}/registrations`. `registrationEnabled=false` makes an event informational only. `capacity` `null` or `0` means unlimited registrations; a positive capacity fills registered seats first, then creates `WAITLISTED` registrations when `waitlistEnabled=true`. Deleting a registered seat promotes the oldest waitlisted player when possible. Admins can export both registered and waitlisted users with `/api/admin/events/{eventId}/registrants`, or only the waitlist with `/api/admin/events/{eventId}/waitlist`.

Player event responses carry `registeredFriends`, the accepted friends of the requesting player who hold a registration on that event. Each entry exposes `userId`, `displayName`, `departmentId`, and its own `status` (`REGISTERED` or `WAITLISTED`), sorted by display name. The list is empty in admin responses, which are not scoped to a player.
See `docs/flows/events.md` for the admin and user event flows.

### Admin User Search

Admins can search synchronized users with:

```text
GET /api/admin/users
```

All query parameters are optional. Without parameters, the endpoint returns the paginated user list.

Supported filters:

- `query`: case-insensitive match on display name, email, or Keycloak subject.
- `departmentId`: exact department UUID.
- `gender`: `MALE`, `FEMALE`, or `NEUTRAL`.
- `hiddenFromLeaderboards`: `true` or `false`.
- Standard Spring pagination/sorting parameters: `page`, `size`, and `sort`. The default sort is `displayName,asc`.

The response is a Spring `Page` of admin user DTOs including user id, Keycloak subject, email, display name, gender, department id/name, points, leaderboard visibility, and timestamps.

Admins can open a player moderation detail with:

```text
GET /api/admin/users/{userId}
```

The response includes the admin user DTO, summary counters, challenge submissions where the user is submitter or participant, friendships, virtual and physical card collections, and assigned physical card instances.

Admins can load the challenges currently available to this player with `GET /api/admin/users/{userId}/available-challenges`. The response uses the same visibility and eligibility rules as `GET /api/challenges`: active period, department targeting, prerequisites, and current submission state.

Admins can correct the player profile directly:

- `POST /api/admin/users/{userId}/challenge-submissions` creates an immediately validated manual submission from `{ "challengeId": "...", "comment": "..." }`. It follows normal eligibility and duplicate-submission rules, then recalculates challenge points.
- `POST /api/admin/users/{userId}/cards` adds virtual cards with `{ "cardId": "...", "collectionType": "VIRTUAL", "quantity": 1 }`, or assigns existing physical instances with `{ "collectionType": "PHYSICAL", "cardInstanceIds": ["..."] }`.
- `DELETE /api/admin/users/{userId}/cards` removes virtual cards by quantity with the same virtual payload, or releases selected physical instances with the physical payload. A released physical instance remains in the instance inventory but no longer belongs to the player.

See [docs/flows/admin-users-frontend.md](docs/flows/admin-users-frontend.md) for the frontend flow and request payloads.

Challenge submissions have a discussion thread available to the submission members and to eligible moderators.
Players use `GET/POST /api/challenges/submissions/{id}/messages`; moderator identities are returned as `Équipe de modération` and proof files are not exposed through those endpoints.
Moderators use `GET/POST /api/moderation/submissions/{id}/messages`; author identities are visible, and proof access remains separate through the moderation proof URL endpoint.
Players can write only after the discussion has been opened by a moderator message or by `PATCH /api/moderation/submissions/{id}/messages/enabled`.
The first moderator message opens the player discussion automatically. Rejected submissions cannot receive new messages.

Lifecycle summary: submission created -> optional group confirmation -> pending moderation -> optional discussion with hidden proofs for players -> validated or rejected. Once rejected, the discussion becomes read-only.
See `docs/flows/README.md` for the detailed proof upload, moderation, and submission discussion lifecycle docs.

Friend requests send a `FRIEND_REQUEST_RECEIVED` notification to the target user. Accepting a request creates the friendship and sends a `FRIEND_REQUEST_ACCEPTED` notification back to the requester.
See `docs/flows/friends.md` for the client-side friendship flow.

Cards are stored in the public S3 bucket configured by `S3_PUBLIC_BUCKET`.
Admins create card categories separately, then create/update cards with `categoryId` or an existing category name.
Card categories have `hidden` and `game` flags, both editable through the admin category endpoints. Hidden categories are excluded from every non-admin card catalogue. Game categories are also excluded from the normal player deck, but are included alongside public categories by `GET /api/sts/cardsImage`; this endpoint returns the same card objects as `GET /api/admin/cards` and excludes hidden categories.
This is API-level visibility; already-known public S3 asset URLs are not revoked by hiding a category.
Admins create and update cards with `multipart/form-data`; `image` is required on creation and may be replaced on update.
Card image uploads accept JPEG/PNG files up to 20 MB; the multipart request limit defaults to 25 MB to allow form overhead.
The hidden locked-card cover is updated with `PATCH /api/admin/cards/cover`, which accepts only an `image` multipart field, and deleted with `DELETE /api/admin/cards/cover`.
When no cover is configured, locked cards keep their hidden metadata and return `null` image URLs instead of exposing their real artwork.
The API stores a compressed JPEG display image plus a generated JPEG thumbnail under the `cards/` object prefix.
`GET /api/cards/deck` returns enabled collectible cards grouped by category, with owned cards ordered before locked cards. Les catégories sortent dans l'ordre réglé en admin (`displayOrder`), pas dans l'ordre alphabétique : celui-ci plaçait « Légendaire » avant « Peu commune ». Une catégorie sans rang passe après celles qui en ont un. Le front joueur se sert aussi de cette position comme indice de rareté, d'où l'importance de l'ordre reçu.
Use `collectionType=VIRTUAL` or `collectionType=PHYSICAL` to select which user collection to read; `VIRTUAL` is the default.
Cards in hidden categories do not count toward public owned/total deck counters.
Locked cards return the default card image as their back cover and `???` for string fields such as `name` and `category`.
The single `defaultCard` has the fixed id `00000000-0000-0000-0000-000000000000`, is used only as the back cover asset, always stays enabled, and cannot be granted, deleted, disabled, or changed into a normal card.
Admins reorder cards inside a category with `PUT /api/admin/cards/categories/{categoryId}/order` by submitting the final ordered list of card ids.
Admins grant virtual cards into a collection with `POST /api/admin/cards/{id}/collections?collectionType=VIRTUAL`; `VIRTUAL` is the default when the parameter is omitted.
Physical cards must be assigned through a unique `CardInstance`, either with `POST /api/admin/cards/instances/{instanceId}/assignment` or by player claim with `POST /api/cards/instances/claim`.
Admins can search physical instances with `GET /api/admin/cards/instances` using `publicId`, `ownerUserId`, `cardId`, `type`, and `number`, then update the card definition, public code, or owner with `PATCH /api/admin/cards/instances/{instanceId}`.
See `docs/flows/cards.md` for the admin and user card management flow.

### STS multijoueur : formats, historique des cartes, decks

Un combat multijoueur n'oppose plus forcément un joueur à un autre. `POST /api/sts/pvp/matchmaking/quick`
accepte un `mode` — `ONE_V_ONE` (défaut), `TWO_V_TWO`, `RAID` — et n'apparie qu'à l'intérieur d'un mode :
un joueur qui cherche un raid ne peut pas être tiré dans un duel. La réponse dit dans quelle file il
attend (`mode`, `queuedPlayers`, `neededPlayers`), et `GET /api/sts/pvp/battles/{battleId}` renvoie le
`mode` de la bataille.

`fillWithAi: true` ouvre le combat sans attendre la salle complète : les sièges restés vides sont tenus
par des renforts que le serveur joue, avec le deck d'un joueur de leur équipe. C'est explicite et non
automatique — se retrouver entouré de robots sans l'avoir demandé serait une surprise — et c'est ce qui
rend un 2v2 essayable sans réunir quatre joueurs connectés en même temps.

Un **raid** oppose deux joueurs à une rencontre que le serveur pilote, tirée au hasard pour chaque raid
dans le fichier facultatif `gameplay/pvp_raid_encounters.json` (à créer dans l'éditeur de configuration
de jeu) — une liste de rencontres, chacune une liste d'identifiants d'ennemis :

```json
{ "encounters": [
    ["Behemoth"],
    { "enemies": ["Aegis_Drone", "Astrum", "Aegis_Drone"], "hpMultiplier": 0.6 }
] }
```

Le tableau nu `[ [...], [...] ]` se lit aussi. Les ennemis sont ceux des données de jeu, nommés par `id`
ou `enemyName`, et un même ennemi peut revenir plusieurs fois dans une rencontre. Une rencontre qui nomme
un ennemi inexistant n'est **jamais tirée** (le serveur le signale dans ses journaux) plutôt que de faire
échouer le raid une fois les joueurs sortis de la file. Sans fichier, ou sans rencontre jouable, le raid
retombe sur le boss unique de `sts.pvp.raid.boss-enemy-id` (`STS_PVP_RAID_BOSS_ENEMY_ID`). Chaque ennemi
voit ses points de vie mis à l'échelle du nombre de joueurs en face, séparément, puis multipliés par le
`hpMultiplier` de sa rencontre (écrite en objet) : c'est ce qui permet d'alléger une rencontre à plusieurs
ennemis, sinon bien plus coriace qu'un boss seul. Une rencontre écrite en liste nue garde l'échelle 1, et
un multiplicateur nul, négatif ou non numérique vaut 1 plutôt que de donner un boss à 1 PV. **Un raid ne déplace aucun classement** : l'elo mesure des
joueurs les uns contre les autres, et il n'y a personne en face à qui prendre des points. La victoire
compte pour les succès, pas au classement.

En équipe, abandonner ne termine plus le combat : le partant en sort, son équipier continue. Le combat
ne s'arrête que lorsque la dernière personne d'une équipe est hors jeu, et le classement de tous bouge
alors du même montant qu'en duel.

Chaque bataille tient un **historique partagé des cartes jouées**, servi dans `playedHistory` sur la
projection de combat (socket et `GET /api/sts/pvp/battles/{battleId}/snapshot`). Il est tenu par le
serveur plutôt qu'accumulé par chaque client, et ce n'est pas une commodité : un snapshot ne rejoue
aucun événement, donc une liste accumulée côté client repartirait vide à la moindre reconnexion et les
deux joueurs cesseraient de voir le même « historique partagé ». Rien de privé n'y entre — une carte
jouée est déjà publique — et la liste est bornée aux dernières cartes.

`POST /api/sts/pvp/decks` **remplace** le deck qui porte déjà le nom demandé pour le même personnage
(casse et espaces autour ignorés) au lieu d'en créer un second : le deck garde son identifiant, et les
doublons plus anciens laissés par les versions précédentes sont retirés. La règle vaut par personnage
seulement — enregistrer « Aggro » pour MECA ne touche pas au « Aggro » d'EP — et remplacer le contenu du
deck actif ne le désactive pas.

`DELETE /api/sts/pvp/decks/{deckId}` efface un deck sauvegardé du joueur courant (204). Le deck d'un
autre joueur est *introuvable* plutôt que refusé, pour que la route ne serve pas à deviner quels
identifiants existent. Le deck actif s'efface comme les autres : le combat sait déjà se passer d'un deck
marqué actif, et refuser empêcherait à jamais de nettoyer la liste.

### STS multijoueur : classement et récompenses

Le classement suit la formule d'Elo (départ 1000, K = 32), ce qui veut dire une chose et c'est la
seule qui compte : **ce qu'un résultat vaut dépend de qui était en face**. Battre plus fort que soi
rapporte beaucoup, battre plus faible presque rien. Le barème plat de ±25 qui précédait payait autant
l'exploit que la victoire acquise d'avance, et se cultivait en ne défiant que des débutants. Le calcul
vit dans `StsPvpRating`, seul endroit à changer pour le régler.

Les jetons suivent la même mesure plutôt qu'un second barème inventé à côté : une victoire paie
`WIN_TOKENS_BASE` plus `WIN_TOKENS_UPSET_BONUS` au prorata de l'exploit — tout le bonus contre un
adversaire réputé imbattable, la moitié entre égaux, rien contre un adversaire qu'on ne pouvait pas
perdre contre. Une défaite coûte `LOSS_TOKENS`, fixe et petit devant ce qu'une victoire rapporte : le
portefeuille ne descend jamais sous zéro, et le montant *appliqué* est celui qui est annoncé.

Deux garde-fous méritent d'être connus. Un résultat déplace **toujours** le classement d'au moins un
point : sans ce plancher, un écart énorme s'arrondit à zéro et l'outsider qui perd ne perd rien, ce qui
rend gratuit le fait de défier hors de portée. Et le classement ne descend **jamais** sous zéro, un
classement négatif ne voulant rien dire pour des appariements qui raisonnent sur des écarts.

En équipe, chacun est évalué contre la **moyenne** des classements d'en face, lue avant que quoi que ce
soit ne bouge — sinon le premier joueur réglé changerait la moyenne servant à évaluer le suivant, et
l'ordre de la liste déciderait des gains.

**Ce qui ne rapporte rien :** un duel amical (il ne bouge que les compteurs amicaux ; deux joueurs
peuvent en arranger autant qu'ils veulent) et un raid (il se joue contre un boss que le serveur pilote,
et il n'y a personne en face à qui prendre des points).

Ce qu'un duel a valu à chacun est gardé sur la bataille et servi dans `rewards` par
`GET /api/sts/pvp/battles/{battleId}` — `eloBefore`, `eloAfter`, `eloDelta`, `tokenDelta`, par
identifiant de joueur. Gardé plutôt que recalculé : la formule part des classements d'avant le duel, et
ceux-là ont déjà bougé. Sans cette trace, un joueur constate seulement que son classement a changé entre
deux passages au menu.

Le règlement est le même quel que soit le chemin — le combat que le moteur termine, l'abandon, et le
forfait que la veille des absents prononce passent tous par `StsPvpCombatService.settleRewards`.

L'appariement rapide se fait **à l'intérieur d'un mode**, puis par proximité de classement : la file
est triée sur l'écart d'Elo et les adversaires les plus proches sont retenus. Il n'y a pas d'écart
maximum, volontairement — sur une base de joueurs réduite, un plafond empêcherait surtout de jouer.

### STS multijoueur : classement public et statistiques d'équilibrage

**Classement Elo public.** `PVP_ELO` rejoint les métriques de classement (migration V177). Ce n'est pas
un compte de points, et cela a trois conséquences, portées par `LeaderboardMetric` et non par des cas
particuliers dispersés :

* il n'existe qu'**entre joueurs** (`supports(DEPARTMENT)` est faux) — la somme des classements d'une
  promo mesurerait surtout son effectif ;
* il **n'entre pas dans le classement total** (`countsTowardTotal()` est faux). Le formulaire des poids
  ne le propose pas, et `PUT /api/admin/leaderboards/users/total/weights` refuse qu'on lui donne un
  poids non nul ; un poids à 0 est toléré, pour qu'un ancien panneau qui l'envoie ne soit pas bloqué ;
* n'y figurent **que les joueurs qui ont disputé un duel classé** (`ranksOnlyScoredPlayers()`). Ouvrir
  le menu multijoueur crée déjà un profil à 1000 : un curieux passerait sinon devant un vrai joueur
  descendu à 950. L'appartenance se décide sur la présence d'un classement, pas sur sa valeur.

Les routes qui rendent plusieurs classements d'un coup filtrent les métriques par échelle, **y compris
une liste explicite**. Ce n'est pas du confort : le site demande tous les classements de département en
un appel et avale l'erreur, si bien qu'un seul classement inexistant à cette échelle vidait tous les
autres sans rien afficher.

**Statistiques d'équilibrage** (réservées à `ADMIN`, par la règle par défaut de `/api/admin/**`) :

* `GET /api/admin/sts/pvp/characters/usage?days=&mode=&includeFriendly=` — pour chaque personnage : les
  sélections, leur part, le bilan et le taux de victoire, sur les batailles terminées. **Rien n'est
  compté à part** : chaque bataille garde déjà le personnage de chacun de ses joueurs et son vainqueur,
  et les batailles ne sont jamais purgées. Un compteur tenu à côté serait une seconde vérité qui
  dériverait ; ces chiffres pourront devenir publics en se lisant au même endroit. Les sièges que le
  serveur joue (renforts, boss) ne comptent pas, les nuls sortent du taux de victoire, et les **raids
  sont exclus par défaut** — on y gagne contre le serveur, pas contre un autre personnage.
* `GET /api/admin/sts/pvp/players?limit=` — les joueurs classés, du meilleur au moins bon, avec leur place
  au classement Elo public (`leaderboardRank`, nul pour un compte qui n'y figure pas, comme le staff).
  L'ordre et les égalités sont ceux du classement public, et l'éligibilité est lue par
  `LeaderboardService.isRankable` plutôt que recopiée.
* `GET /api/admin/sts/pvp/players/{userId}` — le classement, le bilan, le personnage et sa relique, et
  **tous les decks**, avec `usedInBattle` : le deck qu'un duel utiliserait vraiment aujourd'hui (l'actif
  du personnage choisi, à défaut le plus récent), qui n'est pas forcément celui marqué actif.

### STS multijoueur : défis entre amis

Un défi est un duel 1v1 proposé à un ami accepté, qui vit dix minutes.

* `POST /api/sts/pvp/challenges` `{ targetUserId, friendly }` — lance le défi. Le défiant doit avoir un
  deck prêt, pour ne pas faire accepter un duel qui ne pourrait pas s'ouvrir. Un second défi au même ami
  tant que le premier attend **rend le premier** au lieu d'en créer un autre : un double clic ne
  notifie pas deux fois.
* `GET /api/sts/pvp/challenges` — les défis en attente, reçus (`direction: INCOMING`) puis lancés
  (`OUTGOING`), les plus récents d'abord. Un défi dont l'échéance est passée est marqué `EXPIRED` à la
  lecture et n'apparaît plus.
* `GET /api/sts/pvp/challenges/{challengeId}` — un défi dont on est l'un des deux joueurs (introuvable
  sinon). C'est ainsi que le défiant apprend la réponse : une fois `ACCEPTED`, `battleId` nomme la
  bataille à rejoindre.
* `POST /api/sts/pvp/challenges/{challengeId}/accept` — réservé au défié. Vérifie le deck **des deux**
  joueurs, retire les deux de la file de recherche rapide (sous le même verrou que l'appariement) et
  ouvre la bataille, rendue dans `battleId`. Le défiant reçoit une notification `CHALLENGE_ACCEPTED`.
* `POST /api/sts/pvp/challenges/{challengeId}/decline` — réservé au défié ; notifie `CHALLENGE_DECLINED`.
* `POST /api/sts/pvp/challenges/{challengeId}/cancel` — réservé au défiant.

Répondre à un défi qui n'attend plus (déjà répondu, annulé, expiré) renvoie `409`. Un défi `friendly`
suit les règles d'un duel amical : il ne bouge ni le classement ni les jetons.

Le site peut défier depuis la liste d'amis sans passer par le jeu : il envoie le défi, puis ouvre le jeu
avec une « intention de lancement » que le jeu vient lire une fois prêt (`sts.launch.intent` sur le
pont). Rien de nouveau côté API : ce sont les mêmes routes.

### Card Archive Export And Import

Admins can export the full card system with `GET /api/admin/cards/export`.
The downloaded ZIP contains a JSON manifest plus every referenced public S3 object for card images, thumbnails, print templates, and generated print-run PDFs.

Admins can restore the archive on another server with `POST /api/admin/cards/import` using multipart field `archive`.
Import is destructive for the card system: it deletes card collections, physical instances, cards, categories, decks, print templates, and print batches before restoring the archive.
UUIDs, public claim codes, batch links, and object keys are preserved, but physical instances are restored without owners because users are not exported.
Card and template public URLs are recalculated from the target server `S3_PUBLIC_ENDPOINT`/`S3_ENDPOINT` and `S3_PUBLIC_BUCKET`.

Large archives go through Spring multipart handling; increase `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` and `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` when the archive is larger than the local defaults.

## Physical Card Printing

Admins can create duplex PDF print templates for physical card batches.

Flow:

1. Upload a two-page PDF patron with `POST /api/admin/card-print-templates`. Page 1 is the front and page 2 is the back.
2. Calibrate the 9 front-page card slots and code rendering settings with `PUT /api/admin/card-print-templates/{id}/calibration`.
3. Generate a preview PDF with `POST /api/admin/card-print-templates/{id}/preview`. This does not create card instances and stamps test page ids (`TEST-1`, `TEST-2`).
4. Generate a real print run with `POST /api/admin/card-print-runs`. This creates physical `CardInstance` rows, renders their public claim codes on the selected cards, stores the generated PDF as a private S3 object, and returns the authenticated backend PDF URL.

Template coordinates are stored in PDF points (`1 pt = 1/72 inch`) with a top-left origin on page 1, matching the values commonly read from design tools such as Inkscape. The backend converts them to PDFBox's bottom-left coordinate system during rendering.

Each template stores:

- 9 front-page card slots: `xPt`, `yPt`, `widthPt`, `heightPt`.
- Card image fit mode: `COVER` or `CONTAIN`, plus optional bleed in points to absorb small print gaps.
- Code text box relative to each card: position, size, font family, font weight, font size, text color, optional background color and opacity, and alignment.

Print runs are limited to 100 sheets per request and generate two PDF pages per sheet. Each generated PDF page is stamped in the lower-left corner with `<batch UUID>-<page number>`, for example `2d5f...-1`.
Generated print-run PDFs contain claim codes and are private S3 objects. Admins fetch them through `GET /api/admin/batches/{id}/pdf`; do not expose direct S3 URLs for print-run PDFs.
Admins can regenerate old print-run PDFs with `POST /api/admin/batches/regenerate-pdfs`. The backend skips PDFs already generated by the compressed renderer and rewrites only older batches from the stored template, card slots, and public claim codes.
When public bucket initialization is enabled, startup scopes anonymous S3 reads to public asset prefixes and excludes `card-print/runs/`. New print-run PDFs are uploaded with private object ACLs. If old run PDFs were uploaded publicly, run once with `S3_PUBLIC_BUCKET_RUN_ACL_HARDENING_ENABLED=true` to mark existing `card-print/runs/` object ACLs private, or update those ACLs manually if bucket policy is managed outside the API.
Generated physical card instances remain unassigned until a player claims them through `POST /api/cards/instances/claim`.
Admins can list generated batches with `GET /api/admin/batches`, inspect a batch with `GET /api/admin/batches/{id}`, list its generated cards with `GET /api/admin/batches/{id}/cards`, and resolve a stamped page id with `GET /api/admin/batches/pages/{printPageId}`.
Unused print templates can be deleted with `DELETE /api/admin/card-print-templates/{id}`, or in batch with `DELETE /api/admin/card-print-templates`. Templates already used by a print run are kept for traceability and deletion is rejected.
See `docs/flows/card-printing.md` for the template calibration and print run flow.

Purchasable decks are created with `POST /api/admin/decks` and updated with `PATCH /api/admin/decks/{deckId}`, using `multipart/form-data`. The image is required on creation and optional on update.

L'animation d'ouverture, elle, se choisit **par catégorie de carte** (`revealAnimation` sur `/api/admin/card-categories`). Cinq valeurs règlent une intensité — `AUTO`, `CALM`, `SPARKLE`, `RADIANT`, `SPECTACULAR` — et six règlent un caractère : `HOLOGRAM`, `EMBER`, `FROST`, `COSMIC`, `GOLD`, `GLITCH`. Un caractère impose sa propre mise en scène et son propre plancher d'intensité, parce que deux catégories également rares peuvent mériter deux révélations qui ne se ressemblent pas. Une catégorie porte une identité que la statistique ignore — une légendaire mérite son grand jeu même dans un pack qui en donne souvent — et doit se révéler pareil quel que soit le pack d'où elle sort. `AUTO`, la valeur par défaut, laisse la rareté mesurée du palier décider. Le champ est facultatif à l'envoi : absent, il laisse le choix en place.

Chaque palier porte aussi sa rareté mesurée — `pullProbabilityPercent`, `expectedPullsForOne` et `rarity` — parce qu'un pourcentage ne se compare pas d'un pack à l'autre : 10 % dans un pack qui tire dix cartes se voit presque à chaque ouverture, 10 % dans un pack qui n'en tire qu'une se font attendre dix packs. C'est cette mesure, et non le pourcentage, qui pilote l'animation de révélation.

Un agencement de paliers peut être mis de côté sous un nom et reversé dans un autre pack : `GET|POST /api/admin/decks/tier-templates`, `PUT|DELETE /api/admin/decks/tier-templates/{templateId}`. Charger un modèle ne remplit que le formulaire — rien n'est écrit tant que le pack n'est pas enregistré. Un modèle répond à la même arithmétique qu'un pack, sinon on ne découvrirait son invalidité qu'une fois le formulaire rempli.
The deck JSON supports `cardsPerPull` to choose how many cards one purchase returns, and each drop tier supports `minCardsPerPull` and `maxCardsPerPull` to guarantee or cap how many cards from that tier can appear in one pull.
Player deck detail and pull routes ignore cards from hidden categories; admin deck responses keep them visible for management.
See `docs/flows/decks.md` for the deck setup, quota, and weighted pull behavior.

Game assets are stored in the public S3 bucket configured by `S3_PUBLIC_BUCKET`, under the versioned object prefix `game/{version}/`.
The public frontend reads the active build with `GET /api/public/game`. The response contains:

```json
{
  "version": "2026.1.0",
  "baseUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/",
  "indexUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/index.html",
  "loaderUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/Build/game.loader.js",
  "fileCount": 308,
  "totalSizeBytes": 42000000,
  "publishedAt": "2026-07-01T10:00:00Z"
}
```

Admin frontends publish a new game build with `POST /api/admin/game/assets` as `multipart/form-data`.
Send `version` as a string made of letters, digits, `.`, `_`, or `-`, and send every file in the selected directory as repeated `files` parts.
For a browser directory picker, use an input such as `<input type="file" webkitdirectory multiple>` and append each selected file to `files`; the API accepts paths rooted at `public/game/`, `game/`, or directly at the build root.
Deployment must set `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` and `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` high enough for the largest Unity file and the full build folder.
Deployment must also set `SERVER_TOMCAT_MAX_PART_COUNT` above the number of selected files plus form fields; for example 308 files require at least 309 parts.

Game JSON configs are stored in the public S3 bucket configured by `S3_PUBLIC_BUCKET`.
The editable development configs live under `APP_GAME_CONFIG_OBJECT_PREFIX` (`GameData/current/dev/` by default), while released snapshots live under `APP_GAME_CONFIG_RELEASES_OBJECT_PREFIX` (`GameData/versions/` by default).
Admins can manage JSON files and S3 folder markers in the editable current prefix:

- `GET /api/admin/game/configs` lists valid `*.json` file paths, including nested paths such as `levels/world_1/settings.json`.
- `GET /api/admin/game/configs/folders` lists folders under the current prefix.
- `POST /api/admin/game/configs/folders?path=levels/world_1` creates an S3 folder marker under the current prefix.
- `DELETE /api/admin/game/configs/folders?path=levels/world_1` deletes that folder marker and every nested current object.
- `GET /api/admin/game/configs/{filePath}` reads one file as text.
- `PUT /api/admin/game/configs/{filePath}` validates the text body as JSON, then creates or overwrites the current S3 object.
- `DELETE /api/admin/game/configs/{filePath}` deletes one current JSON object.
- `POST /api/admin/game/configs/releases?version=2026.1.0` copies current JSON configs, including nested files, to `GameData/versions/2026.1.0/` and updates `GameData/versions/latest.json`.
- `POST /api/admin/game/configs/releases/upload?version=2026.1.0` uploads repeated multipart `files` parts as a JSON config tree directly to a versioned release and updates `latest.json`.
- `GET /api/admin/game/configs/releases` lists existing versioned config releases and their files.
- `GET /api/admin/game/configs/releases/latest` returns the latest release metadata.
- `GET /api/admin/game/configs/releases/{version}/files/{filePath}` reads one JSON file from a versioned release as text.
- `DELETE /api/admin/game/configs/releases/{version}` deletes one versioned release; if it was latest, the newest remaining release becomes latest, or `latest.json` is deleted when no release remains.

Every folder segment must match letters, digits, `_`, or `-`; file names must also end with `.json`.
See `docs/flows/game-configs.md` for the admin frontend flow.
The folder must contain `index.html`, `Build/game.data.br`, `Build/game.framework.js.br`, `Build/game.loader.js`, and `Build/game.wasm.br`.
The API uploads the new folder first, switches the active release, then deletes the previous `game/{version}/` prefix. Reusing the active version is rejected so user URLs remain cache-busting.
Game files are uploaded with immutable one-year cache headers; Unity Brotli files (`*.br`) are served with `Content-Encoding: br`.

Proof uploads use a direct-to-S3 POST policy flow:

1. `POST /api/uploads/proofs/direct` with `challengeId`, `originalFileName`, `contentType`, and `sizeBytes` reserves the file size against the draft limit and returns `uploadId`, a presigned S3 POST URL, and signed form fields. The S3 client is configured with path-style access for SeaweedFS compatibility. Use `S3_PUBLIC_ENDPOINT` for browser-facing upload and read URLs, for example `https://s3.example.com`, while `S3_ENDPOINT` can stay on the internal Docker hostname.
2. `POST /api/uploads/proofs/direct/batch` accepts the same challenge field plus a `files` array, and returns one signed POST policy per file.
3. Upload each file directly to S3 with `multipart/form-data`: add every returned field first, then append the file as the final `file` part.
4. `POST /api/uploads/proofs/{uploadId}/complete` confirms each upload with an empty body. The API reads S3 metadata and the first object bytes to validate the declared size and real media type before exposing the file as a draft proof.
5. Submit media proofs with `POST /api/challenges/{id}/submit`, `proofType: "MEDIA"`, and `proofUploadIds`. Submit text proofs with `proofType: "TEXT"` and `comment` as the proof text.

At startup, bucket initialization also applies CORS for the configured frontend origins, allowing browser `POST` uploads and `GET`/`HEAD` reads with `ETag` exposed. Disable this with `S3_BUCKET_CORS_ENABLED=false` if bucket CORS is managed outside the API.

`PATCH /api/users/profile` updates the authenticated user's profile fields. The current request body supports:

```json
{
  "gender": "NEUTRAL"
}
```

## Moderation Review Locks

Moderators must claim a pending submission before opening its proof files or deciding it:

- `GET /api/moderation/submissions/{id}` returns the detail needed by a moderation screen: challenge summary, proof metadata, participants, review state, chat state, and messages.
- `POST /api/moderation/submissions/{id}/review-lock` claims the review for the current moderator.
- `DELETE /api/moderation/submissions/{id}/review-lock` releases the review lock.
- `GET /api/moderation/submissions/{id}/proof-url` and `POST /api/moderation/submissions/{id}/decide` require the current moderator to own the active review lock.

Review locks expire after 15 minutes, so abandoned reviews can be claimed by another eligible moderator. The API returns `409 Conflict` when a submission is already locked by someone else, when the caller does not own the lock, or when the submission has already been decided.

Allowed gender values are `MALE`, `FEMALE`, and `NEUTRAL`; input is case-insensitive.

Admin challenge create/update payloads support `sensitive`, a boolean flag indicating whether the challenge contains sensitive content. It defaults to `false` when omitted.

Challenge submissions accept `participantUserIds` to attach additional users to the submission group. The submitter is always included implicitly. When participants are present, the submission starts as `WAITING_CONFIRMATION` and each invited participant receives a `CHALLENGE_GROUP_INVITE_RECEIVED` notification with the submission id. Participants confirm with `POST /api/challenges/submissions/{id}/participants/accept` or refuse with `POST /api/challenges/submissions/{id}/participants/decline`. Once every participant accepts, the submission moves to normal moderation (`PENDING`) or is auto-validated for `AUTO` challenges; a refusal rejects the submission before moderation. `GET /api/challenges/submissions/{id}` returns the player-visible detail with participant confirmation statuses (`ACCEPTED`, `PENDING`, `DECLINED`). The submitter can cancel a waiting group submission with `DELETE /api/challenges/submissions/{id}` or resend notifications to participants still `PENDING` with `POST /api/challenges/submissions/{id}/participants/resend`. A submission is refused when any group member already has a waiting, pending, or validated submission for the same challenge. Proof upload preparation validates only the authenticated uploader; group validation happens at final submission time.

Admin challenge create/update payloads also support `prerequisiteChallengeIds`. A challenge with prerequisites is hidden from a player until they have validated every prerequisite. Group submissions are refused unless every group member has validated every prerequisite.

Les refus de soumission de groupe nomment la personne et la cause : « Chloé n'a pas encore validé « Monter la tour ». », « Yanis a déjà validé ce défi. », « Chloé n'est pas dans un département autorisé (INFO, MECA). ». Le message est le même en haut de la réponse et dans `fieldErrors[participantUserIds]`, pour que le client puisse l'afficher tel quel — il l'était auparavant sous la forme « Prérequis de défi incomplets. », qui obligeait à ouvrir la console pour savoir qui bloquait.

`GET /api/challenges/{challengeId}/participants/eligibility?userIds=…` répond la même chose avant l'invitation : pour chaque joueur demandé, `eligible` et `reason`. Les règles sont celles de la soumission — département, prérequis, soumission déjà en cours — évaluées au moment du choix, pour ne pas former une équipe et prendre une photo avant d'apprendre qu'un membre ne pouvait pas participer.

`GET /api/challenges` includes `completed` and `favorite` flags for the authenticated player. Favorites can be managed with `POST /api/challenges/{id}/favorite` and `DELETE /api/challenges/{id}/favorite`; a player can only favorite a challenge currently visible to them. `GET /api/challenges/favorites` returns the player's visible favorite challenges, so deleted challenges, inactive challenges, and challenges no longer available to the player's department are not returned.

`GET /api/challenges/submissions/results` returns the authenticated user's submission history, including waiting and pending submissions, with the submission id, challenge id, submission status, submission date, moderation comment when present, and `participantOnly` when the user was added as a group member instead of submitting.

The two pending phases can also be loaded separately. `GET /api/challenges/submissions/waiting-confirmation` returns only `WAITING_CONFIRMATION` submissions where invited participants have not all accepted yet. `GET /api/challenges/submissions/pending-validation` returns only `PENDING` submissions whose participant confirmations are complete and which are waiting for validation. Both endpoints use the same response shape as submission results and include submissions where the authenticated user is the submitter or a participant.

Moderation pending submissions include the submission group members. Moderation decisions accept an optional `moderationComment`, typically used to explain a rejected proof. Validated decisions also accept optional non-negative `bonusPoints`, which are added to the challenge reward when the submission is inside the challenge quota.

`PUT /api/admin/department-verification-links` replaces the proof verification link table. A link is directional:
`verifyingDepartmentId` can verify proofs for `verifiedDepartmentId`. For bidirectional verification, send both links.

```json
{
  "links": [
    {
      "verifyingDepartmentId": "00000000-0000-0000-0000-000000000001",
      "verifiedDepartmentId": "00000000-0000-0000-0000-000000000002"
    },
    {
      "verifyingDepartmentId": "00000000-0000-0000-0000-000000000002",
      "verifiedDepartmentId": "00000000-0000-0000-0000-000000000001"
    }
  ]
}
```

Swagger UI is available at:

```text
/swagger-ui.html
```

You must be authenticated to access it. The selector separates the documentation into three route groups:

- `App` for player-facing endpoints (`/v3/api-docs/app`)
- `Admin` for administrator endpoints (`/v3/api-docs/admin`)
- `Moderation` for moderation endpoints (`/v3/api-docs/moderation`)

The complete OpenAPI JSON is published directly by Springdoc at those three authenticated endpoints. Download the group needed by a client or documentation tool; there is no single ungrouped document, so admin-only schemas and routes remain separated from player and moderation APIs.

Generate versioned JSON files locally with:

```bash
./gradlew generateOpenApiDocs
```

The task writes `app.json`, `admin.json`, and `moderation.json` to `docs/openapi/` using an isolated in-memory generation profile.

## Proof Uploads

Proof files are uploaded to S3-compatible storage and tracked in the database as drafts before submission.

Proof submission types:

- `MEDIA`: one or more uploaded files.
- `TEXT`: text proof stored in `comment`.

Supported file types:

- Images: JPEG, PNG, WebP
- Videos: MP4, WebM, QuickTime

Limits:

- Maximum total media proof size: 15 MB
- Draft retention: 24 hours by default
- Unfinished direct upload reservation timeout: 15 minutes by default

Draft cleanup is controlled by:

| Variable | Default |
| --- | --- |
| `APP_PROOFS_CLEANUP_ENABLED` | `true` |
| `APP_PROOFS_DRAFT_RETENTION_HOURS` | `24` |
| `APP_PROOFS_UPLOAD_TIMEOUT_MINUTES` | `15` |
| `APP_PROOFS_CLEANUP_INITIAL_DELAY_MS` | `300000` |
| `APP_PROOFS_CLEANUP_DELAY_MS` | `3600000` |

## Collection Points

Collection points (PC) are what a player's cards are worth. They feed the `PHYSICAL_COLLECTION_POINTS` and `VIRTUAL_COLLECTION_POINTS` leaderboards, and each point earned pays tokens.

The columns were declared in V65 but never given a scale: every category sat at 0, so no card was worth anything and no token was ever paid. V95 seeds the scale and adds the missing knobs.

| Category | First acquisition (virtual) | Per duplicate | Duplicates per point |
| --- | --- | --- | --- |
| Commun | 1 | 1 | **10** |
| Peu commun | 3 | 1 | 1 |
| Rare | 8 | 2 | 1 |
| Épique | 20 | 5 | 1 |
| Légendaire | 50 | 10 | 1 |

`duplicates_per_point` is what makes "a common is worth a point every ten copies" expressible: the batch count is an integer division, so nine spare commons are worth nothing until the tenth completes the batch. A card may override any of the three values; otherwise it inherits its category's.

A **physical** card is worth its first acquisition multiplied by `physical_first_acquisition_multiplier` (default 2) and nothing for duplicates: owning the object is the reward, stacking printed copies is not. Set `physical_duplicates_enabled` to change that.

Every knob is editable from the admin panel — the scale on each card category, and the global settings through `GET` and `PUT /api/admin/card-collection-settings` (`ADMIN`): the physical multiplier, whether physical duplicates score, and `tokens_per_point` (default 1).

Points are computed on read by `CollectionPointsCalculator`, never stored. Everything that ranks, shows, or pays PC goes through it: duplicating the formula is how a leaderboard and a token balance start disagreeing. Existing collections therefore count on the leaderboard immediately, with no backfill.

Tokens are credited by `CollectionRewardService` at each acquisition, on the **difference** in points before and after the stack grew, never on the total. Nothing is backfilled: a player is not paid retroactively for cards they already held.

## Leaderboards

Leaderboards are released as snapshots. The API supports:

- User leaderboards.
- Department leaderboards.
- Admin configuration for refresh delay and auto-refresh.
- Manual release from the admin API.

Non-student users are hidden from leaderboards by default when synchronized from Keycloak.

The four ranked metrics are `TOURNAMENT_POINTS`, `CHALLENGE_POINTS`, `PHYSICAL_COLLECTION_POINTS`, and `VIRTUAL_COLLECTION_POINTS`. `TOURNAMENT_POINTS` replaced the former `TOKENS` metric: tokens remain the in-app wallet spent on decks and the game, but they are no longer ranked. Tournament points live in `users.tournament_points` and are granted only through a tournament settlement.

## Achievements

An achievement is an administrator-authored goal: a free title and description, one **key phrase** with its parameters, and a token payout. Phrases are Spring plugins discovered at startup, so the catalogue grows without touching the engine:

| Key | Phrase | Parameters |
| --- | --- | --- |
| `CARD_CATEGORY_OBTAINED` | Obtiens N cartes de catégorie X | `categoryId`, `count` |
| `CARD_CATEGORY_COMPLETION` | Atteins X % de la catégorie Y | `categoryId`, `percentage` |
| `CARD_DUPLICATE_COUNT` | Obtiens N fois une même carte | `copies` |
| `CHALLENGES_VALIDATED` | Fais valider N défis | `count` |
| `CHALLENGE_STREAK` | Fais valider un défi N jours d'affilée | `days` |
| `TOURNAMENTS_ATTENDED` | Sois présent à N tournois | `count` |
| `TOURNAMENT_PODIUMS` | Termine N fois sur le podium | `count` |
| `EVENT_REGISTRATIONS` | Inscris-toi à N événements | `count` |
| `FRIENDS_COUNT` | Ajoute N amis | `count` |
| `TREASURE_CHECKPOINTS` | Valide N checkpoints de la chasse au trésor | `count` |
| `QR_SCANNED` | Scanne le QR permanent | — |
| `PLACE_BONUS_PIXELS_USED` | Utilise N pixels bonus sur r/place | `count` |
| `TOKENS_BALANCE` | Détiens N tokens en même temps | `amount` |
| `LEADERBOARD_RANK` | Entre dans le top N d'un classement | `metric`, `maxRank` |
| `STS_RUNS_FINISHED` | Termine N runs d'Aventure | `count` |
| `STS_BEST_FLOOR` | Atteins l'étage N | `floor` |
| `STS_BEST_SCORE` | Termine une run avec N points | `score` |
| `STS_PVP_WINS` | Gagne N duels | `count`, `includeFriendly` |
| `STS_PVP_ELO` | Atteins N d'elo | `elo` |

Card categories are the ranked rarity axis, so `CARD_CATEGORY_OBTAINED` covers "obtain a common card", "obtain a legendary card", and "obtain a card of category X" with different parameters rather than three separate phrases. `CARD_CATEGORY_COMPLETION` resolves its target against the category's live card count and rounds up, so "50 % of 5 cards" needs 3: a completion goal should never clear while the player is short of the honest half.

Three phrases are shaped by what the data allows rather than by what would read best. The permanent QR link is one destination and scanning it flips a boolean, so `QR_SCANNED` takes no parameter. The r/place canvas keeps no per-player pixel history, so only bonus pixels — earned through challenges and counted on the user — are measurable. There is no ledger of token spending, so "tokens spent" is not expressible at all; `TOKENS_BALANCE` measures what a player holds instead.

A finished run means a **retired** one: retiring is the ending that banks a score and pays tokens. `StsRunStatus.Completed` exists in the enum but is never assigned anywhere in the codebase, so a phrase counting it would count nothing. Floor and score read every run including deaths, because dying on floor 30 is still reaching floor 30, and elo unlocks the first time a threshold is crossed rather than tracking the current value: an achievement records what a player did, not what they still hold. Friendly duels are opt-in on `STS_PVP_WINS`, since two players can arrange them at will.

`TOKENS_BALANCE` and `LEADERBOARD_RANK` declare no trigger on purpose. A balance moves from half a dozen places, and unlocking an achievement grants tokens itself, which would make the hot path re-enter; ranks only change when a snapshot is released, which is already scheduled. Both rely on the hourly sweep.

Administration lives under `/api/admin/achievements/**` and requires `ADMIN`, because an achievement mints tokens. `GET /api/admin/achievements/types` returns the phrase catalogue with the parameter metadata the admin form renders from.

Players read `GET /api/achievements`, which returns every active achievement with their own progress. **Progress is computed on read**, never stored: a phrase added today reports correctly against yesterday's history and no counter can drift from the data it describes.

Evaluation runs on two paths:

- **Hot path.** A domain service publishes an `AchievementTriggerEvent` through `AchievementTriggerPublisher`; the engine evaluates that one player after the transaction commits, against only the achievements whose phrase listens to that trigger. This is what makes the popup land while the player is still looking at the pack they opened.
- **Cold path.** An hourly sweep (`app.achievements.catch-up.cron`, default `0 30 * * * *`) catches what the hot path structurally cannot: an achievement created today over yesterday's history, an action that happened while the listener was down, a condition no domain event announces.

Unlocking is idempotent through a unique constraint on `(achievement_id, user_id)`: two concurrent triggers race there and the loser reports "already unlocked" instead of paying twice.

The popup is a queue, not a broadcast. An unlock stays in `GET /api/achievements/unseen` until the client acknowledges it with `POST /api/achievements/unseen/acknowledge`, so a toast survives a refresh instead of being lost to a missed push. A `ACHIEVEMENT_UNLOCKED` notification is created alongside it.

`GET /api/achievements/players/{userId}/completion` returns a player's completion ratio, restricted to the requester and their accepted friends.

See [the achievement flow](docs/flows/achievements.md) for payloads, and `src/main/java/fr/insastronaute/webapi/achievement/types/README.md` to add a phrase.

## Event Questionnaires

Some events need to ask their registrants something: walking or running to the picnic, vegetarian or not for the dinner. An organiser attaches questions to an event and reads the tallies back.

Two question types. `SINGLE_CHOICE` is countable and is what the chart is built from; `FREE_TEXT` is read as a list and never charted — it exists because the meal question always drags an allergies field behind it.

Administration is under `/api/admin/events/{eventId}`:

- `GET|PUT /questions` reads and replaces the questionnaire in one call. Questions and options sent **with their id** are updated in place, so renaming "Végétarien" never discards the answers pointing at it; anything left out is removed along with its answers.
- `GET /questionnaire` returns, per question, the tally per option, the free-text answers with their author, and how many registrants never answered.

Players answer at registration: `POST /api/events/{eventId}/registrations` now takes an optional body carrying the answers, and validation happens inside the same transaction, so a rejected answer rolls the registration back with it. `GET /api/events/{eventId}/questions` returns the form to render, empty when the event asks nothing.

Answers stay revisable while registration is open, through `PUT /api/events/{eventId}/registrations/answers`. Without it a registrant who turns vegetarian would have to unregister and register again, losing their seat if the quota filled in between.

A question added after people have registered is allowed. Those registrants simply count as unanswered, which the results report separately rather than folding into the percentages: option shares are computed over the people who answered, so the chart is never diluted by silence, and `unansweredCount` tells the organiser who to chase.

An answer carries exactly one shape — an option or a text — enforced by a database check rather than by convention.

See [the questionnaire flow](docs/flows/event-questionnaires.md).

## Tournaments

A tournament is an event with `source=TOURNAMENT`. Players register through the usual event endpoints, then the organiser checks them in and converts attendance into leaderboard points.

Administration runs under `/api/admin/tournaments/**`, open to the same roles as event administration, `RESPO_TOURNOIS` and `SOUS_RESPO_TOURNOIS` included:

- `GET /api/admin/tournaments` lists every tournament, most recent first, with registration, attendance, and settlement counters.
- `GET /api/admin/tournaments/{eventId}` returns the participants, their check-in state, the points already granted, and the saved victory tiers.
- `PUT /api/admin/tournaments/{eventId}/attendance` saves check-ins without granting anything. Attendance is tri-state: `true`, `false`, or `null` when the organiser has not decided yet.
- `POST /api/admin/tournaments/{eventId}/settle` grants participation points to everyone marked present, adds the victory tiers on top, and notifies each player whose balance moved.
- `DELETE /api/admin/tournaments/{eventId}/settlement` rolls the whole settlement back and clears the ledger, keeping the check-ins.

Victory tiers are ordered steps (`1er`, `2e`, …) that each carry their own bonus and can hold several players at once, which is how a winning team shares a placement. A tier member must be marked present, and a player cannot appear in two tiers.

Settlement is idempotent. Every grant is written to `tournament_point_awards`, and re-running a settlement compares the new outcome with that ledger and applies only the difference, so validating twice never stacks points and un-checking somebody hands their points back.

Players are notified with a `TOURNAMENT_POINTS_AWARDED` notification carrying the event, the participation and victory split, and the tier reached. A notification failure is logged and never rolls back points that were already granted.

See [the tournament flow](docs/flows/tournaments.md) for the complete admin workflow and payloads.

## Administration Action Register

`admin_action_events` records who granted what, to whom, and when: challenge decisions,
token movements, virtual and physical card grants and removals, and the three global-scope
gestures that move points for many players at once. It is **append-only**. Nothing updates
or deletes a row, and no write verb is mapped on its path; a test walks the mappings under
the prefix and accepts `GET` only.

`GET /api/admin/action-events` reads it, newest first, in the usual `PageResponse`
envelope, and requires `ADMIN`. Every filter is typed and optional — `actorUserId`,
`targetUserId`, `action` (repeatable), `scope`, `from`, `to`. `from` is inclusive and `to`
is exclusive. Any `sort` parameter is ignored: the register is always chronological.

Two of a row's sides can be absent, and the payload says so with `null` rather than an
empty object. A global-scope row names a scope and a number of affected players instead of
one target, so it has no `target` and is never returned by a `targetUserId` filter — ask
for it with `scope=GLOBAL` instead, `scope=ALL` being the default. A row rebuilt by the
historical backfill may have no `actor` when its original moderator can no longer be
resolved, and is likewise never returned by an `actorUserId` filter.

`GET /api/admin/action-events/types` returns every action type with its `globalScope`, so
the admin panel's filter does not keep a copy of the list.

## Testing

Run all tests:

```bash
./gradlew test
```

Tests use:

```text
src/test/resources/application-test.properties
```

The test profile uses an in-memory H2 database and disables Flyway.

## Building

Build the application JAR:

```bash
./gradlew clean bootJar
```

Build the Docker image:

```bash
docker build -t insastronaute-webapi:latest .
```

## Troubleshooting

If the app fails with missing datasource credentials, set `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

If login redirects fail, check `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, and the Keycloak redirect URI:

```text
{baseUrl}/login/oauth2/code/keycloak
```

If browser requests fail because of CORS or cookies, check `APP_SECURITY_FRONTEND_BASE_URL`, `DOMAIN`, and `APP_SECURITY_ALLOWED_SUBDOMAINS`.

If proof uploads fail, check the S3 endpoint, bucket, access key, secret key, and bucket permissions.

If Docker Compose starts but the API is unreachable, verify that the Spring `SERVER_PORT` matches the port published by the compose file.

## Notifications push

Les notifications de l'application peuvent partir jusqu'a l'appareil, application
fermee. Le systeme est **ferme par defaut** : sans cles VAPID, rien ne s'active et
l'application demarre normalement.

### Mise en service, une seule fois

```bash
./gradlew vapidKeys
```

La commande imprime les quatre lignes a coller dans le `.env` du serveur. **Elle n'est
pas a rejouer** : chaque appareil abonne a memorise la cle publique, et en changer
ferait refuser tous les envois suivants par les services de push. Personne ne s'en
apercevrait, sinon en cessant de recevoir.

```
APP_PUSH_ENABLED=true
APP_PUSH_SUBJECT=mailto:contact@example.com
APP_PUSH_VAPID_PUBLIC_KEY=MFkwEwYHKoZIzj0CAQ...
APP_PUSH_VAPID_PRIVATE_KEY=MEECAQAwEwYHKoZIzj0...
```

`APP_PUSH_SUBJECT` doit etre une adresse joignable ou une URL du service : c'est par
la que Mozilla ou Google previennent en cas d'abus, et un `mailto:` fantaisiste peut
faire refuser les envois.

Active sans cles, l'application **refuse de demarrer** plutot que d'empiler a chaque
passage du planificateur des erreurs qu'aucun ecran ne montre.

### Verifier que la chaine fonctionne

1. Dans l'application joueur, **Parametres > Notifications > Activer**. C'est le seul
   endroit ou la permission se demande.
2. Dans le panel d'administration, tableau de bord, **Test Cedric**. La carte indique
   le nombre d'appareils abonnes : zero est le cas le plus frequent et le plus
   trompeur, le push s'activant cote joueur et non dans le panel.

### Sur iPhone

Safari ne delivre le push **que si l'application est installee sur l'ecran d'accueil**
(iOS 16.4+). Hors de la, l'interrupteur est remplace par l'instruction d'installation :
proposer d'activer la ou rien n'arrivera brulerait une permission qu'on ne peut pas
redemander. Un essai sur un vrai iPhone installe est indispensable avant de compter
dessus.

### Ce qui se passe a l'envoi

Ecrire une notification depose une ligne dans `push_outbox`, dans la meme transaction.
Un planificateur la reprend, resout les abonnements du destinataire et envoie. Un
service de push injoignable ne peut donc ni faire echouer l'ecriture, ni ralentir la
requete qui l'a declenchee - ce qui compte lors des diffusions, une annulation
d'evenement prevenant tous ses inscrits.

Un `404` ou un `410` signifie que l'appareil n'existe plus : l'abonnement est
supprime. C'est la seule purge du systeme.
