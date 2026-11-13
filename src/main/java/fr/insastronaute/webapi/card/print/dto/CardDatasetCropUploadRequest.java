package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ce que l'outil de collecte sait de la decoupe qu'il envoie.
 *
 * <p>{@code label} est facultatif : une decoupe qu'aucun code de la planche n'explique part
 * en arbitrage au lieu d'etre rejetee.
 *
 * <p>Les cinq champs {@code region*} decrivent ou se trouvait la decoupe dans la photo entiere
 * qui l'accompagne, dans les coordonnees de cette photo. Ils vont ensemble : quatre nombres ou
 * aucun. Ils sont facultatifs, comme la photo -- les outils deja en place n'envoient ni l'un ni
 * l'autre, et leurs decoupes restent parfaitement utilisables pour la lecture. Ce qu'elles ne
 * permettent pas, c'est de reparer le cadrage.
 *
 * <p>{@code framing} est facultatif et vaut {@link CardDatasetCropFraming#AUTOMATIC} en son
 * absence : les outils de collecte deja en place ne l'envoient pas et passent tous par la
 * segmentation automatique.
 */
public record CardDatasetCropUploadRequest(
        @NotNull @PositiveOrZero Integer position,
        String label,
        CardDatasetCropFraming framing,
        String captureDevice,
        Integer captureWidthPx,
        Integer captureHeightPx,
        Integer regionXPx,
        Integer regionYPx,
        Integer regionWidthPx,
        Integer regionHeightPx,
        Double regionAngleDeg
) {
}
