package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardGrouping;
import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDeckCategoryDto;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.dto.CardInstanceDto;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService cardService;

    @MockitoBean
    private CardInstanceService cardInstanceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void authenticatedUserCanGetCardCover() throws Exception {
        when(cardService.getCover()).thenReturn(new CardDto(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "Card cover",
                null,
                "COVER",
                true,
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                null,
                "https://s3.example.com/cards/default/thumbnail.jpg",
                "image/jpeg",
                null,
                null
        ));

        mockMvc.perform(get("/api/cards/cover")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000000"))
                .andExpect(jsonPath("$.imageUrl").value("https://s3.example.com/cards/default/original.jpg"));

        verify(cardService).getCover();
    }

    @Test
    void authenticatedUserCanGetDeck() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(cardService.getDeck("player-sub", CardCollectionType.VIRTUAL, CardGrouping.RANK, CardCollectionScope.PERSONAL)).thenReturn(new CardDeckDto(
                1,
                2,
                List.of(new CardDeckCategoryDto(
                        categoryId,
                        "Légendaire",
                        "https://s3.example.com/card-categories/legendary/original.jpg",
                        "https://s3.example.com/card-categories/legendary/thumbnail.jpg",
                        1,
                        2,
                        List.of(new CardDeckCardDto(
                                cardId,
                                "Course au classement",
                                categoryId,
                                "Légendaire",
                                null,
                                null,
                                true,
                                1,
                                0,
                                "https://s3.example.com/public/cards/original.jpg",
                                "https://s3.example.com/public/cards/thumbnail.jpg",
                                100,
                                15,
                                1,
                                100L
                        ))
                ))
        ));

        mockMvc.perform(get("/api/cards/deck")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.categories[0].category").value("Légendaire"))
                .andExpect(jsonPath("$.categories[0].imageUrl")
                        .value("https://s3.example.com/card-categories/legendary/original.jpg"))
                .andExpect(jsonPath("$.categories[0].cards[0].id").value(cardId.toString()))
                .andExpect(jsonPath("$.categories[0].cards[0].quantity").value(1))
                .andExpect(jsonPath("$.categories[0].cards[0].thumbnailUrl").value("https://s3.example.com/public/cards/thumbnail.jpg"))
                .andExpect(jsonPath("$.categories[0].cards[0].firstAcquisitionPoints").value(100))
                .andExpect(jsonPath("$.categories[0].cards[0].duplicatePoints").value(15));

        verify(cardService).getDeck("player-sub", CardCollectionType.VIRTUAL, CardGrouping.RANK, CardCollectionScope.PERSONAL);
    }

    @Test
    void authenticatedUserCanRequestThePhysicalTeamDeck() throws Exception {
        when(cardService.getDeck(
                "player-sub", CardCollectionType.PHYSICAL, CardGrouping.TYPE, CardCollectionScope.TEAM
        )).thenReturn(new CardDeckDto(0, 0, List.of(), CardCollectionScope.TEAM, null, 0L));

        mockMvc.perform(get("/api/cards/deck")
                        .param("collectionType", "PHYSICAL")
                        .param("groupBy", "type")
                        .param("scope", "TEAM")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TEAM"))
                .andExpect(jsonPath("$.teamScore").value(0));

        verify(cardService).getDeck(
                "player-sub", CardCollectionType.PHYSICAL, CardGrouping.TYPE, CardCollectionScope.TEAM
        );
    }

    @Test
    void authenticatedUserCanGetPhysicalDeck() throws Exception {
        when(cardService.getDeck("player-sub", CardCollectionType.PHYSICAL, CardGrouping.RANK, CardCollectionScope.PERSONAL)).thenReturn(new CardDeckDto(
                0,
                0,
                List.of()
        ));

        mockMvc.perform(get("/api/cards/deck")
                        .param("collectionType", "PHYSICAL")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCount").value(0));

        verify(cardService).getDeck("player-sub", CardCollectionType.PHYSICAL, CardGrouping.RANK, CardCollectionScope.PERSONAL);
    }

    @Test
    void authenticatedUserCanClaimPhysicalCardInstance() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(cardInstanceService.claim(eq("player-sub"), eq("ABCDE-FGHJK-MNPQR"))).thenReturn(new CardInstanceDto(
                instanceId,
                cardId,
                "Carte",
                "ABCDE-FGHJK-MNPQR",
                userId,
                "Player",
                "player@example.com",
                null,
                null,
                null,
                false
        ));

        mockMvc.perform(post("/api/cards/instances/claim")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicId": "ABCDE-FGHJK-MNPQR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.ownerUserId").value(userId.toString()));

        verify(cardInstanceService).claim("player-sub", "ABCDE-FGHJK-MNPQR");
    }

    @Test
    void studentLimitedCannotGetPhysicalDeck() throws Exception {
        mockMvc.perform(get("/api/cards/deck")
                        .param("collectionType", "PHYSICAL")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")).idToken(token -> token.subject("limited-sub"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
    }

    @Test
    void studentLimitedCannotRequestPhysicalTeamDeck() throws Exception {
        mockMvc.perform(get("/api/cards/deck")
                        .param("scope", "TEAM")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")).idToken(token -> token.subject("limited-sub"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
    }

    @Test
    void studentLimitedCannotClaimPhysicalCardInstance() throws Exception {
        mockMvc.perform(post("/api/cards/instances/claim")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")).idToken(token -> token.subject("limited-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicId": "ABCDE-FGHJK-MNPQR"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
    }

    @Test
    void studentLimitedCanGetVirtualDeck() throws Exception {
        when(cardService.getDeck("limited-sub", CardCollectionType.VIRTUAL, CardGrouping.RANK, CardCollectionScope.PERSONAL))
                .thenReturn(new CardDeckDto(0, 0, List.of()));

        mockMvc.perform(get("/api/cards/deck")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")).idToken(token -> token.subject("limited-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCount").value(0));

        verify(cardService).getDeck("limited-sub", CardCollectionType.VIRTUAL, CardGrouping.RANK, CardCollectionScope.PERSONAL);
    }

    @Test
    void playerListsTheirCopiesOfACard() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(cardInstanceService.findMine("player-sub", cardId)).thenReturn(List.of(
                new fr.insastronaute.webapi.card.dto.OwnedCardInstanceDto("ABCDE-FGHJK-MNPQR", java.time.Instant.parse("2026-09-20T10:00:00Z"))));

        mockMvc.perform(get("/api/cards/{cardId}/instances/mine", cardId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value("ABCDE-FGHJK-MNPQR"))
                .andExpect(jsonPath("$[0].assignedAt").value("2026-09-20T10:00:00Z"));
    }

    @Test
    void playerReleasesADuplicate() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(cardInstanceService.releaseMine("player-sub", "ABCDE-FGHJK-MNPQR")).thenReturn(
                new fr.insastronaute.webapi.card.dto.ReleasedCardInstanceDto(cardId, "Carte", "ABCDE-FGHJK-MNPQR", 1));

        mockMvc.perform(post("/api/cards/instances/release")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicId": "ABCDE-FGHJK-MNPQR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId.toString()))
                .andExpect(jsonPath("$.publicId").value("ABCDE-FGHJK-MNPQR"))
                .andExpect(jsonPath("$.remainingQuantity").value(1));

        verify(cardInstanceService).releaseMine("player-sub", "ABCDE-FGHJK-MNPQR");
    }

    @Test
    void releasingRequiresACode() throws Exception {
        mockMvc.perform(post("/api/cards/instances/release")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentLimitedCannotListTheirPhysicalCopies() throws Exception {
        mockMvc.perform(get("/api/cards/{cardId}/instances/mine", UUID.randomUUID())
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")).idToken(token -> token.subject("limited-sub"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
    }

    @Test
    void studentLimitedCannotReleaseAPhysicalCopy() throws Exception {
        mockMvc.perform(post("/api/cards/instances/release")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")).idToken(token -> token.subject("limited-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicId": "ABCDE-FGHJK-MNPQR"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
    }
}
