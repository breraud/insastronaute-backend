package fr.insastronaute.webapi.place.dto;

import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownType;
import java.time.Instant;

/**
 * @param purchased ce pixel a-t-il été payé plutôt qu'attendu
 * @param eventPoints solde de PE restant après un achat, {@code null} pour un pixel normal
 * @param nextBonusPrice ce que coûtera le prochain pixel bonus, {@code null} hors achat
 */
public record PlacePixelUpdate(int x, int y, String color, String revision, CooldownType cooldownType,
                               long remainingCooldownMs, Instant nextPlacementAt,
                               boolean purchased, Long eventPoints, Long nextBonusPrice) {
    /** Un pixel attendu : ni achat, ni solde, ni prix à renvoyer. */
    public PlacePixelUpdate(int x, int y, String color, String revision, CooldownType cooldownType,
                            long remainingCooldownMs, Instant nextPlacementAt) {
        this(x, y, color, revision, cooldownType, remainingCooldownMs, nextPlacementAt, false, null, null);
    }

    public PlacePixelUpdate(int x, int y, String color, long revision, CooldownType cooldownType,
                            long remainingCooldownMs, Instant nextPlacementAt) {
        this(x, y, color, Long.toString(revision), cooldownType, remainingCooldownMs, nextPlacementAt);
    }
}
