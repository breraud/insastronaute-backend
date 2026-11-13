package fr.insastronaute.webapi.common.adminaudit.backfill;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La reprise des gestes d'administration sur les defis, anterieurs au registre.
 *
 * <p>Les tokens et les cartes n'ont laisse aucune trace : pour eux l'histoire
 * commence au deploiement. Les defis, si. Deux gestes s'y retrouvent :</p>
 * <ul>
 *   <li>une <strong>decision de moderation</strong> - {@code decideSubmission}
 *   ecrit une ligne {@code DECISION}/{@code SUCCESS} dans
 *   {@code moderation_audit_events}, qui porte le moderateur, la date et le
 *   sens de la decision ;</li>
 *   <li>un <strong>octroi manuel</strong> - {@code createApprovedManualSubmission}
 *   cree une soumission deja VALIDATED depuis la fiche d'un joueur. Ce chemin
 *   n'a jamais rien ecrit dans {@code moderation_audit_events} : l'octroi n'a
 *   donc pas d'auteur recuperable, mais il a bien eu lieu, et c'est exactement
 *   ce que le registre existe pour retrouver.</li>
 * </ul>
 *
 * <p>Classe ordinaire, sans Spring et sans JPA : la migration Flyway qui
 * l'appelle recoit une {@link Connection} et rien d'autre, et surtout les
 * tests n'executent jamais de migration - la suite tourne sur H2 avec
 * {@code spring.flyway.enabled=false}. Mettre la logique ici est le seul moyen
 * de la tester avant qu'elle n'ecrive, une fois, dans une table en ajout
 * seul.</p>
 *
 * <h2>Ce que la classe garantit, et ce qu'elle ne garantit pas</h2>
 * <ul>
 *   <li><strong>Elle ne se duplique pas elle-meme.</strong> L'identifiant de
 *   chaque ligne reprise est <em>derive</em> de la ligne source - l'evenement
 *   de moderation pour une decision ({@link #decisionRowIdFor}), la soumission
 *   pour un octroi ({@link #grantRowIdFor}). Une seconde execution recalcule
 *   les memes identifiants, les retrouve, et n'ecrit rien.</li>
 *   <li><strong>Elle ne duplique pas non plus un geste que le registre tient
 *   deja en direct</strong> - voir {@link #liveRegisterVerdict}. C'est une
 *   garantie distincte de la precedente, et elle ne s'en deduit pas : une ligne
 *   vivante porte un identifiant aleatoire, la recherche par identifiant derive
 *   ne la trouve donc jamais.</li>
 *   <li><strong>Elle ne touche pas aux lignes vivantes.</strong> Sur
 *   {@code admin_action_events} elle ne fait que lire et inserer. Aucun UPDATE,
 *   aucun DELETE, et toutes ses lignes portent {@code backfilled = true}.</li>
 *   <li><strong>Ce qu'elle ne garantit pas :</strong> les details d'une ligne
 *   reprise sont reconstitues depuis l'etat <em>actuel</em> de la base. Les
 *   points du defi, les points bonus et la composition du groupe sont ceux
 *   d'aujourd'hui ; aucune trace historique n'existe pour faire mieux. Sur une
 *   soumission jugee deux fois, la decision intermediaire porte donc des
 *   details qui decrivent l'etat final. Elle est tout de meme reprise : deux
 *   decisions sont deux gestes, et les fondre en une effacerait qu'un
 *   moderateur en a contredit un autre.</li>
 * </ul>
 */
public final class ChallengeDecisionBackfill {

    /**
     * Ce qui distingue une ligne reprise d'une autre, avant hachage. Les deux
     * prefixes different parce que les deux gestes different : sans cela, une
     * decision et un octroi portant le meme identifiant source deriveraient le
     * meme identifiant de ligne.
     */
    private static final String DECISION_ROW_ID_NAMESPACE = "admin_action_events:backfill:challenge-decision:";
    private static final String GRANT_ROW_ID_NAMESPACE = "admin_action_events:backfill:challenge-grant:";

    private static final String STATUS_VALIDATED = "VALIDATED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String PROOF_TYPE_TEXT = "TEXT";
    private static final String PROOF_TYPE_AUTO = "AUTO";
    private static final String VALIDATION_MODE_AUTO = "AUTO";

    /**
     * Le plancher au-dessous duquel la signature d'un octroi manuel n'est plus
     * exclusive.
     *
     * <p>La signature est {@code status = VALIDATED}, {@code proof_type = TEXT}
     * et {@code proof_data} vide. Aucun joueur ne peut la produire :
     * {@code ChallengeService.proofDataForSubmission} refuse une preuve texte
     * au commentaire vide, et une preuve MEDIA n'arrive jamais en
     * {@code proof_type = TEXT}. Une seule autre chose l'a produite, et une
     * seule fois : la migration V6, qui a remplace {@code proof_text} par
     * {@code proof_type VARCHAR NOT NULL DEFAULT 'TEXT'} et
     * {@code proof_data TEXT NOT NULL DEFAULT ''}. Toute soumission anterieure
     * a cette migration porte donc la signature sans etre un octroi.</p>
     *
     * <p>Le plancher est l'instant exact ou
     * {@code createApprovedManualSubmission} est arrive dans le code : commit
     * {@code 0db33554}, 2026-07-19 03:10:32 +0200, soit
     * {@code 2026-07-19T01:10:32Z}. Voici ce qu'il garantit, et rien de
     * plus.</p>
     *
     * <p><strong>Il n'ecarte aucun octroi reel.</strong> Aucun octroi ne peut
     * preceder le code qui le cree. C'est un fait sur le depot, verifiable
     * sans rien savoir d'un deploiement, et c'est la seule direction dans
     * laquelle le plancher pourrait detruire de l'information.</p>
     *
     * <p><strong>Il ecarte les lignes heritees de V6, sauf sur une base
     * precise.</strong> Une ligne heritee n'existe que si
     * {@code challenge_submissions} portait deja des lignes quand V6 s'est
     * appliquee ; sa date de creation est donc anterieure a l'application de
     * V6 sur <em>cette</em> base. Deux cas se rangent seuls : sur une base
     * deployee en continu, V6 s'est appliquee le 2026-05-07, deux mois et demi
     * sous le plancher ; sur une base creee apres V6, le
     * {@code ADD COLUMN ... DEFAULT} a reecrit une table vide et il n'existe
     * aucune ligne heritee. Reste un cas que le plancher ne couvre pas : une
     * base figee entre le 2026-05-07 et le 2026-07-19, portant deja des
     * soumissions, et migree apres que le chemin d'octroi eut ete deploye. Une
     * ligne heritee y passerait le plancher et serait reprise comme un octroi.
     * Cela ne se deduit pas du depot ; le plancher est une borne exacte sur le
     * code, pas une preuve sur l'historique de deploiement de chaque base.</p>
     *
     * <p><strong>Les dates ne sont pas hors de portee d'un operateur.</strong>
     * {@code created_at} est pose par {@code TimeTravelContext.instantNow()}
     * ({@code ChallengeSubmission.@PrePersist}) et {@code validated_at} de
     * meme, et l'en-tete {@code X-Time-Travel-Date} laisse un compte STAFF ou
     * ADMIN les antidater ({@code TimeTravelFilter}). Un octroi pose sous un
     * en-tete antidate tombe sous le plancher et n'est pas repris. Il ne peut
     * pas faire ecrire une ligne fausse dans l'autre sens : une soumission de
     * joueur antidatee porte toujours une preuve non vide et ne prend donc pas
     * la signature d'octroi. La perte, elle, se compte - la ligne rejoint les
     * gestes sans trace, en {@code WARN}.</p>
     */
    static final Instant MANUAL_GRANT_FLOOR = Instant.parse("2026-07-19T01:10:32Z");

    /**
     * La fenetre du sondage qui cherche, autour d'un geste, la ligne vivante
     * que le registre en aurait deja ecrite.
     *
     * <p>Les deux ecritures se font dans la meme transaction, a quelques
     * millisecondes l'une de l'autre : {@code decideSubmission} enregistre la
     * trace de moderation puis appelle le journal, et
     * {@code createApprovedManualSubmission} horodate la validation puis
     * appelle le journal. Une minute est quatre ordres de grandeur de marge, et
     * reste tres en dessous de l'ecart qui separe un geste historique du
     * premier jour du registre.</p>
     */
    private static final Duration LIVE_ROW_WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Ce que la reprise a fait, pour que l'appelant puisse le journaliser.
     *
     * <p>Les compteurs de ce qui n'a pas ete repris ne disent pas la meme
     * chose, et les melanger ferait croire a un trou la ou il n'y en a pas -
     * ou, pire, nommerait une perte comme une securite. Trois groupes :</p>
     * <ul>
     *   <li><strong>ce qui n'a jamais ete un geste d'administration</strong>
     *   ({@code outsideTheRegister}) : exclu par construction, rien a
     *   chercher ;</li>
     *   <li><strong>un geste dont le registre tient deja la ligne</strong>
     *   ({@code alreadyReprised}, {@code alreadyInTheLiveRegister}) : rien a
     *   ecrire, et rien de perdu ;</li>
     *   <li><strong>un geste que la reprise n'a pas ecrit et que personne ne
     *   tient</strong> ({@code decidedBeforeTheModerationTrail},
     *   {@code decidedWithoutTrailAfterItExisted},
     *   {@code afterTheRegisterOpenedWithoutALiveRow},
     *   {@code decisionsThatCouldNotBeRead}) : c'est la, et seulement la, qu'un
     *   operateur a quelque chose a regarder.</li>
     * </ul>
     *
     * @param decisionsReprised                 lignes ecrites depuis une trace de moderation
     * @param grantsReprised                    lignes ecrites depuis un octroi manuel, toutes
     *                                          sans auteur
     * @param withoutResolvableAuthor           decisions reprises sans auteur, faute d'avoir pu
     *                                          retrouver le moderateur derriere son hachage
     * @param alreadyReprised                   gestes qu'une execution precedente avait deja repris
     * @param alreadyInTheLiveRegister          gestes dont le sondage a trouve la ligne vivante,
     *                                          donc volontairement non repris : un doublon evite,
     *                                          aucune perte
     * @param afterTheRegisterOpenedWithoutALiveRow gestes situes au niveau ou au-dessus de la
     *                                          premiere ligne vivante du registre, sans qu'aucune
     *                                          ligne vivante n'ait ete trouvee autour d'eux. Non
     *                                          repris par prudence, mais c'est une perte
     *                                          possible - pas une securite
     * @param decisionsThatCouldNotBeRead       traces de moderation inexploitables : soumission
     *                                          disparue, ou sens de la decision illisible
     * @param decidedBeforeTheModerationTrail   gestes d'administration decides avant que
     *                                          {@code moderation_audit_events} n'existe :
     *                                          irreductible, l'information n'a jamais ete ecrite
     * @param decidedWithoutTrailAfterItExisted gestes d'administration sans trace alors que la
     *                                          trace existait deja : anormal, et le seul nombre
     *                                          qui appelle une enquete
     * @param outsideTheRegister                ce qui ne fut jamais un geste d'administration
     */
    public record Outcome(
            int decisionsReprised,
            int grantsReprised,
            int withoutResolvableAuthor,
            int alreadyReprised,
            int alreadyInTheLiveRegister,
            int afterTheRegisterOpenedWithoutALiveRow,
            int decisionsThatCouldNotBeRead,
            int decidedBeforeTheModerationTrail,
            int decidedWithoutTrailAfterItExisted,
            OutsideTheRegister outsideTheRegister
    ) {
        @Override
        public String toString() {
            return decisionsReprised + " decision(s) et " + grantsReprised + " octroi(s) manuel(s) repris, dont "
                    + withoutResolvableAuthor + " decision(s) sans auteur retrouvable ; "
                    + alreadyReprised + " deja repris ; "
                    + alreadyInTheLiveRegister + " deja tenu(s) par le registre vivant ; "
                    + afterTheRegisterOpenedWithoutALiveRow
                    + " non repris et sans ligne vivante trouvee, apres l'ouverture du registre ; "
                    + decidedBeforeTheModerationTrail + " geste(s) d'administration sans trace, anterieur(s) a "
                    + "moderation_audit_events ; " + decidedWithoutTrailAfterItExisted
                    + " geste(s) d'administration sans trace alors qu'elle existait ; "
                    + outsideTheRegister.total() + " soumission(s) hors registre (" + outsideTheRegister + ")";
        }
    }

    /**
     * Ce qui ne fut jamais un geste d'administration, et n'a donc rien a faire
     * ni dans le registre ni dans le compte de ce qui manque.
     *
     * @param automaticAwards                     defis automatiques attribues par le systeme
     *                                            ({@code proof_type = 'AUTO'})
     * @param automaticGroupValidations           soumissions <em>de groupe</em> validees par le
     *                                            systeme parce que le defi est en mode AUTO. Les
     *                                            participants sont exiges : une soumission solo
     *                                            sur un defi AUTO part quand meme en PENDING et a
     *                                            donc demande un moderateur
     * @param rejectedByTheDeduplicationMigration soumissions rejetees par la migration V70, qui a
     *                                            deduplique les participations concurrentes a un
     *                                            meme defi. Le compte est legerement trop large :
     *                                            V70 a aussi rejete des soumissions deja validees
     *                                            par un administrateur, et cette validation-la fut
     *                                            un vrai geste
     * @param endedByAPlayer                      soumissions de groupe annulees par leur
     *                                            initiateur, ou dont un membre a refuse
     *                                            l'invitation
     */
    public record OutsideTheRegister(
            int automaticAwards,
            int automaticGroupValidations,
            int endedByAPlayer,
            int rejectedByTheDeduplicationMigration
    ) {
        public int total() {
            return automaticAwards + automaticGroupValidations + endedByAPlayer
                    + rejectedByTheDeduplicationMigration;
        }

        @Override
        public String toString() {
            return automaticAwards + " defi(s) automatique(s), " + automaticGroupValidations
                    + " validation(s) automatique(s) de groupe, " + endedByAPlayer
                    + " arret(s) par un joueur, " + rejectedByTheDeduplicationMigration
                    + " rejet(s) par la migration V70";
        }
    }

    /**
     * L'identifiant de la ligne reprise pour une decision de moderation donnee.
     *
     * <p>Il est derive de l'evenement de moderation, et non de la soumission :
     * une soumission peut avoir ete jugee deux fois, et deux jugements sont
     * deux gestes. Deriver de la soumission les fondrait en une seule ligne et
     * effacerait qu'un moderateur en a contredit un autre.</p>
     *
     * <p>La table n'a pas de colonne ou ranger l'identifiant de la ligne
     * d'origine, et lui en ajouter une pour une reprise unique serait payer une
     * colonne a vie ; deriver l'identifiant revient au meme sans changer le
     * schema.</p>
     */
    public static UUID decisionRowIdFor(UUID moderationEventId) {
        return UUID.nameUUIDFromBytes(
                (DECISION_ROW_ID_NAMESPACE + moderationEventId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * L'identifiant de la ligne reprise pour un octroi manuel donne. Derive de
     * la soumission : l'octroi <em>est</em> la creation de cette soumission, il
     * ne peut pas y en avoir deux.
     */
    public static UUID grantRowIdFor(UUID submissionId) {
        return UUID.nameUUIDFromBytes((GRANT_ROW_ID_NAMESPACE + submissionId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reprend les gestes d'administration passes sur les defis.
     *
     * <p>La connexion n'est ni validee ni fermee : elle appartient a Flyway,
     * qui execute la migration dans une transaction. Une erreur ici annule donc
     * toute la reprise plutot que d'en laisser la moitie.</p>
     */
    public Outcome run(Connection connection) throws SQLException {
        Map<String, UUID> actorByModeratorHash = new HashMap<>();
        Map<UUID, String> displayNameByUserId = new HashMap<>();
        readUsers(connection, actorByModeratorHash, displayNameByUserId);

        Map<UUID, DecidedSubmission> submissions = readDecidedSubmissions(connection);
        Map<UUID, List<UUID>> participantsBySubmission = readParticipants(connection);
        List<ModerationDecision> decisions = readModerationDecisions(connection);
        Instant liveRegisterFloor = earliestLiveRegisterRow(connection);
        Instant moderationTrailFloor = earliestModerationTrail(connection);

        Set<UUID> submissionsWithATrail = new HashSet<>();
        decisions.forEach(decision -> submissionsWithATrail.add(decision.submissionId()));

        Counters counters = new Counters();

        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT 1 FROM admin_action_events WHERE id = ?");
             PreparedStatement liveProbe = connection.prepareStatement("""
                     SELECT 1 FROM admin_action_events
                     WHERE backfilled = FALSE
                       AND action = ?
                       AND target_user_id = ?
                       AND occurred_at >= ?
                       AND occurred_at <= ?
                     """);
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO admin_action_events
                         (id, occurred_at, actor_user_id, target_user_id, action, details_json, backfilled)
                     VALUES (?, ?, ?, ?, ?, ?, TRUE)
                     """)) {

            for (ModerationDecision decision : decisions) {
                DecidedSubmission submission = submissions.get(decision.submissionId());
                AdminActionType action = actionFor(decision.status());
                if (submission == null || action == null) {
                    // Une trace dont la soumission a disparu, ou dont le sens
                    // n'est pas lisible, ne dit pas assez pour ecrire une ligne
                    // juste dans une table en ajout seul. On la compte et on la
                    // laisse voir, plutot que d'interrompre un deploiement ou
                    // d'ecrire au jugement.
                    counters.decisionsThatCouldNotBeRead++;
                    continue;
                }

                UUID actorId = actorByModeratorHash.get(decision.moderatorHash());
                boolean written = write(
                        exists, liveProbe, insert, liveRegisterFloor, counters,
                        decisionRowIdFor(decision.moderationEventId()),
                        decision.decidedAt(),
                        actorId,
                        submission.submitterId(),
                        action,
                        decisionDetails(submission, decision.status(), participantsBySubmission, displayNameByUserId));
                if (written) {
                    counters.decisionsReprised++;
                    if (actorId == null) {
                        counters.withoutResolvableAuthor++;
                    }
                }
            }

            for (DecidedSubmission submission : submissions.values()) {
                if (submissionsWithATrail.contains(submission.submissionId())) {
                    continue;
                }
                if (isManualGrant(submission)) {
                    boolean written = write(
                            exists, liveProbe, insert, liveRegisterFloor, counters,
                            grantRowIdFor(submission.submissionId()),
                            submission.grantedAt(),
                            // Jamais d'auteur : ce chemin n'a jamais rien ecrit
                            // dans moderation_audit_events, l'administrateur qui
                            // a accorde le defi n'est nulle part. C'est le cas
                            // que V114 existe pour permettre - une ligne sans
                            // auteur reste une information, son absence n'en est
                            // pas une.
                            null,
                            submission.submitterId(),
                            AdminActionType.CHALLENGE_GRANTED,
                            grantDetails(submission, participantsBySubmission, displayNameByUserId));
                    if (written) {
                        counters.grantsReprised++;
                    }
                    continue;
                }
                countUnreprised(submission, participantsBySubmission, moderationTrailFloor, counters);
            }
        }

        return counters.toOutcome();
    }

    /**
     * La signature d'un octroi manuel, et pourquoi elle est exclusive.
     *
     * <p>{@code createApprovedManualSubmission} pose {@code proof_type = TEXT}
     * et {@code proof_data = ""} sur une soumission deja VALIDATED. Un joueur
     * ne peut pas produire cette combinaison :
     * {@code ChallengeService.proofDataForSubmission} leve
     * {@code BadRequestException("La preuve texte est requise.")} des que le
     * commentaire d'une preuve TEXT est vide, et une preuve MEDIA ne passe pas
     * en {@code proof_type = TEXT}. Le seul autre producteur de la combinaison
     * est la migration V6, ecartee par {@link #MANUAL_GRANT_FLOOR}.</p>
     */
    private static boolean isManualGrant(DecidedSubmission submission) {
        return STATUS_VALIDATED.equals(submission.status())
                && PROOF_TYPE_TEXT.equals(submission.proofType())
                && (submission.proofData() == null || submission.proofData().isBlank())
                && submission.createdAt() != null
                && !submission.createdAt().isBefore(MANUAL_GRANT_FLOOR);
    }

    /**
     * Le classement de ce qui n'a pas ete repris.
     *
     * <p>L'ordre des tests compte : la signature d'octroi a deja ete essayee
     * par l'appelant, et chaque marqueur positif passe avant le cas par defaut.
     * Ce qui tombe dans le cas par defaut est un geste d'administration dont la
     * trace manque - le seul nombre qui appelle une enquete.</p>
     */
    private static void countUnreprised(
            DecidedSubmission submission,
            Map<UUID, List<UUID>> participantsBySubmission,
            Instant moderationTrailFloor,
            Counters counters) {
        if (PROOF_TYPE_AUTO.equals(submission.proofType())) {
            // Le systeme attribue un defi automatique : aucun administrateur
            // n'est intervenu. Le reprendre serait l'erreur exacte que serait
            // journaliser une recompense de PvP.
            counters.automaticAwards++;
            return;
        }
        if (STATUS_VALIDATED.equals(submission.status())
                && VALIDATION_MODE_AUTO.equals(submission.challengeValidationMode())
                && hasParticipants(submission, participantsBySubmission)) {
            // promoteConfirmedGroupSubmission : le systeme valide un groupe
            // confirme parce que le defi est en mode AUTO. Pas d'administrateur
            // non plus.
            //
            // Les participants sont exiges, et ils ne sont pas decoratifs :
            // promoteConfirmedGroupSubmission ne s'applique qu'a une soumission
            // de groupe (ChallengeService, la confirmation d'invitation). Une
            // soumission solo, elle, part toujours en PENDING - le mode AUTO du
            // defi n'y change rien (submitChallenge : participants vides =>
            // STATUS_PENDING). Elle a donc necessairement demande un
            // moderateur. Sans cette condition, une validation solo sans trace
            // sur un defi AUTO passait pour une validation automatique, et un
            // vrai geste d'administration quittait le compte des pertes pour
            // celui de ce qui n'a jamais eu lieu.
            counters.automaticGroupValidations++;
            return;
        }
        if (STATUS_REJECTED.equals(submission.status())
                && (ChallengeService.GROUP_SUBMISSION_CANCELLED_BY_INITIATOR.equals(submission.moderationComment())
                || ChallengeService.GROUP_INVITE_DECLINED_BY_MEMBER.equals(submission.moderationComment()))) {
            // Un joueur a annule sa propre soumission de groupe, ou a refuse
            // une invitation. Le statut est REJECTED, mais personne ne l'a
            // juge : reprendre ce cas inventerait un geste d'administration qui
            // n'a jamais eu lieu.
            counters.endedByAPlayer++;
            return;
        }
        if (STATUS_REJECTED.equals(submission.status()) && submission.validatedAt() != null) {
            // Aucun chemin de code ne laisse un rejet garder sa date de
            // validation : decideSubmission, l'annulation et le refus la
            // remettent tous a null. La migration V70, elle, a bascule le
            // statut en SQL sans y toucher. C'est donc son empreinte.
            //
            // Et c'est une empreinte ambigue, dans un sens qu'il faut dire :
            // la boucle de V70 selectionne
            // status IN ('WAITING_CONFIRMATION', 'PENDING', 'VALIDATED'), donc
            // elle a aussi rejete des soumissions qu'un administrateur avait
            // deja validees. Cette validation-la fut un vrai geste
            // d'administration, et elle est comptee ici comme n'en ayant jamais
            // ete un. C'est la seule des quatre imprecisions de classement qui
            // pointe hors du compte des pertes plutot que dedans. Elle reste
            // dans le lot anterieur a V72, deja tenu pour irreductible : la
            // trace de cette validation n'existe pas davantage que les autres,
            // donc rien de reparable n'est cache derriere ce compteur - seul le
            // nom sous lequel la ligne est rangee est trop affirmatif.
            counters.rejectedByTheDeduplicationMigration++;
            return;
        }
        if (moderationTrailFloor == null
                || submission.createdAt() == null
                || submission.createdAt().isBefore(moderationTrailFloor)) {
            // Decide avant que moderation_audit_events n'existe : irreductible,
            // l'information n'a jamais ete ecrite.
            counters.decidedBeforeTheModerationTrail++;
            return;
        }
        counters.decidedWithoutTrailAfterItExisted++;
    }

    private static boolean hasParticipants(
            DecidedSubmission submission, Map<UUID, List<UUID>> participantsBySubmission) {
        List<UUID> participants = participantsBySubmission.get(submission.submissionId());
        return participants != null && !participants.isEmpty();
    }

    /**
     * Ecrit une ligne reprise, sauf si le registre tient deja ce geste.
     *
     * @return {@code true} si la ligne a ete ecrite
     */
    private boolean write(
            PreparedStatement exists,
            PreparedStatement liveProbe,
            PreparedStatement insert,
            Instant liveRegisterFloor,
            Counters counters,
            UUID rowId,
            Instant occurredAt,
            UUID actorId,
            UUID targetId,
            AdminActionType action,
            String details
    ) throws SQLException {
        if (rowExists(exists, rowId)) {
            counters.alreadyReprised++;
            return false;
        }
        LiveRegisterVerdict verdict =
                liveRegisterVerdict(liveProbe, liveRegisterFloor, occurredAt, targetId, action);
        if (verdict == LiveRegisterVerdict.MATCHED_A_LIVE_ROW) {
            counters.alreadyInTheLiveRegister++;
            return false;
        }
        if (verdict == LiveRegisterVerdict.AFTER_THE_REGISTER_OPENED_WITHOUT_A_LIVE_ROW) {
            counters.afterTheRegisterOpenedWithoutALiveRow++;
            return false;
        }

        insert.setObject(1, rowId);
        insert.setObject(2, occurredAt.atOffset(ZoneOffset.UTC));
        if (actorId == null) {
            // setObject(index, null) laisse au pilote le soin de deviner le
            // type ; setNull le lui dit.
            insert.setNull(3, Types.OTHER);
        } else {
            insert.setObject(3, actorId);
        }
        insert.setObject(4, targetId);
        insert.setString(5, action.name());
        insert.setString(6, details);
        insert.executeUpdate();
        return true;
    }

    /**
     * Le registre tient-il deja ce geste en direct, et sinon, pourquoi la
     * reprise passe-t-elle quand meme son tour ?
     *
     * <p>C'est la garde qui manquait, et le risque qu'elle ferme est
     * irreparable : un geste pose <em>apres</em> l'arrivee du registre a deja
     * sa ligne vivante, ecrite par le chemin de production lui-meme. Cette
     * ligne porte un identifiant aleatoire, que la recherche par identifiant
     * derive ne trouve jamais - rien n'empechait donc d'en ecrire une seconde,
     * reprise, pour le meme geste. La table est en ajout seul : un doublon ne
     * s'enleve pas.</p>
     *
     * <p>Deux regles, parce qu'aucune des deux ne suffit seule :</p>
     * <ol>
     *   <li><strong>Le sondage.</strong> Il cherche, autour du geste et pour le
     *   meme joueur, une ligne vivante du meme type. C'est lui qui rattrape le
     *   geste qui a <em>ouvert</em> le registre : sa ligne vivante est la plus
     *   ancienne de la table, et l'horodatage que la reprise lui donnerait -
     *   date de la trace de moderation, ou date de validation - precede de
     *   quelques millisecondes celui de la ligne du journal, ecrite juste apres
     *   dans la meme transaction. Le plancher seul le laisserait passer.</li>
     *   <li><strong>Le plancher.</strong> Au niveau ou au-dessus de la plus
     *   ancienne ligne vivante, le registre ecrivait deja : on ne reprend rien,
     *   meme si le sondage n'a rien trouve, parce qu'on ne peut pas prouver
     *   qu'aucune ligne vivante ne tient ce geste. En dessous, le registre
     *   n'ecrivait pas encore : aucun geste n'a pu y laisser de ligne, et la
     *   reprise ecrit. Sur une base ou le registre n'a encore rien ecrit il n'y
     *   a pas de plancher, et tout est repris.</li>
     * </ol>
     *
     * <p>Les deux regles se trompent dans le sens sur : elles font passer un
     * tour, elles n'ecrivent jamais. Mais <strong>elles ne se trompent pas de
     * la meme facon</strong>, et c'est pourquoi elles rendent deux verdicts
     * distincts. Le sondage qui trouve une ligne vivante constate un doublon
     * evite. Le plancher qui retient un geste sans que le sondage ait rien
     * trouve, lui, <em>perd peut-etre ce geste</em> : s'il a eu lieu apres
     * l'ouverture du registre sans y laisser de ligne, c'est qu'il n'a jamais
     * ete journalise, et la reprise ne le rattrapera pas. Ce cas est inerte en
     * production - le registre y est vide quand la reprise s'execute, il n'y a
     * donc pas de plancher - mais il est atteignable sur une base de
     * developpement ou le registre tourne depuis plusieurs migrations. Il a son
     * compteur, {@code afterTheRegisterOpenedWithoutALiveRow}, et sa phrase en
     * {@code WARN}.</p>
     *
     * <p><strong>La granularite du sondage.</strong> Il compare
     * {@code (action, target_user_id, occurred_at a une minute pres)}, pas les
     * details. Deux gestes du meme type sur le meme joueur a moins d'une minute
     * l'un de l'autre sont donc indiscernables : un geste historique tombant
     * dans la minute qui entoure la premiere ligne vivante du registre serait
     * pris pour elle et non repris. Comparer {@code details_json} les
     * departagerait, au prix d'une egalite sur du JSON reconstitue depuis
     * l'etat actuel - qui ne coincide justement pas toujours avec ce que la
     * ligne vivante porte. La fenetre a ete gardee etroite plutot que la
     * comparaison rendue exacte.</p>
     *
     * <p><strong>Le sondage suppose que les deux tables suivent la meme
     * horloge.</strong> La date historique vient de
     * {@code moderation_audit_events.created_at}, la date vivante de
     * {@code admin_action_events.occurred_at} : les "quelques millisecondes"
     * ci-dessus n'existent que si rien ne les separe. Les deux tables sont
     * desormais datees a l'horloge murale, donc rien ne les separe - mais cela
     * n'a pas toujours ete vrai. Sur une base ou une decision a ete prise sous
     * {@code X-Time-Travel-Date} avant que ces deux horloges ne soient
     * alignees, sa trace de moderation porte la date voyagee et sa ligne
     * vivante la date reelle ; le sondage ne les rapproche pas, le plancher ne
     * retient pas une date de 2020, et la reprise ecrit un doublon antidate
     * dans une table qui n'accepte aucune suppression. Aucune base de
     * production n'est concernee - le registre y est vide quand la reprise
     * s'execute - mais une base de developpement peut l'etre, et le doublon y
     * serait indelebile.</p>
     */
    private static LiveRegisterVerdict liveRegisterVerdict(
            PreparedStatement liveProbe,
            Instant liveRegisterFloor,
            Instant occurredAt,
            UUID targetId,
            AdminActionType action
    ) throws SQLException {
        if (liveRegisterFloor == null) {
            return LiveRegisterVerdict.NOT_HELD;
        }
        liveProbe.setString(1, action.name());
        liveProbe.setObject(2, targetId);
        liveProbe.setObject(3, occurredAt.minus(LIVE_ROW_WINDOW).atOffset(ZoneOffset.UTC));
        liveProbe.setObject(4, occurredAt.plus(LIVE_ROW_WINDOW).atOffset(ZoneOffset.UTC));
        try (ResultSet found = liveProbe.executeQuery()) {
            if (found.next()) {
                return LiveRegisterVerdict.MATCHED_A_LIVE_ROW;
            }
        }
        if (!occurredAt.isBefore(liveRegisterFloor)) {
            return LiveRegisterVerdict.AFTER_THE_REGISTER_OPENED_WITHOUT_A_LIVE_ROW;
        }
        return LiveRegisterVerdict.NOT_HELD;
    }

    /**
     * Ce que le registre vivant dit d'un geste, et ce que la reprise en fait.
     *
     * <p>Deux des trois cas se soldent par la meme decision - ne pas ecrire -
     * mais ils ne rapportent pas la meme chose, et les confondre nommerait une
     * perte comme une securite.</p>
     */
    private enum LiveRegisterVerdict {
        /** Le registre ne tient pas ce geste : la reprise l'ecrit. */
        NOT_HELD,
        /** Une ligne vivante correspond : la reprise passe son tour, sans perte. */
        MATCHED_A_LIVE_ROW,
        /**
         * Le geste est au niveau ou au-dessus de la premiere ligne vivante,
         * et le sondage n'a rien trouve autour de lui. La reprise passe son
         * tour parce qu'elle ne peut pas prouver qu'aucune ligne vivante ne le
         * tient - mais ce tour saute peut-etre un geste que personne n'a
         * journalise. C'est une perte, elle se compte comme telle.
         */
        AFTER_THE_REGISTER_OPENED_WITHOUT_A_LIVE_ROW
    }

    /** Le sens d'une decision, ou {@code null} si la trace ne le dit pas. */
    private static AdminActionType actionFor(String status) {
        if (STATUS_VALIDATED.equals(status)) {
            return AdminActionType.CHALLENGE_VALIDATED;
        }
        if (STATUS_REJECTED.equals(status)) {
            return AdminActionType.CHALLENGE_REJECTED;
        }
        return null;
    }

    /**
     * Les details d'une decision, dans la forme exacte qu'ecrit une ligne
     * vivante de {@code decideSubmission}.
     *
     * <p>Rien n'est invente : chaque cle vient d'une donnee que la base porte
     * reellement. {@code bonusPoints} n'apparait que sur une validation, comme
     * en direct, et la composition du groupe est absente plutot que vide sur
     * une soumission solo - une cle presente et vide se lirait comme une
     * information.</p>
     *
     * <p>Une approximation, et elle est inevitable : ces details decrivent
     * l'etat <em>actuel</em> de la soumission et de son defi. Si les points d'un
     * defi ont bouge depuis la decision, ou si la soumission a ete jugee deux
     * fois, la ligne reprise porte la valeur d'aujourd'hui. La base ne garde
     * aucun historique du bareme ni des points bonus.</p>
     */
    private String decisionDetails(
            DecidedSubmission submission,
            String status,
            Map<UUID, List<UUID>> participantsBySubmission,
            Map<UUID, String> displayNameByUserId
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("challengeTitle", submission.challengeTitle());
        details.put("points", submission.challengePoints());
        if (STATUS_VALIDATED.equals(status)) {
            details.put("bonusPoints", submission.bonusPoints());
        }
        putGroupComposition(details, submission, participantsBySubmission, displayNameByUserId);
        return serialize(details);
    }

    /**
     * Les details d'un octroi manuel, dans la forme exacte qu'ecrit
     * {@code createApprovedManualSubmission}.
     *
     * <p>Le commentaire est la seule chose qui dise pourquoi ce defi a ete
     * accorde sans preuve ; rien n'oblige a en laisser un, d'ou la cle absente
     * plutot que vide quand il manque - exactement comme en direct.</p>
     */
    private String grantDetails(
            DecidedSubmission submission,
            Map<UUID, List<UUID>> participantsBySubmission,
            Map<UUID, String> displayNameByUserId
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("challengeTitle", submission.challengeTitle());
        details.put("points", submission.challengePoints());
        if (submission.comment() != null && !submission.comment().isBlank()) {
            details.put("comment", submission.comment());
        }
        // Inerte aujourd'hui, comme en direct : le chemin d'octroi pose des
        // participants vides. La garde reste identique et devient active le
        // jour ou ce chemin accepte un groupe.
        putGroupComposition(details, submission, participantsBySubmission, displayNameByUserId);
        return serialize(details);
    }

    private static void putGroupComposition(
            Map<String, Object> details,
            DecidedSubmission submission,
            Map<UUID, List<UUID>> participantsBySubmission,
            Map<UUID, String> displayNameByUserId
    ) {
        List<UUID> participants = participantsBySubmission.get(submission.submissionId());
        if (participants == null || participants.isEmpty()) {
            return;
        }
        // Le soumettant en tete puis le reste trie par nom affiche, comme le
        // fait submissionMembers : la composition inclut l'auteur de la
        // soumission, une composition qui l'oublierait ne serait pas le groupe.
        List<UUID> members = new ArrayList<>();
        members.add(submission.submitterId());
        List<UUID> teammates = new ArrayList<>(participants);
        teammates.removeIf(participant -> participant.equals(submission.submitterId()));
        teammates.sort(Comparator.comparing(participant -> displayName(displayNameByUserId, participant)));
        members.addAll(teammates);

        details.put("participantNames", members.stream()
                .map(member -> displayName(displayNameByUserId, member))
                .toList());
        details.put("participantIds", members.stream().map(UUID::toString).toList());
    }

    private String serialize(Map<String, Object> details) {
        AdminActionJournal.rejectSensitiveDetailKeys(details);
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de serialiser les details d'un geste repris.", exception);
        }
    }

    private static String displayName(Map<UUID, String> displayNameByUserId, UUID userId) {
        String name = displayNameByUserId.get(userId);
        return name == null ? "" : name;
    }

    /**
     * L'existence est cherchee sur l'identifiant seul, sans filtrer sur
     * {@code backfilled} : dans le cas invraisemblable ou une ligne vivante
     * porterait deja cet identifiant, on passe son tour plutot que d'echouer
     * sur la cle primaire - et surtout on ne la touche pas.
     */
    private static boolean rowExists(PreparedStatement exists, UUID rowId) throws SQLException {
        exists.setObject(1, rowId);
        try (ResultSet found = exists.executeQuery()) {
            return found.next();
        }
    }

    /**
     * Les utilisateurs, indexes par le hachage de leur sujet Keycloak.
     *
     * <p>C'est la seule raison pour laquelle la reprise tourne en Java :
     * associer une decision passee a son moderateur demande un SHA-256 du
     * {@code keycloak_sub}, que Postgres ne sait faire qu'avec pgcrypto. Le
     * hachage lui-meme est celui de {@link ModerationAuditService}, reutilise
     * et non reecrit - deux implementations qui divergeraient d'un octet ne
     * retrouveraient plus aucun moderateur.</p>
     *
     * <p>Le nom d'affichage suit la meme regle que {@code ChallengeService} :
     * le nom quand il y en a un, l'adresse sinon.</p>
     */
    private void readUsers(
            Connection connection,
            Map<String, UUID> actorByModeratorHash,
            Map<UUID, String> displayNameByUserId
    ) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet users = statement.executeQuery("SELECT id, keycloak_sub, display_name, email FROM users")) {
            while (users.next()) {
                UUID id = UUID.fromString(users.getString("id"));
                String keycloakSub = users.getString("keycloak_sub");
                if (keycloakSub != null) {
                    actorByModeratorHash.put(ModerationAuditService.hashSubject(keycloakSub), id);
                }
                String displayName = users.getString("display_name");
                displayNameByUserId.put(id,
                        displayName != null && !displayName.isBlank() ? displayName : users.getString("email"));
            }
        }
    }

    /**
     * Les soumissions arrivees a un statut terminal, avec ce que leur defi
     * porte aujourd'hui.
     *
     * <p>Une seule requete, pas une par soumission : la reprise tourne au
     * deploiement, sur toute l'histoire, et un N+1 s'y paierait en minutes. La
     * carte garde l'ordre de la requete pour que deux executions ecrivent dans
     * le meme ordre.</p>
     */
    private Map<UUID, DecidedSubmission> readDecidedSubmissions(Connection connection) throws SQLException {
        Map<UUID, DecidedSubmission> submissions = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT submission.id AS submission_id,
                            submission.user_id AS submitter_id,
                            submission.status AS status,
                            submission.bonus_points AS bonus_points,
                            submission.proof_type AS proof_type,
                            submission.proof_data AS proof_data,
                            submission.comment AS submission_comment,
                            submission.moderation_comment AS moderation_comment,
                            submission.created_at AS created_at,
                            submission.validated_at AS validated_at,
                            challenge.title AS challenge_title,
                            challenge.points_reward AS points_reward,
                            challenge.validation_mode AS validation_mode
                     FROM challenge_submissions submission
                     JOIN challenges challenge ON challenge.id = submission.challenge_id
                     WHERE submission.status IN ('VALIDATED', 'REJECTED')
                     ORDER BY submission.id
                     """)) {
            while (rows.next()) {
                UUID submissionId = UUID.fromString(rows.getString("submission_id"));
                submissions.put(submissionId, new DecidedSubmission(
                        submissionId,
                        UUID.fromString(rows.getString("submitter_id")),
                        rows.getString("status"),
                        rows.getInt("bonus_points"),
                        rows.getString("proof_type"),
                        rows.getString("proof_data"),
                        rows.getString("submission_comment"),
                        rows.getString("moderation_comment"),
                        instant(rows, "created_at"),
                        instant(rows, "validated_at"),
                        rows.getString("challenge_title"),
                        rows.getInt("points_reward"),
                        rows.getString("validation_mode")
                ));
            }
        }
        return submissions;
    }

    /**
     * Toutes les decisions de moderation, et non la seule derniere de chaque
     * soumission.
     *
     * <p>Une soumission peut avoir ete jugee deux fois ; deux decisions sont
     * deux gestes, et n'en reprendre qu'un effacerait qu'un moderateur en a
     * contredit un autre. Chacune porte sa date, son moderateur et son sens,
     * lu dans les metadonnees que {@code decideSubmission} y ecrit.</p>
     */
    private List<ModerationDecision> readModerationDecisions(Connection connection) throws SQLException {
        List<ModerationDecision> decisions = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT id, submission_id, moderator_hash, created_at, metadata_json
                     FROM moderation_audit_events
                     WHERE event_type = 'DECISION' AND outcome = 'SUCCESS'
                     ORDER BY submission_id, created_at, id
                     """)) {
            while (rows.next()) {
                decisions.add(new ModerationDecision(
                        UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("submission_id")),
                        rows.getString("moderator_hash"),
                        instant(rows, "created_at"),
                        decisionStatus(rows.getString("metadata_json"))
                ));
            }
        }
        return decisions;
    }

    /** Le sens de la decision, tel que {@code decideSubmission} l'a ecrit. */
    private String decisionStatus(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(metadataJson).path("status").asText(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<UUID, List<UUID>> readParticipants(Connection connection) throws SQLException {
        Map<UUID, List<UUID>> participants = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT submission_id, user_id FROM challenge_submission_participants")) {
            while (rows.next()) {
                participants.computeIfAbsent(UUID.fromString(rows.getString("submission_id")), key -> new ArrayList<>())
                        .add(UUID.fromString(rows.getString("user_id")));
            }
        }
        return participants;
    }

    /**
     * Le premier jour du registre vivant, ou {@code null} s'il n'a encore rien
     * ecrit. Voir {@link #liveRegisterVerdict}.
     */
    private static Instant earliestLiveRegisterRow(Connection connection) throws SQLException {
        return firstInstant(connection,
                "SELECT MIN(occurred_at) FROM admin_action_events WHERE backfilled = FALSE");
    }

    /**
     * Le premier jour de {@code moderation_audit_events}, ou {@code null} si la
     * table est vide. Il separe ce qui n'a jamais pu avoir de trace de ce qui
     * aurait du en avoir une.
     */
    private static Instant earliestModerationTrail(Connection connection) throws SQLException {
        return firstInstant(connection, "SELECT MIN(created_at) FROM moderation_audit_events");
    }

    private static Instant firstInstant(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                return null;
            }
            OffsetDateTime value = rows.getObject(1, OffsetDateTime.class);
            return value == null ? null : value.toInstant();
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * Une soumission arrivee a un statut terminal.
     *
     * @param grantedAt la date que porte un octroi manuel : celle de la
     *                  validation, posee par
     *                  {@code createApprovedManualSubmission} au moment du
     *                  geste, et a defaut celle de la creation de la soumission
     *                  - le geste <em>est</em> cette creation
     */
    private record DecidedSubmission(
            UUID submissionId,
            UUID submitterId,
            String status,
            int bonusPoints,
            String proofType,
            String proofData,
            String comment,
            String moderationComment,
            Instant createdAt,
            Instant validatedAt,
            String challengeTitle,
            int challengePoints,
            String challengeValidationMode
    ) {
        Instant grantedAt() {
            return validatedAt != null ? validatedAt : createdAt;
        }
    }

    private record ModerationDecision(
            UUID moderationEventId,
            UUID submissionId,
            String moderatorHash,
            Instant decidedAt,
            String status
    ) {}

    /** Les compteurs pendant la reprise, figes en {@link Outcome} a la fin. */
    private static final class Counters {
        private int decisionsReprised;
        private int grantsReprised;
        private int withoutResolvableAuthor;
        private int alreadyReprised;
        private int alreadyInTheLiveRegister;
        private int afterTheRegisterOpenedWithoutALiveRow;
        private int decisionsThatCouldNotBeRead;
        private int decidedBeforeTheModerationTrail;
        private int decidedWithoutTrailAfterItExisted;
        private int automaticAwards;
        private int automaticGroupValidations;
        private int endedByAPlayer;
        private int rejectedByTheDeduplicationMigration;

        private Outcome toOutcome() {
            return new Outcome(
                    decisionsReprised,
                    grantsReprised,
                    withoutResolvableAuthor,
                    alreadyReprised,
                    alreadyInTheLiveRegister,
                    afterTheRegisterOpenedWithoutALiveRow,
                    decisionsThatCouldNotBeRead,
                    decidedBeforeTheModerationTrail,
                    decidedWithoutTrailAfterItExisted,
                    new OutsideTheRegister(
                            automaticAwards,
                            automaticGroupValidations,
                            endedByAPlayer,
                            rejectedByTheDeduplicationMigration));
        }
    }
}
