package fr.insastronaute.webapi.cardteam.dto;

/** Ce qu'un nom d'equipe a le droit d'etre. */
public final class CardTeamNames {

    /**
     * La longueur maximale d'un nom d'equipe.
     *
     * <p>Elle tient a l'endroit ou le nom se lit le plus mal : la ligne de classement,
     * qui le pose a cote d'un rang, d'un departement et d'un score. Un nom long y
     * chassait le reste hors de l'ecran.</p>
     *
     * <p>La colonne en base accepte toujours 60 caracteres, et c'est voulu : des equipes
     * portent deja des noms plus longs, ecrits quand la regle etait plus large. Les
     * retrecir de force renommerait des equipes sans leur demander ; le classement les
     * tronque a l'affichage, et elles gardent leur nom partout ailleurs.</p>
     */
    public static final int MAX_LENGTH = 18;

    private CardTeamNames() {
    }
}
