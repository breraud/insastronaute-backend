package fr.insastronaute.webapi.common.adminaudit.controller;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionEventDto;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionScope;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionTypeDto;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionEventQueryService;
import fr.insastronaute.webapi.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La lecture du registre des gestes d'administration.
 *
 * <p><strong>Aucun verbe d'ecriture n'est monte ici, et aucun ne doit
 * l'etre.</strong> La table est en ajout seul : elle n'est ecrite que de
 * l'interieur, par les services qui accomplissent le geste, et une ligne qu'on
 * pourrait modifier ou supprimer ne vaudrait rien comme preuve.
 * {@code AdminActionEventControllerTest} epingle cette propriete plutot que de
 * la laisser a la memoire de chacun.</p>
 *
 * <p>Le prefixe {@code /api/admin/**} est reserve au role {@code ADMIN} par
 * {@code SecurityConfig} ; ce chemin tombe dans cette regle generale, sans
 * exception a lui.</p>
 */
@RestController
@RequestMapping("/api/admin/action-events")
@Tag(name = "Admin Action Events")
public class AdminActionEventController {

    private final AdminActionEventQueryService queryService;

    public AdminActionEventController(AdminActionEventQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "Read the administration action register",
            description = """
                    Returns the append-only administration action register, newest first. \
                    All filters are optional and typed; there is no free-text search. \
                    'action' is repeatable. 'from' is inclusive, 'to' is exclusive, both ISO-8601 instants. \
                    'scope' selects nominal rows (those naming one player), global rows (those naming a \
                    scope and a number of affected players, with no target), or both. \
                    Global rows are never returned when 'targetUserId' is set: they have no target. \
                    Backfilled rows whose original moderator could not be resolved have no actor, \
                    and are likewise never returned when 'actorUserId' is set. \
                    The register is always sorted newest first; any 'sort' parameter is ignored."""
    )
    public PageResponse<AdminActionEventDto> search(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) List<AdminActionType> action,
            @RequestParam(defaultValue = "ALL") AdminActionScope scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return queryService.search(actorUserId, targetUserId, action, scope, from, to, pageable);
    }

    /**
     * Le catalogue des types d'action.
     *
     * <p>La barre de filtres le lit plutot que d'en garder une copie : la
     * liste a deja gagne trois valeurs depuis sa creation, et une copie cote
     * panel les aurait manquees en silence. Meme forme que
     * {@code GET /api/admin/achievements/types}, qui sert deja son catalogue
     * de phrases-cles au formulaire qui les propose.</p>
     */
    @GetMapping("/types")
    @Operation(
            summary = "List the available action types",
            description = "Returns every action type the register can hold, with whether it is a global-scope type."
    )
    public List<AdminActionTypeDto> actionTypes() {
        return queryService.actionTypes();
    }
}
