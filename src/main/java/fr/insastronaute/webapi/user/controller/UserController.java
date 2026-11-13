package fr.insastronaute.webapi.user.controller;

import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.service.CollectionPointsCalculator;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.auth.dto.SessionUserResponse;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.dto.ProofRetentionDto;
import fr.insastronaute.webapi.user.dto.UserProfileDto;
import fr.insastronaute.webapi.user.dto.UserProfileUpdateDto;
import fr.insastronaute.webapi.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserSynchronizationService synchronizationService;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final CardCollectionRepository cardCollectionRepository;
    private final CollectionPointsCalculator collectionPointsCalculator;
    private final GoofyCardService goofyCardService;

    public UserController(
            UserSynchronizationService synchronizationService,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            CardCollectionRepository cardCollectionRepository,
            CollectionPointsCalculator collectionPointsCalculator,
            GoofyCardService goofyCardService
    ) {
        this.synchronizationService = synchronizationService;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.cardCollectionRepository = cardCollectionRepository;
        this.collectionPointsCalculator = collectionPointsCalculator;
        this.goofyCardService = goofyCardService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the authenticated user's synchronized identity and granted roles.")
    public SessionUserResponse getMyProfile(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        List<String> roles = oidcUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        boolean isFirstScan = user.getFirstScan();
        if (isFirstScan) {
            user.setFirstScan(false);
            user = userRepository.save(user);
        }

        return new SessionUserResponse(
                user.getId(),
                user.getKeycloakSub(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                isFirstScan
        );
    }

    @PostMapping("/me/reset-first-scan")
    @Operation(summary = "Reset first scan", description = "Resets the first scan boolean to true for the authenticated user.")
    public void resetFirstScan(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        user.setFirstScan(true);
        userRepository.save(user);
    }

    @GetMapping("/me/proof-retention")
    @Operation(
            summary = "Read the proof retention choice",
            description = "Tells whether the authenticated user asked for their proof files to be deleted once a challenge is validated.")
    public ProofRetentionDto getProofRetention(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return new ProofRetentionDto(user.isPurgeProofsAfterValidation());
    }

    @PutMapping("/me/proof-retention")
    @Operation(
            summary = "Set the proof retention choice",
            description = "Remembers the choice for every future proof, and applies it to the ones still awaiting a decision.")
    public ProofRetentionDto setProofRetention(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody ProofRetentionDto dto
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        // Une position de principe, pas une case a cocher a chaque envoi : elle
        // vaut pour les preuves a venir comme pour celles encore en attente.
        user.setPurgeProofsAfterValidation(dto.purgeAfterValidation());
        AppUser saved = userRepository.save(user);
        return new ProofRetentionDto(saved.isPurgeProofsAfterValidation());
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Returns the authenticated user's editable profile data.")
    public UserProfileDto getProfile(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return profileFrom(user);
    }

    /**
     * Le genre ne se choisit qu'une fois.
     *
     * <p>Ce champ n'est pas cosmetique : pour un defi marque sensible, un moderateur
     * qui n'est pas administrateur ne peut traiter que les soumissions de son propre
     * genre (voir ChallengeService#canModerateSubmission). Tant qu'il restait
     * librement modifiable, un moderateur pouvait basculer le sien pour s'ouvrir les
     * preuves sensibles d'une cible precise, puis le remettre.</p>
     *
     * <p>La borne est l'horodatage pose au choix du genre. Les comptes anterieurs a
     * l'accueil ne l'ont pas : ils gardent un choix, un seul, apres quoi la voie de
     * correction est administrative. Une requete qui renvoie le genre deja
     * enregistre reste acceptee, pour qu'un front qui repousse le profil entier ne
     * se prenne pas un refus sur un champ qu'il n'a pas touche.</p>
     */
    @PatchMapping("/profile")
    @Operation(summary = "Update user profile", description = "Updates editable profile fields for the authenticated user.")
    public UserProfileDto updateProfile(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody UserProfileUpdateDto dto
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);

        boolean alreadyChosen = user.getOnboardingGenderChosenAt() != null;
        boolean changesGender = user.getGender() != dto.gender();

        if (alreadyChosen && changesGender) {
            throw new CodedBadRequestException(
                    "GENDER_ALREADY_CHOSEN",
                    "Ton genre a deja ete enregistre. Rapproche-toi d'un respo pour le corriger.");
        }

        if (!alreadyChosen) {
            user.setOnboardingGenderChosenAt(TimeTravelContext.instantNow());
        }
        user.setGender(dto.gender());
        AppUser savedUser = userRepository.save(user);
        return profileFrom(savedUser);
    }

    private UserProfileDto profileFrom(AppUser user) {
        String department = user.getDepartment() == null
                ? null
                : departmentRepository.findById(user.getDepartment()).map(Department::getShortName).orElse(null);

        long virtual = collectionPoints(user, CardCollectionType.VIRTUAL);
        long physical = collectionPoints(user, CardCollectionType.PHYSICAL);
        return UserProfileDto.from(user, department, virtual, physical);
    }

    private long collectionPoints(AppUser user, CardCollectionType type) {
        CardCollectionSettings settings = collectionPointsCalculator.settings();
        // La carte bonus est hors collection : la lecture du deck la garde pour
        // l'afficher, le score doit s'en passer.
        Optional<UUID> bonusCardId = goofyCardService.configuredCardId();
        return cardCollectionRepository.findByUserIdAndCollectionType(user.getId(), type)
                .stream()
                .filter(collection -> bonusCardId.filter(collection.getCard().getId()::equals).isEmpty())
                .mapToLong(collection -> collectionPointsCalculator.points(collection, settings))
                .sum();
    }
}
