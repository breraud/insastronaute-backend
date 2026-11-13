package fr.insastronaute.webapi.sts.multiplayer.controller;

import fr.insastronaute.webapi.sts.multiplayer.service.StsPvpAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les statistiques multijoueur sont réservées aux administrateurs.
 *
 * <p>Aucune règle dédiée ne les protège : {@code /api/admin/**} revient à ADMIN par défaut. Ce
 * test fige cette protection, parce qu'elle tient à l'ordre des règles de sécurité — une règle
 * plus générale ajoutée avant pourrait ouvrir la fiche d'un joueur, avec son adresse et ses decks,
 * sans faire échouer quoi que ce soit d'autre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StsPvpAdminAccessTest {

    private static final List<String> PATHS = List.of(
            "/api/admin/sts/pvp/characters/usage",
            "/api/admin/sts/pvp/players",
            "/api/admin/sts/pvp/players/" + UUID.randomUUID()
    );

    /** Des rôles de staff qui ont d'autres pages d'administration, mais pas celle-ci. */
    private static final List<String> OTHER_STAFF_ROLES = List.of(
            "RESPOS_DEFIS", "RESPO_CARTES", "MODERATOR", "STUDENT");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StsPvpAdminService adminService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void anAdministratorCanReadThem() throws Exception {
        for (String path : PATHS) {
            mockMvc.perform(get(path).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void otherStaffCannot() throws Exception {
        for (String role : OTHER_STAFF_ROLES) {
            for (String path : PATHS) {
                mockMvc.perform(get(path).with(user("staff").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }
    }
}
