package fr.insastronaute.webapi.game.controller;

import fr.insastronaute.webapi.game.dto.GameAssetReleaseDto;
import fr.insastronaute.webapi.game.service.GameAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGameAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameAssetService gameAssetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanPublishGameAssets() throws Exception {
        when(gameAssetService.publish(eq("2026.1.0"), anyList())).thenReturn(new GameAssetReleaseDto(
                "2026.1.0",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/index.html",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/Build/game.loader.js",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/asset-manifest.json",
                5,
                100L,
                Instant.parse("2026-07-01T10:00:00Z")
        ));

        mockMvc.perform(multipart("/api/admin/game/assets")
                        .file(gameFile("public/game/index.html"))
                        .file(gameFile("public/game/Build/game.loader.js"))
                        .param("version", "2026.1.0")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("2026.1.0"));

        verify(gameAssetService).publish(eq("2026.1.0"), anyList());
    }

    @Test
    void nonAdminCannotPublishGameAssets() throws Exception {
        mockMvc.perform(multipart("/api/admin/game/assets")
                        .file(gameFile("public/game/index.html"))
                        .param("version", "2026.1.0")
                        .with(user("student").roles("STUDENT"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile gameFile(String path) {
        return new MockMultipartFile(
                "files",
                path,
                MediaType.TEXT_PLAIN_VALUE,
                new byte[]{1}
        );
    }

    /**
     * Les bornes viennent de la configuration appliquee, pas d'une copie.
     *
     * <p>Elles se surchargent par variable d'environnement, et un environnement les a
     * deja eues bien plus basses que ce que le code declare. Un chiffre fige cote panel
     * annoncerait une limite fausse des que l'ecart reapparait.</p>
     */
    @Test
    void exposesTheMultipartLimitsActuallyInForce() throws Exception {
        mockMvc.perform(get("/api/admin/game/assets/limits").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxFileSizeBytes").isNumber())
                .andExpect(jsonPath("$.maxRequestSizeBytes").isNumber())
                .andExpect(jsonPath("$.maxPartCount").isNumber());
    }
}
