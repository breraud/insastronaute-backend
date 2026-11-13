package fr.insastronaute.webapi.common.adminaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verrouille l'atomicite du registre : une ligne qui y figure doit vouloir
 * dire que le geste a reellement tenu.
 *
 * <p>AdminActionJournal n'a deliberement aucune annotation transactionnelle :
 * il partage la transaction de l'appelant. Si quelqu'un pose un jour
 * REQUIRES_NEW dessus pour "ne pas perdre l'audit", ce test tombe.</p>
 *
 * <p>{@code @DataJpaTest} enveloppe chaque test dans sa propre transaction,
 * annulee a la fin du test. Pour observer un vrai rollback pendant le test
 * (et non seulement apres), cette classe suspend cette enveloppe avec
 * {@code Propagation.NOT_SUPPORTED} : le {@link TransactionTemplate} ouvre
 * alors sa propre transaction physique, qui s'annule reellement quand le
 * geste echoue.</p>
 *
 * <p>Consequence directe : rien ici n'est plus jamais annule automatiquement
 * par le framework de test, y compris la fixture. Comme plusieurs classes
 * {@code @DataJpaTest} partagent le meme contexte Spring (et donc la meme
 * base H2 embarquee) tant que leur configuration est identique, {@code
 * tearDown} supprime explicitement ce que {@code setUp} a cree pour ne rien
 * laisser fuiter vers les autres suites.</p>
 *
 * <p>{@code AdminActionJournal} est obtenu via le contexte Spring (voir
 * {@link Config}), pas construit a la main : c'est la seule facon pour que le
 * proxy transactionnel de Spring entre en jeu. Si {@code record} recevait un
 * jour une annotation {@code @Transactional}, un {@code new
 * AdminActionJournal(...)} brut l'ignorerait completement et ce test
 * continuerait de passer a tort ; un bean recupere par {@code @Autowired}
 * passe par le meme tissage AOP que la production.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(AdminJournalAtomicityTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminJournalAtomicityTest {

    @TestConfiguration
    static class Config {
        @Bean
        AdminActionJournal journal(AdminActionEventRepository events, UserRepository users) {
            return new AdminActionJournal(events, users, new ObjectMapper());
        }
    }

    @Autowired
    private AdminActionEventRepository events;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AdminActionJournal journal;

    private TransactionTemplate transactionTemplate;
    private AppUser admin;
    private AppUser player;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        admin = users.saveAndFlush(user("sub-admin", "admin@example.test", "Admin"));
        player = users.saveAndFlush(user("sub-player", "player@example.test", "Joueur"));
    }

    // Rien n'est annule automatiquement pour cette classe (voir javadoc de
    // classe) : on rend explicitement la base a l'etat trouve avant le test,
    // pour que les autres @DataJpaTest partageant le meme contexte ne voient
    // jamais cette fixture.
    @AfterEach
    void tearDown() {
        events.deleteAll(events.findByTargetIdOrderByOccurredAtDesc(player.getId()));
        users.delete(player);
        users.delete(admin);
    }

    /**
     * Le journal partage la transaction du geste.
     *
     * <p>Une ligne presente dans le registre doit vouloir dire que le geste a
     * tenu. Si quelqu'un pose un jour REQUIRES_NEW sur AdminActionJournal pour
     * "ne pas perdre l'audit", ce test tombe -- et c'est le bon comportement :
     * un registre qui affirme des dons annules est pire que pas de registre.</p>
     */
    @Test
    void aGestureRolledBackLeavesNoTrace() {
        UUID playerId = player.getId();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            journal.record("sub-admin", playerId, AdminActionType.TOKENS_GRANTED, Map.of("delta", 10L));
            throw new IllegalStateException("le geste echoue apres la journalisation");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(events.findAll()).isEmpty();
    }

    private static AppUser user(String sub, String email, String displayName) {
        AppUser user = new AppUser();
        user.setKeycloakSub(sub);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return user;
    }
}
