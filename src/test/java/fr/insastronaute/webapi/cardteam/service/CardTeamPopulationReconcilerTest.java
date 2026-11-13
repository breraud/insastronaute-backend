package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamInvitation;
import fr.insastronaute.webapi.cardteam.domain.CardTeamJoinRequest;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardTeamPopulationReconcilerTest {

    @Mock CardTeamMemberRepository memberRepository;
    @Mock CardTeamJoinRequestRepository joinRequestRepository;
    @Mock CardTeamInvitationRepository invitationRepository;
    @Mock CardTeamMembership membership;
    @Mock CardTeamPopulationPolicy policy;
    private CardTeamPopulationReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new CardTeamPopulationReconciler(
                memberRepository, joinRequestRepository, invitationRepository, membership, policy
        );
    }

    @Test
    void skipsWithoutTouchingTeamsWhenRolesAreUnknown() {
        when(policy.knownStaff()).thenThrow(new CardTeamRoleDirectoryUnavailableException());

        reconciler.reconcile();

        verifyNoInteractions(memberRepository, joinRequestRepository, invitationRepository, membership);
    }

    @Test
    void removesStaffFromMixedTeamsAndCancelsIncompatiblePendingOperations() {
        AppUser staff = user("staff");
        AppUser player = user("player");
        CardTeam mixed = team();
        CardTeamMember staffMember = member(mixed, staff);
        CardTeamMember playerMember = member(mixed, player);
        CardTeamJoinRequest incompatibleRequest = request(mixed, staff);
        CardTeamInvitation incompatibleInvitation = invitation(mixed, player, staff);
        CardTeamInvitation incompatibleFoundingInvitation = invitation(null, staff, player);
        when(policy.knownStaff()).thenReturn(Set.of("staff"));
        when(memberRepository.findAllWithTeam()).thenReturn(List.of(staffMember, playerMember));
        when(membership.lockTeam(mixed.getId())).thenReturn(mixed);
        when(memberRepository.findByTeamIdOrderByJoinedAt(mixed.getId()))
                .thenReturn(List.of(staffMember, playerMember))
                .thenReturn(List.of(playerMember));
        when(joinRequestRepository.findAllByStatus(CardTeamRequestStatus.PENDING))
                .thenReturn(List.of(incompatibleRequest));
        when(invitationRepository.findAllByStatus(CardTeamRequestStatus.PENDING))
                .thenReturn(List.of(incompatibleInvitation, incompatibleFoundingInvitation));

        reconciler.reconcile();

        verify(memberRepository).delete(staffMember);
        verify(memberRepository, never()).delete(playerMember);
        assertThat(incompatibleRequest.getStatus()).isEqualTo(CardTeamRequestStatus.CANCELLED);
        assertThat(incompatibleInvitation.getStatus()).isEqualTo(CardTeamRequestStatus.CANCELLED);
        assertThat(incompatibleFoundingInvitation.getStatus()).isEqualTo(CardTeamRequestStatus.CANCELLED);
        assertThat(incompatibleRequest.getRespondedAt()).isNotNull();
        verify(joinRequestRepository).save(incompatibleRequest);
        verify(invitationRepository).save(incompatibleInvitation);
        verify(invitationRepository).save(incompatibleFoundingInvitation);
    }

    private static AppUser user(String sub) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub(sub);
        return user;
    }

    private static CardTeam team() {
        CardTeam team = new CardTeam();
        team.setId(UUID.randomUUID());
        team.setName("Mixte");
        return team;
    }

    private static CardTeamMember member(CardTeam team, AppUser user) {
        CardTeamMember member = new CardTeamMember();
        member.setTeam(team);
        member.setUser(user);
        return member;
    }

    private static CardTeamJoinRequest request(CardTeam team, AppUser requester) {
        CardTeamJoinRequest request = new CardTeamJoinRequest();
        request.setTeam(team);
        request.setRequester(requester);
        request.setStatus(CardTeamRequestStatus.PENDING);
        return request;
    }

    private static CardTeamInvitation invitation(CardTeam team, AppUser inviter, AppUser invited) {
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setTeam(team);
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        invitation.setStatus(CardTeamRequestStatus.PENDING);
        return invitation;
    }
}
