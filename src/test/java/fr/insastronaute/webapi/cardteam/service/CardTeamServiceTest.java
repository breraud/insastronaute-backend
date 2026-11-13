package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamInvitation;
import fr.insastronaute.webapi.cardteam.domain.CardTeamJoinRequest;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import fr.insastronaute.webapi.cardteam.dto.CardTeamStateDto;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardTeamServiceTest {

    @Mock CardTeamRepository teamRepository;
    @Mock CardTeamMemberRepository memberRepository;
    @Mock CardTeamJoinRequestRepository joinRequestRepository;
    @Mock CardTeamInvitationRepository invitationRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @Mock CardTeamPopulationPolicy populationPolicy;

    private CardTeamService service;

    @BeforeEach
    void setUp() {
        service = new CardTeamService(
                teamRepository,
                memberRepository,
                joinRequestRepository,
                invitationRepository,
                friendshipRepository,
                userRepository,
                new CardTeamMembership(teamRepository, memberRepository, joinRequestRepository, invitationRepository, friendshipRepository, populationPolicy),
                populationPolicy
        );
    }

    @Test
    void incompatibleInvitationIsRejectedBeforeItBecomesPending() {
        AppUser inviter = user("Staff");
        AppUser invited = user("Joueur");
        Friendship friendship = friendship(inviter, invited, FriendshipStatus.ACCEPTED);
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(friendshipRepository.findBetween(inviter.getId(), invited.getId())).thenReturn(Optional.of(friendship));
        org.mockito.Mockito.doThrow(new CodedConflictException("CARD_TEAM_STAFF_PLAYER_MIX", "mix"))
                .when(populationPolicy).requireHomogeneous(any());

        assertThatThrownBy(() -> service.invite(inviter, invited.getId(), "Nova"))
                .isInstanceOf(CodedConflictException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void incompatibleJoinRequestIsRejectedBeforeItBecomesPending() {
        AppUser requester = user("Staff");
        AppUser player = user("Joueur");
        CardTeam team = team("Nova");
        when(memberRepository.findByUserId(requester.getId())).thenReturn(Optional.empty());
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdOrderByJoinedAt(team.getId())).thenReturn(List.of(member(team, player)));
        org.mockito.Mockito.doThrow(new CodedConflictException("CARD_TEAM_STAFF_PLAYER_MIX", "mix"))
                .when(populationPolicy).requireHomogeneous(any());

        assertThatThrownBy(() -> service.requestJoin(requester, team.getId()))
                .isInstanceOf(CodedConflictException.class);
        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void onlyDiscoversTeamsContainingAcceptedFriends() {
        AppUser current = user("Alice");
        UUID friendId = UUID.randomUUID();
        CardTeam visible = team("Amis");
        when(memberRepository.findByUserId(current.getId())).thenReturn(Optional.empty());
        when(friendshipRepository.findAcceptedFriendIds(current.getId())).thenReturn(List.of(friendId));
        when(memberRepository.findTeamsContainingUsers(List.of(friendId))).thenReturn(List.of(visible));
        when(memberRepository.findByTeamIdOrderByJoinedAt(visible.getId())).thenReturn(List.of(member(visible, user(friendId, "Bob"))));
        when(invitationRepository.findPendingForInvitedUser(current.getId())).thenReturn(List.of());
        when(joinRequestRepository.findPendingForRequester(current.getId())).thenReturn(List.of());

        CardTeamStateDto state = service.getState(current);

        assertThat(state.team()).isNull();
        assertThat(state.discoverableTeams()).extracting(team -> team.name()).containsExactly("Amis");
        assertThat(state.discoverableTeams().getFirst().friends())
                .extracting(friend -> friend.id()).containsExactly(friendId);
    }

    @Test
    void aMemberCanInviteAnAcceptedFriendWithoutATeam() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        CardTeam team = team("Nova");
        CardTeamMember membership = member(team, inviter);
        Friendship friendship = friendship(inviter, invited, FriendshipStatus.ACCEPTED);
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.of(membership));
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(friendshipRepository.findBetween(inviter.getId(), invited.getId())).thenReturn(Optional.of(friendship));
        when(memberRepository.findByUserId(invited.getId())).thenReturn(Optional.empty());
        when(memberRepository.countByTeamId(team.getId())).thenReturn(1L);
        when(invitationRepository.existsByInvitedIdAndStatus(invited.getId(), CardTeamRequestStatus.PENDING)).thenReturn(false);

        service.invite(inviter, invited.getId(), null);

        ArgumentCaptor<CardTeamInvitation> invitation = ArgumentCaptor.forClass(CardTeamInvitation.class);
        verify(invitationRepository).save(invitation.capture());
        assertThat(invitation.getValue().getTeam()).isSameAs(team);
        assertThat(invitation.getValue().getInvited()).isSameAs(invited);
    }

    @Test
    void aPlayerCanOnlyRequestATeamContainingAFriend() {
        AppUser requester = user("Alice");
        AppUser friend = user("Bob");
        CardTeam team = team("Nova");
        when(memberRepository.findByUserId(requester.getId())).thenReturn(Optional.empty());
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdOrderByJoinedAt(team.getId())).thenReturn(List.of(member(team, friend)));
        when(friendshipRepository.findAcceptedFriendIds(requester.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.requestJoin(requester, team.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ami");
        verify(joinRequestRepository, never()).save(any());

        when(friendshipRepository.findAcceptedFriendIds(requester.getId())).thenReturn(List.of(friend.getId()));
        when(joinRequestRepository.existsByRequesterIdAndStatus(requester.getId(), CardTeamRequestStatus.PENDING)).thenReturn(false);
        when(memberRepository.countByTeamId(team.getId())).thenReturn(1L);
        service.requestJoin(requester, team.getId());
        verify(joinRequestRepository).save(any(CardTeamJoinRequest.class));
    }

    @Test
    void acceptingARequestLocksCapacityAndMakesEveryMemberAFriend() {
        AppUser decidingMember = user("Alice");
        AppUser otherMember = user("Claire");
        AppUser requester = user("Bob");
        CardTeam team = team("Nova");
        CardTeamJoinRequest request = new CardTeamJoinRequest();
        request.setId(UUID.randomUUID());
        request.setTeam(team);
        request.setRequester(requester);
        request.setStatus(CardTeamRequestStatus.PENDING);
        when(memberRepository.findByUserId(decidingMember.getId())).thenReturn(Optional.of(member(team, decidingMember)));
        when(joinRequestRepository.findByIdAndStatus(request.getId(), CardTeamRequestStatus.PENDING)).thenReturn(Optional.of(request));
        when(teamRepository.findByIdForUpdate(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByUserId(requester.getId())).thenReturn(Optional.empty());
        when(memberRepository.countByTeamId(team.getId())).thenReturn(2L);
        when(memberRepository.findByTeamIdOrderByJoinedAt(team.getId())).thenReturn(List.of(
                member(team, decidingMember), member(team, otherMember)
        ));
        when(friendshipRepository.findBetween(requester.getId(), decidingMember.getId())).thenReturn(Optional.empty());
        when(friendshipRepository.findBetween(requester.getId(), otherMember.getId())).thenReturn(Optional.empty());

        service.acceptJoinRequest(decidingMember, request.getId());

        verify(memberRepository).save(any(CardTeamMember.class));
        verify(friendshipRepository, times(2)).save(any(Friendship.class));
        assertThat(request.getStatus()).isEqualTo(CardTeamRequestStatus.ACCEPTED);
        assertThat(request.getDecidedBy()).isSameAs(decidingMember);
    }

    @Test
    void refusesASeventhMemberAfterLockingTheTeam() {
        AppUser decidingMember = user("Alice");
        AppUser requester = user("Bob");
        CardTeam team = team("Nova");
        CardTeamJoinRequest request = new CardTeamJoinRequest();
        request.setId(UUID.randomUUID());
        request.setTeam(team);
        request.setRequester(requester);
        when(memberRepository.findByUserId(decidingMember.getId())).thenReturn(Optional.of(member(team, decidingMember)));
        when(joinRequestRepository.findByIdAndStatus(request.getId(), CardTeamRequestStatus.PENDING)).thenReturn(Optional.of(request));
        when(teamRepository.findByIdForUpdate(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByUserId(requester.getId())).thenReturn(Optional.empty());
        when(memberRepository.countByTeamId(team.getId())).thenReturn(6L);

        assertThatThrownBy(() -> service.acceptJoinRequest(decidingMember, request.getId()))
                .hasMessageContaining("complete");
        verify(memberRepository, never()).save(any());
    }

    @Test
    void invitedPlayerCanAcceptForThemself() {
        AppUser invited = user("Bob");
        AppUser inviter = user("Alice");
        CardTeam team = team("Nova");
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setTeam(team);
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        when(invitationRepository.findByIdAndStatus(invitation.getId(), CardTeamRequestStatus.PENDING)).thenReturn(Optional.of(invitation));
        when(teamRepository.findByIdForUpdate(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByUserId(invited.getId())).thenReturn(Optional.empty());
        when(memberRepository.countByTeamId(team.getId())).thenReturn(1L);
        when(memberRepository.findByTeamIdOrderByJoinedAt(team.getId())).thenReturn(List.of(member(team, inviter)));
        when(friendshipRepository.findBetween(invited.getId(), inviter.getId())).thenReturn(Optional.of(friendship(inviter, invited, FriendshipStatus.ACCEPTED)));

        service.acceptInvitation(invited, invitation.getId());

        verify(memberRepository).save(any(CardTeamMember.class));
        assertThat(invitation.getStatus()).isEqualTo(CardTeamRequestStatus.ACCEPTED);
    }

    // ── Une equipe nait a deux ──────────────────────────────────────────────────

    /**
     * Inviter sans equipe ne cree rien : l'invitation porte seulement un nom.
     *
     * <p>Une equipe d'une personne n'apporte rien -- rien n'est mutualise -- et elle
     * reserverait un nom que plus personne ne pourrait prendre. Tant que l'autre n'a pas
     * accepte, il n'y a donc pas d'equipe, et l'invitant reste sans equipe.</p>
     */
    @Test
    void invitingWithoutATeamCreatesNothingAndCarriesTheProposedName() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(friendshipRepository.findBetween(inviter.getId(), invited.getId()))
                .thenReturn(Optional.of(friendship(inviter, invited, FriendshipStatus.ACCEPTED)));
        when(memberRepository.findByUserId(invited.getId())).thenReturn(Optional.empty());
        when(invitationRepository.existsByInvitedIdAndStatus(invited.getId(), CardTeamRequestStatus.PENDING))
                .thenReturn(false);
        when(joinRequestRepository.existsByRequesterIdAndStatus(invited.getId(), CardTeamRequestStatus.PENDING))
                .thenReturn(false);

        service.invite(inviter, invited.getId(), "  Les Cometes  ");

        verify(teamRepository, never()).save(any());
        ArgumentCaptor<CardTeamInvitation> invitation = ArgumentCaptor.forClass(CardTeamInvitation.class);
        verify(invitationRepository).save(invitation.capture());
        assertThat(invitation.getValue().getTeam()).isNull();
        assertThat(invitation.getValue().getProposedTeamName()).isEqualTo("Les Cometes");
    }

    /**
     * Un nom d'equipe tient en dix-huit caracteres.
     *
     * <p>La contrainte vient de l'endroit ou il se lit le plus mal : la ligne de
     * classement, ou il partage la largeur avec un rang, un departement et un score. Un
     * nom plus long y chassait le reste hors de l'ecran. Elle est posee ici, la ou le nom
     * naît, et non a l'affichage seul : sinon chaque ecran devrait le tronquer a son
     * tour, et le joueur ne verrait jamais le nom qu'il a choisi.</p>
     */
    @Test
    void aTeamNameLongerThanEighteenCharactersIsRefused() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(inviter, invited.getId(), "Les Explorateurs du Cosmos"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("18");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void invitingWithoutATeamRequiresAName() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(inviter, invited.getId(), null))
                .isInstanceOf(BadRequestException.class);
        verify(invitationRepository, never()).save(any());
    }

    /**
     * Depuis une equipe existante, le nom n'a pas a etre fourni -- ni accepte.
     *
     * <p>L'equipe est nommee depuis sa naissance. Un nom envoye ici serait un moyen
     * detourne de la renommer, et l'ignorer en silence laisserait croire qu'il a ete
     * pris en compte.</p>
     */
    @Test
    void invitingFromAnExistingTeamRefusesAName() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        CardTeam team = team("Nova");
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.of(member(team, inviter)));

        assertThatThrownBy(() -> service.invite(inviter, invited.getId(), "Autre nom"))
                .isInstanceOf(BadRequestException.class);
        verify(invitationRepository, never()).save(any());
    }

    /** L'acceptation fait naitre l'equipe, avec ses deux membres d'un coup. */
    @Test
    void acceptingAnInvitationWithoutATeamCreatesItWithBothMembers() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        invitation.setProposedTeamName("Les Cometes");
        invitation.setStatus(CardTeamRequestStatus.PENDING);

        when(invitationRepository.findByIdAndStatus(invitation.getId(), CardTeamRequestStatus.PENDING))
                .thenReturn(Optional.of(invitation));
        when(memberRepository.findByUserId(invited.getId())).thenReturn(Optional.empty());
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());
        when(teamRepository.existsByNameIgnoreCase("Les Cometes")).thenReturn(false);
        when(teamRepository.save(any())).thenAnswer(call -> {
            CardTeam created = call.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });

        service.acceptInvitation(invited, invitation.getId());

        ArgumentCaptor<CardTeam> created = ArgumentCaptor.forClass(CardTeam.class);
        verify(teamRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Les Cometes");
        assertThat(created.getValue().getCreator()).isSameAs(inviter);
        // Les deux arrivent ensemble : une equipe ne passe jamais par un seul membre.
        verify(memberRepository, times(2)).save(any(CardTeamMember.class));
        assertThat(invitation.getStatus()).isEqualTo(CardTeamRequestStatus.ACCEPTED);
        assertThat(invitation.getProposedTeamName()).isNull();
    }

    /**
     * Le nom peut avoir ete pris entre l'invitation et l'acceptation.
     *
     * <p>Il n'etait reserve nulle part -- c'est tout l'interet de ne rien creer avant --
     * donc le conflit ne se decide qu'ici, et doit rester lisible.</p>
     */
    @Test
    void acceptingRefusesANameTakenSinceTheInvitation() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        invitation.setProposedTeamName("Les Cometes");
        invitation.setStatus(CardTeamRequestStatus.PENDING);

        when(invitationRepository.findByIdAndStatus(invitation.getId(), CardTeamRequestStatus.PENDING))
                .thenReturn(Optional.of(invitation));
        when(memberRepository.findByUserId(invited.getId())).thenReturn(Optional.empty());
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());
        when(teamRepository.existsByNameIgnoreCase("Les Cometes")).thenReturn(true);

        assertThatThrownBy(() -> service.acceptInvitation(invited, invitation.getId()))
                .isInstanceOf(CodedConflictException.class)
                .extracting(error -> ((CodedConflictException) error).getCode())
                .isEqualTo("CARD_TEAM_NAME_TAKEN");
        verify(memberRepository, never()).save(any(CardTeamMember.class));
    }

    /**
     * L'invitant a rejoint une equipe entre-temps : l'invitation n'a plus de sens.
     *
     * <p>Elle promettait une equipe a deux qui n'existera pas. La refuser explicitement
     * vaut mieux que de creer une equipe dont l'invitant ne fera pas partie.</p>
     */
    @Test
    void acceptingRefusesWhenTheInviterJoinedSomewhereElseMeanwhile() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        invitation.setProposedTeamName("Les Cometes");
        invitation.setStatus(CardTeamRequestStatus.PENDING);

        when(invitationRepository.findByIdAndStatus(invitation.getId(), CardTeamRequestStatus.PENDING))
                .thenReturn(Optional.of(invitation));
        when(memberRepository.findByUserId(invited.getId())).thenReturn(Optional.empty());
        when(memberRepository.findByUserId(inviter.getId()))
                .thenReturn(Optional.of(member(team("Ailleurs"), inviter)));

        assertThatThrownBy(() -> service.acceptInvitation(invited, invitation.getId()))
                .isInstanceOf(CodedConflictException.class)
                .extracting(error -> ((CodedConflictException) error).getCode())
                .isEqualTo("CARD_TEAM_INVITER_LEFT");
        verify(teamRepository, never()).save(any());
    }

    /** Fonder une equipe seul n'existe plus. */
    @Test
    void thereIsNoWayToCreateATeamAlone() {
        assertThat(CardTeamService.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("create", "leaveTeam");
    }

    /**
     * Sans equipe, une invitation envoyee est la seule trace de ce qu'on a entrepris.
     *
     * <p>Rien n'a ete cree en base -- c'est le principe -- donc si l'etat ne la portait
     * pas, l'ecran resterait identique a celui d'avant l'envoi.</p>
     */
    @Test
    void theTeamlessStateCarriesTheInvitationsAlreadySent() {
        AppUser inviter = user("Alice");
        AppUser invited = user("Bob");
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        invitation.setProposedTeamName("Les Cometes");
        when(memberRepository.findByUserId(inviter.getId())).thenReturn(Optional.empty());
        when(friendshipRepository.findAcceptedFriendIds(inviter.getId())).thenReturn(List.of());
        when(invitationRepository.findPendingForInvitedUser(inviter.getId())).thenReturn(List.of());
        when(joinRequestRepository.findPendingForRequester(inviter.getId())).thenReturn(List.of());
        when(invitationRepository.findPendingFromInviter(inviter.getId())).thenReturn(List.of(invitation));

        CardTeamStateDto state = service.getState(inviter);

        assertThat(state.outgoingInvitations()).singleElement().satisfies(sent -> {
            assertThat(sent.teamName()).isEqualTo("Les Cometes");
            assertThat(sent.invited().displayName()).isEqualTo("Bob");
        });
    }

    private static AppUser user(String name) {
        return user(UUID.randomUUID(), name);
    }

    private static AppUser user(UUID id, String name) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setDisplayName(name);
        user.setEmail(name.toLowerCase() + "@example.test");
        return user;
    }

    private static CardTeam team(String name) {
        CardTeam team = new CardTeam();
        team.setId(UUID.randomUUID());
        team.setName(name);
        team.setCreator(user("Creator"));
        team.setCreatedAt(Instant.parse("2026-08-24T10:00:00Z"));
        return team;
    }

    private static CardTeamMember member(CardTeam team, AppUser user) {
        CardTeamMember member = new CardTeamMember();
        member.setTeam(team);
        member.setUser(user);
        member.setJoinedAt(Instant.parse("2026-08-24T10:00:00Z"));
        return member;
    }

    private static Friendship friendship(AppUser first, AppUser second, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setRequester(first);
        friendship.setAddressee(second);
        friendship.setStatus(status);
        return friendship;
    }
}
