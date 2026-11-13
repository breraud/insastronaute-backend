package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.AnimationType;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.EnemyMoveParser;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The names ITI gives the cards it tears off the other side.
 *
 * <p>The two authored examples are pinned first, because they are the specification: everything
 * else here is a rule read out of them. They also fix the two things the written rules leave
 * implicit -- that the compound's prefix comes from the first effect that is <em>not</em> the
 * blow, and that the damage mark closes the acronym block with no space before it.
 */
class StolenCardNamingTest {

    @Test
    void namesTheAuthoredSlashExample() {
        // "Annulame T": deals slash damage, dispels buffs, advances a turn.
        CardDefinition card = card(
                damage(6, AnimationType.SLASH),
                effect(EffectType.DISPEL_BUFF, 1),
                effect(EffectType.ADVANCE_TURN, 1));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Annulame T");
    }

    @Test
    void namesTheAuthoredLightExample() {
        // "Somnirayon ABADX": heavy light damage, delays a turn, dispels buffs, dispels debuffs.
        CardDefinition card = card(
                damage(24, AnimationType.LIGHT),
                effect(EffectType.DELAY_TURN, 1),
                effect(EffectType.DISPEL_BUFF, 1),
                effect(EffectType.DISPEL_DEBUFF, 1));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Somnirayon ABADX");
    }

    /**
     * The blow heads the name from wherever it sits, so a card that armours before it strikes is
     * still named after the strike.
     */
    @Test
    void theBlowTakesTheSubjectWhereverItSits() {
        CardDefinition card = card(
                effect(EffectType.ARMOR, 5),
                damage(4, AnimationType.FIRE),
                effect(EffectType.DRAW, 1));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Armoflamme P");
    }

    /** Two effects compound too, while the welded word stays short enough to say. */
    @Test
    void twoEffectsCompoundWhenTheWordStaysShort() {
        CardDefinition card = card(damage(4, AnimationType.SLASH), effect(EffectType.HEAL, 3));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Vitalame");
    }

    /**
     * Past that length the second effect drops back to its initial.
     *
     * <p>"Destructobouclier" is the whole reason the threshold exists: the compound is the better
     * name until it stops being sayable, and there is no room at two effects for initials to
     * carry it the way they do at three.
     */
    @Test
    void twoEffectsFallBackToAnInitialWhenTheWordRunsLong() {
        CardDefinition card = card(effect(EffectType.ARMOR, 5), status("Poison", 2));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Bouclier D");
    }

    /** And a compound right at the limit still welds, which is where the line currently sits. */
    @Test
    void aCompoundAtTheLimitStillWelds() {
        CardDefinition card = card(damage(7, AnimationType.MAGIC), status("Poison", 2));

        assertThat(StolenCardNaming.describe(card).name())
                .as("thirteen characters, and thirteen is allowed")
                .isEqualTo("Destructosort");
    }

    /** The threshold is on the welded word alone, so a damage mark never pushes it over. */
    @Test
    void theDamageMarkDoesNotCountTowardsTheLength() {
        CardDefinition card = card(damage(12, AnimationType.ICE), effect(EffectType.GAIN_ENERGY, 1));

        assertThat(StolenCardNaming.describe(card).name())
                .as("Dynamoglace is exactly at the limit; the + rides along")
                .isEqualTo("Dynamoglace+");
    }

    @Test
    void oneEffectIsJustItsWord() {
        assertThat(StolenCardNaming.describe(card(damage(4, AnimationType.ICE))).name())
                .isEqualTo("Glace");
    }

    /** With no blow at all, the first effect with a word takes the subject instead. */
    @Test
    void aCardThatNeverStrikesIsNamedAfterItsFirstEffect() {
        CardDefinition card = card(
                effect(EffectType.ARMOR, 5),
                effect(EffectType.HEAL, 3),
                effect(EffectType.DRAW, 1));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Vitabouclier P");
    }

    @Test
    void marksDamageOverTenAndOverTwentySeparately() {
        assertThat(StolenCardNaming.describe(card(damage(9, AnimationType.DEFAULT))).name())
                .as("nine is not ten or above")
                .isEqualTo("Frappe");
        assertThat(StolenCardNaming.describe(card(damage(11, AnimationType.DEFAULT))).name())
                .isEqualTo("Frappe+");
        assertThat(StolenCardNaming.describe(card(damage(21, AnimationType.DEFAULT))).name())
                .as("past the heavy mark a lone strike becomes its flavour's ultimate")
                .isEqualTo("Extermination");
    }

    /** A Multihit is marked on everything it will do, not on one of its hits. */
    @Test
    void aMultihitCountsEveryHit() {
        EffectDefinition fiveTimesFive = new EffectDefinition(
                EffectType.MULTIHIT, 5, false, null, 5, null, null, null, null, List.of(),
                false, false, null, 0, AnimationType.THUNDER);

        // Twenty-five in five hits is over the heavy mark, and a lone heavy blow is an ultimate:
        // what this pins is that the hits were counted at all, since one hit of five would not
        // have reached it.
        assertThat(StolenCardNaming.describe(card(fiveTimesFive)).name())
                .isEqualTo("Fulguration");
    }

