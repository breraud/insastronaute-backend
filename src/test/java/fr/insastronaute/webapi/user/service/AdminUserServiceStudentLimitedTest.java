package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakAccountGateway;
import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.auth.sessionreset.UserSessionRevocationService;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.dto.AdminUserDto;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceStudentLimitedTest {

    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ChallengeSubmissionRepository submissionRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock CardCollectionRepository cardCollectionRepository;
    @Mock CardInstanceService cardInstanceService;
    @Mock CardService cardService;
    @Mock ProofStorageService proofStorageService;
    @Mock ChallengeService challengeService;
    @Mock AdminCardTeamService adminCardTeamService;
    @Mock AdminActionJournal journal;
    @Mock KeycloakAccountGateway accountGateway;
    @Mock AdminActionEventRepository adminActionEventRepository;
    @Mock CardTeamRepository cardTeamRepository;
    @Mock KeycloakRealmRoleGateway roleGateway;
    @Mock RealmRoleDirectory roleDirectory;
    @Mock UserSessionRevocationService sessionRevocationService;

    private AdminUserService service;
    private AppUser player;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository,
                departmentRepository,
                submissionRepository,
                friendshipRepository,
                cardCollectionRepository,
                cardInstanceService,
                cardService,
                proofStorageService,
                challengeService,
                adminCardTeamService,
                journal,
                accountGateway,
                adminActionEventRepository,
                cardTeamRepository,
                roleGateway,
                roleDirectory,
                sessionRevocationService
        );

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub("player-sub");
        player.setOnboardingStatus(OnboardingStatus.VALIDATED);
        player.setDisplayName("Player 1");
    }

    @Test
    void switchingToStudentLimitedAssignsRoleAndRevokesSessionsWithoutKeycloakLogout() {
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));

        AdminUserDto dto = service.updateStudentLimited(player.getId(), true, "admin-sub");

        assertThat(dto.studentLimited()).isTrue();
        assertThat(player.getHiddenFromLeaderboards()).isTrue();

        verify(roleGateway).assignRealmRole("player-sub", RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        verify(roleGateway).removeRealmRole("player-sub", RealmRoleDirectory.STUDENT_ROLE);
        verify(roleDirectory).invalidateCache();
        verify(roleGateway, never()).logoutUser(any());
        verify(sessionRevocationService).revokeAll("player-sub");
        verify(userRepository).save(player);
        verify(journal).record(eq("admin-sub"), eq(player.getId()), eq(AdminActionType.ONBOARDING_VALIDATED), any());
    }

    @Test
    void switchingBackToStandardStudentAssignsRoleAndRevokesSessionsWithoutKeycloakLogout() {
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));

        AdminUserDto dto = service.updateStudentLimited(player.getId(), false, "admin-sub");

        assertThat(dto.studentLimited()).isFalse();

        verify(roleGateway).assignRealmRole("player-sub", RealmRoleDirectory.STUDENT_ROLE);
        verify(roleGateway).removeRealmRole("player-sub", RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        verify(roleDirectory).invalidateCache();
        verify(roleGateway, never()).logoutUser(any());
        verify(sessionRevocationService).revokeAll("player-sub");
        verify(userRepository).save(player);
    }

    @Test
    void switchingRejectedUserToLimitedValidatesOnboarding() {
        player.setOnboardingStatus(OnboardingStatus.REJECTED);
        player.setOnboardingRejectionReason("Email invalide");
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));

        AdminUserDto dto = service.updateStudentLimited(player.getId(), true, "admin-sub");

        assertThat(dto.studentLimited()).isTrue();
        assertThat(player.getOnboardingStatus()).isEqualTo(OnboardingStatus.VALIDATED);
        assertThat(player.getOnboardingRejectionReason()).isNull();

        verify(roleGateway, never()).logoutUser(any());
        verify(sessionRevocationService).revokeAll("player-sub");
    }
}
