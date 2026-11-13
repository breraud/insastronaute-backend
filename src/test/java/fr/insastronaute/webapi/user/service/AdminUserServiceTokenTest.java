package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.dto.AdminUserDto;
import fr.insastronaute.webapi.auth.keycloak.KeycloakAccountGateway;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifie que l'ajustement du solde de tokens journalise le geste, et seulement
 * quand il est accepte.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTokenTest {

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
                cardTeamRepository
        );

        player = new AppUser();
        player.setId(UUID.randomUUID());
    }

    @Test
    void addingTokensWritesAGrantedEventWithTheResultingBalance() {
        player.setTokens(100L);
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        AdminUserDto result = service.updateTokenBalance(player.getId(), 50L, "sub-admin");

        assertThat(player.getTokens()).isEqualTo(150L);
        assertThat(result.tokens()).isEqualTo(150L);
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.TOKENS_GRANTED), details.capture());
        assertThat(details.getValue())
                .containsEntry("delta", 50L)
                .containsEntry("balance", 150L);
    }

    @Test
    void removingTokensWritesARevokedEvent() {
        player.setTokens(100L);
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));

        AdminUserDto result = service.updateTokenBalance(player.getId(), -30L, "sub-admin");

        assertThat(player.getTokens()).isEqualTo(70L);
        assertThat(result.tokens()).isEqualTo(70L);
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.TOKENS_REVOKED), any());
    }

    @Test
    void aRefusedAdjustmentWritesNothing() {
        player.setTokens(10L);
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> service.updateTokenBalance(player.getId(), -50L, "sub-admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le solde de tokens ne peut pas devenir négatif.")
                .satisfies(exception -> assertThat(((BadRequestException) exception).getFieldErrors())
                        .containsExactly(new ApiErrorResponse.FieldViolation(
                                "tokensDelta", "Le retrait dépasse le solde disponible.")));

        verify(journal, never()).record(any(), any(), any(), any());
        assertThat(player.getTokens()).isEqualTo(10L);
    }

    @Test
    void aNullAdjustmentIsRefused() {
        assertThatThrownBy(() -> service.updateTokenBalance(player.getId(), 0L, "sub-admin"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anOverflowingAdjustmentIsRefused() {
        player.setTokens(Long.MAX_VALUE);
        when(userRepository.findByIdForUpdate(player.getId())).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> service.updateTokenBalance(player.getId(), 1L, "sub-admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La variation de tokens est invalide.");

        verify(journal, never()).record(any(), any(), any(), any());
    }
}
