package fr.insastronaute.webapi.card.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetCode;
import fr.insastronaute.webapi.card.print.repository.CardDatasetCropRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardDatasetExportServiceTest {

    @Mock
    private CardDatasetCropRepository cropRepository;

    @Mock
    private CardDatasetCropStorageService storageService;

    private CardDatasetExportService exportService() {
        return new CardDatasetExportService(cropRepository, storageService, new ObjectMapper());
    }

    @Test
    void noPhysicalStripEverAppearsOnBothSidesOfTheSplit() {
        // Le coeur de la tache. Une bande physique est un bout de papier unique, photographie
        // plusieurs fois -- angles, distances, lumieres differentes. Si deux photos d'une meme
        // bande tombent de part et d'autre de la separation, la validation note un modele sur
        // du papier qu'il a deja vu : elle mesure de la memorisation et annonce un score que le
        // terrain dementira. La separation se fait donc par bande, jamais par image.
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(40, 5);
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        Map<String, Set<String>> stripsBySplit = new LinkedHashMap<>();
        for (Map<String, String> row : rows(archive, "labels.csv")) {
            stripsBySplit.computeIfAbsent(row.get("split"), key -> new java.util.HashSet<>())
                    .add(row.get("slot"));
        }
        for (Map<String, String> row : rows(archive, "held-out/crops.csv")) {
            stripsBySplit.computeIfAbsent(row.get("split"), key -> new java.util.HashSet<>())
                    .add(row.get("slot"));
        }

        assertThat(stripsBySplit.keySet()).containsExactlyInAnyOrder("train", "val", "test");
        Set<String> train = stripsBySplit.get("train");
        Set<String> validation = stripsBySplit.get("val");
        Set<String> test = stripsBySplit.get("test");
        assertThat(train).isNotEmpty();
        assertThat(validation).isNotEmpty();
        assertThat(test).isNotEmpty();
        assertThat(train).doesNotContainAnyElementsOf(validation);
        assertThat(train).doesNotContainAnyElementsOf(test);
        assertThat(validation).doesNotContainAnyElementsOf(test);
        assertThat(train.size() + validation.size() + test.size()).isEqualTo(40);
    }

    @Test
    void everyPhotoOfOneStripLandsInTheSameSplit() {
        // Le corollaire, dit du point de vue de l'image : cinq photos d'une bande, un seul cote.
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(30, 5);
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        Map<String, Set<String>> splitsByStrip = new LinkedHashMap<>();
        for (Map<String, String> row : allLabelledRows(archive)) {
            splitsByStrip.computeIfAbsent(row.get("slot"), key -> new java.util.HashSet<>())
                    .add(row.get("split"));
        }
        assertThat(splitsByStrip).hasSize(30);
        assertThat(splitsByStrip.values()).allSatisfy(splits -> assertThat(splits).hasSize(1));
    }

    @Test
    void theHeldOutSetIsDecidedByStripIdentityAndNotRedrawnWhenTheDatasetGrows() {
        // Un jeu de test qu'on retire au sort a chaque export n'est pas mis de cote : la bande
        // qui etait en test hier entraine aujourd'hui. L'appartenance est donc une fonction de
        // l'identite de la bande -- fixee a l'impression de la planche, avant qu'une seule photo
        // n'existe -- et rien d'autre : ni le nombre de photos, ni l'ordre, ni la date.
        List<CardDatasetCrop> first = manyPhotosOfManyStrips(40, 2);
        when(cropRepository.findAllForExport()).thenReturn(first);
        stubImages();
        Set<String> heldOutBefore = stripsIn(unzip(exportService().exportArchive()), "held-out/crops.csv");

        List<CardDatasetCrop> grown = new ArrayList<>(first);
        for (CardDatasetCrop crop : first) {
            grown.add(photoOf(crop.getStrip(), crop.getLabel()));
        }
        java.util.Collections.reverse(grown);
        when(cropRepository.findAllForExport()).thenReturn(grown);
        Set<String> heldOutAfter = stripsIn(unzip(exportService().exportArchive()), "held-out/crops.csv");

        assertThat(heldOutBefore).isNotEmpty();
        assertThat(heldOutAfter).isEqualTo(heldOutBefore);
    }

    @Test
    void twoStripsPrintingTheSameCodeNeverStraddleTheSplit() {
        // Regenerer la planche d'un lot enregistre une planche neuve avec les memes codes :
        // deux bouts de papier differents, la meme suite de dix-sept caracteres. Les separer
        // laisserait la validation reconnaitre une chaine vue a l'entrainement -- un autre
        // papier, mais le meme mot, sorti de la meme imprimante. La cle groupe donc les bandes
        // qui portent le meme code, sans jamais couper une bande en deux.
        CardDatasetSheet first = sheet("PL-48213-3", 12);
        CardDatasetSheet reprint = sheet("PL-90210-8", 12);
        for (int position = 0; position < 12; position++) {
            reprint.getCodes().get(position).setCode(first.getCodes().get(position).getCode());
        }
        List<CardDatasetCrop> crops = new ArrayList<>();
        for (CardDatasetSheet printed : List.of(first, reprint)) {
            for (CardDatasetSheetCode strip : printed.getCodes()) {
                crops.add(photoOf(strip, strip.getCode()));
                crops.add(photoOf(strip, strip.getCode()));
            }
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        Map<String, Set<String>> splitsByCode = new LinkedHashMap<>();
        Map<String, Set<String>> splitsByStrip = new LinkedHashMap<>();
        for (Map<String, String> row : allLabelledRows(archive)) {
            splitsByCode.computeIfAbsent(row.get("code"), key -> new java.util.HashSet<>()).add(row.get("split"));
            splitsByStrip.computeIfAbsent(row.get("slot"), key -> new java.util.HashSet<>()).add(row.get("split"));
        }
        assertThat(splitsByCode).hasSize(12);
        assertThat(splitsByStrip).hasSize(24);
        assertThat(splitsByCode.values()).allSatisfy(splits -> assertThat(splits).hasSize(1));
        assertThat(splitsByStrip.values()).allSatisfy(splits -> assertThat(splits).hasSize(1));
    }

    @Test
    void aCropAwaitingArbitrationNeverEntersATrainingOrValidationSplit() {
        // PENDING_ARBITRATION veut dire qu'aucun code de la planche n'explique la lecture :
        // l'etiquette est inconnue, pas approximative. L'exporter avec le code attendu de sa
        // position, ce serait entrainer sur une etiquette fausse. Elle est donc sortie des
        // splits etiquetes -- et jamais jetee : c'est l'exemple que le modele ne sait pas lire.
        CardDatasetSheet sheet = sheet("PL-48213-3", 2);
        CardDatasetCrop labelled = photoOf(sheet.getCodes().get(0), sheet.getCodes().get(0).getCode());
        CardDatasetCrop pending = photoOf(sheet.getCodes().get(1), null);
        when(cropRepository.findAllForExport()).thenReturn(List.of(labelled, pending));
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        List<String> labelledCropIds = allLabelledRows(archive).stream()
                .map(row -> row.get("crop_id"))
                .toList();
        assertThat(labelledCropIds).containsExactly(labelled.getId().toString());

        List<Map<String, String>> arbitration = rows(archive, "arbitration/crops.csv");
        assertThat(arbitration).hasSize(1);
        assertThat(arbitration.get(0).get("crop_id")).isEqualTo(pending.getId().toString());
        assertThat(archive).containsKey("arbitration/" + arbitration.get(0).get("image"));

        // Aucune colonne `code`, et aucun `labels.csv` : le chargeur d'entrainement ne peut pas
        // lire ce dossier par megarde, il echoue bruyamment.
        assertThat(arbitration.get(0)).doesNotContainKey("code");
        assertThat(arbitration.get(0)).containsKey("expected_code");
        assertThat(archive).doesNotContainKey("arbitration/labels.csv");
    }

    @Test
    void theHeldOutSetCannotBeLoadedAsATrainingRootByAccident() {
        // `finetune_crnn_real.py --data <racine>` lit `<racine>/labels.csv` puis repartitionne
        // lui-meme en train/val. Poser un `labels.csv` sous `held-out/` suffirait donc a ce
        // qu'une ligne de commande distraite entraine sur le jeu tenu a l'ecart.
        when(cropRepository.findAllForExport()).thenReturn(manyPhotosOfManyStrips(40, 2));
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        assertThat(archive).containsKey("held-out/crops.csv");
        assertThat(archive).doesNotContainKey("held-out/labels.csv");
        assertThat(rows(archive, "labels.csv")).isNotEmpty();
        assertThat(rows(archive, "labels.csv"))
                .allSatisfy(row -> assertThat(row.get("split")).isIn("train", "val"));
    }

    @Test
    void theArchiveCarriesTheShapeTheTrainingScriptAlreadyConsumes() {
        // `labels.csv` avec les colonnes image,code,split,source_photo,slot, et les images a
        // l'emplacement relatif que la colonne `image` designe. C'est ce que produit
        // `extract_printed_sheets.py` et ce que lit `RealCodeStore`.
        when(cropRepository.findAllForExport()).thenReturn(manyPhotosOfManyStrips(20, 3));
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        String header = new String(archive.get("labels.csv"), StandardCharsets.UTF_8).lines().findFirst().orElseThrow();
        assertThat(header).startsWith("image,code,split,source_photo,slot");

        for (Map<String, String> row : rows(archive, "labels.csv")) {
            assertThat(archive).containsKey(row.get("image"));
            assertThat(row.get("image")).startsWith(row.get("split").equals("train") ? "train/" : "val/");
            assertThat(row.get("code")).matches("[0-9A-Z]{5}-[0-9A-Z]{5}-[0-9A-Z]{5}");
            assertThat(row.get("source_photo")).isNotBlank();
        }
        for (Map<String, String> row : rows(archive, "held-out/crops.csv")) {
            assertThat(archive).containsKey("held-out/" + row.get("image"));
            assertThat(row.get("image")).startsWith("test/");
        }
        assertThat(archive).containsKey("report.json");
    }

    @Test
    void everyManifestSaysWhichCropsTheOperatorHadToFrameByHand() {
        // Une decoupe cadree a la main est une decoupe que la segmentation automatique n'a pas
        // su traiter : le cas difficile, designe gratuitement par le geste de l'operateur. Si
        // l'export ne porte pas cette colonne, le signal existe en base et n'atteint jamais
        // l'entrainement -- une colonne que personne ne peut lire est un signal que personne
        // ne peut utiliser. Elle doit donc etre dans les trois manifestes, y compris celui de
        // l'arbitrage : c'est la que les cas difficiles se concentrent.
        List<CardDatasetCrop> crops = new ArrayList<>();
        CardDatasetSheet sheet = sheet("PL-48213-3", 40);
        for (CardDatasetSheetCode strip : sheet.getCodes()) {
            CardDatasetCrop automatic = photoOf(strip, strip.getCode());
            CardDatasetCrop manual = photoOf(strip, strip.getCode());
            manual.setFraming(CardDatasetCropFraming.MANUAL);
            CardDatasetCrop pending = photoOf(strip, null);
            pending.setFraming(CardDatasetCropFraming.MANUAL);
            crops.add(automatic);
            crops.add(manual);
            crops.add(pending);
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        Map<String, String> framingByCropId = new LinkedHashMap<>();
        for (Map<String, String> row : allLabelledRows(archive)) {
            framingByCropId.put(row.get("crop_id"), row.get("framing"));
        }
        for (Map<String, String> row : rows(archive, "arbitration/crops.csv")) {
            framingByCropId.put(row.get("crop_id"), row.get("framing"));
        }

        assertThat(framingByCropId).hasSize(crops.size());
        for (CardDatasetCrop crop : crops) {
            assertThat(framingByCropId.get(crop.getId().toString()))
                    .as("cadrage de la decoupe %s", crop.getId())
                    .isEqualTo(crop.getFraming().name());
        }
        assertThat(framingByCropId.values()).contains("MANUAL", "AUTOMATIC");
        assertThat(new String(archive.get("labels.csv"), StandardCharsets.UTF_8).lines().findFirst().orElseThrow())
                .contains("framing");
        assertThat(new String(archive.get("held-out/crops.csv"), StandardCharsets.UTF_8).lines().findFirst().orElseThrow())
                .contains("framing");
        assertThat(new String(archive.get("arbitration/crops.csv"), StandardCharsets.UTF_8).lines().findFirst().orElseThrow())
                .contains("framing");
    }

    @Test
    void theReportSaysHowTheSplitWasMadeAndWhatWasKeptOutOfIt() {
        List<CardDatasetCrop> crops = new ArrayList<>(manyPhotosOfManyStrips(20, 2));
        CardDatasetSheet extra = sheet("PL-11111-5", 1);
        crops.add(photoOf(extra.getCodes().get(0), null));
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());
        Map<String, Object> report = readJson(archive.get("report.json"));

        assertThat(report.get("split_unit")).isEqualTo("physical_strip");
        assertThat(report.get("crops_total")).isEqualTo(41);
        assertThat(report.get("crops_pending_arbitration")).isEqualTo(1);
        // La bande dont la seule photo attend un arbitrage existe, mais n'est dans aucun split :
        // le total la compte, la somme des splits non.
        assertThat(report.get("strips_total")).isEqualTo(21);
        assertThat(report.get("strips_labelled")).isEqualTo(20);
        @SuppressWarnings("unchecked")
        Map<String, Object> crossings = (Map<String, Object>) report.get("strips_by_split");
        assertThat(crossings.keySet()).contains("train", "val", "test");
        assertThat(crossings.values().stream().mapToInt(value -> (Integer) value).sum()).isEqualTo(20);
    }

    @Test
    void aSheetWithNoLabelledCropAtAllStillProducesAReadableArchive() {
        CardDatasetSheet sheet = sheet("PL-48213-3", 1);
        when(cropRepository.findAllForExport()).thenReturn(List.of(photoOf(sheet.getCodes().get(0), null)));
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        assertThat(rows(archive, "labels.csv")).isEmpty();
        assertThat(new String(archive.get("labels.csv"), StandardCharsets.UTF_8))
                .startsWith("image,code,split,source_photo,slot");
        assertThat(rows(archive, "arbitration/crops.csv")).hasSize(1);
    }

    @Test
    void aCropTheOperatorDiscardedNeverReachesAnyManifest() {
        // L'assertion qui compte. L'operateur a regarde la photo et l'a jugee inutilisable :
        // floue, mal cadree, la mauvaise bande. Elle reste en base pour pouvoir revenir, mais
        // elle ne doit atteindre aucun entrainement -- ni labels.csv, ni le jeu tenu a l'ecart,
        // ni le manifeste d'arbitrage. Une decoupe ecartee qui ressort par la porte de
        // l'arbitrage serait exactement le bug que ce test existe pour interdire.
        CardDatasetSheet sheet = sheet("PL-48213-3", 3);
        CardDatasetCrop kept = photoOf(sheet.getCodes().get(0), sheet.getCodes().get(0).getCode());
        CardDatasetCrop pending = photoOf(sheet.getCodes().get(1), null);
        CardDatasetCrop discardedLabelled = photoOf(sheet.getCodes().get(2), sheet.getCodes().get(2).getCode());
        discardedLabelled.setStatus(CardDatasetCropStatus.DISCARDED);
        // Une decoupe ecartee alors qu'elle n'avait pas d'etiquette : sans exclusion explicite,
        // elle retomberait dans l'arbitrage par la seule absence de code.
        CardDatasetCrop discardedUnlabelled = photoOf(sheet.getCodes().get(2), null);
        discardedUnlabelled.setStatus(CardDatasetCropStatus.DISCARDED);
        when(cropRepository.findAllForExport())
                .thenReturn(List.of(kept, pending, discardedLabelled, discardedUnlabelled));
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        List<String> everyCropIdInTheArchive = new ArrayList<>();
        for (Map<String, String> row : allLabelledRows(archive)) {
            everyCropIdInTheArchive.add(row.get("crop_id"));
        }
        for (Map<String, String> row : rows(archive, "arbitration/crops.csv")) {
            everyCropIdInTheArchive.add(row.get("crop_id"));
        }

        assertThat(everyCropIdInTheArchive)
                .containsExactlyInAnyOrder(kept.getId().toString(), pending.getId().toString());
        assertThat(everyCropIdInTheArchive).doesNotContain(
                discardedLabelled.getId().toString(), discardedUnlabelled.getId().toString());

        // Et pas davantage leurs octets : rien de ce que l'operateur a ecarte n'est dans l'archive.
        for (String entry : archive.keySet()) {
            assertThat(entry)
                    .as("entree %s", entry)
                    .doesNotContain(discardedLabelled.getId().toString())
                    .doesNotContain(discardedUnlabelled.getId().toString());
        }
    }

    @Test
    void aStripWhoseEveryPhotoWasDiscardedIsNotCountedAmongTheStripsTheDatasetHolds() {
        // La bande n'a plus une seule photo utilisable : elle n'est dans aucun split, et la
        // compter parmi les bandes du jeu de donnees ferait croire a une couverture qui n'existe
        // plus.
        CardDatasetSheet sheet = sheet("PL-48213-3", 2);
        CardDatasetCrop kept = photoOf(sheet.getCodes().get(0), sheet.getCodes().get(0).getCode());
        CardDatasetCrop discarded = photoOf(sheet.getCodes().get(1), sheet.getCodes().get(1).getCode());
        discarded.setStatus(CardDatasetCropStatus.DISCARDED);
        when(cropRepository.findAllForExport()).thenReturn(List.of(kept, discarded));
        stubImages();

        Map<String, Object> report = readJson(unzip(exportService().exportArchive()).get("report.json"));

        assertThat(report.get("strips_total")).isEqualTo(1);
    }

    @Test
    void theReportSaysHowManyCropsTheOperatorDiscarded() {
        // Un jeu de donnees qui retrecit doit dire pourquoi. Sans ce compte, la seule trace de
        // dix captures jetees serait un total qui a baisse sans explication.
        CardDatasetSheet sheet = sheet("PL-48213-3", 4);
        List<CardDatasetCrop> crops = new ArrayList<>();
        crops.add(photoOf(sheet.getCodes().get(0), sheet.getCodes().get(0).getCode()));
        crops.add(photoOf(sheet.getCodes().get(1), null));
        for (int index = 2; index < 4; index++) {
            CardDatasetCrop discarded = photoOf(sheet.getCodes().get(index), sheet.getCodes().get(index).getCode());
            discarded.setStatus(CardDatasetCropStatus.DISCARDED);
            crops.add(discarded);
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, Object> report = readJson(unzip(exportService().exportArchive()).get("report.json"));

        assertThat(report.get("crops_discarded")).isEqualTo(2);
        // Le total est celui de ce que l'archive porte : les deux ecartees n'y sont pas, et
        // crops_discarded dit ou elles sont passees.
        assertThat(report.get("crops_total")).isEqualTo(2);
        assertThat(report.get("crops_pending_arbitration")).isEqualTo(1);
    }

    // --- fabrication ---

    private void stubImages() {
        when(storageService.read(anyString())).thenAnswer(invocation ->
                ("raw:" + invocation.getArgument(0)).getBytes(StandardCharsets.UTF_8));
    }

    private List<CardDatasetCrop> manyPhotosOfManyStrips(int stripCount, int photosPerStrip) {
        CardDatasetSheet sheet = sheet("PL-48213-3", stripCount);
        List<CardDatasetCrop> crops = new ArrayList<>();
        for (CardDatasetSheetCode strip : sheet.getCodes()) {
            for (int photo = 0; photo < photosPerStrip; photo++) {
                crops.add(photoOf(strip, strip.getCode()));
            }
        }
        return crops;
    }

    private CardDatasetSheet sheet(String sheetId, int stripCount) {
        CardDatasetSheet sheet = new CardDatasetSheet();
        sheet.setId(UUID.nameUUIDFromBytes(("sheet:" + sheetId).getBytes(StandardCharsets.UTF_8)));
        sheet.setSheetId(sheetId);
        sheet.setCreatedAt(Instant.parse("2026-08-24T10:00:00Z"));
        for (int position = 0; position < stripCount; position++) {
            CardDatasetSheetCode strip = new CardDatasetSheetCode();
            strip.setId(UUID.nameUUIDFromBytes(("strip:" + sheetId + ":" + position).getBytes(StandardCharsets.UTF_8)));
            strip.setPosition(position);
            strip.setCode(syntheticCode(sheetId, position));
            sheet.addCode(strip);
        }
        return sheet;
    }

    private String syntheticCode(String sheetId, int position) {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder compact = new StringBuilder();
        long seed = (sheetId.hashCode() * 31L) + position;
        for (int index = 0; index < 15; index++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            compact.append(alphabet.charAt((int) Math.floorMod(seed >> 17, 36)));
        }
        return compact.substring(0, 5) + "-" + compact.substring(5, 10) + "-" + compact.substring(10, 15);
    }

    private CardDatasetCrop photoOf(CardDatasetSheetCode strip, String label) {
        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setId(UUID.randomUUID());
        crop.setSheet(strip.getSheet());
        crop.setStrip(strip);
        crop.setLabel(label);
        crop.setStatus(label == null ? CardDatasetCropStatus.PENDING_ARBITRATION : CardDatasetCropStatus.LABELLED);
        crop.setFraming(CardDatasetCropFraming.AUTOMATIC);
        crop.setImageObjectKey("card-dataset/crops/" + strip.getSheet().getSheetId() + "/" + crop.getId() + ".png");
        crop.setImageContentType("image/png");
        crop.setImageByteSize(2048L);
        crop.setImageWidthPx(312);
        crop.setImageHeightPx(84);
        crop.setCaptureDevice("Pixel 7a, dos");
        crop.setCaptureWidthPx(1920);
        crop.setCaptureHeightPx(1080);
        crop.setCreatedAt(Instant.parse("2026-08-24T10:00:00Z"));
        return crop;
    }

    /**
     * Les colonnes {@code region_*} situent la bande dans la photo entiere. Sans cette photo,
     * elles ne situent rien : l'archive permet d'entrainer un lecteur, jamais un detecteur.
     */
    @Test
    void theWholePhotosShipWhenTheyAreAskedFor() {
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(4, 2);
        for (CardDatasetCrop crop : crops) {
            crop.setFrameObjectKey("card-dataset/frames/PL-48213-3/" + crop.getId() + ".jpg");
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive(true));

        for (CardDatasetCrop crop : crops) {
            assertThat(archive).containsKey(crop.getFrameObjectKey());
        }
    }

    /**
     * La photo est ecrite sous la cle que {@code frame_object_key} porte deja : c'est ce qui
     * permet de retrouver l'une depuis l'autre sans rien resoudre. Une colonne qui pointerait
     * a cote rendrait les photos inutilisables tout en paraissant livrees.
     */
    @Test
    void aManifestPointsAtThePhotoTheArchiveContains() {
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(3, 1);
        for (CardDatasetCrop crop : crops) {
            crop.setFrameObjectKey("card-dataset/frames/PL-48213-3/" + crop.getId() + ".jpg");
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive(true));

        for (Map<String, String> row : rows(archive, "labels.csv")) {
            String key = row.get("frame_object_key");
            if (key != null && !key.isBlank()) {
                assertThat(archive).containsKey(key);
            }
        }
    }

    /**
     * Une photo porte plusieurs bandes : les decoupes d'une meme planche la partagent. La
     * livrer une fois par decoupe multiplierait le poids de l'archive pour le meme contenu.
     */
    @Test
    void aPhotoSharedBySeveralCropsShipsOnce() {
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(4, 3);
        for (CardDatasetCrop crop : crops) {
            crop.setFrameObjectKey("card-dataset/frames/PL-48213-3/unique.jpg");
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive(true));

        assertThat(archive).containsKey("card-dataset/frames/PL-48213-3/unique.jpg");
        verify(storageService, times(1)).read("card-dataset/frames/PL-48213-3/unique.jpg");
    }

    /**
     * L'export ordinaire ne change pas : entrainer un lecteur n'a que faire des photos
     * entieres, et elles pesent bien plus que les decoupes.
     */
    @Test
    void theOrdinaryExportCarriesNoWholePhoto() {
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(4, 2);
        for (CardDatasetCrop crop : crops) {
            crop.setFrameObjectKey("card-dataset/frames/PL-48213-3/" + crop.getId() + ".jpg");
        }
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive());

        assertThat(archive.keySet()).noneMatch(entry -> entry.startsWith("card-dataset/frames/"));
    }

    /**
     * Les decoupes d'avant l'enregistrement des coordonnees n'ont pas de photo. Elles doivent
     * traverser l'export sans le faire echouer : refuser l'archive entiere pour cela
     * priverait l'entrainement de tout le reste.
     */
    @Test
    void aCropWithoutItsPhotoDoesNotStopTheExport() {
        List<CardDatasetCrop> crops = manyPhotosOfManyStrips(4, 2);
        crops.get(0).setFrameObjectKey("card-dataset/frames/PL-48213-3/seule.jpg");
        when(cropRepository.findAllForExport()).thenReturn(crops);
        stubImages();

        Map<String, byte[]> archive = unzip(exportService().exportArchive(true));

        assertThat(archive).containsKey("card-dataset/frames/PL-48213-3/seule.jpg");
        assertThat(archive.keySet().stream().filter(e -> e.startsWith("card-dataset/frames/"))).hasSize(1);
    }

    // --- lecture de l'archive ---

    private Set<String> stripsIn(Map<String, byte[]> archive, String entry) {
        return rows(archive, entry).stream().map(row -> row.get("slot")).collect(Collectors.toSet());
    }

    private List<Map<String, String>> allLabelledRows(Map<String, byte[]> archive) {
        List<Map<String, String>> rows = new ArrayList<>(rows(archive, "labels.csv"));
        rows.addAll(rows(archive, "held-out/crops.csv"));
        return rows;
    }

    private List<Map<String, String>> rows(Map<String, byte[]> archive, String entry) {
        byte[] content = archive.get(entry);
        assertThat(content).as("entree %s", entry).isNotNull();
        List<String> lines = new String(content, StandardCharsets.UTF_8).lines().toList();
        List<String> header = splitCsvLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            List<String> values = splitCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < header.size(); index++) {
                row.put(header.get(index), index < values.size() ? values.get(index) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"' && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else if (character == '"') {
                    quoted = false;
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString());
        return values;
    }

    private Map<String, byte[]> unzip(byte[] archive) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return entries;
    }

    private Map<String, Object> readJson(byte[] content) {
        try {
            return new ObjectMapper().readValue(new InputStreamReader(
                    new ByteArrayInputStream(content), StandardCharsets.UTF_8), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
