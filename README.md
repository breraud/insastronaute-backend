# INSAstronaute Backend

API Java 25 / Spring Boot 4 pour INSAstronaute : comptes, défis et modération,
événements, cartes, boutique, classements et fonctionnalités de jeu.

Le backend utilise PostgreSQL, Redis, Keycloak et un stockage compatible S3.

## Démarrer en local

Prérequis : JDK 25, Docker, Docker Compose et curl.

```bash
git clone https://github.com/breraud/insastronaute-backend.git
cd insastronaute-backend
cp .env.example .env
set -a
. ./.env
set +a
docker compose up -d --wait
until curl -fsS http://localhost:18080/realms/insastronaute/.well-known/openid-configuration > /dev/null 2>&1; do
  sleep 2
done
./gradlew bootRun
```

Compose lance les dépendances ; l'API tourne sur la machine et écoute sur
`http://localhost:3457`. Le compte de développement est `developer` /
`local-password`.

Le [guide local](docs/local.md) détaille les services, la connexion et les tests.
Le [guide de production](docs/production.md) décrit le déploiement avec HTTPS,
Keycloak et S3 externes.

## Construire et tester

```bash
./gradlew test bootJar
```

Les tests utilisent H2. Le JAR est généré dans `build/libs/`.

## Organisation

- `src/main/java/` : application et domaines métier.
- `src/main/resources/db/migration/` : migrations Flyway appliquées au démarrage.
- `src/test/` : tests et données de test.
- `docker/keycloak/` : configuration Keycloak de développement.
- `compose.yaml` : dépendances locales.
- `compose.prod.yaml` : API, PostgreSQL et Redis de production.

Le dépôt utilise une seule branche : `main`.

## Licence

[PolyForm Noncommercial 1.0.0](LICENSE) autorise l'utilisation, la modification et
la redistribution pour les usages permis par la licence, dont les usages non
commerciaux. Les dépendances conservent leurs licences respectives.
