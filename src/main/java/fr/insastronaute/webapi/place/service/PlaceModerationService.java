package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import fr.insastronaute.webapi.place.dto.PlaceCanvasEvent;
import fr.insastronaute.webapi.place.dto.PlaceCensorRequest;
import fr.insastronaute.webapi.place.dto.PlaceCensorResultDto;
import fr.insastronaute.webapi.place.dto.PlacePixelAuthorDto;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Effacer une image choquante, et savoir qui l'a posée.
 *
 * <p>Recouvrir sans imputer ne sert à rien : la personne recommence ailleurs. La
 * censure rend donc les auteurs de la zone en même temps qu'elle l'efface, pour
 * que le geste suivant — la sanction — n'ait pas à être reconstitué à la main.</p>
 */
@Service
public class PlaceModerationService {

    private static final int HISTORY_LIMIT = 25;

    private final PlacePixelPlacementRepository placements;
    private final PlaceCanvasStore canvasStore;
    private final PlaceConfigService configService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActionJournal journal;

    public PlaceModerationService(
            PlacePixelPlacementRepository placements,
            PlaceCanvasStore canvasStore,
            PlaceConfigService configService,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            AdminActionJournal journal
    ) {
        this.placements = placements;
        this.canvasStore = canvasStore;
        this.configService = configService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.journal = journal;
    }

    /** L'historique d'une case, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<PlacePixelAuthorDto> history(int x, int y) {
        String generation = canvasStore.descriptor().generation();
        List<PlacePixelPlacement> rows = placements.findByGenerationAndXAndYOrderByPlacedAtDesc(
                generation, x, y, PageRequest.of(0, HISTORY_LIMIT));
        Map<UUID, String> names = displayNames(rows);
        return rows.stream().map(row -> toDto(row, names)).toList();
    }

    /**
     * Recouvre une zone et rend ceux qui l'avaient dessinée.
     *
     * <p>Les pixels sont réécrits un à un plutôt qu'en bloc : le canevas est une
     * chaîne d'octets dans Redis, et sa mise à jour case par case est déjà ce que
     * fait une pose normale — inutile d'inventer un second chemin d'écriture qui
     * pourrait diverger.</p>
     */
    @Transactional
    public PlaceCensorResultDto censor(PlaceCensorRequest request, String moderatorSub) {
        PlaceConfig config = configService.currentConfig();
        int maxX = Math.min(request.x() + request.width(), config.getCanvasWidth()) - 1;
        int maxY = Math.min(request.y() + request.height(), config.getCanvasHeight()) - 1;
        if (maxX < request.x() || maxY < request.y()) {
            throw new NotFoundException("La zone est en dehors du canevas.");
        }

        String generation = canvasStore.descriptor().generation();
        List<PlacePixelPlacement> touched = placements.findLatestInArea(
                generation, request.x(), maxX, request.y(), maxY);

        int cleared = 0;
        for (int y = request.y(); y <= maxY; y++) {
            for (int x = request.x(); x <= maxX; x++) {
                canvasStore.placePixel(x, y, request.color());
                messagingTemplate.convertAndSend("/topic/canvas-updates",
                        PlaceCanvasEvent.pixel(x, y, request.color(), canvasStore.descriptor().revision()));
                cleared++;
            }
        }

        // La censure ne s'inscrit pas dans l'historique des poses : elle n'est
        // l'œuvre de personne au sens du jeu, et s'y ajouter ferait du modérateur
        // le dernier auteur de chaque case effacée.
        journal.recordGlobal(moderatorSub, AdminActionType.PLACE_CANVAS_CENSORED, Map.of(
                "x", request.x(),
                "y", request.y(),
                "width", request.width(),
                "height", request.height(),
                "pixelsCleared", cleared,
                "authorsTouched", touched.stream().map(PlacePixelPlacement::getUserId).distinct().count()
        ));

        Map<UUID, String> names = displayNames(touched);
        List<PlacePixelAuthorDto> authors = touched.stream()
                .sorted(Comparator.comparing(PlacePixelPlacement::getPlacedAt).reversed())
                .map(row -> toDto(row, names))
                .toList();
        return new PlaceCensorResultDto(cleared, authors);
    }

    /** Ferme ou rouvre le canevas à quelqu'un. Le reste du site n'est pas touché. */
    @Transactional
    public void setBanned(UUID userId, boolean banned, String moderatorSub) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        user.setPlaceBanned(banned);
        userRepository.save(user);
        journal.record(
                moderatorSub,
                userId,
                banned ? AdminActionType.PLACE_BANNED : AdminActionType.PLACE_UNBANNED,
                Map.of("pixelsPlaced", placements.countByUserId(userId))
        );
    }

    private Map<UUID, String> displayNames(List<PlacePixelPlacement> rows) {
        List<UUID> ids = rows.stream().map(PlacePixelPlacement::getUserId).distinct().toList();
        Map<UUID, String> names = new LinkedHashMap<>();
        userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getDisplayName()));
        return names;
    }

    private PlacePixelAuthorDto toDto(PlacePixelPlacement row, Map<UUID, String> names) {
        return new PlacePixelAuthorDto(
                row.getX(),
                row.getY(),
                row.getColor(),
                names.getOrDefault(row.getUserId(), "Compte supprimé"),
                row.getPlacedAt(),
                row.getUserId()
        );
    }
}
