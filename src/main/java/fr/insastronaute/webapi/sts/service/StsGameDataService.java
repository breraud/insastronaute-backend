package fr.insastronaute.webapi.sts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.game.dto.GameConfigReleaseDto;
import fr.insastronaute.webapi.game.service.GameConfigService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class StsGameDataService {

    private static final String CARDS_PATH = "STSCardData/cards.json";
    private static final String LEGACY_CARDS_PATH = "catalogs/cards.json";
    private static final String CARDS_PREFIX = "STSCardData/";
    private static final String CARDS_SUFFIX = ".json";
    private static final String CHARACTERS_PREFIX = "Players/";
    private static final String LEGACY_CHARACTERS_PREFIX = "catalogs/characters/";
    private static final String CHARACTERS_SUFFIX = ".json";
    private static final String ENEMIES_PREFIX = "EnemyData/";
    private static final String LEGACY_ENEMIES_PATH = "catalogs/enemies.json";
    private static final String ENEMIES_SUFFIX = ".json";
    private static final String ENCOUNTERS_PATH = "EnemyPool/EnemyPool.json";
    private static final String LEGACY_ENCOUNTERS_PATH = "gameplay/encounter_tables.json";
    private static final String EVENTS_PATH = "Events/EventData.json";
    private static final String LEGACY_EVENTS_PATH = "events/EventData.json";
    private static final String MAP_GENERATION_PATH = "gameplay/map_generation.json";
    private static final String RELICS_PATH = "Relics/relics.json";
    private static final String LEGACY_RELICS_PATH = "gameplay/relics.json";
    private static final String REWARDS_PATH = "gameplay/reward_tables.json";
    private static final String RUN_SCORE_REWARDS_PATH = "gameplay/run_score_rewards.json";
    private static final String PVP_RATING_PATH = "gameplay/pvp_rating.json";
    private static final String PVP_DECK_RULES_PATH = "gameplay/pvp_deck_rules.json";
    private static final String PVP_RAID_ENCOUNTERS_PATH = "gameplay/pvp_raid_encounters.json";

    private final GameConfigService gameConfigService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, JsonNode> cardsByVersion = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ArrayNode> enemiesByVersion = new ConcurrentHashMap<>();

    public StsGameDataService(GameConfigService gameConfigService, ObjectMapper objectMapper) {
        this.gameConfigService = gameConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * @return true when the catalogs of this version are already in memory
     *
     * <p>Lets the first combat of a version, which pays for reading and parsing the catalogs, be
     * measured apart from every later one, which does not. Averaged together they hide both.
     */
    public boolean isCatalogLoaded(String dataVersion) {
        return dataVersion != null
                && cardsByVersion.containsKey(dataVersion)
                && enemiesByVersion.containsKey(dataVersion);
    }

    public String latestVersion() {
        return latestRelease().version();
    }

    public String resolveVersion(String dataVersion) {
        if (dataVersion == null || dataVersion.isBlank()) {
            return latestVersion();
        }
        String latestVersion = latestVersion();
        if (dataVersion.equals(latestVersion)) {
            return latestVersion;
        }
        List<GameConfigReleaseDto> releases = gameConfigService.listReleases();
        if (releases != null && releases.stream().anyMatch(release -> dataVersion.equals(release.version()))) {
            return dataVersion;
        }
        throw new NotFoundException("Version de configuration de jeu introuvable : " + dataVersion);
    }

    public JsonNode cards(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        return cardsByVersion.computeIfAbsent(release.version(), ignored -> loadCards(release));
    }

    private JsonNode loadCards(GameConfigReleaseDto release) {

        List<String> perCardFiles = release.files().stream()
                .filter(file -> file.startsWith(CARDS_PREFIX))
                .filter(file -> file.endsWith(CARDS_SUFFIX))
                .filter(file -> !file.equals(CARDS_PATH))
                .filter(file -> !file.equals(CARDS_PREFIX + "index.json"))
                .sorted(Comparator.naturalOrder())
                .toList();

        if (!perCardFiles.isEmpty()) {
            return cardsFromIndividualFilesFirst(release, perCardFiles);
        }

        for (String path : List.of(CARDS_PATH, LEGACY_CARDS_PATH)) {
            if (release.files().contains(path)) {
                return read(release, path);
            }
        }
        return read(release, CARDS_PATH);
    }

    private JsonNode cardsFromIndividualFilesFirst(GameConfigReleaseDto release, List<String> perCardFiles) {
        Map<String, JsonNode> cardsByKey = new LinkedHashMap<>();

        for (String file : perCardFiles) {
            JsonNode card = read(release, file);
            String key = cardKey(card);
            if (key == null) {
                continue;
            }
            cardsByKey.put(key, card);
        }

        if (release.files().contains(CARDS_PATH)) {
            JsonNode combined = read(release, CARDS_PATH);
            for (JsonNode card : extractCards(combined)) {
                String key = cardKey(card);
                if (key == null || cardsByKey.containsKey(key)) {
                    continue;
                }
                cardsByKey.put(key, card);
            }
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("dataVersion", release.version());
        ArrayNode cards = root.putArray("cards");
        cardsByKey.values().forEach(cards::add);
        return root;
    }

    private ArrayNode extractCards(JsonNode cardsNode) {
        if (cardsNode == null || cardsNode.isNull()) {
            return objectMapper.createArrayNode();
        }

        if (cardsNode instanceof ArrayNode arrayNode) {
            return arrayNode;
        }

        if (cardsNode.path("cards") instanceof ArrayNode arrayNode) {
            return arrayNode;
        }

        return objectMapper.createArrayNode();
    }

    private String cardKey(JsonNode card) {
        if (card == null || card.isNull()) {
            return null;
        }

        String id = card.path("id").asText("").trim();
        if (!id.isBlank()) {
            return id;
        }

        String cardName = card.path("cardName").asText("").trim();
        if (!cardName.isBlank()) {
            return cardName;
        }

        return null;
    }

    public ArrayNode enemies(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        return enemiesByVersion.computeIfAbsent(release.version(), ignored -> loadEnemies(release));
    }

    private ArrayNode loadEnemies(GameConfigReleaseDto release) {
        if (release.files().stream().anyMatch(file -> file.startsWith(ENEMIES_PREFIX))) {
            ArrayNode enemies = objectMapper.createArrayNode();
            release.files().stream()
                    .filter(file -> file.startsWith(ENEMIES_PREFIX))
                    .filter(file -> file.endsWith(ENEMIES_SUFFIX))
                    .filter(file -> !file.equals(ENEMIES_PREFIX + "index.json"))
                    // The directory also holds an aggregate of every enemy; reading it as one
                    // would add a nameless enemy with no moves to every encounter pool.
                    .filter(file -> !file.equals(ENEMIES_PREFIX + "enemies.json"))
                    .sorted()
                    .map(file -> read(release, file))
                    .forEach(enemies::add);

            return enemies;
        }

        JsonNode legacyEnemies = read(release, LEGACY_ENEMIES_PATH);
        if (legacyEnemies instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        if (legacyEnemies.path("enemies") instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        throw new BadRequestException("Catalogue d'ennemis STS invalide.");
    }

    public ArrayNode characters(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        ArrayNode characters = objectMapper.createArrayNode();
        String characterPrefix = release.files().stream().anyMatch(file -> file.startsWith(CHARACTERS_PREFIX))
                ? CHARACTERS_PREFIX
                : LEGACY_CHARACTERS_PREFIX;
        release.files().stream()
                .filter(file -> file.startsWith(characterPrefix))
                .filter(file -> file.endsWith(CHARACTERS_SUFFIX))
                .filter(file -> !file.equals(characterPrefix + "index.json"))
                .sorted()
                .map(file -> read(release, file))
                .forEach(characters::add);

        return characters;
    }

    public JsonNode character(String dataVersion, String characterId) {
        if (characterId == null || characterId.isBlank()) {
            throw new BadRequestException("Le personnage STS est requis.");
        }

        String normalizedCharacterId = characterId.trim();
        for (JsonNode character : characters(dataVersion)) {
            if (normalizedCharacterId.equals(character.path("characterId").asText())) {
                return character;
            }
        }

        throw new BadRequestException("Personnage STS inconnu.");
    }

    public JsonNode mapGeneration(String dataVersion) {
        return read(dataVersion, MAP_GENERATION_PATH);
    }

    public JsonNode rewardTables(String dataVersion) {
        return read(dataVersion, REWARDS_PATH);
    }

    public JsonNode runScoreRewards(String dataVersion) {
        return read(dataVersion, RUN_SCORE_REWARDS_PATH);
    }

    /**
     * Le barème des duels, ou un objet vide quand la version des données n'en porte pas.
     *
     * <p>Facultatif, contrairement aux autres catalogues : c'est un réglage d'équilibrage arrivé
     * après coup, et les versions publiées avant lui doivent continuer à régler leurs duels avec
     * les valeurs d'origine plutôt que d'échouer.
     */
    public JsonNode pvpRating(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        if (release.files() == null || !release.files().contains(PVP_RATING_PATH)) {
            return objectMapper.createObjectNode();
        }
        return read(release, PVP_RATING_PATH);
    }

    /**
     * Les tailles de deck PVP, ou un objet vide quand la version des données n'en porte pas.
     *
     * <p>Facultatif comme {@link #pvpRating}, pour la même raison : une version publiée avant ce
     * réglage doit continuer à composer ses decks avec les tailles d'origine plutôt que d'échouer.
     */
    public JsonNode pvpDeckRules(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        if (release.files() == null || !release.files().contains(PVP_DECK_RULES_PATH)) {
            return objectMapper.createObjectNode();
        }
        return read(release, PVP_DECK_RULES_PATH);
    }

    /**
     * Les rencontres de raid, ou un objet vide quand la version des données n'en porte pas.
     *
     * <p>Facultatif comme {@link #pvpRating} : sans ce fichier, un raid garde le boss unique que
     * la configuration du serveur désigne, exactement comme avant.
     */
    public JsonNode pvpRaidEncounters(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        if (release.files() == null || !release.files().contains(PVP_RAID_ENCOUNTERS_PATH)) {
            return objectMapper.createObjectNode();
        }
        return read(release, PVP_RAID_ENCOUNTERS_PATH);
    }

    public JsonNode relics(String dataVersion) {
        return readFirst(dataVersion, List.of(RELICS_PATH, LEGACY_RELICS_PATH));
    }

    public JsonNode encounterTables(String dataVersion) {
        GameConfigReleaseDto release = release(dataVersion);
        if (release.files().contains(ENCOUNTERS_PATH)) {
            return encounterTablesFromEnemyPool(read(release, ENCOUNTERS_PATH));
        }
        return read(release, LEGACY_ENCOUNTERS_PATH);
    }

    public JsonNode enemyPool(String dataVersion) {
        return read(dataVersion, ENCOUNTERS_PATH);
    }

    /**
     * Le nombre total d'actes, écrit une seule fois sur l'enemy pool.
     *
     * <p>Tolère l'absence de données : un pool illisible ne doit pas empêcher une run de se
     * terminer, et un seul acte est la réponse la plus prudente.
     */
    public int actCount(String dataVersion) {
        return actCount(enemyPoolOrNull(dataVersion));
    }

    private int actCount(JsonNode pool) {
        int maxAct = pool == null ? -1 : pool.path("maxAct").asInt(-1);
        return maxAct > 0 ? maxAct : 1;
    }

    /**
     * L'acte sur lequel une nouvelle run démarre, en index interne (0 = Acte 1).
     *
     * <p>Écrit dans l'enemy pool sous {@code startingAct}, <em>numéroté comme le joueur le voit</em>
     * — {@code "startingAct": 3} démarre sur l'Acte 3 — contrairement aux {@code minAct}/{@code
     * maxAct} des entrées du même fichier, qui sont, eux, des index internes. La différence est
     * assumée : ce réglage-là se règle à la main en pensant à un numéro d'acte affiché, alors que
     * les bornes des entrées se lisent à côté de la valeur que le serveur compare.
     *
     * <p>Borné à un acte qui existe : une valeur au-delà du dernier acte démarrerait une run dont
     * aucune table d'encounter ne peut rien tirer, et 0 ou une absence valent le premier acte.
     */
    public int startingAct(String dataVersion) {
        JsonNode pool = enemyPoolOrNull(dataVersion);
        if (pool == null) {
            return 0;
        }
        int authored = pool.path("startingAct").asInt(1);
        int index = Math.max(0, authored - 1);
        return Math.min(index, Math.max(0, actCount(pool) - 1));
    }

    /**
     * L'enemy pool, ou null quand la version des données n'en publie pas de lisible.
     *
     * <p>Les deux réglages d'acte ci-dessus sont consultés sur des chemins — fin de run, création
     * de run — où l'échec du catalogue n'a rien à voir avec ce que le joueur est en train de
     * faire, et où lever ferait perdre la run plutôt que le réglage.
     */
    private JsonNode enemyPoolOrNull(String dataVersion) {
        try {
            JsonNode pool = enemyPool(dataVersion);
            return pool == null || pool.isNull() ? null : pool;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public JsonNode events(String dataVersion) {
        return readFirst(dataVersion, List.of(EVENTS_PATH, LEGACY_EVENTS_PATH));
    }

    private GameConfigReleaseDto latestRelease() {
        return gameConfigService.latestRelease();
    }

    private GameConfigReleaseDto release(String dataVersion) {
        if (dataVersion == null || dataVersion.isBlank()) {
            return latestRelease();
        }

        GameConfigReleaseDto latest = latestRelease();
        if (dataVersion.equals(latest.version())) {
            return latest;
        }

        List<GameConfigReleaseDto> releases = gameConfigService.listReleases();
        if (releases != null) {
            for (GameConfigReleaseDto release : releases) {
                if (dataVersion.equals(release.version())) {
                    return release;
                }
            }
        }

        return latest;
    }

    private JsonNode readFirst(String dataVersion, List<String> paths) {
        GameConfigReleaseDto release = release(dataVersion);
        for (String path : paths) {
            if (release.files().contains(path)) {
                return read(release, path);
            }
        }
        return read(release, paths.getFirst());
    }

    private JsonNode read(String dataVersion, String path) {
        return read(release(dataVersion), path);
    }

    private JsonNode read(GameConfigReleaseDto release, String path) {
        try {
            return objectMapper.readTree(gameConfigService.readReleaseFile(release.version(), path));
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Configuration STS invalide: " + path);
        }
    }

    private JsonNode encounterTablesFromEnemyPool(JsonNode enemyPool) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode tables = root.putArray("tables");
        addEncounterTable(tables, enemyPool, "Combat", enemy -> !enemy.path("elite").asBoolean(false) && !enemy.path("boss").asBoolean(false));
        addEncounterTable(tables, enemyPool, "Elite", enemy -> enemy.path("elite").asBoolean(false));
        addEncounterTable(tables, enemyPool, "Boss", enemy -> enemy.path("boss").asBoolean(false));
        return root;
    }

    private void addEncounterTable(ArrayNode tables, JsonNode enemyPool, String nodeType, Predicate<JsonNode> predicate) {
        ObjectNode table = objectMapper.createObjectNode();
        ArrayNode nodeTypes = table.putArray("nodeTypes");
        nodeTypes.add(nodeType);
        ArrayNode entries = table.putArray("entries");
        for (JsonNode enemyEntry : enemyPool.path("enemies")) {
            if (!predicate.test(enemyEntry)) {
                continue;
            }
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("encounterId", encounterId(enemyEntry));
            ArrayNode enemyIds = entry.putArray("enemyIds");
            enemyEntry.path("enemyIds").forEach(enemyIds::add);
            entry.put("weight", enemyEntry.path("weight").asDouble(1));
            entries.add(entry);
        }
        tables.add(table);
    }

    private String encounterId(JsonNode enemyEntry) {
        String displayName = enemyEntry.path("displayName").asText("");
        if (!displayName.isBlank()) {
            return displayName;
        }
        ArrayNode enemyIds = (ArrayNode) enemyEntry.path("enemyIds");
        if (!enemyIds.isEmpty()) {
            return enemyIds.get(0).asText();
        }
        return "Encounter";
    }
}
