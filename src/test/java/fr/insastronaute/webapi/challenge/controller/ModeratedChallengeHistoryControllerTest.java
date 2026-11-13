package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ModeratedChallengeHistoryDto;
import fr.insastronaute.webapi.challenge.dto.ModerationHistoryType;
import fr.insastronaute.webapi.challenge.service.ModeratedChallengeHistoryService;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.onboarding.service.ModeratorGenderGateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModeratedChallengeHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModeratedChallengeHistoryService history;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ModeratorGenderGateService moderatorGenderGateService;

    @Test
    void moderatorReceivesThePaginatedHistoryAndAllTypedFiltersReachTheService() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        var entry = new ModeratedChallengeHistoryDto(
                entryId, Instant.parse("2026-09-08T12:00:00Z"), ModerationHistoryType.REJECTED,
                null, true, null, List.of(), "Photo de rentrée", 100, 0, null,
                "La date n'est pas visible.", null, null, false, false, false);
        when(history.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(entry), PageRequest.of(1, 25), 26)));
        when(moderatorGenderGateService.hasChosenGender("respo-sub")).thenReturn(true);

        mockMvc.perform(get("/api/moderation/history")
                        .param("playerUserId", playerId.toString())
                        .param("challenge", "rentrée")
                        .param("type", "VALIDATED", "REJECTED")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z")
                        .param("direction", "ASC")
                        .param("page", "1")
                        .param("size", "25")
                        .with(oidcLogin()
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_DEFIS"))
                                .idToken(token -> token.subject("respo-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$.content[0].actorHidden").value(true))
                .andExpect(jsonPath("$.content[0].message").value("La date n'est pas visible."))
                .andExpect(jsonPath("$.number").value(1));

        verify(history).search(
                eq("respo-sub"), any(), eq(playerId), eq(null), eq("rentrée"),
                eq(List.of(ModerationHistoryType.VALIDATED, ModerationHistoryType.REJECTED)),
                eq(Instant.parse("2026-09-01T00:00:00Z")), eq(Instant.parse("2026-10-01T00:00:00Z")),
                eq(org.springframework.data.domain.Sort.Direction.ASC), eq(1), eq(25));
    }

    @Test
    void studentCannotReadModeratedChallengeHistory() throws Exception {
        mockMvc.perform(get("/api/moderation/history")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verify(history, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
