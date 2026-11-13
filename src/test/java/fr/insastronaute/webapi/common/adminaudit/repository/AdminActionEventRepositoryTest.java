package fr.insastronaute.webapi.common.adminaudit.repository;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AdminActionEventRepositoryTest {

    @Autowired
    private AdminActionEventRepository events;

    @Autowired
    private UserRepository users;

    @Test
    void eventsOfOnePlayerComeBackNewestFirst() {
        AppUser admin = user("sub-admin", "admin@example.test", "Admin");
        AppUser player = user("sub-player", "player@example.test", "Joueur");
        AppUser other = user("sub-other", "other@example.test", "Autre");

        events.saveAndFlush(new AdminActionEvent(admin, player, AdminActionType.TOKENS_GRANTED, "{}", false));
        events.saveAndFlush(new AdminActionEvent(admin, player, AdminActionType.TOKENS_REVOKED, "{}", false));
        events.saveAndFlush(new AdminActionEvent(admin, other, AdminActionType.CHALLENGE_GRANTED, "{}", false));

        List<AdminActionEvent> found = events.findByTargetIdOrderByOccurredAtDesc(player.getId());

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(event -> event.getTarget().getId().equals(player.getId()));
        assertThat(found.getFirst().getOccurredAt()).isAfterOrEqualTo(found.getLast().getOccurredAt());
    }

    @Test
    void anEventRemembersWhoActedAndWhetherItWasBackfilled() {
        AppUser admin = user("sub-admin", "admin@example.test", "Admin");
        AppUser player = user("sub-player", "player@example.test", "Joueur");

        AdminActionEvent saved = events.saveAndFlush(new AdminActionEvent(
                admin, player, AdminActionType.CHALLENGE_VALIDATED, "{\"title\":\"Bullseye\"}", true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOccurredAt()).isNotNull();
        assertThat(saved.getActor().getId()).isEqualTo(admin.getId());
        assertThat(saved.isBackfilled()).isTrue();
        assertThat(saved.getDetailsJson()).contains("Bullseye");
    }

    /**
     * Un geste de portee globale n'a pas de cible : il a un auteur et N joueurs
     * touches, et sa ligne nomme sa portee. La colonne doit donc accepter
     * l'absence de joueur.
     */
    @Test
    void aGlobalGestureIsStoredWithoutATarget() {
        AppUser admin = user("sub-admin", "admin@example.test", "Admin");

        AdminActionEvent saved = events.saveAndFlush(new AdminActionEvent(
                admin, null, AdminActionType.COLLECTION_SCALE_CHANGED,
                "{\"scope\":\"CARD_CATEGORY\",\"affectedPlayers\":42}", false));

        assertThat(saved.getTarget()).isNull();
        assertThat(saved.getActor().getId()).isEqualTo(admin.getId());
        assertThat(saved.getDetailsJson()).contains("affectedPlayers");
    }

    private AppUser user(String sub, String email, String displayName) {
        AppUser user = new AppUser();
        user.setKeycloakSub(sub);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return users.saveAndFlush(user);
    }
}
