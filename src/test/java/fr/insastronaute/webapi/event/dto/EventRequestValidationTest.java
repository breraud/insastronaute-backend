package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventSource;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que le formulaire d'evenement exige, et ce qu'il laisse vide.
 *
 * La description courte etait requise. Beaucoup d'evenements se decrivent pourtant par leur
 * titre, leur lieu et leur horaire : l'exigence faisait ecrire « voir affiche » ou recopier
 * le titre, du texte qui occupe une place a l'ecran sans rien apprendre.
 */
class EventRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private EventRequest requestWithDescription(String shortDescription) {
        return new EventRequest(
                "Tournoi",
                shortDescription,
                null,
                null,
                EventSource.TOURNAMENT,
                Instant.parse("2026-09-01T10:00:00Z"),
                null, null, false, null, false, null,
                Set.of(), Set.of()
        );
    }

    @Test
    void anEventNeedsNoDescription() {
        assertThat(validator.validate(requestWithDescription(null))).isEmpty();
        assertThat(validator.validate(requestWithDescription(""))).isEmpty();
        assertThat(validator.validate(requestWithDescription("   "))).isEmpty();
    }

    @Test
    void aDescriptionStillCannotOverflowItsColumn() {
        assertThat(validator.validate(requestWithDescription("x".repeat(501))))
                .isNotEmpty();
    }

    /** Le titre, lui, reste requis : un evenement sans nom ne se lit nulle part. */
    @Test
    void anEventStillNeedsATitle() {
        EventRequest untitled = new EventRequest(
                "  ", "Description", null, null,
                EventSource.TOURNAMENT, Instant.parse("2026-09-01T10:00:00Z"),
                null, null, false, null, false, null, Set.of(), Set.of()
        );

        assertThat(validator.validate(untitled)).isNotEmpty();
    }
}
