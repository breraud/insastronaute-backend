package fr.insastronaute.webapi.auth.passkey;

import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quand proposer la passkey.
 *
 * <p>Ce n'est qu'une suggestion de securite : la faire trop souvent agace, ne jamais la
 * faire la rend inutile, et rien d'autre n'en depend. D'ou une regle qu'on peut lire
 * d'un coup d'oeil, et ces quatre cas qui la fixent.</p>
 */
class PasskeySuggestionTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Test
    void aFreshAccountIsAsked() {
        assertThat(user().isPasskeySuggestionDue(NOW)).isTrue();
    }

    @Test
    void anAnsweredAccountIsNeverAskedAgain() {
        AppUser user = user();
        user.setPasskeyPromptAnsweredAt(NOW.minus(Duration.ofDays(30)));

        assertThat(user.isPasskeySuggestionDue(NOW)).isFalse();
    }

    @Test
    void aSnoozedAccountIsLeftAloneUntilTheDelayPasses() {
        AppUser user = user();
        user.setPasskeyPromptSnoozedUntil(NOW.plus(Duration.ofHours(6)));

        assertThat(user.isPasskeySuggestionDue(NOW)).isFalse();
        assertThat(user.isPasskeySuggestionDue(NOW.plus(Duration.ofHours(7)))).isTrue();
    }

    /**
     * Une reponse definitive l'emporte sur un report en cours : quelqu'un qui a dit
     * « plus tard » puis a enregistre sa passkey ne doit pas revoir la carte le
     * lendemain.
     */
    @Test
    void answeringWinsOverAPendingSnooze() {
        AppUser user = user();
        user.setPasskeyPromptSnoozedUntil(NOW.minus(Duration.ofHours(1)));
        user.setPasskeyPromptAnsweredAt(NOW);

        assertThat(user.isPasskeySuggestionDue(NOW)).isFalse();
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setKeycloakSub("sub");
        user.setEmail("joueur@example.test");
        return user;
    }
}
