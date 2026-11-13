package fr.insastronaute.webapi.challenge.auto.evaluation;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeSavedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Rattrape ceux qui remplissent déjà une phrase qu'on vient d'écrire.
 *
 * <p>Les déclencheurs ne parlent que du présent : « avoir un ami » n'attrape que
 * les amitiés acceptées après coup. Sans ce rattrapage, une phrase publiée sur une
 * condition déjà remplie attendait le balayage horaire, et ce silence d'une heure
 * après la création ressemble à un défi cassé.</p>
 *
 * <p>Après le commit : le défi doit exister en base pour qu'on puisse chercher ses
 * candidats. Et hors de la transaction d'écriture, pour qu'un balayage long ne
 * tienne pas ouverte la transaction qui a enregistré le défi.</p>
 *
 * <p>Après le commit, donc l'enregistrement est acquis : une erreur ici ne doit pas
 * remonter. Elle remonterait jusqu'à l'appelant — qui verrait une erreur 500 sur une
 * écriture pourtant réussie —, et une modification en masse publie un événement par
 * défi : un seul rattrapage en échec ferait passer tout le lot pour perdu. Le
 * balayage horaire repassera.</p>
 */
@Service
public class AutoChallengeSavedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoChallengeSavedListener.class);

    private final AutoChallengeEvaluationService evaluationService;

    public AutoChallengeSavedListener(AutoChallengeEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAutoChallengeSaved(AutoChallengeSavedEvent event) {
        try {
            int awarded = evaluationService.evaluateOne(event.challengeId());
            if (awarded > 0) {
                LOGGER.info("Défi automatique {} : {} joueur(s) le remplissaient déjà", event.challengeId(), awarded);
            }
        } catch (RuntimeException | Error failure) {
            LOGGER.error("Rattrapage du défi automatique {} en échec ; le balayage horaire repassera.",
                    event.challengeId(), failure);
        }
    }
}
