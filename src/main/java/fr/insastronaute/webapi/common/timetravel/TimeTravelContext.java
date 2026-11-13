package fr.insastronaute.webapi.common.timetravel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Supplier;

public class TimeTravelContext {

    private static final ThreadLocal<Instant> FORCED_TIME = new ThreadLocal<>();
    private static final ThreadLocal<UUID> FORCED_DEPARTMENT_ID = new ThreadLocal<>();

    public static void setForcedTime(Instant time) {
        FORCED_TIME.set(time);
    }

    public static Instant getForcedTime() {
        return FORCED_TIME.get();
    }

    /**
     * Le departement force, <strong>deja resolu en identifiant</strong> par
     * {@link TimeTravelFilter}.
     *
     * <p>Il porte un identifiant et non le nom de groupe Keycloak que l'en-tete
     * transporte, pour une raison de fond : une valeur qui ne designe aucun
     * departement doit etre refusee a l'entree, pas promenee dans la requete
     * jusqu'a un appelant qui ne saura qu'en faire. Quand ce champ est rempli,
     * il designe un departement qui existe.</p>
     */
    public static void setForcedDepartmentId(UUID departmentId) {
        FORCED_DEPARTMENT_ID.set(departmentId);
    }

    public static UUID getForcedDepartmentId() {
        return FORCED_DEPARTMENT_ID.get();
    }

    /**
     * Le departement a utiliser : celui qu'on force s'il y en a un, sinon le
     * vrai.
     *
     * <p>C'est <strong>le</strong> point de passage pour tout code qui lit le
     * departement du joueur courant. Lire {@code user.getDepartment()}
     * directement fonctionne - et c'est exactement le probleme : ca fonctionne
     * en ignorant silencieusement le voyage dans le temps, et personne ne s'en
     * apercoit avant d'avoir passe une soiree a chercher pourquoi le
     * changement de departement n'a aucun effet sur une page.</p>
     */
    public static UUID departmentIdOr(UUID realDepartmentId) {
        UUID forced = FORCED_DEPARTMENT_ID.get();
        return forced != null ? forced : realDepartmentId;
    }

    public static void clear() {
        FORCED_TIME.remove();
        FORCED_DEPARTMENT_ID.remove();
    }

    /**
     * Executer une action sur la vraie horloge, meme au milieu d'une requete
     * qui voyage dans le temps.
     *
     * <p>Le voyage sert a regarder, pas a ecrire. Ce qui persiste un fait - une
     * attribution automatique en premier lieu - ne doit pas le dater ni le
     * decider d'apres la date qu'un membre du staff s'est choisie : sous un
     * voyage d'un mois en avant, les defis du mois prochain paraissaient ouverts
     * et se validaient pour de vrai. L'horloge forcee est rendue a la sortie, le
     * reste de la requete continue de voyager.</p>
     */
    public static <T> T withRealTime(Supplier<T> action) {
        Instant forced = FORCED_TIME.get();
        FORCED_TIME.remove();
        try {
            return action.get();
        } finally {
            if (forced != null) {
                FORCED_TIME.set(forced);
            }
        }
    }

    // Time replacement helpers
    public static Instant instantNow() {
        Instant forced = FORCED_TIME.get();
        return forced != null ? forced : Instant.now();
    }

    public static ZonedDateTime zonedDateTimeNow() {
        Instant forced = FORCED_TIME.get();
        return forced != null ? ZonedDateTime.ofInstant(forced, ZoneId.systemDefault()) : ZonedDateTime.now();
    }

    public static OffsetDateTime offsetDateTimeNow() {
        Instant forced = FORCED_TIME.get();
        return forced != null ? OffsetDateTime.ofInstant(forced, ZoneId.systemDefault()) : OffsetDateTime.now();
    }
}
