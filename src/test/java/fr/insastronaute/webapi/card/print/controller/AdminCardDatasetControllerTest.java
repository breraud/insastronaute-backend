package fr.insastronaute.webapi.card.print.controller;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropUploadRequest;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetCodeDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetSummaryDto;
import fr.insastronaute.webapi.card.print.service.CardDatasetCropService;
import fr.insastronaute.webapi.card.print.service.CardDatasetExportService;
import fr.insastronaute.webapi.card.print.service.CardDatasetService;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCardDatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardDatasetService datasetService;

    @MockitoBean
    private CardDatasetCropService cropService;

    @MockitoBean
    private CardDatasetExportService exportService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void generatingATestSheetHandsBackTheIdentifierItPrinted() throws Exception {
        when(datasetService.generateTestDatasetPdf())
                .thenReturn(new CardDatasetService.GeneratedDatasetSheet("PL-48213-3", new byte[]{'%', 'P', 'D', 'F'}));

        mockMvc.perform(post("/api/admin/batches/dataset/test")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Dataset-Sheet-Id", "PL-48213-3"));
    }

    @Test
    void sheetGenerationRequiresCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/admin/batches/dataset/sheets")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(datasetService);
    }

    @Test
    void legacyGetCannotGenerateATestSheetAnymore() throws Exception {
        mockMvc.perform(get("/api/admin/batches/dataset/test")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(datasetService);
    }

    @Test
    void adminCanGenerateABatchDatasetOnlyThroughPost() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(datasetService.generateBatchDatasetPdf(batchId))
                .thenReturn(new CardDatasetService.GeneratedDatasetSheet("PL-48213-3", new byte[]{'%'}));

        mockMvc.perform(post("/api/admin/batches/{id}/dataset", batchId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/admin/batches/{id}/dataset", batchId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminCanReadTheCodesASheetCarries() throws Exception {
        when(datasetService.findSheet("PL-48213-3")).thenReturn(new CardDatasetSheetDto(
                UUID.randomUUID(),
                "PL-48213-3",
                null,
                Instant.parse("2026-08-24T10:00:00Z"),
                List.of(
                        new CardDatasetSheetCodeDto(UUID.randomUUID(), 0, "AAAAA-BBBBB-CCCCC"),
                        new CardDatasetSheetCodeDto(UUID.randomUUID(), 1, "DDDDD-EEEEE-FFFFF")
                )
        ));

        mockMvc.perform(get("/api/admin/batches/dataset/{sheetId}/codes", "PL-48213-3")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetId").value("PL-48213-3"))
                .andExpect(jsonPath("$.codes[0].position").value(0))
                .andExpect(jsonPath("$.codes[0].code").value("AAAAA-BBBBB-CCCCC"))
                .andExpect(jsonPath("$.codes[1].code").value("DDDDD-EEEEE-FFFFF"));
    }

    @Test
    void adminCanListGeneratedSheetsIncludingLegacyOnes() throws Exception {
        UUID downloadableId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();
        when(datasetService.findAllSheets()).thenReturn(List.of(
                new CardDatasetSheetSummaryDto(downloadableId, "PL-48213-3", null,
                        Instant.parse("2026-08-25T12:00:00Z"), 7, true),
                new CardDatasetSheetSummaryDto(legacyId, "PL-01234-8", null,
                        Instant.parse("2026-08-24T12:00:00Z"), 3, false)
        ));

        mockMvc.perform(get("/api/admin/batches/dataset/sheets")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(downloadableId.toString()))
                .andExpect(jsonPath("$[0].sheetId").value("PL-48213-3"))
                .andExpect(jsonPath("$[0].batchId").doesNotExist())
                .andExpect(jsonPath("$[0].scanCount").value(7))
                .andExpect(jsonPath("$[0].downloadable").value(true))
                .andExpect(jsonPath("$[1].id").value(legacyId.toString()))
                .andExpect(jsonPath("$[1].downloadable").value(false));
    }

    @Test
    void adminCanGenerateANewTestSheetWithoutEmbeddingThePdfInJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(datasetService.createTestSheets(1)).thenReturn(List.of(new CardDatasetSheetSummaryDto(
                id, "PL-48213-3", null, Instant.parse("2026-08-25T12:00:00Z"), 0, true)));

        mockMvc.perform(post("/api/admin/batches/dataset/sheets")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].sheetId").value("PL-48213-3"))
                .andExpect(jsonPath("$[0].scanCount").value(0))
                .andExpect(jsonPath("$[0].downloadable").value(true))
                .andExpect(jsonPath("$[0].pdf").doesNotExist());
    }

    @Test
    void adminCanGenerateSeveralTestSheetsInOnePass() throws Exception {
        when(datasetService.createTestSheets(3)).thenReturn(List.of(
                new CardDatasetSheetSummaryDto(
                        UUID.randomUUID(), "PL-48213-3", null, Instant.parse("2026-08-25T12:00:00Z"), 0, true),
                new CardDatasetSheetSummaryDto(
                        UUID.randomUUID(), "PL-31337-9", null, Instant.parse("2026-08-25T12:00:01Z"), 0, true),
                new CardDatasetSheetSummaryDto(
                        UUID.randomUUID(), "PL-11111-5", null, Instant.parse("2026-08-25T12:00:02Z"), 0, true)));

        mockMvc.perform(post("/api/admin/batches/dataset/sheets")
                        .param("count", "3")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].sheetId").value("PL-31337-9"));
    }

    @Test
    void adminCanRegenerateASheetPdf() throws Exception {
        byte[] regenerated = new byte[]{'%', 'P', 'D', 'F', 3};
        when(datasetService.regenerateSheetPdf("PL-48213-3")).thenReturn(regenerated);

        mockMvc.perform(post("/api/admin/batches/dataset/{sheetId}/regenerate", "PL-48213-3")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(regenerated));
    }

    @Test
    void adminCanRegenerateEverySheetAtOnce() throws Exception {
        when(datasetService.regenerateAllSheetPdfs())
                .thenReturn(new CardPrintPdfRegenerationDto(15, 2, 12, 1));

        mockMvc.perform(post("/api/admin/batches/dataset/sheets/regenerate")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(15))
                .andExpect(jsonPath("$.skippedCount").value(2))
                .andExpect(jsonPath("$.regeneratedCount").value(12))
                .andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test
    void adminCanDeleteASheet() throws Exception {
        mockMvc.perform(delete("/api/admin/batches/dataset/{sheetId}", "PL-48213-3")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(datasetService).deleteSheet("PL-48213-3");
    }

    @Test
    void adminCanDownloadSeveralSheetsAsOnePdf() throws Exception {
        byte[] grouped = new byte[]{'%', 'P', 'D', 'F', 7};
        when(datasetService.renderSheetsPdf(List.of("PL-48213-3", "PL-31337-9"))).thenReturn(grouped);

        mockMvc.perform(get("/api/admin/batches/dataset/sheets/pdf")
                        .param("sheetId", "PL-48213-3", "PL-31337-9")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(grouped));
    }

    @Test
    void adminCanRedownloadTheExactOriginalSheetPdf() throws Exception {
        byte[] original = new byte[]{'%', 'P', 'D', 'F', 9, 1};
        when(datasetService.readSheetPdf("PL-48213-3")).thenReturn(original);

        mockMvc.perform(get("/api/admin/batches/dataset/{sheetId}/pdf", "PL-48213-3")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("dataset-PL-48213-3.pdf")))
                .andExpect(content().bytes(original));
    }

    @Test
    void adminCanStoreARawCropWithItsLabel() throws Exception {
        when(cropService.store(eq("PL-48213-3"), any(), any(), any())).thenReturn(cropDto());

        mockMvc.perform(multipart("/api/admin/batches/dataset/{sheetId}/crops", "PL-48213-3")
                        .file(new MockMultipartFile("image", "strip.png", "image/png", new byte[]{1, 2, 3}))
                        .param("position", "1")
                        .param("label", "DDDDD-EEEEE-FFFFF")
                        .param("captureDevice", "Pixel 7a")
                        .param("captureWidthPx", "1920")
                        .param("captureHeightPx", "1080")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.status").value("LABELLED"));

        verify(cropService).store(eq("PL-48213-3"), any(), any(), any());
    }

    @Test
    void adminCanStoreACropTheOperatorFramedByHand() throws Exception {
        when(cropService.store(eq("PL-48213-3"), any(), any(), any())).thenReturn(cropDto());

        mockMvc.perform(multipart("/api/admin/batches/dataset/{sheetId}/crops", "PL-48213-3")
                        .file(new MockMultipartFile("image", "strip.png", "image/png", new byte[]{1, 2, 3}))
                        .param("position", "1")
                        .param("label", "DDDDD-EEEEE-FFFFF")
                        .param("framing", "MANUAL")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated());

        assertThat(capturedUpload().framing()).isEqualTo(CardDatasetCropFraming.MANUAL);
    }

    @Test
    void adminCanSendTheWholePhotographAndWhereTheCropSatInIt() throws Exception {
        // La photo et la region sont ce qu'un modele de cadrage aurait a apprendre. Les laisser
        // au bord de la route, c'est garder la reponse de l'operateur et jeter la question --
        // et une decoupe cadree a la main est justement un echec de la segmentation corrige.
        when(cropService.store(eq("PL-48213-3"), any(), any(), any())).thenReturn(cropDto());

        mockMvc.perform(multipart("/api/admin/batches/dataset/{sheetId}/crops", "PL-48213-3")
                        .file(new MockMultipartFile("image", "strip.png", "image/png", new byte[]{1, 2, 3}))
                        .file(new MockMultipartFile("frame", "frame.jpg", "image/jpeg", new byte[]{4, 5, 6}))
                        .param("position", "1")
                        .param("label", "DDDDD-EEEEE-FFFFF")
                        .param("framing", "MANUAL")
                        .param("regionXPx", "90")
                        .param("regionYPx", "480")
                        .param("regionWidthPx", "900")
                        .param("regionHeightPx", "110")
                        .param("regionAngleDeg", "-1.5")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated());

        CardDatasetCropUploadRequest upload = capturedUpload();
        assertThat(upload.regionXPx()).isEqualTo(90);
        assertThat(upload.regionYPx()).isEqualTo(480);
        assertThat(upload.regionWidthPx()).isEqualTo(900);
        assertThat(upload.regionHeightPx()).isEqualTo(110);
        assertThat(upload.regionAngleDeg()).isEqualTo(-1.5);
    }

    @Test
    void aCropSentWithoutSayingHowItWasFramedReachesTheServiceAsAutomatic() throws Exception {
        // Les front-ends deja en place n'envoient pas ce parametre : leur silence vaut
        // "cadrage automatique", decide ici et pas laisse a null pour la suite de la chaine.
        when(cropService.store(eq("PL-48213-3"), any(), any(), any())).thenReturn(cropDto());

        mockMvc.perform(multipart("/api/admin/batches/dataset/{sheetId}/crops", "PL-48213-3")
                        .file(new MockMultipartFile("image", "strip.png", "image/png", new byte[]{1, 2, 3}))
                        .param("position", "1")
                        .param("label", "DDDDD-EEEEE-FFFFF")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated());

        assertThat(capturedUpload().framing()).isEqualTo(CardDatasetCropFraming.AUTOMATIC);
    }

    @Test
    void aFramingValueNobodyDefinedIsRefusedInsteadOfPassingForAutomatic() throws Exception {
        // Une decoupe rangee a tort parmi les cadrages automatiques dilue silencieusement le lot
        // des cas difficiles. Mieux vaut un 400 bruyant qu'une etiquette fausse.
        mockMvc.perform(multipart("/api/admin/batches/dataset/{sheetId}/crops", "PL-48213-3")
                        .file(new MockMultipartFile("image", "strip.png", "image/png", new byte[]{1, 2, 3}))
                        .param("position", "1")
                        .param("framing", "SEMI_AUTOMATIC")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cropService);
    }

    @Test
    void adminCanRecordAnArbitration() throws Exception {
        UUID cropId = UUID.randomUUID();
        when(cropService.label(cropId, "DDDDD-EEEEE-FFFFF")).thenReturn(cropDto());

        mockMvc.perform(patch("/api/admin/batches/dataset/crops/{cropId}/label", cropId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"DDDDD-EEEEE-FFFFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("DDDDD-EEEEE-FFFFF"));

        verify(cropService).label(cropId, "DDDDD-EEEEE-FFFFF");
    }

    @Test
    void adminCanDiscardACropTheyJudgedUnusable() throws Exception {
        UUID cropId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/batches/dataset/crops/{cropId}", cropId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(cropService).discard(cropId);
    }

    @Test
    void discardingACropTwiceAnswers204Twice() throws Exception {
        // Le pouce derape sur un ecran tactile, la requete part deux fois, le reseau la rejoue :
        // le second appel constate l'etat deja atteint. 204 les deux fois, jamais une erreur --
        // l'operateur ne doit pas avoir a distinguer les deux cas au milieu d'un lot.
        UUID cropId = UUID.randomUUID();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/admin/batches/dataset/crops/{cropId}", cropId)
                            .with(user("admin").roles("ADMIN"))
                            .with(csrf().asHeader()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        verify(cropService, org.mockito.Mockito.times(2)).discard(cropId);
    }

    @Test
    void discardingACropThatNeverExistedIsNotFound() throws Exception {
        UUID cropId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new fr.insastronaute.webapi.common.web.NotFoundException("Découpe de dataset introuvable."))
                .when(cropService).discard(cropId);

        mockMvc.perform(delete("/api/admin/batches/dataset/crops/{cropId}", cropId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanPutADiscardedCropBack() throws Exception {
        // Le chemin de retour. Sans lui, ecarter serait une suppression deguisee.
        UUID cropId = UUID.randomUUID();
        when(cropService.restore(cropId)).thenReturn(cropDto());

        mockMvc.perform(post("/api/admin/batches/dataset/crops/{cropId}/restore", cropId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LABELLED"));

        verify(cropService).restore(cropId);
    }

    @Test
    void aPlainPlayerCannotDiscardACrop() throws Exception {
        mockMvc.perform(delete("/api/admin/batches/dataset/crops/{cropId}", UUID.randomUUID())
                        .with(user("joueur").roles("USER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cropService);
    }

    @Test
    void adminCanReadBackTheRawCropBytes() throws Exception {
        UUID cropId = UUID.randomUUID();
        when(cropService.readImage(cropId))
                .thenReturn(new CardDatasetCropService.StoredCropImage(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/admin/batches/dataset/crops/{cropId}/image", cropId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void adminCanDownloadTheDatasetSplitByPhysicalStrip() throws Exception {
        when(exportService.exportArchive(false)).thenReturn(new byte[]{'P', 'K', 3, 4});

        mockMvc.perform(get("/api/admin/batches/dataset/export")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/zip")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("card-dataset.zip")))
                .andExpect(content().bytes(new byte[]{'P', 'K', 3, 4}));
    }

    /**
     * Les photos entieres ne partent que si on les demande : elles pesent bien plus que les
     * decoupes, et l'entrainement d'un lecteur n'en a aucun besoin. Le nom du fichier les
     * annonce, sans quoi deux archives tres differentes arriveraient sous le meme nom dans
     * le dossier de telechargement.
     */
    @Test
    void adminCanAskForTheWholePhotosToo() throws Exception {
        when(exportService.exportArchive(true)).thenReturn(new byte[]{'P', 'K', 3, 4});

        mockMvc.perform(get("/api/admin/batches/dataset/export").param("frames", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("card-dataset-frames.zip")));
    }

    @Test
    void aPlainPlayerCannotDownloadTheDataset() throws Exception {
        mockMvc.perform(get("/api/admin/batches/dataset/export")
                        .with(user("joueur").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPlainPlayerCannotReachTheDataset() throws Exception {
        mockMvc.perform(get("/api/admin/batches/dataset/{sheetId}/codes", "PL-48213-3")
                        .with(user("joueur").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private CardDatasetCropUploadRequest capturedUpload() {
        ArgumentCaptor<CardDatasetCropUploadRequest> captor =
                ArgumentCaptor.forClass(CardDatasetCropUploadRequest.class);
        verify(cropService).store(eq("PL-48213-3"), captor.capture(), any(), any());
        return captor.getValue();
    }

    private CardDatasetCropDto cropDto() {
        return new CardDatasetCropDto(
                UUID.randomUUID(),
                "PL-48213-3",
                UUID.randomUUID(),
                1,
                "DDDDD-EEEEE-FFFFF",
                "DDDDD-EEEEE-FFFFF",
                CardDatasetCropStatus.LABELLED,
                CardDatasetCropFraming.AUTOMATIC,
                "image/png",
                4096L,
                312,
                78,
                "Pixel 7a",
                1920,
                1080,
                Instant.parse("2026-08-24T10:00:00Z")
        );
    }
}
