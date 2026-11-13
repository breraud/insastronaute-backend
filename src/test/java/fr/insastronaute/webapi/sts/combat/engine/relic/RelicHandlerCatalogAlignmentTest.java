package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * {@link RelicEngine} indexes handlers by {@link RelicHandler#id()} and looks them up using the
 * relic ids stored on a run's inventory, which come from the {@code STS/GameData/relics.json}
 * catalog (field {@code id}). A handler whose {@code id()} does not match a catalog id is
 * therefore silently unreachable: {@code handlers.get(relicId)} always returns {@code null} for it
 * and the relic never triggers in game.
 *
 * <p>This test scans every {@link RelicHandler} implementation on the classpath and asserts that
 * its {@code id()} is one of the catalog's known relic ids.
 *
 * <p><b>Known unresolved handlers.</b> Two handlers cannot be aligned without guessing an id that
 * isn't backed by the catalog, so they are tracked explicitly below instead of silently ignored:
 *
 * <ul>
 *   <li>{@code CardRelic} returns {@code "CardRelic"}, which has no counterpart in the catalog at
 *       all. The handler itself is an empty no-op stub, so there is no behavior to cross-check
 *       against a catalog entry either. ({@code ITIRelic} used to be the one catalog id with no
 *       handler; it has one now.)
 *   <li>{@code SpeedRelicHandler} returns {@code "SpeedRelic"}. The plausible catalog match,
 *       {@code SpeedCadenceRelic}, is already claimed by {@code SpeedCadenceRelicHandler} (a
 *       distinct, differently-behaved handler still pending an {@code onCardPlayed} hook). Reusing
 *       that id for {@code SpeedRelicHandler} would silently break {@code SpeedCadenceRelicHandler}
 *       instead.
 * </ul>
 *
 * If either of these is genuinely fixed, remove it from {@link #KNOWN_UNRESOLVED_HANDLER_CLASSES}
 * so this test keeps enforcing full alignment.
 */
class RelicHandlerCatalogAlignmentTest {

    private static final String HANDLERS_PACKAGE =
            "fr.insastronaute.webapi.sts.combat.engine.relic.handlers";
    /**
     * The ids CI checks against. {@code /STS/} is git-ignored — it is a developer's working copy
     * of data the server reads from object storage — so a test reading it from disk passes on a
     * machine that happens to hold the file and fails on a fresh checkout.
     */
    private static final String RELIC_IDS_FIXTURE = "/sts/relic-catalog-2026-08/relic-ids.json";

    /** The developer copy, when there is one: present locally, absent in CI. */
    private static final Path DEVELOPER_CATALOG_PATH = Path.of("STS", "GameData", "relics.json");

    private static final Set<String> KNOWN_UNRESOLVED_HANDLER_CLASSES =
            Set.of("CardRelic", "SpeedRelicHandler");

    @Test
    void everyRelicHandlerIdExistsInTheRelicsCatalog() throws IOException {
        Set<String> catalogIds = loadCatalogRelicIds();
        Map<String, String> handlerIdsByClassName = loadHandlerIdsByClassName();

        Map<String, String> unmatched = new TreeMap<>();
        handlerIdsByClassName.forEach((className, id) -> {
            if (!catalogIds.contains(id) && !KNOWN_UNRESOLVED_HANDLER_CLASSES.contains(className)) {
                unmatched.put(className, id);
            }
        });

        assertThat(unmatched)
                .as(
                        "RelicHandler.id() values not found in the relic catalog (these relics can "
                                + "never trigger because RelicEngine looks handlers up by the catalog id): %s",
                        describe(unmatched))
                .isEmpty();
    }

    private static String describe(Map<String, String> unmatched) {
        StringBuilder sb = new StringBuilder();
        unmatched.forEach((className, id) -> sb.append("\n  - ")
                .append(className)
                .append(".id() = \"")
                .append(id)
                .append("\""));
        return sb.toString();
    }

    private static Set<String> loadCatalogRelicIds() throws IOException {
        try (java.io.InputStream fixture =
                RelicHandlerCatalogAlignmentTest.class.getResourceAsStream(RELIC_IDS_FIXTURE)) {
            assertThat(fixture).as("missing fixture %s", RELIC_IDS_FIXTURE).isNotNull();
            JsonNode root = new ObjectMapper().readTree(fixture);
            Set<String> ids = new TreeSet<>();
            for (JsonNode id : root.path("ids")) {
                ids.add(id.asText());
            }
            return ids;
        }
    }

    /**
     * The fixture is a copy, so it can drift. On a machine that holds the developer catalog, this
     * says so on the first run; CI, which has no such file, keeps a deterministic list.
     */
    @Test
    void keepsTheFixtureInStepWithTheDeveloperCatalogWhenThereIsOne() throws IOException {
        if (!java.nio.file.Files.exists(DEVELOPER_CATALOG_PATH)) {
            return;
        }
        JsonNode root = new ObjectMapper().readTree(DEVELOPER_CATALOG_PATH.toFile());
        Set<String> catalogIds = new TreeSet<>();
        for (JsonNode relic : root.path("relics")) {
            catalogIds.add(relic.path("id").asText());
        }

        assertThat(loadCatalogRelicIds())
                .as("%s has drifted from %s; regenerate it", RELIC_IDS_FIXTURE, DEVELOPER_CATALOG_PATH)
                .isEqualTo(catalogIds);
    }

    private static Map<String, String> loadHandlerIdsByClassName() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(RelicHandler.class));

        Map<String, String> idsByClassName = new LinkedHashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(HANDLERS_PACKAGE)) {
            String className = candidate.getBeanClassName();
            try {
                Class<?> clazz = Class.forName(className);
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                RelicHandler handler = (RelicHandler) constructor.newInstance();
                idsByClassName.put(clazz.getSimpleName(), handler.id());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to instantiate relic handler " + className, e);
            }
        }
        return idsByClassName;
    }
}
