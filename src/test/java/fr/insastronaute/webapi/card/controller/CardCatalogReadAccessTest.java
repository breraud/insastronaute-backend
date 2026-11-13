package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.service.CardCategoryService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.card.service.CardTypeService;
import fr.insastronaute.webapi.common.security.config.SecurityConfig;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le catalogue est lisible par les responsables defis, qui configurent des defis
 * automatiques designant une categorie de carte, et reste modifiable par les seuls
 * responsables cartes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardCatalogReadAccessTest {

    private static final List<String> READABLE_PATHS = List.of(
            "/api/admin/cards",
            "/api/admin/card-categories",
            "/api/admin/card-types"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService cardService;

    @MockitoBean
    private CardCategoryService categoryService;

    @MockitoBean
    private CardTypeService typeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void everyCatalogReaderCanListTheCatalogue() throws Exception {
        // La boucle ne verifie que le mecanisme : elle iterait tout aussi bien sur un
        // tableau ampute. Sans cette assertion sur la composition exacte, un role retire
        // par erreur perdrait son acces sans faire echouer aucun test de ce fichier.
        assertThat(SecurityConfig.CARD_CATALOG_READ_ROLES).containsExactlyInAnyOrder(
                "ADMIN",
                "RESPO_CARTES",
                "SOUS_RESPO_CARTES",
                "RESPOS_DEFIS",
                "SOUS_RESPO_DEFIS"
        );

        for (String role : SecurityConfig.CARD_CATALOG_READ_ROLES) {
            for (String path : READABLE_PATHS) {
                mockMvc.perform(get(path).with(user("staff").roles(role)))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void aChallengeRoleCannotWriteToTheCatalogue() throws Exception {
        mockMvc.perform(post("/api/admin/card-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}")
                        .with(user("defis").roles("RESPOS_DEFIS"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/card-types/{id}", "00000000-0000-0000-0000-000000000000")
                        .with(user("defis").roles("RESPOS_DEFIS"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    /**
     * Les instances physiques nomment leurs proprietaires : la lecture du catalogue
     * s'arrete au catalogue.
     */
    @Test
    void aChallengeRoleCannotReadThePhysicalInstances() throws Exception {
        mockMvc.perform(get("/api/admin/cards/instances").with(user("defis").roles("RESPOS_DEFIS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theArchiveExportStaysWithTheAdministrators() throws Exception {
        mockMvc.perform(get("/api/admin/cards/export").with(user("defis").roles("RESPOS_DEFIS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnrelatedStaffRoleIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/card-categories").with(user("goodies").roles("SOUS_RESPO_GOODIES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPlayerIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/card-categories").with(user("player").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }
}
