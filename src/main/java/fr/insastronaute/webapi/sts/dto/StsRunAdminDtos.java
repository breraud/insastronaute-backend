package fr.insastronaute.webapi.sts.dto;

import java.time.Instant;
import java.util.List;

/**
 * Ce que l'administration lit des runs solo : quels personnages se jouent, et comment leurs
 * runs se terminent.
 *
 * <p>Regroupés dans un seul fichier parce qu'ils ne servent qu'ensemble, à la même page, comme
 * {@code StsPvpAdminDtos} pour le multijoueur.
 */
public final class StsRunAdminDtos {

    private StsRunAdminDtos() {
    }

    /**
     * @param since  depuis quand, ou null pour « depuis toujours »
     * @param runs   combien de runs ont été lancées sur la période
     */
    public record CharacterUsage(
            Instant since,
            int runs,
            StatusSplit statusSplit,
            List<CharacterUsageRow> characters
    ) {
    }

    /** Comment les runs se sont terminées, tous personnages confondus. */
    public record StatusSplit(int retired, int failed, int abandoned, int active) {
    }

    /**
     * @param share       la part des runs lancées avec ce personnage, entre 0 et 1
     * @param retiredRate les retraits sur les runs décidées (retirées ou game over), ou null s'il
     *                    n'y en a aucune — une run active ou abandonnée ne juge rien
     */
    public record CharacterUsageRow(
            String character,
            int runs,
            double share,
            int retired,
            int failed,
            int abandoned,
            int active,
            Double retiredRate
    ) {
    }

    /** Combien de runs un joueur a lancées avec ce personnage. */
    public record PlayerCharacterUsageRow(String character, int runs) {
    }
}
