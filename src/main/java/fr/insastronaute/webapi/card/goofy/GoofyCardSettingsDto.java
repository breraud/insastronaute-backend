package fr.insastronaute.webapi.card.goofy;

import java.util.UUID;

public record GoofyCardSettingsDto(
        UUID cardId,
        String cardName,
        String code,
        boolean enabled
) {
    public static GoofyCardSettingsDto from(GoofyCardSettings settings) {
        return new GoofyCardSettingsDto(
                settings.getCard() == null ? null : settings.getCard().getId(),
                settings.getCard() == null ? null : settings.getCard().getName(),
                settings.getCode(),
                settings.isEnabled()
        );
    }
}
