package fr.insastronaute.webapi.economy.domain;

/**
 * D'ou vient — ou ou part — un mouvement de tokens ou de points Events.
 *
 * <p>La regle des PE vit ici, source par source, parce qu'elle en depend : un
 * defi rapporte le double de ses tokens, un duel perdu ne coute rien, un achat
 * ne rapporte rien. L'ecrire a chaque point de credit l'aurait laissee diverger.</p>
 */
public enum CurrencySource {
    /** Recalcul des points de defi. Le delta peut etre negatif : defi retire, points revus. */
    CHALLENGE(true),
    /** Fin d'une partie du jeu. */
    GAME_RUN(true),
    /** Duel PvP. Un duel perdu retire des tokens, mais pas de PE. */
    PVP_BATTLE(true),
    /** Recompense de collection de cartes. */
    COLLECTION_REWARD(true),
    /** Ajustement manuel d'un administrateur, dans un sens ou dans l'autre. */
    ADMIN_ADJUSTMENT(true),
    /** Achat d'un pack. Une depense : aucun PE. */
    PACK_PURCHASE(false),
    /** Pixel bonus de Place, paye en PE. */
    PLACE_BONUS_PIXEL(false);

    private final boolean earnsEventPoints;

    CurrencySource(boolean earnsEventPoints) {
        this.earnsEventPoints = earnsEventPoints;
    }

    /**
     * Les PE qui accompagnent un mouvement de tokens de cette source.
     *
     * <p>Un pour un, deux pour un sur les defis. Le retrait d'un defi retire ses
     * PE avec lui — sans cela, valider puis retirer le meme defi en boucle
     * fabriquerait des PE a partir de rien. Un duel perdu, lui, n'est pas un
     * retrait de gain mais une mise : il ne coute aucun PE.</p>
     */
    public long eventPointsFor(long tokenDelta) {
        if (!earnsEventPoints || tokenDelta == 0) {
            return 0;
        }
        return switch (this) {
            case CHALLENGE -> Math.multiplyExact(tokenDelta, 2);
            case PVP_BATTLE -> Math.max(0, tokenDelta);
            default -> tokenDelta;
        };
    }
}
