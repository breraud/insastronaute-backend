package fr.insastronaute.webapi.sts.combat.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Typed JSON accessors shared by every combat data parser.
 *
 * <p>Each accessor reports the exact JSON path it rejected, so a data error can be fixed without
 * reading engine code, and every parser reports failures the same way.
 */
final class JsonValues {

    private JsonValues() {
    }

    static void rejectUnknownFields(JsonNode object, Set<String> allowedFields, String context) {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowedFields.contains(field)) {
                throw invalid(context + "." + field, "unknown field");
            }
        }
    }

    static String requiredText(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + "." + field, "must be a non-blank string");
        }
        return value.textValue();
    }

    static int requiredNonNegativeInt(JsonNode object, String field, String context) {
        int intValue = requiredInt(object, field, context);
        if (intValue < 0) {
            throw invalid(context + "." + field, "must be non-negative");
        }
        return intValue;
    }

    static int requiredInt(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(context + "." + field, "must be an integer");
        }
        return value.intValue();
    }

    static int optionalInt(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return 0;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(context + "." + field, "must be an integer");
        }
        return value.intValue();
    }

    static String optionalText(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(context + "." + field, "must be a string");
        }
        return value.textValue();
    }

    static boolean optionalBoolean(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw invalid(context + "." + field, "must be a boolean");
        }
        return value.booleanValue();
    }

    static List<String> optionalTextArray(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw invalid(context + "." + field, "must be an array");
        }
        List<String> result = new ArrayList<>(value.size());
        for (int index = 0; index < value.size(); index++) {
            JsonNode item = value.get(index);
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw invalid(context + "." + field + "[" + index + "]", "must be a non-blank string");
            }
            result.add(item.textValue());
        }
        return List.copyOf(result);
    }

    static void rejectTrueBoolean(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isBoolean()) {
            throw invalid(context + "." + field, "must be a boolean");
        }
        if (value.booleanValue()) {
            throw invalid(context + "." + field, "true is not supported");
        }
    }

    static void rejectNonBlankText(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isTextual()) {
            throw invalid(context + "." + field, "must be a string");
        }
        if (!value.textValue().isBlank()) {
            throw invalid(context + "." + field, "non-blank values are not supported");
        }
    }

    static void rejectNonEmptyArray(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw invalid(context + "." + field, "must be an array");
        }
        if (!value.isEmpty()) {
            throw invalid(context + "." + field, "non-empty arrays are not supported");
        }
    }

    static CardCatalogValidationException invalid(String path, String problem) {
        return new CardCatalogValidationException(path + ": " + problem);
    }
}