    /** Damage and Multihit share the table, so the same animation gives the same word. */
    @Test
    void multihitBorrowsTheWordOfTheAnimationItShares() {
        EffectDefinition multihit = new EffectDefinition(
                EffectType.MULTIHIT, 2, false, null, 2, null, null, null, null, List.of(),
                false, false, null, 0, AnimationType.SLASH);

        assertThat(StolenCardNaming.describe(card(multihit)).name()).isEqualTo("Lame");
    }

    /**
     * Every flavour has an ultimate, and reaching it is one heavy blow and nothing else.
     *
     * <p>Magic had one long before the rest; the others were spelling out "FoudreX" and the like.
     */
    @Test
    void eachFlavourHasItsOwnUltimate() {
        assertThat(nameOf(damage(30, AnimationType.SLASH))).isEqualTo("Omnislash");
        assertThat(nameOf(damage(30, AnimationType.ICE))).isEqualTo("Snowgrave");
        assertThat(nameOf(damage(30, AnimationType.FIRE))).isEqualTo("Firegrave");
        assertThat(nameOf(damage(30, AnimationType.THUNDER))).isEqualTo("Fulguration");
        assertThat(nameOf(damage(30, AnimationType.LIGHT))).isEqualTo("Sidéral");
        assertThat(nameOf(damage(30, AnimationType.EXPLOSION_SMALL))).isEqualTo("Explosion");
        assertThat(nameOf(damage(30, AnimationType.EXPLOSION_LARGE))).isEqualTo("Méga Atomnium");
        assertThat(nameOf(damage(30, AnimationType.DEFAULT))).isEqualTo("Extermination");
        assertThat(nameOf(damage(30, AnimationType.MAGIC))).isEqualTo("Ultima");
    }

    /** An ultimate sweeps the field -- except the blade and the plain strike, which stay single. */
    @Test
    void mostUltimatesWidenToEveryEnemy() {
        assertThat(targetingOf(damage(30, AnimationType.ICE)))
                .isEqualTo(TargetingMode.ALL_ENEMIES);
        assertThat(targetingOf(damage(30, AnimationType.MAGIC)))
                .isEqualTo(TargetingMode.ALL_ENEMIES);
        assertThat(targetingOf(damage(30, AnimationType.EXPLOSION_LARGE)))
                .isEqualTo(TargetingMode.ALL_ENEMIES);
    }

    @Test
    void theBladeAndThePlainStrikeStayOnOneTarget() {
        assertThat(targetingOf(damage(30, AnimationType.SLASH)))
                .isEqualTo(TargetingMode.SINGLE_ENEMY);
        assertThat(targetingOf(damage(30, AnimationType.DEFAULT)))
                .isEqualTo(TargetingMode.SINGLE_ENEMY);
    }

    /** Below the heavy mark nothing is an ultimate, whatever it is flavoured as. */
    @Test
    void aBlowThatIsNotHeavyEnoughKeepsItsOrdinaryName() {
        assertThat(nameOf(damage(12, AnimationType.ICE))).isEqualTo("Glace+");
        assertThat(targetingOf(damage(12, AnimationType.ICE)))
                .isEqualTo(TargetingMode.SINGLE_ENEMY);
    }

    /** And an ultimate is one effect alone: give it company and it is an ordinary name again. */
    @Test
    void aHeavyBlowWithCompanyIsNoUltimate() {
        CardDefinition card = card(damage(30, AnimationType.ICE), effect(EffectType.DRAW, 1));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("CartoglaceX");
        assertThat(StolenCardNaming.describe(card).targeting())
                .isEqualTo(TargetingMode.SINGLE_ENEMY);
    }

    /** The two explosions have words of their own now, instead of borrowing the plain strike's. */
    @Test
    void theExplosionsSpeakForThemselves() {
        assertThat(nameOf(damage(4, AnimationType.EXPLOSION_SMALL))).isEqualTo("Blast");
        assertThat(nameOf(damage(4, AnimationType.EXPLOSION_LARGE))).isEqualTo("Explosion");
    }

    @Test
    void theExplosionsCarryTheirOwnPrefixAndInitials() {
        assertThat(StolenCardNaming.describe(card(
                damage(4, AnimationType.EXPLOSION_SMALL),
                effect(EffectType.ARMOR, 5),
                effect(EffectType.DRAW, 1))).name())
                .as("Armo + blast, then the draw's initial")
                .isEqualTo("Armoblast P");
        assertThat(StolenCardNaming.describe(card(
                effect(EffectType.ARMOR, 5),
                damage(4, AnimationType.EXPLOSION_LARGE),
                effect(EffectType.DRAW, 1))).name())
                .as("the blow still takes the subject, so Armo leads it")
                .isEqualTo("Armoexplosion P");
    }

    @Test
    void aHeavyMagicCardIsCalledUltimaRatherThanSortX() {
        assertThat(StolenCardNaming.describe(card(damage(30, AnimationType.MAGIC))).name())
                .isEqualTo("Ultima");
    }

