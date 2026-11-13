package fr.insastronaute.webapi.common.adminaudit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionEventDto;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionScope;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionTypeDto;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionEventQueryService;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les filtres du registre, contre la base.
 *
 * <p>Le service de lecture est construit a la main plutot qu'injecte : le
 * decoupage {@code @DataJpaTest} ne monte pas les {@code @Service}, et ce qu'on
 * veut eprouver ici est justement le couple requete + projection, pas le
 * cablage Spring.</p>
 *
 * <p>Les statistiques Hibernate sont allumees pour un seul test, celui qui
 * compte les requetes d'une page. Une promesse de "une requete pour toute la
 * page" qu'on ne compte pas n'est pas une promesse.</p>
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class AdminActionEventSearchTest {

    private static final Instant MONDAY = Instant.parse("2026-08-24T10:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2026-08-26T10:00:00Z");

    @Autowired
    private AdminActionEventRepository events;

    @Autowired
    private UserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private AdminActionEventQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AdminActionEventQueryService(events, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TimeTravelContext.clear();
    }

    @Test
    void theRegisterComesBackNewestFirst() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, player, AdminActionType.TOKENS_REVOKED, WEDNESDAY);
        record(admin, player, AdminActionType.CHALLENGE_GRANTED, TUESDAY);

        List<AdminActionEventDto> content = search().content();

        assertThat(content).extracting(AdminActionEventDto::action).containsExactly(
                AdminActionType.TOKENS_REVOKED,
                AdminActionType.CHALLENGE_GRANTED,
                AdminActionType.TOKENS_GRANTED);
    }

    /**
     * Une ligne de portee globale n'a pas de cible : demander un joueur precis
     * ne doit jamais la ramener. La propriete tient a la colonne nulle -
     * {@code null = ?} n'est jamais vrai - mais elle repose aussi sur la
     * jointure externe explicite de la requete, d'ou ce test plutot qu'une
     * deduction.
     */
    @Test
    void aGlobalRowIsNeverBroughtBackByATargetFilter() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, null, AdminActionType.COLLECTION_SCALE_CHANGED, TUESDAY);
        record(admin, null, AdminActionType.CHALLENGE_DELETED, WEDNESDAY);

        PageResponse<AdminActionEventDto> page = queryService.search(
                null, player.getId(), null, AdminActionScope.ALL, null, null, PageRequest.of(0, 50));

        assertThat(page.content()).extracting(AdminActionEventDto::action)
                .containsExactly(AdminActionType.TOKENS_GRANTED);
        // Le total est ici deduit du contenu (page non pleine) : la requete
        // de comptage ne part pas. C'est theCountBehindThePaginationObeysTheSameFilters,
        // avec une page d'une seule ligne, qui eprouve la meme regle cote
        // comptage.
        assertThat(page.totalElements()).isEqualTo(1);
    }

    /**
     * Une ligne reprise sans moderateur retrouvable n'a pas d'auteur : demander
     * un auteur precis ne doit pas davantage la ramener. Meme propriete de
     * colonne nulle que ci-dessus, verifiee separement plutot que deduite de
     * l'autre.
     */
    @Test
    void anAuthorlessRowIsNeverBroughtBackByAnActorFilter() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.CHALLENGE_VALIDATED, MONDAY);
        events.saveAndFlush(new AdminActionEvent(
                null, player, AdminActionType.CHALLENGE_VALIDATED, "{}", true));

        PageResponse<AdminActionEventDto> page = queryService.search(
                admin.getId(), null, null, AdminActionScope.ALL, null, null, PageRequest.of(0, 50));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().actor().id()).isEqualTo(admin.getId());
        assertThat(page.totalElements()).isEqualTo(1);
    }

    /**
     * La reprise cree des lignes sans auteur : elles doivent traverser la
     * lecture entiere - requete, resolution des noms, projection - sans
     * produire ni erreur ni auteur fantome.
     */
    @Test
    void anAuthorlessRowSurvivesTheWholeReadPath() {
        AppUser player = user("sub-player", "Joueur");
        events.saveAndFlush(new AdminActionEvent(
                null, player, AdminActionType.CHALLENGE_VALIDATED,
                "{\"challengeTitle\":\"Bullseye\",\"points\":30}", true));
        entityManager.flush();
        entityManager.clear();

        AdminActionEventDto row = search().content().getFirst();

        assertThat(row.actor()).isNull();
        assertThat(row.target().displayName()).isEqualTo("Joueur");
        assertThat(row.backfilled()).isTrue();
        assertThat(row.details()).containsEntry("challengeTitle", "Bullseye").containsEntry("points", 30);
    }

    /** Une ligne de portee globale s'annonce comme telle et ne nomme personne. */
    @Test
    void aGlobalRowKeepsNoTargetAndSaysWhy() {
        AppUser admin = user("sub-admin", "Admin");
        record(admin, null, AdminActionType.COLLECTION_SCALE_CHANGED, MONDAY,
                "{\"scope\":\"CARD_CATEGORY\",\"affectedPlayers\":42}");
        entityManager.flush();
        entityManager.clear();

        AdminActionEventDto row = search().content().getFirst();

        assertThat(row.target()).isNull();
        assertThat(row.globalScope()).isTrue();
        assertThat(row.actor().displayName()).isEqualTo("Admin");
        assertThat(row.details()).containsEntry("scope", "CARD_CATEGORY").containsEntry("affectedPlayers", 42);
    }

    /**
     * Le filtre de portee rend exactement les types que
     * {@link AdminActionType#isGlobalScope()} designe - l'attendu est derive de
     * l'enumeration, pas recopie : une valeur ajoutee demain tombe du bon cote
     * sans toucher a ce test, et un type mal classe le fait echouer.
     */
    @Test
    void theScopeFilterReturnsExactlyTheTypesTheEnumDesignates() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        for (AdminActionType action : AdminActionType.values()) {
            record(admin, action.isGlobalScope() ? null : player, action, MONDAY);
        }

        assertThat(actionsOf(AdminActionScope.GLOBAL)).isEqualTo(typesWhereGlobalScopeIs(true));
        assertThat(actionsOf(AdminActionScope.NOMINAL)).isEqualTo(typesWhereGlobalScopeIs(false));
        assertThat(actionsOf(AdminActionScope.ALL))
                .isEqualTo(Arrays.stream(AdminActionType.values()).collect(Collectors.toSet()));
    }

    /**
     * Les deux axes se croisent au lieu de s'ecraser : demander la portee
     * globale et une action nominative ne peut rien rendre, et le dire par une
     * page vide vaut mieux que par les treize types.
     */
    @Test
    void anImpossibleCombinationOfScopeAndActionIsAnEmptyPage() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, null, AdminActionType.CHALLENGE_DELETED, TUESDAY);

        PageResponse<AdminActionEventDto> page = queryService.search(
                null, null, List.of(AdminActionType.TOKENS_GRANTED), AdminActionScope.GLOBAL,
                null, null, PageRequest.of(0, 50));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    /**
     * La requete de comptage suit les memes filtres que celle de la page.
     *
     * <p>Elle ne part que lorsque le total ne se deduit pas du contenu, d'ou
     * la page d'une seule ligne. C'est ici, cote cible, qu'elle est eprouvee :
     * sans la jointure externe, une jointure interne cote comptage ferait
     * disparaitre du total les lignes de portee globale, que la page, elle,
     * affiche. Le cote auteur a son propre test,
     * theCountIncludesAnAuthorlessRowWithNoActorFilter.</p>
     */
    @Test
    void theCountBehindThePaginationObeysTheSameFilters() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, player, AdminActionType.TOKENS_REVOKED, TUESDAY);
        record(admin, null, AdminActionType.COLLECTION_SCALE_CHANGED, WEDNESDAY);
        record(admin, null, AdminActionType.CHALLENGE_DELETED, WEDNESDAY);

        PageResponse<AdminActionEventDto> nominal = queryService.search(
                null, player.getId(), null, AdminActionScope.ALL, null, null, PageRequest.of(0, 1));
        assertThat(nominal.content()).hasSize(1);
        assertThat(nominal.totalElements()).isEqualTo(2);
        assertThat(nominal.totalPages()).isEqualTo(2);

        PageResponse<AdminActionEventDto> global = queryService.search(
                null, null, null, AdminActionScope.GLOBAL, null, null, PageRequest.of(0, 1));
        assertThat(global.content()).hasSize(1);
        assertThat(global.totalElements()).isEqualTo(2);
    }

    /**
     * Le comptage cote auteur, seul angle mort de la suite avant ce test.
     *
     * <p>Des quatre jambes de jointure - page cote cible, page cote auteur,
     * comptage cote cible, comptage cote auteur - les trois premieres
     * echoueraient deja si on les rendait internes. Celle-ci ne l'aurait pas
     * fait : chaque test qui porte une ligne sans auteur utilise une page de
     * taille 50, et {@code PageableExecutionUtils} saute alors la requete de
     * comptage - son total vient de {@code content.size()}, jamais du SQL.
     * Une page de taille 1 est necessaire pour forcer son execution.</p>
     */
    @Test
    void theCountIncludesAnAuthorlessRowWithNoActorFilter() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        events.saveAndFlush(new AdminActionEvent(
                null, player, AdminActionType.CHALLENGE_VALIDATED, "{}", true));

        PageResponse<AdminActionEventDto> page = queryService.search(
                null, null, null, AdminActionScope.ALL, null, null, PageRequest.of(0, 1));

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    /** Une periode : borne basse incluse, borne haute exclue. */
    @Test
    void theLowerBoundIsInclusiveAndTheUpperBoundIsExclusive() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, player, AdminActionType.TOKENS_REVOKED, TUESDAY);
        record(admin, player, AdminActionType.CHALLENGE_GRANTED, WEDNESDAY);

        PageResponse<AdminActionEventDto> page = queryService.search(
                null, null, null, AdminActionScope.ALL, MONDAY, WEDNESDAY, PageRequest.of(0, 50));

        assertThat(page.content()).extracting(AdminActionEventDto::action)
                .containsExactly(AdminActionType.TOKENS_REVOKED, AdminActionType.TOKENS_GRANTED);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    /** Auteur et periode se combinent : chaque filtre restreint, aucun n'annule l'autre. */
    @Test
    void anActorAndAPeriodCombine() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser other = user("sub-other", "Autre admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, player, AdminActionType.TOKENS_REVOKED, WEDNESDAY);
        record(other, player, AdminActionType.TOKENS_GRANTED, WEDNESDAY);

        PageResponse<AdminActionEventDto> page = queryService.search(
                admin.getId(), null, null, AdminActionScope.ALL, TUESDAY, null, PageRequest.of(0, 50));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().action()).isEqualTo(AdminActionType.TOKENS_REVOKED);
        assertThat(page.content().getFirst().actor().displayName()).isEqualTo("Admin");
    }

    /**
     * La promesse comptee, pas supposee. Le meme filtre est mesure sur deux
     * lignes puis sur cinq : c'est l'egalite des deux comptes qui dit qu'il n'y
     * a pas une requete par ligne, et elle tient quel que soit le nombre de
     * requetes qu'une page coute par ailleurs.
     *
     * <p>Une page complete en coute une seule : Spring Data n'emet pas la
     * requete de comptage quand la premiere page n'est pas pleine, le total
     * etant alors deja connu.</p>
     *
     * <p>Le contexte de persistance est vide avant chaque mesure : sans cela,
     * les entites du montage y seraient deja et aucune requete ne partirait,
     * meme sans les {@code join fetch} - le test passerait pour rien.</p>
     */
    @Test
    void theNamesOfAWholePageDoNotCostOneQueryPerRow() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser otherAdmin = user("sub-other-admin", "Autre admin");
        record(admin, user("sub-first", "Premier"), AdminActionType.TOKENS_GRANTED, MONDAY);
        record(otherAdmin, user("sub-second", "Deuxieme"), AdminActionType.TOKENS_REVOKED, MONDAY);

        long forTwoRows = statementsForAFullPageOf(2);

        record(admin, user("sub-third", "Troisieme"), AdminActionType.CHALLENGE_GRANTED, TUESDAY);
        record(otherAdmin, user("sub-fourth", "Quatrieme"), AdminActionType.PHYSICAL_CARD_ASSIGNED, TUESDAY);
        record(otherAdmin, null, AdminActionType.CHALLENGE_DELETED, WEDNESDAY);

        long forFiveRows = statementsForAFullPageOf(5);

        assertThat(forTwoRows).isEqualTo(1);
        assertThat(forFiveRows).isEqualTo(forTwoRows);
    }

    /**
     * Lit une page entiere, noms compris, et rend le nombre d'ordres SQL que
     * cela a coute.
     */
    private long statementsForAFullPageOf(int expectedRows) {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<AdminActionEventDto> content = search().content();

        assertThat(content).hasSize(expectedRows);
        // Les noms sont bien resolus : un compteur bas avec des noms nuls
        // voudrait dire que rien n'a ete charge.
        assertThat(content).allSatisfy(row -> {
            if (row.actor() != null) {
                assertThat(row.actor().displayName()).isNotBlank();
            }
            if (row.target() != null) {
                assertThat(row.target().displayName()).isNotBlank();
            }
        });
        return statistics.getPrepareStatementCount();
    }

    /**
     * Un tri demande par l'appelant est ignore : un registre se lit dans son
     * ordre, et une colonne triee autrement en ferait autre chose. Le
     * parametre n'est pas non plus une erreur - il ne doit rien casser.
     */
    @Test
    void aSortAskedByTheCallerIsIgnoredRatherThanObeyed() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY);
        record(admin, player, AdminActionType.TOKENS_REVOKED, WEDNESDAY);

        PageResponse<AdminActionEventDto> page = queryService.search(
                null, null, null, AdminActionScope.ALL, null, null,
                PageRequest.of(0, 50, Sort.by("occurredAt").ascending()));

        assertThat(page.content()).extracting(AdminActionEventDto::action)
                .containsExactly(AdminActionType.TOKENS_REVOKED, AdminActionType.TOKENS_GRANTED);
    }

    /**
     * Une ligne aux details illisibles ne peut pas etre reparee : la table est
     * en ajout seul. Elle ne doit donc pas emporter la page avec elle - le
     * reste de la ligne, lui, se lit encore.
     */
    @Test
    void anUnreadableDetailsColumnDoesNotTakeTheWholePageDown() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-player", "Joueur");
        record(admin, player, AdminActionType.TOKENS_GRANTED, MONDAY, "pas du json");
        record(admin, player, AdminActionType.TOKENS_REVOKED, WEDNESDAY, "{\"delta\":-20}");

        List<AdminActionEventDto> content = search().content();

        assertThat(content).hasSize(2);
        assertThat(content.getFirst().details()).containsEntry("delta", -20);
        assertThat(content.getLast().details()).isEmpty();
        assertThat(content.getLast().actor().displayName()).isEqualTo("Admin");
    }

    /**
     * Le catalogue servi a la barre de filtres est l'enumeration elle-meme :
     * la page n'en garde pas de copie, et une valeur ajoutee y apparait sans
     * qu'on touche au panel.
     */
    @Test
    void theServedActionTypesAreTheEnumeration() {
        assertThat(queryService.actionTypes()).containsExactlyElementsOf(
                Arrays.stream(AdminActionType.values()).map(AdminActionTypeDto::from).toList());
    }

    private PageResponse<AdminActionEventDto> search() {
        return queryService.search(null, null, null, AdminActionScope.ALL, null, null, PageRequest.of(0, 50));
    }

    private Set<AdminActionType> actionsOf(AdminActionScope scope) {
        return queryService.search(null, null, null, scope, null, null, PageRequest.of(0, 50))
                .content().stream().map(AdminActionEventDto::action).collect(Collectors.toSet());
    }

    private Set<AdminActionType> typesWhereGlobalScopeIs(boolean globalScope) {
        return Arrays.stream(AdminActionType.values())
                .filter(action -> action.isGlobalScope() == globalScope)
                .collect(Collectors.toSet());
    }

    private void record(AppUser actor, AppUser target, AdminActionType action, Instant at) {
        record(actor, target, action, at, "{}");
    }

    /**
     * Une ligne a une date choisie. Le constructeur a instant explicite est le
     * seul moyen de la dater : le constructeur vivant lit l'horloge murale et
     * ignore {@code TimeTravelContext}, deliberement - un registre dont la date
     * suit l'en-tete {@code X-Time-Travel-Date} se laisserait antidater par la
     * personne meme qu'il tient comptable.
     */
    private void record(AppUser actor, AppUser target, AdminActionType action, Instant at, String detailsJson) {
        events.saveAndFlush(new AdminActionEvent(at, actor, target, action, detailsJson, false));
    }

    private AppUser user(String sub, String displayName) {
        AppUser user = new AppUser();
        user.setKeycloakSub(sub);
        user.setEmail(sub + "@example.test");
        user.setDisplayName(displayName);
        return users.saveAndFlush(user);
    }
}
