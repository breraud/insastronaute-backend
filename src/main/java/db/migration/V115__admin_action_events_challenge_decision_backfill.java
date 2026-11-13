package db.migration;

import fr.insastronaute.webapi.common.adminaudit.backfill.ChallengeDecisionBackfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La reprise des gestes de defi passes dans le registre des actions
 * d'administration : les decisions de moderation, et les octrois manuels que
 * {@code createApprovedManualSubmission} n'a jamais traces.
 *
 * <p>Premiere migration Java du projet, et pour une raison precise : associer
 * une decision passee a son moderateur demande un SHA-256 du
 * {@code keycloak_sub} de chaque utilisateur, que Postgres ne sait faire
 * qu'avec pgcrypto. Le reste des migrations reste en SQL.</p>
 *
 * <p>Elle est volontairement vide de logique : tout est dans
 * {@link ChallengeDecisionBackfill}, une classe ordinaire. Les tests tournent
 * sur H2 avec {@code spring.flyway.enabled=false} et un schema regenere -
 * aucune migration ne s'y execute jamais, et ce qui vit ici ne serait donc
 * jamais teste. Cette classe se trouve sous {@code db.migration} parce que
 * c'est l'emplacement que Flyway scrute par defaut, le meme que celui des
 * migrations SQL.</p>
 *
 * <p>Flyway execute la migration dans une transaction : une erreur en cours de
 * reprise n'en laisse pas la moitie derriere elle.</p>
 */
public class V115__admin_action_events_challenge_decision_backfill extends BaseJavaMigration {

    private static final Logger log =
            LoggerFactory.getLogger(V115__admin_action_events_challenge_decision_backfill.class);

    @Override
    public void migrate(Context context) throws Exception {
        ChallengeDecisionBackfill.Outcome outcome =
                new ChallengeDecisionBackfill().run(context.getConnection());

        log.info("Registre des actions d'administration, reprise des gestes de defi : {}", outcome);

        // Ce qui n'est pas repris se lit en plusieurs nombres qui ne veulent pas
        // dire la meme chose. Les melanger ferait croire a un trou la ou il n'y
        // en a pas, et cacherait le vrai : un operateur doit pouvoir dire d'un
        // coup d'oeil ce qui manque de ce qui n'a jamais eu a etre repris.
        if (outcome.outsideTheRegister().total() > 0) {
            log.info("{} soumission(s) ne sont pas des gestes d'administration et sont exclues par construction, "
                            + "pas faute d'information : {}.",
                    outcome.outsideTheRegister().total(), outcome.outsideTheRegister());
        }
        if (outcome.decidedBeforeTheModerationTrail() > 0) {
            // Le seuil est MIN(created_at) de moderation_audit_events sur cette
            // base, pas une date en dur : nommer V72 et sa date serait faux
            // pour un geste pose entre l'application de V72 et la premiere
            // decision qu'elle a reellement tracee.
            log.warn("{} geste(s) d'administration n'ont aucune trace et ne sont pas repris : ils sont anterieurs a "
                            + "la plus ancienne trace de moderation_audit_events sur cette base. Avant cet instant, "
                            + "aucune decision de moderation n'a de trace ; l'information n'a jamais ete ecrite et "
                            + "rien ne la retrouvera.",
                    outcome.decidedBeforeTheModerationTrail());
        }
        // Distinct du precedent, et distinct d'alreadyInTheLiveRegister : ici le
        // sondage n'a trouve aucune ligne vivante, mais le geste est au niveau
        // ou au-dessus de la premiere ligne du registre. On ne l'ecrit pas -
        // impossible de prouver qu'il n'y a pas de doublon - et il est donc
        // peut-etre perdu. Inerte en production, ou le registre est vide quand
        // cette migration s'execute.
        if (outcome.afterTheRegisterOpenedWithoutALiveRow() > 0) {
            log.warn("{} geste(s) ne sont pas repris alors que le sondage n'a trouve aucune ligne vivante autour "
                            + "d'eux : ils sont posterieurs a l'ouverture du registre, qui journalise de lui-meme "
                            + "depuis cet instant. Un geste sans ligne vivante n'y a donc pas ete journalise, et la "
                            + "reprise ne le rattrape pas : ce n'est pas un doublon evite, c'est une perte possible. "
                            + "Ce nombre devrait etre nul.",
                    outcome.afterTheRegisterOpenedWithoutALiveRow());
        }
        if (outcome.decidedWithoutTrailAfterItExisted() > 0) {
            log.warn("{} geste(s) d'administration n'ont aucune trace alors que moderation_audit_events existait "
                            + "deja a leur date : ce nombre devrait etre nul, il merite une verification.",
                    outcome.decidedWithoutTrailAfterItExisted());
        }
        if (outcome.decisionsThatCouldNotBeRead() > 0) {
            log.warn("{} trace(s) de moderation n'ont pas pu etre lues et ne sont pas reprises : soumission "
                    + "disparue, ou sens de la decision absent des metadonnees.",
                    outcome.decisionsThatCouldNotBeRead());
        }
        // Une ligne sans auteur le restera : la table est en ajout seul.
        if (outcome.withoutResolvableAuthor() > 0) {
            log.warn("{} decision(s) reprise(s) sans auteur : le moderateur d'origine n'a plus de ligne users, "
                    + "son sujet Keycloak ne hache plus vers personne.", outcome.withoutResolvableAuthor());
        }
        // Les octrois manuels n'ont jamais eu d'auteur enregistrable : ce n'est
        // pas une perte, c'est ce que le chemin de production n'a jamais ecrit.
        if (outcome.grantsReprised() > 0) {
            log.info("{} octroi(s) manuel(s) repris sans auteur : createApprovedManualSubmission n'a jamais rien "
                    + "ecrit dans moderation_audit_events, l'administrateur qui a accorde le defi n'est nulle part.",
                    outcome.grantsReprised());
        }
    }
}
