package fr.insastronaute.webapi.challenge.auto.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTypeMetadata;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Component
public class AutoChallengeTypeRegistry {

    private static final String TYPE_FIELD = "autoChallengeType";
    private static final String PARAMETERS_FIELD = "autoChallengeParameters";

    private final Map<String, AutoChallengeType<?>> types;
    private final ObjectMapper objectMapper;

    public AutoChallengeTypeRegistry(List<AutoChallengeType<?>> discoveredTypes, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, AutoChallengeType<?>> indexedTypes = new LinkedHashMap<>();
        for (AutoChallengeType<?> type : discoveredTypes) {
            String key = type.key();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Automatic challenge type key must not be blank");
            }
            if (indexedTypes.putIfAbsent(key, type) != null) {
                throw new IllegalStateException("Duplicate automatic challenge type key: " + key);
            }
        }
        this.types = Collections.unmodifiableMap(indexedTypes);
    }

    public AutoChallengeType<?> require(String key) {
        AutoChallengeType<?> type = types.get(key);
        if (type == null) {
            throw new BadRequestException(
                    "Unknown automatic challenge type.", TYPE_FIELD, "Unknown automatic challenge type."
            );
        }
        return type;
    }

    public Object parseAndValidate(String key, JsonNode parameters) {
        return parseAndValidateTyped(require(key), parameters);
    }

    public List<AutoChallengeTypeMetadata> metadata() {
        return types.values().stream()
                .map(type -> new AutoChallengeTypeMetadata(
                        type.key(), type.publicName(), type.description(), List.copyOf(type.parameters()),
                        type.isTeamScoped()
                ))
                .toList();
    }

    /** L'objectif de ce type se mesure-t-il sur l'equipe ? Faux pour un type inconnu. */
    public boolean isTeamScoped(String key) {
        if (key == null) {
            return false;
        }
        AutoChallengeType<?> type = types.get(key);
        return type != null && type.isTeamScoped();
    }

    /**
     * Cette phrase-ci se mesure-t-elle sur l'equipe ?
     *
     * <p>Le type seul ne suffit pas : un objectif de cartes porte sur la collection
     * physique, mutualisee, ou sur la virtuelle, qui ne l'est pas. Des parametres
     * illisibles rendent la reponse du type, faute de mieux — l'enregistrement qui
     * suit les refusera de toute facon.</p>
     */
    public boolean isTeamScoped(String key, JsonNode parameters) {
        if (key == null) {
            return false;
        }
        AutoChallengeType<?> type = types.get(key);
        if (type == null) {
            return false;
        }
        if (parameters == null || parameters.isNull()) {
            return type.isTeamScoped();
        }
        try {
            return teamScopedTyped(type, parameters);
        } catch (RuntimeException invalid) {
            return type.isTeamScoped();
        }
    }

    private <P> boolean teamScopedTyped(AutoChallengeType<P> type, JsonNode parameters) {
        return type.isTeamScoped(parseAndValidateTyped(type, parameters));
    }

    /** Ce type écoute-t-il ce moment ? Un type inconnu n'écoute rien. */
    public boolean listensTo(String key, AutoChallengeTrigger trigger) {
        AutoChallengeType<?> type = types.get(key);
        return type != null && type.triggers().contains(trigger);
    }

    /**
     * Où en est un joueur sur cette phrase, ou {@code null} si le type ne sait
     * pas répondre pour un joueur isolé.
     */
    public AutoChallengeProgress progress(String key, JsonNode parameters, UUID userId) {
        return progressTyped(require(key), parameters, userId);
    }

    /** L'aperçu de la phrase, paramètres remplis. */
    public String phrase(String key, JsonNode parameters) {
        return phraseTyped(require(key), parameters);
    }

    private <P> AutoChallengeProgress progressTyped(AutoChallengeType<P> type, JsonNode parameters, UUID userId) {
        return type.progress(parseAndValidateTyped(type, parameters), userId);
    }

    private <P> String phraseTyped(AutoChallengeType<P> type, JsonNode parameters) {
        return type.phrase(parseAndValidateTyped(type, parameters));
    }

    private <P> P parseAndValidateTyped(AutoChallengeType<P> type, JsonNode parameters) {
        final P typedParameters;
        try {
            typedParameters = objectMapper.treeToValue(parameters, type.parametersType());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidParameters("Invalid automatic challenge parameters.");
        }

        try {
            type.validate(typedParameters);
        } catch (BadRequestException exception) {
            throw invalidParameters(exception.getMessage());
        } catch (AutoChallengeParameterValidationException exception) {
            throw invalidParameters(exception.getMessage());
        }
        return typedParameters;
    }

    private BadRequestException invalidParameters(String message) {
        String detail = message == null || message.isBlank()
                ? "Invalid automatic challenge parameters."
                : message;
        return new BadRequestException(detail, PARAMETERS_FIELD, detail);
    }
}
