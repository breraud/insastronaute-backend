package fr.insastronaute.webapi.card.print.controller;

import fr.insastronaute.webapi.card.print.domain.CardPrintFitMode;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;
import fr.insastronaute.webapi.card.print.domain.CardPrintQrCorner;
import fr.insastronaute.webapi.card.print.domain.CardPrintTextAlign;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchPageDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchPageLookupDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintCodeSettingsDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintQrSettingsDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunSlotDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateSlotDto;
import fr.insastronaute.webapi.card.print.service.CardPrintPdfRegenerationService;
import fr.insastronaute.webapi.card.print.service.CardPrintRunService;
import fr.insastronaute.webapi.card.print.service.CardPrintTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCardPrintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardPrintTemplateService templateService;

    @MockitoBean
    private CardPrintRunService printRunService;

    @MockitoBean
    private CardPrintPdfRegenerationService pdfRegenerationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanCreateTemplateFromPdf() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(templateService.create(eq("Patron A4"), any())).thenReturn(template(templateId));

        mockMvc.perform(multipart("/api/admin/card-print-templates")
                        .file(new MockMultipartFile("pdf", "template.pdf", "application/pdf", new byte[]{1, 2, 3}))
                        .param("name", "Patron A4")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.pageWidthPt").value(595.28));

        verify(templateService).create(eq("Patron A4"), any());
    }

    @Test
    void adminCanPreviewTemplateAsPdf() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(templateService.preview(eq(templateId), any())).thenReturn(new byte[]{'%','P','D','F'});

        mockMvc.perform(post("/api/admin/card-print-templates/{id}/preview", templateId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotSelectionJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        verify(templateService).preview(eq(templateId), any());
    }

    @Test
    void adminCanDeleteTemplate() throws Exception {
        UUID templateId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/card-print-templates/{id}", templateId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(templateService).delete(templateId);
    }

    @Test
    void adminCanDeleteTemplatesInBatch() throws Exception {
        UUID firstTemplateId = UUID.randomUUID();
        UUID secondTemplateId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/card-print-templates")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateIds": [
                                    "%s",
                                    "%s"
                                  ]
                                }
                                """.formatted(firstTemplateId, secondTemplateId)))
                .andExpect(status().isNoContent());

        verify(templateService).deleteAll(List.of(firstTemplateId, secondTemplateId));
    }

    @Test
    void adminCanCreatePrintRun() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID printRunId = UUID.randomUUID();
        when(printRunService.create(any(), any())).thenReturn(new CardPrintRunDto(
                printRunId,
                templateId,
                "Patron A4",
                2,
                18,
                CardPrintRunStatus.COMPLETED,
                "/api/admin/batches/" + printRunId + "/pdf",
                List.of(new CardPrintRunSlotDto(0, 0, UUID.randomUUID(), "Carte", UUID.randomUUID(), "ABCDE-FGHJK-MNPQR")),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z")
        ));

        mockMvc.perform(post("/api/admin/card-print-runs")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "sheetCount": 2,
                                  "slots": %s
                                }
                                """.formatted(templateId, slotsArrayJson())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(printRunId.toString()))
                .andExpect(jsonPath("$.instanceCount").value(18))
                .andExpect(jsonPath("$.pdfUrl").value("/api/admin/batches/" + printRunId + "/pdf"));

        verify(printRunService).create(any(), any());
    }

    @Test
    void adminCanRegenerateOldBatchPdfs() throws Exception {
        when(pdfRegenerationService.regenerateUncompressedRunPdfs())
                .thenReturn(new CardPrintPdfRegenerationDto(3, 1, 2, 0));

        mockMvc.perform(post("/api/admin/batches/regenerate-pdfs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(3))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.regeneratedCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0));

        verify(pdfRegenerationService).regenerateUncompressedRunPdfs();
    }

    @Test
    void adminCanForceRegenerateEveryBatchForTemplate() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(pdfRegenerationService.regenerateTemplateBatches(templateId))
                .thenReturn(new CardPrintPdfRegenerationDto(4, 1, 2, 1));

        mockMvc.perform(post("/api/admin/card-print-templates/{id}/regenerate-batches", templateId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(4))
                .andExpect(jsonPath("$.regeneratedCount").value(2));

        verify(pdfRegenerationService).regenerateTemplateBatches(templateId);
    }

    @Test
    void adminCanForceRegenerateOneBatch() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(pdfRegenerationService.regenerateBatch(batchId))
                .thenReturn(new CardPrintPdfRegenerationDto(1, 0, 1, 0));

        mockMvc.perform(post("/api/admin/batches/{id}/regenerate-pdf", batchId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regeneratedCount").value(1));

        verify(pdfRegenerationService).regenerateBatch(batchId);
    }

    @Test
    void adminCanDownloadBatchPdfThroughBackend() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(printRunService.readBatchPdf(batchId)).thenReturn(new byte[]{'%','P','D','F'});

        mockMvc.perform(get("/api/admin/batches/{id}/pdf", batchId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        verify(printRunService).readBatchPdf(batchId);
    }

    @Test
    void nonAdminCannotDownloadBatchPdf() throws Exception {
        mockMvc.perform(get("/api/admin/batches/{id}/pdf", UUID.randomUUID())
                        .with(user("player").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListBatches() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(printRunService.findAllBatches()).thenReturn(List.of(batch(batchId)));

        mockMvc.perform(get("/api/admin/batches")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(batchId.toString()))
                .andExpect(jsonPath("$[0].pageCount").value(4))
                .andExpect(jsonPath("$[0].pages[0].printPageId").value(batchId + "-1"));

        verify(printRunService).findAllBatches();
    }

    @Test
    void adminCanFindBatchPageFromPrintedId() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(printRunService.findPage(batchId + "-2")).thenReturn(new CardPrintBatchPageLookupDto(
                batchId,
                batchId + "-2",
                2,
                0,
                "BACK",
                "https://s3.example.com/print.pdf",
                page(batchId, 2)
        ));

        mockMvc.perform(get("/api/admin/batches/pages/{printPageId}", batchId + "-2")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.side").value("BACK"))
                .andExpect(jsonPath("$.page.slots.length()").value(1));

        verify(printRunService).findPage(batchId + "-2");
    }

    @Test
    void adminCanDeleteBatch() throws Exception {
        UUID batchId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/batches/{id}", batchId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(printRunService).deleteBatch(batchId);
    }

    private CardPrintTemplateDto template(UUID templateId) {
        return new CardPrintTemplateDto(
                templateId,
                "Patron A4",
                "https://s3.example.com/template.pdf",
                2,
                595.28,
                841.89,
                false,
                CardPrintFitMode.COVER,
                0D,
                List.of(new CardPrintTemplateSlotDto(UUID.randomUUID(), 0, 36.85, 29.76, 178.55, 249.39)),
                new CardPrintCodeSettingsDto(
                        10D,
                        220D,
                        150D,
                        16D,
                        CardPrintFontFamily.HELVETICA,
                        CardPrintFontWeight.BOLD,
                        9D,
                        "#111111",
                        "#FFFFFF",
                        0.75D,
                        CardPrintTextAlign.CENTER
                ),
                List.of(),
                new CardPrintQrSettingsDto(false, CardPrintQrCorner.BOTTOM_RIGHT, 32D, 4, 4D),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private CardPrintBatchDto batch(UUID batchId) {
        UUID templateId = UUID.randomUUID();
        CardPrintRunSlotDto slot = new CardPrintRunSlotDto(0, 0, UUID.randomUUID(), "Carte", UUID.randomUUID(), "ABCDE-FGHJK-MNPQR");
        return new CardPrintBatchDto(
                batchId,
                templateId,
                "Patron A4",
                2,
                4,
                18,
                CardPrintRunStatus.COMPLETED,
                "https://s3.example.com/print.pdf",
                List.of(page(batchId, 1), page(batchId, 2)),
                List.of(slot),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z")
        );
    }

    private CardPrintBatchPageDto page(UUID batchId, int pageNumber) {
        return new CardPrintBatchPageDto(
                batchId + "-" + pageNumber,
                pageNumber,
                (pageNumber - 1) / 2,
                pageNumber % 2 == 1 ? "FRONT" : "BACK",
                List.of(new CardPrintRunSlotDto(0, 0, UUID.randomUUID(), "Carte", UUID.randomUUID(), "ABCDE-FGHJK-MNPQR"))
        );
    }

    private String slotSelectionJson() {
        return """
                {
                  "slots": %s
                }
                """.formatted(slotsArrayJson());
    }

    private String slotsArrayJson() {
        return """
                [
                  { "slotIndex": 0, "cardId": "00000000-0000-0000-0000-000000000001" },
                  { "slotIndex": 1, "cardId": "00000000-0000-0000-0000-000000000002" },
                  { "slotIndex": 2, "cardId": "00000000-0000-0000-0000-000000000003" },
                  { "slotIndex": 3, "cardId": "00000000-0000-0000-0000-000000000004" },
                  { "slotIndex": 4, "cardId": "00000000-0000-0000-0000-000000000005" },
                  { "slotIndex": 5, "cardId": "00000000-0000-0000-0000-000000000006" },
                  { "slotIndex": 6, "cardId": "00000000-0000-0000-0000-000000000007" },
                  { "slotIndex": 7, "cardId": "00000000-0000-0000-0000-000000000008" },
                  { "slotIndex": 8, "cardId": "00000000-0000-0000-0000-000000000009" }
                ]
                """;
    }
}
