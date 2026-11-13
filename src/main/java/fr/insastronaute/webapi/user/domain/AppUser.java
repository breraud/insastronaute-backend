package fr.insastronaute.webapi.user.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.insastronaute.webapi.department.domain.Department;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_sub", nullable = false, unique = true)
    private String keycloakSub;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender = Gender.NEUTRAL;

    /**
     * Quand ce joueur a ete supprime, ou {@code null} s'il est actif.
     *
     * <p>La ligne survit a la suppression parce que le registre d'administration
     * reference les joueurs en {@code ON DELETE RESTRICT} et doit continuer a les
     * nommer. Ce qui disparait vraiment est ailleurs : le compte Keycloak, et la
     * presence du joueur dans l'etat de jeu des autres.</p>
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "department_id")
    private UUID departmentId;

    /**
     * Le departement que le voyage dans le temps fait voir, sans toucher au vrai.
     *
     * <p>{@code @Transient} : rien n'en arrive en base. Hibernate lit les champs et non
     * les accesseurs, donc {@code getDepartment()} peut rendre celui-ci pendant que la
     * colonne garde le sien -- un {@code save()} au milieu d'une session de voyage ne
     * deplace donc personne de departement.</p>
     *
     * <p>Cette superposition remplace un objet reconstruit champ par champ. La liste de
     * copie oubliait {@code passkeyPromptAnsweredAt} et {@code passkeyPromptSnoozedUntil}
     * -- la suggestion de passkey revenait a chaque rafraichissement malgre un refus
     * enregistre -- et tout ce qu'elle oubliait etait efface par les appelants qui
     * reenregistrent l'utilisateur, l'accueil comme la mise a jour du profil. Le defaut
     * n'etait pas la liste mais le fait qu'il y en ait une.</p>
     */
    @Transient
    private UUID overriddenDepartmentId;

    /**
     * Ce joueur suit une filiere par apprentissage.
     *
     * <p>Il reste rattache a son departement parent : la coche ne deplace personne. Elle
     * change deux choses, et seulement deux. Ses evenements sont ceux de l'audience PERF
     * et non ceux de sa filiere, le ciblage etant exclusif dans les deux sens. Et il ne
     * compte dans aucun effectif de classement, alors que ses points, eux, reviennent a
     * sa filiere.</p>
     *
     * <p>Pose a la main par un administrateur : l'annuaire ne distingue pas les
     * apprentis, et rien ne remplit ce champ a la connexion.</p>
     */
    @Column(nullable = false)
    private boolean perf = false;

    @Column(nullable = false)
    private Long tokens = 0L;

    /**
     * Points Events : la monnaie des evenements, depensee par exemple sur Place.
     *
     * <p>Ils ne s'achetent pas, ils suivent les tokens gagnes — voir
     * {@link fr.insastronaute.webapi.economy.domain.CurrencySource#eventPointsFor}.</p>
     */
    @Column(name = "event_points", nullable = false)
    private long eventPoints = 0L;

    @Column(name = "virtual_collection_points", nullable = false)
    @SuppressWarnings("FieldCanBeLocal") // Read by JPA through field access.
    private Long virtualCollectionPoints = 0L;

    @Column(name = "physical_collection_points", nullable = false)
    @SuppressWarnings("FieldCanBeLocal") // Read by JPA through field access.
    private Long physicalCollectionPoints = 0L;

    @Column(name = "challenge_points", nullable = false)
    private Long challengePoints = 0L;

    /**
     * Challenge points already converted into spendable tokens. Points are recomputed
     * from scratch on every validation, so only the difference with this ledger is
     * credited; mirroring the total would wipe out whatever the player has spent.
     */
    @Column(name = "challenge_tokens_granted", nullable = false)
    private Long challengeTokensGranted = 0L;

    @Column(name = "tournament_points", nullable = false)
    private Long tournamentPoints = 0L;

    @Column(name = "place_bonus_pixels_used", nullable = false)
    private Long placeBonusPixelsUsed = 0L;

    /** Pixels bonus achetés en jetons — comptés à part de ceux gagnés aux défis. */
    @Column(name = "place_purchased_bonus_pixels", nullable = false)
    private Long placePurchasedBonusPixels = 0L;

    /** Le dernier pixel posé était-il un achat ? Deux achats d'affilée sont refusés. */
    @Column(name = "place_last_pixel_purchased", nullable = false)
    private boolean placeLastPixelPurchased = false;

    @Column(name = "hidden_from_leaderboards", nullable = false)
    private Boolean hiddenFromLeaderboards = false;

    /** Exclu du canevas — sanction locale, le reste du site reste ouvert. */
    @Column(name = "place_banned", nullable = false)
    private Boolean placeBanned = false;

    /**
     * Effacer les fichiers de ses preuves une fois le défi validé.
     *
     * <p>Une preuve est une photo de soi, souvent avec d'autres, envoyée pour
     * qu'un modérateur la regarde. Cet examen fini, elle n'a plus d'usage. Le
     * réglage est porté par le joueur et pas par la soumission : on ne redemande
     * pas à chaque envoi ce qui est une position de principe.</p>
     */
    @Column(name = "purge_proofs_after_validation", nullable = false)
    private boolean purgeProofsAfterValidation = false;

    @Column(nullable = false)
    private Boolean scanned = false;

    @Column(name = "first_scan", nullable = false)
    private Boolean firstScan = true;

    /**
     * Le moment où ce compte a ouvert le pool de cartes secrètes de STS, ou null tant qu'il ne
     * l'a pas fait.
     *
     * <p>Jouer une carte marquée {@code SecretUnlock} l'ouvre, une fois pour toutes et pour
     * toutes les runs du compte : les cartes marquées {@code Secret} n'apparaissent dans aucune
     * table de récompense avant. Le réglage est porté par le joueur et non par la run — une
     * découverte ne se reperd pas à la run suivante — et la date plutôt qu'un booléen, parce
     * qu'un « depuis quand » se répond alors sans autre table.
     */
    @Column(name = "sts_secret_cards_unlocked_at")
    private Instant stsSecretCardsUnlockedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false)
    private OnboardingStatus onboardingStatus = OnboardingStatus.NOT_STARTED;

    @Column(name = "onboarding_gender_chosen_at")
    private Instant onboardingGenderChosenAt;

    @Column(name = "onboarding_rejection_reason")
    private String onboardingRejectionReason;

    @Column(name = "onboarding_submitted_at")
    private Instant onboardingSubmittedAt;

    @Column(name = "onboarding_decided_at")
    private Instant onboardingDecidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboarding_decided_by_user_id")
    private AppUser onboardingDecidedBy;

    // --- Callbacks JPA pour gérer les dates automatiquement ---
    @PrePersist
    protected void onCreate() {
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    /**
     * Quand la suggestion de passkey a recu une reponse definitive - enregistree, ou
     * refusee. Nulle tant que la question n'a pas ete tranchee.
     */
    @Column(name = "passkey_prompt_answered_at")
    private Instant passkeyPromptAnsweredAt;

    /** Jusqu'a quand « plus tard » repousse la suggestion. */
    @Column(name = "passkey_prompt_snoozed_until")
    private Instant passkeyPromptSnoozedUntil;

    public Instant getPasskeyPromptAnsweredAt() { return passkeyPromptAnsweredAt; }
    public void setPasskeyPromptAnsweredAt(Instant at) { this.passkeyPromptAnsweredAt = at; }

    public Instant getPasskeyPromptSnoozedUntil() { return passkeyPromptSnoozedUntil; }
    public void setPasskeyPromptSnoozedUntil(Instant until) { this.passkeyPromptSnoozedUntil = until; }

    /**
     * Faut-il proposer la passkey maintenant ?
     *
     * <p>Le calcul vit ici plutot que dans le front : c'est une suggestion de securite,
     * et deux fronts qui la calculeraient chacun a leur facon finiraient par ne pas la
     * proposer aux memes personnes.</p>
     */
    public boolean isPasskeySuggestionDue(Instant now) {
        if (passkeyPromptAnsweredAt != null) {
            return false;
        }
        return passkeyPromptSnoozedUntil == null || passkeyPromptSnoozedUntil.isBefore(now);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getKeycloakSub() { return keycloakSub; }
    public void setKeycloakSub(String keycloakSub) { this.keycloakSub = keycloakSub; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender == null ? Gender.NEUTRAL : gender; }
    public UUID getDepartment() {
        return overriddenDepartmentId != null ? overriddenDepartmentId : departmentId;
    }

    /** Le departement reellement enregistre, que la superposition ne masque pas. */
    public UUID getPersistedDepartment() { return departmentId; }

    /** Faire voir un autre departement le temps d'une requete, sans rien persister. */
    public void overrideDepartment(UUID departmentId) { this.overriddenDepartmentId = departmentId; }
    public void setDepartment(UUID departmentId) { this.departmentId = departmentId; }
    public boolean isPerf() { return perf; }
    public void setPerf(boolean perf) { this.perf = perf; }
    @JsonIgnore
    public void setDepartment(Department department) { this.departmentId = department.getId(); }
    public Long getTokens() { return tokens; }
    public void setTokens(Long tokens) { this.tokens = tokens == null ? 0L : tokens; }
    public long getEventPoints() { return eventPoints; }
    public void setEventPoints(long eventPoints) { this.eventPoints = Math.max(0L, eventPoints); }

    /**
     * Credite les PE qui accompagnent un mouvement de tokens, et rend ce qui a
     * reellement ete applique.
     *
     * <p>Le solde ne passe jamais sous zero : un defi retire apres que ses PE ont
     * ete depenses ne laisse pas une dette, il remet simplement a zero.</p>
     */
    public long creditEventPointsFor(fr.insastronaute.webapi.economy.domain.CurrencySource source, long tokenDelta) {
        long wanted = source.eventPointsFor(tokenDelta);
        long before = eventPoints;
        eventPoints = Math.max(0L, Math.addExact(eventPoints, wanted));
        return eventPoints - before;
    }
    public Long getVirtualCollectionPoints() { return virtualCollectionPoints; }
    public Long getPhysicalCollectionPoints() { return physicalCollectionPoints; }
    public Long getChallengePoints() { return challengePoints; }
    public void setChallengePoints(Long challengePoints) { this.challengePoints = challengePoints == null ? 0L : challengePoints; }

    public Long getChallengeTokensGranted() { return challengeTokensGranted; }
    public void setChallengeTokensGranted(Long value) { this.challengeTokensGranted = value == null ? 0L : value; }
    public Long getTournamentPoints() { return tournamentPoints; }
    public void setTournamentPoints(Long tournamentPoints) { this.tournamentPoints = tournamentPoints == null ? 0L : tournamentPoints; }
    public Long getPlacePurchasedBonusPixels() { return placePurchasedBonusPixels; }
    public void setPlacePurchasedBonusPixels(Long placePurchasedBonusPixels) {
        this.placePurchasedBonusPixels = placePurchasedBonusPixels == null ? 0L : placePurchasedBonusPixels;
    }
    public boolean isPlaceLastPixelPurchased() { return placeLastPixelPurchased; }
    public void setPlaceLastPixelPurchased(boolean placeLastPixelPurchased) {
        this.placeLastPixelPurchased = placeLastPixelPurchased;
    }
    public Long getPlaceBonusPixelsUsed() { return placeBonusPixelsUsed; }
    public void setPlaceBonusPixelsUsed(Long placeBonusPixelsUsed) {
        this.placeBonusPixelsUsed = placeBonusPixelsUsed == null ? 0L : placeBonusPixelsUsed;
    }
    public Boolean getPlaceBanned() { return placeBanned; }
    public void setPlaceBanned(Boolean placeBanned) {
        this.placeBanned = placeBanned != null && placeBanned;
    }

    public Boolean getHiddenFromLeaderboards() { return hiddenFromLeaderboards; }
    public void setHiddenFromLeaderboards(Boolean hiddenFromLeaderboards) {
        this.hiddenFromLeaderboards = hiddenFromLeaderboards != null && hiddenFromLeaderboards;
    }
    public boolean isPurgeProofsAfterValidation() { return purgeProofsAfterValidation; }
    public void setPurgeProofsAfterValidation(boolean purgeProofsAfterValidation) {
        this.purgeProofsAfterValidation = purgeProofsAfterValidation;
    }
    public Boolean getScanned() { return scanned; }
    public void setScanned(Boolean scanned) { this.scanned = scanned != null && scanned; }
    public Boolean getFirstScan() { return firstScan; }
    public void setFirstScan(Boolean firstScan) { this.firstScan = firstScan != null && firstScan; }
    public Instant getStsSecretCardsUnlockedAt() { return stsSecretCardsUnlockedAt; }
    public void setStsSecretCardsUnlockedAt(Instant stsSecretCardsUnlockedAt) {
        this.stsSecretCardsUnlockedAt = stsSecretCardsUnlockedAt;
    }
    public boolean hasUnlockedStsSecretCards() { return stsSecretCardsUnlockedAt != null; }
    public OnboardingStatus getOnboardingStatus() { return onboardingStatus; }
    public void setOnboardingStatus(OnboardingStatus onboardingStatus) {
        this.onboardingStatus = onboardingStatus == null ? OnboardingStatus.NOT_STARTED : onboardingStatus;
    }
    public Instant getOnboardingGenderChosenAt() { return onboardingGenderChosenAt; }
    public void setOnboardingGenderChosenAt(Instant onboardingGenderChosenAt) { this.onboardingGenderChosenAt = onboardingGenderChosenAt; }
    public String getOnboardingRejectionReason() { return onboardingRejectionReason; }
    public void setOnboardingRejectionReason(String onboardingRejectionReason) { this.onboardingRejectionReason = onboardingRejectionReason; }
    public Instant getOnboardingSubmittedAt() { return onboardingSubmittedAt; }
    public void setOnboardingSubmittedAt(Instant onboardingSubmittedAt) { this.onboardingSubmittedAt = onboardingSubmittedAt; }
    public Instant getOnboardingDecidedAt() { return onboardingDecidedAt; }
    public void setOnboardingDecidedAt(Instant onboardingDecidedAt) { this.onboardingDecidedAt = onboardingDecidedAt; }
    public AppUser getOnboardingDecidedBy() { return onboardingDecidedBy; }
    public void setOnboardingDecidedBy(AppUser onboardingDecidedBy) { this.onboardingDecidedBy = onboardingDecidedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
