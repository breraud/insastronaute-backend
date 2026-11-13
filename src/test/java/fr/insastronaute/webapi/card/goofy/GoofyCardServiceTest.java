package fr.insastronaute.webapi.card.goofy;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoofyCardServiceTest {

    @Mock
    private GoofyCardSettingsRepository settingsRepository;

    @Mock
    private GoofyCardRedemptionRepository redemptionRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardInstanceRepository instanceRepository;

    @InjectMocks
    private GoofyCardService service;

    private GoofyCardSettings configured(String code) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte goofy");
        GoofyCardSettings settings = new GoofyCardSettings();
        settings.setCard(card);
        settings.setCode(code);
        settings.setEnabled(true);
        return settings;
    }

    @Test
    void reconnaitLeCodeQuelsQueSoientLaCasseEtLesTirets() {
        // Le champ de saisie remet le code en forme pendant la frappe : comparer
        // les chaînes telles quelles rejetterait un code pourtant juste.
        when(settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID))
                .thenReturn(Optional.of(configured("goofy-goofy-goofy")));

        assertThat(service.cardFor("GOOFYGOOFYGOOFY")).isPresent();
        assertThat(service.cardFor("goofy-GOOFY-goofy")).isPresent();
    }

    @Test
    void ignoreUnAutreCode() {
        when(settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID))
                .thenReturn(Optional.of(configured("GOOFY-GOOFY-GOOFY")));

        assertThat(service.cardFor("ABCDE-FGHJK-MNPQR")).isEmpty();
    }

    @Test
    void neDonneRienTantQueLeReglageEstDesactive() {
        GoofyCardSettings settings = configured("GOOFY-GOOFY-GOOFY");
        settings.setEnabled(false);
        when(settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertThat(service.cardFor("GOOFY-GOOFY-GOOFY")).isEmpty();
    }

    @Test
    void refuseUneSecondeObtention() {
        UUID userId = UUID.randomUUID();
        when(redemptionRepository.existsById(userId)).thenReturn(true);

        assertThatThrownBy(() -> service.markRedeemed(userId))
                .isInstanceOf(ConflictException.class);
        verify(redemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void refuseDActiverSansCarteNiCode() {
        // Activé sans carte, le code ne donnerait rien : autant le dire à
        // l'enregistrement plutôt que de laisser un joueur saisir dans le vide.
        assertThatThrownBy(() -> service.update(null, "GOOFY", true))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "  ", true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void genereUnCodeValideEtUnique() {
        when(instanceRepository.existsByPublicId(any())).thenReturn(false);

        String code = service.generateUniqueCode();

        assertThat(code).matches("^[A-NP-Z1-9]{5}-[A-NP-Z1-9]{5}-[A-NP-Z1-9]{5}$");
        verify(instanceRepository).existsByPublicId(code);
    }

    @Test
    void echoueSiImpossibleDeGenererUnCodeUnique() {
        when(instanceRepository.existsByPublicId(any())).thenReturn(true);

        assertThatThrownBy(() -> service.generateUniqueCode())
                .isInstanceOf(ConflictException.class)
                .hasMessage("Impossible de générer un identifiant public unique.");
    }

    @Test
    void refuseUnCodeDejaAttribueAUneCartePhysique() {
        when(instanceRepository.existsByPublicId("ABCDE-FGHJK-MNPQR")).thenReturn(true);

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "ABCDE-FGHJK-MNPQR", true))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ce code est déjà attribué à une carte physique imprimée.");
    }

    @Test
    void matchesCodeVerifieLaCorrespondanceSansCasseNiTirets() {
        GoofyCardSettings settings = configured("GOOFY-GOOFY-GOOFY");
        when(settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertThat(service.matchesCode("goofygoofygoofy")).isTrue();
        assertThat(service.matchesCode("GOOFY-GOOFY-GOOFY")).isTrue();
        assertThat(service.matchesCode("AUTRE-CODE-CARTE")).isFalse();
    }

    @Test
    void matchesCodeRenvoieFauxSiCodeNulOuVide() {
        assertThat(service.matchesCode(null)).isFalse();
        assertThat(service.matchesCode("")).isFalse();
        assertThat(service.matchesCode("   ")).isFalse();

        GoofyCardSettings settings = configured(null);
        when(settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        assertThat(service.matchesCode("GOOFY")).isFalse();
    }
}
