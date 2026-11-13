package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Qui joue quel personnage, et avec quel succès.
 *
 * <p><b>Rien n'est compté à part.</b> Chaque bataille garde déjà, dans sa colonne {@code teams},
 * le personnage de chacun de ses joueurs au moment où elle s'est ouverte, et son vainqueur ; les
 * batailles ne sont jamais purgées. Tenir un compteur par personnage à côté reviendrait à écrire
 * deux fois la même chose, et les deux finiraient par ne plus être d'accord — un compteur ne se
 * corrige pas quand on découvre une bataille mal réglée, un recalcul si. Le jour où ces chiffres
 * deviendront publics, ils se liront au même endroit.
 *
 * <p>Le taux de victoire accompagne l'usage, et c'est lui qui sert à équilibrer : un personnage
 * peu joué mais qui gagne tout n'a pas le même problème qu'un personnage partout et qui perd.
 *
 * <p>Classe pure : elle ne voit que des batailles déjà lues, et se teste sans base.
 */
public final class StsPvpCharacterUsage {

    /** Le personnage d'un joueur qui n'en a pas choisi, comme partout ailleurs en PVP. */
    static final String DEFAULT_CHARACTER = "EP";

    private StsPvpCharacterUsage() {
    }

    /** Une bataille réglée, réduite à ce que la statistique lit. */
    public record Battle(JsonNode teams, Integer winnerTeam) {
    }

    /**
     * Un personnage et ce qu'il a donné.
     *
     * @param share   sa part des sélections, entre 0 et 1
     * @param winRate ses victoires sur ses parties décidées, ou null s'il n'en a aucune. Les nuls
     *                n'y entrent pas : ils ne disent rien de la force du personnage, et les compter
     *                comme des défaites pénaliserait celui qu'on joue dans les parties serrées.
     */
    public record Row(
            String character,
            int picks,
            double share,
            int wins,
            int losses,
            int draws,
            Double winRate
    ) {
    }

    /** Le résultat d'ensemble : combien de batailles, combien de sélections, et le détail. */
    public record Result(int battles, int picks, List<Row> characters) {

        public Result {
            characters = List.copyOf(characters);
        }
    }

    /**
     * Compte les personnages des joueurs de {@code battles}.
     *
     * <p><b>Seuls les joueurs comptent.</b> Un renfort que le serveur joue emprunte le deck et le
     * personnage d'un coéquipier, et le compter ferait valoir double le choix de ce coéquipier ; un
     * boss de raid n'a pas de personnage du tout.
     */
    public static Result compute(List<Battle> battles) {
        Objects.requireNonNull(battles, "battles");

        Map<String, int[]> tally = new LinkedHashMap<>();
        int battleCount = 0;
        int totalPicks = 0;

        for (Battle battle : battles) {
            if (battle == null || battle.teams() == null || !battle.teams().isArray()) {
                continue;
            }
            battleCount++;

            for (int teamIndex = 0; teamIndex < battle.teams().size(); teamIndex++) {
                JsonNode team = battle.teams().get(teamIndex);
                if (team == null || !team.isArray()) {
                    continue;
                }
                for (JsonNode participant : team) {
                    if (isServerDriven(participant)) {
                        continue;
                    }

                    // [picks, wins, losses, draws]
                    int[] counts = tally.computeIfAbsent(characterOf(participant), ignored -> new int[4]);
                    counts[0]++;
                    totalPicks++;
                    if (battle.winnerTeam() == null) {
                        counts[3]++;
                    } else if (battle.winnerTeam() == teamIndex) {
                        counts[1]++;
                    } else {
                        counts[2]++;
                    }
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            int decided = counts[1] + counts[2];
            rows.add(new Row(
                    entry.getKey(),
                    counts[0],
                    totalPicks == 0 ? 0d : (double) counts[0] / totalPicks,
                    counts[1],
                    counts[2],
                    counts[3],
                    decided == 0 ? null : (double) counts[1] / decided));
        }
        rows.sort(Comparator.comparingInt(Row::picks).reversed().thenComparing(Row::character));

        return new Result(battleCount, totalPicks, rows);
    }

    /**
     * Vrai pour un siège que le serveur joue.
     *
     * <p>Les batailles d'avant les modes ne portent aucun de ces marqueurs, et leurs deux sièges
     * sont bien deux joueurs : l'absence de marqueur vaut « humain ».
     */
    static boolean isServerDriven(JsonNode participant) {
        if (participant == null || !participant.isObject()) {
            return true;
        }
        return "AI".equalsIgnoreCase(participant.path("controller").asText(""))
                || !participant.path("bossEnemyId").asText("").isBlank()
                || !participant.path("deckOwnerUserId").asText("").isBlank();
    }

    static String characterOf(JsonNode participant) {
        String character = participant.path("selectedCharacter").asText("").trim();
        return character.isEmpty() ? DEFAULT_CHARACTER : character;
    }
}
