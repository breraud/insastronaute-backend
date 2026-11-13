package fr.insastronaute.webapi.treasurehunt.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * L'outil par lequel le secret d'une etape doit arriver.
 *
 * Les sept y figurent des maintenant, KEYPAD et PIANO etant les seuls qu'une etape
 * soit cense attendre a ce jour. Les cinq autres sont des outils complets qu'aucune
 * etape n'attend : la difference tient a une ligne de configuration, pas a une ligne
 * de code, et passer une etape en TELEGRAPH rend le telegraphe vrai sans redeployer
 * le front.
 */
public enum TreasureHuntInputTool {
    KEYPAD, PIANO, DIAL, RADIO, SAFE, TELEGRAPH, PADLOCK;

    /**
     * Les outils que le front joueur implemente aujourd'hui -- une copie, pas l'autorite.
     *
     * L'autorite de fait est `TOOLS` dans le depot insastral, a
     * `src/pages/chasse/tools/index.ts` : c'est elle qui dessine reellement les outils cote
     * joueur. Cette liste-ci en est une copie, tout comme `playableInputTools` dans
     * PanelAdmin (`src/features/treasureHunt/treasureHuntTypes.ts`), et rien -- ni type ni
     * test -- ne les relie entre elles.
     *
     * L'ordre compte : on etend d'abord `TOOLS`, on deploie le front joueur, et seulement
     * apres on allonge cette liste-ci. Les fronts partent par Cloudflare Pages, le backend
     * a la main -- jamais ensemble. Front d'abord, l'ecart est sans danger : la garde reste
     * trop stricte et l'outil neuf ne sert pas encore. Backend d'abord, l'ecart est celui
     * que cette garde existe pour empecher : une chasse s'activerait avec une etape
     * qu'aucun front ne sait afficher.
     */
    public static final Set<TreasureHuntInputTool> IMPLEMENTED_BY_THE_PLAYER_FRONT = EnumSet.of(KEYPAD, PIANO);

    public boolean isImplementedByThePlayerFront() {
        return IMPLEMENTED_BY_THE_PLAYER_FRONT.contains(this);
    }
}
