# Développement local

## Prérequis

Installer JDK 25, Docker, Docker Compose et curl. Les commandes suivantes se lancent à
la racine du dépôt, dans Bash ou Zsh. Le wrapper Gradle fournit Gradle.

```bash
cp .env.example .env
set -a
. ./.env
set +a
docker compose up -d --wait
until curl -fsS http://localhost:18080/realms/insastronaute/.well-known/openid-configuration > /dev/null 2>&1; do
  sleep 2
done
```

Attendre que les dépendances soient prêtes avec `docker compose ps` et, si
nécessaire, `docker compose logs keycloak s3`. L'import initial du realm
Keycloak peut prendre un moment.

```bash
./gradlew bootRun
```

L'API écoute sur `http://localhost:3457`. Les variables sont exportées dans le
terminal courant : les réimporter dans chaque nouveau terminal avant `bootRun`.
Gradle ne lit pas `.env` automatiquement. `SPRING_DOCKER_COMPOSE_ENABLED=false`
laisse la gestion des services aux commandes Compose ci-dessus.

## Services et comptes de développement

| Service | Adresse sur la machine | Configuration |
| --- | --- | --- |
| API | `http://localhost:3457` | Application lancée par Gradle |
| PostgreSQL 17 | `localhost:15432` | Identifiants dans `.env.example` |
| Redis 8 | `localhost:16379` | Sessions et traitements différés |
| Keycloak 26.8.2 | `http://localhost:18080` | Realm `insastronaute` importé au premier démarrage |
| SeaweedFS 4.53, API S3 | `http://localhost:18333` | Clé `local-access-key`, secret `local-secret-key` |
| Frontend, séparé | `http://localhost:5173` | Non fourni dans ce dépôt |

Les identifiants ci-dessous sont des données de développement publiques ; ils ne
doivent pas être réutilisés pour un déploiement.

- Compte applicatif : `developer` / `local-password`, rôles de realm `ADMIN` et `STUDENT`.
- Console Keycloak : `http://localhost:18080/admin/`, administrateur du realm
  `master` : `local-admin` / `local-admin-password`.
- Client OIDC du realm `insastronaute` : `inte-backend`, secret `local-client-secret`.

La configuration importée est dans
[`docker/keycloak/insastronaute-realm.json`](../docker/keycloak/insastronaute-realm.json).
Keycloak conserve le realm dans son volume : modifier le JSON ne remplace pas un
realm existant à chaque redémarrage. Voir la documentation
[import/export Keycloak](https://www.keycloak.org/server/importExport).

## Vérifier la connexion

```bash
curl http://localhost:3457/api/session
curl http://localhost:3457/api/csrf
```

Sans connexion, `/api/session` indique `authenticated: false`. Ouvrir ensuite
`http://localhost:3457/oauth2/authorization/keycloak?redirect_uri=http://localhost:3457/api/session`
dans un navigateur et se connecter avec `developer`. Le retour OIDC utilise
`http://localhost:3457/login/oauth2/code/keycloak`, puis la page de session est
affichée sans nécessiter le frontend. Le contrôle de file d’admission est
désactivé localement par `APP_AUTH_GATE_ENABLED=false`.

Swagger est accessible après connexion sur
`http://localhost:3457/swagger-ui/index.html`. Les requêtes qui modifient l'état
exigent le cookie de session et l'en-tête CSRF `X-XSRF-TOKEN`, obtenu depuis
`/api/csrf`. Pour le frontend, conserver les cookies avec `credentials: 'include'`.

Pour utiliser un frontend local sur `http://localhost:5173`, définir
`APP_SECURITY_FRONTEND_BASE_URL=http://localhost:5173` dans `.env`, réexporter les
variables puis redémarrer l'API. Sans frontend, la valeur fournie pointe vers
l'API pour permettre la connexion et le retour sur `/api/session`.

Le compte de service du client permet les opérations d'administration Keycloak
utilisées par le backend. Pour adapter un realm existant, consulter la section
Keycloak du [guide de production](production.md), notamment les permissions et
les claims de rôles et de département.

## Données et tests

Flyway crée et met à jour le schéma au démarrage : aucune création manuelle des
tables n'est nécessaire. Les données métier, contenus de jeu et fichiers publics
ne sont pas préchargés. Les créer via les interfaces d'administration ou fournir
les catalogues et assets compatibles avec l'installation.

```bash
./gradlew test
./gradlew bootJar
```

Les tests utilisent H2. Ils ne nécessitent pas le lancement de l'API locale.
Pour un essai temporaire sur un autre port :

```bash
SERVER_PORT=13457 ./gradlew bootRun
```

Adapter aussi les URI de redirection Keycloak si une connexion OIDC doit être
réalisée sur ce port ; le realm fourni vise le port `3457`.

## Arrêter et réinitialiser

Arrêter l'API avec `Ctrl+C`, puis les dépendances :

```bash
docker compose down
```

Les volumes restent conservés. Pour repartir d'un environnement local vide,
`docker compose down --volumes` efface les données locales des dépendances ;
relancer ensuite Compose pour réimporter le realm initial.
