package fr.insastronaute.webapi.sts.combat.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Builds the {@link CardCatalog} one combat needs out of the authored catalog of a data version.
 *
 * <p>Only the cards that combat can reach are parsed. The authored catalog is written for the
 * client and holds cards this engine cannot express yet, so parsing all of it would fail on a
 * card nobody is playing; and the reachable set is not just the decks, because an effect can put
 * a card no player owns into a pile — a gap that would only show up mid-combat. The references
 * are therefore followed to a fixed point here.
 *
 * <p>PvE and PvP both come through this class so the two cannot drift into two different notions
 * of what a combat is allowed to reference.
 */
public final class CardCatalogFactory {

    private final CardCatalogParser parser = new CardCatalogParser();
    private final ConcurrentMap<String, IndexedCatalog> indexesByVersion = new ConcurrentHashMap<>();
    private final ConcurrentMap<CompleteCatalogKey, CardCatalog> completeCatalogsByVersionAndCharacter =
            new ConcurrentHashMap<>();

    /**
     * @param sourceCatalog  the authored catalog of the data version, as game data serves it
     * @param dataVersion    the data version the combat runs on, used when the catalog omits its own
     * @param cardReferences the cards the combat starts from, by id or by authored name
     * @param character      the character whose created cards a random draw picks from; blank or
     *                       {@code null} leaves the random pool unscoped
     */
    public CardCatalog create(
            JsonNode sourceCatalog,
            String dataVersion,
            Collection<String> cardReferences,
            String character
    ) {
        return create(sourceCatalog, dataVersion, cardReferences, character, List.of());
    }

    /**
     * @param enemies the authored enemies taking part, read for the follow-up statuses they
     *                start with: an enemy whose follow-up names no card plays one drawn from the
     *                whole game, which the combat can only do if it holds the whole game
     */
    public CardCatalog create(
            JsonNode sourceCatalog,
            String dataVersion,
            Collection<String> cardReferences,
            String character,
            Collection<JsonNode> enemies
    ) {
        Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        Objects.requireNonNull(cardReferences, "cardReferences");

        String catalogVersion = sourceCatalog.path("dataVersion").asText(dataVersion);
        IndexedCatalog indexed = indexesByVersion.computeIfAbsent(
            catalogVersion, ignored -> index(sourceCatalog));
        Map<String, JsonNode> cardsById = indexed.cardsById();
        Map<String, String> idByReference = indexed.idByReference();

        // Le Désespoir n'est référencé par aucune carte : c'est le moteur qui le donne quand il
        // n'y a plus rien à piocher (PileOperations.draw), donc tout combat peut en avoir besoin.
        List<String> references = new ArrayList<>(cardReferences);
        references.add(fr.insastronaute.webapi.sts.combat.engine.PileOperations.DESPAIR_CARD_ID);
        references.addAll(enemyFollowUpReferences(enemies));
        LinkedHashSet<String> required = requiredCardIds(references, cardsById, idByReference);
        addRandomPoolIfReachable(cardsById, required, character);
        if (randomFollowUpReachable(cardsById, idByReference, required, enemies)) {
            required.addAll(cardsById.keySet());
        }

        if (required.containsAll(cardsById.keySet())) {
            CompleteCatalogKey key = new CompleteCatalogKey(
                catalogVersion, character == null ? "" : character);
            return completeCatalogsByVersionAndCharacter.computeIfAbsent(key, ignored ->
                parseCatalog(sourceCatalog, dataVersion, cardsById, required, character));
        }

        return parseCatalog(sourceCatalog, dataVersion, cardsById, required, character);
        }

        private CardCatalog parseCatalog(
            JsonNode sourceCatalog,
            String dataVersion,
            Map<String, JsonNode> cardsById,
            LinkedHashSet<String> required,
            String character
        ) {
        ObjectNode combatCatalog = JsonNodeFactory.instance.objectNode();
        combatCatalog.put("dataVersion", sourceCatalog.path("dataVersion").asText(dataVersion));
        ArrayNode combatCards = combatCatalog.putArray("cards");
        for (Map.Entry<String, JsonNode> entry : cardsById.entrySet()) {
            if (required.contains(entry.getKey())) {
                combatCards.add(entry.getValue());
            }
        }

        return parser.parse(combatCatalog).forCharacter(character);
    }

    private record CompleteCatalogKey(String dataVersion, String character) {
    }

    /** Indexes the authored catalog by id, and by name as well, because effects reference either. */
    private static IndexedCatalog index(JsonNode sourceCatalog) {
        Map<String, JsonNode> cardsById = new LinkedHashMap<>();
        Map<String, String> idByReference = new LinkedHashMap<>();
        for (JsonNode card : sourceCatalog.path("cards")) {
            String id = card.path("id").asText("").trim();
            if (id.isBlank()) {
                continue;
            }
            cardsById.putIfAbsent(id, card);
            // Unity indexes a card by its name as well as its id, and effects reference either.
            String cardName = card.path("cardName").asText("").trim();
            if (!cardName.isBlank()) {
                idByReference.putIfAbsent(cardName, id);
            }
        }
        cardsById.keySet().forEach(id -> idByReference.put(id, id));
        return new IndexedCatalog(
                Collections.unmodifiableMap(cardsById),
                Collections.unmodifiableMap(idByReference));
    }

