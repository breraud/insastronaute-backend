package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Stellaire, Solaire, Lunaire, Critique et la Distorsion temporelle, jouées pour de vrai. */
class CombatEngineCelestialStatusesTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final int HERO_HP = 60;
    private static final int ENEMY_HP = 200;
    private static final int PERMANENT = -1;
    private static final long ENEMY_TICK = 100;
    private static final int ENEMY_DELAY = 40;

    // --- Stellaire -----------------------------------------------------------------------------

    /** Cinq fois un point : la grosse frappe est ramenée à sa plus simple expression. */
    @Test
    void astraPlaysTheCardFiveTimesForOneEach() {
        CombatResult result = play(damage(12), astra());

        assertThat(ENEMY_HP - enemy(result).hp()).isEqualTo(5);
    }

    @Test
    void theSameCardIsWholeWithoutAstra() {
        assertThat(ENEMY_HP - enemy(play(damage(12), none())).hp()).isEqualTo(12);
    }

    /** La potence d'un statut posé y passe comme le reste. */
    @Test
    void astraBringsAStatusPotencyDownToOne() {
        CombatResult result = play(statusCard("Strength", 5, PERMANENT), astra());

        assertThat(potencyOf(result, "Strength"))
                .describedAs("cinq fois 1, et non cinq fois 5")
                .isEqualTo(5);
    }

    /**
     * Et sa durée aussi : c'était ce qui manquait, une Vulnérabilité durait ses trois tours
     * pleins, cinq fois de suite.
     *
     * <p>Chaque pose ne vaut plus qu'un tour, et les durées s'empilent : cinq poses d'un tour
     * font cinq tours, là où cinq poses de trois en auraient fait quinze.
     */
    @Test
    void astraBringsEachApplicationOfAStatusDurationDownToOne() {
        CombatResult result = play(statusCard("Vuln", 0, 3), astra());

        assertThat(durationOf(result, "Vuln"))
                .describedAs("cinq fois un tour, et non cinq fois trois")
                .isEqualTo(5);
    }

    @Test
    void theSameStatusKeepsItsDurationWithoutAstra() {
        assertThat(durationOf(play(statusCard("Vuln", 0, 3), none()), "Vuln")).isEqualTo(3);
    }

    /** Pincé, pas écrit : un malus garde son signe, et un permanent reste permanent. */
    @Test
    void astraKeepsTheSignAndLeavesAPermanentPermanent() {
        CombatResult result = play(statusCard("Strength", -4, PERMANENT), astra());

        assertThat(potencyOf(result, "Strength"))
                .describedAs("-1 cinq fois, et non +1")
                .isEqualTo(-5);
        assertThat(durationOf(result, "Strength"))
                .describedAs("-1 est l'écriture du permanent, pas un compte")
                .isEqualTo(PERMANENT);
    }

    // --- Solaire -------------------------------------------------------------------------------

    @Test
    void solHealsHalfOfWhatWasDealt() {
        CombatResult result = play(damage(10), sol());

        assertThat(hero(result).hp()).isEqualTo(HERO_HP - WOUND + 5);
    }

    @Test
    void nothingIsHealedWithoutSol() {
        assertThat(hero(play(damage(10), none())).hp()).isEqualTo(HERO_HP - WOUND);
    }

    // --- Lunaire -------------------------------------------------------------------------------

    /** L'armure ne l'arrête pas. */
    @Test
    void lunaGoesThroughArmour() {
        CombatResult result = play(damage(10), luna(), 20, List.of());

        assertThat(ENEMY_HP - enemy(result).hp()).isEqualTo(10);
    }

    @Test
    void armourStopsTheSameBlowWithoutLuna() {
        assertThat(ENEMY_HP - enemy(play(damage(10), none(), 20, List.of())).hp())
                .isZero();
    }

    /** Et la réduction de dégâts de la cible non plus. */
    @Test
    void lunaIgnoresTheTargetsDamageReduction() {
        List<StatusInstance> halving =
                List.of(new StatusInstance("DamageReduction", 50, PERMANENT, null, 0));

        assertThat(ENEMY_HP - enemy(play(damage(10), luna(), 0, halving)).hp()).isEqualTo(10);
        assertThat(ENEMY_HP - enemy(play(damage(10), none(), 0, halving)).hp()).isEqualTo(5);
    }

    /** Ce qui aggrave le coup reste : ignorer les réductions n'est pas ignorer la cible. */
    @Test
    void lunaKeepsWhatMakesTheBlowWorseForTheTarget() {
        List<StatusInstance> vulnerable =
                List.of(new StatusInstance("Vuln", 0, 3, null, 0));

        assertThat(ENEMY_HP - enemy(play(damage(10), luna(), 0, vulnerable)).hp())
                .describedAs("Vulnérable majore encore de moitié")
                .isEqualTo(15);
    }

    // --- Critique ------------------------------------------------------------------------------

    @Test
    void criticalDoublesTheBlowAndSpendsOneCharge() {
        CombatResult result = play(damage(10), critical(2));

        assertThat(ENEMY_HP - enemy(result).hp()).isEqualTo(20);
        assertThat(chargesOf(result, "Critical")).isEqualTo(1);
    }

    /** Une carte qui ne blesse personne ne coûte aucune charge. */
    @Test
    void aCardThatWoundsNobodySpendsNoCharge() {
        CardDefinition armourCard = new CardDefinition(
                "card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 5, true)));

        assertThat(chargesOf(play(armourCard, critical(2)), "Critical")).isEqualTo(2);
    }

    // --- Distorsion temporelle -----------------------------------------------------------------

    /** Le tour de quelqu'un recule, d'au plus la moitié de son délai. */
    @Test
    void timeDistortionPushesSomebodysNextTurnBack() {
        CombatResult result = play(damage(1), timeDistortion());

        long moved = tickOf(result, ENEMY) - ENEMY_TICK;
        assertThat(moved)
                .describedAs("soit l'ennemi a reculé, soit c'est le lanceur qui a pris")
                .isBetween(0L, (long) ENEMY_DELAY * 50 / 100);
        assertThat(tickOf(result, ENEMY) + tickOf(result, HERO))
                .describedAs("quelqu'un a forcément reculé")
                .isGreaterThan(ENEMY_TICK);
    }

    @Test
    void nothingMovesWithoutTheDistortion() {
        CombatResult result = play(damage(1), none());

        assertThat(tickOf(result, ENEMY)).isEqualTo(ENEMY_TICK);
    }

    /** Le tirage sort du flux du combat : deux fois le même état, deux fois la même distorsion. */
    @Test
    void theDistortionReplaysIdentically() {
        assertThat(tickOf(play(damage(1), timeDistortion()), ENEMY))
                .isEqualTo(tickOf(play(damage(1), timeDistortion()), ENEMY));
    }

    // --- montage -------------------------------------------------------------------------------

    /** Le héros part blessé, sans quoi un soin n'aurait nulle part où se voir. */
    private static final int WOUND = 20;

    private static List<StatusInstance> none() {
        return List.of();
    }

    private static List<StatusInstance> astra() {
        return List.of(new StatusInstance("Astra", 0, PERMANENT, null, 0));
    }

    private static List<StatusInstance> sol() {
        return List.of(new StatusInstance("Sol", 0, 3, null, 0));
    }

    private static List<StatusInstance> luna() {
        return List.of(new StatusInstance("Luna", 0, 3, null, 0));
    }

    private static List<StatusInstance> critical(int charges) {
        return List.of(new StatusInstance("Critical", charges, PERMANENT, null, 0));
    }

    private static List<StatusInstance> timeDistortion() {
        return List.of(new StatusInstance("TimeDistortion", 0, PERMANENT, null, 0));
    }

    private static int chargesOf(CombatResult result, String statusType) {
        return hero(result).statuses().stream()
                .filter(status -> statusType.equals(status.statusType()))
                .mapToInt(StatusInstance::value)
                .findFirst()
                .orElse(0);
    }

    private static CombatantState hero(CombatResult result) {
        return combatant(result, HERO);
    }

    private static CombatantState enemy(CombatResult result) {
        return combatant(result, ENEMY);
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static long tickOf(CombatResult result, String combatantId) {
        return result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .mapToLong(TimelineEntry::readyAtTick)
                .min()
                .orElseThrow();
    }

    private static CardDefinition statusCard(String statusType, int value, int duration) {
        return new CardDefinition(
                "card", 1, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.STATUS, value, false, statusType, duration, null, null)));
    }

    private static int potencyOf(CombatResult result, String statusType) {
        return enemy(result).statuses().stream()
                .filter(status -> statusType.equals(status.statusType()))
                .mapToInt(StatusInstance::value)
                .findFirst()
                .orElseThrow();
    }

    private static int durationOf(CombatResult result, String statusType) {
        return enemy(result).statuses().stream()
                .filter(status -> statusType.equals(status.statusType()))
                .mapToInt(StatusInstance::duration)
                .findFirst()
                .orElseThrow();
    }

    private static CardDefinition damage(int amount) {
        return new CardDefinition(
                "card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, amount)));
    }

    private static CombatResult play(CardDefinition definition, List<StatusInstance> heroStatuses) {
        return play(definition, heroStatuses, 0, List.of());
    }

    private static CombatResult play(
            CardDefinition definition,
            List<StatusInstance> heroStatuses,
            int enemyArmor,
            List<StatusInstance> enemyStatuses
    ) {
        CardInstance card = new CardInstance("played", definition.id());

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, HERO_HP, HERO_HP - WOUND, 0, 3, 3, 20,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                heroStatuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, ENEMY_HP, ENEMY_HP, enemyArmor, 0, 0,
                ENEMY_DELAY,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                enemyStatuses, List.of(), null, 0);

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(17),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, ENEMY_TICK, 1)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        return engine.handle(state,
                new PlayCardCommand(COMBAT_ID, 7, HERO, card.instanceId(),
                        definition.targetingMode() == TargetingMode.SELF
                                ? List.of(HERO)
                                : List.of(ENEMY)));
    }
}
