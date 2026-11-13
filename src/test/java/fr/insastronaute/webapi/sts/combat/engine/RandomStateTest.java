package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import fr.insastronaute.webapi.sts.combat.model.RandomState;
import org.junit.jupiter.api.Test;

class RandomStateTest {

    @Test
    void sameSeedProducesSameStep() {
        assertThat(new RandomState(42L).nextInt(100))
                .isEqualTo(new RandomState(42L).nextInt(100));
    }

    @Test
    void matchesReferenceVectorsForDifferentSeedsAndBounds() {
        assertStep(new RandomState(0L).nextInt(10), 5, -7046029254386353131L);
        assertStep(new RandomState(42L).nextInt(100), 13, -7046029254386353089L);
        assertStep(new RandomState(-1L).nextInt(3), 2, -7046029254386353132L);
        assertStep(new RandomState(Long.MAX_VALUE).nextInt(Integer.MAX_VALUE),
                47402322, 2177342782468422676L);
    }

    @Test
    void matchesReferenceVectorAcrossSuccessiveDraws() {
        RandomState.RandomStep first = new RandomState(0L).nextInt(10);
        assertStep(first, 5, -7046029254386353131L);

        RandomState.RandomStep second = first.next().nextInt(10);
        assertStep(second, 0, 4354685564936845354L);

        RandomState.RandomStep third = second.next().nextInt(10);
        assertStep(third, 9, -2691343689449507777L);

        RandomState.RandomStep fourth = third.next().nextInt(10);
        assertStep(fourth, 4, 8709371129873690708L);
    }

    @Test
    void repeatedDrawsStayWithinBoundAndAdvanceState() {
        RandomState state = new RandomState(42L);

        for (int draw = 0; draw < 100; draw++) {
            RandomState.RandomStep step = state.nextInt(3);

            assertThat(step.value()).isBetween(0, 2);
            assertThat(step.next()).isNotEqualTo(state);
            state = step.next();
        }
    }

    @Test
    void rejectsNonPositiveBounds() {
        RandomState state = new RandomState(42L);

        assertThatIllegalArgumentException().isThrownBy(() -> state.nextInt(0));
        assertThatIllegalArgumentException().isThrownBy(() -> state.nextInt(-1));
    }

    private static void assertStep(RandomState.RandomStep step, int value, long nextState) {
        assertThat(step)
                .isEqualTo(new RandomState.RandomStep(value, new RandomState(nextState)));
    }
}
