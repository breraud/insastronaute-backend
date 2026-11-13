package fr.insastronaute.webapi.announcement;

import fr.insastronaute.webapi.announcement.domain.AnnouncementKind;
import fr.insastronaute.webapi.announcement.dto.AnnouncementDto;
import fr.insastronaute.webapi.announcement.dto.AnnouncementRequest;
import fr.insastronaute.webapi.announcement.repository.AnnouncementDismissalRepository;
import fr.insastronaute.webapi.announcement.repository.AnnouncementRepository;
import fr.insastronaute.webapi.announcement.service.AnnouncementService;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Une annonce peut renvoyer vers un onglet du site.
 *
 * <p>Vers un onglet, et rien d'autre : elle est écrite en administration et s'ouvre
 * chez tous les joueurs, donc elle ne doit pas pouvoir les emmener hors du site.</p>
 */
class AnnouncementLinkTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final AnnouncementService service = new AnnouncementService(
            announcements, mock(AnnouncementDismissalRepository.class), mock(UserRepository.class));

    private static AnnouncementRequest request(String link) {
        return new AnnouncementRequest("Le jeu rouvre", "Ce soir à 20h.", AnnouncementKind.INFO, true, null, null, link);
    }

    @Test
    void anAnnouncementKeepsTheTabItSendsTo() {
        when(announcements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AnnouncementDto created = service.create(request("/jeu"));

        assertThat(created.link()).isEqualTo("/jeu");
    }

    /** Le panneau envoie une chaîne vide quand on ne choisit rien : ce n'est pas un lien. */
    @Test
    void nothingChosenIsNoLinkAtAll() {
        when(announcements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(request("")).link()).isNull();
        assertThat(service.create(request("   ")).link()).isNull();
        assertThat(service.create(request(null)).link()).isNull();
    }

    @Test
    void aLinkOutOfTheSiteIsRefused() {
        for (String outside : List.of("https://ailleurs.example", "//ailleurs.example", "javascript:alert(1)", "jeu")) {
            assertThat(VALIDATOR.validate(request(outside)))
                    .as("lien refusé : %s", outside)
                    .isNotEmpty();
        }
    }

    @Test
    void aPathOfTheSiteIsAccepted() {
        for (String inside : List.of("/jeu", "/chasse-au-tresor", "/defis/envois", "")) {
            assertThat(VALIDATOR.validate(request(inside)))
                    .as("lien accepté : %s", inside)
                    .isEmpty();
        }
    }
}
