package fr.insastronaute.webapi.site.service;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traverse la vraie base (H2) plutot qu'un {@link SiteAvailabilityStore} mocke : c'est le seul
 * moyen de detecter une {@link org.hibernate.LazyInitializationException} sur l'association
 * paresseuse {@code updatedBy}, qui ne peut pas se manifester quand le store est simule.
 */
@SpringBootTest
@ActiveProfiles("test")
class SiteAvailabilityServiceIntegrationTest {

    @Autowired
    private SiteAvailabilityStore store;

    @Autowired
    private SiteAvailabilityService service;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void getReflectsTheActorWhoLastFlippedTheSwitchWithoutLazyInitFailure() {
        AppUser actor = new AppUser();
        actor.setKeycloakSub("sub-bob-" + System.nanoTime());
        actor.setEmail("bob@example.com");
        actor.setDisplayName("Bob");
        actor.setGender(Gender.NEUTRAL);
        actor = userRepository.saveAndFlush(actor);

        store.save(false, "Retour a 18h.", actor);

        assertThat(service.get().updatedBy()).isEqualTo("Bob");
    }
}
