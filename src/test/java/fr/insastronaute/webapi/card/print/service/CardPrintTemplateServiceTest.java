package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.print.domain.CardPrintFitMode;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintQrCorner;
import fr.insastronaute.webapi.card.print.domain.CardPrintTextAlign;
import fr.insastronaute.webapi.card.print.dto.CardPrintCodeSettingsRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateCalibrationRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateSlotRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintQrSettingsRequest;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardPrintTemplateServiceTest {

    @Mock
    private CardPrintTemplateRepository templateRepository;

    @Mock
    private CardPrintRunRepository printRunRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardPrintStorageService storageService;

    @Mock
    private CardPrintPdfRenderer pdfRenderer;

    @InjectMocks
    private CardPrintTemplateService templateService;

    @Test
    void calibrationUpdatesExistingSlotsInsteadOfRecreatingThem() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = calibratedTemplate(templateId);
        List<UUID> originalSlotIds = template.getSlots().stream()
                .map(CardPrintTemplateSlot::getId)
                .toList();

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(CardPrintTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPrintTemplateDto updated = templateService.updateCalibration(templateId, calibrationRequest(45D));

        assertThat(template.getSlots()).hasSize(9);
        assertThat(updated.slots()).hasSize(9);
        assertThat(updated.slots()).extracting(slot -> slot.id()).containsExactlyElementsOf(originalSlotIds);
        assertThat(updated.slots().getFirst().xPt()).isEqualTo(45D);
    }

    @Test
    void calibrationPersistsIndependentBackSlotsAndQrSettings() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = calibratedTemplate(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(CardPrintTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPrintTemplateCalibrationRequest request = calibrationRequest(45D, true);
        CardPrintTemplateDto updated = templateService.updateCalibration(templateId, request);

        assertThat(updated.backSlots()).hasSize(9);
        assertThat(updated.backSlots().getFirst().xPt()).isEqualTo(245D);
        assertThat(updated.qr().enabled()).isTrue();
        assertThat(updated.qr().corner()).isEqualTo(CardPrintQrCorner.BOTTOM_RIGHT);
        assertThat(updated.qr().sizePt()).isEqualTo(32D);
        assertThat(updated.qr().marginModules()).isEqualTo(2);
        assertThat(updated.qr().insetPt()).isEqualTo(7D);
    }

    @Test
    void legacyCalibrationMayLeaveBackSlotsAndQrAbsent() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = calibratedTemplate(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(CardPrintTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPrintTemplateDto updated = templateService.updateCalibration(templateId, calibrationRequest(45D));

        assertThat(updated.backSlots()).isEmpty();
        assertThat(updated.qr().enabled()).isFalse();
    }

    @Test
    void qrEnabledTemplateIsNotReadyWithoutNineBackSlots() {
        CardPrintTemplate template = calibratedTemplate(UUID.randomUUID());
        template.setQrEnabled(true);

        assertThatThrownBy(() -> templateService.validateTemplateReady(template, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le patron d'impression doit contenir 9 emplacements verso calibrés.");
    }

    @Test
    void deletesUnusedTemplateAndStoredPdf() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = template(templateId);

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(printRunRepository.existsByTemplateId(templateId)).thenReturn(false);

        templateService.delete(templateId);

        verify(templateRepository).delete(template);
        verify(storageService).deletePublicObjectQuietly("card-print/templates/template/template.pdf");
    }

    @Test
    void rejectsDeleteWhenTemplateWasUsedByPrintRun() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = template(templateId);

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(printRunRepository.existsByTemplateId(templateId)).thenReturn(true);

        assertThatThrownBy(() -> templateService.delete(templateId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ce patron a déjà été utilisé pour un tirage et ne peut pas être supprimé.");

        verify(templateRepository, never()).delete(any());
        verify(storageService, never()).deletePublicObjectQuietly(any());
    }

    @Test
    void deletesMultipleUnusedTemplates() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        CardPrintTemplate firstTemplate = template(firstId);
        firstTemplate.setPdfObjectKey("card-print/templates/first/template.pdf");
        CardPrintTemplate secondTemplate = template(secondId);
        secondTemplate.setPdfObjectKey("card-print/templates/second/template.pdf");

        when(templateRepository.findById(firstId)).thenReturn(Optional.of(firstTemplate));
        when(templateRepository.findById(secondId)).thenReturn(Optional.of(secondTemplate));
        when(printRunRepository.existsByTemplateId(firstId)).thenReturn(false);
        when(printRunRepository.existsByTemplateId(secondId)).thenReturn(false);

        templateService.deleteAll(List.of(firstId, secondId));

        verify(templateRepository).delete(firstTemplate);
        verify(templateRepository).delete(secondTemplate);
        verify(storageService).deletePublicObjectQuietly("card-print/templates/first/template.pdf");
        verify(storageService).deletePublicObjectQuietly("card-print/templates/second/template.pdf");
    }

    private CardPrintTemplate calibratedTemplate(UUID templateId) {
        CardPrintTemplate template = template(templateId);
        IntStream.range(0, 9).forEach(index -> {
            CardPrintTemplateSlot slot = new CardPrintTemplateSlot();
            slot.setId(UUID.randomUUID());
            slot.setSlotIndex(index);
            slot.setXPt(30D + index);
            slot.setYPt(40D + index);
            slot.setWidthPt(100D);
            slot.setHeightPt(140D);
            template.addSlot(slot);
        });
        return template;
    }

    private CardPrintTemplate template(UUID templateId) {
        CardPrintTemplate template = new CardPrintTemplate();
        template.setId(templateId);
        template.setName("Patron");
        template.setPdfObjectKey("card-print/templates/template/template.pdf");
        template.setPdfUrl("https://s3.example.com/template.pdf");
        template.setPageCount(2);
        template.setPageWidthPt(595D);
        template.setPageHeightPt(842D);
        return template;
    }

    private CardPrintTemplateCalibrationRequest calibrationRequest(Double firstSlotX) {
        return calibrationRequest(firstSlotX, false);
    }

    private CardPrintTemplateCalibrationRequest calibrationRequest(Double firstSlotX, boolean qrEnabled) {
        return new CardPrintTemplateCalibrationRequest(
                true,
                CardPrintFitMode.COVER,
                0D,
                IntStream.range(0, 9)
                        .mapToObj(index -> new CardPrintTemplateSlotRequest(
                                index,
                                index == 0 ? firstSlotX : 30D + index,
                                40D + index,
                                100D,
                                140D
                        ))
                        .toList(),
                new CardPrintCodeSettingsRequest(
                        0D,
                        0D,
                        80D,
                        12D,
                        CardPrintFontFamily.HELVETICA,
                        CardPrintFontWeight.BOLD,
                        9D,
                        "#111111",
                        null,
                        0D,
                        CardPrintTextAlign.CENTER
                ),
                qrEnabled
                        ? IntStream.range(0, 9)
                                .mapToObj(index -> new CardPrintTemplateSlotRequest(
                                        index,
                                        index == 0 ? 245D : 230D + index,
                                        40D + index,
                                        100D,
                                        140D
                                ))
                                .toList()
                        : null,
                qrEnabled
                        ? new CardPrintQrSettingsRequest(true, CardPrintQrCorner.BOTTOM_RIGHT, 32D, 2, 7D)
                        : null
        );
    }
}
