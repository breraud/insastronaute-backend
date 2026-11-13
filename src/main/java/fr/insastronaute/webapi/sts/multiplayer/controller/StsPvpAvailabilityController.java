package fr.insastronaute.webapi.sts.multiplayer.controller;

import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpAvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dit si le mode PvP est ouvert, et le dit même quand il ne l'est pas.
 *
 * <p>C'est tout l'intérêt : {@link StsPvpController} disparaît sous condition, donc un client
 * qui interroge n'importe laquelle de ses routes pour savoir reçoit un 404 qu'il ne peut pas
 * distinguer d'une panne. Celle-ci n'est jamais conditionnelle et répond toujours, ce qui laisse
 * le menu principal cacher son bouton en connaissance de cause plutôt qu'au jugé.
 *
 * <p>Volontairement dans le même paquet et sous le même préfixe que le contrôleur qu'elle
 * décrit : c'est la seule route PvP qui survit à l'extinction, et la ranger ailleurs ferait
 * oublier qu'elle en fait partie.
 */
@RestController
@RequestMapping("/api/sts/pvp")
@Tag(name = "STS PVP")
public class StsPvpAvailabilityController {

    private final boolean pvpEnabled;

    public StsPvpAvailabilityController(@Value("${app.sts.pvp.enabled:true}") boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    @GetMapping("/availability")
    @Operation(summary = "Whether STS PVP is open on this server",
            description = "Reports app.sts.pvp.enabled. Always answers, including when PVP is off and its other routes are absent.")
    public StsPvpAvailabilityResponse availability() {
        return new StsPvpAvailabilityResponse(pvpEnabled);
    }
}
