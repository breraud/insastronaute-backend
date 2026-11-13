package fr.insastronaute.webapi.challenge.auto.api;

/**
 * Les moments après lesquels un joueur peut venir de satisfaire une phrase clé.
 *
 * Le balayage horaire suffirait à tout attraper, mais une heure de retard entre
 * le geste et sa récompense casse le lien entre les deux. Ces déclencheurs
 * servent le chemin chaud : évaluer un joueur, tout de suite, sur les seuls défis
 * que son geste peut avoir débloqués.
 */
public enum AutoChallengeTrigger {
    CARD_OBTAINED,
    CHALLENGE_VALIDATED,
    TOURNAMENT_SETTLED,
    QR_SCANNED,
    TREASURE_CHECKPOINT_VALIDATED,
    PLACE_PIXEL_PLACED,
    FRIEND_ACCEPTED,
    EVENT_REGISTERED,
    STS_RUN_ENDED,
    STS_PVP_BATTLE_SETTLED
    // Le solde de jetons et le rang au classement n'ont volontairement pas de
    // déclencheur. Un solde bouge d'une demi-douzaine d'endroits, et valider un
    // défi en donne — le chemin chaud se rappellerait lui-même. Le balayage
    // horaire couvre les deux sans ce risque.
}
