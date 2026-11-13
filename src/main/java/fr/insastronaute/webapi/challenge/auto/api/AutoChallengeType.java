package fr.insastronaute.webapi.challenge.auto.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AutoChallengeType<P> {

    String key();

    String publicName();

    String description();

    Class<P> parametersType();

    List<AutoChallengeParameter> parameters();

    /**
     * Validates server-side business constraints after JSON parsing. Parameter
     * metadata is descriptive and intended for clients; it does not replace
     * server-side validation. Types without additional constraints do not need
     * to override this method.
     */
    default void validate(P parameters) {
    }

    /**
     * Rattrapage groupé pour le balayage périodique : les joueurs qui satisfont
     * déjà la phrase.
     *
     * <p>La requête doit être bornée, ordonnée de façon déterministe, avancer sur
     * un curseur UUID strictement croissant, et rendre {@code nextCursor = null}
     * à la dernière page. Elle doit aussi écarter, en SQL, ceux qui ont déjà
     * validé {@code query.challengeId()} : ils qualifient pour toujours, et un
     * balayage qui continue de les traverser coûte la même chose chaque heure
     * quoi qu'il reste à attribuer.
     */
    AutoChallengeCandidatePage findCandidates(P parameters, AutoChallengeCandidateQuery query);

    /**
     * La phrase telle qu'un joueur la lit, paramètres remplis : « Ajoute 5 amis ».
     * Sert d'aperçu dans le formulaire d'administration.
     *
     * <p>Par défaut la description du type, pour les types qui n'ont rien à
     * personnaliser.
     */
    default String phrase(P parameters) {
        return description();
    }

    /**
     * Les moments après lesquels ce type mérite d'être réévalué pour un joueur.
     *
     * <p>Vide = seulement le balayage périodique. C'est le bon réglage pour ce
     * qui bouge de partout, comme un solde de jetons : réévaluer à chaque
     * mouvement coûterait plus que d'attendre l'heure suivante.
     */
    /**
     * L'objectif se mesure-t-il sur l'equipe plutot que sur le joueur ?
     *
     * <p>Les cartes physiques sont mutualisees : un objectif de collection est atteint
     * collectivement, souvent grace a des cartes que le joueur n'a pas lui-meme. La
     * recompense se partage alors entre les membres, faute de quoi l'equipe encaisserait
     * autant de fois la mise qu'elle compte de joueurs pour un seul accomplissement.</p>
     *
     * <p>C'est une propriete du type, pas un reglage d'administration : la facon dont
     * l'objectif se mesure ne se coche pas, elle decoule de ce qu'il mesure.</p>
     */
    default boolean isTeamScoped() {
        return false;
    }

    /**
     * Le meme, une fois les parametres connus.
     *
     * <p>Certains types mesurent sur l'equipe ou sur le joueur selon leur reglage :
     * la collection physique est mutualisee, la virtuelle non. Le sans-argument
     * reste ce que le type annonce au panneau avant tout reglage ; celui-ci decide
     * pour une phrase donnee, et c'est lui qui commande le partage de la
     * recompense.</p>
     */
    default boolean isTeamScoped(P parameters) {
        return isTeamScoped();
    }

    default Set<AutoChallengeTrigger> triggers() {
        return Set.of();
    }

    /**
     * Où en est un joueur, ou {@code null} si ce type ne sait pas répondre pour
     * un joueur isolé.
     *
     * <p>Le chemin chaud s'en sert pour trancher sans rebalayer toute la base ;
     * un type qui rend {@code null} n'est attribué que par le balayage.
     */
    default AutoChallengeProgress progress(P parameters, UUID userId) {
        return null;
    }
}