    /** Ultima is for magic alone: a heavy magic card that also does something else is not one. */
    @Test
    void magicWithCompanyKeepsItsOrdinaryName() {
        CardDefinition card = card(damage(30, AnimationType.MAGIC), effect(EffectType.DRAW, 1));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("CartosortX");
    }

    /** Effects the table has no word for are invisible: they neither name nor count. */
    @Test
    void unspokenEffectsAreNotCounted() {
        CardDefinition card = card(
                damage(4, AnimationType.SLASH),
                effect(EffectType.DISCARD, 1),
                effect(EffectType.EXHAUST, 1),
                effect(EffectType.HEAL, 3));

        assertThat(StolenCardNaming.describe(card).name())
                .as("the discard and the exhaust are not effects the name can speak")
                .isEqualTo("Vitalame");
    }

    /** A card the table cannot speak at all keeps the name it came with. */
    @Test
    void aCardWithNothingToSayKeepsItsOwnName() {
        CardDefinition card = new CardDefinition(
                "purge", "Purge", "", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(effect(EffectType.DISCARD, 1)));

        assertThat(StolenCardNaming.describe(card).name()).isEqualTo("Purge");
    }

    /**
     * Un mouvement ecrit a meme la fiche de l'ennemi n'a pas de nom : sa definition porte pour
     * nom l'identifiant que le parseur lui a fabrique, et c'est cet identifiant qui s'affichait
     * sur la carte volee.
     */
    @Test
    void aMoveWithNoAuthoredNameIsCalledMaintenanceRatherThanItsGeneratedId() {
        String moveId = EnemyMoveParser.moveId("gobelin", 2);
        CardDefinition move = new CardDefinition(
                moveId, 0, CardType.NONE, TargetingMode.SELF, Set.of(),
                List.of(effect(EffectType.DISCARD, 1)));

        assertThat(StolenCardNaming.describe(move).name()).isEqualTo("Maintenance");
    }

    @Test
    void aCardThatStrikesIsAnAttackAndOneThatDoesNotIsASkill() {
        assertThat(StolenCardNaming.describe(card(damage(4, AnimationType.SLASH))).type())
                .isEqualTo(CardType.ATTACK);
        assertThat(StolenCardNaming.describe(card(effect(EffectType.ARMOR, 5))).type())
                .isEqualTo(CardType.SKILL);
    }

    @Test
    void mostCopiesCostOne() {
        CardDefinition card = card(damage(4, AnimationType.SLASH), effect(EffectType.HEAL, 3));

        assertThat(StolenCardNaming.describe(card).cost()).isEqualTo(1);
    }

    @Test
    void aCrowdedCopyCostsTwo() {
        CardDefinition card = card(
                effect(EffectType.ARMOR, 5),
                effect(EffectType.HEAL, 3),
                effect(EffectType.DRAW, 1),
                effect(EffectType.GAIN_ENERGY, 1));

        assertThat(StolenCardNaming.describe(card).cost()).isEqualTo(2);
    }

    @Test
    void aHeavyCopyWithCompanyCostsTwo() {
        CardDefinition card = card(damage(24, AnimationType.LIGHT), effect(EffectType.DRAW, 1));

        assertThat(StolenCardNaming.describe(card).cost()).isEqualTo(2);
    }

    /** Heavy on its own is not enough: a single blow, however hard, is still one point. */
    @Test
    void aHeavyCopyOnItsOwnStillCostsOne() {
        assertThat(StolenCardNaming.describe(card(damage(24, AnimationType.LIGHT))).cost())
                .isEqualTo(1);
    }

    /** A status is named by which way it cuts, so the same effect type gives two words. */
    @Test
    void aStatusIsNamedBuffOrDebuffByWhatItDoes() {
        CardDefinition strengthening = card(
                effect(EffectType.ARMOR, 5), status("Strength", 2), effect(EffectType.DRAW, 1));
        CardDefinition weakening = card(
                effect(EffectType.ARMOR, 5), status("Poison", 2), effect(EffectType.DRAW, 1));

        assertThat(StolenCardNaming.describe(strengthening).name()).isEqualTo("Hyperbouclier P");
        assertThat(StolenCardNaming.describe(weakening).name()).isEqualTo("Destructobouclier P");
    }



    private static String nameOf(EffectDefinition only) {
        return StolenCardNaming.describe(card(only)).name();
    }

    private static TargetingMode targetingOf(EffectDefinition only) {
        return StolenCardNaming.describe(card(only)).targeting();
    }

    private static CardDefinition card(EffectDefinition... effects) {
        return new CardDefinition(
                "source", "Carte source", "", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(effects));
    }

    private static EffectDefinition damage(int value, AnimationType animation) {
        return new EffectDefinition(
                EffectType.DAMAGE, value, false, null, 0, null, null, null, null, List.of(),
                false, false, null, 0, animation);
    }

    private static EffectDefinition effect(EffectType type, int value) {
        return new EffectDefinition(type, value, false);
    }

    private static EffectDefinition status(String statusType, int value) {
        return new EffectDefinition(EffectType.STATUS, value, false, statusType, 2, null, null);
    }
}
