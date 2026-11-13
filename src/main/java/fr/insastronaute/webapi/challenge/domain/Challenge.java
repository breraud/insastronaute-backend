package fr.insastronaute.webapi.challenge.domain;

import com.fasterxml.jackson.databind.JsonNode;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.proof.domain.ProofType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "points_reward", nullable = false)
    private Integer pointsReward;

    /**
     * La recompense se divise-t-elle entre les membres d'une soumission ?
     *
     * <p>Pour les objectifs atteints collectivement -- une collection d'equipe, qui est
     * mutualisee -- ou chaque membre recevrait sinon la mise entiere, l'equipe encaissant
     * autant de fois la recompense qu'elle compte de joueurs.</p>
     *
     * <p>Un point valant un token, ce drapeau partage les deux d'un coup.</p>
     */
    @Column(name = "reward_shared", nullable = false)
    private boolean rewardShared;

    @Column(nullable = false, length = 50)
    private String category = ChallengeCategory.DEFAULT_NAME;

    /**
     * Les autres categories du defi, en plus de la principale.
     *
     * <p>Un defi photo peut etre aussi un defi de groupe : il doit alors se trouver
     * sous les deux onglets. La principale reste a part parce qu'elle est celle
     * qu'on affiche, et parce que deux fronts deployes separement la lisent
     * deja.</p>
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "challenge_extra_categories",
            joinColumns = @JoinColumn(name = "challenge_id")
    )
    @Column(name = "category", nullable = false, length = 50)
    private Set<String> extraCategories = new HashSet<>();

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    /**
     * Date de parution : le défi n'apparaît pas dans la liste d'un joueur avant elle.
     * Nulle veut dire « paru ». Sans effet sur la soumission, que bornent startDate
     * et endDate.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "global_quota")
    private Integer globalQuota;

    @Column(name = "validation_mode", nullable = false, length = 20)
    private String validationMode; // Idéalement, on en fera un Enum: ValidationMode.ADMIN ou ValidationMode.AUTO

    /**
     * Ce que le defi attend comme preuve : MEDIA ou TEXT.
     *
     * <p>Il ne vivait que sur la soumission, donc le joueur l'annoncait lui-meme
     * et rien ne verifiait qu'il correspondait a la demande. Porte par le defi,
     * il commande le formulaire cote application et sert de reference au controle
     * a la soumission.</p>
     */
    @Column(name = "proof_type", nullable = false, length = 20)
    private String proofType = ProofType.MEDIA.name();

    @Column(name = "auto_challenge_type", length = 100)
    private String autoChallengeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auto_challenge_parameters", columnDefinition = "jsonb")
    private JsonNode autoChallengeParameters;

    @Column(nullable = false)
    private Boolean sensitive = false;

    /**
     * Ce defi accepte-t-il qu'un ajustement descende sous zero ?
     *
     * <p>Un malus ne peut qu'annuler le gain, jamais mordre dessous — sauf ici. Le
     * cas existe, une promo sanctionnee gardant ses points d'un cote et en perdant
     * de l'autre, mais il ne doit pas etre le defaut : sur un defi ordinaire, un
     * malus mal saisi retirerait des points a qui n'a rien demande.</p>
     */
    @Column(name = "allow_negative_reward", nullable = false)
    private Boolean allowNegativeReward = false;

    /**
     * Un defi que la promo releve une fois, pour elle.
     *
     * <p>Ses points vont au departement de celui qui a porte la preuve, et a lui
     * seul : le joueur n'en tire rien pour son propre classement. En echange, le
     * defi compte comme fait pour toute la promo des qu'un de ses membres l'a
     * valide, et se ferme aux autres des la premiere preuve envoyee.</p>
     */
    @Column(nullable = false)
    private boolean promo = false;

    @Column(name = "min_group_size", nullable = false)
    private Integer minGroupSize = 1;

    @Column(name = "max_group_size", nullable = false)
    private Integer maxGroupSize = 1;

    // --- LE CIBLAGE DÉPARTEMENT ---

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "challenge_target_departments",
            joinColumns = @JoinColumn(name = "challenge_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private Set<Department> targetDepartments = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "challenge_prerequisites",
            joinColumns = @JoinColumn(name = "challenge_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_challenge_id")
    )
    private Set<Challenge> prerequisiteChallenges = new HashSet<>();

    // --- DATES AUTOMATIQUES ---

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Quand ce defi a ete supprime, s'il l'a ete.
     *
     * <p>La suppression n'efface rien : elle pose cette date. Le defi sort des
     * listes, ses preuves cessent de donner des points -- la requete de recalcul
     * joint le defi et ecarte ceux-ci -- et remettre le champ a NULL rend les deux.
     * Un clic de trop n'est plus definitif.</p>
     *
     * <p>Le filtrage est declare sur le depot, methode par methode, plutot qu'en
     * {@code @SQLRestriction} sur cette classe. Deliberement : une restriction
     * globale rendrait aussi le defi invisible depuis {@code submission.getChallenge()},
     * et les ecrans de moderation qui veulent precisement dire « ce defi a ete
     * supprime » ne pourraient plus le lire. Elle ecarterait aussi les defis
     * supprimes du renommage de categorie, qui doit les suivre sous peine de les
     * restaurer avec une categorie qui n'existe plus.</p>
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Qui a supprime, pour que la corbeille le dise sans ouvrir le registre. */
    @Column(name = "deleted_by_keycloak_sub", length = 64)
    private String deletedByKeycloakSub;

    @PrePersist
    protected void onCreate() {
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedByKeycloakSub() {
        return deletedByKeycloakSub;
    }

    public void setDeletedByKeycloakSub(String deletedByKeycloakSub) {
        this.deletedByKeycloakSub = deletedByKeycloakSub;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isRewardShared() { return rewardShared; }
    public void setRewardShared(boolean rewardShared) { this.rewardShared = rewardShared; }

    public Integer getPointsReward() {
        return pointsReward;
    }

    public void setPointsReward(Integer pointsReward) {
        this.pointsReward = pointsReward;
    }

    public String getCategory() {
        return category;
    }

    public Set<String> getExtraCategories() {
        return extraCategories;
    }

    public void setExtraCategories(Set<String> extraCategories) {
        this.extraCategories = extraCategories == null ? new HashSet<>() : new HashSet<>(extraCategories);
    }

    /**
     * Toutes les categories du defi, la principale en tete.
     *
     * <p>C'est cette liste que lisent le filtrage et les comportements lies a une
     * categorie : un defi quotidien l'est autant s'il porte DAILY en second.</p>
     */
    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        categories.add(category);
        extraCategories.stream().filter(extra -> !extra.equals(category)).sorted().forEach(categories::add);
        return categories;
    }

    public boolean hasCategory(String name) {
        return name != null && (name.equals(category) || extraCategories.contains(name));
    }

    public void setCategory(String category) {
        this.category = category == null || category.isBlank() ? ChallengeCategory.DEFAULT_NAME : category.trim();
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Integer getGlobalQuota() {
        return globalQuota;
    }

    public void setGlobalQuota(Integer globalQuota) {
        this.globalQuota = globalQuota;
    }

    public String getValidationMode() {
        return validationMode;
    }

    public void setValidationMode(String validationMode) {
        this.validationMode = validationMode;
    }

    public String getProofType() {
        return proofType;
    }

    public void setProofType(String proofType) {
        this.proofType = proofType;
    }

    public String getAutoChallengeType() {
        return autoChallengeType;
    }

    public void setAutoChallengeType(String autoChallengeType) {
        this.autoChallengeType = autoChallengeType;
    }

    public JsonNode getAutoChallengeParameters() {
        return autoChallengeParameters;
    }

    public void setAutoChallengeParameters(JsonNode autoChallengeParameters) {
        this.autoChallengeParameters = autoChallengeParameters;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public Boolean getAllowNegativeReward() { return allowNegativeReward; }

    public void setAllowNegativeReward(Boolean allowNegativeReward) {
        this.allowNegativeReward = allowNegativeReward != null && allowNegativeReward;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive != null && sensitive;
    }

    public boolean isPromo() {
        return promo;
    }

    public void setPromo(Boolean promo) {
        this.promo = Boolean.TRUE.equals(promo);
    }

    public Integer getMinGroupSize() {
        return minGroupSize;
    }

    public void setMinGroupSize(Integer minGroupSize) {
        this.minGroupSize = minGroupSize == null ? 1 : minGroupSize;
    }

    public Integer getMaxGroupSize() {
        return maxGroupSize;
    }

    public void setMaxGroupSize(Integer maxGroupSize) {
        this.maxGroupSize = maxGroupSize == null ? 1 : maxGroupSize;
    }

    public Set<Department> getTargetDepartments() {
        return targetDepartments;
    }

    public void setTargetDepartments(Set<Department> targetDepartments) {
        this.targetDepartments = targetDepartments == null ? new HashSet<>() : new HashSet<>(targetDepartments);
    }

    public Set<Challenge> getPrerequisiteChallenges() {
        return prerequisiteChallenges;
    }

    public void setPrerequisiteChallenges(Set<Challenge> prerequisiteChallenges) {
        this.prerequisiteChallenges = prerequisiteChallenges == null ? new HashSet<>() : new HashSet<>(prerequisiteChallenges);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
