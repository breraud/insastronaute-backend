package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Translates two PvP participants into the engine's {@link CombatSetup}.
 *
 * <p>PvP combatants are both {@link ControllerType#HUMAN}: no enemy policy ever
 * runs, the engine simply waits for whichever player the timeline makes active.
 *
 * <p>Each duelist brings the base relic of the character their deck is built around, the same one
 * a run grants at character select. Duels used to grant none at all, which quietly made every
 * base relic a PvE-only affair -- a deck built around what its character's relic does played, in
 * a duel, as a deck built around nothing.
 */
public final class StsPvpCombatSetupFactory {

    public static final int PVP_MAX_HP = 80;
    public static final int PVP_BASE_ENERGY = 3;
    public static final int PVP_TURN_DELAY_TICKS = 25;

    private StsPvpCombatSetupFactory() {
    }

    /**
     * @param selectedCharacter the character the deck is built around, whose base relic its owner
     *                          carries into the duel; null or blank for a duelist with none
     */
    public record StsPvpParticipantSetup(
            String combatantId,
            List<String> cardIds,
            String selectedCharacter,
            int teamIndex,
            ControllerType controllerType,
            int maxHp
    ) {
        public StsPvpParticipantSetup {
            Objects.requireNonNull(combatantId, "combatantId");
            Objects.requireNonNull(controllerType, "controllerType");
            cardIds = List.copyOf(Objects.requireNonNull(cardIds, "cardIds"));
            if (teamIndex < 0) {
                throw new IllegalArgumentException("teamIndex must not be negative");
            }
            if (maxHp <= 0) {
                throw new IllegalArgumentException("maxHp must be positive");
            }
        }

        /** Un duelliste : humain, sur son equipe, aux points de vie du format. */
        public StsPvpParticipantSetup(
                UUID userId, List<String> cardIds, String selectedCharacter, int teamIndex) {
            this(userId.toString(), cardIds, selectedCharacter, teamIndex,
                    ControllerType.HUMAN, PVP_MAX_HP);
        }

        public StsPvpParticipantSetup(UUID userId, List<String> cardIds, String selectedCharacter) {
            this(userId, cardIds, selectedCharacter, 0);
        }

        public StsPvpParticipantSetup(UUID userId, List<String> cardIds) {
            this(userId, cardIds, null, 0);
        }

        /**
         * The relic this duelist carries, by the {@code <character>Relic} name every base relic
         * handler answers to.
         *
         * <p>The name is composed rather than looked up in a table of the nine: a character whose
         * handler does not exist yet contributes an id the relic engine does not know, and an
         * unknown id is already ignored. That way a new character's relic starts working in duels
         * the moment its handler is written, and never before.
         */
        List<String> relics() {
            return selectedCharacter == null || selectedCharacter.isBlank()
                    ? List.of()
                    : List.of(selectedCharacter.trim() + "Relic");
        }

        /**
         * Un boss n'a pas de deck a lui : il joue le motif de mouvements de sa definition, comme
         * en run. Sa pioche part donc vide, et {@code combatantSetup} ne refuse une pioche vide
         * que d'un combattant cense en avoir une.
         */
        boolean playsFromAPattern() {
            return controllerType == ControllerType.AI && cardIds.isEmpty();
        }
    }

    public static String teamId(int teamIndex) {
        return "team-" + teamIndex;
    }

    /** Le duel historique, ecrit avec la forme generale : deux humains, une equipe chacun. */
    public static CombatSetup create(
            UUID combatId,
            String dataVersion,
            long seed,
            StsPvpParticipantSetup first,
            StsPvpParticipantSetup second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return create(combatId, dataVersion, seed, List.of(
                withTeam(first, 0),
                withTeam(second, 1)));
    }

    /**
     * Le montage d'un combat multijoueur, quel qu'en soit le format.
     *
     * <p>Chaque participant porte son equipe et son pilote, si bien que le 1v1, le 2v2 et le raid
     * ne sont plus ici que des listes de longueurs differentes. Le moteur ne demandait deja rien
     * de plus : {@code CombatSetup} verifie qu'il y a au moins deux equipes vivantes et ne compte
     * jamais les joueurs.
     *
     * @throws IllegalArgumentException quand un participant cense jouer un deck n'en a pas
     */
    public static CombatSetup create(
            UUID combatId,
            String dataVersion,
            long seed,
            List<StsPvpParticipantSetup> participants
    ) {
        Objects.requireNonNull(participants, "participants");

        List<CombatantSetup> combatants = new ArrayList<>();
        for (StsPvpParticipantSetup participant : participants) {
            combatants.add(combatantSetup(participant));
        }
        return new CombatSetup(combatId, dataVersion, seed, combatants);
    }

    private static StsPvpParticipantSetup withTeam(StsPvpParticipantSetup participant, int teamIndex) {
        return new StsPvpParticipantSetup(
                participant.combatantId(),
                participant.cardIds(),
                participant.selectedCharacter(),
                teamIndex,
                participant.controllerType(),
                participant.maxHp());
    }

    private static CombatantSetup combatantSetup(StsPvpParticipantSetup participant) {
        if (participant.cardIds().isEmpty() && !participant.playsFromAPattern()) {
            throw new IllegalArgumentException(
                    "PvP participant " + participant.combatantId() + " has an empty deck");
        }

        List<CardInstance> deck = new ArrayList<>();
        for (String definitionId : participant.cardIds()) {
            if (definitionId == null || definitionId.isBlank()) {
                throw new IllegalArgumentException(
                        "PvP participant " + participant.combatantId() + " has a blank card in its deck");
            }
            deck.add(new CardInstance(UUID.randomUUID().toString(), definitionId));
        }

        return new CombatantSetup(
                participant.combatantId(),
                teamId(participant.teamIndex()),
                participant.controllerType(),
                participant.maxHp(),
                PVP_BASE_ENERGY,
                PVP_TURN_DELAY_TICKS,
                0L,
                deck,
                participant.relics());
    }
}
