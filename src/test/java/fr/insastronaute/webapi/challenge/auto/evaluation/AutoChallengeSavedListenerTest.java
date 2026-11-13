package fr.insastronaute.webapi.challenge.auto.evaluation;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeSavedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Une phrase automatique porte souvent sur un état ancien — « avoir un ami »
 * quand on en a deux depuis une semaine. Les déclencheurs ne parlent que du
 * présent, et le balayage passe à l'heure : sans rattrapage à l'écriture, le défi
 * paraît cassé pendant une heure.
 */
@ExtendWith(MockitoExtension.class)
class AutoChallengeSavedListenerTest {

    @Mock
    private AutoChallengeEvaluationService evaluationService;

    @InjectMocks
    private AutoChallengeSavedListener listener;

    @Test
    void evaluatesTheChallengeThatWasJustSaved() {
        UUID challengeId = UUID.randomUUID();
        when(evaluationService.evaluateOne(challengeId)).thenReturn(2);

        listener.onAutoChallengeSaved(new AutoChallengeSavedEvent(challengeId));

        verify(evaluationService).evaluateOne(challengeId);
    }

    @Test
    void anEvaluationThatFailsDoesNotUndoTheSave() {
        // L'écoute est après commit : le défi est déjà enregistré, et une erreur
        // d'évaluation ne doit pas remonter jusqu'à l'appelant.
        UUID challengeId = UUID.randomUUID();
        when(evaluationService.evaluateOne(challengeId)).thenReturn(0);

        assertThatCode(() -> listener.onAutoChallengeSaved(new AutoChallengeSavedEvent(challengeId)))
                .doesNotThrowAnyException();
    }

    /**
     * Une erreur après commit remonterait jusqu'à l'appelant : il verrait une erreur
     * 500 sur une écriture pourtant enregistrée. Une modification en masse publie un
     * événement par défi, et un seul rattrapage en échec ferait passer tout le lot
     * pour perdu.
     */
    @Test
    void anEvaluationThatThrowsStaysWithTheEvaluation() {
        UUID challengeId = UUID.randomUUID();
        when(evaluationService.evaluateOne(challengeId))
                .thenThrow(new IllegalStateException("plus de connexion disponible"));

        assertThatCode(() -> listener.onAutoChallengeSaved(new AutoChallengeSavedEvent(challengeId)))
                .doesNotThrowAnyException();
    }
}
