package fr.insastronaute.webapi.card.goofy;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Le code et la carte à donner.
 *
 * <p>{@code enabled} est un booléen encapsulé : absent, il vaut « désactivé »
 * plutôt que de faire échouer la requête sur un champ manquant.</p>
 */
public record GoofyCardSettingsRequest(
        UUID cardId,
        @Size(max = 64) String code,
        Boolean enabled
) {
    public boolean enabledOrFalse() {
        return Boolean.TRUE.equals(enabled);
    }
}
