package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * L'historique partagé des cartes jouées d'un duel.
 *
 * <p>Il est tenu par le serveur, et non reconstitué par chaque client, pour une raison qui
 * n'est pas de commodité : un client peut déjà accumuler les {@link CardPlayed} qu'il reçoit,
 * mais cette liste ne survit pas à une reconnexion ni à un {@code RESYNC_REQUIRED} — le
 * snapshot ne rejoue aucun événement, il ne porte qu'un état. Deux joueurs dont l'un s'est
 * reconnecté verraient alors deux historiques différents, d'un historique dit « partagé ».
 *
 * <p><b>Rien de privé n'y entre.</b> Une carte jouée est publique par construction :
 * {@link StsPvpCombatBroadcaster#visibleTo} ne retire que les événements qui nomment une carte
 * d'une pile cachée — ce qui a été pioché, ce qui a bougé de ou vers la main ou la pioche, et
 * l'ordre qu'un remaniement a produit — et {@code CardPlayed} n'est aucun de ceux-là : il part
 * déjà aux deux joueurs sans filtrage. L'historique ne fait donc que rendre durable ce que les
 * deux camps ont déjà vu passer, et il est servi tel quel à tout le monde. Le jour où une carte
 * serait jouée face cachée, c'est ici qu'il faudrait la retirer, et nulle part ailleurs.
 *
 * <p>La liste est bornée. Elle vit dans une colonne {@code jsonb} lue et réécrite à chaque
 * commande, donc sa taille est un coût par tour et pas seulement un coût de stockage ; un duel
 * qui s'éternise ne doit pas alourdir chaque coup joué. Ce sont les {@value #MAX_ENTRIES}
 * dernières cartes qui sont gardées, parce qu'un historique sert à relire ce qui vient de se
 * passer.
 */
public final class StsPvpCardHistory {

    /** Combien de cartes l'historique retient au plus, les plus récentes. */
    public static final int MAX_ENTRIES = 200;

    private StsPvpCardHistory() {
    }

    /**
     * Une carte jouée, telle que les deux joueurs la relisent.
     *
     * <p>{@code revision} est celle de l'événement, ce qui donne à la liste un ordre que le
     * client n'a pas à deviner et un moyen de reconnaître une entrée qu'il connaît déjà.
     */
    public record Entry(
            long revision,
            String actorId,
            String cardInstanceId,
            String definitionId,
            List<String> targetIds
    ) {

        public Entry {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(definitionId, "definitionId");
            targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
        }
    }

    /**
     * L'historique de {@code battle} augmenté des cartes que {@code events} a vu jouer.
     *
     * <p>Rend le tableau à écrire sur la bataille, sans jamais modifier celui qu'elle porte :
     * une {@code JsonNode} d'entité modifiée sur place n'est pas toujours vue comme sale par
     * Hibernate, et l'ajout serait perdu au commit sans que rien ne le signale.
     *
     * @param existing l'historique actuel, {@code null} ou non-tableau valant « vide »
     * @param events   les événements d'une commande acceptée ; ceux qui ne sont pas des cartes
     *                 jouées sont ignorés, l'ordre des autres est conservé
     */
    public static ArrayNode append(ObjectMapper objectMapper, JsonNode existing, List<CombatEvent> events) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(events, "events");

        ArrayNode history = objectMapper.createArrayNode();
        if (existing != null && existing.isArray()) {
            history.addAll((ArrayNode) existing.deepCopy());
        }

        for (CombatEvent event : events) {
            if (!(event instanceof CardPlayed played)) {
                continue;
            }
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("revision", played.revision());
            entry.put("actorId", played.actorId());
            entry.put("cardInstanceId", played.cardInstanceId());
            entry.put("definitionId", played.definitionId());
            ArrayNode targets = entry.putArray("targetIds");
            played.targetIds().forEach(targets::add);
            history.add(entry);
        }

        if (history.size() <= MAX_ENTRIES) {
            return history;
        }

        // Les plus anciennes partent : ce qui vient d'être joué est ce qu'on relit.
        ArrayNode trimmed = objectMapper.createArrayNode();
        for (int index = history.size() - MAX_ENTRIES; index < history.size(); index++) {
            trimmed.add(history.get(index));
        }
        return trimmed;
    }

    /**
     * L'historique stocké, relu.
     *
     * <p>Une entrée sans acteur ou sans définition est sautée plutôt que rendue à moitié : elle
     * ne désigne aucune carte affichable, et un client qui la recevrait aurait à décider seul
     * quoi en faire.
     */
    public static List<Entry> read(JsonNode stored) {
        List<Entry> entries = new ArrayList<>();
        if (stored == null || !stored.isArray()) {
            return List.copyOf(entries);
        }

        for (JsonNode node : stored) {
            String actorId = node.path("actorId").asText("");
            String definitionId = node.path("definitionId").asText("");
            if (actorId.isBlank() || definitionId.isBlank()) {
                continue;
            }

            List<String> targetIds = new ArrayList<>();
            for (JsonNode target : node.path("targetIds")) {
                String value = target.asText("");
                if (!value.isBlank()) {
                    targetIds.add(value);
                }
            }

            entries.add(new Entry(
                    node.path("revision").asLong(0L),
                    actorId,
                    node.path("cardInstanceId").asText(""),
                    definitionId,
                    targetIds));
        }
        return List.copyOf(entries);
    }
}
