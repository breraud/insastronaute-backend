package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.dto.AdminSubmissionAuditDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/submissions")
@Tag(name = "Admin Submissions")
public class AdminSubmissionAuditController {
    private final ModerationAuditService moderationAuditService;

    public AdminSubmissionAuditController(ModerationAuditService moderationAuditService) {
        this.moderationAuditService = moderationAuditService;
    }

    @GetMapping("/{submissionId}/audit")
    @Operation(
            summary = "Get a submission audit",
            description = "Returns the recorded moderation decision and chronological proof media consultations."
    )
    public AdminSubmissionAuditDto getSubmissionAudit(@PathVariable UUID submissionId) {
        return moderationAuditService.getSubmissionAudit(submissionId);
    }
}
