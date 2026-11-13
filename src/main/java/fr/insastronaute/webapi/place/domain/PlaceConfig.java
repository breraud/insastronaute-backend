package fr.insastronaute.webapi.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "place_configs")
public class PlaceConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    @SuppressWarnings("FieldCanBeLocal") // Read by JPA through field access.
    private Short id = SINGLETON_ID;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "canvas_width", nullable = false)
    private int canvasWidth;

    @Column(name = "canvas_height", nullable = false)
    private int canvasHeight;

    @Column(name = "base_cooldown_ms", nullable = false)
    private long baseCooldownMs;

    @Column(name = "challenge_cooldown_ms", nullable = false)
    private long challengeCooldownMs;

    @Column(name = "challenge_ratio", nullable = false)
    private int challengeRatio;

    /** Prix du premier pixel bonus acheté, en jetons. */
    @Column(name = "bonus_pixel_price", nullable = false)
    private long bonusPixelPrice = 30;

    /** Ce que chaque achat ajoute au prix du suivant. */
    @Column(name = "bonus_pixel_price_increment", nullable = false)
    private long bonusPixelPriceIncrement = 1;

    /**
     * Afficher l'auteur d'un pixel au survol.
     *
     * <p>L'auteur est enregistré dans tous les cas — sans quoi rien ne serait
     * modérable. Ce réglage ne décide que de le montrer aux autres joueurs.</p>
     */
    @Column(name = "show_pixel_authors", nullable = false)
    private boolean showPixelAuthors = true;

    /**
     * Ce que le front montre de Place, independamment de la fenetre.
     *
     * <p>AUTO par defaut : c'est le comportement d'avant ce reglage, et le
     * deploiement ne doit rien faire apparaitre a personne.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_mode", nullable = false, length = 10)
    private PlaceVisibilityMode visibilityMode = PlaceVisibilityMode.AUTO;

    /** La fresque des departements est-elle ouverte, a cote de la fresque libre ? */
    @Column(name = "department_canvas_enabled", nullable = false)
    private boolean departmentCanvasEnabled = false;

    /**
     * L'ecran de resultats remplace Place une fois la fresque terminee.
     *
     * <p>Interrupteur a part plutot que deduit de la fenetre : la fin d'un evenement
     * est une heure, la publication d'un palmares est une decision. Les organisateurs
     * verifient la fresque avant de figer un classement, et le rangement de
     * l'evenement — {@code active} a faux, mode masque — n'emporte pas les resultats
     * avec lui.</p>
     */
    @Column(name = "results_visible", nullable = false)
    private boolean resultsVisible = false;

    public Short getId() { return id; }
    public boolean isActive() { return active; }

    /**
     * L'evenement est-il ouvert a cet instant ?
     *
     * <p>{@link #isActive()} ne suffit pas : un evenement peut etre arme sans que sa
     * fenetre soit commencee, ou l'avoir depassee. Les deux endroits qui s'en soucient --
     * le refus de poser un pixel et la visibilite de l'entree dans le front -- doivent
     * lire la meme regle, sinon la tuile s'affiche sur un evenement qui refuse les
     * pixels, ou disparait alors qu'il les accepte.</p>
     */
    public boolean isOpenAt(Instant now) {
        // Masque vaut aussi ferme : sans cela, un evenement retire du front
        // resterait posable par quiconque garde le lien.
        return active
                && visibilityMode != PlaceVisibilityMode.HIDDEN
                && startDate != null && endDate != null
                && !now.isBefore(startDate) && now.isBefore(endDate);
    }

    /**
     * L'onglet doit-il apparaitre dans le front a cet instant ?
     *
     * <p>Visible n'implique pas ouvert : SHOWN montre un canevas ferme, sur
     * lequel la pose reste refusee par {@link #isOpenAt(Instant)}.</p>
     */
    public boolean isVisibleAt(Instant now) {
        return switch (visibilityMode) {
            case SHOWN -> true;
            case HIDDEN -> false;
            case AUTO -> isOpenAt(now);
        };
    }
    public PlaceVisibilityMode getVisibilityMode() { return visibilityMode; }
    public boolean isDepartmentCanvasEnabled() { return departmentCanvasEnabled; }

    /**
     * Faut-il montrer les resultats plutot que la fresque a cet instant ?
     *
     * <p>Avant la fin il n'y a rien a classer : un palmares publie pendant que les
     * pixels bougent encore serait faux la seconde d'apres.</p>
     */
    public boolean showsResultsAt(Instant now) {
        return resultsVisible && endDate != null && !now.isBefore(endDate);
    }

    public boolean isResultsVisible() { return resultsVisible; }

    public void setResultsVisible(boolean resultsVisible) { this.resultsVisible = resultsVisible; }
    public void setDepartmentCanvasEnabled(boolean departmentCanvasEnabled) { this.departmentCanvasEnabled = departmentCanvasEnabled; }
    public void setVisibilityMode(PlaceVisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode == null ? PlaceVisibilityMode.AUTO : visibilityMode;
    }
    public void setActive(boolean active) { this.active = active; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public int getCanvasWidth() { return canvasWidth; }
    public void setCanvasWidth(int canvasWidth) { this.canvasWidth = canvasWidth; }
    public int getCanvasHeight() { return canvasHeight; }
    public void setCanvasHeight(int canvasHeight) { this.canvasHeight = canvasHeight; }
    public long getBaseCooldownMs() { return baseCooldownMs; }
    public void setBaseCooldownMs(long baseCooldownMs) { this.baseCooldownMs = baseCooldownMs; }
    public long getBonusPixelPrice() { return bonusPixelPrice; }
    public void setBonusPixelPrice(long bonusPixelPrice) { this.bonusPixelPrice = Math.max(0, bonusPixelPrice); }
    public long getBonusPixelPriceIncrement() { return bonusPixelPriceIncrement; }
    public void setBonusPixelPriceIncrement(long bonusPixelPriceIncrement) {
        this.bonusPixelPriceIncrement = Math.max(0, bonusPixelPriceIncrement);
    }
    public long getChallengeCooldownMs() { return challengeCooldownMs; }
    public void setChallengeCooldownMs(long challengeCooldownMs) { this.challengeCooldownMs = challengeCooldownMs; }
    public int getChallengeRatio() { return challengeRatio; }
    public void setChallengeRatio(int challengeRatio) { this.challengeRatio = challengeRatio; }
    public boolean isShowPixelAuthors() { return showPixelAuthors; }
    public void setShowPixelAuthors(boolean showPixelAuthors) { this.showPixelAuthors = showPixelAuthors; }
}
