package fr.insastronaute.webapi.onboarding.web;

import fr.insastronaute.webapi.onboarding.service.ModeratorGenderGateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * On ne modere pas sans avoir declare son genre.
 *
 * <p>Le champ decide des preuves sensibles qu'un moderateur peut ouvrir. Tant que personne ne
 * l'a renseigne, la colonne vaut NEUTRAL par defaut et trie la file au nom d'une valeur que
 * son porteur n'a pas choisie.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModeratorGenderGateFilterTest {

    /** Sonde du profil test, sous le prefixe garde. */
    private static final String MODERATION_PROBE = "/api/moderation/test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModeratorGenderGateService gateService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void aModeratorWhoHasChosenPassesThrough() throws Exception {
        when(gateService.hasChosenGender(any())).thenReturn(true);

        mockMvc.perform(get(MODERATION_PROBE).with(user("respo").roles("RESPOS_DEFIS")))
                .andExpect(status().isOk());
    }

    @Test
    void aModeratorWhoHasNotChosenIsRefusedWithAStableCode() throws Exception {
        when(gateService.hasChosenGender(any())).thenReturn(false);

        mockMvc.perform(get(MODERATION_PROBE).with(user("respo").roles("RESPOS_DEFIS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODERATOR_GENDER_REQUIRED"))
                .andExpect(jsonPath("$.path").value(MODERATION_PROBE));
    }

    /**
     * L'administrateur n'a pas de dispense.
     *
     * <p>La regle du genre ne le bride pas -- il voit toutes les preuves --, mais il modere
     * les memes que les autres. Une exception pour ADMIN ferait de la question une formalite
     * reservee aux subalternes.</p>
     */
    @Test
    void anAdministratorIsHeldToTheSameRule() throws Exception {
        when(gateService.hasChosenGender(any())).thenReturn(false);

        mockMvc.perform(get(MODERATION_PROBE).with(user("boss").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODERATOR_GENDER_REQUIRED"));
    }

    /** Le garde ne barre que la moderation : le reste du panneau reste ouvert. */
    @Test
    void theRestOfTheAdminApiIsUntouched() throws Exception {
        when(gateService.hasChosenGender(any())).thenReturn(false);

        mockMvc.perform(get("/api/admin/test").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    /**
     * Un joueur reste refuse pour la raison qui le concerne.
     *
     * <p>Il n'a rien a faire dans la moderation, et l'autorisation l'a deja dit. Repondre
     * « renseigne ton genre » le mettrait sur une piste qui ne le mene nulle part.</p>
     */
    @Test
    void aPlayerKeepsTheRefusalThatConcernsHim() throws Exception {
        when(gateService.hasChosenGender(any())).thenReturn(false);

        mockMvc.perform(get(MODERATION_PROBE).with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("MODERATOR_GENDER_REQUIRED"));
    }
}
