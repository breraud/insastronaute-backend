package fr.insastronaute.webapi.announcement;

import fr.insastronaute.webapi.announcement.domain.Announcement;
import fr.insastronaute.webapi.announcement.domain.AnnouncementDismissal;
import fr.insastronaute.webapi.announcement.domain.AnnouncementKind;
import fr.insastronaute.webapi.announcement.dto.AnnouncementRequest;
import fr.insastronaute.webapi.announcement.repository.AnnouncementDismissalRepository;
import fr.insastronaute.webapi.announcement.repository.AnnouncementRepository;
import fr.insastronaute.webapi.announcement.service.AnnouncementService;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock private AnnouncementRepository announcements;
    @Mock private AnnouncementDismissalRepository dismissals;
    @Mock private UserRepository users;

    @InjectMocks private AnnouncementService service;

    private AppUser player() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Announcement announcement(boolean active, Instant start, Instant end) {
        Announcement announcement = new Announcement();
        announcement.setId(UUID.randomUUID());
        announcement.setTitle("Maintenance");
        announcement.setBody("Le site sera coupé une heure.");
        announcement.setKind(AnnouncementKind.INCIDENT);
        announcement.setActive(active);
        announcement.setStartDate(start);
        announcement.setEndDate(end);
        return announcement;
    }

    @Test
    void anAnnouncementOutsideItsWindowIsNotShown() {
        // Une panne annoncée resterait sinon à l'écran une semaine après sa fin.
        Instant now = Instant.parse("2026-09-07T12:00:00Z");
        assertThat(announcement(true, null, Instant.parse("2026-09-06T12:00:00Z")).isVisibleAt(now)).isFalse();
        assertThat(announcement(true, Instant.parse("2026-09-08T12:00:00Z"), null).isVisibleAt(now)).isFalse();
        assertThat(announcement(true, null, null).isVisibleAt(now)).isTrue();
    }

    @Test
    void aClosedAnnouncementStaysWrittenButHidden() {
        // Couper avant terme ne doit pas obliger à effacer le texte.
        assertThat(announcement(false, null, null).isVisibleAt(Instant.parse("2026-09-07T12:00:00Z"))).isFalse();
    }

    @Test
    void closingTwiceIsNotAnError() {
        // Deux onglets ouverts sur la même annonce : le second clic arrive après le
        // premier, et n'a rien de fautif.
        AppUser user = player();
        UUID announcementId = UUID.randomUUID();
        when(users.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(announcements.existsById(announcementId)).thenReturn(true);
        when(dismissals.existsById(any(AnnouncementDismissal.Key.class))).thenReturn(true);

        service.dismiss("player-sub", announcementId);

        verify(dismissals, never()).save(any());
    }

    @Test
    void closingRemembersWhoClosedWhat() {
        AppUser user = player();
        UUID announcementId = UUID.randomUUID();
        when(users.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(announcements.existsById(announcementId)).thenReturn(true);
        when(dismissals.existsById(any(AnnouncementDismissal.Key.class))).thenReturn(false);

        service.dismiss("player-sub", announcementId);

        verify(dismissals).save(any(AnnouncementDismissal.class));
    }

    @Test
    void closingAnAnnouncementThatNoLongerExistsSaysSo() {
        when(users.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player()));
        when(announcements.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.dismiss("player-sub", UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        AnnouncementRequest backwards = new AnnouncementRequest(
                "Titre", "Corps", AnnouncementKind.INFO, true,
                Instant.parse("2026-09-08T12:00:00Z"), Instant.parse("2026-09-07T12:00:00Z"));

        assertThat(backwards.isValidWindow()).isFalse();
    }

    @Test
    void anAnnouncementIsActiveUnlessSaidOtherwise() {
        // On écrit une annonce pour qu'elle s'affiche : l'absence du champ ne doit
        // pas la laisser muette.
        AnnouncementRequest request = new AnnouncementRequest(
                "Titre", "Corps", AnnouncementKind.INFO, null, null, null);

        assertThat(request.activeOrDefault()).isTrue();
    }

    @Test
    void listingSortsTheNewestFirst() {
        when(announcements.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(announcement(true, null, null)));

        assertThat(service.findAll()).hasSize(1);
        verify(announcements).findAllByOrderByCreatedAtDesc();
    }
}
