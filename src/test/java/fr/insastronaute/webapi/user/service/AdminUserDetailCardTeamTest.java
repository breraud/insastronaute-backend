package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.dto.AdminCardTeamDto;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.dto.AdminUserDetailDto;
import fr.insastronaute.webapi.auth.keycloak.KeycloakAccountGateway;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifie le seam reel entre AdminUserService et AdminCardTeamService.
 *
 * <p>La version precedente de ce test bootait un contexte Spring complet, mockait
 * AdminCardTeamService, et n'affirmait rien que la forme de AdminUserDetailDto via
 * reflexion : ni le bean injecte ni son mock n'etaient utilises. Rien ne verifiait
 * que getUserDetail appelait bien findForUser, si bien que supprimer cet appel et
 * renvoyer null n'aurait fait echouer aucun test.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminUserDetailCardTeamTest {

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

    private AdminUserService service;

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
                cardTeamRepository
        );
    }

    @Test
    void theDetailCarriesTheUsersCardTeamFromTheDedicatedService() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        AdminCardTeamDto team = new AdminCardTeamDto(
                UUID.randomUUID(), "Les Commeres", 1, 6, List.of(), null, null
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(submissionRepository.findDetailedByUserIdOrParticipantUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(friendshipRepository.findByUserIdForAdmin(userId)).thenReturn(List.of());
        when(cardCollectionRepository.findByUserIdForAdmin(userId)).thenReturn(List.of());
        when(cardInstanceService.searchAdmin(null, null, userId, null, null)).thenReturn(List.of());
        when(adminCardTeamService.findForUser(userId)).thenReturn(team);

        AdminUserDetailDto detail = service.getUserDetail(userId);

        verify(adminCardTeamService).findForUser(userId);
        assertThat(detail.cardTeam()).isSameAs(team);
    }
}
