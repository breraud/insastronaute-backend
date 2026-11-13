package fr.insastronaute.webapi.user;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.dto.UserProfileDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileDtoTest {

    @Test
    void fromIncludesNeutralGenderByDefault() {
        AppUser user = new AppUser();
        user.setDisplayName("Test User");

        UserProfileDto dto = UserProfileDto.from(user, "ITI", 0L, 0L);

        assertThat(dto.fullName()).isEqualTo("Test User");
        assertThat(dto.gender()).isEqualTo(Gender.NEUTRAL);
        assertThat(dto.department()).isEqualTo("ITI");
    }

    /**
     * Le profil garde la couleur de la filiere et ajoute la marque.
     *
     * <p>Un apprenti reste un GC : c'est son departement qui colore ses puces partout
     * ou la couleur porte du sens. La pastille PERF se pose a cote, elle ne remplace
     * rien.</p>
     */
    @Test
    void anApprenticeKeepsTheirParentDepartmentAndCarriesThePerfMark() {
        AppUser user = new AppUser();
        user.setDisplayName("Apprentie");
        user.setPerf(true);

        UserProfileDto dto = UserProfileDto.from(user, "GC", 0L, 0L);

        assertThat(dto.department()).isEqualTo("GC");
        assertThat(dto.perf()).isTrue();
    }

    @Test
    void genderParsesCaseInsensitiveApiValues() {
        assertThat(Gender.from("Male")).isEqualTo(Gender.MALE);
        assertThat(Gender.from("female")).isEqualTo(Gender.FEMALE);
        assertThat(Gender.from("NEUTRAL")).isEqualTo(Gender.NEUTRAL);
    }

    @Test
    void genderRejectsUnsupportedApiValues() {
        assertThatThrownBy(() -> Gender.from("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
