package fr.insastronaute.webapi.challenge.auto.types.permanentqrscanned;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parameterless configuration for {@code PERMANENT_QR_SCANNED}.
 *
 * <p>The explicit type keeps the plugin contract uniform while rejecting
 * accidental or unsupported configuration fields.</p>
 */
public record PermanentQrScannedParameters() {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PermanentQrScannedParameters fromJson(JsonNode value) {
        if (!value.isObject() || !value.isEmpty()) {
            throw new IllegalArgumentException("Parameters must be an empty JSON object.");
        }
        return new PermanentQrScannedParameters();
    }
}
