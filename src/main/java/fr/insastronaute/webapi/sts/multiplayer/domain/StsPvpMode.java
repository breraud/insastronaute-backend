package fr.insastronaute.webapi.sts.multiplayer.domain;

import java.util.List;
import java.util.Objects;

/**
 * Ce qu'un combat multijoueur oppose : le nombre de sièges humains de chaque équipe, et
 * l'éventuel boss que le serveur pilote.
 *
 * <p>Le moteur, lui, ne connaît pas ces modes et n'a pas à les connaître : {@code CombatSetup}
 * prend une liste de combattants portant chacun son {@code teamId} et son {@code ControllerType},
 * et {@code CombatProgress} désigne l'équipe survivante sans jamais compter les joueurs. Le duel
 * à un contre un n'était donc pas une limite du moteur mais une convention de la couche PVP —
 * une par équipe, l'indice d'équipe valant l'indice du joueur — et c'est cette convention que ce
 * type remplace.
 *
 * <p>Les sièges sont décrits par équipe plutôt que par un total, parce qu'un raid n'est pas
 * symétrique : deux joueurs d'un côté, le boss seul de l'autre. Un total ne saurait pas dire de
 * quel côté ils vont.
 */
public enum StsPvpMode {

    /** Le duel historique, et le seul mode que les batailles antérieures connaissaient. */
    ONE_V_ONE("1v1", List.of(1, 1), null),

    /** Deux contre deux, tous humains. */
    TWO_V_TWO("2v2", List.of(2, 2), null),

    /**
     * Deux joueurs contre un boss que le serveur joue.
     *
     * <p>L'équipe 1 n'a aucun siège humain : elle est remplie par le boss, dont les points de vie
     * suivent le nombre de joueurs en face — sans quoi un boss réglé pour un joueur tomberait
     * deux fois plus vite.
     */
    RAID("Raid", List.of(2, 0), 1);

    private final String label;
    private final List<Integer> humanSeatsByTeam;
    private final Integer bossTeamIndex;

    StsPvpMode(String label, List<Integer> humanSeatsByTeam, Integer bossTeamIndex) {
        this.label = label;
        this.humanSeatsByTeam = List.copyOf(humanSeatsByTeam);
        this.bossTeamIndex = bossTeamIndex;
    }

    public String label() {
        return label;
    }

    public int teamCount() {
        return humanSeatsByTeam.size();
    }

    public int humanSeats(int teamIndex) {
        return humanSeatsByTeam.get(teamIndex);
    }

    /** Combien de joueurs il faut réunir avant que le combat puisse s'ouvrir. */
    public int totalHumanSeats() {
        return humanSeatsByTeam.stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasBoss() {
        return bossTeamIndex != null;
    }

    public int bossTeamIndex() {
        if (bossTeamIndex == null) {
            throw new IllegalStateException(this + " has no boss");
        }
        return bossTeamIndex;
    }

    /**
     * L'équipe du n-ième joueur admis, les équipes étant remplies dans l'ordre.
     *
     * <p>C'est ce qui fait que deux joueurs entrés ensemble dans un raid se retrouvent
     * <em>du même côté</em>, alors qu'en 2v2 ils sont répartis de part et d'autre.
     */
    public int teamOfHumanSeat(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= totalHumanSeats()) {
            throw new IllegalArgumentException(
                    "seat " + seatIndex + " is outside " + this + " (" + totalHumanSeats() + " seats)");
        }
        int remaining = seatIndex;
        for (int teamIndex = 0; teamIndex < humanSeatsByTeam.size(); teamIndex++) {
            int seats = humanSeatsByTeam.get(teamIndex);
            if (remaining < seats) {
                return teamIndex;
            }
            remaining -= seats;
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Le mode nommé par {@code value}, insensible à la casse et aux espaces.
     *
     * <p>Une valeur absente vaut {@link #ONE_V_ONE} : c'est ce que jouaient toutes les batailles
     * et toutes les entrées de file écrites avant que les modes existent, et un client qui ne
     * connaît pas encore le champ demande bien un duel.
     *
     * @throws IllegalArgumentException sur une valeur non vide et inconnue, qui est une erreur
     *         d'appel et non une absence : la deviner mettrait le demandeur dans un autre mode
     *         que celui qu'il a nommé
     */
    public static StsPvpMode parse(String value) {
        if (value == null || value.isBlank()) {
            return ONE_V_ONE;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (StsPvpMode mode : values()) {
            if (mode.name().equals(normalized) || mode.label.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Mode PVP inconnu : " + value);
    }

    /** Le mode d'une bataille ou d'une entrée de file, l'absence valant le duel historique. */
    public static StsPvpMode ofNullable(StsPvpMode mode) {
        return Objects.requireNonNullElse(mode, ONE_V_ONE);
    }
}
