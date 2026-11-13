package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Objects;

public record ResolutionStep(
        List<CombatantState> combatants,
        List<TimelineEntry> timeline,
        List<CombatEvent> events,
        RandomState random,
        List<PendingCardPlay> pendingCardPlays,
        boolean endsTurn
) {

    /**
     * Une carte qu'un effet ou une relique demande à voir jouer sur-le-champ.
     *
     * <p>Un handler ne sait pas résoudre une carte : il ne voit ni le catalogue ni le résolveur.
     * PERF, qui doit « jouer la carte du dessus de votre pioche » quand son porteur encaisse un
     * coup, n'avait donc que le moyen de la déplacer en main — la carte arrivait, mais ne se
     * jouait jamais, et aucune animation ne venait le dire. Le handler nomme désormais ce qu'il
     * veut voir jouer, et {@code EffectResolver}, qui sait le faire, s'en charge.
     *
     * @param casterId    qui la joue
     * @param definitionId la carte
     * @param targetId    la cible voulue, ou null pour laisser le résolveur la choisir
     * @param cardInstanceId l'exemplaire joué quand il existe — la carte du dessus de la pioche
     *                    pour PERF — ou null pour une carte qui n'existe nulle part. Le client
     *                    retrouve ainsi la vraie carte, avec ses améliorations, plutôt que d'en
     *                    reconstruire une depuis sa seule définition.
     */
    public record PendingCardPlay(
            String casterId, String definitionId, String targetId, String cardInstanceId) {

        public PendingCardPlay {
            Objects.requireNonNull(casterId, "casterId");
            Objects.requireNonNull(definitionId, "definitionId");
        }

        public PendingCardPlay(String casterId, String definitionId, String targetId) {
            this(casterId, definitionId, targetId, null);
        }
    }

    public ResolutionStep {
        combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
        timeline = List.copyOf(Objects.requireNonNull(timeline, "timeline"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(random, "random");
        pendingCardPlays = List.copyOf(
                Objects.requireNonNull(pendingCardPlays, "pendingCardPlays"));
    }

    /** The shape that carries card plays but asks for no turn to end. */
    public ResolutionStep(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            List<CombatEvent> events,
            RandomState random,
            List<PendingCardPlay> pendingCardPlays
    ) {
        this(combatants, timeline, events, random, pendingCardPlays, false);
    }

    /** The shape every handler but the ones that ask for a card play still uses. */
    public ResolutionStep(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            List<CombatEvent> events,
            RandomState random
    ) {
        this(combatants, timeline, events, random, List.of(), false);
    }

    public ResolutionStep(CombatantState combatant, List<TimelineEntry> timeline, List<CombatEvent> events, RandomState random) {
        this(List.of(Objects.requireNonNull(combatant, "combatant")), timeline, events, random);
    }

    /** The same step, with one more card waiting to be played. */
    public ResolutionStep asking(PendingCardPlay play) {
        List<PendingCardPlay> asked = new java.util.ArrayList<>(pendingCardPlays);
        asked.add(play);
        return new ResolutionStep(combatants, timeline, events, random, asked, endsTurn);
    }

    /**
     * Le même pas, en demandant que le tour du lanceur se termine une fois la carte finie.
     *
     * <p>Un handler ne peut pas clore un tour : la cérémonie de fin de tour tient dans
     * {@code CombatEngine} — statuts de fin, défausse de la main, suivis de tour de terrain,
     * avancement de la file, ouverture du tour suivant — et rien de tout cela n'est à sa portée.
     * Il le demande donc, et le moteur s'en charge quand la carte a fini de se résoudre.
     */
    public ResolutionStep endingTheTurn() {
        return new ResolutionStep(combatants, timeline, events, random, pendingCardPlays, true);
    }
}
