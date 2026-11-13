package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryGrant;
import fr.insastronaute.webapi.medialibrary.dto.MediaLibraryGrantDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaPersonRefDto;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryGrantRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Les designations, que seuls les administrateurs gerent. */
@Service
public class MediaLibraryGrantService {

    static final String NOT_STAFF = "MEDIA_LIBRARY_GRANT_NOT_STAFF";

    private final MediaLibraryGrantRepository grants;
    private final UserRepository users;
    private final RealmRoleDirectory roles;
    private final AdminActionJournal journal;

    public MediaLibraryGrantService(MediaLibraryGrantRepository grants, UserRepository users,
                                    RealmRoleDirectory roles, AdminActionJournal journal) {
        this.grants = grants;
        this.users = users;
        this.roles = roles;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<MediaLibraryGrantDto> list() {
        return grants.findAllWithUsers().stream()
                .map(grant -> new MediaLibraryGrantDto(
                        grant.getUserId(),
                        MediaPersonRefDto.nameOf(grant.getUser()),
                        grant.getUser().getEmail(),
                        roles.isStaff(grant.getUser().getKeycloakSub()),
                        MediaPersonRefDto.nameOf(grant.getGrantedBy()),
                        grant.getGrantedAt()))
                .toList();
    }

    @Transactional
    public void grant(UUID userId, String actorKeycloakSub) {
        AppUser target = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."));
        if (grants.existsById(userId)) {
            return;
        }
        if (target.isDeleted()) {
            throw new CodedBadRequestException(NOT_STAFF, "Ce compte a été supprimé.");
        }
        if (!roles.isKnown()) {
            throw new RoleDirectoryUnavailableException();
        }
        if (!roles.isStaff(target.getKeycloakSub())) {
            throw new CodedBadRequestException(NOT_STAFF, "Seul un membre du staff peut être désigné.");
        }
        AppUser actor = users.findByKeycloakSub(actorKeycloakSub)
                .orElseThrow(() -> new NotFoundException("Auteur de l'action introuvable."));
        grants.save(new MediaLibraryGrant(target, actor, Instant.now()));
        journal.record(actorKeycloakSub, userId, AdminActionType.MEDIA_LIBRARY_ACCESS_GRANTED, Map.of());
    }

    @Transactional
    public void revoke(UUID userId, String actorKeycloakSub) {
        if (!grants.existsById(userId)) {
            return;
        }
        grants.deleteById(userId);
        journal.record(actorKeycloakSub, userId, AdminActionType.MEDIA_LIBRARY_ACCESS_REVOKED, Map.of());
    }
}
