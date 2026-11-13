package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTypeMetadata;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.dto.AdminChallengeDto;
import fr.insastronaute.webapi.challenge.dto.AdminChallengePageDto;
import fr.insastronaute.webapi.challenge.dto.AdminChallengeOptionDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateResultDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeDeletionImpactDto;
import fr.insastronaute.webapi.challenge.dto.DeletedChallengeDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/challenges")
@Tag(name = "Admin Challenges")
public class AdminChallengeController {

    private final ChallengeService challengeService;
    private final AutoChallengeTypeRegistry autoChallengeTypeRegistry;

    public AdminChallengeController(
            ChallengeService challengeService,
            AutoChallengeTypeRegistry autoChallengeTypeRegistry) {
        this.challengeService = challengeService;
        this.autoChallengeTypeRegistry = autoChallengeTypeRegistry;
    }

    @GetMapping
    @Operation(summary = "List challenges", description = "Returns all challenges for administration, including disabled or otherwise non-player-visible entries.")
    public AdminChallengePageDto getAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String validation,
            @RequestParam(defaultValue = "START_DATE_DESC") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return challengeService.getChallengesPage(q, status, category, validation, sort, page, size);
    }

    @GetMapping("/ids")
    @Operation(summary = "List matching challenge IDs", description = "Returns IDs of challenges matching filters for bulk operations.")
    public List<UUID> getIds(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String validation
    ) {
        return challengeService.getChallengeIds(q, status, category, validation);
    }

    @GetMapping("/options")
    @Operation(summary = "List lightweight challenge prerequisite options")
    public List<AdminChallengeOptionDto> getOptions() {
        return challengeService.getChallengeOptions();
    }

    @GetMapping("/auto-types")
    @Operation(summary = "List automatic challenge types", description = "Returns automatic challenge type metadata for administration.")
    public List<AutoChallengeTypeMetadata> getAutoTypes() {
        return autoChallengeTypeRegistry.metadata();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a challenge", description = "Creates a new challenge and returns the administrative representation.")
    public AdminChallengeDto create(@Valid @RequestBody ChallengeCreateDto dto) {
        return challengeService.createChallenge(dto);
    }

    @DeleteMapping("/{challengeId}/proofs")
    @Operation(
            summary = "Erase the proofs of a challenge",
            description = "Deletes the stored files of every decided submission — validated or rejected. Pending "
                    + "submissions keep theirs: without its photo, a submission cannot be judged and would block "
                    + "the moderation queue. Irreversible."
    )
    public fr.insastronaute.webapi.challenge.dto.ChallengeProofsPurgeResultDto purgeProofs(
            @PathVariable UUID challengeId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return new fr.insastronaute.webapi.challenge.dto.ChallengeProofsPurgeResultDto(
                challengeService.purgeChallengeProofs(challengeId, oidcUser.getSubject()));
    }

    @PostMapping("/bulk-update")
    @Operation(summary = "Bulk update challenges", description = "Applies common changes to a selection of challenges.")
    public ChallengeBulkUpdateResultDto bulkUpdate(
            @Valid @RequestBody ChallengeBulkUpdateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.bulkUpdateChallenges(dto, oidcUser.getSubject());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a challenge", description = "Replaces the editable fields of an existing challenge.")
    public AdminChallengeDto update(
            @PathVariable UUID id,
            @Valid @RequestBody ChallengeCreateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.updateChallenge(id, dto, oidcUser.getSubject());
    }

    /**
     * Ce que la suppression emporterait, avant de la demander.
     *
     * <p>Lu a l'ouverture de la confirmation : le chiffre n'a de sens qu'au moment
     * ou on hesite, et le charger pour chaque ligne de la liste coûterait une
     * requete par defi affiche.</p>
     */
    @GetMapping("/{id}/deletion-impact")
    @Operation(summary = "What deleting this challenge would erase",
            description = "Players credited and proofs stored, to show before confirming.")
    public ChallengeDeletionImpactDto deletionImpact(@PathVariable UUID id) {
        return challengeService.deletionImpact(id);
    }

    /**
     * La corbeille : les defis supprimes, et ce qu'une restauration rendrait.
     *
     * <p>Avant {@code /{id}} dans ce fichier par lisibilite seule : Spring fait
     * passer un segment litteral avant un gabarit, quel que soit l'ordre.</p>
     */
    @GetMapping("/deleted")
    @Operation(summary = "Deleted challenges", description = "Challenges that can still be restored.")
    public List<DeletedChallengeDto> deleted() {
        return challengeService.listDeletedChallenges();
    }

    /**
     * Remettre un defi supprime, avec ses preuves et les points qu'elles donnaient.
     */
    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a deleted challenge",
            description = "Brings back the challenge, its proofs and the points they awarded.")
    public AdminChallengeDto restore(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.restoreChallenge(id, oidcUser.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a challenge", description = "Deletes an existing challenge by id.")
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        challengeService.deleteChallenge(id, oidcUser.getSubject());
    }
}
