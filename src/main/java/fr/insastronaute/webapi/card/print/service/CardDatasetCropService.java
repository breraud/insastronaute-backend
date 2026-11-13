package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetCode;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetId;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropUploadRequest;
import fr.insastronaute.webapi.card.print.repository.CardDatasetCropRepository;
import fr.insastronaute.webapi.card.print.repository.CardDatasetSheetRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Le stockage des decoupes photographiees et de leurs etiquettes.
 *
 * <p>Chaque decoupe est rattachee a sa <strong>bande physique</strong> -- une position d'une
 * planche, c'est-a-dire un bout de papier unique. C'est cette identite, et pas l'image, qui
 * sert a separer entrainement et validation : la meme bande sera photographiee plusieurs fois,
 * et deux photos d'une meme bande de part et d'autre de la separation feraient mesurer de la
 * memorisation a la validation.
 *
 * <p>Une decoupe sans etiquette n'est pas un rejet : elle part en arbitrage. C'est
 * l'exemple que le modele ne sait pas lire, donc le plus utile du lot.
 */
@Service
public class CardDatasetCropService {

    private final CardDatasetSheetRepository sheetRepository;
    private final CardDatasetCropRepository cropRepository;
    private final CardDatasetCropStorageService storageService;

    public CardDatasetCropService(
            CardDatasetSheetRepository sheetRepository,
            CardDatasetCropRepository cropRepository,
            CardDatasetCropStorageService storageService
    ) {
        this.sheetRepository = sheetRepository;
        this.cropRepository = cropRepository;
        this.storageService = storageService;
    }

    @Transactional
    public CardDatasetCropDto store(String rawSheetId, CardDatasetCropUploadRequest request, MultipartFile image) {
        return store(rawSheetId, request, image, null);
    }

