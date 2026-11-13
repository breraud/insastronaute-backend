package fr.insastronaute.webapi.sts.multiplayer.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'interrupteur du mode PvP, vu des deux côtés.
 *
 * <p>Ce qui compte n'est pas que la disponibilité réponde, mais qu'elle réponde <em>quand le
 * mode est éteint</em> : c'est le seul moment où le client en a besoin, et c'est aussi celui où
 * toutes les autres routes PvP ont disparu.
 */
class StsPvpAvailabilityControllerTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class PvpOnByDefault {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ClientRegistrationRepository clientRegistrationRepository;

        @Test
        void availabilitySaysOpen() throws Exception {
            mockMvc.perform(get("/api/sts/pvp/availability").with(user("joueur").roles("STUDENT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));
        }

        /**
         * Et les routes du mode sont bien là. On ne regarde que l'absence de 404 : ce que le
         * profil répond ensuite dépend de la session, et ce n'est pas ce qui est en jeu ici.
         */
        @Test
        void theModeItselfIsReachable() throws Exception {
            int status = mockMvc.perform(
                            get("/api/sts/pvp/profile").with(user("joueur").roles("STUDENT")))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            assertThat(status).isNotEqualTo(404);
        }
    }

    @Nested
    @SpringBootTest(properties = "app.sts.pvp.enabled=false")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class PvpSwitchedOff {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ClientRegistrationRepository clientRegistrationRepository;

        /** La question survit à l'extinction : c'est elle qui fait disparaître le bouton. */
        @Test
        void availabilityStillAnswersAndSaysClosed() throws Exception {
            mockMvc.perform(get("/api/sts/pvp/availability").with(user("joueur").roles("STUDENT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        }

        /** Et le mode est vraiment fermé, pas seulement caché. */
        @Test
        void theModeIsGone() throws Exception {
            mockMvc.perform(get("/api/sts/pvp/profile").with(user("joueur").roles("STUDENT")))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/sts/pvp/battles/{battleId}", UUID.randomUUID())
                            .with(user("joueur").roles("STUDENT")))
                    .andExpect(status().isNotFound());
        }
    }
}
