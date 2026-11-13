package fr.insastronaute.webapi.announcement.service;

import fr.insastronaute.webapi.announcement.domain.Announcement;
import fr.insastronaute.webapi.announcement.domain.AnnouncementDismissal;
import fr.insastronaute.webapi.announcement.dto.AnnouncementDto;
import fr.insastronaute.webapi.announcement.dto.AnnouncementRequest;
import fr.insastronaute.webapi.announcement.repository.AnnouncementDismissalRepository;
import fr.insastronaute.webapi.announcement.repository.AnnouncementRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Les annonces : leur écriture côté administration, leur lecture côté joueur. */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcements;
    private final AnnouncementDismissalRepository dismissals;
    private final UserRepository users;

    public AnnouncementService(
            AnnouncementRepository announcements,
            AnnouncementDismissalRepository dismissals,
            UserRepository users
    ) {
        this.announcements = announcements;
        this.dismissals = dismissals;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> findAll() {
        return announcements.findAllByOrderByCreatedAtDesc().stream().map(AnnouncementDto::from).toList();
    }

    @Transactional
    public AnnouncementDto create(AnnouncementRequest request) {
        Announcement announcement = new Announcement();
        apply(announcement, request);
        return AnnouncementDto.from(announcements.save(announcement));
    }

    @Transactional
    public AnnouncementDto update(UUID id, AnnouncementRequest request) {
        Announcement announcement = announcements.findById(id)
                .orElseThrow(() -> new NotFoundException("Annonce introuvable."));
        apply(announcement, request);
        return AnnouncementDto.from(announcements.save(announcement));
    }

    /**
     * Supprime l'annonce, et avec elle les « compris » qui la refermaient.
     *
     * <p>Rien à conserver : une annonce supprimée n'a plus rien à ne plus afficher.</p>
     */
    @Transactional
    public void delete(UUID id) {
        if (!announcements.existsById(id)) {
            throw new NotFoundException("Annonce introuvable.");
        }
        announcements.deleteById(id);
    }

    /** Ce que ce joueur doit voir maintenant, le plus récent d'abord. */
    @Transactional(readOnly = true)
    public List<AnnouncementDto> visibleFor(String keycloakSub) {
        AppUser user = requireUser(keycloakSub);
        return announcements.findVisibleFor(user.getId(), TimeTravelContext.instantNow())
                .stream()
                .map(AnnouncementDto::from)
                .toList();
    }

    /**
     * Referme une annonce pour ce joueur.
     *
     * <p>Refermer deux fois n'est pas une erreur : deux onglets ouverts sur la même
     * annonce, et le second clic arriverait après le premier.</p>
     */
    @Transactional
    public void dismiss(String keycloakSub, UUID announcementId) {
        AppUser user = requireUser(keycloakSub);
        if (!announcements.existsById(announcementId)) {
            throw new NotFoundException("Annonce introuvable.");
        }
        AnnouncementDismissal.Key key = new AnnouncementDismissal.Key(announcementId, user.getId());
        if (dismissals.existsById(key)) {
            return;
        }
        dismissals.save(new AnnouncementDismissal(
                announcementId, user.getId(), TimeTravelContext.instantNow()));
    }

    private void apply(Announcement announcement, AnnouncementRequest request) {
        announcement.setTitle(request.title().trim());
        announcement.setBody(request.body().trim());
        announcement.setKind(request.kind());
        announcement.setActive(request.activeOrDefault());
        announcement.setStartDate(request.startDate());
        announcement.setEndDate(request.endDate());
        announcement.setLink(normalizeLink(request.link()));
    }

    /**
     * Un champ laissé vide n'est pas un lien vide : c'est l'absence de lien.
     *
     * <p>Le panneau envoie une chaîne vide quand on ne choisit rien ; la garder
     * ferait afficher un bouton qui ne mène nulle part.</p>
     */
    private static String normalizeLink(String link) {
        if (link == null) {
            return null;
        }
        String trimmed = link.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AppUser requireUser(String keycloakSub) {
        return users.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }
}
