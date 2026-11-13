package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Les rencontres qu'un raid peut opposer aux joueurs, telles que {@code gameplay/pvp_raid_encounters.json}
 * les décrit : une liste de rencontres, chacune une liste d'ennemis et, au besoin, sa propre échelle
 * de points de vie.
 *
 * <pre>{@code
 * { "encounters": [
 *     ["Behemoth"],
 *     { "enemies": ["Aegis_Drone", "Astrum", "Aegis_Drone"], "hpMultiplier": 0.6 }
 * ] }
 * }</pre>
 *
 * <p>Une rencontre s'écrit donc de deux façons : la liste nue, qui garde l'échelle ordinaire, ou
 * l'objet, qui la règle. L'échelle est propre à la rencontre parce que c'est elle qui décide de la
 * difficulté : trois ennemis mis chacun à l'échelle du nombre de joueurs pèsent bien plus lourd
 * qu'un boss seul, et seul celui qui compose la rencontre sait de combien il faut les alléger.
 *
 * <p>Le tableau nu {@code [ ... ]} se lit aussi à la place de {@code {"encounters": [ ... ]}}.
 *
 * <p>Une rencontre est tirée au hasard pour chaque raid, uniformément. Le fichier est facultatif :
 * sans lui, ou sans aucune rencontre jouable, le raid garde le boss unique qui le configurait jusque-là.
 */
public final class StsPvpRaidEncounters {

    /** Aucune rencontre : le raid retombe sur son boss par défaut. */
    public static final StsPvpRaidEncounters NONE = new StsPvpRaidEncounters(List.of());

    /**
     * Une rencontre : ses ennemis, dans l'ordre, et ce par quoi elle multiplie leurs points de vie.
     *
     * @param hpMultiplier appliqué aux PV de chaque ennemi une fois ceux-ci mis à l'échelle du nombre
     *                     de joueurs ; toujours strictement positif, {@code 1} gardant l'échelle ordinaire
     */
    public record Encounter(List<String> enemies, double hpMultiplier) {

        public Encounter {
            enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
            if (!(hpMultiplier > 0d) || Double.isInfinite(hpMultiplier)) {
                hpMultiplier = 1d;
            }
        }
    }

    private final List<Encounter> encounters;

    private StsPvpRaidEncounters(List<Encounter> encounters) {
        this.encounters = List.copyOf(encounters);
    }

    /**
     * Les rencontres du fichier.
     *
     * <p>Un identifiant vide est sauté, et une rencontre qui n'en garde aucun l'est aussi : un
     * raid sans adversaire ne se termine jamais, puisque le moteur exige deux équipes vivantes. Un
     * multiplicateur absent, non numérique, nul ou négatif vaut {@code 1} : un zéro glissé par erreur
     * donnerait un boss qui tombe au premier coup. Un fichier illisible ou d'une autre forme vaut
     * {@link #NONE} : c'est un réglage, et un réglage cassé doit laisser le raid jouable plutôt que
     * d'empêcher de le lancer.
     */
    public static StsPvpRaidEncounters from(JsonNode node) {
        if (node == null) {
            return NONE;
        }
        JsonNode list = node.isArray() ? node : node.path("encounters");
        if (!list.isArray()) {
            return NONE;
        }

        List<Encounter> encounters = new ArrayList<>();
        for (JsonNode entry : list) {
            JsonNode enemiesNode;
            double hpMultiplier = 1d;
            if (entry.isArray()) {
                enemiesNode = entry;
            } else if (entry.isObject()) {
                enemiesNode = entry.path("enemies");
                JsonNode multiplier = entry.path("hpMultiplier");
                if (multiplier.isNumber()) {
                    hpMultiplier = multiplier.asDouble();
                }
            } else {
                continue;
            }
            if (!enemiesNode.isArray()) {
                continue;
            }

            List<String> enemies = new ArrayList<>();
            for (JsonNode enemy : enemiesNode) {
                String id = enemy.isTextual() ? enemy.asText().trim() : "";
                if (!id.isEmpty()) {
                    enemies.add(id);
                }
            }
            if (!enemies.isEmpty()) {
                encounters.add(new Encounter(enemies, hpMultiplier));
            }
        }
        return new StsPvpRaidEncounters(encounters);
    }

    public List<Encounter> encounters() {
        return encounters;
    }

    /**
     * Les rencontres dont chaque ennemi existe dans les données de jeu.
     *
     * <p>Filtrées plutôt que tirées puis vérifiées : un ennemi inconnu n'est découvert qu'à
     * l'ouverture du combat, une fois les joueurs déjà sortis de la file, et le raid échouerait
     * alors pour tous. Écarter la rencontre avant le tirage garde le raid jouable avec les autres.
     */
    public List<Encounter> playable(Predicate<String> knownEnemy) {
        Objects.requireNonNull(knownEnemy, "knownEnemy");
        return encounters.stream()
                .filter(encounter -> encounter.enemies().stream().allMatch(knownEnemy))
                .toList();
    }

    /** Une rencontre jouable tirée au hasard, ou rien s'il n'y en a aucune. */
    public Optional<Encounter> pick(RandomGenerator random, Predicate<String> knownEnemy) {
        Objects.requireNonNull(random, "random");
        List<Encounter> playable = playable(knownEnemy);
        if (playable.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(playable.get(random.nextInt(playable.size())));
    }
}
