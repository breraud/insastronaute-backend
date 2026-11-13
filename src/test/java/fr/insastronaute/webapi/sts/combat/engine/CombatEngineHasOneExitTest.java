package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reform this guards: six production lockups came out of three handlers each
 * building a combat state by hand and each forgetting a different part of the rules.
 * A fourth hand-built state would bring the family back, and no behavioural test
 * would catch it until a player was stuck again — so the shape is asserted directly.
 *
 * <p>Counting the constructions was not enough to say that. Two of them anywhere in the file
 * satisfied a count of two, so moving one back into {@code playCard} — which is exactly how the
 * defect returns — kept the tally at two and left this test green: the method name asserted
 * something its body never asked. What matters is not how many states the engine builds but where
 * it builds them, so the lines are located inside {@code transition(...)} rather than tallied.
 */
class CombatEngineHasOneExitTest {

    private static final Path ENGINE = Path.of(
            "src/main/java/fr/insastronaute/webapi/sts/combat/engine/CombatEngine.java");

    private static final String TRANSITION = "static CombatResult transition(";

    @Test
    void onlyTheTransitionBuildsACombatState() throws Exception {
        List<String> lines = Files.readAllLines(ENGINE);

        List<Integer> constructions = linesContaining(lines, "new CombatState(");
        int declaration = onlyLineContaining(lines, TRANSITION);
        int closing = closingLineOfMethodAt(lines, declaration);

        assertThat(constructions)
                .as("CombatEngine must build a state only inside transition(...), which runs "
                        + "from line %d to line %d; a hand-built state anywhere else is how each "
                        + "of six lockups began", declaration + 1, closing + 1)
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).isBetween(declaration, closing));

        assertThat(constructions)
                .as("transition(...) builds two states and no more: the finished one and the "
                        + "active one")
                .hasSize(2);
    }

    private static List<Integer> linesContaining(List<String> lines, String needle) {
        List<Integer> found = new ArrayList<>();
        for (int line = 0; line < lines.size(); line++) {
            if (lines.get(line).contains(needle)) {
                found.add(line);
            }
        }
        return found;
    }

    private static int onlyLineContaining(List<String> lines, String needle) {
        List<Integer> found = linesContaining(lines, needle);
        assertThat(found)
                .as("CombatEngine must declare exactly one %s", needle)
                .hasSize(1);
        return found.getFirst();
    }

    /**
     * Where the method declared on {@code declaration} ends, found by following its braces down to
     * the one that closes it. Nothing in this engine puts a brace inside a string or a comment,
     * and a method left unclosed fails here rather than silently widening the range this test
     * accepts.
     */
    private static int closingLineOfMethodAt(List<String> lines, int declaration) {
        int depth = 0;
        boolean opened = false;
        for (int line = declaration; line < lines.size(); line++) {
            for (char character : lines.get(line).toCharArray()) {
                if (character == '{') {
                    depth++;
                    opened = true;
                } else if (character == '}') {
                    depth--;
                }
            }
            if (opened && depth == 0) {
                return line;
            }
        }
        throw new AssertionError("transition(...) is never closed in " + ENGINE);
    }
}
