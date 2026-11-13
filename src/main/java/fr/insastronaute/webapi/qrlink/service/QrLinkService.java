package fr.insastronaute.webapi.qrlink.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.qrlink.domain.QrLinkConfig;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigDto;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigUpdateDto;
import fr.insastronaute.webapi.qrlink.dto.QrScanResultDto;
import fr.insastronaute.webapi.qrlink.repository.QrLinkConfigRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
public class QrLinkService {

    private final QrLinkConfigRepository configRepository;
    private final UserRepository userRepository;
    private final AppSecurityProperties securityProperties;
    private final String scanLandingUrl;
    private final String publicUrl;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    public QrLinkService(
            QrLinkConfigRepository configRepository,
            UserRepository userRepository,
            AppSecurityProperties securityProperties,
            @Value("${app.qr-link.scan-landing-url:}") String scanLandingUrl,
            @Value("${app.qr-link.public-url:}") String publicUrl,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher
    ) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
        this.scanLandingUrl = scanLandingUrl;
        this.publicUrl = publicUrl;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
    }

    @Transactional(readOnly = true)
    public QrLinkConfigDto getConfig() {
        return toDto(requireConfig());
    }

    @Transactional
    public QrLinkConfigDto updateConfig(QrLinkConfigUpdateDto update) {
        String redirectUrl = validatedHttpsUrl(update.redirectUrl());
        QrLinkConfig config = requireConfig();
        config.setRedirectUrl(redirectUrl);
        config.setChallengeModeEnabled(update.challengeModeEnabled());
        return toDto(configRepository.save(config));
    }

    @Transactional(readOnly = true)
    public String resolveQrDestination() {
        QrLinkConfig config = requireConfig();
        if (Boolean.TRUE.equals(config.getChallengeModeEnabled())) {
            if (scanLandingUrl != null && !scanLandingUrl.isBlank()) {
                return scanLandingUrl;
            }
            return securityProperties.getFrontendBaseUrl() + "/scan";
        }
        return config.getRedirectUrl();
    }

    @Transactional
    public QrScanResultDto scan(String keycloakSub) {
        ensureScanAvailable();

        AppUser user = userRepository.findWithLockByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        if (Boolean.TRUE.equals(user.getScanned())) {
            return new QrScanResultDto("ALREADY_SCANNED", "Ce code avait déjà été scanné.");
        }

        user.setScanned(true);
        userRepository.save(user);
        autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.QR_SCANNED);
        return new QrScanResultDto("SCANNED", "Code scanné avec succès.");
    }

    @Transactional(readOnly = true)
    public void ensureScanAvailable() {
        if (!Boolean.TRUE.equals(requireConfig().getChallengeModeEnabled())) {
            throw new NotFoundException("Page de scan introuvable.");
        }
    }

    private QrLinkConfig requireConfig() {
        return configRepository.findById(QrLinkConfig.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Configuration du lien QR introuvable."));
    }

    private QrLinkConfigDto toDto(QrLinkConfig config) {
        String effectivePublicUrl = (publicUrl != null && !publicUrl.isBlank())
                ? publicUrl
                : securityProperties.getFrontendBaseUrl() + "/q";
        return new QrLinkConfigDto(effectivePublicUrl, config.getRedirectUrl(), Boolean.TRUE.equals(config.getChallengeModeEnabled()));
    }

    private static String validatedHttpsUrl(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            URI uri = URI.create(normalized);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("L'URL de redirection doit être une URL HTTPS absolue.");
        }
    }
}
