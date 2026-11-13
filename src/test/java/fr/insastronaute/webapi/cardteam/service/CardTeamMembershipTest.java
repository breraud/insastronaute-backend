package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardTeamMembershipTest {

    @Mock CardTeamRepository teamRepository;
    @Mock CardTeamMemberRepository memberRepository;
    @Mock CardTeamJoinRequestRepository joinRequestRepository;
    @Mock CardTeamInvitationRepository invitationRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock CardTeamPopulationPolicy populationPolicy;

    private CardTeamMembership membership;

    private static AppUser user(String name) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        return user;
    }

    private static CardTeam team() {
        CardTeam team = new CardTeam();
        team.setId(UUID.randomUUID());
        team.setName("Les Commeres");
        return team;
    }

    private static CardTeamMember memberOf(CardTeam team, AppUser user) {
        CardTeamMember member = new CardTeamMember();
        member.setTeam(team);
        member.setUser(user);
        return member;
    }

    @BeforeEach
    void setUp() {
        membership = new CardTeamMembership(teamRepository, memberRepository, joinRequestRepository, invitationRepository, friendshipRepository, populationPolicy);
    }

    /**
     * La taille d'une equipe est une decision de produit, pas un detail d'implementation.
     *
     * <p>Les autres tests utilisent la constante symboliquement et passeraient donc quelle
     * que soit sa valeur. Celui-ci epingle le nombre lui-meme, pour qu'il ne bouge pas
     * sans que le texte affiche aux joueurs bouge avec lui.</p>
     */
    @Test
    void aCardTeamHoldsFourPlayers() {
        assertThat(CardTeam.MAX_MEMBERS).isEqualTo(4);
    }

    @Test
    void aFullTeamRefusesOneMoreArrival() {
        CardTeam team = team();
        when(memberRepository.countByTeamId(team.getId())).thenReturn((long) CardTeam.MAX_MEMBERS);

        assertThatThrownBy(() -> membership.requireCapacity(team))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode()).isEqualTo("CARD_TEAM_FULL"));
    }

    /**
     * L'autre cote de la borne. Sans ce cas, requireCapacity pourrait refuser des
     * l'avant-dernier membre sans qu'aucun test ne proteste : celui du cas plein
     * passerait toujours, et une equipe a trois refuserait son quatrieme en silence.
     * Ce chemin n'etait couvert qu'indirectement, par les tests du service.
     */
    @Test
    void aTeamOneShortOfTheMaximumAcceptsExactlyOneMoreArrival() {
        CardTeam team = team();
        when(memberRepository.countByTeamId(team.getId())).thenReturn((long) CardTeam.MAX_MEMBERS - 1);

        membership.requireCapacity(team);
    }

    @Test
    void aMergeThatWouldOverflowNamesTheResultingSize() {
        CardTeam team = team();
        when(memberRepository.countByTeamId(team.getId())).thenReturn(3L);

        assertThatThrownBy(() -> membership.requireCapacityFor(team, 3))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> {
                    CodedConflictException conflict = (CodedConflictException) thrown;
                    assertThat(conflict.getCode()).isEqualTo("CARD_TEAM_MERGE_TOO_LARGE");
                    assertThat(conflict.getMessage()).contains("6").contains("4");
                });
    }

    @Test
    void aMergeThatFitsExactlyIsAccepted() {
        CardTeam team = team();
        when(memberRepository.countByTeamId(team.getId())).thenReturn(2L);

        membership.requireCapacityFor(team, 2);
    }

    @Test
    void attachingAMemberBefriendsEveryPlayerAlreadyThere() {
        CardTeam team = team();
        AppUser joining = user("Nouveau");
        AppUser first = user("Un");
        AppUser second = user("Deux");
        when(memberRepository.findByTeamIdOrderByJoinedAt(team.getId()))
                .thenReturn(List.of(memberOf(team, first), memberOf(team, second)));
        when(friendshipRepository.findBetween(any(), any())).thenReturn(Optional.empty());

        membership.attachMember(team, joining);

        verify(memberRepository).save(any(CardTeamMember.class));
        verify(friendshipRepository, times(2)).save(any(Friendship.class));
    }

    @Test
    void attachingAMemberValidatesPopulationBeforeWriting() {
        CardTeam team = team();
        AppUser joining = user("Staff");
        AppUser existing = user("Joueur");
        when(memberRepository.findByTeamIdOrderByJoinedAt(team.getId()))
                .thenReturn(List.of(memberOf(team, existing)));
        org.mockito.Mockito.doThrow(new CodedConflictException("CARD_TEAM_STAFF_PLAYER_MIX", "mix"))
                .when(populationPolicy).requireHomogeneous(any());

        assertThatThrownBy(() -> membership.attachMember(team, joining))
                .isInstanceOf(CodedConflictException.class);

        verify(memberRepository, never()).save(any());
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void anExistingAcceptedFriendshipIsLeftAlone() {
        AppUser joining = user("Nouveau");
        AppUser existing = user("Un");
        Friendship accepted = new Friendship();
        accepted.setRequester(joining);
        accepted.setAddressee(existing);
        accepted.setStatus(FriendshipStatus.ACCEPTED);
        ReflectionTestUtils.setField(accepted, "createdAt", java.time.Instant.EPOCH);
        when(friendshipRepository.findBetween(joining.getId(), existing.getId())).thenReturn(Optional.of(accepted));

        membership.ensureAcceptedFriendship(joining, existing);

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void anEmptyTeamIsDeletedAndAPopulatedOneIsNot() {
        UUID emptyId = UUID.randomUUID();
        UUID populatedId = UUID.randomUUID();
        when(memberRepository.countByTeamId(emptyId)).thenReturn(0L);
        when(memberRepository.countByTeamId(populatedId)).thenReturn(2L);

        membership.deleteIfEmpty(emptyId);
        membership.deleteIfEmpty(populatedId);

        verify(teamRepository).deleteById(emptyId);
        verify(teamRepository, never()).deleteById(populatedId);
    }

    @Test
    void anEmptiedTeamHasItsPendingRequestsAndInvitationsClearedFirst() {
        UUID emptyId = UUID.randomUUID();
        when(memberRepository.countByTeamId(emptyId)).thenReturn(0L);

        membership.deleteIfEmpty(emptyId);

        // Sans ce menage explicite, deleteById se heurterait a une demande ou une
        // invitation encore adressee a cette equipe : ca ne casse rien en production,
        // ou le schema porte ON DELETE CASCADE, mais le schema H2 des tests, genere
        // depuis les entites, n'en porte pas.
        verify(joinRequestRepository).deleteByTeamId(emptyId);
        verify(invitationRepository).deleteByTeamId(emptyId);
        verify(teamRepository).deleteById(emptyId);
    }
}
