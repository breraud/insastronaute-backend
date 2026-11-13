package fr.insastronaute.webapi.challenge.auto.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTypeMetadata;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoChallengeTypeRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesExactlyTheSupportedParameterTypes() {
        assertThat(Stream.of(AutoChallengeParameterType.values()).map(Enum::name))
                .containsExactly("INTEGER", "BOOLEAN", "TEXT", "DATE", "SELECT",
                        // Une phrase clé qui vise une catégorie de cartes en a besoin.
                        "CARD_CATEGORY",
                        // Le catalogue a deux axes : la catégorie porte la rareté, le
                        // type porte ce que la carte représente — un bar, un lieu. Viser
                        // « toutes les cartes bar » demande celui-ci.
                        "CARD_TYPE",
                        // Et celle qui vise une carte précise, en a besoin d'une autre :
                        // le panneau doit savoir dessiner le sélecteur correspondant.
                        "CARD");
    }

    @Test
    void discoversTypesAndExposesTheirMetadata() {
        AutoChallengeParameter parameter = new AutoChallengeParameter(
                "minimum", "Minimum", AutoChallengeParameterType.INTEGER, true,
                objectMapper.valueToTree(1), 1L, 10L, List.of()
        );
        TestType type = new TestType("TEST", List.of(parameter));

        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(List.of(type), objectMapper);

        assertThat(registry.require("TEST")).isSameAs(type);
        assertThat(registry.metadata()).containsExactly(new AutoChallengeTypeMetadata(
                "TEST", "Test type", "Test description", List.of(parameter)
        , false));
    }

    @Test
    void parsesTypedParametersAndInvokesTypeValidation() throws Exception {
        TestType type = new TestType("TEST", List.of());
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(List.of(type), objectMapper);

        Object parsed = registry.parseAndValidate("TEST", objectMapper.readTree("{\"minimum\":3}"));

        assertThat(parsed).isEqualTo(new Threshold(3));
        assertThat(type.validated).isEqualTo(new Threshold(3));
    }

    @Test
    void acceptsTypesWithoutAdditionalBusinessValidation() throws Exception {
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(
                List.of(new TypeWithoutBusinessValidation()), objectMapper
        );

        Object parsed = registry.parseAndValidate("NO_VALIDATION", objectMapper.readTree("{\"minimum\":3}"));

        assertThat(parsed).isEqualTo(new Threshold(3));
    }

    @Test
    void rejectsUnknownKeysAsBadRequests() {
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(List.of(), objectMapper);

        assertThatThrownBy(() -> registry.require("UNKNOWN"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(error -> assertThat(((BadRequestException) error).getFieldErrors())
                        .extracting("field")
                        .containsExactly("autoChallengeType"));
    }

    @Test
    void convertsJacksonFailuresToParameterFieldErrors() throws Exception {
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(
                List.of(new TestType("TEST", List.of())), objectMapper
        );

        assertThatThrownBy(() -> registry.parseAndValidate(
                "TEST", objectMapper.readTree("{\"minimum\":\"not-a-number\"}")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(error -> assertThat(((BadRequestException) error).getFieldErrors())
                        .extracting("field")
                        .containsExactly("autoChallengeParameters"));
    }

    @Test
    void convertsTypeValidationFailuresToParameterFieldErrors() throws Exception {
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(
                List.of(new TestType("TEST", List.of())), objectMapper
        );

        assertThatThrownBy(() -> registry.parseAndValidate(
                "TEST", objectMapper.readTree("{\"minimum\":0}")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(error -> assertThat(((BadRequestException) error).getFieldErrors())
                        .extracting("field")
                        .containsExactly("autoChallengeParameters"));
    }

    @Test
    void propagatesUnexpectedRuntimeFailuresFromTypeValidation() throws Exception {
        AutoChallengeType<Threshold> broken = new TestType("BROKEN", List.of()) {
            @Override public void validate(Threshold parameters) { throw new IllegalStateException("bug"); }
        };
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(List.of(broken), objectMapper);

        assertThatThrownBy(() -> registry.parseAndValidate("BROKEN", objectMapper.readTree("{\"minimum\":1}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("bug");
    }

    @Test
    void rejectsBlankAndDuplicateKeysAtConstruction() {
        assertThatThrownBy(() -> new AutoChallengeTypeRegistry(
                List.of(new TestType(" ", List.of())), objectMapper))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new AutoChallengeTypeRegistry(List.of(
                new TestType("TEST", List.of()), new TestType("TEST", List.of())), objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST");
    }

    private record Threshold(int minimum) {}

    private static class TestType implements AutoChallengeType<Threshold> {
        private final String key;
        private final List<AutoChallengeParameter> parameters;
        private Threshold validated;

        private TestType(String key, List<AutoChallengeParameter> parameters) {
            this.key = key;
            this.parameters = parameters;
        }

        @Override public String key() { return key; }
        @Override public String publicName() { return "Test type"; }
        @Override public String description() { return "Test description"; }
        @Override public Class<Threshold> parametersType() { return Threshold.class; }
        @Override public List<AutoChallengeParameter> parameters() { return parameters; }

        @Override
        public void validate(Threshold parameters) {
            if (parameters.minimum() < 1) {
                throw new AutoChallengeParameterValidationException("minimum must be positive");
            }
            validated = parameters;
        }

        @Override
        public AutoChallengeCandidatePage findCandidates(Threshold parameters, AutoChallengeCandidateQuery query) {
            return new AutoChallengeCandidatePage(List.of(), null);
        }
    }

    private static class TypeWithoutBusinessValidation implements AutoChallengeType<Threshold> {
        @Override public String key() { return "NO_VALIDATION"; }
        @Override public String publicName() { return "No validation"; }
        @Override public String description() { return "No additional business validation"; }
        @Override public Class<Threshold> parametersType() { return Threshold.class; }
        @Override public List<AutoChallengeParameter> parameters() { return List.of(); }

        @Override
        public AutoChallengeCandidatePage findCandidates(Threshold parameters, AutoChallengeCandidateQuery query) {
            return new AutoChallengeCandidatePage(List.of(), null);
        }
    }
}
