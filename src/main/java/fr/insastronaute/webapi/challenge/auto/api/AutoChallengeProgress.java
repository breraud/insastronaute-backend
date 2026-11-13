package fr.insastronaute.webapi.challenge.auto.api;

/**
 * Où en est un joueur sur une phrase clé.
 *
 * Calculé à la lecture plutôt que stocké : il ne peut donc pas diverger de ce
 * qu'il décrit, et une phrase ajoutée aujourd'hui rend compte de l'historique
 * d'hier.
 */
public record AutoChallengeProgress(long current, long target) {

    public AutoChallengeProgress {
        if (target < 1) {
            throw new IllegalArgumentException("Une phrase clé vise au moins 1");
        }
        current = Math.max(0L, Math.min(current, target));
    }

    public static AutoChallengeProgress of(long current, long target) {
        return new AutoChallengeProgress(current, target);
    }

    public boolean completed() {
        return current >= target;
    }

    /** 0..100, arrondi vers le bas : un défi inachevé n'affiche jamais 100 %. */
    public int percentage() {
        return completed() ? 100 : (int) Math.floor((current * 100.0) / target);
    }
}
