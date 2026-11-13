package fr.insastronaute.webapi.sts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StsCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StsGameDataService gameDataService;

    @MockitoBean
    private CardService cardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void authenticatedUserCanReadEventsCatalog() throws Exception {
        when(gameDataService.latestVersion()).thenReturn("2026-07-10.1");
        when(gameDataService.events("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "events": [
                    {
                      "id": "event_campfire",
                      "title": "Campfire"
                    }
                  ]
                }
                """));

        mockMvc.perform(get("/api/sts/catalog/events").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].id").value("event_campfire"))
                .andExpect(jsonPath("$.events[0].title").value("Campfire"));
    }

    @Test
    void authenticatedUserCanReadEnemiesCatalog() throws Exception {
        when(gameDataService.latestVersion()).thenReturn("2026-07-10.1");
        when(gameDataService.enemies("2026-07-10.1")).thenReturn((ArrayNode) objectMapper.readTree("""
                [
                  {
                    "enemyId": "Slime",
                    "maxHp": 20
                  }
                ]
                """));

        mockMvc.perform(get("/api/sts/catalog/enemies").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enemyId").value("Slime"))
                .andExpect(jsonPath("$[0].maxHp").value(20));
    }

                @Test
                void authenticatedUserCanReadPinnedCardsCatalogVersion() throws Exception {
              String version = "2026-07-09.1";
              when(gameDataService.resolveVersion(version)).thenReturn(version);
              when(gameDataService.cards(version)).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-07-09.1","cards":[{"id":"old-card"}]}
                """));

              mockMvc.perform(get("/api/sts/catalog/cards")
                  .param("version", version)
                  .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataVersion").value(version))
                .andExpect(jsonPath("$.cards[0].id").value("old-card"));
                }

    @Test
    void authenticatedUserCanReadEnemyPoolCatalog() throws Exception {
        when(gameDataService.latestVersion()).thenReturn("2026-07-10.1");
        when(gameDataService.enemyPool("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "schemaVersion": 3,
                  "enemies": [
                    {
                      "displayName": "Slime group",
                      "enemyIds": ["Slime"],
                      "customUnityField": "preserved"
                    }
                  ]
                }
                """));

        mockMvc.perform(get("/api/sts/catalog/enemy-pool").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(3))
                .andExpect(jsonPath("$.enemies[0].displayName").value("Slime group"))
                .andExpect(jsonPath("$.enemies[0].enemyIds[0]").value("Slime"))
                .andExpect(jsonPath("$.enemies[0].customUnityField").value("preserved"));
    }

    @Test
    void authenticatedUserCanReadStsCardImages() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(cardService.findAllForSts()).thenReturn(List.of(new CardDto(
                cardId, "Game card", UUID.randomUUID(), "Game", true, false, 1,
                "https://s3.example.com/game.jpg", null, "https://s3.example.com/game-thumb.jpg",
                "image/jpeg", null, null
        )));

        mockMvc.perform(get("/api/sts/cardsImage").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cardId.toString()))
                .andExpect(jsonPath("$[0].imageUrl").value("https://s3.example.com/game.jpg"));
    }
}