    private record IndexedCatalog(
            Map<String, JsonNode> cardsById,
            Map<String, String> idByReference
    ) {
    }

    /** Every card definition the combat can possibly need, references followed to a fixed point. */
    private static LinkedHashSet<String> requiredCardIds(
            Collection<String> cardReferences,
            Map<String, JsonNode> cardsById,
            Map<String, String> idByReference
    ) {
        LinkedHashSet<String> required = new LinkedHashSet<>();
        for (String reference : cardReferences) {
            addReference(reference, required, idByReference);
        }

        List<String> pending = new ArrayList<>(required);
        while (!pending.isEmpty()) {
            JsonNode card = cardsById.get(pending.removeFirst());
            if (card == null) {
                continue;
            }
            LinkedHashSet<String> created = new LinkedHashSet<>();
            addCreatedCardIds(card, created, idByReference);
            addStatusFollowUpCardIds(card, created, idByReference);
            for (String cardId : created) {
                if (required.add(cardId)) {
                    pending.add(cardId);
                }
            }
        }
        return required;
    }

    /**
     * Loads the pool a random card can come from, but only when the combat can actually reach it.
     *
     * <p>Transforming a card, or adding one with no filter tags, draws from the character's
     * created cards, and from the whole catalog when that character has none
     * ({@link CardCatalog#randomPool()}). An {@code AddRandomCard} effect that carries filter
     * tags instead searches the <em>entire</em> catalog for whatever matches them
     * ({@code AddRandomCardHandler#pool}), independently of the character's favored pool — so
     * scoping {@code required} down to the favored cards here left it with nothing a tag filter
     * would ever match, and the effect silently drew nothing. Loading either pool unconditionally
     * would parse the entire catalog for every combat, so the cost is paid only by decks that can
     * trigger it.
     */
    private static void addRandomPoolIfReachable(
            Map<String, JsonNode> cardsById,
            LinkedHashSet<String> required,
            String character
    ) {
        boolean needsFavoredPool = false;
        boolean needsWholeCatalog = false;
        for (String id : required) {
            JsonNode card = cardsById.get(id);
            if (card == null) {
                continue;
            }
            for (JsonNode effect : card.path("effects")) {
                if (isFilteredRandomDraw(effect)) {
                    needsWholeCatalog = true;
                } else if (isUnfilteredRandomDraw(effect)) {
                    needsFavoredPool = true;
                }
            }
        }
        if (needsWholeCatalog) {
            required.addAll(cardsById.keySet());
            return;
        }
        if (!needsFavoredPool) {
            return;
        }

        String favoredCharacter = character == null ? "" : character;
        List<String> pool = cardsById.entrySet().stream()
                .filter(entry -> favoredCharacter.equals(entry.getValue().path("favoredCharacter").asText("")))
                .filter(entry -> hasCreatedTag(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        required.addAll(pool.isEmpty() ? cardsById.keySet() : pool);
    }

    private static final java.util.Set<String> FOLLOW_UP_STATUSES =
            java.util.Set.of("CardFollowUp", "AnyCardFollowUp", "FieldTurnFollowUp");

    /**
     * Whether some follow-up this combat can reach plays a card drawn from the whole game.
     *
     * <p>A follow-up that names no card, or a card the game does not have, falls back to any card
     * at all (Unity's {@code FollowUpStatus} -> {@code GetRandomCard}). A combat's catalog holds
     * only what the combat names, so without this the draw only ever saw the player's own deck:
     * Coloriage magique kept replaying Innovation and nothing else.
     */
    private static boolean randomFollowUpReachable(
            Map<String, JsonNode> cardsById,
            Map<String, String> idByReference,
            LinkedHashSet<String> required,
            Collection<JsonNode> enemies
    ) {
        for (String id : required) {
            JsonNode card = cardsById.get(id);
            if (card == null) {
                continue;
            }
            for (JsonNode effect : card.path("effects")) {
                if ("Status".equals(effect.path("type").asText(""))
                        && FOLLOW_UP_STATUSES.contains(effect.path("statusType").asText(""))
                        && !resolves(effect.path("cardID").asText(""), cardsById, idByReference)) {
                    return true;
                }
            }
        }
        if (enemies != null) {
            for (JsonNode enemy : enemies) {
                if (enemy == null) {
                    continue;
                }
                if (FOLLOW_UP_STATUSES.contains(enemy.path("startingStatus").asText("").trim())
                        && !resolves(enemy.path("startingStatusInfo").asText(""), cardsById,
                                idByReference)) {
                    return true;
                }
                // A move written inline carries its effects itself (EnemyMoveParser); one that
                // names a card is that card, already followed through the references.
                for (JsonNode move : enemy.path("movePattern")) {
                    if (!move.path("cardId").asText("").isBlank()) {
                        continue;
                    }
                    for (JsonNode effect : move.path("effects")) {
                        if ("Status".equals(effect.path("type").asText(""))
                                && FOLLOW_UP_STATUSES.contains(effect.path("statusType").asText(""))
                                && !resolves(effect.path("cardID").asText(""), cardsById,
                                        idByReference)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The cards the enemies' follow-ups name, which nothing else puts in the catalog.
     *
     * <p>A follow-up an enemy walks in with names its card in {@code startingStatusInfo}, and no
     * deck or move references it. Missing from the catalog, the card counted as unknown and the
     * follow-up played a random one instead -- out of whatever the catalog did hold, which is
     * mostly the player's own deck: the enemy answered with a card the player had just played.
     * The cards named by inline moves are taken as well, so the rule does not rest on each caller
     * remembering them.
     */
    private static List<String> enemyFollowUpReferences(Collection<JsonNode> enemies) {
        List<String> references = new ArrayList<>();
        if (enemies == null) {
            return references;
        }
        for (JsonNode enemy : enemies) {
            if (enemy == null) {
                continue;
            }
            if (FOLLOW_UP_STATUSES.contains(enemy.path("startingStatus").asText("").trim())) {
                references.add(enemy.path("startingStatusInfo").asText(""));
            }
            for (JsonNode move : enemy.path("movePattern")) {
                references.add(move.path("cardId").asText(""));
                for (JsonNode effect : move.path("effects")) {
                    references.add(effect.path("cardID").asText(""));
                }
            }
        }
        return references;
    }

    /** Whether a reference names a card of the authored catalog, by id, by name, or loosely. */
    private static boolean resolves(
            String reference, Map<String, JsonNode> cardsById, Map<String, String> idByReference) {
        String trimmed = reference == null ? "" : reference.trim();
        if (trimmed.isBlank()) {
            return false;
        }
        String cardId = idByReference.get(trimmed);
        if (cardId == null) {
            String normalized = normalizedId(trimmed);
            cardId = idByReference.entrySet().stream()
                    .filter(entry -> normalized.equals(normalizedId(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return cardId != null && cardsById.containsKey(cardId);
    }

    /** An {@code AddRandomCard} narrowed by {@code cardFilterTags}, which searches every card. */
    private static boolean isFilteredRandomDraw(JsonNode effect) {
        return "AddRandomCard".equals(effect.path("type").asText(""))
                && effect.path("cardFilterTags").size() > 0;
    }

    /**
     * An {@code AddRandomCard} with no filter, or a {@code CardSelection} Transform, both of
     * which draw from the character's favored pool ({@code AddRandomCardHandler},
     * {@code CardSelectionResolver#randomPool}).
     */
    private static boolean isUnfilteredRandomDraw(JsonNode effect) {
        String type = effect.path("type").asText("");
        if ("AddRandomCard".equals(type)) {
            return effect.path("cardFilterTags").size() == 0;
        }
        return "CardSelection".equals(type)
                && "Transform".equals(effect.path("cardSelectionEffect").asText(""));
    }

    private static boolean hasCreatedTag(JsonNode card) {
        for (JsonNode tag : card.path("tags")) {
            if ("Created".equals(tag.asText(""))) {
                return true;
            }
        }
        return false;
    }

    private static void addCreatedCardIds(
            JsonNode owner,
            LinkedHashSet<String> target,
            Map<String, String> idByReference
    ) {
        for (JsonNode effect : owner.path("effects")) {
            addReference(effect.path("cardID").asText("").trim(), target, idByReference);
        }
    }

    /** Cards Jump, Crystallize, Propulsion and Sadism play implicitly when their statuses trigger. */
    private static void addStatusFollowUpCardIds(
            JsonNode owner,
            LinkedHashSet<String> target,
            Map<String, String> idByReference
    ) {
        for (JsonNode effect : owner.path("effects")) {
            if (!"Status".equals(effect.path("type").asText(""))) {
                continue;
            }
            String followUp = switch (effect.path("statusType").asText("")) {
                case "Jump" -> "Atterrissage";
                case "Crystallize" -> "Recristallisation";
                case "Propulsion" -> "roquette";
                // Sadisme finit par devenir Sadisme III, qui joue Acharnement.
                case "Sadism", "SadismII", "SadismIII" -> "Acharnement";
                default -> null;
            };
            addReference(followUp, target, idByReference);
        }
    }

    private static void addReference(
            String reference,
            LinkedHashSet<String> target,
            Map<String, String> idByReference
    ) {
        if (reference == null) {
            return;
        }
        String trimmed = reference.trim();
        if (trimmed.isBlank()) {
            return;
        }
        String cardId = idByReference.get(trimmed);
        if (cardId == null) {
            String normalized = normalizedId(trimmed);
            cardId = idByReference.entrySet().stream()
                .filter(entry -> normalized.equals(normalizedId(entry.getKey())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(trimmed);
        }
        target.add(cardId);
        }

        private static String normalizedId(String reference) {
        String ascii = Normalizer.normalize(reference.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
        return ascii.replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }
}
