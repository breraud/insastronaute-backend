package fr.insastronaute.webapi.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * Le passage entre le JSON libre d'une requête et l'arbre Jackson qu'on stocke.
 *
 * <p>Deux Jackson cohabitent ici : la couche HTTP est en Jackson 3, la
 * persistance et les types de défis automatiques en Jackson 2. Un {@code JsonNode}
 * de Jackson 2 posé sur un DTO d'entrée est donc illisible par le convertisseur
 * HTTP, qui ne sait pas le construire — toute création de défi automatique
 * répondait 500 pour cette seule raison.</p>
 *
 * <p>Les DTO portent donc une {@code Map}, que les deux Jackson savent lire et
 * écrire, et la conversion vers l'arbre se fait ici, une fois passée la
 * frontière.</p>
 */
public final class JsonPayloads {

    /**
     * Un mapper à lui seul, sans configuration : la conversion ne porte que des
     * types simples — chaînes, nombres, booléens, listes, tables. Emprunter celui
     * de l'application le rendrait sensible à des réglages qui ne le concernent
     * pas.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private JsonPayloads() {
    }

    /** L'arbre correspondant, ou {@code null} si rien n'a été envoyé. */
    public static JsonNode toNode(Map<String, Object> payload) {
        return payload == null ? null : MAPPER.valueToTree(payload);
    }

    /** La table correspondante, ou {@code null} si rien n'est stocké. */
    public static Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return MAPPER.convertValue(node, MAP);
    }
}
