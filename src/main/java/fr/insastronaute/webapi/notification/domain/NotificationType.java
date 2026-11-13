package fr.insastronaute.webapi.notification.domain;

public enum NotificationType {
    FRIEND_REQUEST_RECEIVED,
    FRIEND_REQUEST_ACCEPTED,
    GROUP_INVITE_RECEIVED,
    GROUP_JOIN_REQUEST,
    CHALLENGE_GROUP_INVITE_RECEIVED,
    CHALLENGE_GROUP_INVITE_ACCEPTED,
    CHALLENGE_GROUP_INVITE_DECLINED,
    /**
     * Quelqu'un a ete retire d'un groupe par le chef de l'envoi.
     *
     * <p>Sans ce mot, le defi redevient disponible dans son application sans raison
     * visible : il avait accepte, puis tout a disparu. Cela ressemble a une panne.</p>
     */
    CHALLENGE_GROUP_MEMBER_REMOVED,
    CHALLENGE_SUBMISSION_VALIDATED,
    CHALLENGE_SUBMISSION_REJECTED,
    CHALLENGE_GRACE_GRANTED,
    /** Les points d'un defi deja valide ont change : le joueur doit savoir pourquoi. */
    CHALLENGE_BONUS_ADJUSTED,
    TOURNAMENT_POINTS_AWARDED,
    EVENT_CANCELLED,
    EVENT_REGISTRATION_CANCELLED,
    PUSH_TEST
}
