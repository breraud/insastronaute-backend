package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ce que Chaos est, au catalogue.
 *
 * <p>Ce qu'il <em>fait</em> — plus aucun dégât, et tout le reste doublé vers l'autre bord — se
 * lit dans {@code CombatEngineChaoticTest} : cela se décide au moment où un effet part, et pas
 * dans la pose d'un statut.
 */
class ChaoticStatusTest {

    private static final int PERMANENT = -1;

    /** Un cadre, aucun nombre à lui, et rangé du côté des bienfaits. */
    @Test
    void isAFramedBuffCarryingNoNumbersOfItsOwn() {
        StatusInstance chaos = StatusCatalog.instantiate("Chaotic", 7, 7, null, 0);

        assertThat(StatusCatalog.of("Chaotic").category()).isEqualTo(StatusCategory.BUFF);
        assertThat(StatusCatalog.of("Chaotic").shape()).isEqualTo(StatusShape.FIXED);
        assertThat(StatusCatalog.of("Chaotic").framed()).isTrue();
        assertThat(chaos.value()).describedAs("aucune valeur écrite ne le regarde").isZero();
        assertThat(chaos.duration()).describedAs("il dure tant qu'on ne le retire pas")
                .isEqualTo(PERMANENT);
    }

    /** Il ne se mêle pas de la pose d'un statut : rien n'y double. */
    @Test
    void doesNothingToAStatusBeingApplied() {
        StatusOperations.Application application = StatusOperations.apply(
                List.of(),
                StatusCatalog.instantiate("Vuln", 0, 2, null, 0),
                List.of(new StatusInstance("Chaotic", 0, PERMANENT, null, 0)));

        assertThat(application.result().duration())
                .describedAs("le doublement est décidé sur l'effet, en amont")
                .isEqualTo(2);
    }
}
