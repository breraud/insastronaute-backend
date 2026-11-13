package fr.insastronaute.webapi.challenge.auto.evaluation;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.award.AutoChallengeAwardService;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerEvent;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Le chemin chaud des défis automatiques : évaluer un joueur tout de suite, sur
 * les seuls défis que son geste peut avoir débloqués.
 *
 * <p>Le balayage horaire suffirait à tout attraper, mais une heure entre le
 * geste et sa récompense casse le lien entre les deux — c'est lui qui fait que
 * la popup arrive pendant qu'on regarde encore le pack qu'on vient d'ouvrir. Le
 * balayage reste indispensable pour ce que le chemin chaud ne peut
 * structurellement pas voir : un défi créé aujourd'hui sur l'historique d'hier,
 * un joueur dont le geste a eu lieu pendant que l'écouteur était à terre, une
 * phrase dont aucun événement métier n'annonce la condition.
 */
@Service
public class AutoChallengeTriggerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoChallengeTriggerListener.class);

    private final ActiveAutoChallengesCache activeChallenges;
    private final AutoChallengeTypeRegistry registry;
    private final AutoChallengeAwardService awardService;

    public AutoChallengeTriggerListener(
            ActiveAutoChallengesCache activeChallenges,
            AutoChallengeTypeRegistry registry,
            AutoChallengeAwardService awardService
    ) {
        this.activeChallenges = activeChallenges;
        this.registry = registry;
        this.awardService = awardService;
    }

    /**
     * Après commit, jamais avant : la phrase interroge les lignes que l'appelant
     * vient d'écrire, donc l'évaluer dans sa transaction lirait un état que la
     * base n'a pas encore accepté.
     *
     * <p>S'exécute sur le fil de l'appelant. L'application n'active pas @Async, et
     * le faire pour ce seul usage tirerait un exécuteur et la propagation du
     * contexte de sécurité ; une poignée de requêtes indexées coûte moins que
     * cette décision.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(AutoChallengeTriggerEvent event) {
        try {
            evaluatePlayer(event.userId(), event.trigger());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Évaluation des défis automatiques en échec pour le joueur '{}' sur {}.",
                    event.userId(), event.trigger(), exception
            );
        }
    }

    /**
     * Volontairement pas @Transactional : onTrigger appelle cette méthode sur le
     * même bean, donc le proxy serait contourné et l'annotation mentirait. Chaque
     * lecture se tient seule, et chaque attribution ouvre sa propre transaction.
     */
    public int evaluatePlayer(UUID userId, AutoChallengeTrigger trigger) {
        List<Challenge> candidates = activeChallenges.current().stream()
                .filter(challenge -> registry.listensTo(challenge.getAutoChallengeType(), trigger))
                .toList();

        int awarded = 0;
        for (Challenge challenge : candidates) {
            try {
                AutoChallengeProgress progress = registry.progress(
                        challenge.getAutoChallengeType(), challenge.getAutoChallengeParameters(), userId
                );
                // Un type qui ne sait pas répondre pour un joueur isolé n'est
                // attribué que par le balayage : c'est lui qui sait chercher.
                if (progress != null && progress.completed()) {
                    awarded += awardService.awardBatch(challenge.getId(), List.of(userId));
                }
            } catch (RuntimeException exception) {
                // Un défi mal configuré ne doit pas bloquer les autres.
                LOGGER.warn(
                        "Défi automatique '{}' inévaluable pour le joueur '{}'.",
                        challenge.getId(), userId, exception
                );
            }
        }
        return awarded;
    }
}
