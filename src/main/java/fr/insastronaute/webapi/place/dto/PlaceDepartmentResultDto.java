package fr.insastronaute.webapi.place.dto;

/**
 * Une ligne du classement des departements.
 *
 * @param territory les cases que le departement tient sur la fresque des departements
 *                  a la fin. C'est le resultat visible.
 * @param placed    les pixels poses par ses membres, toutes fresques confondues. C'est
 *                  l'effort fourni. Un departement recouvert a la derniere minute garde
 *                  le second sans avoir le premier : les deux colonnes ne disent pas la
 *                  meme chose, et aucune ne remplace l'autre.
 * @param share     la part du territoire peint que ce departement occupe, entre 0 et 1.
 *                  Calculee ici : le front ne sait pas si le denominateur est la surface
 *                  du canevas ou ses seules cases peintes.
 */
public record PlaceDepartmentResultDto(
        int rank,
        String name,
        String color,
        long territory,
        long placed,
        double share
) {
}
