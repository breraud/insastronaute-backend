package fr.insastronaute.webapi.sts.controller;

import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import fr.insastronaute.webapi.sts.dto.StsDebugAvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dit si le combat de débogage est ouvert à celui qui demande.
 *
 * <p>{@link StsDebugCombatController} n'existe que sous condition, et son chemin n'est ouvert
 * qu'à l'encadrement : un joueur ordinaire qui trouve le bouton reçoit un 404 ou un 403 selon
 * ce qui manque. Le client ne pouvait le découvrir qu'en lançant un combat, ce que la route
 * fait pour de bon — elle réécrit la run de l'appelant. D'où cette question-ci, qui ne fait
 * rien et se pose avant d'afficher quoi que ce soit.
 *
 * <p>Répond toujours, même quand le débogage est fermé : « la route n'existe pas » et « elle
 * existe et vous n'y avez pas droit » sont deux réponses différentes, et un client qui ne
 * distingue pas les deux ne peut rien en dire d'utile dans ses journaux.
 */
@RestController
@RequestMapping("/api/sts/debug/combat")
@Tag(name = "STS Debug")
public class StsDebugAvailabilityController {

    /**
     * Les autorités auxquelles SecurityConfig ouvre {@code /api/sts/runs/*}{@code /debug/**}.
     *
     * <p>Dérivées de la liste elle-même plutôt que recopiées : deux listes de rôles censées
     * s'accorder finissent par diverger, et celle qui mentirait ici est celle qui décide de
     * montrer un bouton menant à un 403.
     */
    private static final Set<String> STAFF_AUTHORITIES = Arrays.stream(SecurityConfig.STAFF_ROLES)
            .map(role -> "ROLE_" + role)
            .collect(Collectors.toUnmodifiableSet());

    private final boolean debugCombatEnabled;

    public StsDebugAvailabilityController(
            @Value("${app.sts.debug.combat.enabled:false}") boolean debugCombatEnabled
    ) {
        this.debugCombatEnabled = debugCombatEnabled;
    }

    @GetMapping("/availability")
    @Operation(summary = "Whether hand-picked STS combats are open to the caller",
            description = "Reports the app.sts.debug.combat.enabled flag and whether the caller holds a staff role.")
    public StsDebugAvailabilityResponse availability(Authentication authentication) {
        boolean allowed = authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(STAFF_AUTHORITIES::contains);

        return new StsDebugAvailabilityResponse(
                debugCombatEnabled, allowed, debugCombatEnabled && allowed);
    }
}
