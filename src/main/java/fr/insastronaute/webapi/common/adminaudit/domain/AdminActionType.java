package fr.insastronaute.webapi.common.adminaudit.domain;

/**
 * Les gestes d'administration qui changent ce qu'un joueur possede.
 *
 * <p>Par paires : un registre ou manquent les retraits laisse passer
 * exactement ce qu'on voudrait y retrouver.</p>
 *
 * <p>Trois d'entre eux n'ont pas de cible : ils ont un auteur et N joueurs
 * touches. Leur ligne nomme le geste et sa portee, pas une personne - une
 * ligne par joueur ferait des milliers de lignes pour un seul clic. C'est ce
 * que dit {@link #isGlobalScope()}, et c'est la meme regle que celle portee
 * par la contrainte {@code ck_admin_action_events_target_presence}.</p>
 */
public enum AdminActionType {
    CHALLENGE_VALIDATED,
    CHALLENGE_REJECTED,
    CHALLENGE_GRANTED,
    /**
     * Le droit de reposter rendu a un joueur sur un defi ferme.
     *
     * <p>Sans paire, et c'est voulu : la grace expire d'elle-meme. La retirer
     * avant terme reviendrait a reprendre du temps deja annonce au joueur.</p>
     */
    CHALLENGE_GRACE_GRANTED,
    /**
     * Les points bonus d'un defi deja valide, revus apres coup.
     *
     * <p>La part discretionnaire de la decision se posait au moment de la
     * validation, et jamais apres : un bonus oublie ou mal dose demandait de
     * refuser la preuve pour la revalider -- donc d'annoncer au joueur un refus
     * qui n'en etait pas un, et parfois d'effacer sa preuve au passage.</p>
     *
     * <p>Sans paire, parce qu'il est sa propre paire : l'ajustement peut rendre
     * comme retirer. Les details portent l'avant et l'apres, et le motif --
     * c'est exactement le "joueur soudain riche" que le registre existe pour
     * expliquer, sauf qu'ici le geste est nomme.</p>
     */
    CHALLENGE_BONUS_ADJUSTED,
    TOKENS_GRANTED,
    TOKENS_REVOKED,
    VIRTUAL_CARDS_GRANTED,
    VIRTUAL_CARDS_REVOKED,
    PHYSICAL_CARD_ASSIGNED,
    PHYSICAL_CARD_RELEASED,
    /**
     * Une instance physique repointee vers une autre definition de carte,
     * sans changer de proprietaire.
     *
     * <p>Sans paire : ce geste n'a pas de contraire, il retire et donne d'un
     * seul mouvement. Le joueur perd une carte et en gagne une autre - c'est
     * ce qu'il faut pouvoir retrouver. Les points portes au registre sont les
     * valeurs nominales des deux cartes, pas le mouvement reel du score de
     * collection du joueur.</p>
     */
    PHYSICAL_CARD_SWAPPED,
    /**
     * Le bareme de points de collection a bouge : celui d'une rarete, ou les
     * reglages globaux qui decident de ce que vaut une collection.
     *
     * <p>De portee globale : tous les detenteurs concernes voient leur score
     * bouger d'un seul geste. Les details portent l'avant/apres et le nombre
     * de joueurs reellement touches.</p>
     */
    COLLECTION_SCALE_CHANGED(true),
    /**
     * Les points d'un defi ont bouge : le recalcul recredite tous ses
     * participants.
     *
     * <p>De portee globale, pour la meme raison.</p>
     */
    CHALLENGE_POINTS_CHANGED(true),
    /**
     * Un defi supprime : le recalcul qui suit retire ses points a chacun de
     * ses participants.
     *
     * <p>De portee globale, pour la meme raison que les deux precedents. Le geste
     * est desormais reversible -- rien n'est efface, le defi est marque supprime --
     * mais les details continuent de porter le titre, les points et le nombre de
     * joueurs credites : c'est ce qui permet de lire la ligne sans aller rouvrir le
     * defi, et de savoir ce qu'une restauration rendrait.</p>
     *
     * <p>Voir {@link #CHALLENGE_RESTORED} pour le geste inverse.</p>
     */
    CHALLENGE_DELETED(true),
    /**
     * Un defi remis en place, avec ses preuves et les points qu'elles donnaient.
     *
     * <p>De portee globale comme la suppression : le geste porte sur un defi. Les
     * details reprennent le titre et le nombre de joueurs qui retrouvent leurs
     * points, pour que les deux lignes se lisent en miroir.</p>
     */
    CHALLENGE_RESTORED(true),
    /**
     * Une zone du canevas effacee par la moderation.
     *
     * <p>De portee globale : le geste vise un dessin, pas un joueur. Ceux qui
     * l'avaient pose sont rendus a l'appelant pour la suite — la sanction, elle,
     * nomme sa cible et s'ecrit avec {@link #PLACE_BANNED}.</p>
     */
    /**
     * Les preuves d'un defi, effacees en bloc.
     *
     * <p>De portee globale : le geste vise un defi, pas un joueur. Les preuves
     * effacees appartiennent a plusieurs, et nommer l'un d'eux serait faux.</p>
     */
    CHALLENGE_PROOFS_PURGED(true),

