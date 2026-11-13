package fr.insastronaute.webapi.sts.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StsDebugCombatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    /**
     * Un joueur est refuse, drapeau leve ou non.
     *
     * <p>Ce test attendait 404 : le controleur ne s'expose que sous
     * {@code app.sts.debug.combat.enabled}, donc sa route n'existait pas dans la configuration
     * par defaut. C'etait vrai, et insuffisant - le jour ou le drapeau est leve, et le gabarit
     * de production le levait, la route redevenait ouverte a n'importe quel compte
     * authentifie. La regle qui la reserve au staff vit maintenant dans SecurityConfig, ou elle
     * ne depend plus d'un drapeau : le refus est un 403, et il tient dans les deux cas.
     */
    @Test
    void aPlayerIsRefusedWhateverTheFlagSays() throws Exception {
        mockMvc.perform(post("/api/sts/runs/{runId}/debug/combat", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enemyIds\":[\"slime\"]}")
                        .with(user("joueur").roles("STUDENT"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    /** Et pour un compte du staff, la route reste absente tant que le drapeau n'est pas leve. */
    @Test
    void theHandPickedCombatIsNotReachableWithTheDefaultConfiguration() throws Exception {
        mockMvc.perform(post("/api/sts/runs/{runId}/debug/combat", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enemyIds\":[\"slime\"]}")
                        .with(user("staff").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());
    }

    /**
     * La question de disponibilite, elle, repond toujours : c'est ce qui permet au client de
     * cacher le bouton au lieu de le decouvrir en lancant un combat.
     *
     * <p>Configuration par defaut, donc drapeau baisse : meme au staff, la reponse est non, et
     * elle dit lequel des deux verrous a ferme.
     */
    @Test
    void availabilityAnswersEvenWhenTheFlagIsDown() throws Exception {
        mockMvc.perform(get("/api/sts/debug/combat/availability")
                        .with(user("staff").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.available").value(false));
    }

    /** Un joueur ordinaire se voit refuser par le role, drapeau ou non. */
    @Test
    void availabilityRefusesAPlayerOnTheRoleAlone() throws Exception {
        mockMvc.perform(get("/api/sts/debug/combat/availability")
                        .with(user("joueur").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.available").value(false));
    }
}
