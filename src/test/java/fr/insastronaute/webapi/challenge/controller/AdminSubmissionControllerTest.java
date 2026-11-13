package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionChallengeDto;
import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionDetailDto;
import fr.insastronaute.webapi.challenge.dto.ProofMediaDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.onboarding.service.ModeratorGenderGateService;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChallengeService challengeService;

    /**
     * La moderation exige un genre declare ; ce test-ci porte sur le controleur.
     *
     * <p>Les moderateurs simules n'ont pas de ligne en base : le garde les refuserait tous
     * avant la moindre assertion. Il repond « oui » ici, et se fait mesurer dans son propre
     * test.</p>
     */
    @MockitoBean
    private ModeratorGenderGateService moderatorGenderGateService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void moderatorsHaveDeclaredTheirGender() {
        when(moderatorGenderGateService.hasChosenGender(any())).thenReturn(true);
    }

    /**
     * Revoir les points d'une preuve deja jugee n'est pas moderer.
     *
     * <p>Juger une preuve qu'on a sous les yeux et reecrire apres coup la decision
     * d'un autre ne sont pas le meme pouvoir : les sous-respos moderent, ils
     * n'ajustent pas. La boucle ci-dessous n'en verifie que le mecanisme, d'ou
     * l'assertion sur la composition exacte -- un role retire du tableau perdrait
     * son acces sans faire echouer aucun test.</p>
     */
    @Test
    void adjustingBonusPointsIsReservedToTheChallengeLeads() throws Exception {
        assertThat(SecurityConfig.POINTS_ADJUSTMENT_ROLES)
                .containsExactlyInAnyOrder("ADMIN", "RESPOS_DEFIS");
        UUID submissionId = UUID.randomUUID();
        String body = "{\"bonusPoints\":30,\"reason\":\"Photo exceptionnelle.\"}";

        for (String role : SecurityConfig.POINTS_ADJUSTMENT_ROLES) {
            mockMvc.perform(post("/api/moderation/submissions/{id}/bonus-points", submissionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf().asHeader())
                            .with(oidcLogin()
                                    .idToken(token -> token.subject("staff-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isNoContent());
        }

        for (String role : List.of("SOUS_RESPO_DEFIS", "MODERATOR", "RESPO_DEPART", "STUDENT")) {
            mockMvc.perform(post("/api/moderation/submissions/{id}/bonus-points", submissionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf().asHeader())
                            .with(oidcLogin()
                                    .idToken(token -> token.subject("autre-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
        }

        verify(challengeService, times(2)).adjustValidatedSubmissionBonusPoints(
                eq(submissionId), any(), any(), eq(30), eq("Photo exceptionnelle."));
    }

    @Test
    void anAdjustmentWithoutAReasonNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/moderation/submissions/{id}/bonus-points", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bonusPoints\":30,\"reason\":\"\"}")
                        .with(csrf().asHeader())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());

        verify(challengeService, never()).adjustValidatedSubmissionBonusPoints(any(), any(), any(), any(), any());
    }

    @Test
    void moderatorCanGetSubmissionDetail() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(challengeService.getModerationSubmissionDetail(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN"))
        )).thenReturn(new ModerationSubmissionDetailDto(
                submissionId,
                "PENDING",
                Instant.parse("2026-06-24T10:00:00Z"),
                null,
                "Alice",
                "Département Alpha",
                "MEDIA",
                null,
                List.of(new ProofMediaDto(0, "image/jpeg")),
                // Les fichiers sont encore la : la purge n'a pas eu lieu.
                null,
                "Voici ma preuve",
                null,
                10,
                true,
                true,
                true,
                Instant.parse("2026-06-24T10:01:00Z"),
                new ModerationSubmissionChallengeDto(
                        challengeId,
                        "Équations linéaires",
                        "Résoudre correctement l'exercice.",
                        150,
                        "Mathématiques",
                        "ADMIN",
                        false,
                        1,
                        3
                ),
                List.of(new SubmissionParticipantDto(participantId, "Participant A", "participant@example.com", null, null, null, false, false)),
                List.of(new SubmissionMessageDto(
                        messageId,
                        "PLAYER",
                        participantId,
                        "Participant A",
                        false,
                        "Je vais corriger.",
                        Instant.parse("2026-06-24T10:02:00Z")
                ))
        ));

        mockMvc.perform(get("/api/moderation/submissions/{id}", submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(submissionId.toString()))
                .andExpect(jsonPath("$.challenge.id").value(challengeId.toString()))
                .andExpect(jsonPath("$.challenge.title").value("Équations linéaires"))
                .andExpect(jsonPath("$.proofData").doesNotExist())
                .andExpect(jsonPath("$.proofMedia[0].index").value(0))
                .andExpect(jsonPath("$.proofMedia[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.messagesEnabled").value(true))
                .andExpect(jsonPath("$.participants[0].userId").value(participantId.toString()))
                .andExpect(jsonPath("$.messages[0].content").value("Je vais corriger."));

        verify(challengeService).getModerationSubmissionDetail(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN"))
        );
    }

    @Test
    void moderatorCanStreamLockedProofWithoutCaching() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(challengeService.openModerationProofMedia(
                eq(submissionId), eq(0), eq("admin-sub"), eq(Set.of("ROLE_ADMIN")), eq(null)
        )).thenReturn(fr.insastronaute.webapi.proof.service.ProofMediaStream.full(
                "image/jpeg", 4, new ByteArrayInputStream(new byte[]{1, 2, 3, 4})
        ));

        MvcResult result = mockMvc.perform(get("/api/moderation/submissions/{id}/proofs/{index}/content", submissionId, 0)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private, max-age=0"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void moderatorCanGetSubmissionMessages() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(challengeService.getModerationSubmissionMessages(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN"))
        )).thenReturn(List.of(new SubmissionMessageDto(
                UUID.randomUUID(),
                "PLAYER",
                authorId,
                "Player",
                false,
                "Voici une précision.",
                Instant.parse("2026-06-24T10:00:00Z")
        )));

        mockMvc.perform(get("/api/moderation/submissions/{id}/messages", submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorUserId").value(authorId.toString()))
                .andExpect(jsonPath("$[0].authorName").value("Player"));

        verify(challengeService).getModerationSubmissionMessages(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN"))
        );
    }

    @Test
    void moderatorCanPostSubmissionMessage() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(challengeService.createModerationSubmissionMessage(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN")),
                any()
        )).thenReturn(new SubmissionMessageDto(
                UUID.randomUUID(),
                "MODERATOR",
                UUID.randomUUID(),
                "Admin",
                true,
                "Merci pour la précision.",
                Instant.parse("2026-06-24T10:00:00Z")
        ));

        mockMvc.perform(post("/api/moderation/submissions/{id}/messages", submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Merci pour la précision."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Merci pour la précision."));

        verify(challengeService).createModerationSubmissionMessage(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN")),
                any()
        );
    }

    @Test
    void moderatorCanValidateWithAContextualizedMalus() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/moderation/submissions/{id}/decide", submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "VALIDATED",
                                  "moderationComment": "Preuve partielle.",
                                  "bonusPoints": -5
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(challengeService).decideSubmission(
                submissionId, "admin-sub", Set.of("ROLE_ADMIN"),
                "VALIDATED", "Preuve partielle.", -5);
    }

    @Test
    void moderatorCanDisableSubmissionMessages() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/moderation/submissions/{id}/messages/enabled", submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(challengeService).updateSubmissionMessagesEnabled(
                eq(submissionId),
                eq("admin-sub"),
                eq(Set.of("ROLE_ADMIN")),
                eq(false)
        );
    }

    @Test
    void ownerCanRenewReviewLock() throws Exception {
        UUID submissionId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-08-05T15:15:00Z");
        when(challengeService.heartbeatSubmissionReviewLock(
                submissionId, "admin-sub", Set.of("ROLE_ADMIN"))).thenReturn(expiresAt);

        mockMvc.perform(put("/api/moderation/submissions/{id}/review-lock/heartbeat", submissionId)
                        .with(csrf())
                        .with(oidcLogin().idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value("2026-08-05T15:15:00Z"));
    }
}
