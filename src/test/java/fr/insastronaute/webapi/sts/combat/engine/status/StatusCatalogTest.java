package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.junit.jupiter.api.Test;

/**
 * The taxonomy every dispel, steal and transfer now reads.
 *
 * <p>It replaces five short hardcoded lists that disagreed with each other and named statuses the
 * game does not have, which is why those effects matched almost nothing.
 */
class StatusCatalogTest {

    /**
     * Ce compte rend explicite le catalogue de statuts que le serveur sait traiter. Il tombe dès
     * qu'un statut est ajouté ou retiré sans que cet inventaire de compatibilité soit revu.
     *
     * <p>Les huit précédents sont Univers, Mégacristal, Fragmentation, Propulsion, Empathie,
     * Avenir, Innocence et Mesures d'envergure ; le dernier est Chaos.
     */
    /**
     * Ce que vaut un statut écrit sans nombres : la question que la mise en place d'un ennemi
     * pose avant de le lui donner. Un suivi écrit 0/0 est un suivi qui part à la première carte
     * et ne s'épuise pas — la façon ordinaire d'en écrire un — et il était jeté comme une case
     * vide, si bien qu'aucun ennemi n'a jamais déclenché le sien.
     */
    @Test
    void tellsWhichStatusesStillMeanSomethingWrittenWithoutNumbers() {
        assertThat(StatusCatalog.meansSomethingWithoutNumbers("CardFollowUp")).isTrue();
        assertThat(StatusCatalog.meansSomethingWithoutNumbers("AnyCardFollowUp")).isTrue();
        assertThat(StatusCatalog.meansSomethingWithoutNumbers("FieldTurnFollowUp")).isTrue();
        assertThat(StatusCatalog.meansSomethingWithoutNumbers("Imitate"))
                .describedAs("de forme FIXED : il ne porte aucun nombre par construction")
                .isTrue();

        assertThat(StatusCatalog.meansSomethingWithoutNumbers("Strength"))
                .describedAs("une Force de zero pour zero tour est une case vide")
                .isFalse();
        assertThat(StatusCatalog.meansSomethingWithoutNumbers("Vuln")).isFalse();
        assertThat(StatusCatalog.meansSomethingWithoutNumbers(null)).isFalse();
        assertThat(StatusCatalog.meansSomethingWithoutNumbers("  ")).isFalse();
    }

    @Test
    void knowsEveryStatusUnityDeclares() {
        // Sadisme II et III ne se posent pas, mais le serveur les porte : ils naissent du
        // premier stade (StatusEvolution) et le client doit les connaître pour les afficher.
        assertThat(StatusCatalog.count()).isEqualTo(120);
    }

    @Test
    void classifiesTheCombatStaples() {
        assertThat(StatusCatalog.of("Vuln").category()).isEqualTo(StatusCategory.DEBUFF);
        assertThat(StatusCatalog.of("Weakness").category()).isEqualTo(StatusCategory.DEBUFF);
        assertThat(StatusCatalog.of("Poison").category()).isEqualTo(StatusCategory.DEBUFF);
        assertThat(StatusCatalog.of("Regen").category()).isEqualTo(StatusCategory.BUFF);
        assertThat(StatusCatalog.of("Thorns").category()).isEqualTo(StatusCategory.BUFF);
        assertThat(StatusCatalog.of("Artifact").category()).isEqualTo(StatusCategory.SIGNED);
    }

    @Test
    void knowsFragileByTheNameTheGameActuallyApplies() {
        assertThat(StatusCatalog.find("Fragile")).isPresent();
        assertThat(StatusCatalog.find("Frail"))
                .describedAs("Frail is not a status of this game")
                .isEmpty();
    }

    @Test
    void readsTheSignOfAStatusThatWorksBothWays() {
        assertThat(StatusCatalog.isBuff(status("Strength", 2, -1))).isTrue();
        assertThat(StatusCatalog.isDebuff(status("Strength", 2, -1))).isFalse();
        assertThat(StatusCatalog.isDebuff(status("Strength", -2, -1))).isTrue();
        assertThat(StatusCatalog.isBuff(status("Strength", -2, -1))).isFalse();
        assertThat(StatusCatalog.of("Strength").expiresAt(0)).isTrue();
    }

    @Test
    void countsANeutralStatusAsNeitherBuffNorDebuff() {
        assertThat(StatusCatalog.isBuff(status("Targeting", 1, 2))).isFalse();
        assertThat(StatusCatalog.isDebuff(status("Targeting", 1, 2))).isFalse();
    }

    @Test
    void letsOnlyATrueEffectStripAStatusThatResists() {
        StatusInstance framed = status("Brand", 1, 2);

        assertThat(StatusCatalog.of("Brand").framed()).isTrue();
        assertThat(StatusCatalog.isDispellable(framed, false)).isFalse();
        assertThat(StatusCatalog.isDispellable(framed, true)).isTrue();
    }

    @Test
    void neverStripsAStatusWithTrueResistance() {
        StatusInstance stun = status("Stun", 1, 1);

        assertThat(StatusCatalog.of("Stun").goldFrame()).isTrue();
        assertThat(StatusCatalog.isDispellable(stun, true)).isFalse();
    }

    @Test
    void knowsWhichAuthoredNumberEachStatusReads() {
        assertThat(StatusCatalog.of("Poison").readsDuration()).isTrue();
        assertThat(StatusCatalog.of("Poison").readsValue()).isFalse();
        assertThat(StatusCatalog.of("Strength").readsValue()).isTrue();
        assertThat(StatusCatalog.of("Strength").readsDuration()).isFalse();
        assertThat(StatusCatalog.of("Thorns").readsValue()).isTrue();
        assertThat(StatusCatalog.of("Thorns").readsDuration()).isTrue();
    }

    @Test
    void givesPropulsionItsSingleTurnWhateverTheAuthoredDuration() {
        StatusInstance propulsion = StatusCatalog.instantiate("Propulsion", 0, 0, "", 0);

        assertThat(propulsion.duration()).isEqualTo(1);
    }

    @Test
    void keepsAnUnknownStatusRatherThanReclassifyingIt() {
        assertThat(StatusCatalog.find("Inventé")).isEmpty();
        assertThat(StatusCatalog.of("Inventé").category()).isEqualTo(StatusCategory.NEUTRAL);
        assertThat(StatusCatalog.isBuff(status("Inventé", 3, 2))).isFalse();
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, "", 0);
    }
}
