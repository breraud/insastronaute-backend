package fr.insastronaute.webapi.sts.combat.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a whole {@code dataVersion} and reports every incompatibility in one pass.
 *
 * <p>Deliberately never stops at the first failure: a data version has to be judged as a whole,
 * before deployment, rather than discovered to be broken when a player enters a combat.
 *
 * <p>Two different kinds of finding are reported, and they must not be conflated:
 * <ul>
 *   <li>the definition cannot be built at all, so the entry is <em>blocked</em>;</li>
 *   <li>the definition builds but the engine would lose or ignore part of what the data says,
 *       so the entry loads while still being unfaithful.</li>
 * </ul>
 */
public final class GameDataCompatibilityScanner {

    private static final java.util.Set<EffectType> CREATES_A_CARD = java.util.Set.of(
            EffectType.ADD_CARD_TO_HAND,
            EffectType.ADD_CARD_TO_DRAW_PILE,
            EffectType.ADD_CARD_TO_DISCARD_PILE);

    /**
     * Enemy fields the matrix stays silent about: either the engine reads them, or they are not
     * combat mechanics at all. Everything else is a gameplay difference worth naming.
     */
    private static final java.util.Set<String> IGNORED_ENEMY_FIELDS = java.util.Set.of(
            "id", "enemyName", "maxHP", "movePattern", "patternCardIds",
            "displayName", "rewardCardIds",
            "randomStart", "startingStatus", "startingStatusValue", "startingStatusDuration",
            "startingStatusInfo");

    private final CardCatalogParser cardParser = new CardCatalogParser();
    private final EnemyMoveParser enemyMoveParser = new EnemyMoveParser();

    public GameDataCompatibilityReport scan(JsonNode cardsRoot, JsonNode enemiesRoot) {
        String dataVersion = cardsRoot == null
                ? "unknown"
                : cardsRoot.path("dataVersion").asText("unknown");
        List<GameDataIssue> issues = new ArrayList<>();
        Map<String, CardDefinition> loadableCards = new LinkedHashMap<>();
        List<String> blockedCardIds = new ArrayList<>();

        scanCards(cardsRoot, issues, loadableCards, blockedCardIds);

        CardCatalog catalog = new CardCatalog(dataVersion, loadableCards);
        loadableCards.values().forEach(card -> reportDanglingCardReferences(
                GameDataIssue.Scope.CARD, card.id(), "cards (card '" + card.id() + "')",
                card, catalog, issues));
        List<String> loadableEnemyIds = new ArrayList<>();
        List<String> blockedEnemyIds = new ArrayList<>();
        scanEnemies(enemiesRoot, catalog, issues, loadableEnemyIds, blockedEnemyIds);

        return new GameDataCompatibilityReport(
                dataVersion,
                List.copyOf(loadableCards.keySet()),
                blockedCardIds,
                loadableEnemyIds,
                blockedEnemyIds,
                issues);
    }

    private void scanCards(
            JsonNode cardsRoot,
            List<GameDataIssue> issues,
            Map<String, CardDefinition> loadableCards,
            List<String> blockedCardIds
    ) {
        JsonNode cardsNode = cardsRoot == null ? null : cardsRoot.path("cards");
        if (cardsNode == null || !cardsNode.isArray()) {
            issues.add(new GameDataIssue(
                    GameDataIssue.Scope.CARD, "<root>", "cards", "must be an array"));
            return;
        }

        for (int index = 0; index < cardsNode.size(); index++) {
            JsonNode cardNode = cardsNode.get(index);
            String indexContext = "cards[" + index + "]";
            String cardId = cardNode.path("id").asText("").trim();
            String entryId = cardId.isBlank() ? indexContext : cardId;
            CardDefinition definition;
            try {
                definition = cardParser.parseCard(cardNode, indexContext);
            } catch (IllegalArgumentException failure) {
                blockedCardIds.add(entryId);
                issues.add(issue(GameDataIssue.Scope.CARD, entryId, indexContext, failure));
                continue;
            }
            if (loadableCards.putIfAbsent(definition.id(), definition) != null) {
                blockedCardIds.add(entryId);
                issues.add(new GameDataIssue(GameDataIssue.Scope.CARD, entryId,
                        indexContext + ".id", "duplicate id '" + definition.id() + "'"));
                continue;
            }
            reportUnresolvableSelections(
                    GameDataIssue.Scope.CARD, definition.id(), indexContext, definition, issues);
        }
    }

    private void reportUnresolvableSelections(
            GameDataIssue.Scope scope,
            String entryId,
            String path,
            CardDefinition definition,
            List<GameDataIssue> issues
    ) {
        long selectionCount = definition.effects().stream()
                .filter(effect -> effect.type() == EffectType.CARD_SELECTION)
                .count();
        if (selectionCount > 1) {
            issues.add(new GameDataIssue(scope, entryId, path + ".effects",
                    "several CardSelection effects on one definition are not resolvable yet"));
        }
        if (selectionCount > 0 && scope == GameDataIssue.Scope.ENEMY) {
            issues.add(new GameDataIssue(scope, entryId, path + ".effects",
                    "the server picks the cards for this CardSelection, where the game asks the"
                            + " player"));
        }
        for (int index = 0; index < definition.effects().size(); index++) {
            EffectDefinition effect = definition.effects().get(index);
            String effectPath = path + ".effects[" + index + "]";
            CardSelectionSupport.unsupportedReason(effect).ifPresent(reason -> issues.add(
                    new GameDataIssue(scope, entryId, effectPath, reason)));
        }
    }

