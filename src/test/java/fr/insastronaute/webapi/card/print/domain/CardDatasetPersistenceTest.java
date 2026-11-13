package fr.insastronaute.webapi.card.print.domain;

import fr.insastronaute.webapi.card.print.repository.CardDatasetCropRepository;
import fr.insastronaute.webapi.card.print.repository.CardDatasetSheetRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class CardDatasetPersistenceTest {

    @Autowired
    private CardDatasetSheetRepository sheetRepository;

    @Autowired
    private CardDatasetCropRepository cropRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aSheetHandsBackItsCodesInThePrintedOrderAfterAReload() {
        CardDatasetSheet sheet = sheetWithCodes("PL-10004-0", "AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF", "GGGGG-HHHHH-JJJJJ");
        sheetRepository.saveAndFlush(sheet);
        entityManager.clear();

        CardDatasetSheet reloaded = sheetRepository.findWithCodesBySheetId("PL-10004-0").orElseThrow();

        assertThat(reloaded.getCodes()).extracting(CardDatasetSheetCode::getPosition).containsExactly(0, 1, 2);
        assertThat(reloaded.getCodes()).extracting(CardDatasetSheetCode::getCode)
                .containsExactly("AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF", "GGGGG-HHHHH-JJJJJ");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void aCropIsRetrievableWithItsSheetItsPositionAndItsPhysicalStrip() {
        CardDatasetSheet sheet = sheetWithCodes("PL-10005-7", "AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF");
        sheetRepository.saveAndFlush(sheet);
        CardDatasetSheetCode strip = sheet.getCodes().get(1);

        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setSheet(sheet);
        crop.setStrip(strip);
        crop.setLabel("DDDDD-EEEEE-FFFFF");
        crop.setStatus(CardDatasetCropStatus.LABELLED);
        crop.setImageObjectKey("card-dataset/crops/PL-10005-7/one.png");
        crop.setImageContentType("image/png");
        crop.setImageByteSize(4096L);
        crop.setImageWidthPx(312);
        crop.setImageHeightPx(78);
        crop.setCaptureDevice("Pixel 7a");
        crop.setCaptureWidthPx(1920);
        crop.setCaptureHeightPx(1080);
        cropRepository.saveAndFlush(crop);
        entityManager.clear();

        List<CardDatasetCrop> crops = cropRepository.findBySheetOrderByCreatedAtAsc(
                sheetRepository.findWithCodesBySheetId("PL-10005-7").orElseThrow());

        assertThat(crops).hasSize(1);
        CardDatasetCrop reloaded = crops.getFirst();
        assertThat(reloaded.getSheet().getSheetId()).isEqualTo("PL-10005-7");
        assertThat(reloaded.getStrip().getId()).isEqualTo(strip.getId());
        assertThat(reloaded.getStrip().getPosition()).isEqualTo(1);
        assertThat(reloaded.getLabel()).isEqualTo("DDDDD-EEEEE-FFFFF");
        assertThat(reloaded.getImageObjectKey()).isEqualTo("card-dataset/crops/PL-10005-7/one.png");
        assertThat(reloaded.getImageWidthPx()).isEqualTo(312);
        assertThat(reloaded.getCaptureDevice()).isEqualTo("Pixel 7a");
        // Personne n'a dit comment la decoupe a ete cadree : c'est donc la segmentation
        // automatique, jamais un trou. La colonne ne peut pas etre nulle.
        assertThat(reloaded.getFraming()).isEqualTo(CardDatasetCropFraming.AUTOMATIC);
    }

    @Test
    void twoSheetsCannotShareAPrintedIdentifier() {
        sheetRepository.saveAndFlush(sheetWithCodes("PL-10006-5", "AAAAA-BBBBB-CCCCC"));

        assertThatThrownBy(() -> sheetRepository.saveAndFlush(sheetWithCodes("PL-10006-5", "DDDDD-EEEEE-FFFFF")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onePositionOfASheetCarriesOneCode() {
        CardDatasetSheet sheet = sheetWithCodes("PL-10007-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetSheetCode duplicate = new CardDatasetSheetCode();
        duplicate.setPosition(0);
        duplicate.setCode("DDDDD-EEEEE-FFFFF");
        sheet.addCode(duplicate);

        assertThatThrownBy(() -> sheetRepository.saveAndFlush(sheet))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sheetHistoryCountsEveryStoredScanAndKeepsLegacySheetsVisible() {
        CardDatasetSheet legacy = sheetWithCodes("PL-10008-1", "AAAAA-BBBBB-CCCCC");
        legacy.setCreatedAt(Instant.parse("2026-08-24T10:00:00Z"));
        sheetRepository.saveAndFlush(legacy);
        cropRepository.saveAndFlush(crop(legacy, legacy.getCodes().getFirst(), "legacy-1.png"));

        CardDatasetSheet archived = sheetWithCodes("PL-10009-9", "DDDDD-EEEEE-FFFFF");
        archived.setCreatedAt(Instant.parse("2026-08-25T10:00:00Z"));
        archived.setPdfObjectKey("card-print/dataset-sheets/archived.pdf");
        sheetRepository.saveAndFlush(archived);
        cropRepository.saveAndFlush(crop(archived, archived.getCodes().getFirst(), "archived-1.png"));
        cropRepository.saveAndFlush(crop(archived, archived.getCodes().getFirst(), "archived-2.png"));
        entityManager.clear();

        List<CardDatasetSheetRepository.CardDatasetSheetSummaryProjection> summaries =
                sheetRepository.findAllSummaries(PageRequest.of(0, 100));

        assertThat(summaries).extracting(CardDatasetSheetRepository.CardDatasetSheetSummaryProjection::getId)
                .containsExactly(archived.getId(), legacy.getId());
        assertThat(summaries).extracting(CardDatasetSheetRepository.CardDatasetSheetSummaryProjection::getScanCount)
                .containsExactly(2L, 1L);
        assertThat(summaries).extracting(CardDatasetSheetRepository.CardDatasetSheetSummaryProjection::getDownloadable)
                .containsExactly(true, false);
    }

    private CardDatasetCrop crop(CardDatasetSheet sheet, CardDatasetSheetCode strip, String filename) {
        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setSheet(sheet);
        crop.setStrip(strip);
        crop.setImageObjectKey("card-dataset/crops/" + filename);
        return crop;
    }

    private CardDatasetSheet sheetWithCodes(String sheetId, String... codes) {
        CardDatasetSheet sheet = new CardDatasetSheet();
        sheet.setId(UUID.randomUUID());
        sheet.setSheetId(sheetId);
        int position = 0;
        for (String code : codes) {
            CardDatasetSheetCode sheetCode = new CardDatasetSheetCode();
            sheetCode.setId(UUID.randomUUID());
            sheetCode.setPosition(position++);
            sheetCode.setCode(code);
            sheet.addCode(sheetCode);
        }
        return sheet;
    }
}
