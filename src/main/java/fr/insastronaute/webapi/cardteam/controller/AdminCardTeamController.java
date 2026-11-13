package fr.insastronaute.webapi.cardteam.controller;

import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.cardteam.dto.AdminCardTeamDto;
import fr.insastronaute.webapi.cardteam.dto.CardTeamMergeRequest;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/card-teams")
@Tag(name = "Admin Card Teams")
public class AdminCardTeamController {

    private final AdminCardTeamService service;

    public AdminCardTeamController(AdminCardTeamService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search card teams", description = "Paginated team list, filtered by team name or member display name.")
    public PageResponse<AdminCardTeamDto> search(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20)
            @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.search(query, pageable);
    }

    @GetMapping("/{teamId}/collection")
    @Operation(summary = "Get a card team's physical collection", description = "Returns the aggregated extension-free physical collection and score.")
    public CardDeckDto getCollection(@PathVariable UUID teamId) {
        return service.getCollection(teamId);
    }

    @PostMapping("/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Move a player into a team", description = "Detaches the player from their current team, if any, and attaches them to this one.")
    public void moveMember(@PathVariable UUID teamId, @PathVariable UUID userId) {
        service.moveMember(teamId, userId);
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a player from their team")
    public void removeMember(@PathVariable UUID userId) {
        service.removeMember(userId);
    }

    @PostMapping("/{teamId}/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Merge a team into this one", description = "Moves every member of the source team here, then deletes the source.")
    public void merge(@PathVariable UUID teamId, @Valid @RequestBody CardTeamMergeRequest request) {
        service.merge(teamId, request.sourceTeamId());
    }

    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a team", description = "Frees its members and removes its pending requests and invitations.")
    public void delete(@PathVariable UUID teamId) {
        service.delete(teamId);
    }
}