    private void scanEnemies(
            JsonNode enemiesRoot,
            CardCatalog catalog,
            List<GameDataIssue> issues,
            List<String> loadableEnemyIds,
            List<String> blockedEnemyIds
    ) {
        if (enemiesRoot == null || !enemiesRoot.isArray()) {
            issues.add(new GameDataIssue(
                    GameDataIssue.Scope.ENEMY, "<root>", "enemies", "must be an array"));
            return;
        }

        for (int index = 0; index < enemiesRoot.size(); index++) {
            JsonNode enemyNode = enemiesRoot.get(index);
            String enemyId = enemyId(enemyNode, index);
            if (scanEnemy(enemyNode, enemyId, catalog, issues)) {
                loadableEnemyIds.add(enemyId);
            } else {
                blockedEnemyIds.add(enemyId);
            }
        }
    }

    /** @return {@code true} when every move of this enemy could be built */
    private boolean scanEnemy(
            JsonNode enemyNode,
            String enemyId,
            CardCatalog catalog,
            List<GameDataIssue> issues
    ) {
        JsonNode movePattern = enemyNode.path("movePattern");
        JsonNode patternCardIds = enemyNode.path("patternCardIds");
        if (!movePattern.isArray() && !patternCardIds.isArray()) {
            issues.add(new GameDataIssue(GameDataIssue.Scope.ENEMY, enemyId, "movePattern",
                    "enemy declares neither movePattern nor patternCardIds"));
            return false;
        }

        boolean buildable = true;
        for (int index = 0; index < patternCardIds.size(); index++) {
            String cardId = patternCardIds.get(index).asText("").trim();
            if (!catalog.contains(cardId)) {
                buildable = false;
                issues.add(new GameDataIssue(GameDataIssue.Scope.ENEMY, enemyId,
                        "patternCardIds[" + index + "]",
                        "references card '" + cardId + "' which is not loadable"));
            }
        }

        for (int index = 0; index < movePattern.size(); index++) {
            JsonNode move = movePattern.get(index);
            String movePath = "movePattern[" + index + "]";
            CardDefinition definition;
            try {
                definition = enemyMoveParser.parseMove(catalog, move, enemyId, index);
            } catch (IllegalArgumentException failure) {
                buildable = false;
                issues.add(issue(GameDataIssue.Scope.ENEMY, enemyId, movePath, failure));
                continue;
            }
            if (definition == null) {
                buildable = false;
                issues.add(new GameDataIssue(GameDataIssue.Scope.ENEMY, enemyId, movePath,
                        "move declares neither cardId nor effects"));
                continue;
            }
            reportDanglingCardReferences(
                    GameDataIssue.Scope.ENEMY, enemyId, movePath, definition, catalog, issues);
            if (move.path("cardId").asText("").isBlank()) {
                reportUnresolvableSelections(
                        GameDataIssue.Scope.ENEMY, enemyId, movePath, definition, issues);
            }
        }
        reportUnconsumedEnemyFields(enemyNode, enemyId, issues);
        return buildable;
    }

    /**
     * An enemy field the engine never reads is not a parse error, it is a silent gameplay
     * difference: starting statuses that never apply, an initial intent index that is ignored.
     * Naming them keeps that gap countable instead of invisible.
     */
    private void reportUnconsumedEnemyFields(
            JsonNode enemyNode,
            String enemyId,
            List<GameDataIssue> issues
    ) {
        java.util.Iterator<String> fields = enemyNode.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!IGNORED_ENEMY_FIELDS.contains(field)) {
                issues.add(new GameDataIssue(GameDataIssue.Scope.ENEMY, enemyId, field,
                        "the engine does not read this field yet"));
            }
        }
    }

    /**
     * An effect that creates a card names its definition by id. A reference the catalog cannot
     * resolve only fails once the effect actually resolves, mid-combat, so it belongs here.
     */
    private void reportDanglingCardReferences(
            GameDataIssue.Scope scope,
            String entryId,
            String path,
            CardDefinition definition,
            CardCatalog catalog,
            List<GameDataIssue> issues
    ) {
        for (int index = 0; index < definition.effects().size(); index++) {
            EffectDefinition effect = definition.effects().get(index);
            if (!CREATES_A_CARD.contains(effect.type())) {
                continue;
            }
            String cardId = effect.cardId();
            if (cardId == null || cardId.isBlank()) {
                issues.add(new GameDataIssue(scope, entryId, path + ".effects[" + index + "].cardID",
                        effect.type() + " does not name the card it creates"));
            } else if (!catalog.contains(cardId)) {
                issues.add(new GameDataIssue(scope, entryId, path + ".effects[" + index + "].cardID",
                        "creates card '" + cardId + "' which is not loadable"));
            }
        }
    }

    private static String enemyId(JsonNode enemyNode, int index) {
        String id = enemyNode.path("id").asText("").trim();
        if (!id.isBlank()) {
            return id;
        }
        String enemyName = enemyNode.path("enemyName").asText("").trim();
        return enemyName.isBlank() ? "enemies[" + index + "]" : enemyName;
    }

    private static GameDataIssue issue(
            GameDataIssue.Scope scope,
            String entryId,
            String fallbackPath,
            RuntimeException failure
    ) {
        String message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        int separator = message.indexOf(": ");
        if (failure instanceof CardCatalogValidationException && separator > 0) {
            return new GameDataIssue(scope, entryId,
                    message.substring(0, separator), message.substring(separator + 2));
        }
        return new GameDataIssue(scope, entryId, fallbackPath, message);
    }
}
