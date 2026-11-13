package fr.insastronaute.webapi.game.controller;

import fr.insastronaute.webapi.game.dto.GameAssetReleaseDto;
import fr.insastronaute.webapi.game.service.GameAssetService;
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

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameAssetService gameAssetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void activeGameAssetsArePublic() throws Exception {
        when(gameAssetService.getActiveRelease()).thenReturn(new GameAssetReleaseDto(
                "2026.1.0",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/index.html",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/Build/game.loader.js",
                "https://s3.example.com/insastronaute-public/game/2026.1.0/asset-manifest.json",
                308,
                42_000_000L,
                Instant.parse("2026-07-01T10:00:00Z")
        ));

        mockMvc.perform(get("/api/public/game"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.version").value("2026.1.0"))
                .andExpect(jsonPath("$.baseUrl").value("https://s3.example.com/insastronaute-public/game/2026.1.0/"))
                .andExpect(jsonPath("$.indexUrl").value("https://s3.example.com/insastronaute-public/game/2026.1.0/index.html"))
                .andExpect(jsonPath("$.manifestUrl").value("https://s3.example.com/insastronaute-public/game/2026.1.0/asset-manifest.json"));
    }
}
