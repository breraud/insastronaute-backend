package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.text.Normalizer;
import java.util.Locale;

public final class CardCatalog {

    private final String dataVersion;
    private final String character;
    private final Map<String, CardDefinition> cards;
    private final Map<String, CardDefinition> byReference;
    private final List<CardDefinition> randomPool;
    private final List<CardDefinition> followUpPool;

    public CardCatalog(String dataVersion, Map<String, CardDefinition> cards) {
        this(dataVersion, "", cards);
    }

    /**
     * @param character the run's character, which scopes the pool a random card is drawn from
     */
    public CardCatalog(String dataVersion, String character, Map<String, CardDefinition> cards) {
        if (dataVersion == null || dataVersion.isBlank()) {
            throw new IllegalArgumentException("dataVersion must not be blank");
        }
        Objects.requireNonNull(cards, "cards must not be null");
        cards.forEach((key, definition) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("card catalog keys must not be blank");
            }
            Objects.requireNonNull(definition, "card definitions must not be null");
            if (!key.equals(definition.id())) {
                throw new IllegalArgumentException(
                        "card catalog key '" + key
                                + "' does not match definition id '" + definition.id() + "'");
            }
        });
        this.dataVersion = dataVersion;
        this.character = character == null ? "" : character;
        this.cards = Collections.unmodifiableMap(new LinkedHashMap<>(cards));
        this.byReference = Collections.unmodifiableMap(referenceIndex(this.cards));
        this.randomPool = randomPool(this.cards, this.character);
        this.followUpPool = followUpPool(this.cards);
    }

    /**
     * Pool a follow-up draws from when it names no card, or a card that does not exist.
     *
     * <p>Unity's {@code FollowUpStatus} falls back to {@code STSCardDatabase.GetRandomCard()} in
     * that case: every card of the game but the Starting ones, whatever the character and
     * whatever the tags. It is not the pool an effect hands a card from ({@link #randomPool()}),
     * which is scoped to the character and keeps follow-ups and unobtainables out: a follow-up
     * plays the card at once rather than handing it over. Enemy moves are this engine's own
     * definitions, not cards of the game, and stay out.
     */
    private static List<CardDefinition> followUpPool(Map<String, CardDefinition> cards) {
        return cards.values().stream()
                .filter(card -> !"Starting".equals(card.favoredCharacter()))
                .filter(card -> !card.id().startsWith(EnemyMoveParser.MOVE_ID_PREFIX))
                .toList();
    }

    /** @see #followUpPool(Map) */
    public List<CardDefinition> followUpPool() {
        return followUpPool;
    }

    /**
     * Pool a card is drawn from when an effect creates or transforms one at random.
     *
     * <p>Cards created this way are the ones authored for the run's character and marked as
     * created; when the catalog holds none of those, any card may come up.
     */
    private static List<CardDefinition> randomPool(
            Map<String, CardDefinition> cards,
            String character
    ) {
        List<CardDefinition> favored = cards.values().stream()
                .filter(card -> !character.isBlank() && character.equals(card.favoredCharacter()))
                .filter(card -> card.hasTag(CardTag.CREATED))
                .filter(CardCatalog::mayBeDrawnAtRandom)
                .toList();
        if (!favored.isEmpty()) {
            return favored;
        }
        return cards.values().stream()
                .filter(CardCatalog::mayBeDrawnAtRandom)
                .toList();
    }

    /**
     * Whether a card may be handed to a player by an effect that draws one at random.
     *
     * <p>The fallback pool is the whole catalog, and the whole catalog is not a pool: it holds
     * cards that exist only as the second half of something else. A follow-up is meaningless
     * without the card that triggers it, an unobtainable card is marked as one precisely so it is
     * never handed out, and a Special is the payload of some other card's effect. Any of the
     * three arriving in a hand is a card the game never meant anyone to hold.
     */
    private static boolean mayBeDrawnAtRandom(CardDefinition card) {
        return !"Starting".equals(card.favoredCharacter())
            && card.rarity() != CardRarity.SPECIAL
                && !card.hasTag(CardTag.UNOBTAINABLE)
                && !card.hasTag(CardTag.FOLLOW_UP);
    }

    /**
     * Cards are reachable by id and by authored name, because effects reference either.
     *
     * <p>The id wins when a name collides with another card's id, so a reference can never be
     * captured by an unrelated card's display name.
     */
    private static Map<String, CardDefinition> referenceIndex(Map<String, CardDefinition> cards) {
        Map<String, CardDefinition> index = new LinkedHashMap<>();
        cards.values().forEach(card -> index.putIfAbsent(card.name(), card));
        cards.forEach(index::put);
        return index;
    }

    public String dataVersion() {
        return dataVersion;
    }

    public String character() {
        return character;
    }

    /** Same cards, scoped to the run's character so the random pools resolve. */
    public CardCatalog forCharacter(String character) {
        return new CardCatalog(dataVersion, character, cards);
    }

    /** @see #randomPool(Map, String) */
    public List<CardDefinition> randomPool() {
        return randomPool;
    }

    public Map<String, CardDefinition> cards() {
        return cards;
    }

    /** @return the card an effect or a move references by id or by authored name */
    public CardDefinition find(String reference) {
        if (reference == null) {
            return null;
        }
        CardDefinition exact = byReference.get(reference);
        if (exact != null) {
            return exact;
        }
        return cards.get(normalizedId(reference));
    }

    /**
     * Turns an authored display name into the id spelling used by exported cards.
     *
     * <p>Enemy statuses historically stored names ("Frappe de débris") while the card catalog
     * may have loaded that name through a differently decoded JSON source. The stable id remains
     * {@code frappe_de_debris}; using it as a fallback keeps those references working without
     * weakening exact id/name lookup or hiding real missing cards.
     */
    private static String normalizedId(String reference) {
        String ascii = Normalizer.normalize(reference.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return ascii.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public boolean contains(String reference) {
        return find(reference) != null;
    }

    public CardDefinition require(String reference) {
        CardDefinition card = find(reference);
        if (card == null) {
            throw new IllegalArgumentException("Unknown card id: " + reference);
        }
        return card;
    }
}
