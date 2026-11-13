package fr.insastronaute.webapi.economy.service;

import fr.insastronaute.webapi.economy.domain.CurrencySource;
import fr.insastronaute.webapi.user.domain.AppUser;

import java.util.UUID;

/**
 * Le registre des mouvements de tokens et de points Events.
 *
 * <p>Une interface plutot qu'un service concret : les services qui creditent
 * des tokens le recoivent par setter, et gardent sinon {@link #NOOP}. Brancher
 * un constructeur de plus aurait casse dix-sept fichiers de tests pour une
 * trace statistique, et Spring refuse de demarrer si l'implementation manque.</p>
 *
 * <p>Le calcul des PE, lui, ne passe pas par ici : il est porte par
 * {@link AppUser#creditEventPointsFor}, pour que la monnaie reste juste meme la
 * ou le registre est absent.</p>
 */
@FunctionalInterface
public interface CurrencyLedger {

    CurrencyLedger NOOP = (user, source, tokenDelta, eventPointsDelta, referenceId) -> { };

    /** Ce qui a reellement bouge : un mouvement nul dans les deux monnaies n'est pas ecrit. */
    void record(AppUser user, CurrencySource source, long tokenDelta, long eventPointsDelta, UUID referenceId);
}
