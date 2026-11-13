package fr.insastronaute.webapi.sts.multiplayer.service;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Ce que « le même deck » veut dire au moment d'enregistrer.
 *
 * <p>Enregistrer sous un nom déjà pris <b>remplace</b> le deck qui le porte au lieu d'en créer un
 * second. Sans cela, chaque sauvegarde — et chaque validation, qui enregistre sous « Deck Actif » —
 * ajoutait une ligne au menu déroulant, qui finissait rempli de copies impossibles à distinguer.
 *
 * <p>Deux règles décident de ce qui est « le même nom » :
 * <ul>
 *   <li><b>Pour le même personnage seulement.</b> Un deck est lié à son personnage, et le deck actif
 *       se tient déjà par personnage. Remplacer par-delà les personnages ferait qu'enregistrer
 *       « Aggro » pour MECA effacerait sans prévenir le « Aggro » d'EP.</li>
 *   <li><b>Sans tenir compte de la casse ni des espaces autour.</b> « Aggro » et « aggro  »
 *       s'affichent pareil pour qui les lit, et les garder tous deux reproduirait le doublon que
 *       cette règle doit empêcher.</li>
 * </ul>
 */
final class StsPvpDeckNaming {

    private StsPvpDeckNaming() {
    }

    /** Le nom tel qu'on le compare : sans espaces autour, sans casse. */
    static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static boolean sameName(String left, String right) {
        return key(left).equals(key(right));
    }

    /**
     * Les decks qui portent déjà {@code name}, dans l'ordre reçu.
     *
     * @param decksOfCharacter les decks du joueur pour le personnage concerné, du plus récemment
     *                         modifié au plus ancien, comme le dépôt les rend. Le premier de la liste
     *                         rendue est donc celui à remplacer, les suivants des doublons plus anciens.
     */
    static List<StsPvpDeck> sameNamed(List<StsPvpDeck> decksOfCharacter, String name) {
        Objects.requireNonNull(decksOfCharacter, "decksOfCharacter");
        return decksOfCharacter.stream()
                .filter(deck -> deck != null && sameName(deck.getName(), name))
                .toList();
    }
}