    PLACE_CANVAS_CENSORED(true),
    /** Le canevas ferme a un joueur. Le reste du site lui reste ouvert. */
    PLACE_BANNED,
    /** Le canevas rouvert a un joueur. */
    PLACE_UNBANNED,
    /**
     * Un joueur supprime : compte Keycloak efface, retire de l'etat de jeu des autres.
     *
     * <p>Sans paire, et c'est voulu : rien ne le defait. Le compte d'authentification
     * n'existe plus, et une reinscription donnerait un autre identifiant.</p>
     *
     * <p>Sans cible, aussi -- et pour une raison qui n'est pas celle des trois gestes
     * globaux ci-dessus. Une cible en {@code ON DELETE RESTRICT} empecherait la
     * suppression que cette ligne enregistre : la trace interdirait ce qu'elle trace. Le
     * nom et l'e-mail voyagent donc dans les details, ce qui la garde lisible meme quand
     * la ligne du joueur n'existe plus.</p>
     */
    PLAYER_DELETED(true),
    /**
     * Des exemplaires physiques fabriques, en un geste.
     *
     * <p>De portee globale, mais pas pour la raison des trois premiers : ce n'est pas
     * qu'ils touchent beaucoup de joueurs, c'est qu'ils n'en touchent aucun. Un exemplaire
     * fraichement imprime n'appartient a personne -- la trace de son entree dans une
     * collection viendra plus tard, avec {@link #PHYSICAL_CARD_ASSIGNED}.</p>
     *
     * <p>Une ligne par lot, et non par exemplaire : mille lignes pour un clic ne se
     * relisent pas. Les details portent la carte, la quantite, et les codes publics du
     * premier et du dernier -- de quoi situer la plage sans la recopier entiere.</p>
     */
    PHYSICAL_CARDS_CREATED(true),
    /**
     * Une inscription validee : le joueur recoit le role STUDENT, donc l'acces au jeu.
     *
     * <p>C'est ce qu'un joueur possede de plus fondamental — sans cet acces, aucun des
     * autres gestes du registre n'a de sens. La decision se prenait pourtant sans laisser
     * de trace ailleurs que dans la fiche du joueur, qui ne dit que la derniere en date et
     * jamais la suite : un compte valide, refuse, puis revalide n'y laissait qu'une ligne.</p>
     */
    ONBOARDING_VALIDATED,
    /**
     * Une inscription refusee : le role STUDENT est repris, ou jamais donne.
     *
     * <p>Le pendant du precedent. Le motif voyage dans les details, parce que c'est lui
     * qu'on cherche quand on relit la decision — et qu'un refus ulterieur l'ecrase dans la
     * fiche du joueur.</p>
     */
    ONBOARDING_REJECTED,
    /**
     * Une precommande annulee, effacee pour de bon.
     *
     * <p>Une commande annulee reste lisible : elle dit qu'un joueur avait demande quelque
     * chose puis s'est ravise. L'effacer supprime cela, et c'est parfois ce qu'on veut —
     * une commande de test, un doublon. Mais une commande peut porter de l'argent du, et
     * « qu'est-il arrive a ma commande ? » doit rester une question a laquelle on sait
     * repondre : les details gardent ce qu'elle contenait.</p>
     *
     * <p>Sans paire, et pour la meme raison que {@link #PLAYER_DELETED} : rien ne la
     * defait. Nominative en revanche — la ligne du joueur, elle, existe toujours.</p>
     */
    SHOP_ORDER_DELETED,

    /**
     * Un administrateur ouvre la Mediatheque a un membre du staff. Nominative : la
     * cible est la personne designee. Paire de {@link #MEDIA_LIBRARY_ACCESS_REVOKED}.
     */
    MEDIA_LIBRARY_ACCESS_GRANTED,

    /** Un administrateur retire ce droit. */
    MEDIA_LIBRARY_ACCESS_REVOKED,

    /**
     * Un fichier de preuve telecharge depuis la Mediatheque : une copie quitte le
     * systeme. La cible est l'auteur de la soumission.
     */
    MEDIA_LIBRARY_FILE_DOWNLOADED,

    /** Un instantane de la selection est fige pour export. Global : il porte sur N joueurs. */
    MEDIA_LIBRARY_EXPORT_CREATED(true),

    /** Une partie d'export est telechargee. Globale, comme l'instantane qu'elle lit. */
    MEDIA_LIBRARY_EXPORT_DOWNLOADED(true);

    private final boolean globalScope;

    AdminActionType() {
        this(false);
    }

    AdminActionType(boolean globalScope) {
        this.globalScope = globalScope;
    }

    /**
     * Vrai quand le geste nomme une portee plutot qu'un joueur.
     *
     * <p>Une ligne de portee globale n'a pas de cible ; une ligne nominative
     * en a obligatoirement une. Le schema dit la meme chose de son cote, ce
     * qui fait deux endroits pour une seule regle : {@code
     * AdminActionTypeTargetPresenceMigrationTest} les compare pour qu'ils ne
     * divergent pas.</p>
     */
    public boolean isGlobalScope() {
        return globalScope;
    }
}
