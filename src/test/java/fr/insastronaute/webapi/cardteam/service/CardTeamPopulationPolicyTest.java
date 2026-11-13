package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardTeamPopulationPolicyTest {

    @Mock RealmRoleDirectory roleDirectory;
    private CardTeamPopulationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CardTeamPopulationPolicy(roleDirectory);
    }

    @Test
    void acceptsPlayersTogetherAndTeamStaffTogether() {
        AppUser playerOne = user("player-1");
        AppUser playerTwo = user("player-2");
        when(roleDirectory.isKnown()).thenReturn(true);
        when(roleDirectory.teamStaff()).thenReturn(Set.of("sub-admin", "sub-limited"));

        policy.requireHomogeneous(List.of(playerOne, playerTwo));
        policy.requireHomogeneous(List.of(user("sub-admin"), user("sub-limited")));
    }

    @Test
    void rejectsStudentAndLimitedPopulationsWithAStableCode() {
        when(roleDirectory.isKnown()).thenReturn(true);
        when(roleDirectory.teamStaff()).thenReturn(Set.of("sub-admin", "sub-limited", "sub-both"));

        assertThatThrownBy(() -> policy.requireHomogeneous(List.of(user("sub-student"), user("sub-limited"))))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode())
                        .isEqualTo("CARD_TEAM_STAFF_PLAYER_MIX"));
    }

    @Test
    void treatsStudentAndLimitedOverlapAsTeamStaff() {
        when(roleDirectory.isKnown()).thenReturn(true);
        when(roleDirectory.teamStaff()).thenReturn(Set.of("sub-admin", "sub-limited", "sub-both"));

        policy.requireHomogeneous(List.of(user("sub-admin"), user("sub-both")));
        assertThatThrownBy(() -> policy.requireHomogeneous(List.of(user("sub-student"), user("sub-both"))))
                .isInstanceOf(CodedConflictException.class);
    }

    @Test
    void failsClosedWhileTheRoleDirectoryIsUnknown() {
        when(roleDirectory.isKnown()).thenReturn(false);

        assertThatThrownBy(() -> policy.requireHomogeneous(List.of(user("player-1"))))
                .isInstanceOf(CardTeamRoleDirectoryUnavailableException.class);
    }

    private static AppUser user(String sub) {
        AppUser user = new AppUser();
        user.setKeycloakSub(sub);
        return user;
    }
}
