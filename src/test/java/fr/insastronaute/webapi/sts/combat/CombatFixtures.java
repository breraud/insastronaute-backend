package fr.insastronaute.webapi.sts.combat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CombatFixtures {

    static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000090");
    static final String PLAYER_ID = "player";
    static final String PLAYER_TEAM_ID = "player-team";
    static final String ENEMY_ID = "enemy";
    static final String ENEMY_TEAM_ID = "enemy-team";

    static final String ENERGIZE = "energize";
    static final String FORTIFY = "fortify";
    static final String INSIGHT = "insight";
    static final String JAB = "jab";
    static final String FINISHER = "finisher";
    static final String ENEMY_STRIKE = "enemy-strike";

    private static final List<CardInstance> PLAYER_DECK = List.of(
            card("player-energy-1", ENERGIZE),
            card("player-armor-1", FORTIFY),
            card("player-draw-1", INSIGHT),
            card("player-jab-1", JAB),
            card("player-finisher-1", FINISHER));
    private static final List<CardInstance> ENEMY_DECK =
            List.of(card("enemy-strike-1", ENEMY_STRIKE));

    private CombatFixtures() {}

    static CardCatalog catalog() {
        return new CardCatalog("scenario-v1", Map.of(
                ENERGIZE, selfCard(ENERGIZE, 0, EffectType.GAIN_ENERGY, 2),
                FORTIFY, selfCard(FORTIFY, 1, EffectType.ARMOR, 3),
                INSIGHT, selfCard(INSIGHT, 0, EffectType.DRAW, 1),
                JAB, enemyCard(JAB, 1, 4),
                FINISHER, enemyCard(FINISHER, 1, 8),
                ENEMY_STRIKE, enemyCard(ENEMY_STRIKE, 1, 2)));
    }

    static CombatSetup setup() {
        return new CombatSetup(
                COMBAT_ID,
                "scenario-v1",
                20260731L,
                List.of(
                        new CombatantSetup(
                                PLAYER_ID,
                                PLAYER_TEAM_ID,
                                ControllerType.HUMAN,
                                20,
                                3,
                                10,
                                0,
                                PLAYER_DECK),
                        new CombatantSetup(
                                ENEMY_ID,
                                ENEMY_TEAM_ID,
                                ControllerType.AI,
                                12,
                                1,
                                10,
                                1,
                                ENEMY_DECK)));
    }

    static List<CardInstance> playerDeck() {
        return PLAYER_DECK;
    }

    static List<CardInstance> enemyDeck() {
        return ENEMY_DECK;
    }

    private static CardDefinition selfCard(
            String id,
            int cost,
            EffectType effectType,
            int value
    ) {
        return new CardDefinition(
                id,
                cost,
                TargetingMode.SELF,
                List.of(new EffectDefinition(effectType, value, true)));
    }

    private static CardDefinition enemyCard(String id, int cost, int damage) {
        return new CardDefinition(
                id,
                cost,
                TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, damage, false)));
    }

    private static CardInstance card(String instanceId, String definitionId) {
        return new CardInstance(instanceId, definitionId);
    }
}
