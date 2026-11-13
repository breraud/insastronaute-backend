package fr.insastronaute.webapi.announcement.domain;

/** Ce qu'annonce une annonce : le ton de l'affichage en depend, pas le contenu. */
public enum AnnouncementKind {
    /** Une information ordinaire. */
    INFO,
    /** Ce qui vient de changer dans le site. */
    PATCH,
    /** Une panne, en cours ou passee. */
    INCIDENT
}
