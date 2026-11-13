package fr.insastronaute.webapi.qrlink.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.qrlink.domain.QrLinkConfig;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigUpdateDto;
import fr.insastronaute.webapi.qrlink.repository.QrLinkConfigRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QrLinkServiceTest {

    private static final String LANDING_URL = "https://frontend.example.com/scan";
    private static final String PUBLIC_URL = "https://api.example.com/q";

    @Test
    void routesToExternalTargetWhenChallengeModeIsDisabled() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        QrLinkConfig config = config(false, "https://example.org/programme");
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config));

        QrLinkService service = service(configRepository, mock(UserRepository.class));

        assertThat(service.resolveQrDestination()).isEqualTo("https://example.org/programme");
    }

    @Test
    void routesToLandingWhenChallengeModeIsEnabled() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config(true, "https://example.org")));

        assertThat(service(configRepository, mock(UserRepository.class)).resolveQrDestination()).isEqualTo(LANDING_URL);
    }

    @Test
    void routesToDynamicLandingWhenScanLandingUrlIsEmpty() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config(true, "https://example.org")));

        AppSecurityProperties securityProperties = new AppSecurityProperties();
        securityProperties.setFrontendBaseUrl("https://frontend.example.com");

        QrLinkService service = new QrLinkService(configRepository, mock(UserRepository.class), securityProperties, "", "", mock(AutoChallengeTriggerPublisher.class));

        assertThat(service.resolveQrDestination()).isEqualTo("https://frontend.example.com/scan");
        assertThat(service.getConfig().publicUrl()).isEqualTo("https://frontend.example.com/q");
    }

    @Test
    void rejectsNonHttpsRedirectTargets() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        QrLinkConfig config = config(false, "https://example.org");
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service(configRepository, mock(UserRepository.class))
                .updateConfig(new QrLinkConfigUpdateDto("http://example.org", false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'URL de redirection doit être une URL HTTPS absolue.");
    }

    @Test
    void firstActiveScanPermanentlyMarksUser() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AppUser user = user(false);
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config(true, "https://example.org")));
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(user));

        var result = service(configRepository, userRepository).scan("player-sub");

        assertThat(result.status()).isEqualTo("SCANNED");
        assertThat(user.getScanned()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void repeatedActiveScanIsIdempotent() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AppUser user = user(true);
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config(true, "https://example.org")));
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(user));

        var result = service(configRepository, userRepository).scan("player-sub");

        assertThat(result.status()).isEqualTo("ALREADY_SCANNED");
        verify(userRepository, never()).save(user);
    }

    @Test
    void inactiveScanBehavesAsMissingAndDoesNotLoadUser() {
        QrLinkConfigRepository configRepository = mock(QrLinkConfigRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(configRepository.findById(QrLinkConfig.SINGLETON_ID)).thenReturn(Optional.of(config(false, "https://example.org")));

        assertThatThrownBy(() -> service(configRepository, userRepository).scan("player-sub"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Page de scan introuvable.");
        verify(userRepository, never()).findWithLockByKeycloakSub("player-sub");
    }

    private static QrLinkService service(QrLinkConfigRepository configRepository, UserRepository userRepository) {
        AppSecurityProperties securityProperties = new AppSecurityProperties();
        securityProperties.setFrontendBaseUrl("https://frontend.example.com");
        return new QrLinkService(configRepository, userRepository, securityProperties, LANDING_URL, PUBLIC_URL, mock(AutoChallengeTriggerPublisher.class));
    }

    private static QrLinkConfig config(boolean challengeModeEnabled, String redirectUrl) {
        QrLinkConfig config = new QrLinkConfig();
        config.setRedirectUrl(redirectUrl);
        config.setChallengeModeEnabled(challengeModeEnabled);
        return config;
    }

    private static AppUser user(boolean scanned) {
        AppUser user = new AppUser();
        user.setScanned(scanned);
        return user;
    }
}
