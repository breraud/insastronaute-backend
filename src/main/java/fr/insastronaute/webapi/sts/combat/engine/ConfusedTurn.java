package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Le tour d'un combattant que la Confusion mène à sa place.
 *
 * <p>Confusion ne faisait qu'égarer la cible d'une carte que son porteur choisissait encore. Elle
 * lui prend maintenant la main tout entière : ses cartes partent au hasard, une après l'autre,
 * jusqu'à ce que son énergie ne paie plus rien. Le porteur ne décide plus de rien — ni de ce qui
 * part, ni de sur qui, ni de quand cela s'arrête.
 *
 * <p>C'est le pendant humain de {@code PatternEnemyPolicy} : un tour joué par le serveur, une
 * commande à la fois, chacune sa révision, de sorte que les deux clients voient la main se vider
 * comme ils voient un ennemi dérouler son motif. {@link CombatOrchestrator} s'en sert exactement
 * là où il se sert d'une politique d'ennemi, ce qui la fait marcher en PvE comme en duel sans que
 * ni l'un ni l'autre service ait à le savoir.
 *
 * <p>Le tirage sort du flux du combat, jamais d'un hasard à lui : un combat rejoué doit se
 * dérouler deux fois pareil.
 */
public final class ConfusedTurn {

    /**
     * Le nombre de cartes qu'un tour confus peut jouer avant qu'on l'arrête de force.
     *
     * <p>« Jusqu'à court d'énergie » suppose que l'énergie finisse par manquer, ce qui n'est vrai
     * que des cartes qui coûtent. Une carte à zéro n'en dépense aucune, et une carte à zéro qui se
     * recopie en main s'en redonne autant qu'on en joue : la boucle n'aurait alors aucune raison
     * de s'arrêter, et elle tournerait ici, dans le serveur, sur la requête d'un joueur. Ce
     * plafond est ce qui garantit la terminaison ; ce n'est pas une règle de jeu, et une main
     * ordinaire ne devrait jamais l'atteindre.
     */
    public static final int MAX_PLAYS_PER_TURN = 12;

    /**
     * What a card costs right now, asked of the engine.
     *
     * <p>The catalogue price alone cannot answer: a "Coût nul" on the confused combatant makes
     * the card free, and a card made cheaper this turn is cheaper. Reading the raw price refused
     * plays the engine would have accepted for nothing.
     */
    @FunctionalInterface
    public interface CostOracle {
        int costOf(
                CombatState state,
                CombatantState actor,
                CardInstance card,
                CardDefinition definition);
    }

    private final CardCatalog catalog;
    private final CostOracle costs;

    public ConfusedTurn(CardCatalog catalog, CostOracle costs) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
    }

    /** Whether the reins have been taken from this combatant for the turn. */
    public static boolean isConfused(CombatantState actor) {
        return actor != null && actor.statuses().stream()
                .anyMatch(status -> "Confusion".equals(status.statusType()));
    }

    /**
     * The next card the confusion throws out, or the end of the turn when it has none left.
     *
     * @param playsThisTurn how many cards this confused turn has already thrown out
     */
    public CombatCommand nextCommand(CombatState state, CombatantState actor, int playsThisTurn) {
        if (playsThisTurn >= MAX_PLAYS_PER_TURN) {
            return endTurn(state, actor);
        }
        List<CardInstance> playable = playable(state, actor);
        if (playable.isEmpty()) {
            return endTurn(state, actor);
        }
        RandomState.RandomStep pick = state.random().nextInt(playable.size());
        CardInstance chosen = playable.get(pick.value());
        CardDefinition definition = definitionOf(chosen);
        return new PlayCardCommand(
                state.combatId(),
                state.revision(),
                actor.combatantId(),
                chosen.instanceId(),
                targets(state, actor, definition.targetingMode()));
    }

    /**
     * The cards in hand this confusion could throw out.
     *
     * <p>A card whose cost it cannot pay is left alone, and so is one with nobody to be played at.
     * So is a card carrying a CardSelection: choosing among the cards such an effect offers is a
     * decision, and a confusion makes none. It throws out what can be thrown out without asking
     * anybody anything, and leaves the rest in hand.
     */
    private List<CardInstance> playable(CombatState state, CombatantState actor) {
        return actor.piles().hand().stream()
                .filter(card -> {
                    CardDefinition definition = definitionOf(card);
                    if (definition == null || asksAQuestion(definition)) {
                        return false;
                    }
                    // Une carte à coût X se paie de ce qui reste, quoi qu'il reste.
                    if (!definition.xCost()
                            && costs.costOf(state, actor, card, definition) > actor.energy()) {
                        return false;
                    }
                    return hasSomewhereToLand(state, actor, definition.targetingMode());
                })
                .toList();
    }

    private CardDefinition definitionOf(CardInstance card) {
        CardDefinition authored = catalog.find(card.definitionId());
        if (authored == null) {
            return null;
        }
        try {
            return EffectiveCard.of(card, authored);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean asksAQuestion(CardDefinition definition) {
        return definition.effects().stream()
                .anyMatch(effect -> effect.type() == EffectType.CARD_SELECTION);
    }

    private static boolean hasSomewhereToLand(
            CombatState state, CombatantState actor, TargetingMode mode) {
        return switch (mode) {
            case NONE, SELF -> true;
            case ANY_PLAYER -> anyLiving(state, other -> other.teamId().equals(actor.teamId()));
            case SINGLE_ENEMY, ALL_ENEMIES, RANDOM_ENEMY ->
                    anyLiving(state, other -> !other.teamId().equals(actor.teamId()));
            case ALL_CHARACTERS -> anyLiving(state, other -> true);
        };
    }

    /**
     * Qui la carte vise, avant que la Confusion ne s'en mêle.
     *
     * <p>Sur une visée à cible unique ce choix ne survit pas : le moteur le retire au tirage,
     * puisque le lanceur est confus (CombatEngine.resolveTargets). Il n'est écrit ici que pour que
     * la commande soit recevable, et il suit la convention de la politique d'ennemi — le premier
     * vivant qui convient — afin qu'un combat rejoué reparte sur la même commande.
     */
    private static List<String> targets(
            CombatState state, CombatantState actor, TargetingMode mode) {
        return switch (mode) {
            case NONE -> List.of();
            case SELF -> List.of(actor.combatantId());
            case ANY_PLAYER -> firstLiving(state, other -> other.teamId().equals(actor.teamId()));
            case SINGLE_ENEMY, RANDOM_ENEMY ->
                    firstLiving(state, other -> !other.teamId().equals(actor.teamId()));
            case ALL_ENEMIES -> living(state, other -> !other.teamId().equals(actor.teamId()));
            case ALL_CHARACTERS -> living(state, other -> true);
        };
    }

    private static boolean anyLiving(CombatState state, Predicate<CombatantState> among) {
        return state.combatants().stream().anyMatch(other -> other.hp() > 0 && among.test(other));
    }

    private static List<String> living(CombatState state, Predicate<CombatantState> among) {
        return state.combatants().stream()
                .filter(other -> other.hp() > 0)
                .filter(among)
                .map(CombatantState::combatantId)
                .toList();
    }

    private static List<String> firstLiving(CombatState state, Predicate<CombatantState> among) {
        return living(state, among).stream().findFirst().map(List::of).orElse(List.of());
    }

    private static CombatCommand endTurn(CombatState state, CombatantState actor) {
        return new EndTurnCommand(state.combatId(), state.revision(), actor.combatantId());
    }
}
