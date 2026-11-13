package fr.insastronaute.webapi.place.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ce que chaque département tient sur la fresque, en cases.
 *
 * <p>Une case compte pour le département de celui qui l'a posée en dernier, tant
 * qu'elle montre encore son pixel. C'est ce que dessine la fresque des départements :
 * la barre et l'image disent la même chose.</p>
 *
 * @param revision           la révision du canevas lue, celle de la fresque libre
 * @param attributedPixels   les cases rattachées à un département : le total des parts
 * @param unattributedPixels les cases peintes que rien ne rattache à un département —
 *                           auteur sans département, pose antérieure à un
 *                           redimensionnement, zone censurée
 * @param departments        tous les départements, du plus présent au moins présent
 */
public record PlaceDepartmentContributionsDto(
        String revision,
        long attributedPixels,
        long unattributedPixels,
        List<Entry> departments
) {
    public record Entry(UUID departmentId, String shortName, String name, String color, long pixels) {
    }
}
