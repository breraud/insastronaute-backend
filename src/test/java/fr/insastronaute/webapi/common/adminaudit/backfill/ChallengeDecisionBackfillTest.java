package fr.insastronaute.webapi.common.adminaudit.backfill;

import fr.insastronaute.webapi.challenge.audit.ModerationAuditEvent;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La reprise des gestes de defi passes, testee seule.
 *
 * <p>Flyway ne tourne jamais en test - la suite est sur H2 avec
 * {@code spring.flyway.enabled=false} et un schema regenere. La migration V115
 * ne fait donc rien d'autre qu'appeler {@link ChallengeDecisionBackfill}, et
 * c'est cette classe qu'on eprouve ici, sur le vrai schema des entites et par
 * la vraie connexion JDBC.</p>
 *
 * <p>Quatre proprietes comptent plus que la reprise elle-meme, et chacune a ses
 * tests : elle n'ecrit pas deux fois la meme histoire, elle n'ecrit pas non
 * plus un geste que le registre tient deja en direct, elle ne touche pas aux
 * lignes vivantes, et elle compte separement ce qui manque et ce qui n'a jamais
 * eu a etre repris. La table est en ajout seul : une ligne fausse ecrite ici ne
 * se corrige jamais.</p>
 */
@DataJpaTest
// La base par defaut du slice ignore le type jsonb que declare
// challenges.auto_challenge_parameters, et la table n'est alors pas creee.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ChallengeDecisionBackfillTest {

    private static final Instant DECIDED_AT = Instant.parse("2026-03-01T10:15:30Z");

    /** Apres {@code MANUAL_GRANT_FLOOR} : un octroi que la reprise doit voir. */
    private static final Instant GRANTED_AT = Instant.parse("2026-08-01T09:00:00Z");

    /** Avant {@code MANUAL_GRANT_FLOOR} : l'epoque ou V6 a pu poser la signature. */
    private static final Instant BEFORE_THE_MANUAL_GRANT_PATH = Instant.parse("2026-05-01T09:00:00Z");

    /** Une preuve texte reelle : un joueur ne peut jamais en laisser une vide. */
    private static final String PLAYER_PROOF = "J'ai fait le defi, voici comment.";

    @Autowired
    private EntityManager entityManager;

    private final ChallengeDecisionBackfill backfill = new ChallengeDecisionBackfill();

    @AfterEach
    void releaseTime() {
        TimeTravelContext.clear();
    }

    @Test
    void aPastValidationComesBackWithItsModeratorItsDateAndItsDetails() {
        AppUser moderator = user("sub-moderateur", "Moderatrice");
        AppUser player = user("sub-joueur", "Joueur");
        Challenge challenge = challenge("Photo equipe", 30);
        ChallengeSubmission submission = playerSubmission(challenge, player, Set.of(), "VALIDATED", 5);
        audit(submission, "sub-moderateur", "VALIDATED");

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.decisionsReprised()).isEqualTo(1);
        assertThat(outcome.withoutResolvableAuthor()).isZero();

        AdminActionEvent reprised = onlyEvent();
        assertThat(reprised.getAction()).isEqualTo(AdminActionType.CHALLENGE_VALIDATED);
        assertThat(reprised.isBackfilled()).isTrue();
        assertThat(reprised.getActor().getId()).isEqualTo(moderator.getId());
        assertThat(reprised.getTarget().getId()).isEqualTo(player.getId());
        // La date est celle de la decision, pas celle de la reprise : un
        // registre qui se lit chronologiquement placerait sinon toute
        // l'histoire au jour du deploiement.
        assertThat(reprised.getOccurredAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(DECIDED_AT);
        assertThat(reprised.getDetailsJson())
                .isEqualTo("{\"challengeTitle\":\"Photo equipe\",\"points\":30,\"bonusPoints\":5}");
    }

    /**
     * Un rejet ne porte pas de points bonus : {@code decideSubmission} n'en
     * ecrit que sur une validation, et une cle a zero se lirait comme une
     * decision d'y mettre zero.
     */
    @Test
    void aPastRejectionComesBackWithoutBonusPoints() {
        user("sub-moderateur", "Moderatrice");
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission = playerSubmission(challenge("Defi rate", 10), player, Set.of(), "REJECTED", 0);
        audit(submission, "sub-moderateur", "REJECTED");

        runBackfill();

        AdminActionEvent reprised = onlyEvent();
        assertThat(reprised.getAction()).isEqualTo(AdminActionType.CHALLENGE_REJECTED);
        assertThat(reprised.getDetailsJson())
                .isEqualTo("{\"challengeTitle\":\"Defi rate\",\"points\":10}");
    }

    /**
     * Un defi de groupe porte sa composition, l'auteur de la soumission
     * compris : sans elle, filtrer sur un des autres membres ne trouverait
     * rien.
     */
    @Test
    void aGroupDecisionCarriesItsComposition() {
        user("sub-moderateur", "Moderatrice");
        AppUser author = user("sub-auteur", "Auteur");
        AppUser zoe = user("sub-zoe", "Zoe");
        AppUser bea = user("sub-bea", "Bea");
        ChallengeSubmission submission =
                playerSubmission(challenge("Defi collectif", 20), author, Set.of(zoe, bea), "VALIDATED", 0);
        audit(submission, "sub-moderateur", "VALIDATED");

        runBackfill();

        assertThat(onlyEvent().getDetailsJson()).isEqualTo(
                "{\"challengeTitle\":\"Defi collectif\",\"points\":20,\"bonusPoints\":0,"
                        + "\"participantNames\":[\"Auteur\",\"Bea\",\"Zoe\"],"
                        + "\"participantIds\":[\"" + author.getId() + "\",\"" + bea.getId() + "\",\""
                        + zoe.getId() + "\"]}");
    }

    /**
     * Un moderateur parti ne hache plus vers rien : sa decision est reprise
     * sans auteur plutot qu'omise, et le decompte le dit pour que cela se voie
     * au deploiement.
     */
    @Test
    void aDecisionWhoseModeratorIsGoneComesBackWithoutAnAuthor() {
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission =
                playerSubmission(challenge("Defi ancien", 15), player, Set.of(), "VALIDATED", 0);
        audit(submission, "sub-moderateur-parti", "VALIDATED");

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.decisionsReprised()).isEqualTo(1);
        assertThat(outcome.withoutResolvableAuthor()).isEqualTo(1);

        AdminActionEvent reprised = onlyEvent();
        assertThat(reprised.getActor()).isNull();
        assertThat(reprised.getTarget().getId()).isEqualTo(player.getId());
        assertThat(reprised.isBackfilled()).isTrue();
    }

    /**
     * Le trou que cette etape ferme : un administrateur accorde un defi depuis
     * la fiche d'un joueur, {@code createApprovedManualSubmission} cree une
     * soumission deja validee, et ce chemin n'a jamais rien ecrit dans
     * {@code moderation_audit_events}. Le geste est pourtant exactement ce que
     * le registre existe pour retrouver.
     */
    @Test
    void aManualGrantComesBackAsAGrantWithoutAnAuthor() {
        AppUser player = user("sub-joueur", "Joueur");
        manualGrant(challenge("Accorde a la main", 12), player, "Preuve montree en vrai.", GRANTED_AT);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.grantsReprised()).isEqualTo(1);
        assertThat(outcome.decisionsReprised()).isZero();
        // L'auteur inconnu d'un octroi n'est pas un moderateur qu'on n'aurait
        // pas retrouve : ce chemin n'en a jamais enregistre. Le confondre avec
        // une perte ferait croire a un incident.
        assertThat(outcome.withoutResolvableAuthor()).isZero();
        assertThat(outcome.decidedBeforeTheModerationTrail()).isZero();

        AdminActionEvent reprised = onlyEvent();
        assertThat(reprised.getAction()).isEqualTo(AdminActionType.CHALLENGE_GRANTED);
        assertThat(reprised.isBackfilled()).isTrue();
        assertThat(reprised.getActor()).isNull();
        assertThat(reprised.getTarget().getId()).isEqualTo(player.getId());
        assertThat(reprised.getOccurredAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(GRANTED_AT);
        assertThat(reprised.getDetailsJson()).isEqualTo(
                "{\"challengeTitle\":\"Accorde a la main\",\"points\":12,\"comment\":\"Preuve montree en vrai.\"}");
    }

    /**
     * Rien n'oblige un administrateur a justifier son octroi : la cle est
     * absente plutot que vide, comme la ligne vivante l'ecrit.
     */
    @Test
    void aManualGrantWithoutAJustificationOmitsTheCommentKey() {
        AppUser player = user("sub-joueur", "Joueur");
        manualGrant(challenge("Accorde sans mot", 8), player, null, GRANTED_AT);

        runBackfill();

        assertThat(onlyEvent().getDetailsJson())
                .isEqualTo("{\"challengeTitle\":\"Accorde sans mot\",\"points\":8}");
    }

    /**
     * La signature d'un octroi manuel n'est exclusive qu'au-dessus d'un
     * plancher. La migration V6 a ajoute {@code proof_type NOT NULL DEFAULT
     * 'TEXT'} et {@code proof_data NOT NULL DEFAULT ''} : toute soumission qui
     * la precede porte la meme signature sans etre un octroi. Aucune ligne
     * n'est ecrite sur ce doute - la table est en ajout seul.
     */
    @Test
    void aSubmissionOlderThanTheManualGrantPathIsNotTakenForAGrant() {
        AppUser player = user("sub-joueur", "Joueur");
        legacySubmissionWithTheGrantSignature(
                challenge("Defi d'avant", 12), player, BEFORE_THE_MANUAL_GRANT_PATH);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.grantsReprised()).isZero();
        assertThat(events()).isEmpty();
        // Elle n'est pas non plus rangee hors registre : c'etait bien une
        // soumission jugee, dont la trace n'a jamais existe.
        assertThat(outcome.decidedBeforeTheModerationTrail()).isEqualTo(1);
        assertThat(outcome.outsideTheRegister().total()).isZero();
    }

    /**
     * Le plancher est un instant, pas un jour : celui ou
     * {@code createApprovedManualSubmission} est arrive dans le code (commit
     * {@code 0db33554}, 2026-07-19 03:10:32 +0200, soit 2026-07-19T01:10:32Z).
     *
     * <p>Les deux dates sont ecrites en clair et non lues depuis la constante :
     * ce test doit tomber si le plancher redescend a minuit, car les soixante-dix
     * minutes qui separent minuit de l'arrivee du code ne sont couvertes par
     * aucune preuve tiree du depot - seulement par une hypothese sur l'ordre des
     * deploiements.</p>
     */
    @Test
    void theManualGrantFloorIsTheInstantTheCodeShippedNotTheDay() {
        AppUser player = user("sub-joueur", "Joueur");
        legacySubmissionWithTheGrantSignature(challenge("Une seconde trop tot", 12), player,
                Instant.parse("2026-07-19T01:10:31Z"));
        manualGrant(challenge("Pile a l'heure", 12), player, "Vu en vrai.",
                Instant.parse("2026-07-19T01:10:32Z"));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.grantsReprised()).isEqualTo(1);
        assertThat(onlyEvent().getAction()).isEqualTo(AdminActionType.CHALLENGE_GRANTED);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isEqualTo(1);
    }

    /**
     * Un defi automatique n'a pas d'administrateur : le systeme l'attribue. Le
     * reprendre serait l'erreur exacte que serait journaliser une recompense de
     * PvP - et le compter comme manquant ferait croire a un trou.
     */
    @Test
    void anAutomaticChallengeIsNotAnAdministrationGesture() {
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission =
                playerSubmission(challenge("Defi automatique", 5), player, Set.of(), "VALIDATED", 0);
        submission.setProofType("AUTO");
        submission.setProofData("");

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(events()).isEmpty();
        assertThat(outcome.outsideTheRegister().automaticAwards()).isEqualTo(1);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isZero();
        assertThat(outcome.decidedWithoutTrailAfterItExisted()).isZero();
    }

    /**
     * {@code promoteConfirmedGroupSubmission} valide une soumission de groupe
     * confirmee quand le defi est en mode AUTO. Il n'y a la non plus aucun
     * administrateur.
     */
    @Test
    void anAutomaticGroupValidationIsNotAnAdministrationGesture() {
        AppUser author = user("sub-auteur", "Auteur");
        AppUser bea = user("sub-bea", "Bea");
        Challenge challenge = challenge("Defi de groupe automatique", 9);
        challenge.setValidationMode("AUTO");
        playerSubmission(challenge, author, Set.of(bea), "VALIDATED", 0);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(events()).isEmpty();
        assertThat(outcome.outsideTheRegister().automaticGroupValidations()).isEqualTo(1);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isZero();
    }

    /**
     * Une soumission <em>solo</em> sur un defi en mode AUTO n'est pas une
     * validation automatique. {@code promoteConfirmedGroupSubmission} ne
     * s'applique qu'a une soumission de groupe confirmee ; sans participant,
     * {@code submitChallenge} part toujours en PENDING, quel que soit le mode du
     * defi. Une telle validation a donc demande un moderateur, et la ranger
     * hors registre deplacerait un vrai geste d'administration du compte des
     * pertes vers celui de ce qui n'a jamais eu lieu.
     */
    @Test
    void aSoloValidationOnAnAutomaticChallengeIsStillAnAdministrationGesture() {
        AppUser player = user("sub-joueur", "Joueur");
        Challenge challenge = challenge("Defi automatique, en solo", 9);
        challenge.setValidationMode("AUTO");
        playerSubmission(challenge, player, Set.of(), "VALIDATED", 0);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(events()).isEmpty();
        assertThat(outcome.outsideTheRegister().automaticGroupValidations()).isZero();
        assertThat(outcome.outsideTheRegister().total()).isZero();
        assertThat(outcome.decidedBeforeTheModerationTrail()).isEqualTo(1);
    }

    /**
     * Un joueur qui annule sa soumission de groupe, ou qui refuse une
     * invitation, laisse une ligne REJECTED que personne n'a jugee. La
     * reprendre inventerait un geste d'administration qui n'a jamais eu lieu.
     */
    @Test
    void aGroupSubmissionEndedByAPlayerIsNotAnAdministrationGesture() {
        AppUser author = user("sub-auteur", "Auteur");
        AppUser bea = user("sub-bea", "Bea");
        ChallengeSubmission cancelled =
                playerSubmission(challenge("Groupe annule", 9), author, Set.of(bea), "REJECTED", 0);
        cancelled.setModerationComment(ChallengeService.GROUP_SUBMISSION_CANCELLED_BY_INITIATOR);
        ChallengeSubmission declined =
                playerSubmission(challenge("Groupe refuse", 9), author, Set.of(bea), "REJECTED", 0);
        declined.setModerationComment(ChallengeService.GROUP_INVITE_DECLINED_BY_MEMBER);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(events()).isEmpty();
        assertThat(outcome.outsideTheRegister().endedByAPlayer()).isEqualTo(2);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isZero();
        assertThat(outcome.decidedWithoutTrailAfterItExisted()).isZero();
    }

    /**
     * La migration V70 a rejete en SQL les participations concurrentes a un
     * meme defi, sans toucher a {@code validated_at}. Aucun chemin de code ne
     * laisse un rejet garder sa date de validation : c'est son empreinte, et ce
     * n'etait pas un geste d'administration.
     */
    @Test
    void aRejectionLeftByTheDeduplicationMigrationIsNotAnAdministrationGesture() {
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission =
                playerSubmission(challenge("Doublon", 9), player, Set.of(), "REJECTED", 0);
        submission.setValidatedAt(DECIDED_AT);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(events()).isEmpty();
        assertThat(outcome.outsideTheRegister().rejectedByTheDeduplicationMigration()).isEqualTo(1);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isZero();
    }

    /**
     * Un geste d'administration anterieur a {@code moderation_audit_events} est
     * irreductible : l'information n'a jamais ete ecrite. Il est compte a part,
     * et sous son vrai nom - c'est une phrase exacte, pas une approximation.
     */
    @Test
    void anAdministrationGestureDecidedBeforeTheTrailExistedIsCountedOnItsOwn() {
        user("sub-moderateur", "Moderatrice");
        AppUser player = user("sub-joueur", "Joueur");
        // La trace existe depuis DECIDED_AT, portee par une autre soumission.
        ChallengeSubmission traced =
                playerSubmission(challenge("Defi trace", 10), player, Set.of(), "VALIDATED", 0);
        audit(traced, "sub-moderateur", "VALIDATED");
        // Celle-ci a ete creee avant, et n'a pas de trace.
        playerSubmissionCreatedAt(
                challenge("Defi d'avant la trace", 10), player, "VALIDATED", DECIDED_AT.minus(10, ChronoUnit.DAYS));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.decisionsReprised()).isEqualTo(1);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isEqualTo(1);
        assertThat(outcome.decidedWithoutTrailAfterItExisted()).isZero();
        assertThat(outcome.outsideTheRegister().total()).isZero();
    }

    /**
     * Un geste sans trace alors que la trace existait deja n'est pas
     * irreductible : c'est une anomalie, et c'est le seul nombre sur lequel un
     * operateur peut agir. Il ne doit pas se noyer dans le precedent.
     */
    @Test
    void anAdministrationGestureWithoutATrailAfterItExistedIsCountedApart() {
        user("sub-moderateur", "Moderatrice");
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission traced =
                playerSubmission(challenge("Defi trace", 10), player, Set.of(), "VALIDATED", 0);
        audit(traced, "sub-moderateur", "VALIDATED");
        playerSubmissionCreatedAt(
                challenge("Defi sans trace", 10), player, "VALIDATED", DECIDED_AT.plus(10, ChronoUnit.DAYS));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.decidedWithoutTrailAfterItExisted()).isEqualTo(1);
        assertThat(outcome.decidedBeforeTheModerationTrail()).isZero();
    }

    /**
     * Une soumission jugee deux fois donne deux lignes. Deux decisions sont
     * deux gestes : les fondre en une effacerait qu'un moderateur en a
     * contredit un autre, et la table est en ajout seul - on ne les separerait
     * jamais apres coup.
     */
    @Test
    void aSubmissionJudgedTwiceComesBackAsTwoRows() {
        AppUser first = user("sub-premier", "Premier");
        AppUser second = user("sub-second", "Second");
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission =
                playerSubmission(challenge("Defi rejuge", 25), player, Set.of(), "VALIDATED", 3);
        Instant firstDecision = DECIDED_AT.minus(2, ChronoUnit.DAYS);
        auditAt(submission, "sub-premier", "REJECTED", firstDecision);
        auditAt(submission, "sub-second", "VALIDATED", DECIDED_AT);

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.decisionsReprised()).isEqualTo(2);
        List<AdminActionEvent> reprised = events();
        assertThat(reprised).hasSize(2);

        AdminActionEvent overruled = reprised.getFirst();
        assertThat(overruled.getAction()).isEqualTo(AdminActionType.CHALLENGE_REJECTED);
        assertThat(overruled.getActor().getId()).isEqualTo(first.getId());
        assertThat(overruled.getOccurredAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(firstDecision);

        AdminActionEvent last = reprised.get(1);
        assertThat(last.getAction()).isEqualTo(AdminActionType.CHALLENGE_VALIDATED);
        assertThat(last.getActor().getId()).isEqualTo(second.getId());
        assertThat(last.getOccurredAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(DECIDED_AT);
    }

    /**
     * Flyway n'execute une migration qu'une fois par base, mais la classe doit
     * rester juste si on l'appelle deux fois : une table en ajout seul ne se
     * deduplique pas apres coup.
     */
    @Test
    void runningItTwiceDoesNotWriteHistoryTwice() {
        user("sub-moderateur", "Moderatrice");
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission =
                playerSubmission(challenge("Photo equipe", 30), player, Set.of(), "VALIDATED", 5);
        audit(submission, "sub-moderateur", "VALIDATED");
        manualGrant(challenge("Accorde a la main", 12), player, "Vu en vrai.", GRANTED_AT);

        ChallengeDecisionBackfill.Outcome first = runBackfill();
        ChallengeDecisionBackfill.Outcome second = runBackfill();

        assertThat(first.decisionsReprised()).isEqualTo(1);
        assertThat(first.grantsReprised()).isEqualTo(1);
        assertThat(second.decisionsReprised()).isZero();
        assertThat(second.grantsReprised()).isZero();
        assertThat(second.alreadyReprised()).isEqualTo(2);
        assertThat(events()).hasSize(2);
    }

    /**
     * Le doublon que rien n'arretait, et qu'on ne pourrait pas enlever.
     *
     * <p>Un octroi pose apres l'arrivee du registre a deja sa ligne vivante,
     * ecrite par {@code createApprovedManualSubmission} lui-meme. Cette ligne
     * porte un identifiant aleatoire : la recherche par identifiant derive ne
     * la trouve jamais.</p>
     *
     * <p>Le cas est monte sur l'angle mort exact du plancher : la ligne vivante
     * de cet octroi <em>est</em> la plus ancienne du registre, et elle a ete
     * ecrite quelques millisecondes apres la date de validation que la reprise
     * utiliserait. Le plancher seul le laisserait passer.</p>
     */
    @Test
    void aGrantTheLiveRegisterAlreadyHoldsIsNotWrittenTwice() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-joueur", "Joueur");
        manualGrant(challenge("Accorde a la main", 12), player, "Vu en vrai.", GRANTED_AT);
        AdminActionEvent live = liveEventAt(
                admin, player, AdminActionType.CHALLENGE_GRANTED,
                "{\"challengeTitle\":\"Accorde a la main\",\"points\":12,\"comment\":\"Vu en vrai.\"}",
                GRANTED_AT.plusMillis(3));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.grantsReprised()).isZero();
        assertThat(outcome.alreadyInTheLiveRegister()).isEqualTo(1);
        AdminActionEvent still = onlyEvent();
        assertThat(still.getId()).isEqualTo(live.getId());
        assertThat(still.isBackfilled()).isFalse();
    }

    /** La meme garde, sur une decision de moderation. */
    @Test
    void aDecisionTheLiveRegisterAlreadyHoldsIsNotWrittenTwice() {
        AppUser moderator = user("sub-moderateur", "Moderatrice");
        AppUser player = user("sub-joueur", "Joueur");
        ChallengeSubmission submission =
                playerSubmission(challenge("Photo equipe", 30), player, Set.of(), "VALIDATED", 5);
        audit(submission, "sub-moderateur", "VALIDATED");
        AdminActionEvent live = liveEventAt(
                moderator, player, AdminActionType.CHALLENGE_VALIDATED,
                "{\"challengeTitle\":\"Photo equipe\",\"points\":30,\"bonusPoints\":5}",
                DECIDED_AT.plusMillis(3));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.decisionsReprised()).isZero();
        assertThat(outcome.alreadyInTheLiveRegister()).isEqualTo(1);
        assertThat(onlyEvent().getId()).isEqualTo(live.getId());
    }

    /**
     * Le plancher fait le gros du travail : un geste bien anterieur a la
     * premiere ligne vivante du registre est repris, et le sondage ne trouve
     * rien autour de lui.
     */
    @Test
    void aGestureOlderThanTheRegisterIsStillReprised() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-joueur", "Joueur");
        manualGrant(challenge("Accorde a la main", 12), player, "Vu en vrai.", GRANTED_AT);
        liveEventAt(admin, player, AdminActionType.TOKENS_GRANTED, "{\"delta\":50}",
                GRANTED_AT.plus(20, ChronoUnit.DAYS));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.grantsReprised()).isEqualTo(1);
        assertThat(outcome.alreadyInTheLiveRegister()).isZero();
        assertThat(events()).hasSize(2);
    }

    /**
     * Le revers du plancher, et il ne porte pas le meme nom que le sondage.
     *
     * <p>Un geste posterieur a l'ouverture du registre, dont le sondage ne
     * trouve aucune ligne vivante, n'est pas repris : impossible de prouver
     * qu'aucun doublon n'existe. Mais ce n'est pas un doublon evite - c'est un
     * geste que personne ne tient, et peut-etre perdu. Le compter sous
     * {@code alreadyInTheLiveRegister} le ferait lire comme "tout va bien, le
     * registre l'a deja".</p>
     */
    @Test
    void aGestureAfterTheRegisterOpenedWithoutALiveRowIsCountedAsAPossibleLoss() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-joueur", "Joueur");
        manualGrant(challenge("Accorde a la main", 12), player, "Vu en vrai.", GRANTED_AT);
        // Le registre a ouvert vingt jours avant l'octroi, mais sur un tout
        // autre geste : rien, autour de l'octroi, ne lui correspond.
        liveEventAt(admin, player, AdminActionType.TOKENS_GRANTED, "{\"delta\":50}",
                GRANTED_AT.minus(20, ChronoUnit.DAYS));

        ChallengeDecisionBackfill.Outcome outcome = runBackfill();

        assertThat(outcome.grantsReprised()).isZero();
        assertThat(outcome.alreadyInTheLiveRegister()).isZero();
        assertThat(outcome.afterTheRegisterOpenedWithoutALiveRow()).isEqualTo(1);
        assertThat(onlyEvent().getAction()).isEqualTo(AdminActionType.TOKENS_GRANTED);
    }

    /**
     * La reprise n'ecrit que des lignes {@code backfilled = true} et ne lit
     * rien qu'elle puisse ecraser : une base qui porte deja des lignes vivantes
     * doit en ressortir intacte.
     */
    @Test
    void itLeavesLiveRowsAlone() {
        AppUser admin = user("sub-admin", "Admin");
        AppUser player = user("sub-joueur", "Joueur");
        AdminActionEvent live = liveEventAt(admin, player, AdminActionType.TOKENS_GRANTED,
                "{\"delta\":50,\"balance\":120}", DECIDED_AT.plus(30, ChronoUnit.DAYS));

        ChallengeSubmission submission =
                playerSubmission(challenge("Photo equipe", 30), player, Set.of(), "VALIDATED", 5);
        audit(submission, "sub-admin", "VALIDATED");

        Instant liveOccurredAt = live.getOccurredAt();
        runBackfill();

        List<AdminActionEvent> untouched = events().stream().filter(event -> !event.isBackfilled()).toList();
        assertThat(untouched).hasSize(1);
        AdminActionEvent still = untouched.getFirst();
        assertThat(still.getId()).isEqualTo(live.getId());
        assertThat(still.getAction()).isEqualTo(AdminActionType.TOKENS_GRANTED);
        assertThat(still.getActor().getId()).isEqualTo(admin.getId());
        assertThat(still.getTarget().getId()).isEqualTo(player.getId());
        assertThat(still.getDetailsJson()).isEqualTo("{\"delta\":50,\"balance\":120}");
        assertThat(still.getOccurredAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(liveOccurredAt.truncatedTo(ChronoUnit.MILLIS));
        assertThat(events()).hasSize(2);
    }

    /**
     * L'idempotence tient a des cles naturelles, faute de colonne ou ranger la
     * ligne d'origine. Deux sources differentes doivent donner deux
     * identifiants differents, la meme source toujours le meme - et les deux
     * familles ne doivent pas se rencontrer : un octroi et une decision qui
     * partageraient un identifiant source se recouvriraient.
     */
    @Test
    void theReprisedRowIdsAreDerivedFromTheirSource() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(ChallengeDecisionBackfill.decisionRowIdFor(first))
                .isEqualTo(ChallengeDecisionBackfill.decisionRowIdFor(first))
                .isNotEqualTo(ChallengeDecisionBackfill.decisionRowIdFor(second));
        assertThat(ChallengeDecisionBackfill.grantRowIdFor(first))
                .isEqualTo(ChallengeDecisionBackfill.grantRowIdFor(first))
                .isNotEqualTo(ChallengeDecisionBackfill.grantRowIdFor(second));
        assertThat(ChallengeDecisionBackfill.grantRowIdFor(first))
                .isNotEqualTo(ChallengeDecisionBackfill.decisionRowIdFor(first));
    }

    private ChallengeDecisionBackfill.Outcome runBackfill() {
        entityManager.flush();
        AtomicReference<ChallengeDecisionBackfill.Outcome> outcome = new AtomicReference<>();
        entityManager.unwrap(Session.class).doWork(connection -> outcome.set(backfill.run(connection)));
        entityManager.clear();
        return outcome.get();
    }

    private List<AdminActionEvent> events() {
        return entityManager
                .createQuery("select event from AdminActionEvent event", AdminActionEvent.class)
                .getResultList()
                .stream()
                .sorted(Comparator.comparing(AdminActionEvent::getOccurredAt))
                .toList();
    }

    private AdminActionEvent onlyEvent() {
        List<AdminActionEvent> events = events();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private Challenge challenge(String title, int points) {
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setPointsReward(points);
        challenge.setValidationMode("ADMIN");
        entityManager.persist(challenge);
        return challenge;
    }

    private AppUser user(String subject, String displayName) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.test");
        user.setDisplayName(displayName);
        entityManager.persist(user);
        return user;
    }

    /**
     * Une soumission de joueur : sa preuve texte n'est jamais vide, parce que
     * {@code proofDataForSubmission} refuse une preuve texte sans commentaire.
     * C'est exactement ce qui rend la signature d'un octroi exclusive.
     */
    private ChallengeSubmission playerSubmission(
            Challenge challenge, AppUser author, Set<AppUser> participants, String status, int bonusPoints) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(participants);
        submission.setStatus(status);
        submission.setProofType("TEXT");
        submission.setProofData(PLAYER_PROOF);
        submission.setBonusPoints(bonusPoints);
        if ("VALIDATED".equals(status)) {
            submission.setValidatedAt(DECIDED_AT);
        }
        entityManager.persist(submission);
        return submission;
    }

    private ChallengeSubmission playerSubmissionCreatedAt(
            Challenge challenge, AppUser author, String status, Instant createdAt) {
        TimeTravelContext.setForcedTime(createdAt);
        try {
            return playerSubmission(challenge, author, Set.of(), status, 0);
        } finally {
            TimeTravelContext.clear();
        }
    }

    /**
     * Un octroi manuel tel que {@code createApprovedManualSubmission} l'ecrit :
     * deja VALIDATED, preuve TEXT vide, aucun participant, et le commentaire de
     * l'administrateur pour seule justification.
     */
    private ChallengeSubmission manualGrant(Challenge challenge, AppUser player, String comment, Instant when) {
        TimeTravelContext.setForcedTime(when);
        try {
            ChallengeSubmission submission = new ChallengeSubmission();
            submission.setChallenge(challenge);
            submission.setUser(player);
            submission.setParticipants(Set.of());
            submission.setStatus("VALIDATED");
            submission.setProofType("TEXT");
            submission.setProofData("");
            submission.setComment(comment);
            submission.setBonusPoints(0);
            submission.setValidatedAt(when);
            entityManager.persist(submission);
            return submission;
        } finally {
            TimeTravelContext.clear();
        }
    }

    /**
     * Une soumission de joueur telle que la migration V6 l'a laissee : elle
     * porte la signature d'un octroi sans en etre un, parce que
     * {@code proof_data} lui a ete ajoute avec un defaut vide.
     */
    private ChallengeSubmission legacySubmissionWithTheGrantSignature(
            Challenge challenge, AppUser player, Instant when) {
        return manualGrant(challenge, player, null, when);
    }

    /**
     * Une ligne vivante a une date choisie. Elle passe par le constructeur a
     * instant explicite : le constructeur vivant lit l'horloge murale et ne
     * suit pas {@code TimeTravelContext}, pour que la date d'une ligne du
     * registre ne soit pas choisie par celui qu'elle nomme.
     */
    private AdminActionEvent liveEventAt(
            AppUser actor, AppUser target, AdminActionType action, String details, Instant when) {
        AdminActionEvent event = new AdminActionEvent(when, actor, target, action, details, false);
        entityManager.persist(event);
        return event;
    }

    private void audit(ChallengeSubmission submission, String moderatorSubject, String status) {
        auditAt(submission, moderatorSubject, status, DECIDED_AT);
    }

    /**
     * Une trace de moderation datee. La date est posee par une mise a jour
     * native apres l'ecriture, et non en forcant l'horloge : {@code
     * ModerationAuditEvent} horodate a l'horloge murale, exactement pour que la
     * date d'une trace ne soit pas choisie par celui qu'elle nomme. Fabriquer
     * un passe est le travail d'un montage de test, et une mise a jour native
     * le dit ; forcer l'horloge le deguiserait en comportement du code.
     */
    private void auditAt(ChallengeSubmission submission, String moderatorSubject, String status, Instant when) {
        UUID trailId = UUID.randomUUID();
        entityManager.persist(new ModerationAuditEvent(
                trailId,
                submission.getId(),
                ModerationAuditService.hashSubject(moderatorSubject),
                "DECISION",
                "SUCCESS",
                "{\"status\":\"" + status + "\"}"));
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update moderation_audit_events set created_at = :when where id = :id")
                .setParameter("when", when)
                .setParameter("id", trailId)
                .executeUpdate();
    }
}
