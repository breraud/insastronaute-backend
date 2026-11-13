# Déploiement en production

Ce guide installe l'API, PostgreSQL et Redis avec `compose.prod.yaml`. Keycloak, le stockage compatible S3 et le proxy HTTPS sont fournis séparément. Il décrit la configuration à réaliser ; il ne constitue pas une validation de votre infrastructure en production.

## Préparer le serveur

Installer Git, JDK 25, Docker et Docker Compose. Prévoir des noms DNS et des certificats HTTPS, par exemple :

- `api.example.com` : API derrière le proxy ;
- `app.example.com` : frontend ;
- `auth.example.com` : Keycloak ;
- `s3.example.com` : accès public au stockage.

Le serveur doit joindre Keycloak et S3. Les navigateurs doivent joindre leurs adresses publiques. PostgreSQL et Redis sont internes au réseau Compose, sans port publié ; leurs données sont conservées dans des volumes. Le port de l'API est publié uniquement sur `127.0.0.1:3457` par défaut.

```bash
git clone https://github.com/breraud/insastronaute-backend.git
cd insastronaute-backend
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Remplacer tous les exemples dans `.env.prod`. Garder ce fichier hors de Git. Utiliser un terminal sans les variables du développement exportées : les variables du shell ont priorité sur `--env-file` pour l'interpolation Compose ([documentation Docker](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)).

## Configurer l'environnement

Renseigner au minimum :

| Variables | Configuration |
| --- | --- |
| `APP_DB_NAME`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base et identifiants PostgreSQL propres à cette installation |
| `REDIS_PASSWORD` | Secret Redis dédié |
| `KEYCLOAK_ISSUER_URI` | URL publique du realm, par exemple `https://auth.example.com/realms/insastronaute` |
| `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` | Client confidentiel de l'API |
| `S3_ENDPOINT` | Endpoint joignable depuis le conteneur API |
| `S3_PUBLIC_ENDPOINT` | Endpoint HTTPS joignable depuis les navigateurs |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION` | Identifiants et région du service S3 |
| `S3_BUCKET`, `S3_PUBLIC_BUCKET` | Noms des buckets privés et publics de l'installation |
| `APP_SECURITY_FRONTEND_BASE_URL` | URL HTTPS du frontend, par exemple `https://app.example.com` |
| `DOMAIN` | Domaine parent des cookies, par exemple `example.com` |
| `APP_SECURITY_ALLOWED_SUBDOMAINS` | Sous-domaines autorisés, séparés par des virgules, selon les clients utilisés |
| `QR_LINK_PUBLIC_URL`, `QR_LINK_SCAN_LANDING_URL` | URL publique `/q` de l'API et page `/scan` du frontend |
| `APP_AUTH_GATE_SECRET` | Secret aléatoire stable propre à l'installation, par exemple généré par `openssl rand -hex 32` |

Conserver les cookies `Secure`, `HttpOnly`, et la politique `SameSite` de l'exemple. Garder `APP_SECURITY_PRODUCTION_HARDENING_ENABLED=true`, `SPRINGDOC_ENABLED=false` et `APP_STS_DEBUG_COMBAT_ENABLED=false`. Les notifications push restent désactivées sans une paire de clés VAPID et un sujet de contact valides.

Séparer le bucket privé des preuves du bucket des médias publics. Le compte S3 doit permettre l’initialisation des buckets, `PutBucketPolicy`, la gestion CORS et les ACL compatibles avec les traitements activés. La politique du bucket public expose les assets prévus à cet effet ; ne pas y déposer de preuves privées. Les drapeaux S3 de l’exemple font échouer le démarrage si l’initialisation échoue. Vérifier la compatibilité du fournisseur et les URL présignées depuis le navigateur. Les catalogues de jeu et les assets sont à charger séparément via l’administration.

## Configurer Keycloak

Créer un realm dédié et un client OpenID Connect `inte-backend` :

1. Activer l'authentification du client, le flux standard et les comptes de service.
2. Définir l'URI de redirection exacte
   `https://api.example.com/login/oauth2/code/keycloak` et les origines web de
   l'installation. Configurer les destinations de déconnexion nécessaires.
3. Pour Keycloak 26.8 et versions suivantes, ajouter `kc-admin-api` aux client
   scopes **par défaut** de ce client : ses jetons de compte de service doivent
   porter le scope et l'audience de l'API d'administration. Ce scope ne remplace
   pas les rôles `realm-management`. Reporter le secret du client dans `KEYCLOAK_CLIENT_SECRET` et l'URL publique
   du realm dans `KEYCLOAK_ISSUER_URI`.
