package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy;
import fr.insastronaute.webapi.sts.combat.ai.PatternEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.ai.SingleCardEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Qui joue les combattants que le serveur pilote dans une bataille multijoueur.
 *
 * <p>Un multijoueur peut en compter deux sortes, et elles ne se jouent pas de la meme facon.
 * Le boss d'un raid est un ennemi des donnees de jeu : il n'a pas de deck, il enchaine le motif
 * de coups de sa definition, exactement comme en run — d'ou {@link PatternEnemyPolicy}. Un
 * renfort qui tient un siege vide, lui, a un vrai deck de joueur en main, et un motif ne dirait
 * rien de ce qu'il faut en faire — d'ou {@link SingleCardEnemyPolicy}, qui joue la premiere carte
 * abordable sur une cible legale et finit son tour quand il n'y en a plus.
 *
 * <p>L'aiguillage se fait sur l'identite du combattant et non sur ce qu'il a en main : un renfort
 * dont la main est momentanement vide reste un renfort, et lui faire jouer un motif qu'il n'a pas
 * le ferait passer son tour pour de mauvaises raisons.
 */
public final class StsPvpEnemyPolicy implements EnemyPolicy {

    private final Set<String> patternCombatantIds;
    private final PatternEnemyPolicy patternPolicy;
    private final SingleCardEnemyPolicy deckPolicy;

    /**
     * @param patternCombatantIds les combattants qui jouent un motif — les boss — les autres
     *                            combattants pilotes jouant leur deck
     * @param patternPolicy       la politique de motif, deja chargee des motifs de ces combattants
     */
    public StsPvpEnemyPolicy(
            Set<String> patternCombatantIds,
            PatternEnemyPolicy patternPolicy,
            SingleCardEnemyPolicy deckPolicy
    ) {
        this.patternCombatantIds = Set.copyOf(Objects.requireNonNull(patternCombatantIds, "patternCombatantIds"));
        this.patternPolicy = Objects.requireNonNull(patternPolicy, "patternPolicy");
        this.deckPolicy = Objects.requireNonNull(deckPolicy, "deckPolicy");
    }

    /** La politique la plus simple : personne ne joue de motif, tout le monde joue son deck. */
    public static StsPvpEnemyPolicy decksOnly(
            PatternEnemyPolicy patternPolicy, SingleCardEnemyPolicy deckPolicy) {
        return new StsPvpEnemyPolicy(Set.of(), patternPolicy, deckPolicy);
    }

    @Override
    public CombatCommand nextCommand(CombatState state) {
        Objects.requireNonNull(state, "state");
        return policyFor(state.activeCombatantId()).nextCommand(state);
    }

    @Override
    public CombatCommand predictCommand(CombatState state, CombatantState actor) {
        Objects.requireNonNull(actor, "actor");
        return policyFor(actor.combatantId()).predictCommand(state, actor);
    }

    private EnemyPolicy policyFor(String combatantId) {
        return combatantId != null && patternCombatantIds.contains(combatantId)
                ? patternPolicy
                : deckPolicy;
    }

    /** Les motifs d'un ensemble de boss, sous la forme que {@link PatternEnemyPolicy} attend. */
    public static PatternEnemyPolicy patternPolicy(
            fr.insastronaute.webapi.sts.combat.catalog.CardCatalog catalog,
            Map<String, java.util.List<PatternEnemyPolicy.EnemyMoveNode>> patterns) {
        return new PatternEnemyPolicy(catalog, patterns);
    }
}