    @Transactional
    public CardDatasetCropDto store(String rawSheetId, CardDatasetCropUploadRequest request,
                                    MultipartFile image, MultipartFile frame) {
        if (request == null || request.position() == null) {
            throw new BadRequestException("La position de la bande est requise.");
        }
        CardDatasetSheet sheet = requireSheet(rawSheetId);
        CardDatasetSheetCode strip = sheet.getCodes().stream()
                .filter(code -> code.getPosition().equals(request.position()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Cette planche n'a pas de bande à cette position."));

        requireCompleteRegion(request);
        UUID cropId = UUID.randomUUID();
        // La photo entiere d'abord : c'est elle qui donne le repere dans lequel la region est
        // exprimee, et on ne peut verifier que la boite y tient qu'une fois ses dimensions lues.
        CardDatasetCropStorageService.StoredCrop storedFrame =
                frame == null || frame.isEmpty() ? null : storageService.storeFrame(cropId, sheet.getSheetId(), frame);
        requireRegionInsideFrame(request, storedFrame);

        CardDatasetCropStorageService.StoredCrop stored = storageService.store(cropId, sheet.getSheetId(), image);

        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setId(cropId);
        crop.setSheet(sheet);
        crop.setStrip(strip);
        applyLabel(crop, request.label());
        // Rien de dit vaut "cadrage automatique" : les outils deja en place n'envoient pas le
        // parametre et passent tous par la segmentation. Un cadrage inconnu range parmi les cas
        // faciles rendrait la colonne inexploitable, donc le defaut est arrete ici aussi et pas
        // seulement a l'entree HTTP.
        crop.setFraming(request.framing() == null ? CardDatasetCropFraming.AUTOMATIC : request.framing());
        crop.setImageObjectKey(stored.objectKey());
        crop.setImageContentType(stored.contentType());
        crop.setImageByteSize(stored.byteSize());
        crop.setImageWidthPx(stored.widthPx());
        crop.setImageHeightPx(stored.heightPx());
        crop.setCaptureDevice(trimToLength(request.captureDevice(), 200));
        crop.setCaptureWidthPx(request.captureWidthPx());
        crop.setCaptureHeightPx(request.captureHeightPx());
        if (storedFrame != null) {
            crop.setFrameObjectKey(storedFrame.objectKey());
            crop.setFrameContentType(storedFrame.contentType());
            crop.setFrameByteSize(storedFrame.byteSize());
            crop.setFrameWidthPx(storedFrame.widthPx());
            crop.setFrameHeightPx(storedFrame.heightPx());
        }
        crop.setRegionXPx(request.regionXPx());
        crop.setRegionYPx(request.regionYPx());
        crop.setRegionWidthPx(request.regionWidthPx());
        crop.setRegionHeightPx(request.regionHeightPx());
        crop.setRegionAngleDeg(request.regionAngleDeg());

        return toDto(cropRepository.save(crop));
    }


    /**
     * Une region a quatre nombres, ou n'en a aucun.
     *
     * <p>Trois sur quatre ne decrivent pas une boite. Completer le quatrieme par zero
     * fabriquerait une verite terrain fausse que l'entrainement prendrait au serieux -- et qui
     * ne se verrait jamais, puisqu'une boite reste une boite.
     */
    private static void requireCompleteRegion(CardDatasetCropUploadRequest request) {
        int given = 0;
        if (request.regionXPx() != null) given++;
        if (request.regionYPx() != null) given++;
        if (request.regionWidthPx() != null) given++;
        if (request.regionHeightPx() != null) given++;
        if (given == 0 || given == 4) {
            if (given == 4 && (request.regionWidthPx() <= 0 || request.regionHeightPx() <= 0)) {
                throw new BadRequestException("La region de la decoupe doit avoir une largeur et une hauteur positives.");
            }
            return;
        }
        throw new BadRequestException(
                "La region de la decoupe demande ses quatre coordonnees, ou aucune.");
    }

    /**
     * La boite doit tenir dans la photo qu'elle designe.
     *
     * <p>Le piege que cette verification existe pour attraper : le client envoie la region dans
     * les coordonnees du flux et la photo dans une autre taille. Rien ne planterait -- ce sont
     * des entiers plausibles des deux cotes -- et chaque exemple du jeu de detection pointerait
     * a cote de la bande. Mieux vaut refuser l'envoi que stocker une donnee qu'on croira bonne.
     */
    private static void requireRegionInsideFrame(CardDatasetCropUploadRequest request,
                                                 CardDatasetCropStorageService.StoredCrop frame) {
        if (request.regionXPx() == null) {
            return;
        }
        if (frame == null) {
            throw new BadRequestException(
                    "Une region sans la photo entiere ne designe rien : envoyez les deux, ou aucun des deux.");
        }
        Integer width = frame.widthPx();
        Integer height = frame.heightPx();
        if (width == null || height == null) {
            // Les dimensions n'ont pas pu etre lues : on ne peut ni confirmer ni infirmer. On
            // garde, parce que refuser ici perdrait une decoupe utilisable pour une incertitude.
            return;
        }
        long right = (long) request.regionXPx() + request.regionWidthPx();
        long bottom = (long) request.regionYPx() + request.regionHeightPx();
        if (request.regionXPx() < 0 || request.regionYPx() < 0 || right > width || bottom > height) {
            throw new BadRequestException(String.format(
                    "La region (%d, %d, %d x %d) sort de la photo (%d x %d) : les deux ne sont pas "
                            + "dans le meme repere.",
                    request.regionXPx(), request.regionYPx(), request.regionWidthPx(),
                    request.regionHeightPx(), width, height));
        }
    }

    /**
     * Toutes les decoupes d'une planche, <strong>y compris celles qui ont ete ecartees</strong>.
     *
     * <p>Elles portent leur statut : l'outil d'administration les grise et propose de les
     * remettre. Les cacher ici ferait disparaitre le seul chemin de retour, et un ecart sans
     * retour est une suppression.
     */
    @Transactional(readOnly = true)
    public List<CardDatasetCropDto> findBySheet(String rawSheetId) {
        return cropRepository.findBySheetOrderByCreatedAtAsc(requireSheet(rawSheetId)).stream()
                .map(CardDatasetCropService::toDto)
                .toList();
    }

    @Transactional
    public CardDatasetCropDto label(UUID cropId, String label) {
        CardDatasetCrop crop = requireCrop(cropId);
        // Etiqueter une decoupe ecartee la ferait rentrer dans l'entrainement sans que personne
        // ne l'ait redemande : l'arbitrage deciderait a la place de l'operateur qui l'avait
        // jugee inutilisable. La remettre est un geste explicite, et il a son endpoint.
        if (crop.getStatus() == CardDatasetCropStatus.DISCARDED) {
            throw new ConflictException("Cette découpe est écartée : la restaurer avant de l'étiqueter.");
        }
        applyLabel(crop, label);
        return toDto(cropRepository.save(crop));
    }

    /**
     * Ecarte une decoupe que l'operateur a jugee inutilisable -- floue, mal cadree, la mauvaise
     * bande.
     *
     * <p>Ce n'est pas une suppression, et c'est deliberé. L'operateur enchaine des dizaines de
     * decoupes au pouce sur un ecran tactile ; a ce rythme, un effacement definitif finit par
     * emporter ce qu'il voulait garder. La ligne reste, l'etiquette reste, et les octets restent
     * dans le stockage : les effacer rendrait le geste irreversible, ce qui en est tout
     * l'interet. Le compte des ecartees est en plus une mesure du jeu de donnees -- combien de
     * captures ont ete jetees dit quelque chose de la collecte -- et une ligne effacee ne dit
     * plus rien.
     *
     * <p>L'operation est <strong>idempotente</strong> : ecarter une decoupe deja ecartee
     * constate l'etat atteint au lieu de crier. Le pouce derape, la requete part deux fois, le
     * reseau la rejoue ; l'operateur ne doit pas avoir a distinguer les deux cas au milieu d'un
     * lot. Une decoupe qui n'a jamais existe, elle, reste introuvable.
     */
    @Transactional
    public void discard(UUID cropId) {
        CardDatasetCrop crop = requireCrop(cropId);
        crop.setStatus(CardDatasetCropStatus.DISCARDED);
        cropRepository.save(crop);
    }

    /**
     * Remet dans le jeu de donnees une decoupe ecartee.
     *
     * <p>Elle retrouve l'etat que son etiquette implique, jamais mieux : etiquetee si le code
     * etait connu, en attente d'arbitrage sinon. L'ecart n'a touche ni l'etiquette ni l'image,
     * donc il n'y a rien a reconstruire. Restaurer une decoupe qui n'etait pas ecartee ne fait
     * rien, pour la meme raison que l'ecart est idempotent.
     */
    @Transactional
    public CardDatasetCropDto restore(UUID cropId) {
        CardDatasetCrop crop = requireCrop(cropId);
        applyLabel(crop, crop.getLabel());
        return toDto(cropRepository.save(crop));
    }

    @Transactional(readOnly = true)
    public StoredCropImage readImage(UUID cropId) {
        CardDatasetCrop crop = requireCrop(cropId);
        return new StoredCropImage(storageService.read(crop.getImageObjectKey()), crop.getImageContentType());
    }

    private CardDatasetCrop requireCrop(UUID cropId) {
        return cropRepository.findWithSheetById(cropId)
                .orElseThrow(() -> new NotFoundException("Découpe de dataset introuvable."));
    }

    private CardDatasetSheet requireSheet(String rawSheetId) {
        String sheetId = CardDatasetSheetId.normalize(rawSheetId)
                .orElseThrow(() -> new BadRequestException("Identifiant de planche invalide."));
        return sheetRepository.findWithCodesBySheetId(sheetId)
                .orElseThrow(() -> new NotFoundException("Planche de dataset introuvable."));
    }

    private void applyLabel(CardDatasetCrop crop, String label) {
        String trimmed = label == null || label.isBlank() ? null : label.trim().toUpperCase();
        crop.setLabel(trimmed);
        crop.setStatus(trimmed == null ? CardDatasetCropStatus.PENDING_ARBITRATION : CardDatasetCropStatus.LABELLED);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static CardDatasetCropDto toDto(CardDatasetCrop crop) {
        CardDatasetSheetCode strip = crop.getStrip();
        return new CardDatasetCropDto(
                crop.getId(),
                crop.getSheet().getSheetId(),
                strip.getId(),
                strip.getPosition(),
                strip.getCode(),
                crop.getLabel(),
                crop.getStatus(),
                crop.getFraming(),
                crop.getImageContentType(),
                crop.getImageByteSize(),
                crop.getImageWidthPx(),
                crop.getImageHeightPx(),
                crop.getCaptureDevice(),
                crop.getCaptureWidthPx(),
                crop.getCaptureHeightPx(),
                crop.getCreatedAt()
        );
    }

    public record StoredCropImage(byte[] bytes, String contentType) {
    }
}