4. Définir les rôles applicatifs du realm, dont `ADMIN` et `STUDENT`, puis les
   attribuer aux utilisateurs appropriés. Vérifier que les claims de rôles sont
   présents dans les jetons ; un compte administrateur Keycloak `master` ne
   devient pas automatiquement administrateur de l'application.
5. Pour la synchronisation des départements, exposer le claim multivalué
   `department` à l'utilisateur OIDC et préparer les groupes `CFI`, `EP`, `GM`,
   `ITI`, `MECA` et `MRIE`. Les autres groupes sont préservés.

Accorder au compte de service uniquement les permissions `realm-management` nécessaires aux fonctions activées : lecture/recherche des utilisateurs et groupes (`view-users`, `query-users`, `query-groups`, `view-realm`), et `manage-users` si le backend doit modifier les groupes ou les rôles des utilisateurs. Ne pas utiliser `realm-admin` comme raccourci. Vérifier ces droits avec les opérations réellement activées ; la synchronisation des groupes est contrôlée par `KEYCLOAK_DEPARTMENT_GROUP_SYNC_ENABLED`.

`KEYCLOAK_ADMIN_BASE_URL` peut désigner une adresse interne pour les appels administratifs serveur à serveur. Elle ne remplace pas l'émetteur public OIDC, qui doit rester cohérent avec les jetons et accessible au navigateur. Voir le [guide d'administration Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html).

## Installer le proxy HTTPS

Terminer TLS sur un proxy installé sur l'hôte et relayer vers `http://127.0.0.1:3457`. Exemple Nginx à adapter dans le contexte `http` ; les fichiers de certificat doivent déjà exister :

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}

server {
    listen 443 ssl;
    server_name api.example.com;
    ssl_certificate /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3457;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header Forwarded "";
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        client_max_body_size 25m;
    }
}
```

Ajouter la redirection HTTP vers HTTPS selon la configuration du serveur. Le proxy remplace les en-têtes de transfert reçus du client ; avec la stratégie `SERVER_FORWARD_HEADERS_STRATEGY=framework`, l'API doit uniquement être joignable par ce proxy de confiance. Si le proxy tourne lui-même dans Docker, adapter le réseau et la cible : `127.0.0.1` y désigne son propre conteneur.

Les en-têtes de mise à niveau et les délais sont nécessaires pour les connexions WebSocket durables ([documentation Nginx](https://nginx.org/en/docs/http/websocket.html)). Adapter ensemble les limites de téléversement du proxy et de Spring si les builds ou assets de jeu dépassent la limite de l'exemple.

## Premier démarrage et vérification

```bash
./gradlew test bootJar
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
docker compose --env-file .env.prod -f compose.prod.yaml ps
```

Le Dockerfile embarque le JAR déjà construit ; il ne lance pas Gradle. Flyway applique les migrations au démarrage, puis Hibernate valide le schéma. Consulter les erreurs si nécessaire :

```bash
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 app
curl https://api.example.com/api/session
curl https://api.example.com/api/csrf
```

Les deux routes doivent répondre sans connexion. Dans le navigateur, effectuer une connexion depuis le frontend puis vérifier `/api/session` dans la même session. Tester une opération avec CSRF et un téléversement S3. En production, Swagger reste désactivé. Vérifier que PostgreSQL, Redis et le port direct de l'API ne sont pas accessibles depuis l'extérieur.

Le démarrage peut signaler `Aucune limitation de debit` même si Redis est
joignable. Ce cas a été observé pendant la vérification locale : le limiteur
applicatif n'est alors pas installé. Prévoir une limitation de débit au proxy
avant d'exposer l'API ; la présence du conteneur Redis ne suffit pas à la garantir.

## Mise à jour et sauvegardes

Avant une mise à jour, sauvegarder PostgreSQL, les objets et paramètres S3, ainsi que la base et la configuration Keycloak. Conserver aussi les secrets nécessaires à une restauration, séparément du code. Tester les restaurations ; les exports de realm Keycloak seuls ne remplacent pas une stratégie de sauvegarde complète ([limitations de l'export](https://www.keycloak.org/server/importExport)).

Sur la branche `main`, sans modifications locales du code :

```bash
git pull --ff-only
./gradlew test bootJar
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
```

Vérifier ensuite les journaux et les routes comme au premier démarrage. Un retour à une image antérieure doit tenir compte des migrations déjà appliquées ; garder une sauvegarde restaurable avant toute migration.

`docker compose --env-file .env.prod -f compose.prod.yaml down` conserve les volumes. Ne pas ajouter `--volumes` pour une opération de maintenance.
