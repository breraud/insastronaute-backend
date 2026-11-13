package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.TooManyRequestsException;
import fr.insastronaute.webapi.place.dto.PlacePixelUpdate;
import fr.insastronaute.webapi.place.service.PlaceCanvasDescriptor;
import fr.insastronaute.webapi.place.service.PlaceCanvasSnapshot;
import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownType;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentResultDto;
import fr.insastronaute.webapi.place.dto.PlacePlayerResultDto;
import fr.insastronaute.webapi.place.dto.PlaceResultsDto;
import fr.insastronaute.webapi.place.service.PlaceResultsService;
import fr.insastronaute.webapi.place.service.PlaceService;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PlaceService placeService;
    @MockitoBean PlaceResultsService placeResultsService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void studentLimitedCannotAccessPlaceReadsOrMutations() throws Exception {
        mockMvc.perform(get("/api/place/canvas").with(limitedOidc()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
        mockMvc.perform(post("/api/place/pixel")
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"x\":4,\"y\":8,\"color\":\"#AABBCC\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(placeService);
    }

    @Test
    void successfulPlacementExposesCompletePublicContractWithoutUserId() throws Exception {
        Instant next = Instant.parse("2026-07-23T10:05:00Z");
        when(placeService.placePixel(eq("player-sub"), any())).thenReturn(
                new PlacePixelUpdate(4, 8, "#AABBCC", 12, CooldownType.CHALLENGE, 300_000, next));

        pixelRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(4))
                .andExpect(jsonPath("$.y").value(8))
                .andExpect(jsonPath("$.color").value("#AABBCC"))
                .andExpect(jsonPath("$.revision").value("12"))
                .andExpect(jsonPath("$.cooldownType").value("CHALLENGE"))
                .andExpect(jsonPath("$.remainingCooldownMs").value(300000))
                .andExpect(jsonPath("$.nextPlacementAt").value("2026-07-23T10:05:00Z"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void canvasReturnsPairedPngAndRevision() throws Exception {
        when(placeService.canvasPng()).thenReturn(new PlaceCanvasSnapshot(
                new byte[]{1, 2, 3}, new PlaceCanvasDescriptor("active", 44, 2, 2)));
        mockMvc.perform(get("/api/place/canvas").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Place-Revision", "44"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void cooldownExposesStableCodeRemainingTimeAndRetryAfter() throws Exception {
        when(placeService.placePixel(eq("player-sub"), any())).thenThrow(new TooManyRequestsException(1_500));
        pixelRequest().andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("PLACE_COOLDOWN"))
                .andExpect(jsonPath("$.remainingCooldownMs").value(1500));
    }

    @Test
    void inactiveAndInvalidCoordinatesExposeStableCodes() throws Exception {
        when(placeService.placePixel(eq("player-sub"), any()))
                .thenThrow(new CodedBadRequestException("PLACE_INACTIVE", "inactive"))
                .thenThrow(new CodedBadRequestException("PLACE_INVALID_COORDINATES", "invalid"));
        pixelRequest().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_INACTIVE"));
        pixelRequest().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_INVALID_COORDINATES"));
    }

    private org.springframework.test.web.servlet.ResultActions pixelRequest() throws Exception {
        return mockMvc.perform(post("/api/place/pixel").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                .with(csrf()).contentType("application/json")
                .content("{\"x\":4,\"y\":8,\"color\":\"#AABBCC\"}"));
    }

    @Test
    void exposesWhetherPlaceIsShownToPlayers() throws Exception {
        when(placeService.isEventOpen()).thenReturn(false);

        mockMvc.perform(get("/api/place/visibility")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));
    }

    /** Le front doit savoir remplacer la fresque par ses resultats sans les demander. */
    @Test
    void visibilityAnnouncesThePublishedResults() throws Exception {
        when(placeService.showsResults()).thenReturn(true);

        mockMvc.perform(get("/api/place/visibility")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").value(true));
    }

    @Test
    void resultsExposeBothRankingsAndThePlayersOwnLine() throws Exception {
        when(placeResultsService.results("player-sub")).thenReturn(new PlaceResultsDto(
                Instant.parse("2026-07-23T10:00:00Z"), 35,
                java.util.List.of(new PlaceDepartmentResultDto(1, "GM", "#3B82F6", 2, 5, 2d / 3d)),
                java.util.List.of(new PlacePlayerResultDto(1, "Sonia", "IF", 30, false)),
                new PlacePlayerResultDto(12, "Tom", "GM", 5, true)));

        mockMvc.perform(get("/api/place/results").with(oidcLogin()
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))
                        .idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").value("2026-07-23T10:00:00Z"))
                .andExpect(jsonPath("$.totalPixels").value(35))
                .andExpect(jsonPath("$.departments[0].name").value("GM"))
                .andExpect(jsonPath("$.departments[0].territory").value(2))
                .andExpect(jsonPath("$.departments[0].placed").value(5))
                .andExpect(jsonPath("$.players[0].name").value("Sonia"))
                .andExpect(jsonPath("$.me.rank").value(12))
                .andExpect(jsonPath("$.me.you").value(true));
    }

    /** Non publie veut dire inexistant : l'URL ne doit pas devancer l'annonce. */
    @Test
    void resultsAreNotFoundBeforeTheyArePublished() throws Exception {
        when(placeResultsService.results("player-sub")).thenThrow(new NotFoundException("pas publié"));

        mockMvc.perform(get("/api/place/results").with(oidcLogin()
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))
                        .idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor limitedOidc() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED"))
                .idToken(token -> token.subject("limited-sub"));
    }
}
