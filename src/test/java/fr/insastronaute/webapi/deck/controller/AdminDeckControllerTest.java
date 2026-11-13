package fr.insastronaute.webapi.deck.controller;

import fr.insastronaute.webapi.deck.domain.DeckKind;
import fr.insastronaute.webapi.deck.dto.DeckDetailDto;
import fr.insastronaute.webapi.deck.dto.DropTierDto;
import fr.insastronaute.webapi.deck.service.DeckService;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDeckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void reordersDropTiersForAdmins() throws Exception {
        UUID deckId = UUID.randomUUID();
        UUID rareTierId = UUID.randomUUID();
        when(deckService.reorderDropTiers(eq(deckId), any())).thenReturn(new DeckDetailDto(
                deckId,
                "Starter",
                10L,
                1,
                "https://s3.example.com/decks/starter.jpg",
                50.0,
                1.0,
                DeckKind.PACK,
                List.of(),
                List.of(new DropTierDto(rareTierId, "Rare", BigDecimal.TEN, null, null, List.of()))
        ));

        mockMvc.perform(put("/api/admin/decks/{deckId}/drop-tiers/order", deckId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tierIds": ["%s"] }
                                """.formatted(rareTierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dropTiers[0].id").value(rareTierId.toString()));
    }

    @Test
    void rejectsDropTierReorderingForNonAdmins() throws Exception {
        mockMvc.perform(put("/api/admin/decks/{deckId}/drop-tiers/order", UUID.randomUUID())
                        .with(user("player").roles("USER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tierIds\": [\"%s\"] }".formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesDeckForAdmins() throws Exception {
        UUID deckId = UUID.randomUUID();
        when(deckService.update(eq(deckId), any(), any())).thenReturn(new DeckDetailDto(
                deckId,
                "Updated starter",
                20L,
                2,
                "https://s3.example.com/decks/starter.jpg",
                50.0,
                1.0,
                DeckKind.PACK,
                List.of(),
                List.of()
        ));

        MockMultipartFile deckData = new MockMultipartFile(
                "deckData",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {"name":"Updated starter","price":20,"cardsPerPull":2,"dropTiers":[{"dropChancePercent":100,"cardIds":["%s"]}]}
                        """.formatted(UUID.randomUUID()).getBytes()
        );

        mockMvc.perform(multipart("/api/admin/decks/{deckId}", deckId)
                        .file(deckData)
                        .with(request -> { request.setMethod("PATCH"); return request; })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated starter"));
    }
}
