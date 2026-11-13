package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetCode;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropUploadRequest;
import fr.insastronaute.webapi.card.print.repository.CardDatasetCropRepository;
import fr.insastronaute.webapi.card.print.repository.CardDatasetSheetRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardDatasetCropServiceTest {

    @Mock
    private CardDatasetSheetRepository sheetRepository;

    @Mock
    private CardDatasetCropRepository cropRepository;

    @Mock
    private CardDatasetCropStorageService storageService;

    @InjectMocks
    private CardDatasetCropService cropService;

    @Test
    void aCropIsRetrievableWithItsSheetItsPositionAndItsPhysicalStrip() {
        // La bande physique, pas seulement la planche : une meme bande sera photographiee
        // plusieurs fois sous des angles differents, et la separation entrainement/validation
        // se fait par bande. Sans cette identite, deux photos d'une meme bande tombent de part
        // et d'autre de la separation et la validation mesure de la memorisation.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.store(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/crops/PL-48213-3/x.png", "image/png", 128L, 300, 80));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetCropDto dto = cropService.store("pl 48213 3", upload(1, "DDDDD-EEEEE-FFFFF"), image());

        ArgumentCaptor<CardDatasetCrop> cropCaptor = ArgumentCaptor.forClass(CardDatasetCrop.class);
        verify(cropRepository).save(cropCaptor.capture());
        CardDatasetCrop saved = cropCaptor.getValue();

        assertThat(saved.getSheet()).isSameAs(sheet);
        assertThat(saved.getStrip()).isSameAs(sheet.getCodes().get(1));
        assertThat(saved.getStrip().getPosition()).isEqualTo(1);
        assertThat(saved.getLabel()).isEqualTo("DDDDD-EEEEE-FFFFF");
        assertThat(saved.getStatus()).isEqualTo(CardDatasetCropStatus.LABELLED);
        assertThat(saved.getImageObjectKey()).isEqualTo("card-dataset/crops/PL-48213-3/x.png");
        assertThat(saved.getImageContentType()).isEqualTo("image/png");
        assertThat(saved.getImageWidthPx()).isEqualTo(300);
        assertThat(saved.getImageHeightPx()).isEqualTo(80);
        assertThat(saved.getCaptureDevice()).isEqualTo("Pixel 7a");
        assertThat(saved.getCaptureWidthPx()).isEqualTo(1920);
        assertThat(saved.getCaptureHeightPx()).isEqualTo(1080);

        assertThat(dto.sheetId()).isEqualTo("PL-48213-3");
        assertThat(dto.position()).isEqualTo(1);
        assertThat(dto.stripId()).isEqualTo(sheet.getCodes().get(1).getId());
        assertThat(dto.expectedCode()).isEqualTo("DDDDD-EEEEE-FFFFF");
        assertThat(dto.label()).isEqualTo("DDDDD-EEEEE-FFFFF");
        assertThat(dto.status()).isEqualTo(CardDatasetCropStatus.LABELLED);
    }

    @Test
    void aCropNoCodeExplainsIsKeptForArbitrationInsteadOfBeingThrownAway() {
        // C'est l'exemple le plus precieux du lot : celui que le modele ne sait pas lire.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.store(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/crops/PL-48213-3/y.png", "image/png", 64L, 300, 80));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetCropDto dto = cropService.store("PL-48213-3", upload(0, null), image());

        assertThat(dto.status()).isEqualTo(CardDatasetCropStatus.PENDING_ARBITRATION);
        assertThat(dto.label()).isNull();
        assertThat(dto.expectedCode()).isEqualTo("AAAAA-BBBBB-CCCCC");
        assertThat(dto.stripId()).isEqualTo(sheet.getCodes().getFirst().getId());
    }

    @Test
    void theHumanArbitrationBecomesTheLabel() {
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setId(UUID.randomUUID());
        crop.setSheet(sheet);
        crop.setStrip(sheet.getCodes().getFirst());
        crop.setStatus(CardDatasetCropStatus.PENDING_ARBITRATION);
        crop.setImageObjectKey("card-dataset/crops/PL-48213-3/y.png");
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetCropDto dto = cropService.label(crop.getId(), "AAAAA-BBBBB-CCCCC");

        assertThat(dto.label()).isEqualTo("AAAAA-BBBBB-CCCCC");
        assertThat(dto.status()).isEqualTo(CardDatasetCropStatus.LABELLED);
    }

    @Test
    void aPositionThatIsNotOnTheSheetIsRefused() {
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));

        assertThatThrownBy(() -> cropService.store("PL-48213-3", upload(4, null), image()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storageService);
        verifyNoInteractions(cropRepository);
    }

    @Test
    void aMistypedSheetIdentifierNeverStoresACropAgainstAnotherSheet() {
        assertThatThrownBy(() -> cropService.store("PL-48214-3", upload(0, null), image()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(sheetRepository);
        verifyNoInteractions(storageService);
    }

    @Test
    void anUnknownSheetIsNotFound() {
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cropService.store("PL-48213-3", upload(0, null), image()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aSheetHandsBackEveryCropTakenFromIt() {
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setId(UUID.randomUUID());
        crop.setSheet(sheet);
        crop.setStrip(sheet.getCodes().getFirst());
        crop.setLabel("AAAAA-BBBBB-CCCCC");
        crop.setStatus(CardDatasetCropStatus.LABELLED);
        crop.setImageObjectKey("k");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(cropRepository.findBySheetOrderByCreatedAtAsc(sheet)).thenReturn(List.of(crop));

        List<CardDatasetCropDto> crops = cropService.findBySheet("PL-48213-3");

        assertThat(crops).extracting(CardDatasetCropDto::stripId)
                .containsExactly(sheet.getCodes().getFirst().getId());
    }

    @Test
    void aCropUploadedWithoutSayingHowItWasFramedIsRecordedAsAutomatic() {
        // Les outils de collecte deja en place n'envoient pas le parametre. Leur silence doit
        // vouloir dire "la segmentation a trouve la bande toute seule", jamais "on ne sait pas" :
        // un cadrage inconnu melange aux cas faciles rendrait la colonne inexploitable.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.store(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/crops/PL-48213-3/z.png", "image/png", 64L, 300, 80));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetCropDto dto = cropService.store("PL-48213-3", upload(0, "AAAAA-BBBBB-CCCCC", null), image());

        ArgumentCaptor<CardDatasetCrop> cropCaptor = ArgumentCaptor.forClass(CardDatasetCrop.class);
        verify(cropRepository).save(cropCaptor.capture());
        assertThat(cropCaptor.getValue().getFraming()).isEqualTo(CardDatasetCropFraming.AUTOMATIC);
        assertThat(dto.framing()).isEqualTo(CardDatasetCropFraming.AUTOMATIC);
    }

    @Test
    void aCropTheOperatorFramedByHandIsRecordedAsManual() {
        // Une decoupe cadree a la main est, par definition, une decoupe que la segmentation
        // automatique n'a pas su traiter : le cas difficile, designe gratuitement par le geste
        // de l'operateur. Perdre cette information, c'est perdre le seul signal du lot qui
        // n'a besoin d'aucun modele pour etre trouve.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.store(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/crops/PL-48213-3/z.png", "image/png", 64L, 300, 80));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetCropDto dto = cropService.store(
                "PL-48213-3", upload(0, "AAAAA-BBBBB-CCCCC", CardDatasetCropFraming.MANUAL), image());

        ArgumentCaptor<CardDatasetCrop> cropCaptor = ArgumentCaptor.forClass(CardDatasetCrop.class);
        verify(cropRepository).save(cropCaptor.capture());
        assertThat(cropCaptor.getValue().getFraming()).isEqualTo(CardDatasetCropFraming.MANUAL);
        assertThat(dto.framing()).isEqualTo(CardDatasetCropFraming.MANUAL);
    }

    @Test
    void aCropTheOperatorJudgedUnusableIsSetAsideAndNeverDeleted() {
        // Ecarter n'est pas supprimer. L'operateur enchaine des dizaines de decoupes au pouce ;
        // a ce rythme un effacement definitif finit par emporter ce qu'il voulait garder. La
        // ligne reste, l'etiquette reste, et les octets restent dans le stockage -- sans quoi le
        // geste ne serait pas reversible, ce qui est tout son interet.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = storedCrop(sheet, "AAAAA-BBBBB-CCCCC", CardDatasetCropStatus.LABELLED);
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cropService.discard(crop.getId());

        assertThat(crop.getStatus()).isEqualTo(CardDatasetCropStatus.DISCARDED);
        assertThat(crop.getLabel()).isEqualTo("AAAAA-BBBBB-CCCCC");
        assertThat(crop.getImageObjectKey()).isEqualTo("card-dataset/crops/PL-48213-3/one.png");
        verify(cropRepository).save(crop);
        verify(cropRepository, never()).delete(any(CardDatasetCrop.class));
        // Les octets ne sont jamais touches : les effacer rendrait la restauration impossible.
        verifyNoInteractions(storageService);
    }

    @Test
    void discardingACropTwiceIsNotAnError() {
        // Le pouce derape, la requete part deux fois, le reseau la rejoue : le second appel doit
        // constater l'etat deja atteint, pas crier. Un ecart n'a rien de plus a defaire.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = storedCrop(sheet, "AAAAA-BBBBB-CCCCC", CardDatasetCropStatus.DISCARDED);
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cropService.discard(crop.getId());

        assertThat(crop.getStatus()).isEqualTo(CardDatasetCropStatus.DISCARDED);
        assertThat(crop.getLabel()).isEqualTo("AAAAA-BBBBB-CCCCC");
        verifyNoInteractions(storageService);
    }

    @Test
    void discardingACropThatNeverExistedIsNotFound() {
        UUID cropId = UUID.randomUUID();
        when(cropRepository.findWithSheetById(cropId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cropService.discard(cropId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aDiscardedCropComesBackWithTheStatusItsLabelImplies() {
        // Le voyage aller-retour. L'etiquette n'a pas bouge pendant l'ecart, donc la decoupe
        // retrouve exactement l'etat qu'elle avait : etiquetee.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = storedCrop(sheet, "AAAAA-BBBBB-CCCCC", CardDatasetCropStatus.LABELLED);
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cropService.discard(crop.getId());
        CardDatasetCropDto restored = cropService.restore(crop.getId());

        assertThat(restored.status()).isEqualTo(CardDatasetCropStatus.LABELLED);
        assertThat(restored.label()).isEqualTo("AAAAA-BBBBB-CCCCC");
    }

    @Test
    void aDiscardedCropWithNoLabelComesBackToArbitrationAndNotToATrustedLabel() {
        // Restaurer rend l'etat que l'etiquette implique, jamais mieux : une decoupe qu'aucun
        // code n'expliquait avant l'ecart ne l'explique pas davantage apres.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = storedCrop(sheet, null, CardDatasetCropStatus.PENDING_ARBITRATION);
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cropService.discard(crop.getId());
        CardDatasetCropDto restored = cropService.restore(crop.getId());

        assertThat(restored.status()).isEqualTo(CardDatasetCropStatus.PENDING_ARBITRATION);
        assertThat(restored.label()).isNull();
    }

    @Test
    void restoringACropThatWasNeverDiscardedLeavesItAsItWas() {
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = storedCrop(sheet, "AAAAA-BBBBB-CCCCC", CardDatasetCropStatus.LABELLED);
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetCropDto restored = cropService.restore(crop.getId());

        assertThat(restored.status()).isEqualTo(CardDatasetCropStatus.LABELLED);
        assertThat(restored.label()).isEqualTo("AAAAA-BBBBB-CCCCC");
    }

    @Test
    void restoringACropThatNeverExistedIsNotFound() {
        UUID cropId = UUID.randomUUID();
        when(cropRepository.findWithSheetById(cropId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cropService.restore(cropId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void arbitratingADiscardedCropIsRefusedInsteadOfSilentlyBringingItBack() {
        // Etiqueter une decoupe ecartee la ferait rentrer dans l'entrainement sans que personne
        // ne l'ait redemande. Mieux vaut un refus lisible : la restaurer est un geste explicite.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop crop = storedCrop(sheet, null, CardDatasetCropStatus.DISCARDED);
        when(cropRepository.findWithSheetById(crop.getId())).thenReturn(Optional.of(crop));

        assertThatThrownBy(() -> cropService.label(crop.getId(), "AAAAA-BBBBB-CCCCC"))
                .isInstanceOf(ConflictException.class);
        assertThat(crop.getStatus()).isEqualTo(CardDatasetCropStatus.DISCARDED);
        verify(cropRepository, never()).save(any(CardDatasetCrop.class));
    }

    @Test
    void aSheetStillHandsBackTheCropsTheOperatorDiscarded() {
        // L'outil d'administration doit pouvoir les montrer grisees et proposer de les remettre.
        // Les cacher ferait disparaitre le seul chemin de retour.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        CardDatasetCrop kept = storedCrop(sheet, "AAAAA-BBBBB-CCCCC", CardDatasetCropStatus.LABELLED);
        CardDatasetCrop discarded = storedCrop(sheet, "AAAAA-BBBBB-CCCCC", CardDatasetCropStatus.DISCARDED);
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(cropRepository.findBySheetOrderByCreatedAtAsc(sheet)).thenReturn(List.of(kept, discarded));

        List<CardDatasetCropDto> crops = cropService.findBySheet("PL-48213-3");

        assertThat(crops).extracting(CardDatasetCropDto::id)
                .containsExactly(kept.getId(), discarded.getId());
        assertThat(crops).extracting(CardDatasetCropDto::status)
                .containsExactly(CardDatasetCropStatus.LABELLED, CardDatasetCropStatus.DISCARDED);
    }

    private CardDatasetCrop storedCrop(CardDatasetSheet sheet, String label, CardDatasetCropStatus status) {
        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setId(UUID.randomUUID());
        crop.setSheet(sheet);
        crop.setStrip(sheet.getCodes().getFirst());
        crop.setLabel(label);
        crop.setStatus(status);
        crop.setImageObjectKey("card-dataset/crops/PL-48213-3/one.png");
        crop.setImageContentType("image/png");
        return crop;
    }


    @Test
    void laRegionDeLImageEntiereEstConserveeAvecLaPhotoDontElleVient() {
        // Sans elle, on garde la reponse de l'operateur et on jette la question. Une decoupe
        // cadree a la main est exactement un echec de la segmentation corrige par un humain :
        // c'est l'exemple le plus utile qui existe pour reparer -- ou remplacer -- l'heuristique,
        // et il ne vaut que si l'on sait ou la boite se trouvait dans la photo.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.store(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/crops/PL-48213-3/x.png", "image/png", 128L, 900, 110));
        when(storageService.storeFrame(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/frames/PL-48213-3/x.jpg", "image/jpeg", 4096L, 1080, 1080));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cropService.store("PL-48213-3", upload(0, "AAAAA-BBBBB-CCCCC", null,
                90, 480, 900, 110, -1.5), image(), frame());

        ArgumentCaptor<CardDatasetCrop> captor = ArgumentCaptor.forClass(CardDatasetCrop.class);
        verify(cropRepository).save(captor.capture());
        CardDatasetCrop saved = captor.getValue();
        assertThat(saved.getFrameObjectKey()).isEqualTo("card-dataset/frames/PL-48213-3/x.jpg");
        assertThat(saved.getFrameWidthPx()).isEqualTo(1080);
        assertThat(saved.getRegionXPx()).isEqualTo(90);
        assertThat(saved.getRegionYPx()).isEqualTo(480);
        assertThat(saved.getRegionWidthPx()).isEqualTo(900);
        assertThat(saved.getRegionHeightPx()).isEqualTo(110);
        assertThat(saved.getRegionAngleDeg()).isEqualTo(-1.5);
    }

    @Test
    void uneRegionIncompleteEstRefuseeAuLieuDEtreCompleteeAuHasard() {
        // Trois nombres sur quatre ne decrivent pas une boite. Les accepter en mettant zero
        // dans le quatrieme fabriquerait une verite terrain fausse, que l'entrainement
        // prendrait au serieux -- et qui ne se verrait jamais, puisqu'une boite reste une boite.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));

        assertThatThrownBy(() -> cropService.store("PL-48213-3",
                upload(0, "AAAAA-BBBBB-CCCCC", null, 90, 480, 900, null, null), image(), frame()))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(cropRepository);
    }

    @Test
    void uneRegionQuiDeborderaitDeLaPhotoEstRefusee() {
        // Le piege que cette verification existe pour attraper : le client envoie la boite dans
        // les coordonnees du flux et la photo dans une autre taille. Rien ne planterait, les
        // deux valeurs sont des entiers plausibles -- et chaque exemple du jeu de detection
        // pointerait a cote. On refuse au lieu de stocker une donnee qu'on croira bonne.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.storeFrame(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/frames/PL-48213-3/x.jpg", "image/jpeg", 4096L, 1080, 1080));

        assertThatThrownBy(() -> cropService.store("PL-48213-3",
                upload(0, "AAAAA-BBBBB-CCCCC", null, 900, 480, 900, 110, 0.0), image(), frame()))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(cropRepository);
    }

    @Test
    void unEnvoiSansPhotoEntiereResteAccepte() {
        // Les outils deja en place n'envoient pas la photo. Refuser leur envoi ferait perdre des
        // decoupes utilisables pour une metadonnee qui ne sert qu'a un chantier a venir.
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(storageService.store(any(UUID.class), anyString(), any(MultipartFile.class)))
                .thenReturn(new CardDatasetCropStorageService.StoredCrop(
                        "card-dataset/crops/PL-48213-3/x.png", "image/png", 128L, 900, 110));
        when(cropRepository.save(any(CardDatasetCrop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cropService.store("PL-48213-3", upload(0, "AAAAA-BBBBB-CCCCC"), image(), null);

        ArgumentCaptor<CardDatasetCrop> captor = ArgumentCaptor.forClass(CardDatasetCrop.class);
        verify(cropRepository).save(captor.capture());
        assertThat(captor.getValue().getFrameObjectKey()).isNull();
        assertThat(captor.getValue().getRegionXPx()).isNull();
    }

    private CardDatasetCropUploadRequest upload(int position, String label, CardDatasetCropFraming framing,
                                                Integer regionX, Integer regionY, Integer regionWidth,
                                                Integer regionHeight, Double regionAngle) {
        return new CardDatasetCropUploadRequest(position, label, framing, "Pixel 7a", 1920, 1080,
                regionX, regionY, regionWidth, regionHeight, regionAngle);
    }

    private MultipartFile frame() {
        return new MockMultipartFile("frame", "frame.jpg", "image/jpeg", new byte[]{4, 5, 6});
    }

    private CardDatasetCropUploadRequest upload(int position, String label) {
        return upload(position, label, null);
    }

    private CardDatasetCropUploadRequest upload(int position, String label, CardDatasetCropFraming framing) {
        return upload(position, label, framing, null, null, null, null, null);
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "strip.png", "image/png", new byte[]{1, 2, 3});
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
