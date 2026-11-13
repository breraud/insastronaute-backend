package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ce qu'un duel réglé a rapporté ou coûté à chacun de ses joueurs.
 *
 * <p>Gardé sur la bataille plutôt que recalculé : après coup, il ne serait plus calculable du
 * tout, puisque les classements des deux camps ont déjà bougé et que la formule part justement
 * de ce qu'ils valaient avant. Sans cette trace, un joueur constate seulement que son classement
 * a changé entre deux passages au menu, sans savoir de combien ni pourquoi.
 *
 * <p>Rangé par identifiant de joueur, parce que c'est la question que le client pose : « et moi,
 * qu'est-ce que j'y ai gagné ? »
 */
public final class StsPvpBattleRewards {

    private StsPvpBattleRewards() {
    }

    /**
     * Ce qu'un joueur retire d'un duel.
     *
     * @param eloBefore  son classement avant, sans quoi la variation ne se relit pas
     * @param eloAfter   son classement après, plancher à zéro compris
     * @param tokenDelta les jetons gagnés, négatifs sur une défaite. C'est le montant
     *                   <em>effectivement</em> appliqué au portefeuille : une défaite ne peut pas
     *                   retirer plus que ce que le joueur avait, et annoncer un retrait qui n'a
     *                   pas eu lieu serait pire que de ne rien annoncer.
     */
    public record Entry(
            UUID userId,
            boolean won,
            int eloBefore,
            int eloAfter,
            int eloDelta,
            long tokenDelta
    ) {

        public Entry {
            Objects.requireNonNull(userId, "userId");
        }
    }

    /** Les gains de tous, sous la forme rangée sur la bataille. */
    public static ObjectNode write(ObjectMapper objectMapper, List<Entry> entries) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        ObjectNode rewards = objectMapper.createObjectNode();
        if (entries == null) {
            return rewards;
        }

        for (Entry entry : entries) {
            ObjectNode node = rewards.putObject(entry.userId().toString());
            node.put("won", entry.won());
            node.put("eloBefore", entry.eloBefore());
            node.put("eloAfter", entry.eloAfter());
            node.put("eloDelta", entry.eloDelta());
            node.put("tokenDelta", entry.tokenDelta());
        }
        return rewards;
    }

    /** Ce qui a été rangé, relu. Un contenu absent ou illisible vaut « rien à annoncer ». */
    public static List<Entry> read(JsonNode stored) {
        List<Entry> entries = new ArrayList<>();
        if (stored == null || !stored.isObject()) {
            return List.copyOf(entries);
        }

        stored.fields().forEachRemaining(field -> {
            UUID userId;
            try {
                userId = UUID.fromString(field.getKey());
            } catch (IllegalArgumentException notAnId) {
                return;
            }
            JsonNode value = field.getValue();
            entries.add(new Entry(
                    userId,
                    value.path("won").asBoolean(false),
                    value.path("eloBefore").asInt(0),
                    value.path("eloAfter").asInt(0),
                    value.path("eloDelta").asInt(0),
                    value.path("tokenDelta").asLong(0L)));
        });
        return List.copyOf(entries);
    }
}
