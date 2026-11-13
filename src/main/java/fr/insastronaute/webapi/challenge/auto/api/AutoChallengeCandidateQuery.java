package fr.insastronaute.webapi.challenge.auto.api;

import java.util.UUID;

/**
 * Ce qu'une recherche de candidats a besoin de savoir.
 *
 * <p>{@code challengeId} sert à écarter en SQL les joueurs qui ont déjà validé
 * ce défi. Le moteur revérifie de toute façon avant d'attribuer : c'est donc une
 * question de taille de balayage, pas de justesse. Sans elle, un défi acquis par
 * tout le monde coûterait le même balayage chaque heure, éternellement.
 *
 * @param challengeId le défi qu'on cherche à attribuer
 * @param afterUserId curseur strictement croissant, {@code null} à la première page
 * @param limit taille de page demandée
 */
public record AutoChallengeCandidateQuery(UUID challengeId, UUID afterUserId, int limit) {
}
