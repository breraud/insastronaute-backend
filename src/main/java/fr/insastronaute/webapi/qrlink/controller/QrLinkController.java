package fr.insastronaute.webapi.qrlink.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigDto;
import fr.insastronaute.webapi.qrlink.dto.QrScanResultDto;
import fr.insastronaute.webapi.qrlink.service.QrLinkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class QrLinkController {

    private final QrLinkService qrLinkService;
    private final UserSynchronizationService userSynchronizationService;

    public QrLinkController(QrLinkService qrLinkService, UserSynchronizationService userSynchronizationService) {
        this.qrLinkService = qrLinkService;
        this.userSynchronizationService = userSynchronizationService;
    }

    @GetMapping("/q")
    public ResponseEntity<Void> followQrLink(@AuthenticationPrincipal OidcUser oidcUser) {
        QrLinkConfigDto config = qrLinkService.getConfig();
        String destination = config.challengeModeEnabled()
                ? qrLinkService.resolveQrDestination()
                : config.redirectUrl();
        if (config.challengeModeEnabled() && oidcUser == null) {
            destination = "/oauth2/authorization/keycloak?redirect_uri="
                    + URLEncoder.encode(destination, StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(destination).toASCIIString())
                .build();
    }

    @PostMapping("/api/scan")
    public QrScanResultDto scan(@AuthenticationPrincipal OidcUser oidcUser) {
        qrLinkService.ensureScanAvailable();
        if (oidcUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        userSynchronizationService.syncWithKeycloak(oidcUser);
        return qrLinkService.scan(oidcUser.getSubject());
    }
}
