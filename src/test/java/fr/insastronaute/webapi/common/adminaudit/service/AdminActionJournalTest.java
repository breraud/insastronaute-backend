package fr.insastronaute.webapi.common.adminaudit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminActionJournalTest {

    /** La date qu'un administrateur pourrait forcer par X-Time-Travel-Date. */
    private static final Instant FORCED = Instant.parse("2020-01-01T00:00:00Z");

    /** La date d'un geste anterieur au registre, telle qu'une reprise la relit. */
    private static final Instant BACK_THEN = Instant.parse("2026-05-07T12:00:00Z");

    private AdminActionEventRepository events;
    private UserRepository users;
    private AdminActionJournal journal;
    private AppUser admin;
    private AppUser player;

    @BeforeEach
    void setUp() {
        events = mock(AdminActionEventRepository.class);
        users = mock(UserRepository.class);
        journal = new AdminActionJournal(events, users, new ObjectMapper());

        admin = user(UUID.randomUUID(), "sub-admin", "Admin");
        player = user(UUID.randomUUID(), "sub-player", "Joueur");
        when(users.findByKeycloakSub("sub-admin")).thenReturn(Optional.of(admin));
        when(users.findById(player.getId())).thenReturn(Optional.of(player));
    }

    @AfterEach
    void tearDown() {
        TimeTravelContext.clear();
    }

    @Test
    void aRecordedGestureNamesItsAuthorAndItsTarget() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);

        journal.record("sub-admin", player.getId(), AdminActionType.TOKENS_GRANTED, Map.of("delta", 50));

        verify(events).save(captor.capture());
        AdminActionEvent saved = captor.getValue();
        assertThat(saved.getActor()).isSameAs(admin);
        assertThat(saved.getTarget()).isSameAs(player);
        assertThat(saved.getAction()).isEqualTo(AdminActionType.TOKENS_GRANTED);
        assertThat(saved.getDetailsJson()).isEqualTo("{\"delta\":50}");
        assertThat(saved.isBackfilled()).isFalse();
    }

    /**
     * Meme garde que ModerationAuditService, meme raison : un registre
     * consultable ne doit pas devenir un endroit ou fuient des URL signees.
     */
    @Test
    void sensitiveMetadataIsRefusedRatherThanStored() {
        assertThatThrownBy(() -> journal.record(
                "sub-admin", player.getId(), AdminActionType.TOKENS_GRANTED, Map.of("proofUrl", "https://x")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(events, never()).save(any());
    }

    @Test
    void anUnknownAuthorIsAnError() {
        when(users.findByKeycloakSub("sub-ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> journal.record(
                "sub-ghost", player.getId(), AdminActionType.TOKENS_GRANTED, Map.of()))
                .isInstanceOf(NotFoundException.class);

        verify(events, never()).save(any());
    }

    /**
     * Un geste de portee globale : un auteur, une portee, aucune cible a
     * resoudre.
     */
    @Test
    void aGlobalGestureNamesItsScopeRatherThanAPlayer() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);

        journal.recordGlobal("sub-admin", AdminActionType.CHALLENGE_POINTS_CHANGED,
                Map.of("affectedPlayers", 17L));

        verify(events).save(captor.capture());
        AdminActionEvent saved = captor.getValue();
        assertThat(saved.getActor()).isSameAs(admin);
        assertThat(saved.getTarget()).isNull();
        assertThat(saved.getAction()).isEqualTo(AdminActionType.CHALLENGE_POINTS_CHANGED);
        assertThat(saved.isBackfilled()).isFalse();
    }

    /**
     * La contrainte ck_admin_action_events_target_presence rattraperait un
     * cablage inverse a l'insertion. Echouer ici donne a l'appelant une erreur
     * comprehensible plutot qu'une violation de contrainte.
     */
    @Test
    void aGlobalTypeIsRefusedByRecordAndANominalOneByRecordGlobal() {
        assertThatThrownBy(() -> journal.record(
                "sub-admin", player.getId(), AdminActionType.COLLECTION_SCALE_CHANGED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordGlobal");

        // Fragment distinctif : "record" seul serait aussi satisfait par un
        // message qui dit "recordGlobal", et les deux moities du refus se
        // verifieraient alors l'une l'autre.
        assertThatThrownBy(() -> journal.recordGlobal(
                "sub-admin", AdminActionType.TOKENS_GRANTED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s'ecrit avec record, avec une cible");

        verify(events, never()).save(any());
    }

    /**
     * {@code recordBackfilled} est la troisieme porte vers la table : sans la
     * meme garde, un type de portee globale y passerait avec une cible et ne
     * serait rattrape que par la contrainte de presence, en base.
     */
    @Test
    void aGlobalTypeIsRefusedByRecordBackfilledToo() {
        assertThatThrownBy(() -> journal.recordBackfilled(
                Instant.parse("2026-01-02T03:04:05Z"), admin, player,
                AdminActionType.COLLECTION_SCALE_CHANGED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordGlobal");

        verify(events, never()).save(any());
    }

    @Test
    void aBackfilledEventIsMarkedAsSuch() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);

        journal.recordBackfilled(BACK_THEN, admin, player, AdminActionType.CHALLENGE_VALIDATED,
                Map.of("title", "Bullseye"));

        verify(events).save(captor.capture());
        assertThat(captor.getValue().isBackfilled()).isTrue();
    }

    /**
     * Une reprise porte la date du geste d'origine, pas celle de la reprise :
     * c'est tout ce qui la distingue d'une invention. Le parametre est
     * obligatoire, donc un appelant futur ne peut pas l'oublier ; ce test
     * epingle qu'il est bien celui qui date la ligne.
     */
    @Test
    void aBackfilledEventIsDatedByTheGestureItRevisitsNotByTheReplay() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);

        journal.recordBackfilled(BACK_THEN, admin, player, AdminActionType.CHALLENGE_VALIDATED, Map.of());

        verify(events).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(BACK_THEN);
    }

    /**
     * <strong>Le coeur de la redevabilite.</strong> {@code TimeTravelFilter}
     * laisse toute requete portant {@code ROLE_ADMIN} ou {@code ROLE_STAFF}
     * fixer {@code TimeTravelContext} par l'en-tete
     * {@code X-Time-Travel-Date}. Si la ligne du registre suivait cette
     * horloge, sa date serait choisie par la personne meme que le registre
     * existe pour tenir comptable : un don de 5000 tokens pose sous
     * {@code 2020-01-01} tomberait sous la derniere page d'un registre trie du
     * plus recent au plus ancien, sans que rien n'ait ete supprime.
     *
     * <p>Ce test echoue si {@code AdminActionEvent} revient a
     * {@code TimeTravelContext.instantNow()}.</p>
     */
    @Test
    void aLiveRowIsDatedByTheWallClockEvenUnderATravelledOne() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        Instant before = Instant.now();
        TimeTravelContext.setForcedTime(FORCED);

        journal.record("sub-admin", player.getId(), AdminActionType.TOKENS_GRANTED, Map.of("delta", 5000));

        verify(events).save(captor.capture());
        Instant occurredAt = captor.getValue().getOccurredAt();
        assertThat(occurredAt).isNotEqualTo(FORCED);
        assertThat(occurredAt).isBetween(before.minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.MINUTES));
    }

    /** Meme regle sur un geste de portee globale : l'horloge murale, la aussi. */
    @Test
    void aGlobalRowIsDatedByTheWallClockEvenUnderATravelledOne() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        Instant before = Instant.now();
        TimeTravelContext.setForcedTime(FORCED);

        journal.recordGlobal("sub-admin", AdminActionType.COLLECTION_SCALE_CHANGED, Map.of("scope", "CARD_CATEGORY"));

        verify(events).save(captor.capture());
        Instant occurredAt = captor.getValue().getOccurredAt();
        assertThat(occurredAt).isNotEqualTo(FORCED);
        assertThat(occurredAt).isBetween(before.minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.MINUTES));
    }

    /**
     * La date forcee n'est pas perdue pour autant : elle descend dans les
     * details. Les effets du geste ont ete calcules contre elle - une fenetre
     * de defi, une expiration - et sans cette cle un lecteur six mois plus tard
     * verrait un resultat inexplicable sans pouvoir apprendre pourquoi.
     */
    @Test
    void aGestureUnderATravelledClockSaysSoInItsDetails() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        TimeTravelContext.setForcedTime(FORCED);

        journal.record("sub-admin", player.getId(), AdminActionType.TOKENS_GRANTED, Map.of("delta", 50));

        verify(events).save(captor.capture());
        assertThat(captor.getValue().getDetailsJson())
                .contains("\"" + AdminActionJournal.TIME_TRAVEL_DETAIL_KEY + "\":\"" + FORCED + "\"");
    }

    /**
     * Sans date forcee, la cle est <strong>absente</strong> et non nulle : une
     * cle presente se lirait comme une information, et le cas ordinaire est de
     * loin le plus frequent.
     */
    @Test
    void aGestureUnderTheOrdinaryClockCarriesNoSuchKey() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);

        journal.recordGlobal("sub-admin", AdminActionType.COLLECTION_SCALE_CHANGED, Map.of("scope", "CARD_CATEGORY"));

        verify(events).save(captor.capture());
        assertThat(captor.getValue().getDetailsJson()).doesNotContain(AdminActionJournal.TIME_TRAVEL_DETAIL_KEY);
    }

    /**
     * La cle ajoutee passe la garde des metadonnees sensibles comme les autres.
     * Elle est ajoutee par le service et non par un appelant : sans ce test,
     * un renommage vers un nom interdit ne se decouvrirait qu'a la premiere
     * ligne perdue en production.
     */
    @Test
    void theTravelledClockKeyItselfPassesTheGuard() {
        AdminActionJournal.rejectSensitiveDetailKeys(Map.of(AdminActionJournal.TIME_TRAVEL_DETAIL_KEY, "x"));
    }

    /**
     * Toutes les cles de details reellement ecrites en production, passees par
     * la garde.
     *
     * <p>La garde refuse au moment de l'ecriture, pas a la compilation : une
     * cle nommee "tokensDelta" ou "proofComment" ne se decouvrirait qu'a la
     * premiere ligne perdue en production. Ce test fait le tour des points
     * d'ecriture pour que la decouverte se fasse ici.</p>
     *
     * <p>Une nouvelle cle de details doit etre ajoutee a cette liste.</p>
     */
    @Test
    void everyDetailKeyWrittenInProductionPassesTheGuard() {
        Map<String, Object> allKeys = new LinkedHashMap<>();
        // AdminUserService.updateTokenBalance
        allKeys.put("delta", 50L);
        allKeys.put("balance", 120L);
        // ChallengeService.decideSubmission et createApprovedManualSubmission
        allKeys.put("challengeTitle", "Bullseye");
        allKeys.put("points", 30);
        allKeys.put("bonusPoints", 5);
        allKeys.put("comment", "accorde a la main");
        // La composition d'une soumission de groupe, ecrite par decideSubmission
        // et par la reprise du passe.
        allKeys.put("participantNames", java.util.List.of("Auteur", "Coequipiere"));
        allKeys.put("participantIds", java.util.List.of(UUID.randomUUID().toString()));
        // CardService.grant et AdminUserService.addCards / removeCards (VIRTUAL)
        allKeys.put("cardId", UUID.randomUUID().toString());
        allKeys.put("cardName", "Carte Rare");
        allKeys.put("quantity", 2);
        // CardInstanceService (createAdmin, assign, updateAdmin, release, delete)
        allKeys.put("publicId", "ABCDE-FGHJK-MNPQR");
        // AdminUserService.addCards / removeCards (PHYSICAL)
        allKeys.put("instanceId", UUID.randomUUID().toString());
        // Toutes les lignes de portee globale, qui nomment ce qui a bouge :
        // CardCategoryService.update, CardCollectionSettingsService.update,
        // ChallengeService.updateChallenge et deleteChallenge.
        allKeys.put("scope", "CARD_CATEGORY");
        // CardCategoryService.update et CardCollectionSettingsService.update
        allKeys.put("categoryId", UUID.randomUUID().toString());
        allKeys.put("categoryName", "Legendaire");
        allKeys.put("fromFirstAcquisitionPoints", 50);
        allKeys.put("toFirstAcquisitionPoints", 80);
        allKeys.put("fromDuplicatePoints", 5);
        allKeys.put("toDuplicatePoints", 8);
        allKeys.put("fromDuplicatesPerPoint", 10);
        allKeys.put("toDuplicatesPerPoint", 5);
        allKeys.put("fromPhysicalFirstAcquisitionMultiplier", 2);
        allKeys.put("toPhysicalFirstAcquisitionMultiplier", 3);
        allKeys.put("fromPhysicalDuplicatesEnabled", false);
        allKeys.put("toPhysicalDuplicatesEnabled", true);
        // Le reglage s'appelle tokensPerPoint dans le domaine : la garde
        // refuse toute cle contenant "token", d'ou ce nom.
        allKeys.put("fromPayoutPerPoint", 1);
        allKeys.put("toPayoutPerPoint", 2);
        allKeys.put("affectedPlayers", 42L);
        // ChallengeService.updateChallenge
        allKeys.put("challengeId", UUID.randomUUID().toString());
        allKeys.put("fromPoints", 10);
        allKeys.put("toPoints", 30);
        // ChallengeService.deleteChallenge n'ecrit que des cles deja listees
        // ci-dessus : challengeId, challengeTitle, points, affectedPlayers.
        // AdminActionJournal, sur un geste pose sous X-Time-Travel-Date.
        allKeys.put(AdminActionJournal.TIME_TRAVEL_DETAIL_KEY, "2020-01-01T00:00:00Z");

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);

        journal.record("sub-admin", player.getId(), AdminActionType.TOKENS_GRANTED, allKeys);

        verify(events).save(captor.capture());
        assertThat(captor.getValue().getDetailsJson()).contains(allKeys.keySet());
    }

    private static AppUser user(UUID id, String sub, String displayName) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(sub);
        user.setDisplayName(displayName);
        return user;
    }
}
