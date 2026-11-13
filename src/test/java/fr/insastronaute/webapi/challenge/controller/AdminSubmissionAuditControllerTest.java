package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.dto.AdminSubmissionAuditDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSubmissionAuditControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ModerationAuditService moderationAuditService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanReadSubmissionAudit() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(moderationAuditService.getSubmissionAudit(submissionId)).thenReturn(new AdminSubmissionAuditDto(
                new AdminSubmissionAuditDto.Decision(
                        "REJECTED", "Marie Dupont", Instant.parse("2026-08-12T14:32:10Z")),
                List.of(new AdminSubmissionAuditDto.MediaConsultation(
                        "Paul Martin", 1, Instant.parse("2026-08-12T14:28:03Z")))
        ));

        mockMvc.perform(get("/api/admin/submissions/{id}/audit", submissionId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.actorName").value("Marie Dupont"))
                .andExpect(jsonPath("$.decision.occurredAt").value("2026-08-12T14:32:10Z"))
                .andExpect(jsonPath("$.mediaConsultations[0].actorName").value("Paul Martin"))
                .andExpect(jsonPath("$.mediaConsultations[0].mediaIndex").value(1));

        verify(moderationAuditService).getSubmissionAudit(submissionId);
    }

    @Test
    void nonAdminCannotReadSubmissionAudit() throws Exception {
        mockMvc.perform(get("/api/admin/submissions/{id}/audit", UUID.randomUUID())
                        .with(user("moderator").roles("RESPOS_DEFIS")))
                .andExpect(status().isForbidden());
    }
}
