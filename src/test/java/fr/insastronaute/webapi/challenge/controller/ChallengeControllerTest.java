package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.SubmissionResultDto;
import fr.insastronaute.webapi.challenge.dto.PlayerSubmissionDetailDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChallengeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChallengeService challengeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void studentLimitedCannotAccessChallengeReadsOrMutations() throws Exception {
        UUID challengeId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(get("/api/challenges").with(limitedOidc()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
        mockMvc.perform(post("/api/challenges/{id}/favorite", challengeId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/challenges/{id}/favorite", challengeId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/challenges/{id}/submit", challengeId)
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proofType\":\"TEXT\",\"comment\":\"proof\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/challenges/submissions/{id}/participants/accept", submissionId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/challenges/submissions/{id}/participants/decline", submissionId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/challenges/submissions/{id}", submissionId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/challenges/submissions/{id}/participants/resend", submissionId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/challenges/submissions/{id}/messages", submissionId)
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(challengeService);
    }

    @Test
    void getSubmissionResultsReturnsMinimalHistory() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(challengeService.getSubmissionResults("player-sub")).thenReturn(List.of(new SubmissionResultDto(
                submissionId,
                challengeId,
                "Escalader le totem",
                "REJECTED",
                Instant.parse("2026-06-16T12:00:00Z"),
                null,
                120,
                0,
                false,
                false,
                false,
                true,
                "Preuve illisible",
                false,
                null
        )));

        mockMvc.perform(get("/api/challenges/submissions/results")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].submissionId").value(submissionId.toString()))
                .andExpect(jsonPath("$[0].challengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$[0].challengeTitle").value("Escalader le totem"))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].submittedAt").value("2026-06-16T12:00:00Z"))
                .andExpect(jsonPath("$[0].pointsReward").value(120))
                .andExpect(jsonPath("$[0].bonusPoints").value(0))
                .andExpect(jsonPath("$[0].automatic").value(false))
                .andExpect(jsonPath("$[0].promo").value(false))
                .andExpect(jsonPath("$[0].rewardShared").value(false))
                .andExpect(jsonPath("$[0].rewarded").value(true))
                .andExpect(jsonPath("$[0].moderationComment").value("Preuve illisible"))
                .andExpect(jsonPath("$[0].participantOnly").value(false));
    }

    @Test
    void getSubmissionsWaitingConfirmationReturnsOnlyThatQueue() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(challengeService.getSubmissionsWaitingConfirmation("player-sub")).thenReturn(List.of(new SubmissionResultDto(
                submissionId,
                challengeId,
                "Un défi",
                "WAITING_CONFIRMATION",
                Instant.parse("2026-08-01T10:00:00Z"),
                null,
                50,
                0,
                false,
                false,
                false,
                true,
                null,
                true,
                null
        )));

        mockMvc.perform(get("/api/challenges/submissions/waiting-confirmation")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].submissionId").value(submissionId.toString()))
                .andExpect(jsonPath("$[0].status").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$[0].participantOnly").value(true));

        verify(challengeService).getSubmissionsWaitingConfirmation("player-sub");
    }

    @Test
    void getSubmissionsPendingValidationReturnsOnlyThatQueue() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(challengeService.getSubmissionsPendingValidation("player-sub")).thenReturn(List.of(new SubmissionResultDto(
                submissionId,
                challengeId,
                "Un défi",
                "PENDING",
                Instant.parse("2026-08-01T11:00:00Z"),
                null,
                50,
                0,
                false,
                false,
                false,
                true,
                null,
                false,
                null
        )));

        mockMvc.perform(get("/api/challenges/submissions/pending-validation")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].submissionId").value(submissionId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].participantOnly").value(false));

        verify(challengeService).getSubmissionsPendingValidation("player-sub");
    }

    @Test
    void getSubmissionDetailUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        when(challengeService.getPlayerSubmissionDetail(submissionId, "player-sub"))
                .thenReturn(new PlayerSubmissionDetailDto(
                        submissionId,
                        challengeId,
                        "Photo equipe",
                        "WAITING_CONFIRMATION",
                        Instant.parse("2026-06-16T12:00:00Z"),
                        null,
                        null,
                        true,
                        List.of(
                                new SubmissionParticipantDto(
                                        submitterId,
                                        "Submitter",
                                        "submitter@example.com",
                                        "ACCEPTED",
                                        Instant.parse("2026-06-16T12:00:00Z"),
                                        Instant.parse("2026-06-16T12:00:00Z"),
                                        true
                                , false),
                                new SubmissionParticipantDto(
                                        participantId,
                                        "Participant",
                                        "participant@example.com",
                                        "PENDING",
                                        Instant.parse("2026-06-16T12:01:00Z"),
                                        null,
                                        false
                                , false)
                        ),
                        false,
                        2,
                        4
                ));

        mockMvc.perform(get("/api/challenges/submissions/{id}", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId.toString()))
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$.participants[0].confirmationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.participants[1].confirmationStatus").value("PENDING"));

        verify(challengeService).getPlayerSubmissionDetail(submissionId, "player-sub");
    }

    @Test
    void submitRejectsVideoLinkProofType() throws Exception {
        mockMvc.perform(post("/api/challenges/{id}/submit", UUID.randomUUID())
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "proofType": "VIDEO_LINK",
                                  "proofData": "https://example.com/video"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("proofType"));

        verify(challengeService, never()).submitChallenge(any(), any(), any());
    }

    @Test
    void staffAndAdminCannotSubmitAChallengeAsPlayers() throws Exception {
        UUID challengeId = UUID.randomUUID();

        for (String role : List.of("ROLE_STAFF", "ROLE_ADMIN")) {
            mockMvc.perform(post("/api/challenges/{id}/submit", challengeId)
                            .with(oidcLogin()
                                    .authorities(new SimpleGrantedAuthority(role))
                                    .idToken(token -> token.subject("staff-sub")))
                            .with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"proofType\":\"TEXT\",\"comment\":\"proof\"}"))
                    .andExpect(status().isForbidden());
        }

        verify(challengeService, never()).submitChallenge(any(), any(), any());
    }

    @Test
    void studentCanStillSubmitAChallenge() throws Exception {
        UUID challengeId = UUID.randomUUID();

        mockMvc.perform(post("/api/challenges/{id}/submit", challengeId)
                        .with(oidcLogin()
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))
                                .idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proofType\":\"TEXT\",\"comment\":\"proof\"}"))
                .andExpect(status().isOk());

        verify(challengeService).submitChallenge(eq("player-sub"), eq(challengeId), any());
    }

    @Test
    void getFavoriteChallengesUsesAuthenticatedUser() throws Exception {
        when(challengeService.getFavoriteChallenges("player-sub")).thenReturn(List.of());

        mockMvc.perform(get("/api/challenges/favorites")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(challengeService).getFavoriteChallenges("player-sub");
    }

    @Test
    void addFavoriteUsesAuthenticatedUser() throws Exception {
        UUID challengeId = UUID.randomUUID();

        mockMvc.perform(post("/api/challenges/{id}/favorite", challengeId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(challengeService).addFavoriteChallenge("player-sub", challengeId);
    }

    @Test
    void removeFavoriteUsesAuthenticatedUser() throws Exception {
        UUID challengeId = UUID.randomUUID();

        mockMvc.perform(delete("/api/challenges/{id}/favorite", challengeId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(challengeService).removeFavoriteChallenge("player-sub", challengeId);
    }

    @Test
    void acceptGroupSubmissionUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/challenges/submissions/{id}/participants/accept", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("participant-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(challengeService).respondToGroupSubmission(submissionId, "participant-sub", true);
    }

    @Test
    void declineGroupSubmissionUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/challenges/submissions/{id}/participants/decline", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("participant-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(challengeService).respondToGroupSubmission(submissionId, "participant-sub", false);
    }

    @Test
    void cancelGroupSubmissionUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/challenges/submissions/{id}", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("submitter-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(challengeService).cancelGroupSubmission(submissionId, "submitter-sub");
    }

    @Test
    void resendGroupSubmissionInvitesUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/challenges/submissions/{id}/participants/resend", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("submitter-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(challengeService).resendGroupSubmissionInvites(submissionId, "submitter-sub");
    }

    @Test
    void getSubmissionMessagesUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(challengeService.getPlayerSubmissionMessages(submissionId, "player-sub"))
                .thenReturn(List.of(new SubmissionMessageDto(
                        UUID.randomUUID(),
                        "MODERATOR",
                        null,
                        "Équipe de modération",
                        false,
                        "Il manque un détail.",
                        Instant.parse("2026-06-24T10:00:00Z")
                )));

        mockMvc.perform(get("/api/challenges/submissions/{id}/messages", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorName").value("Équipe de modération"))
                .andExpect(jsonPath("$[0].authorUserId").doesNotExist());

        verify(challengeService).getPlayerSubmissionMessages(submissionId, "player-sub");
    }

    @Test
    void postSubmissionMessageUsesAuthenticatedUser() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(challengeService.createPlayerSubmissionMessage(eq(submissionId), eq("player-sub"), any()))
                .thenReturn(new SubmissionMessageDto(
                        UUID.randomUUID(),
                        "PLAYER",
                        UUID.randomUUID(),
                        "Player",
                        true,
                        "Bonjour",
                        Instant.parse("2026-06-24T10:00:00Z")
                ));

        mockMvc.perform(post("/api/challenges/submissions/{id}/messages", submissionId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Bonjour"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Bonjour"));

        verify(challengeService).createPlayerSubmissionMessage(eq(submissionId), eq("player-sub"), any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor limitedOidc() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED"))
                .idToken(token -> token.subject("limited-sub"));
    }
}
